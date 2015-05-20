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
package me.welcomer.link.xero

import spray.http.HttpResponse
import play.api.libs.json.Json
import play.api.libs.json.JsObject

sealed trait XeroResult[+A] { self =>
  def isSuccess: Boolean = this.isInstanceOf[XeroSuccess[_]]
  def isError: Boolean = this.isInstanceOf[XeroFailure]

  def fold[X](invalid: Seq[XeroError] => X, valid: A => X): X = this match {
    case XeroSuccess(v, _) => valid(v)
    case XeroFailure(e, _) => invalid(e)
  }

  def map[X](f: A => X): XeroResult[X] = this match {
    case XeroSuccess(v, r) => XeroSuccess(f(v), r)
    case e: XeroFailure    => e
  }

  //  def filterNot(error: XeroError)(p: A => Boolean): XeroResult[A] =
  //    this.flatMap { a => if (p(a)) XeroFailure(error, httpResponse) else XeroSuccess(a, httpResponse) }
  //
  //  def filterNot(p: A => Boolean): XeroResult[A] =
  //    this.flatMap { a => if (p(a)) XeroFailure() else XeroSuccess(a, httpResponse) }
  //
  //  def filter(p: A => Boolean): XeroResult[A] =
  //    this.flatMap { a => if (p(a)) XeroSuccess(a, httpResponse) else XeroFailure() }
  //
  //  def filter(otherwise: XeroError)(p: A => Boolean): XeroResult[A] =
  //    this.flatMap { a => if (p(a)) XeroSuccess(a, httpResponse) else XeroFailure(otherwise, httpResponse) }
  //
  //  def collect[B](otherwise: XeroError)(p: PartialFunction[A, B]): XeroResult[B] = flatMap {
  //    case t if p.isDefinedAt(t) => XeroSuccess(p(t), httpResponse)
  //    case _ => XeroFailure(otherwise, httpResponse)
  //  }

  def flatMap[X](f: A => XeroResult[X]): XeroResult[X] = this match {
    case XeroSuccess(v, _) => f(v)
    case e: XeroFailure    => e
  }

  def foreach(f: A => Unit): Unit = this match {
    case XeroSuccess(a, _) => f(a)
    case _                 => ()
  }

  def httpResponse: Option[HttpResponse]
  def get: A

  def getOrElse[AA >: A](t: => AA): AA = this match {
    case XeroSuccess(a, _) => a
    case XeroFailure(_, _) => t
  }

  def orElse[AA >: A](t: => XeroResult[AA]): XeroResult[AA] = this match {
    case s @ XeroSuccess(_, _) => s
    case XeroFailure(_, _)     => t
  }

  def asOpt = this match {
    case XeroSuccess(v, _) => Some(v)
    case XeroFailure(_, _) => None
  }

  def asEither = this match {
    case XeroSuccess(v, _) => Right(v)
    case XeroFailure(e, _) => Left(e)
  }
}

case class XeroSuccess[T](value: T, httpResponse: Option[HttpResponse]) extends XeroResult[T] {
  def get: T = value
}

case class XeroFailure(errors: Seq[XeroError], httpResponse: Option[HttpResponse]) extends XeroResult[Nothing] {
  def get: Nothing = throw new NoSuchElementException("XeroFailure.get")

  //  def ++(error: XeroFailure): XeroFailure = XeroFailure.merge(this, error)
  //
  //  def :+(error: XeroError): XeroFailure = XeroFailure.merge(this, XeroFailure(error))
  //  def append(error: XeroError): XeroFailure = this.:+(error)
  //
  //  def +:(error: XeroError): XeroFailure = XeroFailure.merge(XeroFailure(error), this)
  //  def prepend(error: XeroError): XeroFailure = this.+:(error)

  // def asThrowable = throw new ...
}

object XeroFailure {
  def apply(): XeroFailure = XeroFailure(Seq(), None)
  def apply(error: XeroError, httpResponse: Option[HttpResponse]): XeroFailure = XeroFailure(Seq(error), httpResponse)

  def merge(e1: Seq[XeroError], e2: Seq[XeroError]): Seq[XeroError] = {
    (e1 ++ e2)
  }

  //  def merge(e1: XeroFailure, e2: XeroFailure): XeroFailure = {
  //    XeroFailure(merge(e1.errors, e2.errors))
  //  }
}

trait XeroError

case class BasicError(msg: String) extends XeroError

case class SprayDeserialisationError(err: spray.httpx.unmarshalling.DeserializationError) extends XeroError

//case class XeroApiError(
//  id: String,
//  providerName: String,
//  errorNumber: Int,
//  errorType: String,
//  message: String,
//  raw: JsObject) extends XeroError
