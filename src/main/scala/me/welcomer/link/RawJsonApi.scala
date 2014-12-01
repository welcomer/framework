package me.welcomer.link

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.util.Timeout
import play.api.libs.json._
import spray.client.pipelining._
import spray.http._
import spray.httpx.PlayJsonSupport
import scala.util.Try

class RawJsonApi(implicit val system: ActorSystem, val timeout: Timeout) extends PlayJsonSupport {
  import system.dispatcher

  protected val pipeline: HttpRequest => Future[(HttpResponse, Try[JsValue])] = (
    sendReceive
    ~> parseResponseJson)

  protected def parseResponseJson(r: HttpResponse): (HttpResponse, Try[JsValue]) = {
    val rJson = Try(Json.parse(r.entity.asString))
    (r, rJson)
  }

  def get(url: String): Future[(HttpResponse, Try[JsValue])] = pipeline { Get(url) }

  def post(url: String, json: Option[JsValue] = None): Future[(HttpResponse, Try[JsValue])] = pipeline { Post(url, json) }

  def put(url: String, json: Option[JsValue] = None): Future[(HttpResponse, Try[JsValue])] = pipeline { Put(url, json) }

  def patch(url: String, json: Option[JsValue] = None): Future[(HttpResponse, Try[JsValue])] = pipeline { Patch(url, json) }

  def delete(url: String, json: Option[JsValue] = None): Future[(HttpResponse, Try[JsValue])] = pipeline { Delete(url, json) }

}