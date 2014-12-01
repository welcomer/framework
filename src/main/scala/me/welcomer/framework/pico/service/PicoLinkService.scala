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
package me.welcomer.framework.pico.service

import scala.concurrent.duration._
import scala.language.postfixOps
import akka.actor.ActorSystem
import akka.util.Timeout
import me.welcomer.link.mandrill.MandrillRepository
import me.welcomer.link.mandrill.MandrillRepositoryComponent
import me.welcomer.link.RawJsonApi

trait PicoLinkServiceComponent { self: MandrillRepositoryComponent =>
  protected def _picoLinkService(picoUUID: String)(implicit system: ActorSystem): PicoLinkService

  trait PicoLinkService {
    implicit val system: ActorSystem
    implicit val picoUUID: String

    def mandrill(key: String)(implicit timeout: Timeout = 5 seconds): MandrillRepository

    def rawJsonApi(implicit timeout: Timeout = 5 seconds): RawJsonApi
  }
}

trait PicoLinkServiceComponentImpl extends PicoLinkServiceComponent { self: MandrillRepositoryComponent =>
  override protected def _picoLinkService(picoUUID: String)(implicit system: ActorSystem): PicoLinkService = new PicoLinkServiceImpl()(system, picoUUID)

  private[this] class PicoLinkServiceImpl(implicit val system: ActorSystem, val picoUUID: String) extends PicoLinkService {

    override def mandrill(key: String)(implicit timeout: Timeout = 5 seconds): MandrillRepository = {
      _mandrillRepository(key)
    }

    override def rawJsonApi(implicit timeout: Timeout = 5 seconds): RawJsonApi = new RawJsonApi
  }
}