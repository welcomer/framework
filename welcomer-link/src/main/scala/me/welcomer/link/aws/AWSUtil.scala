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
package me.welcomer.link.aws

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler

object AWSUtil {
  /**
   * Helper to create a [[com.amazonaws.handlers.AsyncHandler]] that can give us a [[scala.concurrent.Future]]
   */
  object FutureAsyncHandler {
    def apply[Request <: AmazonWebServiceRequest, Result]: FutureAsyncHandler[Request, Result] = new FutureAsyncHandler()
  }

  class FutureAsyncHandler[Request <: AmazonWebServiceRequest, Result] extends AsyncHandler[Request, Result] {
    private val promise = Promise[Result]

    val future: Future[Result] = promise.future

    def onSuccess(request: Request, result: Result): Unit = promise.complete(Success(result))

    def onError(e: Exception): Unit = promise.complete(Failure(e))
  }
}
