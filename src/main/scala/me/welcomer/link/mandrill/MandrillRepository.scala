package me.welcomer.link.mandrill
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.util.Timeout

import play.api.libs.json._

import me.welcomer.link.ApiService
import me.welcomer.link.mandrill.MandrillProtocol._

import spray.client.pipelining._
import spray.http._

class MandrillApi(val key: String)(implicit val system: ActorSystem, val timeout: Timeout) extends MandrillRepository with ApiService {
  override val host = MandrillEndpoint._HOST
  override val port = MandrillEndpoint._PORT
  override val ssl = MandrillEndpoint._SSL

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