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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.Props
import akka.io.IO
import akka.util.Timeout

import play.api.libs.json._

import me.welcomer.framework.Settings
import me.welcomer.framework.actors.WelcomerFrameworkActor
import me.welcomer.framework.pico.EventedEvent

import spray.can.Http
import spray.http._
import spray.http.MediaTypes._
import spray.httpx.PlayJsonSupport
import spray.routing._

private[framework] object ExternalEventGateway {
  /**
   * Create Props for an actor of this type.
   * @param eventGatewayPath Path to the eventGateway actor
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(
    settings: Settings,
    eventGatewayPath: ActorPath): Props = {
    Props(classOf[ExternalEventGateway], settings, eventGatewayPath)
  }
}

private[framework] class ExternalEventGateway(
  settings: Settings,
  eventGatewayPath: ActorPath) extends WelcomerFrameworkActor with ExternalEventedEventServiceImpl {
  import akka.pattern.ask
  //  import context._
  //  implicit val rSettings = RoutingSettings.default(context) // This is needed if you `import context._`, otherwise things break

  implicit val timeout = Timeout(5.seconds)

  def actorRefFactory = context

  def overlord = context.parent
  def eventGateway = context.actorSelection(eventGatewayPath)

  var httpListener: Future[ActorRef] = _

  override def insidePreStart(implicit ec: ExecutionContext): Unit = {
    // Start a new HTTP server with self as the handler
    val r = IO(Http)(context.system) ? Http.Bind(
      self,
      interface = settings.ExternalEventGateway.Bind.interface,
      port = settings.ExternalEventGateway.Bind.port)

    httpListener = r map {
      case listenerRef: ActorRef => listenerRef
    }
  }

  override def insidePostStop(implicit ec: ExecutionContext): Unit = {
    httpListener map { _ ? Http.Unbind }
  }

  def receive = runRoute(routes)
}

trait ExternalEventedEventServiceImpl extends ExternalEventedEventService { this: ExternalEventGateway =>
  def raiseEvent(event: EventedEvent): Route = { ctx =>
    log.info("[raiseEvent] event={}", event)

    eventGateway ! EventGateway.RaiseEvent(event)

    ctx.complete(Json.obj("msg" -> "Event raised asynchronously"))
  }
}

// TODO: Refactor this into a proper cake pattern?
trait ExternalEventedEventService extends HttpService with PlayJsonSupport {
  val v1 = pathPrefix("v1")
  val event = pathPrefix("event")

  def TODO(msg: String) = failWith(new RuntimeException(s"TODO: $msg"))

  val routes =
    v1 {
      event {
        path(Segment) { eci =>
          post {
            entity(as[EventedEvent]) { event =>
              raiseEvent(event.withEntityId(eci))
            }
          } // ~
          //            get {
          //              parameters("_domain", "_type", "_timestamp".as[Date]) { (_domain, _type, _timestamp) =>
          //
          //                raiseEvent(
          //                  eci,
          //                  RaiseEventBody(
          //                    _domain,
          //                    _type,
          //                    _timestamp,
          //                    Json.obj("TODO" -> "TODO")))
          //              }
          //            }
        }
      }
    } ~
      //      path("fail") {
      //        get {
      //          failWith(new RuntimeException("testing exception failureness"))
      //        }
      //      } ~
      path("") {
        get {
          complete {
            Json.obj("link" -> "POST /v1/event/:eci")
          }
        }
      }

  def raiseEvent(event: EventedEvent): Route
}