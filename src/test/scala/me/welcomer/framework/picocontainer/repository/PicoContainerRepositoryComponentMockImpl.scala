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
import play.api.libs.iteratee.Iteratee
import reactivemongo.bson.BSONDocument

trait PicoContainerRepositoryComponentMockImpl extends PicoContainerRepositoryComponent {
  override protected def _picoContainerRepository: PicoContainerRepository = new PicoContainerRepositoryMockImpl

  class PicoContainerRepositoryMockImpl extends PicoContainerRepository {

    def save(model: Pico)(implicit ec: ExecutionContext): Future[Unit] = {
      ???
    }

    def findOne(keyValue: (String, String))(implicit ec: ExecutionContext): Future[Option[Pico]] = {
      ???
    }

    def enumerate[B](query: BSONDocument, processor: Iteratee[Pico, B])(implicit ec: ExecutionContext): Future[Iteratee[Pico, B]] = {
      ???
    }
  }
}