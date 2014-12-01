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
package me.welcomer.framework.utils

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.Writes
import play.modules.reactivemongo.json.BSONFormats
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.BSONDocument
import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONDocumentWriter
import reactivemongo.bson.DefaultBSONHandlers.BSONDocumentIdentity
import reactivemongo.core.commands.LastError

// Interface
@deprecated("Use GenericJsonRepository instead")
trait GenericRepository[T] {
  // Read

  def findOne(key: String, value: String)(implicit ec: ExecutionContext): Future[Option[T]]
  def findOne(keyValue: (String, String))(implicit ec: ExecutionContext): Future[Option[T]]
  def findOne(query: BSONDocument, projection: BSONDocument = BSONDocument())(implicit ec: ExecutionContext): Future[Option[T]]

  def findAll(implicit ec: ExecutionContext): Future[Stream[T]]

  def enumerate[B](query: BSONDocument, processor: Iteratee[T, B])(implicit ec: ExecutionContext): Future[Iteratee[T, B]]
  def enumerateAll[B](processor: Iteratee[T, B])(implicit ec: ExecutionContext): Future[Iteratee[T, B]]

  // Write

  def save(model: T)(implicit ec: ExecutionContext): Future[Unit]

  def insert(model: T)(implicit ec: ExecutionContext): Future[Unit]

  // removeOne
  // removeMultiple
}

trait GenericJsonRepository[T] {
  def findOne(
    selector: JsObject,
    projection: JsObject = Json.obj())(implicit ec: ExecutionContext): Future[Option[T]]

  def findAll(implicit ec: ExecutionContext): Future[Stream[T]]

  def enumerate[B](
    selector: JsObject,
    processor: Iteratee[T, B])(implicit ec: ExecutionContext): Future[Iteratee[T, B]]

  def enumerateAll[B](processor: Iteratee[T, B])(implicit ec: ExecutionContext): Future[Iteratee[T, B]]

  def save(model: T)(implicit ec: ExecutionContext): Future[JsObject]

  def insert(model: T)(implicit ec: ExecutionContext): Future[JsObject]

  // removeOne
  // removeMultiple

  def update(
    selector: JsObject,
    update: JsObject,
    upsert: Boolean = false,
    multi: Boolean = false)(implicit ec: ExecutionContext, writesT: Writes[T]): Future[JsObject]

  def setKeys(
    selector: JsObject,
    update: JsObject,
    upsert: Boolean = false,
    multi: Boolean = false)(implicit ec: ExecutionContext, writesT: Writes[T]): Future[JsObject]

  def setKeyValues[T](
    selector: JsObject,
    setKeyValues: List[(String, T)],
    upsert: Boolean = false,
    multi: Boolean = false)(implicit ec: ExecutionContext, writesT: Writes[T]): Future[JsObject]

  def setKeyValue[T](
    selector: JsObject,
    setKeyValue: (String, T),
    upsert: Boolean = false,
    multi: Boolean = false)(implicit ec: ExecutionContext, writesT: Writes[T]): Future[JsObject]

  def pushArrayItem(
    selector: JsObject,
    arrayKey: String,
    item: JsValue,
    unique: Boolean = false,
    upsert: Boolean = false,
    multi: Boolean = false)(implicit ec: ExecutionContext): Future[JsObject]

  def unsetKeys(
    selector: JsObject,
    unsetKeys: List[String],
    multi: Boolean = false)(implicit ec: ExecutionContext): Future[JsObject]

  def unsetKey(
    selector: JsObject,
    unsetKey: String,
    multi: Boolean = false)(implicit ec: ExecutionContext): Future[JsObject]
}

@deprecated("Use GenericRepositoryReactiveMongoJsonImpl instead")
trait GenericRepositoryReactiveMongoImpl[T] extends GenericRepository[T] with DBUtils {
  import reactivemongo.bson.DefaultBSONHandlers._

  val dbCollection: BSONCollection

  implicit val reader: BSONDocumentReader[T]
  implicit val writer: BSONDocumentWriter[T]

  // Read

  override def findOne(key: String, value: String)(implicit ec: ExecutionContext): Future[Option[T]] = {
    findOne(dbQuery(key, value))
  }

  override def findOne(keyValue: (String, String))(implicit ec: ExecutionContext): Future[Option[T]] = {
    findOne(dbQuery(keyValue))
  }

  override def findOne(query: BSONDocument, projection: BSONDocument = BSONDocument())(implicit ec: ExecutionContext): Future[Option[T]] = {
    dbCollection.find(query, projection).one[T]
  }

  override def findAll(implicit ec: ExecutionContext): Future[Stream[T]] = {
    dbCollection.find(dbQueryAll)
      .cursor[T]
      .collect[Stream]()
  }

  override def enumerate[B](query: BSONDocument, processor: Iteratee[T, B])(implicit ec: ExecutionContext): Future[Iteratee[T, B]] = {
    dbEnumerator(
      dbCollection,
      query,
      processor)
  }

  override def enumerateAll[B](processor: Iteratee[T, B])(implicit ec: ExecutionContext): Future[Iteratee[T, B]] = {
    enumerate[B](dbQueryAll, processor)
  }

  // Write

  override def save(model: T)(implicit ec: ExecutionContext): Future[Unit] = {
    dbCollection.save(model) flatMap { lastError =>
      if (lastError.inError) Future.failed(lastError.getCause())
      else Future()
    }
  }

  override def insert(model: T)(implicit ec: ExecutionContext): Future[Unit] = {
    dbCollection.insert(model) map { lastError =>
      if (lastError.inError) Future.failed(lastError.getCause())
      else Future()
    }
  }
}

// Implementation (Reactive Mongo)
trait GenericRepositoryReactiveMongoJsonImpl[T] extends GenericJsonRepository[T] with DBUtils {
  import play.modules.reactivemongo.json.collection.JSONCollection

  val jsonCollection: JSONCollection

  implicit def formatT: Format[T]

  private[this] def lastErrorToJson(lastError: Future[LastError])(implicit ec: ExecutionContext): Future[JsObject] = {
    lastError flatMap { lastError =>
      if (lastError.inError) Future.failed(lastError.getCause())
      else {
        val json = lastError.elements.foldLeft(Json.obj()) { (json, bsonPair) => json ++ Json.obj(bsonPair._1 -> BSONFormats.toJSON(bsonPair._2)) }
        Future(json)
      }
    }
  }

  override def findOne(
    selector: JsObject,
    projection: JsObject = Json.obj())(implicit ec: ExecutionContext): Future[Option[T]] = {
    jsonCollection.find(selector, projection).one[T]
  }

  override def findAll(implicit ec: ExecutionContext): Future[Stream[T]] = {
    jsonCollection.find(Json.obj())
      .cursor[T]
      .collect[Stream]()
  }

  override def enumerate[B](
    selector: JsObject,
    processor: Iteratee[T, B])(implicit ec: ExecutionContext): Future[Iteratee[T, B]] = {
    dbJsonEnumerator(
      jsonCollection,
      selector,
      processor)
  }

  override def enumerateAll[B](processor: Iteratee[T, B])(implicit ec: ExecutionContext): Future[Iteratee[T, B]] = {
    enumerate(
      Json.obj(),
      processor)
  }

  override def save(model: T)(implicit ec: ExecutionContext): Future[JsObject] = {
    val result = jsonCollection.save(model)

    lastErrorToJson(result)
  }

  override def insert(model: T)(implicit ec: ExecutionContext): Future[JsObject] = {
    val result = jsonCollection.insert(model)

    lastErrorToJson(result)
  }

  override def update(
    selector: JsObject,
    update: JsObject,
    upsert: Boolean = false,
    multi: Boolean = false)(implicit ec: ExecutionContext, writesT: Writes[T]): Future[JsObject] = {
    val result = jsonCollection.update(selector, update, upsert = upsert, multi = multi)

    lastErrorToJson(result)
  }

  override def setKeys(
    selector: JsObject,
    update: JsObject,
    upsert: Boolean = false,
    multi: Boolean = false)(implicit ec: ExecutionContext, writesT: Writes[T]): Future[JsObject] = {
    if (update == Json.obj()) Future(Json.obj("msg" -> "Object empty, nothing to update")) // TODO: Figure a better return/standard for returns?
    else {
      val updateJson = Json.obj("$set" -> update)

      val result = jsonCollection.update(selector, updateJson, upsert = upsert, multi = multi)

      lastErrorToJson(result)
    }
  }

  override def setKeyValues[T](
    selector: JsObject,
    setKeyValues: List[(String, T)],
    upsert: Boolean = false,
    multi: Boolean = false)(implicit ec: ExecutionContext, writesT: Writes[T]): Future[JsObject] = {
    val data = setKeyValues.foldLeft(Json.obj()) { (json, keyValue) => json ++ Json.obj(keyValue._1 -> keyValue._2) }

    setKeys(selector, data, upsert = upsert, multi = multi)
  }

  override def setKeyValue[T](
    selector: JsObject,
    setKeyValue: (String, T),
    upsert: Boolean = false,
    multi: Boolean = false)(implicit ec: ExecutionContext, writesT: Writes[T]): Future[JsObject] = {
    setKeyValues(selector, List(setKeyValue), upsert = upsert, multi = multi)
  }

  override def pushArrayItem(
    selector: JsObject,
    arrayKey: String,
    item: JsValue,
    unique: Boolean = false,
    upsert: Boolean = false,
    multi: Boolean = false)(implicit ec: ExecutionContext): Future[JsObject] = {
    val updateJson = unique match {
      case false => Json.obj("$push" -> Json.obj(arrayKey -> item))
      case true => Json.obj("$addToSet" -> Json.obj(arrayKey -> item))
    }

    update(selector, updateJson, upsert = upsert, multi = multi)
  }

  override def unsetKeys(
    selector: JsObject,
    unsetKeys: List[String],
    multi: Boolean = false)(implicit ec: ExecutionContext): Future[JsObject] = {
    val data = unsetKeys.foldLeft(Json.obj()) { (json, key) => json ++ Json.obj(key -> "") }
    val updateJson = Json.obj("$unset" -> data)

    update(selector, updateJson, upsert = false, multi = multi)
  }

  override def unsetKey(
    selector: JsObject,
    unsetKey: String,
    multi: Boolean = false)(implicit ec: ExecutionContext): Future[JsObject] = {
    unsetKeys(selector, List(unsetKey), multi)
  }
}