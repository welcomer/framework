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

import akka.actor.Actor
import akka.actor.ActorLogging

trait PicoRuleDSL extends AnyRef
  with PicoEventsDSL
  with PicoPdsDSL { this: Actor with ActorLogging =>
  // TODO: Scope this to Rules? Would need to use a common trait/base
  //  override def rulesetContainer: ActorRef = context.parent.parent
}
