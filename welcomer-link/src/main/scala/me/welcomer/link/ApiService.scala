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

import akka.actor.ActorSystem
import akka.util.Timeout

import play.api.libs.json._

import me.welcomer.link.utils.SprayPipelineHelpers

import spray.client.pipelining._
import spray.http._
import spray.httpx.PlayJsonSupport
import spray.httpx.marshalling._

trait ApiService
  extends AnyRef
  with PlayJsonSupport
  with SprayPipelineHelpers {

  //  import spray.httpx.ResponseTransformation._

  implicit protected val system: ActorSystem

  implicit protected lazy val log: akka.event.LoggingAdapter = akka.event.Logging(system, this.getClass())

  //  import system.dispatcher
  implicit protected val ec = system.dispatcher.prepare

  implicit protected def timeout: Timeout

  protected lazy val pipeline: SendReceive = sendReceive

  protected lazy val pipelineAsJson = {
    pipeline ~>
      Response.debug ~>
      Response.asJson
  }
}

trait ApiServiceMarshalling { this: ApiService =>
  type SuccessResponse
  type GenericResponse

  protected def unmarshalResponse[T <: SuccessResponse](r: HttpResponse)(implicit readsT: Reads[T]): GenericResponse

  protected def unmarshalPipeline[T <: SuccessResponse](request: => HttpRequest)(implicit readsT: Reads[T]): Future[GenericResponse] = {
    for { httpResponse <- pipeline(request) } yield { unmarshalResponse[T](httpResponse) }
  }
}
