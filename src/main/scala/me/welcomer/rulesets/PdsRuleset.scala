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
package me.welcomer.rulesets

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.PicoRuleset
import me.welcomer.framework.pico.service.PicoPdsServiceComponent
import me.welcomer.framework.pico.service.PicoServicesComponent
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper

// TODO: Refactor RetrieveItemSuccess to be the same for 1 or more items, and change itemKey/itemValue and items to data: {}

object PdsRulesetSchema {
  object Event {
    val EVENT_DOMAIN = "PDS"
  }

  object Attr {
    val ITEM_KEY = "itemKey"
    val ITEM_VALUE = "itemValue"
    val ITEM_NAMESPACE = "itemNamespace"

    val DATA = "data"

    val FILTER = "filter"

    val ERROR_MSG = "errorMsg"
  }
}
object PdsRuleset {

  // TODO: Refactor this into a 'schema' type object?
  // TODO: Do similar for keys?
  sealed class PdsEventType {
    def apply(): String = this.toString()
  }

  object PdsEventType {
    def apply(eventType: String): PdsEventType = {
      eventType match {
        case "StoreItem" => StoreItem
        case "StoreItemSuccess" => StoreItemSuccess
        case "StoreItemFailure" => StoreItemFailure

        case "StoreAllItems" => StoreAllItems

        case "RetrieveItem" => RetrieveItem
        case "RetrieveItemSuccess" => RetrieveItemSuccess
        case "RetrieveItemFailure" => RetrieveItemFailure

        case "RetrieveAllItems" => RetrieveAllItems

        case "RemoveItem" => RemoveItem
        case "RemoveItemSuccess" => RemoveItemSuccess
        case "RemoveItemFailure" => RemoveItemFailure

        case _ => UnknownEventType(eventType)
      }
    }
  }

  case class UnknownEventType(eventType: String) extends PdsEventType

  case object StoreItem extends PdsEventType
  case object StoreItemSuccess extends PdsEventType
  case object StoreItemFailure extends PdsEventType

  case object StoreAllItems extends PdsEventType

  case object RetrieveItem extends PdsEventType
  case object RetrieveItemSuccess extends PdsEventType
  case object RetrieveItemFailure extends PdsEventType

  case object RetrieveAllItems extends PdsEventType

  //  case object RetrieveAllNamespaces extends PdsEventType

  case object RemoveItem extends PdsEventType
  case object RemoveItemSuccess extends PdsEventType
  case object RemoveItemFailure extends PdsEventType

  //  case object RemoveAllItems extends PdsEventType
  //  case object RemoveAllNamespaces extends PdsEventType
}

class PdsRuleset(picoServices: PicoServicesComponent#PicoServices) extends PicoRuleset(picoServices) with PdsRulesetHelper {
  import context._
  import PdsRuleset._
  import PdsRulesetSchema._

  subscribeToLocalEventDomain(Event.EVENT_DOMAIN)

  implicit def pdsService = picoServices.pds

  def selectWhen = {
    case event @ EventedEvent(Event.EVENT_DOMAIN, eventType, _, attributes, _) => PdsEventType(eventType) match {
      case PdsRuleset.StoreItem => storeItem(attributes)
      case PdsRuleset.StoreAllItems => storeAllItems(attributes)
      case PdsRuleset.RetrieveItem => retrieveItem(attributes)
      case PdsRuleset.RetrieveAllItems => retrieveAllItems(attributes)
      case PdsRuleset.RemoveItem => removeItem(attributes)
      case _ => log.debug("Unhandled {} EventedEvent Received ({})", Event.EVENT_DOMAIN, event)
    }
  }

}

trait PdsRulesetHelper { self: PdsRuleset =>
  // TODO: Make this less hacky, decide on interface/attributes to do key/values/etc better
  import PdsRuleset._
  import PdsRulesetSchema._

  protected def raisePdsEvent(eventType: PdsEventType, attributes: JsObject): Unit = {
    raisePicoEvent(EventedEvent(Event.EVENT_DOMAIN, eventType.toString(), attributes = attributes))
  }

  protected def raisePdsFailureEvent(eventType: PdsEventType, errorMsg: String, attributes: JsObject): Unit = {
    raisePdsEvent(eventType, attributes ++ Json.obj(Attr.ERROR_MSG -> errorMsg))
  }

  protected def storeItem(attributes: JsObject)(implicit ec: ExecutionContext, pdsService: PicoPdsServiceComponent#PicoPdsService) = {
    val itemKey = (attributes \ Attr.ITEM_KEY).asOpt[String]
    val itemValue = (attributes \ Attr.ITEM_VALUE).asOpt[JsValue]
    val itemNamespace = (attributes \ Attr.ITEM_NAMESPACE).asOpt[String]

    var resultAttributes = attributes

    // Helpers
    def storeItemSuccess = raisePdsEvent(PdsRuleset.StoreItemSuccess, resultAttributes)
    def storeItemFailure(errorMsg: String) = raisePdsFailureEvent(PdsRuleset.StoreItemFailure, errorMsg, resultAttributes)

    if (itemKey.isDefined && itemValue.isDefined) {
      pdsService.storeItemWithJsValue(itemKey.get, itemValue.get, itemNamespace) onComplete {
        case Success(_) => storeItemSuccess
        case Failure(e) => storeItemFailure(e.getMessage())
      }
    } else {
      storeItemFailure(s"${Attr.ITEM_KEY} & ${Attr.ITEM_VALUE} are required fields")
    }
  }

  protected def storeAllItems(attributes: JsObject)(implicit ec: ExecutionContext, pdsService: PicoPdsServiceComponent#PicoPdsService) = {
    val data = (attributes \ Attr.DATA).asOpt[JsObject]
    val itemNamespace = (attributes \ Attr.ITEM_NAMESPACE).asOpt[String]

    // Helpers
    def storeItemSuccess = raisePdsEvent(PdsRuleset.StoreItemSuccess, attributes)
    def storeItemFailure(errorMsg: String) = raisePdsFailureEvent(PdsRuleset.StoreItemFailure, errorMsg, attributes)

    if (data.isDefined) {
      pdsService.storeAllItemsWithJsObject(data.get, itemNamespace) onComplete {
        case Success(_) => storeItemSuccess
        case Failure(e) => storeItemFailure(e.getMessage())
      }
    } else {
      storeItemFailure(s"${Attr.DATA} is a required field and must be a JSON Object")
    }
  }

  protected def retrieveItem(attributes: JsObject)(implicit ec: ExecutionContext, pdsService: PicoPdsServiceComponent#PicoPdsService) = {
    val itemKeyOpt = (attributes \ Attr.ITEM_KEY).asOpt[String]
    val itemNamespaceOpt = (attributes \ Attr.ITEM_NAMESPACE).asOpt[String]

    // Helpers
    def retrieveItemSuccess(item: (String, String)) = {
      raisePdsEvent(PdsRuleset.RetrieveItemSuccess, attributes ++ Json.obj(Attr.DATA -> Json.obj(item._1 -> item._2)))
    }
    def retrieveItemFailure(errorMsg: String) = raisePdsFailureEvent(PdsRuleset.RetrieveItemFailure, errorMsg, attributes)

    itemKeyOpt.map { itemKey =>
      pdsService.retrieveItem[String](itemKey, itemNamespaceOpt) onComplete {
        case Success(result) =>
          result map { itemValue =>
            retrieveItemSuccess((itemKey, itemValue))
          } getOrElse {
            retrieveItemFailure(s"Item '$itemKey' could not be found")
          }
        case Failure(e) => retrieveItemFailure(e.getMessage())
      }
    } getOrElse { retrieveItemFailure(s"${Attr.ITEM_KEY} is a required field") }
  }

  protected def retrieveAllItems(attributes: JsObject)(implicit ec: ExecutionContext, pdsService: PicoPdsServiceComponent#PicoPdsService) = {
    val itemNamespace = (attributes \ Attr.ITEM_NAMESPACE).asOpt[String]
    val filter = (attributes \ Attr.FILTER).asOpt[List[String]]

    // Helpers
    def retrieveItemSuccess(json: JsObject) = {
      raisePdsEvent(PdsRuleset.RetrieveItemSuccess, attributes ++ Json.obj(Attr.DATA -> json))
    }
    def retrieveItemFailure(errorMsg: String) = {
      raisePdsFailureEvent(PdsRuleset.RetrieveItemFailure, errorMsg, attributes)
    }

    pdsService.retrieveAllItems(itemNamespace, filter) onComplete {
      case Success(result) => result match {
        case Some(json) => retrieveItemSuccess(json)
        case None => retrieveItemFailure("Items could not be found")
      }
      case Failure(e) => retrieveItemFailure(e.getMessage())
    }
  }

  protected def removeItem(attributes: JsObject)(implicit ec: ExecutionContext, pdsService: PicoPdsServiceComponent#PicoPdsService) = {
    val itemKey = (attributes \ Attr.ITEM_KEY).asOpt[String]
    val itemNamespace = (attributes \ Attr.ITEM_NAMESPACE).asOpt[String]

    def removeItemSuccess = raisePdsEvent(PdsRuleset.RemoveItemSuccess, attributes)
    def removeItemFailure(errorMsg: String) = raisePdsFailureEvent(PdsRuleset.RemoveItemFailure, errorMsg, attributes)

    if (itemKey.isDefined) {
      pdsService.removeItem(itemKey.get, itemNamespace) onComplete {
        case Success(_) => removeItemSuccess
        case Failure(e) => removeItemFailure(e.getMessage())
      }
    } else {
      removeItemFailure(s"${Attr.ITEM_KEY} is a required field")
    }
  }
}
