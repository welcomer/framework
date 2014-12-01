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

import me.welcomer.framework.pico.repository.PicoPdsRepositoryComponent
import me.welcomer.framework.utils.DBUtils
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes

/**
 * PicoPds Service Component
 *
 * Provides a PDS service scoped to the provided picoUUID.
 *
 * You must implement the following when using:
 * {{{
 * protected def _picoPdsService(picoUUID: String): PicoPdsService
 * }}}
 */
trait PicoPdsServiceComponent { self: PicoPdsRepositoryComponent =>
  protected def _picoPdsService(picoUUID: String): PicoPdsService

  /**
   * PicoPds Service
   *
   * You must implement the following when using:
   * {{{
   * implicit val picoUUID: String
   * }}}
   */
  trait PicoPdsService {
    /**
     * The picoUUID used for scoping the PDS service.
     */
    implicit val picoUUID: String

    // TODO: Convert all BSONDocument returns to JsObject
    // TODO: Replace Option with Try? (so we can pass exceptions back if needed)

    /**
     * Store a single item in the specified (or default) namespace.
     *
     * @param key The key of the item to retrieve
     * @param The item to be stored
     * @param namespace An optional namespace to locate the key in
     * @return The results of the store
     */
    def storeItem[T](key: String, item: T, namespace: Option[String] = None)(implicit ec: ExecutionContext, writeT: Writes[T]): Future[JsObject]

    // TODO: Remove this for the generic method when we switch BSON to JSON
    def storeItemWithJsValue(key: String, item: JsValue, namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject]

    // TODO: Remove this for the generic method when we switch BSON to JSON
    // TODO: Maybe rename this to merge? Or maybe just make storeItem smarter (with a shouldMerge type flag)
    def storeAllItemsWithJsObject(items: JsObject, namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject]

    /**
     * Push an item into array in the specified (or default) namespace.
     *
     * @param arrayKey The key of the array to modify
     * @param item The item to push into the array
     * @param namespace An optional namespace to locate the key in
     * @param selector An optional selection query
     * @param unique Should the item be unique in the array? (won't be added if already exists)
     * @return The results of the store
     */
    def pushArrayItem(
      arrayKey: String,
      item: JsValue,
      namespace: Option[String] = None,
      selector: Option[JsObject] = None,
      unique: Boolean = false)(implicit ec: ExecutionContext): Future[JsObject]

    /**
     * Retrieve the query/projection in the specified (or default) namespace.
     *
     * @param query The selection query
     * @param projection The fields to select
     * @param namespace An optional namespace to locate the key in
     */
    def retrieve(
      query: JsObject,
      projection: JsObject,
      namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[Option[JsObject]]

    /**
     * Retrieve a single item in the specified (or default) namespace from the PDS as type T.
     *
     * @param key The key of the item to retrieve
     * @param namespace An optional namespace to locate the key in
     * @return The retrieved item as type Option[T] or None
     */
    def retrieveItem[T](key: String, namespace: Option[String] = None)(implicit ec: ExecutionContext, readsT: Reads[T]): Future[Option[T]]

    /**
     * Retrieve all items in the specified (or default) namespace from the PDS.
     *
     *  @param namespace An optional namespace to locate the key in
     *  @param filter A JsObject containing the keys to be included
     *  @return The retrieved items or None
     */
    def retrieveAllItems(namespace: Option[String] = None, filter: Option[List[String]] = None)(implicit ec: ExecutionContext): Future[Option[JsObject]]

    //    /**
    //     * Retrieve all items in the specified (or default) namespace from the PDS as type T.
    //     *
    //     *  @param namespace An optional namespace to locate the key in
    //     *  @param filter A JsObject containing the keys to be included
    //     *  @return The retrieved items or None
    //     */
    //    def retrieveAllItems[T](namespace: Option[String] = None, filter: Option[List[String]] = None)(implicit ec: ExecutionContext, readsT: Reads[T]): Future[Option[T]]

    /**
     * Retrieve all namespaces and their items from the PDS.
     *
     * @return The retrieved items or None
     */
    def retrieveAllNamespaces(implicit ec: ExecutionContext): Future[Option[JsObject]]

    /**
     * Remove a single item in the specified (or default) namespace from the PDS.
     *
     * @param key The key of the item to remove
     * @param namespace An optional namespace to locate the key in
     * @return The results of the removal
     */
    def removeItem(key: String, namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject]

    /**
     * Remove all items in the specified (or default) namespace from the PDS.
     *
     *  @param namespace An optional namespace to locate the key in
     *  @return The results of the removal
     */
    def removeAllItems(namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject]

    /**
     * Remove all namespaces and their items from the PDS.
     *
     * @return The results of the removal
     */
    def removeAllNamespaces(implicit ec: ExecutionContext): Future[JsObject]
  }
}

/**
 * Implementation of [[me.welcomer.framework.pico.service.PicoPdsServiceComponent]]
 */
trait PicoPdsServiceComponentImpl extends PicoPdsServiceComponent { self: PicoPdsRepositoryComponent =>
  override protected def _picoPdsService(picoUUID: String): PicoPdsService = new PicoPdsServiceImpl()(picoUUID)

  /**
   * Implementation of [[me.welcomer.framework.pico.service.PicoPdsServiceComponent#PicoPdsService]]
   */
  private[this] class PicoPdsServiceImpl(implicit val picoUUID: String) extends PicoPdsService with DBUtils {
    import reactivemongo.bson.DefaultBSONHandlers._

    override def storeItem[T](key: String, item: T, namespace: Option[String] = None)(implicit ec: ExecutionContext, writeT: Writes[T]): Future[JsObject] = {
      _picoPdsRepository.setScopedKey(namespace, Some(key), item)
    }

    // TODO: Remove this for the generic method when we switch BSON to JSON
    override def storeItemWithJsValue(key: String, item: JsValue, namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject] = {
      _picoPdsRepository.setScopedKey(namespace, Some(key), item)
    }

    // TODO: Remove this for the generic method when we switch BSON to JSON
    override def storeAllItemsWithJsObject(items: JsObject, namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject] = {
      _picoPdsRepository.mergeScoped(
        namespace,
        None,
        items)
    }

    override def pushArrayItem(
      arrayKey: String,
      item: JsValue,
      namespace: Option[String] = None,
      selector: Option[JsObject] = None,
      unique: Boolean = false)(implicit ec: ExecutionContext): Future[JsObject] = {
      _picoPdsRepository.pushScopedArrayItem(arrayKey, item, namespace, selector, unique)
    }

    override def retrieve(
      query: JsObject,
      projection: JsObject,
      namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[Option[JsObject]] = {
      _picoPdsRepository.findScoped(query, projection, namespace)
    }

    override def retrieveItem[T](key: String, namespace: Option[String] = None)(implicit ec: ExecutionContext,
                                                                                readsT: Reads[T]): Future[Option[T]] = {
      // TODO: Push this functionality down into the repos level?
      _picoPdsRepository.findScopedDocument(namespace) map {
        case Some(result) => (result \ key).asOpt[T]
        case None => None
      }
    }

    override def retrieveAllItems(namespace: Option[String] = None, filter: Option[List[String]] = None)(implicit ec: ExecutionContext): Future[Option[JsObject]] = {
      _picoPdsRepository.findScopedDocument(namespace, filter)
    }

    //    override def retrieveAllItems[T](namespace: Option[String] = None, filter: Option[List[String]] = None)(implicit ec: ExecutionContext, readsT: Reads[T]): Future[Option[T]] = {
    //      _picoPdsRepository.findScopedDocument(namespace, filter) map {
    //        case Some(result) => result.asOpt[T]
    //        case None => None
    //      }
    //    }

    override def retrieveAllNamespaces(implicit ec: ExecutionContext): Future[Option[JsObject]] = {
      _picoPdsRepository.findScopedDocument(None, ignoreNamespace = true)
    }

    override def removeItem(key: String, namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject] = {
      _picoPdsRepository.unsetScopedKey(namespace, Some(key))
    }

    override def removeAllItems(namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject] = {
      _picoPdsRepository.unsetScopedKey(namespace)
    }

    override def removeAllNamespaces(implicit ec: ExecutionContext): Future[JsObject] = {
      _picoPdsRepository.setScopedKey(None, None, Json.obj(), true)
    }

  }
}