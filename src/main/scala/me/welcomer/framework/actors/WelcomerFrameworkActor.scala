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
package me.welcomer.framework.actors

import akka.actor.ActorLogging
import akka.actor.Actor
import scala.concurrent.ExecutionContext

private[framework] abstract class WelcomerFrameworkActor extends Actor with ActorLogging {
  import context._

  protected val className: String = this.getClass().getSimpleName()
  protected val actorName: String = context.self.path.name

  def insidePreStart(implicit ec: ExecutionContext): Unit = {}
  def insidePostStop(implicit ec: ExecutionContext): Unit = {}

  override def preStart(): Unit = {
    log.info("{}[{}] PreStart Initialisation..", actorName, className)

    insidePreStart

    log.info("{}[{}] PreStart Initialisation complete.", actorName, className)
  }

  override def postStop(): Unit = {
    log.info("{}[{}] Shutting down..", actorName, className)

    insidePostStop
  }

  def isParent = (sender == parent)
  def isSelf = (sender == self)
  def isChild = children.exists(_ == sender)
}