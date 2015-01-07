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

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.testkit.TestActorRef
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import me.welcomer.framework.PicoServicesComponentRegistryMockImpl
import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.PicoRulesetDSLMock
import me.welcomer.framework.pico.repository.PicoPdsRepositoryComponent
import me.welcomer.framework.pico.service.PicoPdsServiceComponentMockImpl
import me.welcomer.framework.pico.service.PicoServicesComponent
import me.welcomer.framework.testUtils.WelcomerFrameworkActorTest
import me.welcomer.framework.testUtils.WelcomerFrameworkTestProbe
import play.api.libs.json.JsValue
import akka.actor.ActorSystem
import me.welcomer.framework.pico.EventedMessage

/**
 * Tests for [[me.welcomer.framework.pico.rulesets.welcomerId.VendorRuleset]]
 */
class VendorRulesetSpec extends WelcomerFrameworkActorTest {
  import WelcomerIdSchema._

  def outerSystem = system
  object ComponentRegistryMock
    extends PicoServicesComponentRegistryMockImpl
    with VendorPicoPdsServiceComponentMockImpl {
    implicit val system: ActorSystem = outerSystem
  }

  //  http://www.scalatest.org/user_guide/sharing_fixtures#loanFixtureMethods
  def withVendorRuleset(identifier: String)(testCode: (WelcomerFrameworkTestProbe, WelcomerFrameworkTestProbe, TestActorRef[VendorRulesetMock]) => Any) = {
    val responseWatcher = WelcomerFrameworkTestProbe()
    val picoEventWatcher = WelcomerFrameworkTestProbe()

    val vendorRuleset = TestActorRef(
      new VendorRulesetMock(ComponentRegistryMock.picoServices(identifier)) {
        val responseWatcherRef = responseWatcher.ref
        val picoEventWatcherRef = picoEventWatcher.ref
      }, "VendorRuleset")

    testCode(responseWatcher, picoEventWatcher, vendorRuleset)

    vendorRuleset ! akka.actor.PoisonPill // Probably not needed since we tear down the entire ActorSystem
  }

  def unknownUserTester(
    eventDomain: String,
    eventType: String,
    attr: JsObject,
    canRespond: Boolean,
    vendorRuleset: TestActorRef[VendorRulesetMock],
    responseWatcher: WelcomerFrameworkTestProbe,
    picoEventWatcher: WelcomerFrameworkTestProbe) = {

    vendorRuleset.tell(
      EventedEvent(
        eventDomain,
        eventType,
        attributes = attr),
      responseWatcher.ref)

    picoEventWatcher.expectNoMsg

    if (canRespond) {
      responseWatcher.expectNMsgPF(1) {
        case event @ EventedEvent(EventDomain.WELCOMER_ID, EventType.UNKNOWN_USER, _, _, _) => event
      }
    } else {
      responseWatcher.expectNoMsg
    }
  }

  "VendorRuleset" should {

    "handle 'vendor::initialiseUser' with new user" in {
      withVendorRuleset("UnknownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>

        val event = EventedEvent(
          EventDomain.VENDOR,
          EventType.INITIALISE_USER,
          attributes = InitialiseUser(
            "transaction-id",
            ChannelDetails("new", "channel")))

        vendorRuleset.tell(event, responseWatcher.ref)

        picoEventWatcher.expectNoMsg

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.USER, EventType.CREATED, _, _, _) => event
        }
      }
    }

    "handle 'vendor::initialiseUser' with existing user" in {
      withVendorRuleset("KnownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>

        val event = EventedEvent(
          EventDomain.VENDOR,
          EventType.INITIALISE_USER,
          attributes = InitialiseUser(
            "transaction-id",
            ChannelDetails("valid", "channel")))

        vendorRuleset.tell(event, responseWatcher.ref)

        picoEventWatcher.expectNoMsg

        responseWatcher.expectNoMsg
      }
    }

    "handle 'vendor::retrieveUserData' with 'UnknownUser'" in {
      withVendorRuleset("UnknownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>
        unknownUserTester(
          EventDomain.VENDOR,
          EventType.RETRIEVE_USER_DATA,
          VendorRetrieveUserData(
            "transaction-id",
            ChannelDetails("invalid", "channel"),
            Nil,
            "some-replyTo-eci"),
          canRespond = true,
          vendorRuleset,
          responseWatcher,
          picoEventWatcher)
      }
    }

    "handle 'vendor::retrieveUserData' with 'KnownUser'" in {
      withVendorRuleset("KnownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>

        val event = EventedEvent(
          EventDomain.VENDOR,
          EventType.RETRIEVE_USER_DATA,
          attributes = VendorRetrieveUserData(
            "transaction-id",
            ChannelDetails("valid", "channel"),
            Nil,
            "some-replyTo-eci"))

        vendorRuleset.tell(event, responseWatcher.ref)

        picoEventWatcher.expectNoMsg

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.USER, EventType.RETRIEVE_USER_DATA, _, _, _) => event
        }
      }
    }

    "handle 'vendor::userDataAvailable' with 'UnknownUser'" in {
      withVendorRuleset("UnknownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>
        unknownUserTester(
          EventDomain.VENDOR,
          EventType.USER_DATA_AVAILABLE,
          VendorUserDataAvailable(
            "transaction-id",
            ChannelDetails("invalid", "channel"),
            Json.obj()),
          canRespond = false,
          vendorRuleset,
          responseWatcher,
          picoEventWatcher)
      }
    }

    "handle 'vendor::userDataAvailable' with 'KnownUser'" in {
      withVendorRuleset("KnownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>

        val event = EventedEvent(
          EventDomain.VENDOR,
          EventType.USER_DATA_AVAILABLE,
          attributes = VendorUserDataAvailable(
            "transaction-id",
            ChannelDetails("valid", "channel"),
            Json.obj()))

        vendorRuleset.tell(event, responseWatcher.ref)

        picoEventWatcher.expectNoMsg

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.USER, EventType.USER_DATA_AVAILABLE, _, _, _) => event
        }
      }
    }

    "handle 'vendor::retrieveEdentitiIdOrNewUserData' with 'UnknownUser'" in {
      withVendorRuleset("UnknownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>
        unknownUserTester(
          EventDomain.VENDOR,
          EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA,
          VendorRetrieveEdentitiIdOrNewUserData(
            "transaction-id",
            ChannelDetails("invalid", "channel"),
            "some-replyTo-string"),
          canRespond = true,
          vendorRuleset,
          responseWatcher,
          picoEventWatcher)
      }
    }

    "handle 'vendor::retrieveEdentitiIdOrNewUserData' with 'KnownUser'" in {
      withVendorRuleset("KnownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>

        val event = EventedEvent(
          EventDomain.VENDOR,
          EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA,
          attributes = VendorRetrieveEdentitiIdOrNewUserData(
            "transaction-id",
            ChannelDetails("valid", "channel"),
            "some-replyTo-string"))

        vendorRuleset.tell(event, responseWatcher.ref)

        picoEventWatcher.expectNoMsg

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.USER, EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA, _, _, _) => event
        }
      }
    }

    "handle 'vendor::edentitiNewUser' with 'UnknownUser'" in {
      withVendorRuleset("UnknownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>
        unknownUserTester(
          EventDomain.VENDOR,
          EventType.EDENTITI_NEW_USER,
          EdentitiNewUser(
            "transaction-id",
            ChannelDetails("invalid", "channel"),
            "edentiti-id",
            Json.obj()),
          canRespond = false,
          vendorRuleset,
          responseWatcher,
          picoEventWatcher)
      }
    }

    "handle 'vendor::edentitiNewUser' with 'KnownUser'" in {
      withVendorRuleset("KnownUser edentitiNewUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>

        val event = EventedEvent(
          EventDomain.VENDOR,
          EventType.EDENTITI_NEW_USER,
          attributes = EdentitiNewUser(
            "transaction-id",
            ChannelDetails("valid", "channel"),
            "some-edentiti-id",
            Json.obj()))

        vendorRuleset.tell(event, responseWatcher.ref)

        picoEventWatcher.expectNoMsg

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.USER, EventType.EDENTITI_DATA_AVAILABLE, _, _, _) => event
        }
      }
    }

    "handle 'vendor::userVerificationNotification' with 'UnknownUser'" in {
      withVendorRuleset("UnknownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>
        unknownUserTester(
          EventDomain.VENDOR,
          EventType.USER_VERIFICATION_NOTIFICATION,
          UserVerificationNotification(
            "transaction-id",
            ChannelDetails("invalid", "channel"),
            Json.obj()),
          canRespond = false,
          vendorRuleset,
          responseWatcher,
          picoEventWatcher)
      }
    }

    "handle 'vendor::userVerificationNotification' with 'KnownUser'" in {
      withVendorRuleset("KnownUser") { (responseWatcher, picoEventWatcher, vendorRuleset) =>

        val event = EventedEvent(
          EventDomain.VENDOR,
          EventType.USER_VERIFICATION_NOTIFICATION,
          attributes = UserVerificationNotification(
            "transaction-id",
            ChannelDetails("valid", "channel"),
            Json.obj()))

        vendorRuleset.tell(event, responseWatcher.ref)

        // TODO: Handle checking the email/callback notfication parts somehow? (only happens on 'verified' states)

        picoEventWatcher.expectNoMsg

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.USER, EventType.EDENTITI_DATA_AVAILABLE, _, _, _) => event
        }
      }
    }

    "handle 'user::userData'" in {
      withVendorRuleset("") { (responseWatcher, picoEventWatcher, vendorRuleset) =>

        val event = EventedEvent(
          EventDomain.USER,
          EventType.USER_DATA,
          attributes = UserData(
            "transaction-id",
            Nil,
            Json.obj()))

        vendorRuleset.underlyingActor.mapReplyToEci("transaction-id", Some("replyTo-eci"))

        vendorRuleset.tell(event, responseWatcher.ref)

        picoEventWatcher.expectNoMsg

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.WELCOMER_ID, EventType.USER_DATA, _, _, _) => event
        }
      }
    }

    "handle 'user::edentitiId'" in {
      withVendorRuleset("") { (responseWatcher, picoEventWatcher, vendorRuleset) =>

        val event = EventedEvent(
          EventDomain.USER,
          EventType.EDENTITI_ID,
          attributes = EdentitiId(
            "transaction-id",
            "edentiti-id"))

        vendorRuleset.underlyingActor.mapReplyToEci("transaction-id", Some("replyTo-eci"))

        vendorRuleset.tell(event, responseWatcher.ref)

        picoEventWatcher.expectNoMsg

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.WELCOMER_ID, EventType.EDENTITI_ID, _, _, _) => event
        }
      }
    }

    "handle 'user::edentitiNewUserData'" in {
      withVendorRuleset("") { (responseWatcher, picoEventWatcher, vendorRuleset) =>

        val event = EventedEvent(
          EventDomain.USER,
          EventType.EDENTITI_NEW_USER_DATA,
          attributes = EdentitiNewUserData(
            "transaction-id",
            Json.obj()))

        vendorRuleset.underlyingActor.mapReplyToEci("transaction-id", Some("replyTo-eci"))

        vendorRuleset.tell(event, responseWatcher.ref)

        picoEventWatcher.expectNoMsg

        responseWatcher.expectNMsgPF(1) {
          case event @ EventedEvent(EventDomain.WELCOMER_ID, EventType.EDENTITI_CREATE_NEW_USER, _, _, _) => event
        }
      }
    }

  }
}

trait VendorPicoPdsServiceComponentMockImpl extends PicoPdsServiceComponentMockImpl { self: PicoPdsRepositoryComponent =>
  override protected def _picoPdsService(identifier: String): PicoPdsService = new VendorPicoPdsServiceMockImpl(identifier)

  class VendorPicoPdsServiceMockImpl(val identifier: String) extends PicoPdsServiceMockImpl()(identifier) {
    import me.welcomer.rulesets.welcomerId.WelcomerIdSchema._

    override def retrieve(query: JsObject, projection: JsObject, namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[Option[JsObject]] = {
      (identifier, namespace) match {
        case ("UnknownUser", Some("user")) => {
          println(s"Mocking PDS retrieve: $identifier With: None")

          Future(None)
        }
        case ("KnownUser", Some("user")) => {
          val response = Json.obj(
            "mappings" -> Json.arr(
              UserPicoMapping(
                "some-user-eci",
                "some-vendor-eci",
                Set(ChannelDetails("valid", "channel")))))

          println(s"Mocking PDS retrieve: $identifier::$namespace With: ${response}")

          Future(Some(response))
        }
        case ("KnownUser edentitiNewUser", Some("user")) => {
          val userPicoMapping = Json.obj(
            "mappings" -> Json.arr(
              UserPicoMapping(
                "some-user-eci",
                "some-vendor-eci",
                Set(ChannelDetails("edentitiId", "some-edentiti-id")))))

          println(s"Mocking PDS retrieve: $identifier::$namespace With: ${userPicoMapping}")

          Future(Some(userPicoMapping))
        }
        case _ => println("Unmocked PDS retrieve: $identifier::$namespace"); ???
      }
    }

    override def retrieveAllItems(
      namespace: Option[String] = None,
      filter: Option[List[String]] = None)(implicit ec: ExecutionContext): Future[Option[JsObject]] = {
      (identifier, namespace) match {
        case (_, Some("vendorPreferences")) => {
          val response = Json.obj(
            "active" -> true,
            "redirectUrl" -> "",
            "redirectImmediately" -> false)

          println(s"Mocking PDS retrieve: $identifier::$namespace With: ${response}")

          Future(Some(response))
        }
        case _ => println("Unmocked PDS retrieveAll: $identifier::$namespace"); ???
      }
    }

    override def pushArrayItem(
      arrayKey: String,
      item: JsValue,
      namespace: Option[String] = None,
      selector: Option[JsObject] = None,
      unique: Boolean = false)(implicit ec: ExecutionContext): Future[JsObject] = {
      Future(Json.obj("n" -> 1))
    }

  }
}

abstract class VendorRulesetMock(picoServices: PicoServicesComponent#PicoServices) extends VendorRuleset(picoServices) with PicoRulesetDSLMock /*with PdsResponseMock*/ {
  import me.welcomer.rulesets.welcomerId.WelcomerIdSchema._

  protected def mockEventReply: PartialFunction[EventedMessage, Unit] = {
    case event @ EventedEvent(_, _, _, _, None) => raisePicoEventMock(event)
    case event => {
      log.debug("Unmocked event: {}", event)

      sendEventToTestRef(event)
    }
  }
}