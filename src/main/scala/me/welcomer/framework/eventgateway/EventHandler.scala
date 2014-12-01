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

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.FSM
import akka.actor.FSM.Failure
import akka.actor.Props
import akka.actor.actorRef2Scala
import me.welcomer.framework.eci.EciResolver
import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.eventgateway.EventHandler._

private[eventgateway] object EventHandler {
  // Events
  final case class HandleEvent(event: EventedEvent)

  // States
  sealed trait State
  case object Initial extends State
  case object WaitingForEciResolution extends State

  // Data
  sealed trait Data
  case object Uninitialized extends Data
  final case class Dataz(event: EventedEvent, picoUUID: Option[String]) extends Data
  final case class ErrorData(event: EventedEvent, error: ErrorType) extends Data

  // Errors
  sealed class ErrorType(val description: String)
  case object EmptyEntityId extends ErrorType("Event entityId is empty")
  case object EciResolutionTimedOut extends ErrorType("Eci resolution timed out and max retries reached")
  case object InvalidEci extends ErrorType("Eci was unable to be resolved")

  /**
   * Create Props for an actor of this type.
   * @param eciResolver Reference to the eciResolver
   * @param eciResolutionTimeout The timeout for eciResolution
   * @param maxResolutionRetries The max number of retries after timeout before giving up eci resolution
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(
    eciResolver: ActorRef,
    eciResolutionTimeout: FiniteDuration = 3 seconds,
    maxResolutionRetries: Int = 3): Props = {

    Props(classOf[EventHandler], eciResolver, eciResolutionTimeout, maxResolutionRetries)
  }

}

// TODO: Have a max total runtime using setTimer(name, msg, interval, repeat) ?
private[eventgateway] class EventHandler(
  eciResolver: ActorRef,
  eciResolutionTimeout: FiniteDuration,
  maxResolutionRetries: Int) extends Actor with ActorLogging with FSM[State, Data] {
  import context._

  var retries: Int = 0

  startWith(Initial, Uninitialized)

  // Receive event to process
  when(Initial) {
    case Event(HandleEvent(event), Uninitialized) => {
      log.debug("[Initial::HandleEvent]")
      if (event.entityId.isDefined) {
        // We have an eci, let's resolve it
        eciResolver ! EciResolver.ResolveECIToPicoUUID(event.entityId.get)

        goto(WaitingForEciResolution) using Dataz(event, None)
      } else {
        // Error
        val error = EmptyEntityId
        stop(FSM.Failure(error.description + s" ($event)"), ErrorData(event, error))
      }
    }
  }

  when(WaitingForEciResolution, stateTimeout = eciResolutionTimeout) {
    case Event(EciResolver.ECIResolvedToPicoUUID(eci, picoUUID), d: Dataz) => {
      // Success
      log.debug("[WaitingForEciResolution::ECIResolvedToPicoUUID] {}->{} ({})", eci, picoUUID, d.event)

      parent ! EventGateway.RouteEventToPico(picoUUID, d.event)

      stop(FSM.Normal, d.copy(picoUUID = Some(picoUUID)))
    }
    case Event(EciResolver.InvalidECI(eci), d: Dataz) => {
      // Failure
      log.debug("[WaitingForEciResolution::InvalidECI] {}", eci)

      val error = InvalidEci
      stop(FSM.Failure(error.description + s" (${d.event})"), ErrorData(d.event, error))
    }
    case Event(StateTimeout, d: Dataz) => {
      // Timeout
      log.debug("[WaitingForEciResolution::StateTimeout] {} (retries={}/{})", d.event.entityId, retries, maxResolutionRetries)

      retries match {
        case _ if retries >= maxResolutionRetries => {
          // Error: Max retries exhausted
          val error = EciResolutionTimedOut
          stop(FSM.Failure(error.description + s" (${d.event})"), ErrorData(d.event, error))
        }
        case _ => {
          // Retry resolution
          retries += 1
          goto(WaitingForEciResolution) using d
        }
      }
    }
  }

  whenUnhandled {
    // Log unhandled events
    case Event(e, s) => {
      log.debug("Received unhandled request {} in state {}/{}", e, stateName, s)
      stay
    }
  }

  onTermination {
    case StopEvent(FSM.Normal, state, data) => {
      log.debug("Stop[Normal::{}]: ({})", state, data)
    }
    case StopEvent(FSM.Shutdown, state, data) => {
      log.debug("Shutdown[Shutdown::{}]: ({})", state, data)
    }
    case StopEvent(FSM.Failure(cause), state, data) => {
      log.debug("Stop[Failure::{}]: {} ({})", state, cause, data)
    }
  }

  initialize() // Start the FSM
}