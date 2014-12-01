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
import reactivemongo.bson.BSONDocument
import reactivemongo.bson.Producer.nameValue2Producer
import me.welcomer.framework.utils.ModelSchema

object PicoSchema extends ModelSchema {
  val version = 1
  val picoUUID = "picoUUID"
  val rulesets = "rulesets"
}

case class Pico(metadata: ModelMetadata, picoUUID: String, rulesets: Set[String]) extends Model

object Pico {
  import reactivemongo.bson.BSONDocumentReader
  import reactivemongo.bson.BSONDocumentWriter
  import me.welcomer.framework.utils.DBImplicits._

  def apply(rulesets: Set[String] = Set()) = {
    new Pico(ModelMetadata(), UUID.randomUUID().toString(), rulesets)
  }

  implicit object PicoReader extends BSONDocumentReader[Pico] {
    def read(doc: BSONDocument): Pico = {
      val metadata = ModelMetadata(doc)

      metadata.version match {
        case _ => {
          // Default: Load latest model
          val picoUUID = doc.getAs[String](PicoSchema.picoUUID).get
          val rulesets = doc.getAs[Set[String]](PicoSchema.rulesets) getOrElse Set()

          Pico(metadata, picoUUID, rulesets)
        }
      }
    }
  }

  implicit object PicoWriter extends BSONDocumentWriter[Pico] {
    def write(model: Pico): BSONDocument = model.metadata.withVersion(PicoSchema.version).withUpdatedAtNow ++ BSONDocument(
      PicoSchema.picoUUID -> model.picoUUID,
      PicoSchema.rulesets -> model.rulesets)
  }
}