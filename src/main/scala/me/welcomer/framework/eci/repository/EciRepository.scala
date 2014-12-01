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

import me.welcomer.framework.models.ECI
import me.welcomer.framework.models.ECI.ECIReader
import me.welcomer.framework.models.ECI.ECIWriter
import me.welcomer.framework.utils.GenericRepository
import me.welcomer.framework.utils.GenericRepositoryReactiveMongoImpl
import reactivemongo.api.collections.default.BSONCollection
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

// Interface
trait EciRepositoryComponent {
  protected def _eciRepository: EciRepository

  protected trait EciRepository {
    def save(model: ECI)(implicit ec: ExecutionContext): Future[Unit]

    def findOne(keyValue: (String, String))(implicit ec: ExecutionContext): Future[Option[ECI]]
  }
}

// Implementation
trait EciRepositoryComponentReactiveMongoImpl extends EciRepositoryComponent {
  protected val _eciCollection: BSONCollection
  override protected def _eciRepository: EciRepository = new EciRepositoryReactiveMongoImpl(_eciCollection)

  private[this] class EciRepositoryReactiveMongoImpl(val dbCollection: BSONCollection)
    extends EciRepository
    with GenericRepositoryReactiveMongoImpl[ECI] {
    import me.welcomer.framework.models.ECI._

    implicit val reader = ECIReader
    implicit val writer = ECIWriter
  }
}
