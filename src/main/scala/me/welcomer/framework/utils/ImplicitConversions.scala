package me.welcomer.framework.utils

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api.libs.json.JsString

object ImplicitConversions {
  import scala.language.implicitConversions

  implicit def wrapWithFuture[T](o: T)(implicit ec: ExecutionContext): Future[T] = Future(o)

  implicit def wrapWithOption[T](o: T): Option[T] = Option(o)

  implicit def wrapWithFutureOption[T](o: T)(implicit ec: ExecutionContext): Future[Option[T]] = Future(Option(o))

  implicit def wrapWithList[T](o: T): List[T] = List(o)

  implicit def stringToJsString(s: String): JsString = JsString(s)
}