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

import me.welcomer.framework.pico.PicoRulesetContainer

trait PicoEventsDSL extends AnyRef
  with PicoRaiseRemoteEventDSL
  with PicoRaisePicoEventDSL { this: Actor with ActorLogging =>
  def subscribeToEventedEvents(eventDomain: Option[String] = None, eventType: Option[String] = None, localOnly: Boolean = false): Unit = {
    rulesetContainer ! PicoRulesetContainer.Subscribe(eventDomain, eventType, localOnly)
  }

  def subscribeToEvents(eventDomain: String, eventType: String, localOnly: Boolean = false): Unit = subscribeToEventedEvents(Some(eventDomain), Some(eventType), localOnly)
  def subscribeToLocalEvents(eventDomain: String, eventType: String): Unit = subscribeToEvents(eventDomain, eventType, true)

  def subscribeToAllEvents: Unit = subscribeToEventedEvents(None, None, false)
  def subscribeToAllLocalEvents: Unit = subscribeToEventedEvents(None, None, true)

  def subscribeToEventDomain(eventDomain: String, localOnly: Boolean = false): Unit = subscribeToEventedEvents(Some(eventDomain), None, localOnly)
  def subscribeToLocalEventDomain(eventDomain: String): Unit = subscribeToEventDomain(eventDomain, true)

  def subscribeToEventType(eventType: String, localOnly: Boolean = false): Unit = subscribeToEventedEvents(None, Some(eventType), localOnly)
  def subscribeToLocalEventType(eventType: String): Unit = subscribeToEventType(eventType, true)
}
