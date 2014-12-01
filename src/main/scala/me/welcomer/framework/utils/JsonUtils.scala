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
import scala.annotation.tailrec

import org.apache.commons.lang3.StringUtils

import com.fasterxml.jackson.core.JsonParseException

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.modules.reactivemongo.json.BSONFormats
import reactivemongo.bson.BSONDocument
import reactivemongo.bson.BSONValue

object JsonUtils extends JsonUtils

trait JsonUtils {

  def parseOpt(jsonStr: String): Option[JsValue] = {
    // See: https://github.com/playframework/playframework/pull/3520
    try {
      Some(Json.parse(jsonStr))
    } catch {
      case e: JsonParseException => None
    }
  }

  protected def jsonDeepPath(json: JsValue, deepPath: String, separator: String = "."): JsValue = {
    StringUtils.split(deepPath, separator).toList.foldLeft(json)(_ \ _)
  }

  protected def bsonDeepPath(bson: BSONValue, deepPath: String, seperator: String = "."): JsResult[BSONValue] = {
    val jsonResult = jsonDeepPath(BSONFormats.toJSON(bson), deepPath, seperator)

    BSONFormats.toBSON(jsonResult)
  }

  protected def bsonDocumentDeepPath(bson: BSONDocument, deepPath: String, seperator: String = "."): Option[BSONDocument] = {
    @tailrec def loop(bsonOption: Option[BSONDocument], deepPath: String, seperator: String = "."): Option[BSONDocument] = {
      bsonOption match {
        case None => None
        case Some(bson) => {
          StringUtils.contains(deepPath, seperator) match {
            case false => bson.getAs[BSONDocument](deepPath)
            case true => {
              val head = StringUtils.substringBefore(deepPath, seperator)
              val tail = StringUtils.substringAfter(deepPath, seperator)

              loop(bson.getAs[BSONDocument](head), tail)
            }
          }
        }
      }
    }

    loop(Some(bson), deepPath, seperator)
  }

  protected def prefixJsonValue(prefixPath: String, jsonValue: JsValue, seperator: String = "."): JsObject = {
    val prefixChunks = StringUtils.split(prefixPath, seperator).toList

    prefixChunks.foldRight[JsValue](jsonValue) { (prefix, node) => Json.obj(prefix -> node) }.as[JsObject]
  }

  def withDefault[A](key: String, default: A)(implicit writes: Writes[A]) = {
    // See: http://stackoverflow.com/questions/20616677/defaults-for-missing-properties-in-play-2-json-formats
    __.json.update((__ \ key).json.copyFrom((__ \ key).json.pick orElse Reads.pure(Json.toJson(default))))
  }
}

trait Jsonable

object Jsonable {
  import scala.language.implicitConversions

  implicit def jsonableToJsObject[T <: Jsonable](e: T)(implicit writeT: Writes[T]): JsObject = {
    Json.toJson(e).as[JsObject]
  }
}