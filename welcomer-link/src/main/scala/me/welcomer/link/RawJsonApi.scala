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
package me.welcomer.link

import scala.concurrent.Future
import scala.util.Try

import akka.actor.ActorSystem
import akka.util.Timeout

import play.api.libs.json._

import spray.client.pipelining._
import spray.http._

class RawJsonApi(implicit val system: ActorSystem, val timeout: Timeout) extends ApiService {

  def get(url: String): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson { Get(url) }

  def post(url: String, json: Option[JsValue] = None): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson { Post(url, json) }

  def put(url: String, json: Option[JsValue] = None): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson { Put(url, json) }

  def patch(url: String, json: Option[JsValue] = None): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson { Patch(url, json) }

  def delete(url: String, json: Option[JsValue] = None): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson { Delete(url, json) }

}
