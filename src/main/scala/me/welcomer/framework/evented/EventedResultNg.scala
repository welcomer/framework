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
package me.welcomer.framework.evented

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scalaz.Scalaz
import scalaz.NonEmptyList
import scalaz.Success
import scalaz.Failure
import scalaz.EitherT
import scalaz.ValidationNel
import scalaz.std.FutureInstances
import play.api.libs.json.JsResult
import play.api.libs.json.JsObject
import play.api.libs.json.JsError
import me.welcomer.framework.pico.BasicError
import me.welcomer.framework.pico.EventedError
import me.welcomer.framework.pico.EventedFailure
import me.welcomer.framework.pico.EventedResult
import me.welcomer.framework.pico.EventedSuccess
import me.welcomer.framework.pico.EventedJsonError
import me.welcomer.framework.pico.EventedJsError
import me.welcomer.framework.pico.EventedThrowableError
import scala.util.control.NonFatal

object EventedSuccessNg {
  import me.welcomer.framework.evented.EventedResultNg.EventedSuccessNg

  def apply[A](s: A): EventedSuccessNg[A] = scalaz.Success(s)
}

object EventedFailureNg {
  import me.welcomer.framework.evented.EventedResultNg.EventedFailureNg
  import Scalaz.ToListOpsFromList

  def apply(error: EventedError): EventedFailureNg = scalaz.Failure(NonEmptyList(error))

  def apply(errors: Seq[EventedError]): EventedFailureNg = scalaz.Failure(errors.toList.toNel getOrElse NonEmptyList(BasicError("Error: There were no errors!")))

  def apply(error: String): EventedFailureNg = apply(BasicError(error))
  def apply(error: JsObject): EventedFailureNg = apply(EventedJsonError(error))
  def apply(error: JsError): EventedFailureNg = apply(EventedJsError(error))
  def apply(error: Throwable): EventedFailureNg = apply(EventedThrowableError(error))
}

object EventedResultNg extends FutureInstances {
  import scala.language.implicitConversions

  //  import Scalaz._
  import Scalaz.ToApplyOps
  import Scalaz.ToFoldableOps
  import Scalaz.ToValidationOps
  import Scalaz.ToListOpsFromList

  type EventedResultNg[A] = ValidationNel[EventedError, A]
  type EventedSuccessNg[A] = Success[A]
  type EventedFailureNg = Failure[EventedErrorNel]

  type EventedErrorNel = NonEmptyList[EventedError]

  type FutureEventedResultNgT[T] = EitherT[Future, EventedErrorNel, T]

  // Implicit Conversions

  implicit def futureEventedResultNg2EventedResultNgT[T](f: Future[EventedResultNg[T]])(implicit ec: ExecutionContext): FutureEventedResultNgT[T] = {
    f.toEventedResultNgT
  }

  implicit def futureEventedResultNgT2EventedResultNg[T](f: FutureEventedResultNgT[T])(implicit ec: ExecutionContext): Future[EventedResultNg[T]] = {
    f.toEventedResultNg
  }

  implicit def futureEventedResultNgT2EventedResult[T](f: FutureEventedResultNgT[T])(implicit ec: ExecutionContext): Future[EventedResult[T]] = {
    f.toEventedResult
  }

  implicit def jsResult2EventedResult[T](r: JsResult[T]): EventedResult[T] = r.toEventedResult
  implicit def jsResult2EventedResultNg[T](r: JsResult[T]): EventedResultNg[T] = r.toEventedResultNg
  implicit def jsResult2FutureEventedResultNgT[T](r: JsResult[T])(implicit ec: ExecutionContext): FutureEventedResultNgT[T] = r.toFutureEventedResultNgT

  implicit def futureJsResult2EventedResultNg[T](f: Future[JsResult[T]])(implicit ec: ExecutionContext): Future[EventedResultNg[T]] = {
    f.toEventedResultNg
  }

  implicit def futureJsResult2EventedResultNgT[T](f: Future[JsResult[T]])(implicit ec: ExecutionContext): FutureEventedResultNgT[T] = {
    f.toEventedResultNgT
  }

  implicit def futureJsResult2EventedResult[T](f: Future[JsResult[T]])(implicit ec: ExecutionContext): Future[EventedResult[T]] = {
    f.toEventedResult
  }

  // Implicit Pimps

  implicit class PimpEventedResult[T](val r: EventedResult[T]) extends AnyVal {
    import Converters.EventedResult._

    def toEventedResultNg: EventedResultNg[T] = convertToEventedResultNg(r)

    def toFutureEventedResultNgT(implicit ec: ExecutionContext): FutureEventedResultNgT[T] = Future.successful(toEventedResultNg).toEventedResultNgT
  }

  implicit class PimpEventedResultUnderscore(val r: EventedResult[_]) extends AnyVal {
    import Converters.EventedResult._

    def as[T]: EventedResult[T] = {
      r flatMap { value =>
        Try(value.asInstanceOf[T]) match {
          case scala.util.Success(s) => EventedSuccess(s)
          case scala.util.Failure(f) => EventedFailure("Unable to convert to requested type.")
        }
      }
    }
  }

  implicit class PimpEventedResultNg[T](val r: EventedResultNg[T]) extends AnyVal {
    import Converters.EventedResultNg._

    def zip[B](r2: EventedResultNg[B]): EventedResultNg[(T, B)] = {
      (r |@| r2)({ case (a, b) => (a, b) })
    }

    def toEventedResult: EventedResult[T] = convertToEventedResult(r)

    def toFutureEventedResultNgT(implicit ec: ExecutionContext): FutureEventedResultNgT[T] = Future.successful(r).toEventedResultNgT
  }

  implicit class PimpFutureEventedResult[T](val f: Future[EventedResult[T]]) extends AnyVal {
    def toEventedResultNgT(implicit ec: ExecutionContext): FutureEventedResultNgT[T] = f.map(_.toEventedResultNg).toEventedResultNgT

    def recoverNonFatal(implicit ec: ExecutionContext): Future[EventedResult[T]] = recoverNonFatal()
    def recoverNonFatal(block: Throwable => Unit = (t => ()))(implicit ec: ExecutionContext): Future[EventedResult[T]] = {
      Converters.EventedResult.recoverNonFatal(f)(block)
    }
  }

  implicit class PimpFutureEventedResultUnderscore(val f: Future[EventedResult[_]]) extends AnyVal {
    def as[T](implicit ec: ExecutionContext): Future[EventedResult[T]] = f.map(_.as[T])

    def recoverNonFatal(implicit ec: ExecutionContext): Future[EventedResult[_]] = recoverNonFatal()
    def recoverNonFatal(block: Throwable => Unit = (t => ()))(implicit ec: ExecutionContext): Future[EventedResult[_]] = {
      Converters.EventedResultUnderscore.recoverNonFatal(f)(block)
    }
  }

  implicit class PimpFutureEventedResultNg[T](val f: Future[EventedResultNg[T]]) extends AnyVal {
    import Converters.EventedResultNg._

    def toEventedResultNgT(implicit ec: ExecutionContext): FutureEventedResultNgT[T] = EitherT(f.map(_.disjunction))

    def toEventedResult(implicit ec: ExecutionContext): Future[EventedResult[T]] = f.map(convertToEventedResult(_))

    def recoverNonFatal(implicit ec: ExecutionContext): Future[EventedResultNg[T]] = recoverNonFatal()
    def recoverNonFatal(block: Throwable => Unit = (t => ()))(implicit ec: ExecutionContext): Future[EventedResultNg[T]] = {
      Converters.EventedResultNg.recoverNonFatal(f)(block)
    }
  }

  implicit class PimpFutureEventedResultNgT[T](val f: FutureEventedResultNgT[T]) extends AnyVal {
    import Converters.EventedResultNg._

    def toEventedResultNg(implicit ec: ExecutionContext): Future[EventedResultNg[T]] = f.run.map(_.validation)
    def toEventedResult(implicit ec: ExecutionContext): Future[EventedResult[T]] = toEventedResultNg.map(convertToEventedResult(_))
  }

  implicit class PimpJsResult[T](val result: JsResult[T]) extends AnyVal {
    import Converters.JsResult._
    import Converters.EventedResultNg._

    def toEventedResultNg: EventedResultNg[T] = convertToEventedResultNg(result)

    def toFutureEventedResultNgT(implicit ec: ExecutionContext): FutureEventedResultNgT[T] = {
      Future.successful(result).toEventedResultNgT
    }

    def toEventedResult: EventedResult[T] = convertToEventedResult(toEventedResultNg)
  }

  implicit class PimpFutureJsResult[T](val f: Future[JsResult[T]]) extends AnyVal {
    import Converters.JsResult._

    def toEventedResultNg(implicit ec: ExecutionContext): Future[EventedResultNg[T]] = f.map(convertToEventedResultNg(_))
    def toEventedResultNgT(implicit ec: ExecutionContext): FutureEventedResultNgT[T] = toEventedResultNg.toEventedResultNgT

    def toEventedResult(implicit ec: ExecutionContext): Future[EventedResult[T]] = f map { r =>
      Converters.EventedResultNg.convertToEventedResult(convertToEventedResultNg(r))
    }
  }

  object Converters {
    object EventedResult {
      def convertToEventedResultNg[T](r: EventedResult[T]): EventedResultNg[T] = {
        r.fold(
          e => EventedFailureNg(e),
          s => EventedSuccessNg(s))
      }

      def recoverNonFatal[T](f: Future[EventedResult[T]])(block: Throwable => Unit = (t => ()))(implicit ec: ExecutionContext): Future[EventedResult[T]] = {
        f.recover {
          case NonFatal(e) => {
            block(e)
            EventedFailure(e)
          }
        }
      }
    }

    object EventedResultUnderscore {
      def recoverNonFatal(f: Future[EventedResult[_]])(block: Throwable => Unit = (t => ()))(implicit ec: ExecutionContext): Future[EventedResult[_]] = {
        f.recover {
          case NonFatal(e) => {
            block(e)
            EventedFailure(e)
          }
        }
      }
    }

    object EventedResultNg {
      def convertToEventedResult[T](r: EventedResultNg[T]): EventedResult[T] = {
        r.fold(e => EventedFailure(e.toList), s => EventedSuccess(s))
      }

      def recoverNonFatal[T](f: Future[EventedResultNg[T]])(block: Throwable => Unit = (t => ()))(implicit ec: ExecutionContext): Future[EventedResultNg[T]] = {
        f.recover {
          case NonFatal(e) => {
            block(e)
            EventedFailureNg(e)
          }
        }
      }
    }

    object JsResult {
      def convertToEventedResultNg[T](result: JsResult[T]): EventedResultNg[T] = {
        result.fold(
          jsError => {
            val errors = for {
              (path, errors) <- jsError
              validationError <- errors
            } yield { BasicError(validationError.toString) } // TODO: Handle this better..

            Failure(errors.toList.toNel getOrElse NonEmptyList(BasicError("Error: There were no errors!")))
          },
          _.successNel)
      }
    }
  }
}
