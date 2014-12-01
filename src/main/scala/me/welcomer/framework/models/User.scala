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

import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.BSONDocument
import me.welcomer.framework.utils.ModelMetadata
import me.welcomer.framework.utils.Model
import me.welcomer.framework.utils.ModelSchema

object UserSchema extends ModelSchema {
  val version = 1
  val firstName = "firstName"
  val lastName = "lastName"
}

case class User(metadata: ModelMetadata, firstName: String, lastName: String) extends Model

object User {
  import reactivemongo.bson.BSONDocumentReader
  import reactivemongo.bson.BSONDocumentWriter
  import me.welcomer.framework.utils.DBImplicits._

  implicit object UserReader extends BSONDocumentReader[User] {
    def read(doc: BSONDocument): User = {
      val metadata = ModelMetadata(doc)

      metadata.version match {
        //        case 2 => {
        //          // Handle loading version 2 here
        //          User(metadata, "firstName", "lastName")
        //        }
        case _ => {
          // Default: Load latest model
          val firstName = doc.getAs[String](UserSchema.firstName).get
          val lastName = doc.getAs[String](UserSchema.lastName).get

          User(metadata, firstName, lastName)
        }
      }
    }
  }

  implicit object UserWriter extends BSONDocumentWriter[User] {
    def write(model: User): BSONDocument = model.metadata.withVersion(UserSchema.version).withUpdatedAtNow ++ BSONDocument(
      UserSchema.firstName -> model.firstName,
      UserSchema.lastName -> model.lastName)
  }
}