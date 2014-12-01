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

import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Stash
import akka.actor.actorRef2Scala
import me.welcomer.framework.actors.WelcomerFrameworkActor
import me.welcomer.framework.pico.service.PicoServicesComponent
import me.welcomer.framework.utils.DBUtils
import scala.concurrent.ExecutionContext

private[framework] object PicoOverlord {
  val RULESET_CONTAINER = "rulesets"

  /**
   * Create Props for an actor of this type.
   * @param picoUUID The picoUUID to be passed to this actor's constructor.
   * @param rulesets The rulesets to be passed to this actor's constructor.
   * @param picoServices Scoped PicoServices instance.
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(
    picoUUID: String,
    rulesets: Set[String],
    picoServices: PicoServicesComponent#PicoServices): Props =
    Props(classOf[PicoOverlord], picoUUID, rulesets, picoServices)
}

private[framework] class PicoOverlord(
  picoUUID: String,
  rulesets: Set[String],
  picoServices: PicoServicesComponent#PicoServices) extends WelcomerFrameworkActor with Stash with DBUtils {
  import context._
  import PicoOverlord._

  private def picoRulesetContainer(event: EventedEvent) = child(RULESET_CONTAINER) match {
    case Some(ref) => {
      log.debug("Sending event through to ruleset container ({})", event)

      ref ! event
    }
    case None => log.error("PicoRulesetContainer doesn't exist for some reason.. Event not sent ({})", event)
    // TODO: Also start the RulesetContainer in None if it doesn't exist?
  }

  override def insidePreStart(implicit ec: ExecutionContext) = {
    // TODO: Load config/etc for pico from DB?

    log.info("Starting PicoRulesetContainer..")
    context.actorOf(
      PicoRulesetContainer.props(rulesets, picoServices),
      RULESET_CONTAINER)
  }

  def initialising: Receive = {
    case event: EventedEvent if isParent => {
      log.debug("Stashing event till initialised: ({})", event)
      stash()
    }
    case PicoRulesetContainer.Initialised => {
      log.info("Initialised")

      unstashAll
      become(running)
    }
  }

  def running: Receive = {
    case event: EventedEvent if isParent => picoRulesetContainer(event)
  }

  def receive = initialising
}