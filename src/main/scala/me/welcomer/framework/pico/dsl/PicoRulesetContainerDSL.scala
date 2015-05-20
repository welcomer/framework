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
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.ActorRef

import me.welcomer.framework.extensions.ReflectionExtension
import me.welcomer.framework.pico.PicoRuleset
import me.welcomer.framework.pico.PicoRulesetContainer

trait PicoRulesetContainerDSL extends AnyRef { self: PicoRulesetContainer =>
  def loadRulesets(rulesets: Set[String])(implicit ec: ExecutionContext): Map[String, Try[ActorRef]] = {
    rulesets.size match {
      case size if (size > 0) => {
        log.info(s"Loading rulesets ($size)..")

        val rulesetRefs = rulesets.foldLeft(Map[String, Try[ActorRef]]()) { (rulesetRefs, ruleset) => rulesetRefs + (ruleset -> loadRuleset(ruleset)) }

        log.info("Loading rulesets complete.")

        rulesetRefs
      }
      case _ => {
        log.info(s"No rulesets to load.")

        Map[String, Try[ActorRef]]()
      }
    }
  }

  def loadRuleset(name: String): Try[ActorRef] = {
    log.info("Loading ruleset {}..", name)

    Try(
      useRulesetCalled(name) match {
        case Success(ruleset) => {
          context.actorOf(
            PicoRuleset.props(ruleset, _picoServices),
            ruleset.getCanonicalName())
        }
        case Failure(e) => {
          e match {
            case _: ClassNotFoundException => log.error("ClassNotFoundException while attempting to load ruleset {}", name)
            case _                         => log.error(e, "Error while attempting to load ruleset {}", name)
          }

          throw e
        }
      })
  }

  // TODO: Refactor the ruleset scope into settings somehow? (put pico settings in picoServices?)
  def useRulesetCalled(className: String): Try[Class[_ <: PicoRuleset]] = {
    val fqcn = "me.welcomer.rulesets." + className

    ReflectionExtension(context.system).classFor[PicoRuleset](fqcn)
  }
}
