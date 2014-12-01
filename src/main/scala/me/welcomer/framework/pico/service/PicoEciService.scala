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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import me.welcomer.framework.eci.service.EciServiceComponent
import me.welcomer.framework.models.ECI

trait PicoEciServiceComponent { self: EciServiceComponent =>
  protected def _picoEciService(picoUUID: String): PicoEciService

  trait PicoEciService {
    implicit val picoUUID: String

    def generate(description: Option[String] = None)(implicit ec: ExecutionContext): Future[ECI]
  }
}

trait PicoEciServiceComponentImpl extends PicoEciServiceComponent { self: EciServiceComponent =>
  override protected def _picoEciService(picoUUID: String): PicoEciService = new PicoEciServiceImpl()(picoUUID)

  private[this] class PicoEciServiceImpl(implicit val picoUUID: String) extends PicoEciService {
    override def generate(description: Option[String] = None)(implicit ec: ExecutionContext): Future[ECI] = {
      _eciService.generate(picoUUID, description)
    }
  }
}