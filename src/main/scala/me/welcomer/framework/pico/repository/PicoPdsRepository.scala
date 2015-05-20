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

import me.welcomer.framework.models.PicoSchema
import me.welcomer.framework.utils.DBImplicits
import me.welcomer.framework.utils.GenericJsonRepository
import me.welcomer.framework.utils.GenericRepositoryReactiveMongoJsonImpl
import me.welcomer.utils.JsonUtils
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.Writes
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.bson.BSONDocument

/**
 * PicoPds Repository Component
 */
private[welcomer] trait PicoPdsRepositoryComponent {
  protected def _picoPdsRepository: PicoPdsRepository

  /**
   * PicoPds Repository
   */
  protected trait PicoPdsRepository {
    def initialise(picoUUID: String)(implicit ec: ExecutionContext): Future[JsObject]

    def findScopedDocument(
      namespace: Option[String],
      filter: Option[List[String]] = None,
      ignoreNamespace: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[Option[JsObject]]

    def findScoped(
      query: JsObject,
      projection: JsObject,
      namespaceOpt: Option[String])(implicit ec: ExecutionContext, picoUUID: String): Future[Option[JsObject]]

    def setScopedKey[T](
      namespaceOption: Option[String],
      keyOption: Option[String] = None,
      value: T,
      ignoreNamespace: Boolean = false,
      selector: Option[JsObject] = None)(implicit ec: ExecutionContext, picoUUID: String, writeT: Writes[T]): Future[JsObject]

    def mergeScoped(
      namespaceOption: Option[String],
      keyOption: Option[String] = None,
      values: JsObject,
      ignoreNamespace: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject]

    def pushScopedArrayItem(
      arrayKey: String,
      item: JsValue,
      namespaceOpt: Option[String],
      selector: Option[JsObject] = None,
      unique: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject]

    def pushScopedArrayItems(
      arrayKey: String,
      items: Seq[JsValue],
      namespaceOpt: Option[String],
      selector: Option[JsObject] = None,
      unique: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject]

    def unsetScopedKey(
      namespaceOption: Option[String],
      keyOption: Option[String] = None)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject]
  }
}

/**
 * ReactiveMongo Implementation of [[me.welcomer.framework.pico.repository.PicoPdsRepositoryComponent]]
 */
private[framework] trait PicoPdsRepositoryComponentReactiveMongoImpl extends PicoPdsRepositoryComponent {
  protected val _picoPdsCollection: JSONCollection
  override protected def _picoPdsRepository: PicoPdsRepository = new PicoPdsRepositoryReactiveMongoImpl(_picoPdsCollection)

  /**
   * ReactiveMongo Implementation of [[me.welcomer.framework.pico.repository.PicoPdsRepositoryComponent#PicoPdsRepository]]
   */
  private[this] class PicoPdsRepositoryReactiveMongoImpl(val jsonCollection: JSONCollection) extends PicoPdsRepository
    with GenericRepositoryReactiveMongoJsonImpl[JsValue]
    with JsonUtils {
    import reactivemongo.bson.DefaultBSONHandlers._

    implicit val formatT = DBImplicits.JsValueFormatImpl

    val namespacesKey = "namespaces"
    val defaultNamespaceKey = "_default"

    private def buildPicoScope(picoUUID: String): JsObject = Json.obj(PicoSchema.picoUUID -> picoUUID)

    private def buildNamespacePath(namespaceOption: Option[String], ignoreNamespace: Boolean = false): String = {
      namespaceOption match {
        case _ if ignoreNamespace => namespacesKey
        case Some(namespace)      => s"$namespacesKey.$namespace"
        case None                 => s"$namespacesKey.$defaultNamespaceKey"
      }
    }

    override def initialise(picoUUID: String)(implicit ec: ExecutionContext): Future[JsObject] = {
      val json = Json.obj("picoUUID" -> picoUUID)

      update(json, json, upsert = true)
    }

    // TODO: Deprecate/remove this in favour of findScoped?
    @deprecated("Remove this in favour of findScoped?", "0.0.1")
    override def findScopedDocument(namespaceOption: Option[String], filter: Option[List[String]] = None, ignoreNamespace: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[Option[JsObject]] = {
      // Useful when we switch to JSON? https://www.playframework.com/documentation/2.3.x/ScalaJsonTransformers
      val namespacePath = buildNamespacePath(namespaceOption, ignoreNamespace)
      val filterDoc = filter.map {
        _.foldLeft(Json.obj()) { (json, key) =>
          json ++ dbIncludeKeyJsObject(namespacePath + "." + key)
        }
      } getOrElse Json.obj()
      //      val bsonFilterDoc = BSONFormats.BSONDocumentFormat.reads(filterDoc).getOrElse(BSONDocument())

      findOne(
        buildPicoScope(picoUUID),
        dbIncludeKeyJsObject(namespacePath) ++ filterDoc) map {
          _.map { jsonDeepPath(_, namespacePath).asOpt[JsObject] } getOrElse None
        }
    }

    override def findScoped(
      query: JsObject,
      projection: JsObject,
      namespaceOpt: Option[String])(implicit ec: ExecutionContext, picoUUID: String): Future[Option[JsObject]] = {
      val namespacePath = buildNamespacePath(namespaceOpt, ignoreNamespace = false)

      val scopedQuery = buildPicoScope(picoUUID) ++ scopeAllKeys(namespacePath, query)
      val scopedProjection = scopeAllKeys(namespacePath, projection)

      findOne(
        scopedQuery,
        scopedProjection) map {
          // Ensure the root key is the namespace
          _.map { jsonDeepPath(_, namespacePath).asOpt[JsObject] } getOrElse None
        }
    }

    override def setScopedKey[T](
      namespaceOption: Option[String],
      keyOption: Option[String] = None,
      value: T,
      ignoreNamespace: Boolean = false,
      selector: Option[JsObject] = None)(implicit ec: ExecutionContext, picoUUID: String, writeT: Writes[T]): Future[JsObject] = {
      val namespacePath = buildNamespacePath(namespaceOption, ignoreNamespace)
      val scopedSelector = scopeAllKeys(namespacePath, selector.getOrElse(Json.obj()))
      val fullKeyPath = buildNamespacedKeyPath(namespacePath, keyOption)

      setKeyValue(
        buildPicoScope(picoUUID) ++ scopedSelector,
        fullKeyPath -> value,
        upsert = true)
    }

    override def mergeScoped(
      namespaceOption: Option[String],
      keyOption: Option[String] = None,
      values: JsObject,
      ignoreNamespace: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject] = {
      val namespacePath = buildNamespacePath(namespaceOption, ignoreNamespace)
      val fullKeyPath = buildNamespacedKeyPath(namespacePath, keyOption)

      val scopedValues = scopeAllKeys(fullKeyPath, values)

      setKeys(
        buildPicoScope(picoUUID),
        scopedValues,
        upsert = true,
        multi = false)
    }

    override def pushScopedArrayItem(
      arrayKey: String,
      item: JsValue,
      namespaceOpt: Option[String],
      selector: Option[JsObject] = None,
      unique: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject] = {
      val namespacePath = buildNamespacePath(namespaceOpt, false)
      val scopedSelector = scopeAllKeys(namespacePath, selector.getOrElse(Json.obj()))
      val scopedArrayKey = buildNamespacedKeyPath(namespacePath, Some(arrayKey))

      pushArrayItem(
        buildPicoScope(picoUUID) ++ scopedSelector,
        scopedArrayKey,
        item,
        unique,
        upsert = true)
    }

    override def pushScopedArrayItems(
      arrayKey: String,
      items: Seq[JsValue],
      namespaceOpt: Option[String],
      selector: Option[JsObject] = None,
      unique: Boolean = false)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject] = {
      val namespacePath = buildNamespacePath(namespaceOpt, false)
      val scopedSelector = scopeAllKeys(namespacePath, selector.getOrElse(Json.obj()))
      val scopedArrayKey = buildNamespacedKeyPath(namespacePath, Some(arrayKey))

      pushArrayItems(
        buildPicoScope(picoUUID) ++ scopedSelector,
        scopedArrayKey,
        items,
        unique,
        upsert = true)
    }

    override def unsetScopedKey(
      namespaceOption: Option[String],
      keyOption: Option[String] = None)(implicit ec: ExecutionContext, picoUUID: String): Future[JsObject] = {
      val namespacePath = buildNamespacePath(namespaceOption, false)
      val fullKeyPath = buildNamespacedKeyPath(namespacePath, keyOption)

      unsetKey(
        buildPicoScope(picoUUID),
        fullKeyPath)
    }
  }
}
