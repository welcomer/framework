package me.welcomer.framework.picocontainer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import akka.actor.actorRef2Scala

import play.api.libs.json.JsObject

import me.welcomer.framework.actors.WelcomerFrameworkOverlord
import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.EventedMessage

private[picocontainer] trait PicoContainerDSL extends AnyRef { this: PicoContainer =>

  def routeEventedToPico(picoUUID: String, evented: EventedMessage)(implicit ec: ExecutionContext): Unit = {
    log.info("[publishEventedEvent] {}({})", picoUUID, evented)

    val picoRunning = context.child(picoUUID) match {
      case Some(picoRef) => Future(Success(picoRef))
      case None => picoContainerService.startPico(picoUUID)
    }

    picoRunning map {
      case Success(picoRef) => {
        // TODO: This is a hack and should be handled 'properly' (however that is defined)
        // See: https://whitelabel.atlassian.net/browse/WELCOMER-462
        context.system.scheduler.scheduleOnce(2.seconds) {
          picoRef ! evented
        }
      }
      case Failure(e) => log.error(e, "Evented* not published: Pico not running/couldn't be started: {} ({}->{})", e.getMessage(), picoUUID, evented)
    }
  }

  def raiseRemoteEvent(evented: EventedMessage): Unit = {
    log.debug("[raiseRemoteEvent] {}", evented)
    context.parent ! WelcomerFrameworkOverlord.ToEventedGateway(evented)
  }

  def raiseRemoteEvent(eventDomain: String, eventType: String, attributes: JsObject, entityId: String): Unit = {
    raiseRemoteEvent(EventedEvent(eventDomain, eventType, attributes = attributes, entityId = Some(entityId)))
  }

}