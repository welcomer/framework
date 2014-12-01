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
package me.welcomer.framework.eci.repository

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import me.welcomer.framework.models.ECI

trait EciRepositoryComponentMockImpl extends EciRepositoryComponent {
  override protected def _eciRepository: EciRepository = new EciRepositoryMockImpl

  class EciRepositoryMockImpl extends EciRepository {
    def save(model: ECI)(implicit ec: ExecutionContext): Future[Unit] = {
      Future.successful(Unit)
    }

    def findOne(keyValue: (String, String))(implicit ec: ExecutionContext): Future[Option[ECI]] = {
      Future(Option(ECI("eci", "picoUUID", "Some arbitrary description")))
    }
  }
}