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

import akka.actor.ActorLogging
import akka.actor.Actor

// TODO: Remove this? Not needed anymore since we have PicoServices
trait CreatePicoDSL { this: Actor with ActorLogging =>
  //private val picoContainerPath = "/user/overlord/pico" // TODO: Load this from settings/something?
  //
  //  def createPico(
  //    rulesets: Set[String],
  //    replyToEci: String,
  //    responseEventDomain: Option[String] = None,
  //    responseEventSuccessType: Option[String] = None,
  //    responseEventFailureType: Option[String] = None)(implicit transactionId: Option[String]): Unit = {
  //    val debugStr = s"[createPico] rulesets=$rulesets, replyToEci=$replyToEci, transactionId=$transactionId, responseEventDomain=$responseEventDomain, responseEventSuccessType=$responseEventSuccessType, responseEventFailureType=$responseEventFailureType"
  //    log.debug(debugStr)
  //
  //    context.actorSelection(picoContainerPath) ! CreatePico(
  //      rulesets,
  //      replyToEci,
  //      transactionId,
  //      responseEventDomain,
  //      responseEventSuccessType,
  //      responseEventFailureType)
  //  }
}
