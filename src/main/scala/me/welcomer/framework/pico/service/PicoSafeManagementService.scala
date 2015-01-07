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
import me.welcomer.framework.models.ECI
import me.welcomer.framework.models.Pico
import me.welcomer.framework.picocontainer.service.PicoManagementServiceComponent

// Note: This will only contain safe management methods and/or scoped to effect only the local pico. These are likely to be exposed to users
trait PicoSafeManagementServiceComponent {
  self: PicoManagementServiceComponent =>
  protected def _picoSafeManagementService: PicoSafeManagementService

  trait PicoSafeManagementService {
    /**
     * Create a new pico.
     *
     * @param rulesets Set of rulesets to be installed on this pico.
     * @return The future ECI of the created pico
     */
    def createNewPico(rulesets: Set[String] = Set())(implicit ec: ExecutionContext): Future[String]
  }
}

trait PicoSafeManagementServiceComponentImpl extends PicoSafeManagementServiceComponent {
  self: PicoManagementServiceComponent =>
  override protected def _picoSafeManagementService: PicoSafeManagementService = new PicoSafeManagementServiceImpl()

  private[this] class PicoSafeManagementServiceImpl() extends PicoSafeManagementService {
    override def createNewPico(rulesets: Set[String] = Set())(implicit ec: ExecutionContext): Future[String] = {
      _picoManagementService.createNewPico(rulesets) map {
        case (_, ECI(_, eci, _, description)) => eci
      }
    }
  }
}