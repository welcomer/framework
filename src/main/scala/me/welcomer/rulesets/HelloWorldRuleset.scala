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

import scala.language.postfixOps
import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.PicoRuleset
import me.welcomer.framework.pico.service.PicoServicesComponent
import play.api.libs.json._
import me.welcomer.framework.pico.EventedSuccess
import me.welcomer.framework.pico.EventedFailure
import me.welcomer.framework.pico.EventedResult
import me.welcomer.framework.pico.EventedFunction
import me.welcomer.framework.pico.BasicError

class HelloWorldRuleset(picoServices: PicoServicesComponent#PicoServices) extends PicoRuleset(picoServices) {
  import context._

  subscribeToAllEvents
  //  subscribeToEventDomain("TEST")
  //  subscribeToEvents("foo", "bar")

  // HACK test
  //  val event = EventedEvent("TEST", "RaiseEventedEvent", entityId = Option("testEci"))
  //  val event2 = EventedEvent("TEST", "RaiseEventedEvent2", entityId = Option("someInvalidEciFooBarBaz"))
  //  raiseRemoteEvent(event)
  //  raiseRemoteEvent(event2)

  val picoEvent = EventedEvent("PICO", "TestLocalEvent")
  raisePicoEvent(picoEvent)

  override def externalFunctionSharing = true

  use module "me.welcomer.rulesets.HelloWorldRuleset" alias "foo" at "testEci" withConfiguration Json.obj("foo" -> "bar") save

  //
  //  use
  //    .module("com.foo")
  //    .alias("foo")
  //    .at("testEci")
  //    .withConfiguration(Json.obj("foo" -> "bar"))
  //    //    .timeout(t)
  //    .save
  //
  //  module("foo").call("someFunc", Json.obj("arg1" -> "foo")) map {
  //    case EventedSuccess(s) => log.info("{}", s)
  //    case f: EventedFailure => log.error("{}", f)
  //  }
  //
  //  "foo".call("someFunc", Json.obj("arg1" -> "foo"))

  //  implicit val timeout = Timeout(5 seconds)
  //
  //  val testAsk = EventedEvent("foo", "testAsk", attributes = BSONDocument("replyTo" -> self.path.toString()))
  //  val testAskResult = ask(self, testAsk).mapTo[EventedEvent]
  //  testAskResult map { response =>
  //    log.error("ASKRESPONSE: {}", response)
  //  }
  override def provideFunction = {
    case EventedFunction(_, "fail", _, _) => EventedFailure(BasicError("This is a function fail result!"))
    case _ => EventedSuccess("This is a function success result!")
  }

  override def selectWhen = {
    case EventedEvent("TEST", "HELLO", _, _, _) => log.info("TEST:HELLO EventedEvent Received")
    case event @ EventedEvent("TEST", "RaiseEventedEvent", _, _, _) => log.info("[TEST:RaiseEventedEvent] {}", event)
    case event @ EventedEvent("PICO", _, _, _, _) => log.info("PICO EventedEvent Received ({})", event)
    case event @ EventedEvent("TEST", "Function", _, _, _) => {
      log.info("[TEST:Function] {}", event)

      "foo".call("someFunc", Json.obj("arg1" -> "foo")) onComplete {
        case default => log.info("{}", default)
      }
    }
    //    case event @ EventedEvent("FUNCTION", "RESOLVER", _, attributes, _) => {
    //
    //    }
    //    case event @ EventedEvent("foo", "testAsk", _, attributes, _) => {
    //      // TODO: This is a bit of playing that prefaces WELCOMER-416
    //      log.error("ASKRECEIVED: {}", event)
    //      val replyTo = attributes.getAs[String]("replyTo")
    //      replyTo map { asker =>
    //        log.error("ASKRECEIVED-REPLYING: {}", event)
    //        //        context.actorSelection(asker) ! EventedEvent("foo", "testAskReply")
    //        sender ! EventedEvent("foo", "testAskReply")
    //        log.error("ASKRECEIVED-REPLIED: {}", event)
    //      }
    //    }
  }

}
