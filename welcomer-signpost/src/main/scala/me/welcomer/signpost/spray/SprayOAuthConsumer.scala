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
package me.welcomer.signpost.spray

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import oauth.signpost.AbstractOAuthConsumer

class SprayOAuthConsumer(
  consumerKey: String,
  consumerSecret: String) extends AbstractOAuthConsumer(consumerKey, consumerSecret) with SignatureCalculator {

  this.setTokenWithSecret(consumerKey, consumerSecret)

  override protected def wrap(request: Object): oauth.signpost.http.HttpRequest = {
    if (!request.isInstanceOf[spray.http.HttpRequest]) {
      throw new IllegalArgumentException(
        "This consumer expects requests of type "
          + spray.http.HttpRequest.getClass.getCanonicalName())
    }

    new HttpRequestAdapter(request.asInstanceOf[spray.http.HttpRequest])
  }

  override def sign(request: spray.http.HttpRequest): Try[spray.http.HttpRequest] = {
    Try(sign(wrap(request))) map { _.unwrap.asInstanceOf[spray.http.HttpRequest] }
  }
}

trait SignatureCalculator {
  /**
   * Sign a request
   */
  def sign(request: spray.http.HttpRequest): Try[spray.http.HttpRequest]

  /**
   * Sign a request raising RuntimeException on Failure
   */
  def signE(request: spray.http.HttpRequest): spray.http.HttpRequest = {
    sign(request) match {
      case Success(signedRequest) => signedRequest
      case Failure(e) => throw new RuntimeException(e)
    }
  }
}

trait SprayRequestOAuthSigner {
  import spray.http.HttpRequest

  def signOAuth(oAuthCalc: SprayOAuthConsumer): (HttpRequest => HttpRequest) = { request: HttpRequest =>
    oAuthCalc.signE(request)
  }
}
