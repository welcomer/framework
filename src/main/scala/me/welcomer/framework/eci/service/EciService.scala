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
package me.welcomer.framework.eci.service

import me.welcomer.framework.eci.repository.EciRepositoryComponent
import me.welcomer.framework.models.ECI
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait EciServiceComponent { self: EciRepositoryComponent =>
  protected def _eciService: EciService

  trait EciService {
    def generate(picoUUID: String, description: Option[String] = None)(implicit ec: ExecutionContext): Future[ECI]
  }
}

trait EciServiceComponentImpl extends EciServiceComponent { self: EciRepositoryComponent =>
  override protected def _eciService: EciService = new EciServiceImpl

  private[this] class EciServiceImpl extends EciService {
    override def generate(picoUUID: String, description: Option[String] = None)(implicit ec: ExecutionContext): Future[ECI] = {
      val newEci = ECI(picoUUID = picoUUID, description = description.getOrElse(""))

      for {
        saveResult <- _eciRepository.save(newEci)
      } yield { newEci }
    }
  }
}