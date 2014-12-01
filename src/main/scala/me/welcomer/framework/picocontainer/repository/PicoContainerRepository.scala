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
package me.welcomer.framework.picocontainer.repository

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import me.welcomer.framework.models.Pico
import me.welcomer.framework.models.Pico.PicoReader
import me.welcomer.framework.models.Pico.PicoWriter
import me.welcomer.framework.utils.GenericRepositoryReactiveMongoImpl
import play.api.libs.iteratee.Iteratee
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.BSONDocument

/**
 * PicoContainer Repository Component
 */
private[framework] trait PicoContainerRepositoryComponent {
  protected def _picoContainerRepository: PicoContainerRepository

  /**
   * PicoContainer Repository
   */
  protected trait PicoContainerRepository {
    def save(model: Pico)(implicit ec: ExecutionContext): Future[Unit]

    def findOne(keyValue: (String, String))(implicit ec: ExecutionContext): Future[Option[Pico]]

    def enumerate[B](query: BSONDocument, processor: Iteratee[Pico, B])(implicit ec: ExecutionContext): Future[Iteratee[Pico, B]]
  }
}

/**
 * ReactiveMongo Implementation of [[me.welcomer.framework.picocontainer.repository.PicoContainerRepositoryComponent]]
 */
private[framework] trait PicoContainerRepositoryComponentReactiveMongoImpl extends PicoContainerRepositoryComponent {
  protected val _picoContainerCollection: BSONCollection
  override protected def _picoContainerRepository: PicoContainerRepository = new PicoContainerRepositoryReactiveMongoImpl(_picoContainerCollection)

  /**
   * ReactiveMongo Implementation of [[me.welcomer.framework.picocontainer.repository.PicoContainerRepositoryComponent#PicoContainerRepository]]
   */
  private[this] class PicoContainerRepositoryReactiveMongoImpl(val dbCollection: BSONCollection) extends PicoContainerRepository
    with GenericRepositoryReactiveMongoImpl[Pico] {
    import me.welcomer.framework.models.Pico._

    implicit val reader = PicoReader
    implicit val writer = PicoWriter
  }
}
