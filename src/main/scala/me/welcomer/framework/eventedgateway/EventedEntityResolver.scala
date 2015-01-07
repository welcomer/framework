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

import scala.concurrent.duration.FiniteDuration
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.FSM
import akka.actor.LoggingFSM
import akka.actor.Props
import akka.actor.actorRef2Scala
import me.welcomer.framework.eci.EciResolver
import me.welcomer.framework.pico.EventedMessage
import me.welcomer.framework.pico.EventedError

private[eventedgateway] object EventedEntityResolver {
  // Events
  final case class ResolveEntity(evented: EventedMessage)
  final case class EntityResolveSuccess(evented: EventedMessage, picoUUID: String)
  final case class EntityResolveFailure(evented: EventedMessage, error: ErrorType)

  // States
  sealed trait State
  case object Ready extends State
  case object WaitingForEciResolution extends State

  // Data
  sealed trait Data
  final case object EmptyContext extends Data
  final case class Context(
    evented: EventedMessage,
    requestor: ActorRef,
    picoUUID: Option[String] = None,
    retries: Int = 0,
    error: Option[ErrorType] = None) extends Data

  // Errors
  sealed class ErrorType(val description: String) extends EventedError
  case object EmptyEntityId extends ErrorType("Evented entityId is empty")
  case object EciResolutionTimedOut extends ErrorType("ECI resolution timed out")
  case class InvalidEci(eci: String) extends ErrorType(s"ECI was unable to be resolved ($eci)")

  /**
   * Create Props for an actor of this type.
   * @param eciResolver Reference to the eciResolver
   * @param eciResolutionTimeout The timeout for eciResolution
   * @param maxResolutionRetries The max number of retries after timeout before giving up eci resolution
   * @param eventTraceLogDepth The max number of event trace logs to keep
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(
    eciResolver: ActorSelection,
    eciResolutionTimeout: FiniteDuration,
    maxResolutionRetries: Int,
    eventTraceLogDepth: Int): Props = {

    Props(classOf[EventedEntityResolver],
      eciResolver,
      eciResolutionTimeout,
      maxResolutionRetries,
      eventTraceLogDepth)
  }
}

// TODO: Have a max total runtime using setTimer(name, msg, interval, repeat) ?
private[eventedgateway] class EventedEntityResolver(
  eciResolver: ActorSelection,
  eciResolutionTimeout: FiniteDuration,
  maxResolutionRetries: Int,
  eventTraceLogDepth: Int) extends Actor with ActorLogging with LoggingFSM[EventedEntityResolver.State, EventedEntityResolver.Data] {

  import context._
  import EventedEntityResolver._

  override def logDepth = eventTraceLogDepth

  startWith(Ready, EmptyContext)

  when(Ready) {
    case Event(ResolveEntity(evented), EmptyContext) => {
      val d = Context(evented, sender)

      evented.entityId match {
        case Some(entityId) => {
          eciResolver ! EciResolver.ResolveECIToPicoUUID(entityId)

          goto(WaitingForEciResolution) using d
        }
        case None => stopWithError(EmptyEntityId, d)
      }
    }
  }

  when(WaitingForEciResolution, stateTimeout = eciResolutionTimeout) {
    case Event(EciResolver.ECIResolvedToPicoUUID(eci, picoUUID), ctx: Context) => {
      ctx.requestor ! EntityResolveSuccess(ctx.evented, picoUUID)

      stop(FSM.Normal, ctx.copy(picoUUID = Some(picoUUID)))
    }
    case Event(EciResolver.InvalidECI(eci), ctx: Context) => {
      val error = InvalidEci(eci)
      
      ctx.requestor ! EntityResolveFailure(ctx.evented, error)

      stopWithError(error, ctx)
    }
    case Event(StateTimeout, ctx: Context) => {
      if (ctx.retries >= maxResolutionRetries) {
        val error = EciResolutionTimedOut
        
        ctx.requestor ! EntityResolveFailure(ctx.evented, error)

        stopWithError(error, ctx)
      } else goto(WaitingForEciResolution) using ctx.copy(retries = ctx.retries + 1)
    }
  }

  whenUnhandled {
    case Event(e, s) => {
      log.debug("Received unhandled request {} in state {}/{}", e, stateName, s)
      stay
    }
  }

  onTermination {
    case StopEvent(FSM.Normal, state, ctx) => //log.debug("Stop[Normal::{}]: ({})", state, ctx)
    case StopEvent(FSM.Shutdown, state, ctx) => log.debug("Shutdown[Shutdown::{}]: ({})", state, ctx)
    case StopEvent(FSM.Failure(cause), state, ctx) => {
      val eventTrace = getLog.mkString("\n\t")

      log.warning(
        "Shutdown[Failure::{}] ({})\n Event trace ({}):\n\t{}",
        state,
        ctx,
        getLog.length,
        eventTrace)
    }
  }

  initialize() // Start the FSM

  def stopWithError(e: ErrorType, ctx: Context) = {
    stop(FSM.Failure(e), ctx.copy(error = Some(e)))
  }
}