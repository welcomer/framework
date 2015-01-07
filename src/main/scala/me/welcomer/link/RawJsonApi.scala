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