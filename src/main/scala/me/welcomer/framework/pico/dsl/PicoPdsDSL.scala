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
package me.welcomer.framework.pico.dsl

import akka.actor.Actor
import akka.actor.ActorLogging

import play.api.libs.json._

import me.welcomer.framework.pico.EventedEvent
import me.welcomer.rulesets.PdsRuleset
import me.welcomer.rulesets.PdsRulesetSchema

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
