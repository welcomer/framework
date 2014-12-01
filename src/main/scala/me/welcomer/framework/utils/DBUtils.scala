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

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import akka.actor.ActorSystem
import play.api.libs.iteratee.Iteratee
import reactivemongo.api.MongoConnection
import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.BSONDocument
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.Producer.nameValue2Producer
import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONDocumentWriter
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Format
import play.api.libs.json.JsResult
import play.api.data.validation.ValidationError
import play.modules.reactivemongo.json.collection.JSONCollection
import play.api.libs.json.Reads
import play.modules.reactivemongo.json.BSONFormats

object DBUtils extends DBUtils

trait DBUtils
  extends AnyRef
  with DBConnect
  with DBQueryHelper
  with DBProjectionHelper
  with DBEnumeratorHelper
  with DBConversionHelper
  with DBScopeHelper {
  import me.welcomer.framework.utils.DBImplicits
}

trait DBConnect extends AnyRef {

  /**
   * Connect to database
   */
  protected def dbConnect(implicit system: ActorSystem, uri: String, nbChannelsPerNode: Int = 10): (MongoDriver, Try[MongoConnection]) = {
    val driver = new MongoDriver(system)
    val connection: Try[MongoConnection] =
      MongoConnection.parseURI(uri).map { parsedUri =>
        driver.connection(parsedUri, nbChannelsPerNode)
      }

    (driver, connection)
  }

}

trait DBQueryHelper {
  protected def dbQuery(id: BSONObjectID): BSONDocument = {
    BSONDocument("_id" -> id)
  }

  protected def dbQuery(keyValue: (String, String)): BSONDocument = {
    BSONDocument(keyValue._1 -> keyValue._2)
  }

  protected def dbQuery(key: String, value: String): BSONDocument = {
    BSONDocument(key -> value)
  }

  protected def dbQueryAll(): BSONDocument = {
    BSONDocument()
  }

  protected def dbScopedQuery(scope: BSONDocument, query: BSONDocument): BSONDocument = {
    scope ++ query
  }
}

trait DBProjectionHelper {
  def dbElemMatch(containerKey: String, matchSelector: BSONDocument): BSONDocument = {
    BSONDocument(containerKey -> BSONDocument("$elemMatch" -> matchSelector))
  }

  protected def dbIncludeKeysJsObject(keys: List[String]): JsObject = {
    keys.foldLeft(Json.obj()) { (json, key) => json ++ Json.obj(key -> 1) }
  }

  protected def dbIncludeKeyJsObject(key: String): JsObject = dbIncludeKeysJsObject(List(key))

  protected def dbIncludeKeys(keys: List[String]): BSONDocument = {
    keys.foldLeft(BSONDocument()) { (bson, key) => bson ++ BSONDocument(key -> 1) }
  }

  protected def dbIncludeKey(key: String): BSONDocument = dbIncludeKeys(List(key))

  protected def dbExcludeKeys(keys: List[String]): BSONDocument = {
    keys.foldLeft(BSONDocument()) { (bson, key) => bson ++ BSONDocument(key -> 0) }
  }

  protected def dbExcludeKey(key: String): BSONDocument = dbExcludeKeys(List(key))

  protected def dbExcludeId: BSONDocument = dbExcludeKey("_id")
}

trait DBEnumeratorHelper {
  protected def dbEnumerator[B](
    collection: BSONCollection,
    query: BSONDocument,
    processor: Iteratee[BSONDocument, B])(implicit ec: ExecutionContext): Future[Iteratee[BSONDocument, B]] = {

    dbEnumerator[BSONDocument, B](collection, query, processor)
  }

  protected def dbEnumerator[A, B](
    collection: BSONCollection,
    query: BSONDocument,
    processor: Iteratee[A, B])(implicit ec: ExecutionContext, documentReader: BSONDocumentReader[A]): Future[Iteratee[A, B]] = {

    val documentEnumerator = collection
      .find(query)
      .cursor[A]
      .enumerate()

    documentEnumerator.apply(processor)
  }

  protected def dbJsonEnumerator[A, B](
    collection: JSONCollection,
    selector: JsObject,
    processor: Iteratee[A, B])(implicit ec: ExecutionContext, readsA: Reads[A]): Future[Iteratee[A, B]] = {

    val documentEnumerator = collection
      .find(selector)
      .cursor[A]
      .enumerate()

    documentEnumerator.apply(processor)
  }
}

trait DBConversionHelper {
  protected def bsonDocumentToJsObject(bson: BSONDocument): JsObject = {
    BSONFormats.BSONDocumentFormat.writes(bson).as[JsObject]
  }

  protected def jsObjectToBsonDocument(json: JsObject): BSONDocument = {
    BSONFormats.BSONDocumentFormat.reads(json).getOrElse(BSONDocument())
  }
}

trait DBScopeHelper {
  protected def buildNamespacedKeyPath(namespacePath: String, keyOption: Option[String]): String = {
    keyOption.map { key => s"$namespacePath.$key" } getOrElse namespacePath
  }

  protected def scopeAllKeys(scope: String, json: JsObject): JsObject = {
    json.fieldSet.foldLeft(Json.obj()) { (json, keyValue) =>
      json ++ Json.obj(scope + "." + keyValue._1 -> keyValue._2)
    }
  }

  protected def scopeAllKeys(scope: String, json: Option[JsObject]): JsObject = scopeAllKeys(scope, json.getOrElse(Json.obj()))
}

object DBImplicits {
  import play.api.libs.json._
  import reactivemongo.bson.BSONReader
  import reactivemongo.bson.BSONWriter
  import reactivemongo.bson.BSONValue
  import reactivemongo.bson.BSONDateTime
  import reactivemongo.bson.BSONUndefined

  import java.util.Date

  implicit object DateTimeReader extends BSONReader[BSONDateTime, Date] {
    def read(bson: BSONDateTime): Date = {
      new Date(bson.value)
    }
  }

  implicit object DateTimeWriter extends BSONWriter[Date, BSONDateTime] {
    def write(date: Date): BSONDateTime = {
      BSONDateTime(date.getTime())
    }
  }

  implicit object OptionDateTimeWriter extends BSONWriter[Option[Date], BSONValue] {
    def write(optionDate: Option[Date]): BSONValue = {
      optionDate match {
        case Some(date) => BSONDateTime(date.getTime())
        case None => BSONUndefined
      }
    }
  }

  implicit object BSONDocumentReaderImpl extends BSONDocumentReader[BSONDocument] {
    def read(doc: BSONDocument) = doc
  }

  implicit object BSONDocumentWriterImpl extends BSONDocumentWriter[BSONDocument] {
    def write(doc: BSONDocument) = doc
  }

  implicit object JsObjectFormatImpl extends Format[JsObject] {
    def reads(json: JsValue): JsResult[JsObject] = json match {
      case o: JsObject => JsSuccess(o)
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.jsobject"))))
    }

    def writes(o: JsObject): JsValue = o
  }

  implicit object JsValueFormatImpl extends Format[JsValue] {
    def reads(json: JsValue): JsResult[JsValue] = JsSuccess(json)

    def writes(o: JsValue): JsValue = o
  }
}