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
package me.welcomer.framework.eventedgateway

import akka.actor.ActorPath
import akka.actor.Props
import me.welcomer.framework.Settings
import me.welcomer.framework.actors.WelcomerFrameworkActor
import me.welcomer.framework.pico.EventedMessage
import me.welcomer.framework.picocontainer.PicoContainer
import me.welcomer.framework.pico.EventedFailure
import me.welcomer.framework.pico.UnknownEci

private[framework] object EventedGateway {
  case class SenderAsReplyTo(evented: EventedMessage)

  /**
   * Create Props for an actor of this type.
   * @param settings Framework settings
   * @param eciResolverPath Path to the eciResolver
   * @param picoContainerPath Path to the picoContainer
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(
    settings: Settings,
    eciResolverPath: ActorPath,
    picoContainerPath: ActorPath): Props = {

    Props(classOf[EventedGateway], settings, eciResolverPath, picoContainerPath)
  }
}

private[framework] class EventedGateway(
  settings: Settings,
  eciResolverPath: ActorPath,
  picoContainerPath: ActorPath) extends WelcomerFrameworkActor {

  import context._
  import EventedGateway._

  //  def overlord = context.parent
  def eciResolver = context.actorSelection(eciResolverPath)
  def picoContainer = context.actorSelection(picoContainerPath)

  def receive = {
    case SenderAsReplyTo(evented) => self ! evented.withReplyTo(sender)
    case evented: EventedMessage if evented.entityId.isEmpty => {
      // TODO: Handle 'no entity id' case here (error message? noop?)
      log.warning("Cannot resolve/route Evented* with missing entityId: {}", evented)
    }
    case evented: EventedMessage => {
      log.info("[RaiseEvent] Starting eventHandler to resolve {}", evented)

      val resolver = actorOf(
        EventedEntityResolver.props(
          eciResolver,
          settings.EventedEntityResolver.timeout,
          settings.EventedEntityResolver.retries,
          settings.EventedEntityResolver.eventTraceLogDepth))

      resolver ! EventedEntityResolver.ResolveEntity(evented)
    }
    case EventedEntityResolver.EntityResolveSuccess(evented, picoUUID) => {
      log.info("[EntityResolved] Routing resolved evented message {}->{} ({})", evented.entityId, picoUUID, evented)

      picoContainer ! PicoContainer.RouteEventedToPico(picoUUID, evented)
    }
    case EventedEntityResolver.EntityResolveFailure(evented, error) => {
      evented.replyTo map { replyTo =>
        replyTo ! EventedFailure(error)
      } getOrElse { log.error("{} ({})", error, evented.entityId) }
    }
  }

}
