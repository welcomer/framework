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
package me.welcomer.framework

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Terminated
import me.welcomer.framework.actors.WelcomerFrameworkOverlord

object WelcomerFramework {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("WelcomerFramework")

    // Create the overlord as the root of our Actor hierachy
    val overlord = system.actorOf(
      WelcomerFrameworkOverlord.props(),
      "overlord")

    // Watch the overlord and terminate the ActorSystem when it dies
    system.actorOf(
      SystemTerminator.props(overlord),
      "systemTerminator")

    sys.addShutdownHook {
      system.shutdown // Shut down our ActorSystem
    }
  }

  /**
   * Watches the supplied ActorRef and shuts down the system when it terminates.
   */
  object SystemTerminator {
    /**
     * Create Props for an actor of this type.
     * @param ref The ActorRef to be passed to this actor's constructor.
     * @return a Props for creating this actor, which can then be further configured
     *         (e.g. calling `.withDispatcher()` on it)
     */
    def props(ref: ActorRef): Props = Props(classOf[SystemTerminator], ref)
  }

  class SystemTerminator(ref: ActorRef) extends Actor with ActorLogging {
    import context._

    context watch ref

    def receive = {
      case Terminated(_) => {
        log.info("{} has terminated, shutting down system", ref.path)
        system.shutdown()
      }
    }
  }

}