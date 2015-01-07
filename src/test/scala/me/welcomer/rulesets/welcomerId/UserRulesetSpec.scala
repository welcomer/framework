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

import scala.language.postfixOps
import akka.actor.actorRef2Scala
import akka.testkit.TestActorRef
import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.PicoRulesetDSLMock
import me.welcomer.framework.testUtils.WelcomerFrameworkActorTest
import me.welcomer.framework.testUtils.WelcomerFrameworkTestProbe
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import me.welcomer.rulesets.PdsResponseMock
import me.welcomer.rulesets.PdsTestHelpers
import me.welcomer.framework.pico.EventedMessage

abstract class UserRulesetMock extends UserRuleset(null) with PicoRulesetDSLMock with PdsResponseMock {
  import me.welcomer.rulesets.welcomerId.UserRulesetSchema._
  import me.welcomer.rulesets.welcomerId.WelcomerIdSchema._

  protected def mockEventReply: PartialFunction[EventedMessage, Unit] = {
    case event @ EventedEvent("PDS", "RetrieveAllItems", _, attributes, _) => pdsRetrieveAllItemsMock(event)
    case event @ EventedEvent(_, _, _, _, None) => raisePicoEventMock(event)
    case event => {
      log.debug("Unmocked event: {}", event)

      sendEventToTestRef(event)
    }
  }

  def pdsRetrieveAllItemsMock(event: EventedEvent): Unit = {
    val namespaceOpt = (event.attributes \ "itemNamespace").asOpt[String]
    val identifierOpt = (event.attributes \ "identifier").asOpt[String]

    val replyEvent = (namespaceOpt, identifierOpt) match {
      case (Some(Attr.VENDOR), _) => {
        val items = Json.obj("vendor" -> "namespace", "foo" -> "bar")

        retrieveAllItemsMock(items, event.attributes)
      }
      case (Some(Attr.USER), Some(identifier)) if identifier == EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA => {
        val items = Json.obj("user" -> "data", "goes" -> "here")

        retrieveAllItemsSuccessMock(items, event.attributes) // Ignore force fail tag
      }
      case (Some(Attr.USER), _) => {
        val items = Json.obj("user" -> "namespace", "foo" -> "bar")

        retrieveAllItemsMock(items, event.attributes)
      }
      case (Some(Attr.EDENTITI), Some(identifier)) if identifier == EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA => {
        val items = Json.obj("userId" -> "some-test-edentitiId")

        retrieveAllItemsMock(items, event.attributes)
      }
      case (Some(Attr.EDENTITI), _) => {
        val items = Json.obj("edentiti" -> "namespace", "foo" -> "bar")

        retrieveAllItemsMock(items, event.attributes)
      }
      case _ => retrieveAllItemsFailureMock(s"Namespace not mocked: $namespaceOpt", event.attributes)
    }

    self ! replyEvent
  }
}

/**
 * Tests for [[me.welcomer.framework.pico.rulesets.welcomerId.UserRuleset]]
 */
class UserRulesetSpec extends WelcomerFrameworkActorTest with PdsTestHelpers {
  import UserRulesetSchema._
  import WelcomerIdSchema._

  //  val componentRegistry = new DefaultComponentRegistryImpl(system, Settings()) // TODO: Implement a 'test friendly' wiring of this
  //  val pdsService: PicoPdsServiceComponent#PicoPdsService = componentRegistry.picoPdsService("fooUUID")

  //  http://www.scalatest.org/user_guide/sharing_fixtures#loanFixtureMethods
  def withUserRuleset(testCode: (WelcomerFrameworkTestProbe, WelcomerFrameworkTestProbe, TestActorRef[UserRulesetMock]) => Any) = {
    val responseWatcher = WelcomerFrameworkTestProbe()
    val picoEventWatcher = WelcomerFrameworkTestProbe()

    val userRuleset = TestActorRef(
      new UserRulesetMock {
        val responseWatcherRef = responseWatcher.ref
        val picoEventWatcherRef = picoEventWatcher.ref
      }, "UserRuleset")

    testCode(responseWatcher, picoEventWatcher, userRuleset)

    userRuleset ! akka.actor.PoisonPill // Probably not needed since we tear down the entire ActorSystem
  }

  "UserRuleset" should {

    "react to 'created' by locally raising 'vendorDataAvailable' and 'userDataAvailable'" in {
      withUserRuleset { (responseWatcher, picoEventWatcher, userRuleset) =>
        val testAttr = Json.obj(
          "transactionId" -> "the-transaction-id",
          "vendor" -> Json.obj(
            "vendorId" -> "the-vendor-id",
            "vendorEci" -> "the-vendor-eci"),
          "user" -> Json.obj(
            "foo" -> "bar"))
        val event = EventedEvent(EventDomain.USER, EventType.CREATED, attributes = testAttr)

        userRuleset.tell(event, responseWatcher.ref)

        picoEventWatcher.expectNMsgPF(2) {
          case event @ EventedEvent(EventDomain.USER, EventType.VENDOR_DATA_AVAILABLE, _, _, _) => event
          case event @ EventedEvent(EventDomain.USER, EventType.USER_DATA_AVAILABLE, _, _, _) => event
        }

        responseWatcher.expectNoMsg
      }
    }

    "react to 'userDataAvailable' by locally raising 'PDS:StoreAllItems' with User namespace" in {
      withUserRuleset { (responseWatcher, picoEventWatcher, userRuleset) =>
        val testAttr = Json.obj(
          "transactionId" -> "the-transaction-id",
          "data" -> Json.obj(
            "foo" -> "bar"))
        val event = EventedEvent(EventDomain.USER, EventType.USER_DATA_AVAILABLE, attributes = testAttr)

        userRuleset.tell(event, responseWatcher.ref)

        def checkAttr(attr: JsObject): Boolean = isNamespace(Attr.USER, attr)

        picoEventWatcher.expectNMsgPF(1) {
          case event @ EventedEvent("PDS", "StoreAllItems", _, attributes, _) if checkAttr(attributes) => event
        }

        responseWatcher.expectNoMsg
      }
    }

    "react to 'vendorDataAvailable' by locally raising 'PDS:StoreAllItems' with Vendor namespace" in {
      withUserRuleset { (responseWatcher, picoEventWatcher, userRuleset) =>
        val testAttr = Json.obj(
          "transactionId" -> "the-transaction-id",
          "data" -> Json.obj(
            "foo" -> "bar"))
        val event = EventedEvent(EventDomain.USER, EventType.VENDOR_DATA_AVAILABLE, attributes = testAttr)

        userRuleset.tell(event, responseWatcher.ref)

        def checkAttr(attr: JsObject): Boolean = isNamespace(Attr.VENDOR, attr)

        picoEventWatcher.expectNMsgPF(1) {
          case event @ EventedEvent("PDS", "StoreAllItems", _, attributes, _) if checkAttr(attributes) => event
        }

        responseWatcher.expectNoMsg
      }
    }

    "react to 'edentitiDataAvailable' by locally raising 'PDS:StoreAllItems' with Edentiti namespace" in {
      withUserRuleset { (responseWatcher, picoEventWatcher, userRuleset) =>
        val testAttr = Json.obj(
          "transactionId" -> "the-transaction-id",
          "data" -> Json.obj(
            "foo" -> "bar"))
        val event = EventedEvent(EventDomain.USER, EventType.EDENTITI_DATA_AVAILABLE, attributes = testAttr)

        userRuleset.tell(event, responseWatcher.ref)

        def checkAttr(attr: JsObject): Boolean = isNamespace(Attr.EDENTITI, attr)

        picoEventWatcher.expectNMsgPF(1) {
          case event @ EventedEvent("PDS", "StoreAllItems", _, attributes, _) if checkAttr(attributes) => event
        }

        responseWatcher.expectNoMsg
      }
    }

    "respond to 'retrieveUserData' with 'userData' (via local 'PDS::RetrieveAllItems' with User namespace)" in {
      withUserRuleset { (responseWatcher, picoEventWatcher, userRuleset) =>
        val testAttr = Json.obj(
          "transactionId" -> "the-transaction-id",
          //          "filter" -> Json.arr("keys", "to", "filter", "by?"),
          "replyTo" -> "this-eci")

        val event = EventedEvent(EventDomain.USER, EventType.RETRIEVE_USER_DATA, attributes = testAttr)

        userRuleset.tell(event, responseWatcher.ref)

        def checkAttr(attr: JsObject): Boolean = isNamespace(Attr.USER, attr)

        picoEventWatcher.expectNMsgPF(1) {
          case event @ EventedEvent("PDS", "RetrieveAllItems", _, attributes, _) if checkAttr(attributes) => event
        }

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.USER, EventType.USER_DATA, _, _, _) => event
        }
      }
    }

    "respond to 'retrieveEdentitiData' with 'edentitiData' (via local 'PDS::RetrieveAllItems' with Edentiti namespace)" in {
      withUserRuleset { (responseWatcher, picoEventWatcher, userRuleset) =>
        val testAttr = Json.obj(
          "transactionId" -> "the-transaction-id",
          //          "filter" -> Json.arr("keys", "to", "filter", "by?"),
          "replyTo" -> "this-eci")

        val event = EventedEvent(EventDomain.USER, Event.RETRIEVE_EDENTITI_DATA, attributes = testAttr)

        userRuleset.tell(event, responseWatcher.ref)

        def checkAttr(attr: JsObject): Boolean = isNamespace(Attr.EDENTITI, attr)

        picoEventWatcher.expectNMsgPF(1) {
          case event @ EventedEvent("PDS", "RetrieveAllItems", _, attributes, _) if checkAttr(attributes) => event
        }

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.USER, Event.EDENTITI_DATA, _, _, _) => event
        }
      }
    }

    "respond to 'retrieveEdentitiIdOrUserData' with 'edentitiId' when edentitiId exists (via local 'PDS::RetrieveAllItems')" in {
      withUserRuleset { (responseWatcher, picoEventWatcher, userRuleset) =>
        val testAttr = Json.obj(
          "transactionId" -> "the-transaction-id",
          "replyTo" -> "this-eci")

        val event = EventedEvent(EventDomain.USER, EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA, attributes = testAttr)

        userRuleset.tell(event, responseWatcher.ref)

        def checkAttr(attr: JsObject): Boolean = isNamespace(Attr.EDENTITI, attr)

        picoEventWatcher.expectNMsgPF(1) {
          case event @ EventedEvent("PDS", "RetrieveAllItems", _, attributes, _) if checkAttr(attributes) => event
        }

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.USER, EventType.EDENTITI_ID, _, _, _) => event
        }
      }
    }

    "respond to 'retrieveEdentitiIdOrUserData' with 'edentitiNewUserData' when edentitiId doesn't exist (via local 'PDS::RetrieveAllItems')" in {
      withUserRuleset { (responseWatcher, picoEventWatcher, userRuleset) =>
        val smuggleTestTag = smugglePdsTestTag((PdsTestTag.FORCE_ERROR, "[Force] Item not found"))

        val testAttr = Json.obj(
          "transactionId" -> smuggleTestTag,
          "replyTo" -> "this-eci")

        val event = EventedEvent(EventDomain.USER, EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA, attributes = testAttr)

        userRuleset.tell(event, responseWatcher.ref)

        def checkAttr(attr: JsObject): Boolean = isNamespace(Attr.EDENTITI, attr) || isNamespace(Attr.USER, attr)

        picoEventWatcher.expectNMsgPF(2) {
          case event @ EventedEvent("PDS", "RetrieveAllItems", _, attributes, _) if checkAttr(attributes) => event
        }

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.USER, EventType.EDENTITI_NEW_USER_DATA, _, _, _) => event
        }
      }
    }

  }
}