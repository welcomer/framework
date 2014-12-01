package me.welcomer.framework.picocontainer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import akka.actor.actorRef2Scala
import me.welcomer.framework.actors.WelcomerFrameworkOverlord
import me.welcomer.framework.eventgateway.EventGateway
import me.welcomer.framework.pico.EventedEvent
import play.api.libs.json.JsObject

private[picocontainer] trait PicoContainerDSL extends AnyRef { this: PicoContainer =>

  def publishEventedEvent(picoUUID: String, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    log.info("[publishEventedEvent] {}({})", picoUUID, event)

    val picoRunning = context.child(picoUUID) match {
      case Some(picoRef) => Future(Success(picoRef))
      case None => picoContainerService.startPico(picoUUID)
    }

    picoRunning map {
      case Success(picoRef) => {
        // TODO: This is a hack and should be handled 'properly' (however that is defined)
        // See: https://whitelabel.atlassian.net/browse/WELCOMER-462
        context.system.scheduler.scheduleOnce(2 seconds) {
          picoRef ! event
        }
      }
      case Failure(e) => log.error(e, "Event not published: Pico not running/couldn't be started: {} ({}->{})", e.getMessage(), picoUUID, event)
    }
  }

  def raiseRemoteEvent(event: EventedEvent): Unit = {
    log.debug("[raiseRemoteEvent] {}", event)
    context.parent ! WelcomerFrameworkOverlord.ToEventGateway(EventGateway.RaiseEvent(event))
  }

  def raiseRemoteEvent(eventDomain: String, eventType: String, attributes: JsObject, entityId: String): Unit = {
    raiseRemoteEvent(EventedEvent(eventDomain, eventType, attributes = attributes, entityId = Some(entityId)))
  }

}