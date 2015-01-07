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
package me.welcomer.framework.pico

import akka.actor.ActorRef
import akka.actor.actorRef2Scala

trait PicoRulesetDSLMock extends PicoRulesetDSL { this: PicoRuleset =>
  val responseWatcherRef: ActorRef
  val picoEventWatcherRef: ActorRef

  protected def mockEventReply: PartialFunction[EventedMessage, Unit]

  protected def sendEventToTestRef(evented: EventedMessage) = {
    log.debug("Sending event to testRef: {}", evented)

    responseWatcherRef ! evented
  }

  override def subscribeToEventedEvents(eventDomain: Option[String] = None, eventType: Option[String] = None, localOnly: Boolean = false): Unit = {
    log.debug("Mock subscribeToEvents, ignoring: {}:{}", eventDomain, eventType)

    //    mockEventReply(event)
  }

  final override def raiseRemoteEvent(evented: EventedMessage): Unit = {
    log.debug("Mock raiseRemoteEvent: {}", evented)

    mockEventReply(evented)
  }

  final override def raisePicoEvent(event: EventedEvent): Unit = {
    log.debug("Mock raisePicoEvent: {}", event)

    picoEventWatcherRef ! event

    mockEventReply(event)
  }

  def raisePicoEventMock(event: EventedEvent) = {
    log.debug("Mocked PicoLocal event, reflecting to self: {}", event)

    self ! event
  }
}