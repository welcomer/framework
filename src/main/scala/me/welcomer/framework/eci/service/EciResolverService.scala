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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import me.welcomer.framework.eci.repository.EciRepositoryComponent
import me.welcomer.framework.utils.DBUtils
import me.welcomer.framework.models.ECISchema

// Interface
trait EciResolverServiceComponent { self: EciRepositoryComponent =>
  protected def _eciResolverService: EciResolverService

  trait EciResolverService {
    def resolveEciToPicoUUID(eci: String)(implicit ec: ExecutionContext): Future[Option[String]]
  }
}

// Implementation
trait EciResolverServiceComponentImpl extends EciResolverServiceComponent { self: EciRepositoryComponent =>
  override protected def _eciResolverService: EciResolverService = new EciResolverServiceImpl

  private[this] class EciResolverServiceImpl extends EciResolverService {
    override def resolveEciToPicoUUID(eci: String)(implicit ec: ExecutionContext) = {
      _eciRepository.findOne(ECISchema.eci -> eci) map { optionEci =>
        optionEci match {
          case Some(eciModel) => Some(eciModel.picoUUID)
          case None           => None
        }
      }
    }
  }
}
