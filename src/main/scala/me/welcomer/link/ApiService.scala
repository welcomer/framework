package me.welcomer.link

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.io.IO
import akka.util.Timeout
import akka.pattern.ask

import play.api.libs.json._

import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.httpx.PlayJsonSupport
import spray.httpx.marshalling._

trait ApiService extends AnyRef with PlayJsonSupport {
  implicit protected val system: ActorSystem

  //  import system.dispatcher
  implicit protected val ec = system.dispatcher.prepare

  implicit protected def timeout: Timeout

  def host: String
  def port: Int
  def ssl: Boolean

  type SuccessResponse
  type GenericResponse

  protected def hostSendReceive(host: String, port: Int, ssl: Boolean): Future[SendReceive] = {
    val hostConnector = IO(Http) ? Http.HostConnectorSetup(host, port = port, sslEncryption = ssl)

    for {
      Http.HostConnectorInfo(connector, _) <- hostConnector
    } yield sendReceive(connector)
  }

  protected lazy val pipeline /*: Future[SendReceive]*/ = hostSendReceive(host, port, ssl) //for {
  //    sendReceive <- hostSendReceive(HOST, PORT, SSL)
  //  } yield {
  //    ( addHeader("Content-Type", "application/json;charset=UTF-8")
  //      ~> sendReceive)
  //  }

  protected def unmarshalResponse[T <: SuccessResponse](r: HttpResponse)(implicit readsT: Reads[T]): GenericResponse

  protected def unmarshalPipeline[T <: SuccessResponse](request: => HttpRequest)(implicit readsT: Reads[T]): Future[GenericResponse] = {
    for {
      p <- pipeline
      httpResponse <- p(request)
    } yield { unmarshalResponse[T](httpResponse) }
  }
}