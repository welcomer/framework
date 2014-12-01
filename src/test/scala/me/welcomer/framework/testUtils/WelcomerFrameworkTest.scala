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

import org.scalatest.BeforeAndAfter
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span

import akka.actor.ActorSystem

abstract class WelcomerFrameworkTest extends AnyRef with WordSpecLike with Matchers with ScalaFutures {
  override implicit def patienceConfig = PatienceConfig(Span(5, Seconds))
}

abstract class WelcomerFrameworkSingleActorSystemTest
  extends WelcomerFrameworkTest {
  implicit var system: ActorSystem = ActorSystem("WelcomerFrameworkTest")
}

abstract class WelcomerFrameworkActorTest
  extends WelcomerFrameworkTest
  /*with BeforeAndAfterAll*/ with BeforeAndAfter {

  implicit var system: ActorSystem = _

  before {
    system = ActorSystem("WelcomerFrameworkTest")

    // TODO: Add test timing code here?
  }

  after {
    system.shutdown
    system = null
  }

  //  override def afterAll {
  //    shutdownActorSystem(system)
  //  }
}