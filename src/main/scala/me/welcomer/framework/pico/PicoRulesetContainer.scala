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

import scala.concurrent.ExecutionContext

import akka.actor.ActorSelection.toScala
import akka.actor.Props
import akka.actor.actorRef2Scala

import me.welcomer.framework.actors.WelcomerFrameworkActor
import me.welcomer.framework.pico.service.PicoServicesComponent

private[pico] object PicoRulesetContainer {
  case object Initialised

  case class Subscribe(eventDomain: Option[String], eventType: Option[String], localOnly: Boolean)
  //  case class Unsubscribe(eventDomain: Option[String], eventType: Option[String], localOnly: Boolean)
  //  case object UnubscribeAll

  case class RaiseRemoteEvented(evented: EventedMessage)
  case class RaiseRemoteEventedWithReplyTo(evented: EventedMessage)
  case class RaiseLocalEvent(event: EventedEvent)

  /**
   * Create Props for an actor of this type.
   * @param rulesets The rulesets to be passed to this actor's constructor.
   * @param picoServices Scoped PicoServices instance.
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(
    rulesets: Set[String],
    picoServices: PicoServicesComponent#PicoServices): Props =
    Props(classOf[PicoRulesetContainer], rulesets, picoServices)
}

private[pico] class PicoRulesetContainer(
  rulesets: Set[String],
  picoServices: PicoServicesComponent#PicoServices) extends WelcomerFrameworkActor with PicoRulesetContainerDSL {
  import context._
  import PicoRulesetContainer._

  implicit def _picoServices = picoServices

  // TODO: Should we change this to use 'routing events' like in the FrameworkOverlord?
  protected lazy val eventedGatewayPath = "/user/overlord/event" // TODO: Load this from settings/something?
  protected lazy val eventedGateway = context.actorSelection(eventedGatewayPath)

  protected lazy val eventedEventBus = new PicoEventedEventBusImpl

  override def insidePreStart(implicit ec: ExecutionContext): Unit = {
    // TODO: Should all pico's have this? Or should we make them 'install' it in the normal way?
    loadRuleset("PdsRuleset")

    loadRulesets(rulesets)

    parent ! Initialised
  }

  def receive = {
    case Subscribe(eventDomain, eventType, localOnly) if fromChild => {
      log.debug("Subscribe: domain={}, type={}, localOnly={}", eventDomain, eventType, localOnly)

      eventedEventBus.subscribe(sender, eventDomain, eventType, localOnly)
    }
    // Raise
    case RaiseRemoteEvented(evented) if fromChild => {
      log.debug("RaiseRemoteEvented: {}", evented)

      eventedGateway ! evented
    }
    case RaiseRemoteEventedWithReplyTo(evented) /*if isChild(ref)*/ => {
      log.debug("RaiseRemoteEventedWithReplyTo: {}", evented)

      eventedGateway ! evented.withReplyTo(sender)
    }
    case RaiseLocalEvent(event) if acceptLocalEvent(event) => {
      log.debug("RaiseLocalEvent: {}", event)

      eventedEventBus.publish(event.withNoEntityId)
    }
    //    case event: EventedEvent if acceptLocalEvent(event) => {
    //      log.info("Local event received: {}", event)
    //
    //      self ! RaiseLocalEvent(event)
    //    }
    //    case RaiseRemoteFunction(f) => // TODO
    //    case RaiseLocalFunction(f) => // TODO
    // Receive
    case event: EventedEvent if acceptRemoteEvent(event) => {
      log.debug("Remote event received: {}", event)

      eventedEventBus.publish(event)
    }
    case f: EventedFunction => {
      log.debug("EventedFunction: {}", f)

      f.replyTo map { replyTo =>
        context.child(f.module.id) match {
          case Some(module) => module.tell(f, replyTo)
          case None         => replyTo ! EventedFailure(UnknownModule(f.module.id))
        }
      } getOrElse { log.warning("No replyTo so dropping function call: {}", f) }
    }
  }

  def acceptLocalEvent(event: EventedEvent) = (fromSelf || fromChild) && event.entityId.isEmpty
  def acceptRemoteEvent(event: EventedEvent) = fromParent && event.entityId.isDefined
}
