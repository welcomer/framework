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
package me.welcomer.framework.models

import java.util.UUID
import me.welcomer.framework.utils.Model
import me.welcomer.framework.utils.ModelMetadata
import me.welcomer.framework.utils.ModelMetadata.ModelMetadataToBSONDocument
import me.welcomer.framework.utils.ModelSchema
import reactivemongo.bson.BSONDocument
import reactivemongo.bson.BSONDocumentReader
import reactivemongo.bson.BSONDocumentWriter
import reactivemongo.bson.Producer.nameValue2Producer
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import scala.util.Try
import scala.util.Failure

/** Event Channel Identifier */

object ECISchema extends ModelSchema {
  val version = 1
  val eci = "eci"
  val picoUUID = "picoUUID"
  val description = "description"
}

case class ECI(metadata: ModelMetadata, eci: String, picoUUID: String, description: String) extends Model

object ECI {
  import me.welcomer.framework.utils.DBImplicits._

  def apply(eci: String = UUID.randomUUID().toString(), picoUUID: String, description: String = "") = {
    new ECI(ModelMetadata(), eci, picoUUID, description)
  }

  //  implicit lazy val eciFormat: Format[ECI] = (
  //    (__ \ "metadata").format[ModelMetadata] ~
  //    (__ \ "id").format[String])(ECI.apply, unlift(ECI.unapply))

  //  implicit lazy val eciReads = new Reads[ECI] {
  //    def reads(json: JsValue): JsResult[ECI] = {
  //      val metadataOpt = json.asOpt[ModelMetadata]
  //      val metadataTry = metadataOpt.fold[Try[ModelMetadata]](Failure[ModelMetadata](new Throwable("Couldn't construct ModelMetadata")))(Success(_))
  //
  //      val theTry = Try(
  //        metadataOpt map { metadata =>
  //          metadata.version match {
  //            case _ => {
  //              // Default: Load latest model
  //              val eci = (json \ ECISchema.eci).as[String]
  //              val picoUUID = (json \ ECISchema.picoUUID).as[String]
  //              val description = (json \ ECISchema.description).asOpt[String].getOrElse("")
  //
  //              ECI(metadata, eci, picoUUID, description)
  //            }
  //          }
  //        })
  //
  //      ???
  //    }
  //  }

  implicit object ECIReader extends BSONDocumentReader[ECI] {
    def read(doc: BSONDocument): ECI = {
      val metadata = ModelMetadata(doc)

      metadata.version match {
        case _ => {
          // Default: Load latest model
          val eci = doc.getAs[String](ECISchema.eci).get
          val picoUUID = doc.getAs[String](ECISchema.picoUUID).get
          val description = doc.getAs[String](ECISchema.description).getOrElse("")

          ECI(metadata, eci, picoUUID, description)
        }
      }
    }
  }

  implicit object ECIWriter extends BSONDocumentWriter[ECI] {
    def write(model: ECI): BSONDocument = model.metadata.withVersion(ECISchema.version).withUpdatedAtNow ++ BSONDocument(
      ECISchema.eci -> model.eci,
      ECISchema.picoUUID -> model.picoUUID,
      ECISchema.description -> model.description)
  }
}
