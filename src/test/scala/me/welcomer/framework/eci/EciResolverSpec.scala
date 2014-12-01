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
package me.welcomer.framework.eci

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.actor.actorRef2Scala
import akka.testkit.DefaultTimeout
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import akka.testkit.TestKit.shutdownActorSystem

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import me.welcomer.framework.DefaultComponentRegistryImpl
import me.welcomer.framework.Settings
import me.welcomer.framework.eci.EciResolver.ECIResolvedToPicoUUID
import me.welcomer.framework.eci.EciResolver.InvalidECI

/**
 * Tests for [[me.welcomer.framework.eci.EciResolver]]
 */
class EciResolverSpec
  extends TestKit(ActorSystem("WelcomerFrameworkTest")) with DefaultTimeout with ImplicitSender // Akka TestKit
  with WordSpecLike with Matchers with BeforeAndAfterAll // ScalaTest
  /*with MockFactory*/ { // ScalaMock

  val componentRegistry = new DefaultComponentRegistryImpl(system, Settings()) // TODO: Implement a 'test friendly' wiring of this

  val eciResolverRef = system.actorOf(
    EciResolver.props(
      componentRegistry.settings,
      componentRegistry.eciResolverService))

  override def afterAll {
    shutdownActorSystem(system)
  }

  "EciResolver" should {
    "correctly resolve 'testEci'" in {
      within(5 seconds) {
        val eci = "testEci"
        val expectedPicoUUID = "dc89dbb-721d-40ab-b746-a829771cfce2"

        eciResolverRef ! EciResolver.ResolveECIToPicoUUID(eci)

        expectMsg(ECIResolvedToPicoUUID(eci, expectedPicoUUID))
      }
    }
  }

  "EciResolver" should {
    "be unable to resolve 'ThisEciWillNeverExist'" in {
      within(5 seconds) {
        val eci = "ThisEciWillNeverExist"

        eciResolverRef ! EciResolver.ResolveECIToPicoUUID(eci)

        expectMsg(InvalidECI(eci))
      }
    }
  }
}