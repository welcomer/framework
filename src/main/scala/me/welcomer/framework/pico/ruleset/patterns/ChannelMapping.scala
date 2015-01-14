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
import scala.util.control.NonFatal
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.PicoRuleset
import me.welcomer.framework.utils.Jsonable
import me.welcomer.framework.utils.Jsonable.jsonableToJsObject
import me.welcomer.framework.pico.EventedMessage
import me.welcomer.framework.pico.EventedFunction

object ChannelMapping {
  case class ChannelDetails(channelType: String, channelId: String) extends Jsonable

  // TODO: Should we make 'type' an enum/objects?
  implicit lazy val channelDetailsFormat: Format[ChannelDetails] = (
    (__ \ "type").format[String] ~
    (__ \ "id").format[String])(ChannelDetails.apply, unlift(ChannelDetails.unapply))

  case class PicoMapping(picoEci: String, replyToEci: String, channels: Set[ChannelDetails]) extends Jsonable

  implicit lazy val picoMappingFormat: Format[PicoMapping] = (
    (__ \ "picoEci").format[String] ~
    (__ \ "replyToEci").format[String] ~
    (__ \ "channels").format[Set[ChannelDetails]])(PicoMapping.apply, unlift(PicoMapping.unapply))
}

trait ChannelMapping { this: PicoRuleset =>
  import ChannelMapping._
  import context._

  val MAPPING_NAMESPACE = "channelMappings"

  /**
   * Create a new pico and map it to the supplied channel.
   *
   * @param channelDetails The channel to map.
   * @param rulesets The rulesets to be installed on the new pico.
   */
  def mapNewPico(
    channelDetails: ChannelDetails,
    rulesets: Set[String])(implicit ec: ExecutionContext): Future[(String, String, JsObject)] = {
    def channelType = channelDetails.channelType
    def channelId = channelDetails.channelId

    lazy val eciDescription = s"[Pico] $channelType->$channelId"

    for {
      newPicoEci <- _picoServices.picoManagement.createNewPico(rulesets)
      myReplyToEci <- _picoServices.eci.generate(Some(eciDescription))
      storeResult <- storeMapping(PicoMapping(newPicoEci, myReplyToEci, Set(channelDetails)))
    } yield { (newPicoEci, myReplyToEci, storeResult) }
  }

  /**
   * Retrieve mapping for the supplied channel.
   *
   * @param channelDetails The channel to lookup mapping for.
   */
  def retrieveMapping(channelDetails: ChannelDetails)(implicit ec: ExecutionContext): Future[Option[PicoMapping]] = {
    val selector = Json.obj(
      "mappings.channels" -> Json.obj(
        "$elemMatch" -> Json.obj(
          "type" -> channelDetails.channelType,
          "id" -> channelDetails.channelId)))
    val projection = Json.obj("mappings.channels.$" -> 1)

    _picoServices.pds.retrieve(selector, projection, Some(MAPPING_NAMESPACE)) map {
      case Some(json) => (json \ "mappings")(0).asOpt[PicoMapping]
      case None       => None
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
  def forChannel(channelDetails: ChannelDetails)(validChannelHandler: PicoMapping => Unit)(invalidChannelHandler: => Unit)(implicit ec: ExecutionContext): Unit = {
    retrieveMapping(channelDetails) map {
      case Some(mapping) => {
        log.debug("[forChannel] ValidChannel: " + channelDetails)
        validChannelHandler(mapping)
      }
      case None => {
        log.debug("[forChannel] InvalidChannel: " + channelDetails)
        invalidChannelHandler
      }
    } recover {
      case NonFatal(e) => log.error(e, "[forChannel] Error: " + e.getMessage())
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
    failureEvent: => Future[Option[EventedEvent]])(implicit ec: ExecutionContext): Unit = {
    def success(m: PicoMapping) = successEvent(m) map { _ map { raiseRemoteEvent(_) } }
    def failure = failureEvent map { _ map { raiseRemoteEvent(_) } }

    forChannel(channelDetails)(success)(failure)
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
    failureEvent: => Future[Option[EventedEvent]] = Future(None)): Unit = {
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
    channelResult map { channelDetails =>
      raiseRemoteEventedForChannel(channelDetails, successHandler, failureEvent)
    } getOrElse { log.error("No ChannelDetails found, should raise error") /* TODO: Handle 'missing channelDetails attrs' error */ }
  }
}
