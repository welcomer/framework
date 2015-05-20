/*  Copyright 2015 White Label Personal Clouds Pty Ltd
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
package me.welcomer.framework.pico.dsl

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.Actor
import akka.actor.ActorLogging

import play.api.libs.json._

import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.EventedFailure
import me.welcomer.framework.pico.EventedFunction
import me.welcomer.framework.pico.EventedResult

trait PicoEventHandlingDSL extends AnyRef { this: Actor with ActorLogging =>
  import PicoEventSchema._

  def handleEvent[T](
    successHandler: (T, EventedEvent) => Unit,
    errorHandler: (JsError, EventedEvent) => Unit = handleAttributeError)(implicit event: EventedEvent, readsT: Reads[T]): Unit = {
    event.attributes.validate[T] match {
      case JsSuccess(value, path) => successHandler(value, event)
      case e: JsError             => errorHandler(e, event)
    }
  }

  def handleFunction[A, B](f: EventedFunction)(
    success: A => Future[EventedResult[B]])(implicit ec: ExecutionContext, reads: Reads[A]): Future[EventedResult[B]] = {
    f.args.validate[A] match {
      case JsSuccess(value, _) => success(value)
      case e: JsError          => Future.successful(EventedFailure(e))
    }
  }

  protected def handleAttributeError(e: JsError, event: EventedEvent): Unit = {
    val errorJson = JsError.toFlatJson(e)

    log.error("Error with attributes: {}", Json.prettyPrint(errorJson))

    // TODO: Check if there's a replyTo and if so, use it to send an error event back?
  }
}
