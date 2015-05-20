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
package me.welcomer.rulesets.personalCloud

import scala.concurrent.Future

import play.api.libs.json._

import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.EventedFunction
import me.welcomer.framework.pico.EventedResult
import me.welcomer.framework.pico.EventedSuccess
import me.welcomer.framework.pico.PicoRuleset
import me.welcomer.framework.pico.service.PicoServicesComponent

class PersonalCloudPds(protected val picoServices: PicoServicesComponent#PicoServices)
  extends PicoRuleset(picoServices)
  with PersonalCloudPdsHelper {

  import context._

  val EVENT_DOMAIN = "personalCloud"
  subscribeToEventDomain(EVENT_DOMAIN)

  override def externalFunctionSharing = true

  override def provideFunction = {
    case EventedFunction(_, "retrieveAll", args, _) => retrieveAll
    case EventedFunction(_, "retrieveItem", args, _) => {
      val key = (args \ "key").validate[String]
      key match {
        case JsSuccess(k, _) => retrieveItem(k)
        case JsError(e) => {
          log.error("[retrieveItem] No key provided")
          JsError(e)
        }
      }
    }
  }

  // TODO: Handle 'missing keys' in a more robust way (currently will probably just exception..)
  override def selectWhen = {
    case event @ EventedEvent(EVENT_DOMAIN, eventType, _, args, _) => {
      logEventInfo
      eventType match {
        case "storeAll" => storeAll(args)
        case "storeItem" => {
          val keyval = Json.fromJson[KeyValue](args)
          keyval match {
            case JsSuccess(kv, _) => storeItem(kv.key, kv.value)
            case JsError(e)       => log.error("[storeItem] Key/value pair was incomplete ({})", e)
          }
        }
        case "removeAll" => removeAll
        case "removeItem" => {
          val key = (args \ "key").validate[String]
          key match {
            case JsSuccess(k, _) => removeItem(k)
            case JsError(e) => {
              log.error("[removeItem] No key provided")
            }
          }
        }
      }
    }
  }

}

trait PersonalCloudPdsHelper { this: PersonalCloudPds =>
  import context._
  import me.welcomer.utils.Jsonable
  import play.api.libs.functional.syntax._

  // Events

  def storeAll(data: JsObject): Unit = {
    picoServices.pds.storeAllItemsWithJsObject(data)
  }

  def storeItem(key: String, value: JsValue): Unit = {
    picoServices.pds.storeItem(key, value)
  }

  def retrieveAll: Future[Option[EventedResult[JsObject]]] = {
    picoServices.pds.retrieveAllItems() map {
      case Some(data) => Some(EventedSuccess(data))
      case None       => Some(EventedSuccess(Json.obj()))
    }
  }

  def retrieveItem(key: String): Future[Option[EventedResult[JsObject]]] = {
    picoServices.pds.retrieveItem[JsObject](key, None) map {
      case Some(data) => Some(EventedSuccess(data))
      case None       => Some(EventedSuccess(Json.obj()))
    }
  }

  // Functions

  def removeAll: Unit = {
    picoServices.pds.removeAllItems()
  }

  def removeItem(key: String): Unit = {
    picoServices.pds.removeItem(key)
  }

  // Formats

  case class KeyValue(key: String, value: JsValue) extends Jsonable
  implicit lazy val keyValueFormat: Format[KeyValue] = (
    (__ \ "key").format[String] ~
    (__ \ "value").format[JsValue])(KeyValue.apply, unlift(KeyValue.unapply))
}
