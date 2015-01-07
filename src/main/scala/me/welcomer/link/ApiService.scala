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

  implicit protected val system: ActorSystem

  implicit protected lazy val log = akka.event.Logging(system, this.getClass())

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