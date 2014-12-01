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
package me.welcomer.framework.pico.repository

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import play.api.libs.json._

trait PicoPdsRepositoryComponentMockImpl extends PicoPdsRepositoryComponent {
  override protected def _picoPdsRepository: PicoPdsRepository = new PicoPdsRepositoryMockImpl

  /**
   * ReactiveMongo Implementation of [[me.welcomer.framework.pico.repository.PicoPdsRepositoryComponent#PicoPdsRepository]]
   */
  class PicoPdsRepositoryMockImpl extends PicoPdsRepository {

    def initialise(picoUUID: String)(implicit ec: ExecutionContext): Future[JsObject] = {
      ???
    }

    def findScopedDocument(
      namespace: Option[String],
      filter: Option[List[String]] = None,
      ignoreNamespace: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[Option[JsObject]] = {
      ???
    }

    def findScoped(
      query: JsObject,
      projection: JsObject,
      namespaceOpt: Option[String])(implicit ec: ExecutionContext, picoUUID: String): Future[Option[JsObject]] = {
      ???
    }

    def setScopedKey[T](
      namespaceOption: Option[String],
      keyOption: Option[String] = None,
      value: T,
      ignoreNamespace: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String, writeT: Writes[T]): Future[JsObject] = {
      ???
    }

    def mergeScoped(
      namespaceOption: Option[String],
      keyOption: Option[String] = None,
      values: JsObject,
      ignoreNamespace: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject] = {
      ???
    }

    def pushScopedArrayItem(
      arrayKey: String,
      item: JsValue,
      namespaceOpt: Option[String],
      selector: Option[JsObject] = None,
      unique: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject] = {
      ???
    }

    def unsetScopedKey(
      namespaceOption: Option[String],
      keyOption: Option[String] = None)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject] = {
      ???
    }
  }
}