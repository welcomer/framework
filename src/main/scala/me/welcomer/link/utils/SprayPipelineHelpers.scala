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
package me.welcomer.link.utils

import scala.util.Try

import akka.event.LoggingAdapter

import play.api.libs.json._

import spray.http.HttpRequest
import spray.http.HttpResponse

trait SprayPipelineHelpers {
  import spray.client.pipelining._

  implicit protected def log: LoggingAdapter

  object Request {
    def debug: RequestTransformer = { request: HttpRequest =>
      log.debug("DebugRequest: {}", request)

      request
    }
  }

  object Response {
    def debug: ResponseTransformer = { response: HttpResponse =>
      log.debug("DebugResponse: {}", response)

      response
    }

    def asJson: HttpResponse => (HttpResponse, Try[JsValue]) = { r =>
      val rJson = Try(Json.parse(r.entity.asString))
      (r, rJson)
    }
  }
}