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
package me.welcomer.framework.actors

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala
import me.welcomer.framework.DefaultComponentRegistryImpl
import me.welcomer.framework.Settings
import me.welcomer.framework.eci.EciResolver
import me.welcomer.framework.utils.DBUtils
import akka.actor.ActorRef
import me.welcomer.framework.eventedgateway.EventedGateway
import me.welcomer.framework.picocontainer.PicoContainer
import me.welcomer.framework.eventedgateway.ExternalEventedGateway

private[framework] object WelcomerFrameworkOverlord {
  case object Shutdown

  case class ToEciResolver(msg: Any)
  case class ToPicoContainer(msg: Any)
  case class ToEventedGateway(msg: Any)

  /**
   * Create Props for an actor of this type.
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(): Props = Props(classOf[WelcomerFrameworkOverlord])
}

private[framework] class WelcomerFrameworkOverlord() extends Actor with ActorLogging {
  import context._
  import WelcomerFrameworkOverlord._

  private val componentRegistry = new DefaultComponentRegistryImpl(system, Settings())

  private var eciResolver: ActorRef = _
  private var picoContainer: ActorRef = _
  private var eventedGateway: ActorRef = _
  private var externalEventGateway: ActorRef = _

  override def preStart(): Unit = {
    log.info("Overlord Initialising..")

    log.info("Bringing up 2nd tier systems..")

    log.info("Starting EciResolver..")
    eciResolver = context.actorOf(
      EciResolver.props(
        componentRegistry.settings,
        componentRegistry.eciResolverService),
      "eciResolver")

    log.info("Starting PicoContainer..")
    picoContainer = context.actorOf(
      PicoContainer.props(
        componentRegistry.settings,
        componentRegistry.picoContainerService),
      "pico")

    log.info("Starting EventedGateway..")
    eventedGateway = context.actorOf(
      EventedGateway.props(
        componentRegistry.settings,
        eciResolver.path,
        picoContainer.path),
      "event")

    log.info("Starting ExternalEventedGateway..")
    externalEventGateway = context.actorOf(
      ExternalEventedGateway.props(
        componentRegistry.settings,
        eventedGateway.path),
      "external")

    log.info("Overlord Initialisation complete.")
  }

  def receive = {
    case Shutdown                  => self ! PoisonPill //context.stop(self)
    case ToEciResolver(message)    => eciResolver.forward(message)
    case ToPicoContainer(message)  => picoContainer.forward(message)
    case ToEventedGateway(message) => eventedGateway.forward(message)
  }

  override def postStop(): Unit = {
    log.info("Overlord Shutting down..")

    // Close DB connections
    componentRegistry.dbConnection.close
    componentRegistry.dbDriver.close

    log.info("Overlord Shutdown complete.")
  }
}

