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
package me.welcomer.link.mandrill

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.util.Timeout

import play.api.libs.json._

import me.welcomer.link.ApiService
import me.welcomer.link.ApiServiceMarshalling
import me.welcomer.link.mandrill.MandrillProtocol._

import spray.client.pipelining._
import spray.http._

class MandrillApi(key: String)(implicit val system: ActorSystem, val timeout: Timeout)
  extends MandrillRepository
  with ApiService
  with ApiServiceMarshalling {

  override type SuccessResponse = MandrillSuccessResponse
  override type GenericResponse = MandrillResponse

  override protected def unmarshalResponse[T <: SuccessResponse](r: HttpResponse)(implicit readsT: Reads[T]): GenericResponse = {
    r.status.intValue match {
      case 200 => Json.parse(r.entity.asString).as[T]
      case _ => Json.parse(r.entity.asString).as[MandrillError]
    }
  }

  lazy val messages = new MandrillRepositoryMessages {
    def raw[T <: MandrillSuccessResponse](endpoint: String, msg: JsObject)(implicit readsT: Reads[T]): Future[MandrillResponse] = unmarshalPipeline[T] {
      Post(endpoint, msg)
    }

    def send(msg: Message): Future[MandrillResponse] = unmarshalPipeline[SendResponse] {
      Post(MandrillEndpoint.Messages.SEND, Send(key, msg))
    }
  }
}

trait MandrillRepositoryComponent {
  protected def _mandrillRepository(key: String)(implicit system: ActorSystem, timeout: Timeout): MandrillRepository
}

trait MandrillRepositoryComponentImpl extends MandrillRepositoryComponent {
  override protected def _mandrillRepository(key: String)(implicit system: ActorSystem, timeout: Timeout): MandrillRepository = new MandrillApi(key)
}

trait MandrillRepository {
  def messages: MandrillRepositoryMessages

  trait MandrillRepositoryMessages {
    def raw[T <: MandrillSuccessResponse](
      endpoint: String,
      msg: JsObject)(implicit readsT: Reads[T]): Future[MandrillResponse]

    def send(msg: Message): Future[MandrillResponse]
  }
}