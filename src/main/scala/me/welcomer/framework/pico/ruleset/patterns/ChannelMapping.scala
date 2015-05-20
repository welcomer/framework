/*  Copyright 2015 White Label Personal Clouds Pty Ltd
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.welcomer.framework.pico.ruleset.patterns

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.EventedFailure
import me.welcomer.framework.pico.EventedFunction
import me.welcomer.framework.pico.EventedMessage
import me.welcomer.framework.pico.EventedResult
import me.welcomer.framework.pico.PicoRuleset
import me.welcomer.framework.pico.UnhandledFunction
import me.welcomer.utils.Jsonable
import me.welcomer.utils.Jsonable.jsonableToJsObject
import scalaz.Scalaz
import scalaz.OptionT
import Scalaz.futureInstance

object ChannelMapping {
  case class ChannelDetails(channelType: String, channelId: String) extends Jsonable

  object EciType extends Enumeration {
    type EciType = Value
    val PicoEci = Value("picoEci")
    val ReplyToEci = Value("replyToEci")
  }

  val MAPPING_NAMESPACE = "channelMappings"

  // TODO: Should we make 'type' an enum/objects?
  implicit lazy val channelDetailsFormat: Format[ChannelDetails] = (
    (__ \ "type").format[String] ~
    (__ \ "id").format[String])(ChannelDetails.apply, unlift(ChannelDetails.unapply))

  case class PicoMapping(picoEci: String, replyToEci: String, channels: Set[ChannelDetails]) extends Jsonable {
    def channelIdsForType(channelType: String): Set[String] = {
      for {
        channel <- channels
        if channel.channelType == channelType
      } yield { channel.channelId }
    }
  }

  implicit lazy val picoMappingFormat: Format[PicoMapping] = (
    (__ \ "picoEci").format[String] ~
    (__ \ "replyToEci").format[String] ~
    (__ \ "channels").format[Set[ChannelDetails]])(PicoMapping.apply, unlift(PicoMapping.unapply))

  implicit class OptionTFutureWithFilter[A](val self: OptionT[Future, A]) extends AnyVal {
    def withFilter(f: A => Boolean)(implicit F: scalaz.Functor[Future]): OptionT[Future, A] = self.filter(f)
  }
}

trait ChannelMapping { this: PicoRuleset =>
  import ChannelMapping._
  import ChannelMapping.EciType._
  import context._

  /**
   * Retrieve PicoMapping if exists for channel, otherwise create and map new pico.
   *
   * @param channelDetails The channel to map.
   * @param rulesets The rulesets to be installed on the new pico.
   *
   * @return The PicoMapping and true if the pico was found, otherwise false
   */
  def retrieveOrMapNewPico(
    channelDetails: ChannelDetails,
    rulesets: Set[String])(implicit ec: ExecutionContext): Future[(PicoMapping, Boolean)] = {

    retrieveMapping(channelDetails) flatMap {
      case Some(picoMapping) => Future(picoMapping, true)
      case None              => mapNewPico(channelDetails, rulesets) map { (_, false) }
    }
  }

  /**
   * Create a new pico and map it to the supplied channel.
   *
   * @param channelDetails The channel to map.
   * @param rulesets The rulesets to be installed on the new pico.
   */
  def mapNewPico(
    channelDetails: ChannelDetails,
    rulesets: Set[String])(implicit ec: ExecutionContext): Future[PicoMapping] = {
    def channelType = channelDetails.channelType
    def channelId = channelDetails.channelId

    lazy val eciDescription = s"[Pico] $channelType->$channelId"

    for {
      newPicoEci <- _picoServices.picoManagement.createNewPico(rulesets)
      myReplyToEci <- _picoServices.eci.generate(Some(eciDescription))
      picoMapping = PicoMapping(newPicoEci, myReplyToEci, Set(channelDetails))
      storeResult <- storeMapping(picoMapping)
      //      if storeResult // TODO: Make sure it saved correctly
    } yield { picoMapping }
  }

  /**
   * Retrieve mapping using a custom selector.
   *
   * @param selector Custom selector
   *
   * @return The PicoMapping if found.
   */
  def retrieveMapping(selector: JsObject)(implicit ec: ExecutionContext): Future[Option[PicoMapping]] = {
    val projection = Json.obj("mappings.channels.$" -> 1)

    val result = _picoServices.pds.retrieve(selector, projection, Some(MAPPING_NAMESPACE)) map {
      case Some(json) => (json \ "mappings")(0).asOpt[PicoMapping]
      case None       => None
    }

    result.onComplete {
      case Success(result) => log.debug("[ChannelMapping::retrieveMapping] selector={}, result={}", selector, result)
      case Failure(e)      => log.error(e, "[ChannelMapping::retrieveMapping] Error: {}", e)
    }

    result
  }

  /**
   * Retrieve mapping for the supplied channel.
   *
   * @param channelDetails The channel to lookup mapping for.
   *
   * @return The PicoMapping if found.
   */
  def retrieveMapping(channelDetails: ChannelDetails)(implicit ec: ExecutionContext): Future[Option[PicoMapping]] = {
    val selector = Json.obj(
      "mappings.channels" -> Json.obj(
        "$elemMatch" -> Json.obj(
          "type" -> channelDetails.channelType,
          "id" -> channelDetails.channelId)))

    retrieveMapping(selector)
  }

  /**
   * Retrieve mapping by eci.
   *
   * @param eci The eci to lookup.
   * @param eciType The eci type to lookup.
   *
   * @return The PicoMapping if found.
   */
  def retrieveMapping(eci: String, eciType: EciType)(implicit ec: ExecutionContext): Future[Option[PicoMapping]] = {
    val selector = Json.obj(
      "mappings" -> Json.obj(
        "$elemMatch" -> Json.obj(
          eciType.toString -> eci)))

    retrieveMapping(selector)
  }

  /**
   * Check whether the supplied eci matches a mapped eci.
   *
   * @param eciOpt The eci to check
   * @param eciType The eci type to check
   *
   * @return the PicoMapping if eci was found
   */
  def checkMappedEci(eciOpt: Option[String], eciType: EciType = ReplyToEci)(implicit ec: ExecutionContext): Future[Option[PicoMapping]] = {
    (for {
      eci <- OptionT(Future(eciOpt))
      mapping <- OptionT(retrieveMapping(eci, eciType))
      foundEci = eciType match {
        case ReplyToEci => mapping.replyToEci
        case PicoEci    => mapping.picoEci
      }
      if eci == foundEci // This causes a compiler warning without OptionTFutureWithFilter, see https://github.com/scalaz/scalaz/pull/922#issuecomment-100045248
    } yield { mapping }).run
  }

  /**
   * Validate that the supplied eci matches a mapped eci and execute the successHandler, or return 'UnhandledFunction'.
   *
   * @param f EventedFunction the function to be processed.
   * @param eciType The eci type to check.
   * @param successHandler The success function to execute.
   */
  def validateMappedEci(f: EventedFunction, eciType: EciType = ReplyToEci)(
    successHandler: (EventedFunction, PicoMapping) => Future[Option[EventedResult[_]]])(implicit ec: ExecutionContext): Future[Option[EventedResult[_]]] = {
    checkMappedEci(f.module.eci) flatMap {
      case Some(mapping) => successHandler(f, mapping)
      case None          => Future(Some(EventedFailure(UnhandledFunction)))
    }
  }

  /**
   * Store the PicoMapping.
   *
   * @param picoMapping The mapping to store.
   */
  def storeMapping(picoMapping: PicoMapping)(implicit ec: ExecutionContext): Future[JsObject] = {
    val arrayKey = "mappings"
    val arrayItem = Json.toJson(picoMapping)

    _picoServices.pds.pushArrayItem(arrayKey, arrayItem, Some(MAPPING_NAMESPACE), unique = true) map { result =>
      (result \ "n").asOpt[Int] match {
        case Some(n) if n > 0 => result
        case _                => throw new Throwable(s"No documents were updated ($result) arrayKey=$arrayKey, arrayItem=$arrayItem")
      }
    }
  }

  /**
   * Add a new channel to an existing mapping.
   *
   * @param eci The pico eci of the mapping to update.
   * @param newChannelDetails The new channel to add to the mapping.
   */
  def storeNewChannelDetails(eci: String, newChannelDetails: ChannelDetails)(implicit ec: ExecutionContext): Future[JsObject] = {
    // TODO: This would probably be more useful as a 'lookup existingChannelDetails -> storeNewChannelDetails' type flow instead i think..
    val selector = Json.obj("mappings.picoEci" -> eci)
    val arrayKey = "mappings.$.channels"

    _picoServices.pds.pushArrayItem(arrayKey, newChannelDetails, Some(MAPPING_NAMESPACE), Some(selector), unique = true) map { result =>
      (result \ "n").asOpt[Int] match {
        case Some(n) if n > 0 => result
        case _                => throw new Throwable(s"No documents were updated ($result) arrayKey=$arrayKey, arrayItem=$newChannelDetails")
      }
    }
  }

  /**
   * Lookup channel mapping and call success/failure handler.
   *
   * @param channelDetails The channel to lookup mapping for.
   * @param validChannelHandler Function called when mapping found.
   * @param invalidChannelHandler Function called when mapping not found.
   */
  def forChannel(channelDetails: ChannelDetails)(validChannelHandler: PicoMapping => Unit)(invalidChannelHandler: EventedFailure => Unit)(implicit ec: ExecutionContext): Unit = {
    retrieveMapping(channelDetails) map {
      case Some(mapping) => {
        log.debug("[forChannel] ValidChannel: " + channelDetails)
        validChannelHandler(mapping)
      }
      case None => {
        log.debug("[forChannel] InvalidChannel: " + channelDetails)

        invalidChannelHandler(
          EventedFailure(
            Json.obj(
              "type" -> "error",
              "desc" -> "InvalidChannel",
              "channel" -> channelDetails)))
      }
    } recover {
      case NonFatal(e) => log.error(e, "[forChannel] Error: {}", e.getMessage())
    }
  }

  /**
   * Raise remote evented based on channel mapping lookup.
   *
   * @param channelDetails The channel to lookup mapping for.
   * @param successEvent The channel mapping was found.
   * @param failureEvent The channel mapping was not found.
   */
  def raiseRemoteEventedForChannel(
    channelDetails: ChannelDetails,
    successEvent: PicoMapping => Future[Option[EventedMessage]],
    failureHandler: EventedFailure => Unit)(implicit ec: ExecutionContext): Unit = {
    def success(m: PicoMapping) = successEvent(m) map { _ map { raiseRemoteEvent(_) } }

    forChannel(channelDetails)(success)(failureHandler)
  }

  /**
   * Raise remote EventedFunction based on channel mapping lookup.
   *
   * @param channelDetails The channel to lookup mapping for.
   * @param func The EventedFunction to raise (eci will be set to ChannelMapping.picoEci)
   */
  def raiseRemoteEventedFunctionForChannel(channelDetails: ChannelDetails)(func: EventedFunction)(implicit ec: ExecutionContext): Future[EventedResult[_]] = {
    retrieveMapping(channelDetails) flatMap {
      case Some(mapping) => raiseRemoteEventWithReplyTo(func.withModuleEci(mapping.picoEci))
      case None => {
        Future(
          EventedFailure(
            Json.obj(
              "type" -> "error",
              "desc" -> "InvalidChannel",
              "channel" -> channelDetails)))
      }
    } recover {
      case NonFatal(e) => {
        log.error(e, "[raiseRemoteEventedFunctionForChannel] Error: {}", e.getMessage())
        EventedFailure(e)
      }
    }
  }

  /**
   * Forward evented to the pico mapped to supplied channel, stripping the channel in the process.
   *
   * @param event The event to forward.
   * @param channelDetailsPath The JSON key where the channel details are stored.
   * @param failureEvent The channel mapping was not found.
   */
  def forwardEventedForChannel(
    evented: EventedMessage,
    channelDetailsPath: JsPath = (__ \ "channelDetails"),
    failureHandler: EventedMessage => EventedFailure => Unit = defaultFailureHandler): Unit = {
    val dropChannelDetails = channelDetailsPath.json.prune
    val pickChannelDetails = channelDetailsPath.json.pick

    def json = evented match {
      case event: EventedEvent   => event.attributes
      case func: EventedFunction => func.module.config
    }

    def successHandler(m: PicoMapping): Future[Option[EventedMessage]] = {
      val newEvented = json.transform(dropChannelDetails).asOpt map { newJson =>
        evented match {
          case event: EventedEvent   => event.copy(attributes = newJson, entityId = Some(m.picoEci))
          case func: EventedFunction => func.copy(module = func.module.copy(config = newJson, eci = Some(m.picoEci)))
        }
      }
      Future(newEvented)
    }

    log.debug("[forwardEventedForChannel] Actual event: {}", evented)

    val channelResult = json.transform(pickChannelDetails) map { _.as[ChannelDetails] }
    channelResult match {
      case JsSuccess(channelDetails, _) => raiseRemoteEventedForChannel(channelDetails, successHandler, failureHandler(evented))
      case JsError(errors) => {
        log.error("No ChannelDetails found ({})", evented)

        failureHandler(evented)(EventedFailure(Json.obj("type" -> "error", "desc" -> "No ChannelDetails found")))
      }
    }
  }

  private def defaultFailureHandler(evented: EventedMessage): EventedFailure => Unit = {
    def fail(failure: EventedFailure): Unit = {
      evented match {
        case func: EventedFunction => func.replyTo map { _ ! failure }
        case _                     => Unit // TODO: Handle EventedEvents better when we get directives?
      }
    }

    fail
  }
}
