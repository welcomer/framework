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

import me.welcomer.framework.pico.EventedEvent
import me.welcomer.utils.JsonUtils
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.Reads.JsObjectReads
import play.api.libs.json.Reads.StringReads
import play.api.libs.json.__

trait PdsTestHelpers {
  import me.welcomer.rulesets.PdsRulesetSchema._

  object PdsTestTag {
    val _CONTAINER = "_X-PDS-TEST-TAG"

    val FORCE_ERROR = "FORCE_ERROR" // FORCE_ERROR: "errorMsg"
  }

  def addPdsTestTag(tag: String, value: String, attr: JsObject): JsObject = {
    val transformer = (__ \ PdsTestTag._CONTAINER \ tag).json.put(Json.toJson(value))
    attr.transform(transformer) map { newAttr => attr ++ newAttr } getOrElse { attr }
  }

  def smugglePdsTestTags(tags: List[(String, String)]): String = {
    val smuggleJson = tags.foldLeft(Json.obj()) { (json, tag) => addPdsTestTag(tag._1, tag._2, json) }
    Json.stringify(smuggleJson)
  }

  def smugglePdsTestTag(tag: (String, String)): String = smugglePdsTestTags(List(tag))

  def checkPdsTestTag(tag: String, attr: JsObject, smuggleFieldNameOpt: Option[String] = Some("transactionId")): Option[String] = {
    val tagOpt = (attr \ PdsTestTag._CONTAINER \ tag).asOpt[String]

    tagOpt match {
      case tag @ Some(_) => tag
      case _ if smuggleFieldNameOpt.isDefined => {
        // Convoluted way to smuggle the test tags in a text field
        val smuggleFieldOpt = smuggleFieldNameOpt.flatMap { smuggleFieldName => (attr \ smuggleFieldName).asOpt[String] }
        val smuggleContainer = smuggleFieldOpt.flatMap { JsonUtils.parseOpt(_).flatMap { _.asOpt[JsObject] } }
        smuggleContainer.flatMap { container => (container \ PdsTestTag._CONTAINER \ tag).asOpt[String] }
      }
      case _ => None
    }
  }

  def checkPdsTestForceError(attr: JsObject): Option[String] = checkPdsTestTag(PdsTestTag.FORCE_ERROR, attr)

  def stripAllPdsTestTags(attr: JsObject): JsObject = {
    val transformer = (__ \ PdsTestTag._CONTAINER).json.prune
    attr.transform(transformer) map { strippedAttr => strippedAttr } getOrElse { attr }
  }

  def stripPdsTestTag(tag: String, attr: JsObject): JsObject = {
    val transformer = (__ \ PdsTestTag._CONTAINER \ tag).json.prune
    attr.transform(transformer) map { strippedAttr => strippedAttr } getOrElse { attr }
  }

  def isNamespace(namespace: String, attr: JsObject): Boolean = {
    (attr \ Attr.ITEM_NAMESPACE).asOpt[String] == Some(namespace)
  }
}

trait PdsResponseMock extends AnyRef with PdsTestHelpers {
  import me.welcomer.rulesets.PdsRuleset._;
  import me.welcomer.rulesets.PdsRulesetSchema._;

  /**
   * Helper to construct PDS [[me.welcomer.framework.pico.EventedEvent]]s
   *
   * @param eventType Type of the event
   * @param attributes Attributes for the event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def createPdsEventMock(eventType: String, attributes: JsObject): EventedEvent = {
    EventedEvent(
      Event.EVENT_DOMAIN,
      eventType,
      attributes = stripAllPdsTestTags(attributes))
  }

  /**
   * Helper to construct PDS failure [[me.welcomer.framework.pico.EventedEvent]]s
   *
   * @param eventType Type of the event
   * @param errorMsg Error message to use
   * @param attributes Attributes for the event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def createPdsFailureEventMock(eventType: String, errorMsg: String, attributes: JsObject): EventedEvent = {
    createPdsEventMock(eventType, attributes ++ Json.obj(Attr.ERROR_MSG -> errorMsg))
  }

  /**
   * Mock storeItem response (correctly handling forceError tag)
   *
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def storeItemMock(suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    checkPdsTestForceError(suppliedAttributes) match {
      case Some(errorMsg) => storeItemFailureMock(errorMsg, suppliedAttributes)
      case None           => storeItemSuccessMock(suppliedAttributes)
    }
  }

  /**
   * Mock storeItemSuccess response
   *
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def storeItemSuccessMock(suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    createPdsEventMock(StoreItemSuccess(), suppliedAttributes)
  }

  /**
   * Mock storeItemFailure response
   *
   * @param errorMsg Error message to use
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def storeItemFailureMock(errorMsg: String, suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    createPdsFailureEventMock(StoreItemFailure(), errorMsg, suppliedAttributes)
  }

  /**
   * Mock storeAllItems response (correctly handling forceError tag)
   *
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def storeAllItemsMock(suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    checkPdsTestForceError(suppliedAttributes) match {
      case Some(errorMsg) => storeAllItemsFailureMock(errorMsg, suppliedAttributes)
      case None           => storeAllItemsSuccessMock(suppliedAttributes)
    }
  }

  /**
   * Mock storeAllItemsSuccess response
   *
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def storeAllItemsSuccessMock(suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    storeItemSuccessMock(suppliedAttributes)
  }

  /**
   * Mock storeAllItemsFailure response
   *
   * @param errorMsg Error message to use
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def storeAllItemsFailureMock(errorMsg: String, suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    storeItemFailureMock(errorMsg, suppliedAttributes)
  }

  /**
   * Mock retrieveItem response (correctly handling forceError tag)
   *
   * @param mockData The mocked data to return
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def retrieveItemMock(mockData: (String, String), suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    checkPdsTestForceError(suppliedAttributes) match {
      case Some(errorMsg) => retrieveItemFailureMock(errorMsg, suppliedAttributes)
      case None           => retrieveItemSuccessMock(mockData, suppliedAttributes)
    }
  }

  /**
   * Mock retrieveItemSuccess response
   *
   * @param mockData The mocked data to return
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def retrieveItemSuccessMock(mockData: (String, String), suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    //    createPdsEventMock(RetrieveItemSuccess(), suppliedAttributes ++ Json.obj(Attr.DATA -> Json.obj(mockItem._1 -> mockItem._2)))
    retrieveAllItemsSuccessMock(Json.obj(mockData._1 -> mockData._2), suppliedAttributes)
  }

  /**
   * Mock retrieveItemFailure response
   *
   * @param errorMsg Error message to use
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def retrieveItemFailureMock(errorMsg: String, suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    createPdsFailureEventMock(RetrieveItemFailure(), errorMsg, suppliedAttributes)
  }

  /**
   * Mock retrieveAllItems response (correctly handling forceError tag)
   *
   * @param mockData The mocked data to return
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def retrieveAllItemsMock(mockData: JsObject, suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    checkPdsTestForceError(suppliedAttributes) match {
      case Some(errorMsg) => retrieveAllItemsFailureMock(errorMsg, suppliedAttributes)
      case None           => retrieveAllItemsSuccessMock(mockData, suppliedAttributes)
    }
  }

  /**
   * Mock retrieveAllItemsSuccess response
   *
   * @param mockData The mocked data to return
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def retrieveAllItemsSuccessMock(mockData: JsObject, suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    createPdsEventMock(RetrieveItemSuccess(), suppliedAttributes ++ Json.obj(Attr.DATA -> mockData))
  }

  /**
   * Mock retrieveAllItemsFailure response
   *
   * @param errorMsg Error message to use
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def retrieveAllItemsFailureMock(errorMsg: String, suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    //    createPdsFailureEventMock(RetrieveItemFailure(), errorMsg, suppliedAttributes)
    retrieveItemFailureMock(errorMsg, suppliedAttributes)
  }

  /**
   * Mock removeItem response (correctly handling forceError tag)
   *
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def removeItemMock(suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    checkPdsTestForceError(suppliedAttributes) match {
      case Some(errorMsg) => removeItemFailureMock(errorMsg, suppliedAttributes)
      case None           => removeItemSuccessMock(suppliedAttributes)
    }
  }

  /**
   * Mock removeItemSuccess response
   *
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def removeItemSuccessMock(suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    createPdsEventMock(RemoveItemSuccess(), suppliedAttributes)
  }

  /**
   * Mock removeItemFailure response
   *
   * @param errorMsg Error message to use
   * @param suppliedAttributes Attributes from the triggering event
   * @return The constructed [[me.welcomer.framework.pico.EventedEvent]] mock response
   */
  def removeItemFailureMock(errorMsg: String, suppliedAttributes: JsObject = Json.obj()): EventedEvent = {
    createPdsFailureEventMock(RemoveItemFailure(), errorMsg, suppliedAttributes)
  }

}
