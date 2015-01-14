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

import java.util.Date
import scala.math.Ordered.orderingToOrdered
import play.api.libs.functional.syntax._
import play.api.libs.json._
import scala.concurrent.duration._
import akka.actor.ActorRef

trait EventedMessage {
  def entityId: Option[String]
  def replyTo: Option[ActorRef] //Option[Either[ActorRef, String]] = None

  def withReplyTo(replyTo: ActorRef): EventedMessage
  def withNoReplyTo: EventedMessage
}

sealed trait EventedResult[+A] { self =>
  def isSuccess: Boolean = this.isInstanceOf[EventedSuccess[_]]
  def isError: Boolean = this.isInstanceOf[EventedFailure]

  def fold[X](invalid: Seq[EventedError] => X, valid: A => X): X = this match {
    case EventedSuccess(v) => valid(v)
    case EventedFailure(e) => invalid(e)
  }

  def map[X](f: A => X): EventedResult[X] = this match {
    case EventedSuccess(v) => EventedSuccess(f(v))
    case e: EventedFailure => e
  }

  def filterNot(error: EventedError)(p: A => Boolean): EventedResult[A] =
    this.flatMap { a => if (p(a)) EventedFailure(error) else EventedSuccess(a) }

  def filterNot(p: A => Boolean): EventedResult[A] =
    this.flatMap { a => if (p(a)) EventedFailure() else EventedSuccess(a) }

  def filter(p: A => Boolean): EventedResult[A] =
    this.flatMap { a => if (p(a)) EventedSuccess(a) else EventedFailure() }

  def filter(otherwise: EventedError)(p: A => Boolean): EventedResult[A] =
    this.flatMap { a => if (p(a)) EventedSuccess(a) else EventedFailure(otherwise) }

  def collect[B](otherwise: EventedError)(p: PartialFunction[A, B]): EventedResult[B] = flatMap {
    case t if p.isDefinedAt(t) => EventedSuccess(p(t))
    case _                     => EventedFailure(otherwise)
  }

  def flatMap[X](f: A => EventedResult[X]): EventedResult[X] = this match {
    case EventedSuccess(v) => f(v)
    case e: EventedFailure => e
  }

  def foreach(f: A => Unit): Unit = this match {
    case EventedSuccess(a) => f(a)
    case _                 => ()
  }

  def get: A

  def getOrElse[AA >: A](t: => AA): AA = this match {
    case EventedSuccess(a) => a
    case EventedFailure(_) => t
  }

  def orElse[AA >: A](t: => EventedResult[AA]): EventedResult[AA] = this match {
    case s @ EventedSuccess(_) => s
    case EventedFailure(_)     => t
  }

  def asOpt = this match {
    case EventedSuccess(v) => Some(v)
    case EventedFailure(_) => None
  }

  def asEither = this match {
    case EventedSuccess(v) => Right(v)
    case EventedFailure(e) => Left(e)
  }
}

case class EventedSuccess[T](value: T) extends EventedResult[T] {
  def get: T = value
}

// TODO: Define a 'standard' EventedFailure -> JSON serialisation (then use it in ExternalEventedGateway)
case class EventedFailure(errors: Seq[EventedError]) extends EventedResult[Nothing] {
  def get: Nothing = throw new NoSuchElementException("EventedFailure.get")

  def ++(error: EventedFailure): EventedFailure = EventedFailure.merge(this, error)

  def :+(error: EventedError): EventedFailure = EventedFailure.merge(this, EventedFailure(error))
  def append(error: EventedError): EventedFailure = this.:+(error)

  def +:(error: EventedError): EventedFailure = EventedFailure.merge(EventedFailure(error), this)
  def prepend(error: EventedError): EventedFailure = this.+:(error)

  // def asThrowable = throw new ...
}

object EventedFailure {
  def apply(): EventedFailure = EventedFailure(Seq())
  def apply(error: EventedError): EventedFailure = EventedFailure(Seq(error))
  def apply(error: String): EventedFailure = EventedFailure(BasicError(error))

  def merge(e1: Seq[EventedError], e2: Seq[EventedError]): Seq[EventedError] = {
    (e1 ++ e2)
  }

  def merge(e1: EventedFailure, e2: EventedFailure): EventedFailure = {
    EventedFailure(merge(e1.errors, e2.errors))
  }
}

case class EventedModule(
  id: String,
  config: JsObject = Json.obj(),
  eci: Option[String] = None,
  timeout: FiniteDuration = 3.seconds) /*extends Evented*/ {
}

case class EventedFunction(
  module: EventedModule,
  name: String,
  args: JsObject,
  replyTo: Option[ActorRef] = None) extends EventedMessage {
  def entityId = module.eci

  def withReplyTo(replyTo: ActorRef) = this.copy(replyTo = Some(replyTo))
  def withNoReplyTo = this.copy(replyTo = None)

  def withModuleId(newId: String) = this.copy(module = module.copy(id = newId))

  def withModuleConfig(newConfig: JsObject) = this.copy(module = module.copy(config = newConfig))
  def withNoModuleConfig = this.copy(module = module.copy(config = Json.obj()))

  def withModuleEci(newEci: String) = this.copy(module = module.copy(eci = Option(newEci)))
  def withNoModuleEci = this.copy(module = module.copy(eci = None))
}

// Timestamp should be 'HTTP Date'
case class EventedEvent(
  eventDomain: String,
  eventType: String,
  timestamp: Option[Date] = Some(new Date()), // TODO: Should we shift this after attributes (or to the very end?) since it's less likely to be manually set?
  attributes: JsObject = Json.obj(), // TODO: Should we add apply() helper methods that accepts a Map[String, String] or Set/List((String, String))
  entityId: Option[String] = None) extends EventedMessage with Ordered[EventedEvent] {

  def withEntityId(newEntityId: String) = this.copy(entityId = Option(newEntityId))
  def withNoEntityId = this.copy(entityId = None)

  // TODO: Implement this properly later (when we add the concept of 'directives')
  def replyTo: Option[ActorRef] = None
  def withReplyTo(replyTo: ActorRef) = this //this.copy(replyTo = Some(replyTo))
  def withNoReplyTo = this //this.copy(replyTo = None)

  def compare(that: EventedEvent): Int = {
    import scala.math.Ordered.orderingToOrdered

    (this.timestamp, this.eventDomain, this.eventType) compare (that.timestamp, that.eventDomain, that.eventType)
  }

  override def toString: String = {
    val attributesToString = Json.stringify(attributes)

    s"EventedEvent($eventDomain,$eventType,$timestamp,Attributes($attributesToString),$entityId)"
  }
}

object EventedEvent {
  implicit lazy val eventedEventFormat = (
    (__ \ "_domain").format[String] ~
    (__ \ "_type").format[String] ~
    (__ \ "_timestamp").formatNullable[Date] ~
    (__ \ "_attr").format[JsObject] ~
    (__ \ "_entityId").formatNullable[String])(EventedEvent.apply, unlift(EventedEvent.unapply))
}

// TODO: Wrap these errors up into their own package/object?

trait EventedError {
  def asJson: JsObject = Json.obj(
    "errorType" -> this.getClass().getSimpleName,
    "errorDescription" -> this.toString())
}

case class BasicError(msg: String) extends EventedError {
  override def toString = msg
}

case class EventedJsonError(json: JsObject) extends EventedError {
  override def asJson = json
  override def toString = Json.stringify(asJson)
}

case class EventedJsError(e: JsError) extends EventedError {
  override def asJson = JsError.toFlatJson(e)
  override def toString = asJson.toString()
}

case class UnknownEci(eci: Option[String]) extends EventedError

trait EventedModuleError extends EventedError
case class UnknownModule(moduleId: String) extends EventedModuleError

trait EventedFunctionError extends EventedModuleError //with EventedFunctionResult
case object ExternalFunctionSharingDisabled extends EventedFunctionError
case object UnhandledFunction extends EventedFunctionError
