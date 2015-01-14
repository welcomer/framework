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
package me.welcomer.framework.pico.ruleset.patterns

import scala.collection.mutable.HashMap

import play.api.libs.json._

import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.PicoRuleset

trait TransactionIdMapping { this: PicoRuleset =>
  private var transactions = HashMap[String, Option[String]]() // TODO: Get rid of Option, make it just String?

  def mapReplyToEci(transactionId: String, replyTo: Option[String] = None) = {
    transactions += (transactionId -> replyTo)
  }

  def retrieveReplyToEci(transactionId: String, popEntry: Boolean): Option[String] = {
    transactions.get(transactionId) flatMap { replyToOpt =>
      if (popEntry) transactions -= transactionId // TODO: Is closing over this a bad idea? Maybe send a message so we can update it syncronously? (probably pull it up into PicoRuleset)

      replyToOpt
    }
  }

  def replyToTransaction(
    eventDomain: String,
    eventType: String, attributes: JsObject)(implicit event: EventedEvent, transactionIdOpt: Option[String]) = {
    transactionIdOpt map { implicit transactionId =>
      retrieveReplyToEci(transactionId, true) match {
        case Some(replyTo) => raiseRemoteEvent(eventDomain, eventType, attributes, replyTo)
        case None          => raisePicoEvent(eventDomain, eventType, attributes)
      }
    } getOrElse { log.error("No 'transactionId' to lookup so we can't map it to 'replyTo' ({})", event) }
  }
}
