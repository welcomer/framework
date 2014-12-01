/*  Copyright 2014 White Label Personal Clouds Pty Ltd
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
package me.welcomer.framework.pico

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSelection.toScala
import akka.actor.actorRef2Scala
import me.welcomer.framework.extensions.ReflectionExtension
import me.welcomer.framework.picocontainer.PicoContainer.CreatePico
import me.welcomer.rulesets.PdsRuleset
import me.welcomer.rulesets.PdsRulesetSchema
import play.api.libs.functional.syntax._
import play.api.libs.json._

trait PicoDSL

trait PicoRulesetContainerDSL extends AnyRef { self: PicoRulesetContainer =>
  def loadRulesets(rulesets: Set[String])(implicit ec: ExecutionContext): Map[String, Try[ActorRef]] = {
    rulesets.size match {
      case size if (size > 0) => {
        log.info(s"Loading rulesets ($size)..")

        val rulesetRefs = rulesets.foldLeft(Map[String, Try[ActorRef]]()) { (rulesetRefs, ruleset) => rulesetRefs + (ruleset -> loadRuleset(ruleset)) }

        log.info("Loading rulesets complete.")

        rulesetRefs
      }
      case _ => {
        log.info(s"No rulesets to load.")

        Map[String, Try[ActorRef]]()
      }
    }
  }

  def loadRuleset(name: String): Try[ActorRef] = {
    log.info("Loading ruleset {}..", name)

    Try(
      useRulesetCalled(name) match {
        case Success(ruleset) => {
          context.actorOf(
            PicoRuleset.props(ruleset, _picoServices),
            name)
        }
        case Failure(e) => {
          e match {
            case _: ClassNotFoundException => log.error("ClassNotFoundException while attempting to load ruleset {}", name)
            case _ => log.error(e, "Error while attempting to load ruleset {}", name)
          }

          throw e
        }
      })
  }

  // TODO: Refactor the ruleset scope into settings somehow? (put pico settings in picoServices?)
  def useRulesetCalled(className: String): Try[Class[_ <: PicoRuleset]] = {
    val fqcn = "me.welcomer.rulesets." + className

    ReflectionExtension(context.system).classFor[PicoRuleset](fqcn)
  }
}

trait PicoRulesetDSL extends AnyRef
  with PicoLoggingDSL
  with PicoEventsDSL
  with PicoEventHandlingDSL
  with PicoPdsDSL
  with CreatePicoDSL { this: PicoRuleset =>
  override def rulesetContainer: ActorRef = context.parent
}

trait PicoRuleDSL extends AnyRef
  with PicoEventsDSL
  with PicoPdsDSL { this: Actor with ActorLogging =>
  // TODO: Scope this to Rules? Would need to use a common trait/base
  //  override def rulesetContainer: ActorRef = context.parent.parent
}

trait PicoEventsDSL extends AnyRef
  with PicoRaiseRemoteEventDSL
  with PicoRaisePicoEventDSL { this: Actor with ActorLogging =>
  def subscribeToEventedEvents(eventDomain: Option[String] = None, eventType: Option[String] = None, localOnly: Boolean = false): Unit = {
    rulesetContainer ! PicoRulesetContainer.Subscribe(eventDomain, eventType, localOnly)
  }

  def subscribeToEvents(eventDomain: String, eventType: String, localOnly: Boolean = false): Unit = subscribeToEventedEvents(Some(eventDomain), Some(eventType), localOnly)
  def subscribeToLocalEvents(eventDomain: String, eventType: String): Unit = subscribeToEvents(eventDomain, eventType, true)

  def subscribeToAllEvents: Unit = subscribeToEventedEvents(None, None, false)
  def subscribeToAllLocalEvents: Unit = subscribeToEventedEvents(None, None, true)

  def subscribeToEventDomain(eventDomain: String, localOnly: Boolean = false): Unit = subscribeToEventedEvents(Some(eventDomain), None, localOnly)
  def subscribeToLocalEventDomain(eventDomain: String): Unit = subscribeToEventDomain(eventDomain, true)

  def subscribeToEventType(eventType: String, localOnly: Boolean = false): Unit = subscribeToEventedEvents(None, Some(eventType), localOnly)
  def subscribeToLocalEventType(eventType: String): Unit = subscribeToEventType(eventType, true)
}

trait PicoRaiseRemoteEventDSL { this: Actor with ActorLogging =>
  def rulesetContainer: ActorRef

  def raiseRemoteEvent(event: EventedEvent): Unit = {
    log.debug("[raiseRemoteEvent] {}", event)
    rulesetContainer ! PicoRulesetContainer.RaiseRemoteEvent(event)
  }

  def raiseRemoteEvent(eventDomain: String, eventType: String, attributes: JsObject, entityId: String): Unit = {
    raiseRemoteEvent(EventedEvent(eventDomain, eventType, attributes = attributes, entityId = Some(entityId)))
  }
}

trait PicoRaisePicoEventDSL { this: Actor with ActorLogging =>
  def rulesetContainer: ActorRef

  def raisePicoEvent(event: EventedEvent): Unit = {
    log.debug("[raisePicoEvent] {}", event)

    val strippedEvent = event.copy(entityId = None)
    rulesetContainer ! PicoRulesetContainer.RaiseLocalEvent(strippedEvent)
  }

  def raisePicoEvent(eventDomain: String, eventType: String, attributes: JsObject): Unit = {
    raisePicoEvent(EventedEvent(eventDomain, eventType, attributes = attributes, entityId = None))
  }
}

object PicoEventSchema {
  case class TransactionId(transactionId: String)

  implicit val transactionIdReads =
    ((__ \ "transactionId").read[String]).map(TransactionId(_))

  implicit val transactionIdWrites = new Writes[TransactionId] {
    def writes(transactionId: TransactionId) = Json.obj(
      "transactionId" -> transactionId.transactionId)
  }
}

trait PicoEventHandlingDSL extends AnyRef { this: Actor with ActorLogging =>
  import PicoEventSchema._

  def handleEvent[T](
    successHandler: (T, EventedEvent) => Unit,
    errorHandler: (JsError, EventedEvent) => Unit = handleAttributeError)(implicit event: EventedEvent, readsT: Reads[T]): Unit = {
    event.attributes.validate[T] match {
      case JsSuccess(value, path) => successHandler(value, event)
      case e: JsError => errorHandler(e, event);
    }
  }

  protected def handleAttributeError(e: JsError, event: EventedEvent): Unit = {
    val errorJson = JsError.toFlatJson(e)

    log.error("Error with attributes: {}", Json.prettyPrint(errorJson))

    // TODO: Check if there's a replyTo and if so, use it to send an error event back?
  }
}

trait PicoLoggingDSL { this: PicoRuleset =>
  def logEventInfo(implicit event: EventedEvent) = {
    log.info("[{}::{}] {}", event.eventDomain, event.eventType, event)
  }
}

trait CreatePicoDSL { this: Actor with ActorLogging =>
  private val picoContainerPath = "/user/overlord/pico" // TODO: Load this from settings/something?

  def createPico(
    rulesets: Set[String],
    replyToEci: String,
    responseEventDomain: Option[String] = None,
    responseEventSuccessType: Option[String] = None,
    responseEventFailureType: Option[String] = None)(implicit transactionId: Option[String]): Unit = {
    val debugStr = s"[createPico] rulesets=$rulesets, replyToEci=$replyToEci, transactionId=$transactionId, responseEventDomain=$responseEventDomain, responseEventSuccessType=$responseEventSuccessType, responseEventFailureType=$responseEventFailureType"
    log.debug(debugStr)

    context.actorSelection(picoContainerPath) ! CreatePico(
      rulesets,
      replyToEci,
      transactionId,
      responseEventDomain,
      responseEventSuccessType,
      responseEventFailureType)
  }
}

trait PicoPdsDSL { this: Actor with ActorLogging with PicoEventsDSL =>
  import me.welcomer.framework.utils.ImplicitConversions._

  protected object PDS {
    object Event {
      val EVENT_DOMAIN = PdsRulesetSchema.Event.EVENT_DOMAIN

      val RETRIEVE_ITEM_SUCCESS: String = PdsRuleset.RetrieveItemSuccess()
      val RETRIEVE_ITEM_FAILURE: String = PdsRuleset.RetrieveItemFailure()
    }

    def Attr = PdsRulesetSchema.Attr

    val TRANSACTION_ID = "transactionId"
    val IDENTIFIER = "identifier"

    private def buildAttributes(attributes: (String, Option[String])*)(implicit transactionId: String): JsObject = {
      attributes.foldLeft(Json.obj(TRANSACTION_ID -> transactionId)) { (json, keyValue) =>
        val (key, valueOpt) = keyValue

        json ++ (valueOpt.map { value => Json.obj(key -> value) } getOrElse Json.obj())
      }
    }

    def storeItem(key: String, value: JsValue, namespace: Option[String] = None)(implicit transactionId: String): Unit = {
      val attributes = Json.obj(
        Attr.ITEM_KEY -> key,
        Attr.ITEM_VALUE -> value) ++
        buildAttributes((Attr.ITEM_NAMESPACE, namespace))

      raisePicoEvent(EventedEvent(Event.EVENT_DOMAIN, PdsRuleset.StoreItem(), attributes = attributes))
    }

    def storeAllItems(value: JsObject, namespace: Option[String])(implicit transactionId: String): Unit = {
      val attributes = buildAttributes((Attr.ITEM_NAMESPACE, namespace)) ++ Json.obj(Attr.DATA -> value)

      raisePicoEvent(EventedEvent(Event.EVENT_DOMAIN, PdsRuleset.StoreAllItems(), attributes = attributes))
    }

    def storeAllItems(value: JsObject, namespace: String)(implicit transactionId: String): Unit = storeAllItems(value, Some(namespace))

    def retrieveItem(
      key: String,
      namespace: Option[String] = None,
      identifier: Option[String] = None)(implicit transactionId: String): Unit = {
      // TODO: Make this use an ask or similar to actually return the result?
      // Ask probably wouldn't work since it can't reply to sender
      // Alternately, we could wait till we've implemented proper function calls between picos/rulesets
      val attributes = buildAttributes(
        (Attr.ITEM_NAMESPACE, namespace),
        (IDENTIFIER, identifier)) ++ Json.obj(Attr.ITEM_KEY -> key)

      raisePicoEvent(EventedEvent(Event.EVENT_DOMAIN, PdsRuleset.RetrieveItem(), attributes = attributes))
    }

    def retrieveAllItems(
      namespace: Option[String] = None,
      filter: Option[List[String]] = None,
      identifier: Option[String] = None)(implicit transactionId: String): Unit = {

      val filterJson = filter.map { filter => Json.obj(Attr.FILTER -> filter) } getOrElse Json.obj()
      val attributes = buildAttributes(
        (Attr.ITEM_NAMESPACE, namespace),
        (IDENTIFIER, identifier)) ++ filterJson

      raisePicoEvent(EventedEvent(Event.EVENT_DOMAIN, PdsRuleset.RetrieveAllItems(), attributes = attributes))
    }

    def removeItem(key: String, namespace: Option[String] = None)(implicit transactionId: String): Unit = {
      val attributes = buildAttributes((Attr.ITEM_NAMESPACE, namespace)) ++ Json.obj(Attr.ITEM_KEY -> key)

      raisePicoEvent(EventedEvent(Event.EVENT_DOMAIN, PdsRuleset.RemoveItem(), attributes = attributes))
    }
  }
}
