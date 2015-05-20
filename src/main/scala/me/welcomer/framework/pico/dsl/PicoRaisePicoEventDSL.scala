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
import akka.actor.ActorRef

import play.api.libs.json.JsObject

import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.PicoRulesetContainer

trait PicoRaisePicoEventDSL { this: Actor with ActorLogging =>
  def rulesetContainer: ActorRef

  def raisePicoEvent(event: EventedEvent): Unit = {
    log.debug("[raisePicoEvent] {}", event)

    val strippedEvent = event.copy(entityId = None)
    rulesetContainer ! PicoRulesetContainer.RaiseLocalEvent(strippedEvent)
  }

  def raisePicoEvent(eventDomain: String, eventType: String, attributes: JsObject): Unit = {
    raisePicoEvent(EventedEvent(eventDomain, eventType, attributes = attributes, entityId = None))
  }
}
