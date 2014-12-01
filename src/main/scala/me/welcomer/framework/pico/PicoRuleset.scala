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

  //  def handleFunction: PartialFunction[EventedFunction, ?EventedFunctionResult?]

  def selectWhen: PartialFunction[EventedEvent, Unit]

  final def receive = {
    case event: EventedEvent => {
      event match {
        case _ if (selectWhen.isDefinedAt(event)) => {
          // Set current event scope
          _curEvent = event
          _curTransactionId = (event.attributes \ "transactionId").asOpt[String]

          selectWhen(event)

          // Clean up current event scope
          _curEvent = null
          _curTransactionId = None
        }
        case _ => log.debug(s"Unhandled Event Received: ${event}")
      }
    }
  }

}