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

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef

import play.api.libs.json.JsObject

import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.EventedFunction
import me.welcomer.framework.pico.EventedMessage
import me.welcomer.framework.pico.EventedResult
import me.welcomer.framework.pico.PicoRulesetContainer

trait PicoRaiseRemoteEventDSL { this: Actor with ActorLogging =>
  import akka.pattern.ask
  import akka.util.Timeout
  import context.dispatcher

  def rulesetContainer: ActorRef

  def raiseRemoteEvent(evented: EventedMessage): Unit = {
    log.debug("[raiseRemoteEvent] {}", evented)
    rulesetContainer ! PicoRulesetContainer.RaiseRemoteEvented(evented)
  }

  def raiseRemoteEventWithReplyTo(evented: EventedFunction): Future[EventedResult[_]] = {
    //  def raiseRemoteEventWithReplyTo(evented: EventedMessage): Future[EventedResult[_]] = {
    log.debug("[raiseRemoteEventWithReplyTo] {}", evented)

    //    implicit val timeout: Timeout = evented match {
    //      case event: EventedEvent                     => 5.seconds // TODO: Figure a better way to handle timeouts for events.. (probably only matters once we have directives?)
    //      case func @ EventedFunction(module, _, _, _) => module.timeout
    //    }
    implicit def timeout: Timeout = evented.module.timeout

    (rulesetContainer ? PicoRulesetContainer.RaiseRemoteEventedWithReplyTo(evented)).mapTo[EventedResult[_]]
  }

  def raiseRemoteEvent(eventDomain: String, eventType: String, attributes: JsObject, entityId: String): Unit = {
    raiseRemoteEvent(EventedEvent(eventDomain, eventType, attributes = attributes, entityId = Some(entityId)))
  }
}
