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

import java.util.Date

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.modules.reactivemongo.json.BSONFormats
import reactivemongo.bson._

trait ModelUtils {
  import me.welcomer.framework.utils.DBImplicits._
}

trait Model extends ModelUtils {
  val metadata: ModelMetadata
}

trait ModelSchema {
  val version: Int
}

object ModelMetadataSchema {
  val id = "_id"
  val version = "_version"
  val createdAt = "_createdAt"
  val updatedAt = "_updatedAt"
}

case class ModelMetadata(id: BSONObjectID, version: Int, createdAt: Option[Date], updatedAt: Option[Date]) {
  def withVersion(newVersion: Int): ModelMetadata = this.copy(version = newVersion)

  def withUpdatedAtNow(): ModelMetadata = this.copy(updatedAt = Option(new Date()))
}

object ModelMetadata {
  import language.implicitConversions
  //  import reactivemongo.bson.BSONWriter
  import me.welcomer.framework.utils.DBImplicits._

  def apply(
    version: Int = 1,
    createdAt: Option[Date] = Option(new Date()),
    updatedAt: Option[Date] = Option(new Date())) = {
    val id = BSONObjectID.generate

    new ModelMetadata(id, version, createdAt, updatedAt)
  }

  //  def apply(json: JsObject) = {
  //    val a = Json.fromJson[ModelMetadata](json)
  //    // TODO:
  //  }

  def apply(doc: BSONDocument) = {
    val id = doc.getAs[BSONObjectID](ModelMetadataSchema.id).get
    val version = doc.getAs[Int](ModelMetadataSchema.version) getOrElse 1
    val createdAt = doc.getAs[Date](ModelMetadataSchema.createdAt)
    val updatedAt = doc.getAs[Date](ModelMetadataSchema.updatedAt)

    new ModelMetadata(id, version, createdAt, updatedAt)
  }

  implicit lazy val bsonObjectIdFormat: Format[BSONObjectID] =
    (__ \ "id").format[String].inmap(BSONObjectID(_), _.stringify)

  //
  //  implicit lazy val bsonObjectIdReads: Reads[BSONObjectID] =
  //    (JsPath \ "id").read[String].map(BSONObjectID(_))
  //
  //  implicit lazy val bsonObjectIdWrites = new Writes[BSONObjectID] {
  //    def writes(id: BSONObjectID) = Json.obj(
  //      "id" -> id.stringify)
  //  }

  //  implicit lazy val modelMetadataFormat: Format[ModelMetadata] = (
  //    __.format[BSONObjectID] ~
  //    (__ \ ModelMetadataSchema.version).format[Int] ~
  //    (__ \ ModelMetadataSchema.createdAt).format[Option[Date]] ~
  //    (__ \ ModelMetadataSchema.updatedAt).format[Option[Date]])(ModelMetadata.apply, unlift(ModelMetadata.unapply))

  //  implicit val modelMetadataFormat: Format[ModelMetadata] = new Format[ModelMetadata] {
  implicit val modelMetadataReads: Reads[ModelMetadata] = new Reads[ModelMetadata] {
    import me.welcomer.framework.utils.JsonUtils

    //  val base = Json.format[ModelMetadata]
    def reads(json: JsValue): JsResult[ModelMetadata] = {
      val transformer = (
        JsonUtils.withDefault(ModelMetadataSchema.id, BSONObjectID.generate) ~
        JsonUtils.withDefault(ModelMetadataSchema.version, 1)).reduce

      val jsonWithDefaults = json.transform(transformer)

      jsonWithDefaults match {
        case JsSuccess(json, path) => {
          val dataTry = Try({
            val id = (json \ ModelMetadataSchema.id).as[BSONObjectID]
            val version = (json \ ModelMetadataSchema.version).as[Int]
            val createdAt = (json \ ModelMetadataSchema.createdAt).asOpt[Date]
            val updatedAt = (json \ ModelMetadataSchema.updatedAt).asOpt[Date]

            (id, version, createdAt, updatedAt)
          })

          dataTry match {
            case Success((id, version, createdAt, updatedAt)) => JsSuccess(ModelMetadata(id, version, createdAt, updatedAt))
            case Failure(e) => JsError(e.getMessage())
          }
        }
        case e: JsError => e
      }
      //val id = (json \ ECISchema.eci).as[String]
      //              val picoUUID = (json \ ECISchema.picoUUID).as[String]
      //              val description = (json \ ECISchema.description).asOpt[String].getOrElse("")

    }
    //    def writes(o: ModelMetadata): JsValue = base.writes(o)
  }

  implicit def ModelMetadataToJsObject(metadata: ModelMetadata): JsObject = Json.obj(
    ModelMetadataSchema.id -> BSONFormats.BSONObjectIDFormat.writes(metadata.id),
    ModelMetadataSchema.version -> metadata.version,
    ModelMetadataSchema.createdAt -> metadata.createdAt,
    ModelMetadataSchema.updatedAt -> metadata.updatedAt)

  implicit def ModelMetadataToBSONDocument(metadata: ModelMetadata): BSONDocument = BSONDocument(
    ModelMetadataSchema.id -> metadata.id,
    ModelMetadataSchema.version -> metadata.version,
    ModelMetadataSchema.createdAt -> metadata.createdAt,
    ModelMetadataSchema.updatedAt -> metadata.updatedAt)

  //  implicit object ModelMetadataWriter extends BSONWriter[ModelMetadata, BSONDocument] {
  //    def write(metadata: ModelMetadata): BSONDocument = metadata
  //  }
}