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
package me.welcomer.framework.testUtils

import akka.actor.ActorSystem
import akka.testkit.TestProbe

object WelcomerFrameworkTestProbe {
  def apply()(implicit system: ActorSystem) = {
    new WelcomerFrameworkTestProbe(system)
  }
}

class WelcomerFrameworkTestProbe(system: ActorSystem) extends TestProbe(system) {

  def expectNMsgPF(numMsg: Int = 1)(f: PartialFunction[Any, Any]): Unit = {

    val events: Seq[Any] = receiveWhile(messages = numMsg)(f)

    assert(events.size == numMsg, s"Invalid number of events received: ${events.size}/$numMsg (${events.toString})")

    val allMatch: Boolean = events.foldLeft(true)((doMatch, event) => doMatch && f.isDefinedAt(event))

    assert(allMatch, "Not all events matched the partial function: " + events.toString)
  }
}