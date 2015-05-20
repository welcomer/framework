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

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import scala.collection.mutable.HashMap

import akka.actor.Actor
import akka.actor.ActorLogging

import play.api.libs.json._

import me.welcomer.framework.pico.EventedModule
import me.welcomer.framework.pico.EventedFunction
import me.welcomer.framework.pico.EventedResult
import me.welcomer.framework.pico.EventedFailure
import me.welcomer.framework.pico.UnknownModule

trait PicoModuleDSL extends AnyRef
  with PicoRaiseRemoteEventDSL
  with PicoRaisePicoEventDSL { this: Actor with ActorLogging =>

  import scala.language.implicitConversions

  private[this] var loadedModules: HashMap[String, EventedModule] = HashMap()

  def use: UseModule = new UseModule {}

  def module(alias: String) = new ModuleCallDSL(alias: String)

  implicit def string2ModuleCallDSL(s: String): ModuleCallDSL = module(s)

  protected trait UseModule {
    def module(id: String) = new FluentModuleConfiguration(EventedModule(id), None)
    def module(m: EventedModule) = new FluentModuleConfiguration(m, None)
  }

  protected class FluentModuleConfiguration(module: EventedModule, moduleAlias: Option[String]) {
    def withAlias(a: String) = this.copy(moduleAlias = Some(a))
    def alias = withAlias _

    def atEci(eci: String) = this.copy(module = module.copy(eci = Some(eci)))
    def at = atEci _

    def configure(c: JsObject) = this.copy(module = module.copy(config = c))
    def withConfiguration = configure _

    def timeout(t: FiniteDuration) = this.copy(module = module.copy(timeout = t))

    def get: (EventedModule, Option[String]) = (module, moduleAlias)
    def getModule: EventedModule = module
    def getAlias: Option[String] = moduleAlias

    def save(): Unit = moduleAlias match {
      case Some(a) => loadedModules += (a -> module)
      case None    => loadedModules += (module.id -> module)
    }
    def andSave = save _

    private def copy(module: EventedModule = module, moduleAlias: Option[String] = moduleAlias) = new FluentModuleConfiguration(module, moduleAlias)
  }

  protected class ModuleCallDSL(moduleAlias: String) {
    import akka.pattern.ask
    import context.dispatcher

    def call(name: String, args: JsObject = Json.obj()): Future[EventedResult[_]] = {
      val module = loadedModules.get(moduleAlias)

      module match {
        case Some(m) => {
          val func = EventedFunction(m, name, args)

          m.eci match {
            case Some(eci) => raiseRemoteEventWithReplyTo(func)
            case None      => log.error("TODO: Implement local module call"); ??? //raiseLocalEvent(func)
          }
        }
        case None => Future(EventedFailure(UnknownModule(moduleAlias)))
      }
    }
  }
}
