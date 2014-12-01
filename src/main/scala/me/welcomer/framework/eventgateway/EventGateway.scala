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
package me.welcomer.framework.eventgateway

import akka.actor.Actor
import akka.actor.ActorLogging
import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.eventgateway.EventGateway.RouteEventToPico
import me.welcomer.framework.picocontainer.PicoContainer
import me.welcomer.framework.eventgateway.EventGateway.RaiseEvent
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.Props
import me.welcomer.framework.actors.WelcomerFrameworkActor

private[framework] object EventGateway {
  // Public Events
  case class RaiseEvent(event: EventedEvent)

  // Private Events
  private[eventgateway] case class RouteEventToPico(picoUUID: String, event: EventedEvent)

  /**
   * Create Props for an actor of this type.
   * @param eciResolver Reference to the eciResolver
   * @param picoContainer Reference to the picoContainer
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(
    eciResolver: ActorRef,
    picoContainer: ActorRef): Props = {

    Props(classOf[EventGateway], eciResolver, picoContainer)
  }
}

private[framework] class EventGateway(eciResolver: ActorRef, picoContainer: ActorRef) extends WelcomerFrameworkActor {
  import context._

  def receive = {
    case RaiseEvent(event) => {
      log.info("[RaiseEvent] Starting eventHandler to resolve {}", event)

      val eventHandler = actorOf(EventHandler.props(eciResolver))
      eventHandler ! EventHandler.HandleEvent(event)
    }
    case RouteEventToPico(picoUUID, event) => {
      log.info("[RouteEventToPico] Routing resolved event {}->{} ({})", event.entityId, picoUUID, event)

      picoContainer ! PicoContainer.PublishEventedEvent(picoUUID, event)
    }
  }

}