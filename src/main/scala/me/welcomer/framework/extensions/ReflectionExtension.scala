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
package me.welcomer.framework.extensions

import scala.reflect.ClassTag
import scala.util.Try

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.DynamicAccess
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider

/**
 *  @see http://stackoverflow.com/a/15683071/1137085
 *  @see http://doc.akka.io/docs/akka/current/scala/extending-akka.html
 */
private[framework] class ReflectionExtension(access: DynamicAccess) extends Extension {
  // Loads the Class with the provided Fully Qualified Class Name using the given DynamicAccess
  // Throws exception if it fails to load
  def actorClassFor(fqcn: String): Try[Class[_ <: Actor]] = access.getClassFor[Actor](fqcn)
  def classFor[T](fqcn: String)(implicit tag: ClassTag[T]) = access.getClassFor[T](fqcn)
}

private[framework] object ReflectionExtension extends ExtensionId[ReflectionExtension] with ExtensionIdProvider {
  override def lookup = ReflectionExtension
  override def createExtension(system: ExtendedActorSystem) = new ReflectionExtension(system.dynamicAccess)

  /**
   * Java API: retrieve the Count extension for the given system.
   */
  override def get(system: ActorSystem): ReflectionExtension = super.get(system)
}

private[framework] trait Reflection { self: Actor =>
  def actorClassFor(fqcn: String): Try[Class[_ <: Actor]] = ReflectionExtension(context.system).actorClassFor(fqcn)
  def classFor[T](fqcn: String)(implicit tag: ClassTag[T]) = ReflectionExtension(context.system).classFor(fqcn)
}
