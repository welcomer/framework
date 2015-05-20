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

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem

import oauth.signpost.AbstractOAuthProvider
import spray.client.pipelining._
import spray.http._

class SprayOAuthProvider(
  requestTokenEndpointUrl: String,
  accessTokenEndpointUrl: String,
  authorizationWebsiteUrl: String)(implicit system: ActorSystem, timeout: Duration)
  extends AbstractOAuthProvider(requestTokenEndpointUrl, accessTokenEndpointUrl, authorizationWebsiteUrl) {

  import system.dispatcher // execution context for futures

  private val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  def createRequest(endpointUrl: String): oauth.signpost.http.HttpRequest = {
    new HttpRequestAdapter(Post(endpointUrl))
  }

  def sendRequest(request: oauth.signpost.http.HttpRequest): oauth.signpost.http.HttpResponse = {
    val sprayRequest = request.unwrap().asInstanceOf[spray.http.HttpRequest]
    val response = pipeline(sprayRequest)

    new HttpResponseAdapter(Await.result(response, timeout));
  }
}
