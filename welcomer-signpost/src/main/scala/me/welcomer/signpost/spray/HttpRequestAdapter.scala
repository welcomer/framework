/*  Copyright 2015 White Label Personal Clouds Pty Ltd
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
package me.welcomer.signpost.spray

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

import spray.http.Uri.apply
import spray.httpx.RequestBuilding._

class HttpRequestAdapter(var request: spray.http.HttpRequest) extends oauth.signpost.http.HttpRequest {
  import scala.collection.JavaConversions._

  def getMethod(): String = request.method.name

  def getRequestUrl(): String = request.uri.toString

  def setRequestUrl(url: String): Unit = {
    request = request.copy(uri = url)
  }

  def setHeader(name: String, value: String): Unit = {
    request = request ~> addHeader(name, value)
  }

  def getHeader(name: String): String = {
    request.headers.find(_.name == name) map { _.value } getOrElse { null }
  }

  def getAllHeaders(): java.util.Map[String, String] = {
    request.headers.foldLeft(Map[String, String]()) { (map, header) =>
      map + (header.name -> header.value)
    }
  }

  def getMessagePayload(): InputStream = {
    request.entity.toOption match {
      case Some(entity) => new ByteArrayInputStream(entity.data.toByteArray)
      case None         => throw new IOException()
    }
  }

  def getContentType(): String = {
    request.entity.toOption match {
      case Some(entity) => entity.contentType.toString
      case None         => null
    }
  }

  /**
   * Returns the wrapped request object, in case you must work directly on it.
   *
   * @return the wrapped request object
   */
  def unwrap(): Object = request
}
