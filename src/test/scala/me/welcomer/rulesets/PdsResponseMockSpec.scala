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
import me.welcomer.framework.testUtils.WelcomerFrameworkTest
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper

class PdsResponseMockSpec extends WelcomerFrameworkTest with PdsTestHelpers {
  val pdsResponseMock = new PdsResponseMock {}

  "PdsTestHelpers::addPdsTestTag" should {
    "correctly add a tag to an object" in {
      val tag = "testTag"
      val tagValue = "testValue"
      val previousJson = Json.obj("foo" -> "bar")
      val validJson = previousJson ++ Json.obj(PdsTestTag._CONTAINER -> Json.obj(tag -> tagValue))

      val testJson = addPdsTestTag(tag, tagValue, previousJson)

      assert(testJson == validJson)
    }
  }

  "PdsTestHelpers::checkPdsTestTag" should {
    "not find a tag in an empty object" in {
      val tag = "testTag"
      val checkOpt = checkPdsTestTag(tag, Json.obj())

      assert(checkOpt.isEmpty)
    }

    "find valid tag and return the correct value" in {
      val tag = "testTag"
      val tagValue = "testValue"
      val attr = Json.obj(PdsTestTag._CONTAINER -> Json.obj(tag -> tagValue))

      val checkOpt = checkPdsTestTag(tag, attr)

      assert(checkOpt.isDefined)
      assert(checkOpt.get == tagValue)
    }

    "find valid smuggled tag and return the correct value" in {
      val tag = "testTag"
      val tagValue = "testValue"

      val smuggleTag = "transactionId"
      val smuggleJson = Json.obj(PdsTestTag._CONTAINER -> Json.obj(tag -> tagValue))

      val attr = Json.obj(smuggleTag -> Json.stringify(smuggleJson))

      val checkOpt = checkPdsTestTag(tag, attr, Some(smuggleTag))

      assert(checkOpt.isDefined)
      assert(checkOpt.get == tagValue)
    }
  }

  "PdsTestHelpers::checkPdsTestForceError" should {
    "not find a tag in an empty object" in {
      val tag = PdsTestTag.FORCE_ERROR
      val checkOpt = checkPdsTestTag(tag, Json.obj())

      assert(checkOpt.isEmpty)
    }

    "find valid tag and return the correct value" in {
      val tag = PdsTestTag.FORCE_ERROR
      val tagValue = "Test Error Message"
      val attr = Json.obj(PdsTestTag._CONTAINER -> Json.obj(tag -> tagValue))

      val checkOpt = checkPdsTestTag(tag, attr)

      assert(checkOpt.isDefined)
      assert(checkOpt.get == tagValue)
    }
  }

  "PdsTestHelpers::stripAllPdsTestTags" should {
    "correctly strip all test tags" in {
      val attr = Json.obj(PdsTestTag._CONTAINER -> Json.obj("foo" -> "bar"))

      val strippedAttr = stripAllPdsTestTags(attr)

      assert(strippedAttr == Json.obj())
    }
  }

  "PdsTestHelpers::stripPdsTestTag" should {
    "correctly strip supplied test tag" in {
      val tag = "testTag"
      val tagValue = "testValue"

      val testTag = Json.obj(tag -> tagValue)
      val otherTags = Json.obj("foo" -> "bar")

      val attr = Json.obj(PdsTestTag._CONTAINER -> (testTag ++ otherTags))
      val validAttr = Json.obj(PdsTestTag._CONTAINER -> (otherTags))

      val strippedAttr = stripPdsTestTag(tag, attr)

      assert(strippedAttr == validAttr)
    }
  }

  "PdsTestHelpers::isNamespace" should {
    val namespace = "testNamespace"

    "match valid namespace" in {
      val attr = Json.obj("itemNamespace" -> namespace)

      assert(isNamespace(namespace, attr))
    }

    "not match different namespace" in {
      val attr = Json.obj("itemNamespace" -> "SomeOtherNamespace")

      assert(!isNamespace(namespace, attr))
    }

    "not match missing namespace" in {
      val attr = Json.obj()

      assert(!isNamespace(namespace, attr))
    }
  }

  "PdsResponseMock::createPdsEventMock" should {
    "correctly construct the EventedEvent" in {
      val eventType = "testEventType"
      val attr = Json.obj()

      val testEvent = pdsResponseMock.createPdsEventMock(eventType, attr)

      val validEvent = EventedEvent("PDS", eventType, timestamp = testEvent.timestamp, attributes = attr)

      assert(testEvent == validEvent)
    }

    "not contain any test tags in the constructed event attributes" in {
      val eventType = "testEventType"
      val attr = addPdsTestTag("testTag", "testValue", Json.obj())

      val testEvent = pdsResponseMock.createPdsEventMock(eventType, attr)

      val validEvent = EventedEvent("PDS", eventType, timestamp = testEvent.timestamp, attributes = Json.obj())

      assert(testEvent == validEvent)
    }
  }

  "PdsResponseMock::createPdsFailureEventMock" should {
    "correctly construct the failure EventedEvent" in {
      val eventType = "testFailureEventType"
      val errorMsg = "Test Error Message"
      val attr = Json.obj()

      val testEvent = pdsResponseMock.createPdsFailureEventMock(eventType, errorMsg, attr)

      val validEvent = EventedEvent("PDS", eventType, timestamp = testEvent.timestamp, attributes = Json.obj("errorMsg" -> errorMsg))

      assert(testEvent == validEvent)
    }
  }

  "PdsResponseMock::storeItem*Mock" should {
    "default to Success" in {
      val mockEvent = pdsResponseMock.storeItemMock(Json.obj())

      assert(mockEvent.eventType == "StoreItemSuccess")
    }

    "be Failure when FORCE_ERROR tag present" in {
      val errorMsg = "Test Error Message"
      val attr = addPdsTestTag(PdsTestTag.FORCE_ERROR, errorMsg, Json.obj())

      val mockEvent = pdsResponseMock.storeItemMock(attr)
      val errorMsgOpt = (mockEvent.attributes \ "errorMsg").asOpt[String]

      assert(mockEvent.eventType == "StoreItemFailure")
      assert(errorMsgOpt == Some(errorMsg))
    }

    "be Success when storeItemSuccessMock" in {
      val mockEvent = pdsResponseMock.storeItemSuccessMock(Json.obj())

      assert(mockEvent.eventType == "StoreItemSuccess")
    }

    "be Failure when storeItemFailureMock" in {
      val errorMsg = "Test Error Message"
      val mockEvent = pdsResponseMock.storeItemFailureMock(errorMsg, Json.obj())

      assert(mockEvent.eventType == "StoreItemFailure")
    }
  }

  "PdsResponseMock::storeAllItems*Mock" should {
    "default to Success" in {
      val mockEvent = pdsResponseMock.storeAllItemsMock(Json.obj())

      assert(mockEvent.eventType == "StoreItemSuccess")
    }

    "be Failure when FORCE_ERROR tag present" in {
      val errorMsg = "Test Error Message"
      val attr = addPdsTestTag(PdsTestTag.FORCE_ERROR, errorMsg, Json.obj())

      val mockEvent = pdsResponseMock.storeAllItemsMock(attr)
      val errorMsgOpt = (mockEvent.attributes \ "errorMsg").asOpt[String]

      assert(mockEvent.eventType == "StoreItemFailure")
      assert(errorMsgOpt == Some(errorMsg))
    }

    "be Success when storeAllItemsSuccessMock" in {
      val mockEvent = pdsResponseMock.storeAllItemsSuccessMock(Json.obj())

      assert(mockEvent.eventType == "StoreItemSuccess")
    }

    "be Failure when storeAllItemsFailureMock" in {
      val errorMsg = "Test Error Message"
      val mockEvent = pdsResponseMock.storeAllItemsFailureMock(errorMsg, Json.obj())
      val errorMsgOpt = (mockEvent.attributes \ "errorMsg").asOpt[String]

      assert(mockEvent.eventType == "StoreItemFailure")
      assert(errorMsgOpt == Some(errorMsg))
    }
  }

  "PdsResponseMock::retrieveItem*Mock" should {
    "default to Success" in {
      val mockData = ("testKey", "testValue")
      val mockEvent = pdsResponseMock.retrieveItemMock(mockData, Json.obj())

      assert(mockEvent.eventType == "RetrieveItemSuccess")
    }

    "be Failure when FORCE_ERROR tag present" in {
      val errorMsg = "Test Error Message"
      val attr = addPdsTestTag(PdsTestTag.FORCE_ERROR, errorMsg, Json.obj())
      val mockData = ("testKey", "testValue")

      val mockEvent = pdsResponseMock.retrieveItemMock(mockData, attr)
      val errorMsgOpt = (mockEvent.attributes \ "errorMsg").asOpt[String]

      assert(mockEvent.eventType == "RetrieveItemFailure")
      assert(errorMsgOpt == Some(errorMsg))
    }

    "be Success when retrieveItemSuccessMock" in {
      val mockData = ("testKey", "testValue")
      val mockDataJson = Json.obj(mockData._1 -> mockData._2)

      val mockEvent = pdsResponseMock.retrieveItemSuccessMock(mockData, Json.obj())
      val mockDataJsonOpt = (mockEvent.attributes \ "data").asOpt[JsObject]

      assert(mockEvent.eventType == "RetrieveItemSuccess")
      assert(mockDataJsonOpt == Some(mockDataJson))
    }

    "be Failure when retrieveItemFailureMock" in {
      val errorMsg = "Test Error Message"
      val mockEvent = pdsResponseMock.retrieveItemFailureMock(errorMsg, Json.obj())
      val errorMsgOpt = (mockEvent.attributes \ "errorMsg").asOpt[String]

      assert(mockEvent.eventType == "RetrieveItemFailure")
      assert(errorMsgOpt == Some(errorMsg))
    }
  }

  "PdsResponseMock::retrieveAllItems*Mock" should {
    "default to Success" in {
      val mockData = Json.obj("testKey" -> "testValue")
      val mockEvent = pdsResponseMock.retrieveAllItemsMock(mockData, Json.obj())

      assert(mockEvent.eventType == "RetrieveItemSuccess")
    }

    "be Failure when FORCE_ERROR tag present" in {
      val errorMsg = "Test Error Message"
      val attr = addPdsTestTag(PdsTestTag.FORCE_ERROR, errorMsg, Json.obj())
      val mockData = Json.obj("testKey" -> "testValue")

      val mockEvent = pdsResponseMock.retrieveAllItemsMock(mockData, attr)
      val errorMsgOpt = (mockEvent.attributes \ "errorMsg").asOpt[String]

      assert(mockEvent.eventType == "RetrieveItemFailure")
      assert(errorMsgOpt == Some(errorMsg))
    }

    "be Success when retrieveAllItemsSuccessMock" in {
      val mockData = Json.obj("testKey" -> "testValue")

      val mockEvent = pdsResponseMock.retrieveAllItemsSuccessMock(mockData, Json.obj())
      val mockDataOpt = (mockEvent.attributes \ "data").asOpt[JsObject]

      assert(mockEvent.eventType == "RetrieveItemSuccess")
      assert(mockDataOpt == Some(mockData))
    }

    "be Failure when retrieveAllItemsFailureMock" in {
      val errorMsg = "Test Error Message"
      val mockEvent = pdsResponseMock.retrieveAllItemsFailureMock(errorMsg, Json.obj())
      val errorMsgOpt = (mockEvent.attributes \ "errorMsg").asOpt[String]

      assert(mockEvent.eventType == "RetrieveItemFailure")
      assert(errorMsgOpt == Some(errorMsg))
    }
  }

  "PdsResponseMock::removeItem*Mock" should {
    "default to Success" in {
      val mockEvent = pdsResponseMock.removeItemMock(Json.obj())

      assert(mockEvent.eventType == "RemoveItemSuccess")
    }

    "be Failure when FORCE_ERROR tag present" in {
      val errorMsg = "Test Error Message"
      val attr = addPdsTestTag(PdsTestTag.FORCE_ERROR, errorMsg, Json.obj())

      val mockEvent = pdsResponseMock.removeItemMock(attr)
      val errorMsgOpt = (mockEvent.attributes \ "errorMsg").asOpt[String]

      assert(mockEvent.eventType == "RemoveItemFailure")
      assert(errorMsgOpt == Some(errorMsg))
    }

    "be Success when removeItemSuccessMock" in {
      val mockEvent = pdsResponseMock.removeItemSuccessMock(Json.obj())

      assert(mockEvent.eventType == "RemoveItemSuccess")
    }

    "be Failure when removeItemFailureMock" in {
      val errorMsg = "Test Error Message"
      val mockEvent = pdsResponseMock.removeItemFailureMock(errorMsg, Json.obj())
      val errorMsgOpt = (mockEvent.attributes \ "errorMsg").asOpt[String]

      assert(mockEvent.eventType == "RemoveItemFailure")
      assert(errorMsgOpt == Some(errorMsg))
    }
  }

}
