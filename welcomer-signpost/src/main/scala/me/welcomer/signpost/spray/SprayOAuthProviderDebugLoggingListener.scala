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

import akka.event.LoggingAdapter

import oauth.signpost.OAuthProviderListener

class SprayOAuthProviderDebugLoggingListener(implicit log: LoggingAdapter) extends OAuthProviderListener {
  def prepareRequest(request: oauth.signpost.http.HttpRequest): Unit = {
    // Called after the request has been created and default headers added, but before the request has been signed.
  }

  def prepareSubmission(request: oauth.signpost.http.HttpRequest): Unit = {
    // Called after the request has been signed, but before it's being sent.
  }

  def onResponseReceived(request: oauth.signpost.http.HttpRequest, response: oauth.signpost.http.HttpResponse): Boolean = {
    // Called when the server response has been received.
    val sprayRequest = request.unwrap().asInstanceOf[spray.http.HttpRequest]
    val sprayResponse = response.unwrap().asInstanceOf[spray.http.HttpResponse]

    log.debug("Request: {} \n\nResponse: {}", sprayRequest, sprayResponse)

    false // returning true means you have handled the response, and the provider will return immediately
  }
}
