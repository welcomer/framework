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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import scala.util.{ Success, Failure }

import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.Props
import akka.io.IO
import akka.util.Timeout

import play.api.libs.json._

import me.welcomer.framework.Settings
import me.welcomer.framework.actors.WelcomerFrameworkActor
import me.welcomer.framework.pico.EventedEvent

import me.welcomer.framework.pico.EventedModule
import me.welcomer.framework.pico.EventedFunction
import me.welcomer.framework.pico.EventedError
import me.welcomer.framework.pico.EventedResult
import me.welcomer.framework.pico.EventedSuccess
import me.welcomer.framework.pico.EventedFailure

import spray.can.Http
import spray.http._
import spray.http.MediaTypes._
import spray.httpx.PlayJsonSupport
import spray.routing._

private[framework] object ExternalEventedGateway {
  /**
   * Create Props for an actor of this type.
   * @param settings Framework settings
   * @param eventedGatewayPath Path to the eventGateway actor
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(
    settings: Settings,
    eventedGatewayPath: ActorPath): Props = {
    Props(classOf[ExternalEventGateway], settings, eventedGatewayPath)
  }
}

private[framework] class ExternalEventGateway(
  settings: Settings,
  eventedGatewayPath: ActorPath) extends WelcomerFrameworkActor with ExternalEventedEventServiceImpl {

  import akka.pattern.ask
  //  import context._
  //  implicit val rSettings = RoutingSettings.default(context) // This is needed if you `import context._`, otherwise things break

  implicit val timeout = Timeout(5.seconds)

  def actorRefFactory = context

  def overlord = context.parent
  def eventedGateway = context.actorSelection(eventedGatewayPath)

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

    eventedGateway ! event

    ctx.complete(Json.obj("msg" -> "Event raised asynchronously"))
  }
}

// TODO: Refactor this into a proper cake pattern?
trait ExternalEventedEventService extends HttpService with PlayJsonSupport { this: ExternalEventGateway =>
  import akka.pattern.ask

  implicit def executionContext = actorRefFactory.dispatcher

  val v1 = pathPrefix("v1")
  val event = pathPrefix("event")
  val func = pathPrefix("func")

  def TODO(msg: String) = failWith(new RuntimeException(s"TODO: $msg"))

  val routes =
    v1 {
      event {
        path(Segment) { eci =>
          post {
            entity(as[EventedEvent]) { event =>
              raiseEvent(event.withEntityId(eci))
            }
          }
          // ~
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
      } ~
        func {
          headerValueByName("X-Eci") { eci =>
            pathPrefix(Segment) { moduleId =>
              path(Segment) { funcName =>
                get {
                  parameterMap { params =>
                    val call = callFunction(eci, moduleId, funcName, params)
                    onSuccess(call) {
                      handleEventedResult(_)
                    }
                  }
                }
              }
            }
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
            Json.arr(
              Json.obj("link" -> "POST /v1/event/:eci"),
              Json.obj("link" -> "GET /v1/func/:moduleId/:funcName?paramKeyN=paramValueN"))
          }
        }
      }

  def raiseEvent(event: EventedEvent): Route

  def callFunction(
    eci: String,
    moduleId: String,
    funcName: String,
    params: Map[String, String]): Future[EventedResult[_]] = {
    val moduleConfig = Json.obj() // TODO: Make this configurable
    val m = EventedModule(moduleId, moduleConfig, Some(eci))
    val f = EventedFunction(m, funcName, Json.toJson(params).as[JsObject])

    (eventedGateway ? EventedGateway.SenderAsReplyTo(f)).mapTo[EventedResult[_]]
  }

  def handleEventedResult(result: EventedResult[_]): Route = {
    log.debug("[ExternalEventedEventService] Function call result: {}", result)

    result match {
      case EventedSuccess(s) => {
        s match {
          case s: JsObject => complete(s)
          case s: String => complete(s)
          case _ => {
            log.error("[ExternalEventedEventService] Unhandled EventedResult: {}", s)

            failWith(new Error(s"Unhandled EventedResult: {}"))
          }
        }
      }
      case EventedFailure(errors) => {
        log.error("[ExternalEventedEventService] Failure: {}", errors);

        // TODO: Probably define a nicer way to serialise EventedFailure -> Json?
        val errorsArr = errors.foldLeft(Json.arr()) { (arr, error) =>
          arr :+ Json.obj(
            "errorType" -> error.getClass.getSimpleName,
            "errorMsg" -> error.toString) // TODO: Improve this
        }

        val errorJson = Json.obj(
          "type" -> "error",
          "errors" -> errorsArr)

        complete(StatusCodes.BadRequest, errorJson)
      }
    }
  }
}