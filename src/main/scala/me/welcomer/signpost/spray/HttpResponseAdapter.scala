/*  Copyright 2014 White Label Personal Clouds Pty Ltd
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

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class HttpResponseAdapter(response: spray.http.HttpResponse) extends oauth.signpost.http.HttpResponse {

  def getStatusCode(): Int = response.status.intValue

  def getReasonPhrase(): String = response.status.reason

  def getContent(): InputStream = {
    response.entity.toOption match {
      case Some(entity) => new ByteArrayInputStream(entity.data.toByteArray)
      case None => throw new IOException()
    }
  }

  /**
   * Returns the underlying response object, in case you need to work on it
   * directly.
   *
   * @return the wrapped response object
   */
  def unwrap(): Object = response
}