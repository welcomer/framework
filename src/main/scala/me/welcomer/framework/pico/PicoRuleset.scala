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

import akka.actor.Props

import me.welcomer.framework.actors.WelcomerFrameworkActor
import me.welcomer.framework.pico.service.PicoServicesComponent

private[framework] object PicoRuleset {
  /**
   * Create Props for an actor of this type.
   * @param picoServices Scoped PicoServices instance.
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props[T <: PicoRuleset](rulesetClass: Class[T], picoServices: PicoServicesComponent#PicoServices): Props =
    Props(rulesetClass, picoServices)
}

abstract class PicoRuleset(picoServices: PicoServicesComponent#PicoServices) extends WelcomerFrameworkActor with PicoRulesetDSL {
  import context._
  import me.welcomer.framework.utils.ImplicitConversions._

  protected implicit def _picoServices = picoServices

  // TODO: http://developer.kynetx.com/display/docs/meta
  // TODO: Wrap this in some kind of 'meta' object?
  final val rulesetId: String = this.getClass().getCanonicalName()
  val rulesetName: String = this.getClass().getSimpleName()

  override protected val className = rulesetName

  /**
   * Whether external function sharing is enabled or not. (Default: false)
   */
  def externalFunctionSharing: Boolean = false

  // TODO: Should we replace this concept with an implicit 'current context' (EventedContext?) type thing?
  //  type CurrentEvent = EventedEvent
  //  type TransactionId = Option[String]


  private[this] var _curEvent: EventedEvent = _
  private[this] var _curTransactionId: Option[String] = None // TODO: Make this a case class (more specific scope for implicits) If so, have exists, generate, get, getOrGenerate methods?

  // TODO: Wrap this in some kind of 'currentEvent' type context object?
  implicit protected def curEvent: EventedEvent = {
    val event = _curEvent
    event
  }
  implicit protected def curTransactionId: Option[String] = {
    val transactionId = _curTransactionId
    transactionId
  }

  def selectWhen: PartialFunction[EventedEvent, Unit] = PartialFunction.empty

  def provideFunction: PartialFunction[EventedFunction, EventedResult[_]] = PartialFunction.empty

  //  def selectWhen(
  //    implicit event: EventedEvent,
  //    transactionId: TransactionId): PartialFunction[EventedEvent, Unit] = PartialFunction.empty

  final def receive = {
    case event: EventedEvent => handleEvent(event)
    case func: EventedFunction => handleFunction(func)
  }

  private final def handleEvent(event: EventedEvent): Unit = {
    // TODO: Can we use these implicits easily/nicely with a PartialFunction?
    //    implicit val curEvent: CurrentEvent = event
    //    implicit val curTransactionId: TransactionId = (event.attributes \ "transactionId").asOpt[String]

    event match {
      case _ if (selectWhen.isDefinedAt(event)) => {
        // Set current event scope
        _curEvent = event
        _curTransactionId = (event.attributes \ "transactionId").asOpt[String]

        selectWhen.apply(event)

        // Clean up current event scope
        _curEvent = null
        _curTransactionId = None
      }
      case _ => log.debug(s"Unhandled Event Received: ${event}")
    }
  }

  private final def handleFunction(func: EventedFunction): Unit = {
    if (func.replyTo.isEmpty) {
      log.warning("No replyTo so dropping function call: {}", func)
      return
    }

    val result: EventedResult[_] = func match {
      case _ if !externalFunctionSharing => {
        log.debug("[handleFunction] ExternalFunctionSharing = {}", externalFunctionSharing)

        EventedFailure(ExternalFunctionSharingDisabled)
      }
      case _ if provideFunction.isDefinedAt(func) => {
        log.debug("[handleFunction] Calling: {}", func)

        provideFunction(func)
      }
      case _ => {
        log.debug("[handleFunction] Unhandled function requested: {}", func)

        EventedFailure(UnhandledFunction)
      }
    }

    func.replyTo map { _ ! result }
  }

}

trait PicoRulesetHelper extends AnyRef 
  with akka.actor.Actor
  with akka.actor.ActorLogging
  with PicoRaiseRemoteEventDSL 
  with PicoRaisePicoEventDSL {
  import scala.collection.mutable.HashMap
  import play.api.libs.json.JsObject
  import me.welcomer.rulesets.welcomerId.WelcomerIdSchema._
  
  var transactions = HashMap[String, Option[String]]() // TODO: Get rid of Option, make it just String?
  // TODO: This is likely a fairly common pattern.. can we extract it?
  def mapReplyToEci(transactionId: String, replyTo: Option[String] = None) /*(implicit event: EventedEvent)*/ = {
    //    val storeReplyTo = replyTo match {
    //      case replyTo @ Some(_) => replyTo
    //      case None => event.entityId // This shouldn't be entityId, it should somehow map to the ECI allowed to use this entityId, as we want to know where it came from..
    //    }
    transactions += (transactionId -> replyTo)
  }

  // TODO: This is likely a fairly common pattern.. can we extract it?
  def retrieveReplyToEci(transactionId: String, popEntry: Boolean): Option[String] = {
    transactions.get(transactionId) flatMap { replyToOpt =>
      if (popEntry) transactions -= transactionId // TODO: Is closing over this a bad idea? Maybe send a message so we can update it syncronously? (probably pull it up into PicoRuleset)

      replyToOpt
    }
  }

  // TODO: This is likely a fairly common pattern.. can we extract it?
  def replyToTransaction(eventDomain: String, eventType: String, attributes: JsObject)(implicit event: EventedEvent, transactionIdOpt: Option[String]) = {
    transactionIdOpt map { implicit transactionId =>
      retrieveReplyToEci(transactionId, true) match {
        //      transactions.get(transactionId) map { replyToOpt =>
        //        transactions = transactions -= transactionId // TODO: Is closing over this a bad idea? Maybe send a message so we can update it syncronously? (probably pull it up into PicoRuleset)

        //        replyToOpt match {
        case Some(replyTo) => raiseRemoteEvent(eventDomain, eventType, attributes, replyTo)
        case None => raisePicoEvent(eventDomain, eventType, attributes)
      }
      //      } getOrElse { log.error("'{}' not found in map, don't know who to reply to ({})", TRANSACTION_ID, event) }
    } getOrElse { log.error("No '{}' to lookup so we can't map it to '{}' ({})", TRANSACTION_ID, REPLY_TO, event) }
  }
}