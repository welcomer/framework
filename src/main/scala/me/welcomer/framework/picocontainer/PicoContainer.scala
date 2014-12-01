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
package me.welcomer.framework.picocontainer

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

import akka.actor.Props
import me.welcomer.framework.Settings
import me.welcomer.framework.actors.WelcomerFrameworkActor
import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.picocontainer.service.PicoContainerServiceComponent
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper

// TODO: Setup event channels for various 'domains'. (eg. system for PicoCreated, etc?)

private[framework] object PicoContainer {
  // Note: 'idToken' can be anything, it's not saved, purely used to identify events

  //  case class CreateOrStartPico(idToken: String, picoUUID: String) // Note: picoId will be ignored if creating a new pico
  //  // PicoCreated or PicoStarted with matching idToken will be sent

  case class CreatePico(
    rulesets: Set[String] = Set(),
    replyToEci: String,
    transactionId: Option[String] = None,
    responseEventDomain: Option[String] = None,
    responseEventSuccessType: Option[String] = None,
    responseEventFailureType: Option[String] = None)

  // TODO: Not sure if this should take rulesets as a param, or look them up from DB when message received (probably lookup in DB using UUID)
  case class StartPico(idToken: String, picoUUID: String, rulesets: Set[String])
  //  case class StartPicoSuccess(idToken: String, picoId: String)
  //  case class StartPicoFailure(idToken: String, picoId: String, reason: String)

  //  case class RestartPico(idToken: String, picoUUID: String) // Restart or reload?
  //  case class RestartPicoSuccess(idToken: String, picoId: String)
  //  case class RestartPicoFailure(idToken: String, picoId: String, reason: String)

  case class StopPico(idToken: String, picoUUID: String)
  //  case class StopPicoSuccess(idToken: String, picoId: String)
  //  case class StopPicoFailure(idToken: String, picoId: String, reason: String)

  case class PublishEventedEvent(picoUUID: String, event: EventedEvent)
  // TODO: Implement a 'PublishEventedEvent' type message (if sender == ECIResolverActor { eventedEventBus.publish } else { send to ECIResolver to resolve ECI->PicoUUID }

  /**
   * Create Props for an actor of this type.
   * @param settings The Settings instance
   * @param picoContainerService The PicoContainerService instance
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(
    settings: Settings,
    picoContainerService: PicoContainerServiceComponent#PicoContainerService): Props =
    Props(classOf[PicoContainer], settings, picoContainerService)
}

private[framework] class PicoContainer(
  settings: Settings,
  protected val picoContainerService: PicoContainerServiceComponent#PicoContainerService)
  extends WelcomerFrameworkActor
  with PicoContainerDSL
  with PicoContainerHelper {

  import context._
  import PicoContainer._

  override def insidePreStart(implicit ec: ExecutionContext) = {
    picoContainerService.loadAllPicos
  }

  def receive = {
    case createPico: CreatePico => handleCreatePico(createPico)
    case startPico: StartPico => handleStartPico(startPico)
    case stopPico: StopPico => handleStopPico(stopPico)
    case PublishEventedEvent(picoUUID, event) => {
      // TODO: Add if sender == EventGateway?
      publishEventedEvent(picoUUID, event)
    }
  }
}

private[picocontainer] trait PicoContainerHelper { this: PicoContainer =>
  import scala.concurrent.ExecutionContext
  import me.welcomer.framework.picocontainer.PicoContainer._

  def handleCreatePico(msg: CreatePico)(implicit ec: ExecutionContext): Unit = {
    log.info("CreatePico: {}", msg)

    // Note: this is done outside the onComplete on purpose (so we don't close over mutable state) 
    val transactionIdJson = msg.transactionId.map {id => Json.obj("transactionId" -> id) } getOrElse { Json.obj() }

    picoContainerService.picoManagementService.createNewPico(msg.rulesets) onComplete {
      case Success((newPico, eci)) => {
        log.info("CreatePicoSuccess: picoUUID={}, eci={} ({})", newPico.picoUUID, eci, msg)

        raiseRemoteEvent(
          eventDomain = msg.responseEventDomain.getOrElse("system"),
          eventType = msg.responseEventSuccessType.getOrElse("createPicoSuccess"),
          attributes = transactionIdJson ++ Json.obj("eci" -> eci.eci),
          entityId = msg.replyToEci)
      }
      case Failure(e) => {
        log.error(e, "CreatePicoFailure: err={} ({})", e.getMessage, msg)

        raiseRemoteEvent(
          eventDomain = msg.responseEventDomain.getOrElse("system"),
          eventType = msg.responseEventFailureType.getOrElse("createPicoFailure"),
          attributes = transactionIdJson ++ Json.obj("errorMsg" -> e.getMessage),
          entityId = msg.replyToEci)
      }
    }
  }
  
  def handleStartPico(msg: StartPico): Unit = {
    log.debug("handleStartPico: ({})", msg)

    picoContainerService.startPico(msg.picoUUID, msg.rulesets) //map { picoRef =>
    //      // TODO: Send PicoStarted message to event bus?
    //    }
  }

  def handleStopPico(msg: StopPico) = {
    log.debug("handleStopPico: {}", msg)

    picoContainerService.stopPico(msg.picoUUID)
    // TODO: Send PicoStopped message to event bus?
  }
}