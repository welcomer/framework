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
package me.welcomer.framework.picocontainer.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import me.welcomer.framework.eci.service.EciServiceComponent
import me.welcomer.framework.models.ECI
import me.welcomer.framework.models.Pico
import me.welcomer.framework.pico.repository.PicoPdsRepositoryComponent
import me.welcomer.framework.picocontainer.repository.PicoContainerRepositoryComponent

trait PicoManagementServiceComponent {
  self: PicoContainerRepositoryComponent with EciServiceComponent with PicoPdsRepositoryComponent =>
  protected def _picoManagementService: PicoManagementService

  trait PicoManagementService {
    /**
     * Create a new pico.
     *
     * @param rulesets Set of rulesets to be installed on this pico.
     */
    def createNewPico(rulesets: Set[String] = Set())(implicit ec: ExecutionContext): Future[(Pico, ECI)]
  }
}

trait PicoManagementServiceComponentImpl extends PicoManagementServiceComponent {
  self: PicoContainerRepositoryComponent with EciServiceComponent with PicoPdsRepositoryComponent =>
  override protected def _picoManagementService: PicoManagementService = new PicoManagementServiceImpl()

  private[this] class PicoManagementServiceImpl() extends PicoManagementService {
    override def createNewPico(rulesets: Set[String] = Set())(implicit ec: ExecutionContext): Future[(Pico, ECI)] = {
      val newPico = Pico(rulesets)

      for {
        saveResult <- _picoContainerRepository.save(newPico)
        eci <- _eciService.generate(newPico.picoUUID, Some("Initial/Root ECI"))
        initialisePdsResult <- _picoPdsRepository.initialise(newPico.picoUUID)
      } yield { (newPico, eci) }
    }
  }
}