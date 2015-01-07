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
package me.welcomer.rulesets.welcomerId

import scala.annotation.implicitNotFound
import scala.collection.mutable.HashMap
import scala.language.postfixOps

import me.welcomer.framework.pico.{ EventedEvent, PicoRuleset }
import me.welcomer.framework.pico.service.PicoServicesComponent
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

object UserRulesetSchema {
  object Event {
    val RETRIEVE_EDENTITI_DATA = "retrieveEdentitiData"
    val EDENTITI_DATA = "edentitiData"
    val EDENTITI_DATA_FAILURE = "edentitiDataFailure"
  }

  object Attr {
    val IDENTIFIER = "identifier"
    val ITEM_NAMESPACE = "itemNamespace"

    val VENDOR = "vendor"
    val USER = "user"
    val EDENTITI = "edentiti"

    val USER_ID = "userId"

    val ERROR_MSG = "errorMsg"
  }
}

class UserRuleset(picoServices: PicoServicesComponent#PicoServices) extends PicoRuleset(picoServices) with UserRulesetHelper {
  import context._
  import UserRulesetSchema._
  import me.welcomer.rulesets.welcomerId.WelcomerIdSchema._

  subscribeToEventDomain(EventDomain.USER)
  subscribeToLocalEventDomain("PDS")

  var transactions = HashMap[String, Option[String]]() // transactionId -> Option(eci)

  // TODO: Does it make sense to implement 'ask' within a ruleset now? It would make things like the PDS way simpler to use
  // TODO: If so, probably use it directly for now (will have to pass on sender for local pico events)

  // TODO: Refactor this to make use of handleEvent DSL
  override def selectWhen = {
    case event @ EventedEvent(EventDomain.USER, eventType, timestamp, attributes, entityId) => eventType match {
      case EventType.CREATED => {
        log.info("[{}::{}] {}", event.eventDomain, event.eventType, event)

        curTransactionId map { implicit transactionId =>
          // TODO: Refactor this to make use of WelcomerIdSchema json formats
          val vendorDataOpt = (attributes \ Attr.VENDOR).asOpt[JsObject]
          val userDataOpt = (attributes \ Attr.USER).asOpt[JsObject]

          (vendorDataOpt, userDataOpt) match {
            case (Some(vendorData), Some(userData)) => {
              val attr = Json.obj("transactionId" -> transactionId)

              // Store Vendor Data
              raisePicoEvent(
                EventDomain.USER,
                EventType.VENDOR_DATA_AVAILABLE,
                attr ++ Json.obj("data" -> vendorData))

              // Store User Data
              raisePicoEvent(
                EventDomain.USER,
                EventType.USER_DATA_AVAILABLE,
                attr ++ Json.obj("data" -> userData))
            }
            case _ => {
              // TODO: Handle bad data error here
              log.error("Invalid data received: 'vendorData'/'userData' required and should be objects ({})", event)
            }
          }
        } getOrElse { log.error("Invalid data received: '{}' required ({})", TRANSACTION_ID, event) }
      }
      case EventType.USER_DATA_AVAILABLE | EventType.VENDOR_DATA_AVAILABLE | EventType.EDENTITI_DATA_AVAILABLE => {
        log.info("[{}::{}] {}", event.eventDomain, event.eventType, event)

        curTransactionId map { implicit transactionId =>
          val dataOpt = (attributes \ DATA).asOpt[JsObject]

          dataOpt map { data =>
            val namespace = event.eventType match {
              case EventType.USER_DATA_AVAILABLE => Attr.USER
              case EventType.VENDOR_DATA_AVAILABLE => Attr.VENDOR
              case EventType.EDENTITI_DATA_AVAILABLE => Attr.EDENTITI
            }

            PDS.storeAllItems(data, namespace)
          } getOrElse { log.error("Invalid data received: '{}' required and should be object ({})", DATA, event) }
        } getOrElse { log.error("Invalid data received: '{}' required ({})", TRANSACTION_ID, event) }
      }
      case EventType.RETRIEVE_USER_DATA | Event.RETRIEVE_EDENTITI_DATA | EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA => {
        log.info("[{}::{}] {}", event.eventDomain, event.eventType, event)

        curTransactionId map { implicit transactionId =>
          // Lookup data from PDS and apply filter.
          val replyToOpt = (attributes \ REPLY_TO).asOpt[String]
          val filter = (attributes \ FILTER).asOpt[List[String]]

          replyToOpt map { replyTo =>
            transactions += (transactionId -> Some(replyTo)) // Remember where to reply to

            val (namespace, identifier, extraFilter) = event.eventType match {
              case EventType.RETRIEVE_USER_DATA => (Attr.USER, None, Nil)
              case Event.RETRIEVE_EDENTITI_DATA => (Attr.EDENTITI, None, Nil)
              case identifier @ EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA => {
                (Attr.EDENTITI, Some(identifier), List(Attr.USER_ID))
              }
            }

            val joinedFilter = filter map { _ ++ extraFilter } orElse { Some(extraFilter) }

            PDS.retrieveAllItems(Some(namespace), joinedFilter, identifier)
          } getOrElse { log.error("Invalid data received: {} is required ({})", REPLY_TO, event) }
        } getOrElse { log.error("Invalid data received: '{}' required ({})", TRANSACTION_ID, event) }
      }
      case "EdentitiRegistrationComplete" => // ?edentitiDataAvailable? Store edentitiId (and maybe a flag saying registration has happened). maybe store the result too or at least log it.
      case "userVerificationNotification" => // ?verificationDataAvailable? Store the date and result in the PDS.
      case _ => log.debug("Unhandled {} EventedEvent Received ({})", EventDomain.USER, event)
    }
    case event @ EventedEvent(PDS.Event.EVENT_DOMAIN, eventType, timestamp, attributes, entityId) => eventType match {
      case PDS.Event.RETRIEVE_ITEM_SUCCESS => handlePdsRetrieveItemSuccess(event)
      case PDS.Event.RETRIEVE_ITEM_FAILURE => handlePdsRetrieveItemFailure(event)
      case _ => log.debug("Unhandled PDS Event: ({})", event)
    }
  }
}

trait UserRulesetHelper { self: UserRuleset =>
  import UserRulesetSchema._
  import me.welcomer.rulesets.welcomerId.WelcomerIdSchema._

  protected def replyToTransaction(eventDomain: String, eventType: String, attributes: JsObject)(implicit event: EventedEvent, transactionIdOpt: Option[String]) = {
    transactionIdOpt map { implicit transactionId =>
      transactions.get(transactionId) map { replyToOpt =>
        transactions -= transactionId // TODO: Is closing over this a bad idea? Maybe send a message so we can update it syncronously? (probably pull it up into PicoRuleset)

        replyToOpt match {
          case Some(replyTo) => raiseRemoteEvent(eventDomain, eventType, attributes, replyTo)
          case None => raisePicoEvent(eventDomain, eventType, attributes)
        }
      } getOrElse { log.error("'{}' not found in map, don't know who to reply to ({})", TRANSACTION_ID, event) }
    } getOrElse { log.error("No '{}' so we can't handle this ({})", TRANSACTION_ID, event) }
  }

  protected def handlePdsRetrieveItemSuccess(event: EventedEvent) = {
    log.debug("[{}::{}] {}", event.eventDomain, event.eventType, event)

    val namespaceOpt = (event.attributes \ Attr.ITEM_NAMESPACE).asOpt[String]
    val identifierOpt = (event.attributes \ Attr.IDENTIFIER).asOpt[String]

    namespaceOpt map { namespace =>
      val transformer =
        (__ \ TRANSACTION_ID).json.pickBranch and
          (__ \ DATA).json.pickBranch reduce //and
      //              (__ \ Attr.FILTER).json.pickBranch reduce

      event.attributes.transform(transformer) map { attr =>
        (namespace, identifierOpt) match {
          case (Attr.USER, _) => handlePdsRetrieveItemSuccess_userNamespace(attr, identifierOpt)
          case (Attr.EDENTITI, _) => handlePdsRetrieveItemSuccess_edentitiNamespace(attr, identifierOpt)
          case _ => log.error("Unhandled namespace/identifier: ({})", event)
        }
      } getOrElse { log.error("Unable to transform attributes: ({})", event) }
    } getOrElse { log.error("Unknown namespace: ({})", event) }
  }

  protected def handlePdsRetrieveItemSuccess_userNamespace: PartialFunction[(JsObject, Option[String]), Unit] = {
    // TODO: Not sure if there is any benefit to this being a partial function or not..
    case (attr, Some(identifier)) if identifier == EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA => {
      replyToTransaction(EventDomain.USER, EventType.EDENTITI_NEW_USER_DATA, attr)
    }
    case (attr, _) => replyToTransaction(EventDomain.USER, EventType.USER_DATA, attr)
  }

  protected def handlePdsRetrieveItemSuccess_edentitiNamespace: PartialFunction[(JsObject, Option[String]), Unit] = {
    // TODO: Not sure if there is any benefit to this being a partial function or not..
    case (attr, Some(identifier)) if identifier == EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA => {
      handlePdsRetrieveItemEdentitiIdOrUserData_Edentiti(attr, identifier)
    }
    case (attr, _) => replyToTransaction(EventDomain.USER, Event.EDENTITI_DATA, attr)
  }

  protected def handlePdsRetrieveItemFailure(event: EventedEvent) = {
    log.error("[{}::{}] {}", event.eventDomain, event.eventType, event)

    val namespaceOpt = (event.attributes \ Attr.ITEM_NAMESPACE).asOpt[String]
    val identifierOpt = (event.attributes \ Attr.IDENTIFIER).asOpt[String]

    namespaceOpt map { namespace =>
      val transformer = (
        (__ \ TRANSACTION_ID).json.pickBranch and
        (__ \ Attr.ERROR_MSG).json.pickBranch) reduce

      event.attributes.transform(transformer) map { attr =>
        (namespace, identifierOpt) match {
          case (Attr.USER, _) => handlePdsRetrieveItemFailure_userNamespace(attr, identifierOpt)
          case (Attr.EDENTITI, _) => handlePdsRetrieveItemFailure_edentitiNamespace(attr, identifierOpt)
          case _ => log.error("Unhandled namespace: ({})", event)
        }
      } getOrElse { log.error("Unable to transform attributes: ({})", event) }
    } getOrElse { log.error("Unknown namespace: ({})", event) }
  }

  protected def handlePdsRetrieveItemFailure_userNamespace(attr: JsObject, identifierOpt: Option[String] = None) = {
    //    identifierOpt map { identifier =>
    val transactionId = curTransactionId.getOrElse("")
    val attr = Json.toJson(UserData(transactionId, Nil, Json.obj())).as[JsObject]

    replyToTransaction(EventDomain.USER, EventType.USER_DATA, attr)
    //    }
  }

  protected def handlePdsRetrieveItemFailure_edentitiNamespace(attr: JsObject, identifierOpt: Option[String] = None) = {
    identifierOpt match {
      case Some(identifier) if identifier == EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA => {
        handlePdsRetrieveItemEdentitiIdOrUserData_Edentiti(attr, identifier)
      }
      case _ => replyToTransaction(EventDomain.USER, Event.EDENTITI_DATA_FAILURE, attr)
    }
  }

  protected def handlePdsRetrieveItemEdentitiIdOrUserData_Edentiti(attr: JsObject, identifier: String) = {
    // If edentitiId then return it, otherwise requestUserData
    curTransactionId map { implicit transactionId =>
      val edentitiIdOpt = (attr \ DATA \ Attr.USER_ID).asOpt[String]

      edentitiIdOpt map { edentitiId =>
        // edentitiId found, return it
        val transformer =
          (__ \ TRANSACTION_ID).json.pickBranch and
            (__ \ EDENTITI_ID).json.copyFrom((__ \ DATA \ Attr.USER_ID).json.pick) reduce

        attr.transform(transformer) map { replyAttr =>
          replyToTransaction(EventDomain.USER, EventType.EDENTITI_ID, replyAttr)
        } getOrElse { log.error("Unable to transform attributes: ({})", curEvent) }
      } getOrElse {
        // No edentitiId, get all userData instead
        PDS.retrieveAllItems(Some(Attr.USER), None, Some(identifier))
      }
    } getOrElse { log.error("No '{}' so we can't handle this ({})", TRANSACTION_ID, curEvent) }
  }

}