package me.welcomer.framework.pico.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.Writes

import me.welcomer.framework.pico.repository.PicoPdsRepositoryComponent

trait PicoPdsServiceComponentMockImpl extends PicoPdsServiceComponent { self: PicoPdsRepositoryComponent =>
  override protected def _picoPdsService(picoUUID: String): PicoPdsService // = new PicoPdsServiceMockImpl()(picoUUID)

  class PicoPdsServiceMockImpl(implicit val picoUUID: String) extends PicoPdsService {

    override def storeItem[T](
      key: String,
      item: T,
      namespace: Option[String] = None)(implicit ec: ExecutionContext, writeT: Writes[T]): Future[JsObject] = {
      ???
    }

    override def storeItemWithJsValue(
      key: String,
      item: JsValue,
      namespace: Option[String] = None,
      selector: Option[JsObject] = None)(implicit ec: ExecutionContext): Future[JsObject] = {
      ???
    }

    override def storeAllItemsWithJsObject(
      items: JsObject,
      namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject] = {
      ???
    }

    override def pushArrayItem(
      arrayKey: String,
      item: JsValue,
      namespace: Option[String] = None,
      selector: Option[JsObject] = None,
      unique: Boolean = false)(implicit ec: ExecutionContext): Future[JsObject] = {
      ???
    }

    override def retrieve(
      query: JsObject,
      projection: JsObject,
      namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[Option[JsObject]] = {
      ???
    }

    override def retrieveItem[T](
      key: String,
      namespace: Option[String] = None)(implicit ec: ExecutionContext, readsT: Reads[T]): Future[Option[T]] = {
      ???
    }

    override def retrieveAllItems(
      namespace: Option[String] = None,
      filter: Option[List[String]] = None)(implicit ec: ExecutionContext): Future[Option[JsObject]] = {
      ???
    }

    override def retrieveAllNamespaces(filter: Option[List[String]] = None)(implicit ec: ExecutionContext): Future[Option[JsObject]] = {
      ???
    }

    override def removeItem(
      key: String,
      namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject] = {
      ???
    }

    override def removeAllItems(namespace: Option[String] = None)(implicit ec: ExecutionContext): Future[JsObject] = {
      ???
    }

    override def removeAllNamespaces(implicit ec: ExecutionContext): Future[JsObject] = {
      ???
    }

  }
}
