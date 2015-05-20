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
package me.welcomer.link.aws.auth

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials

object StaticCredentialsProvider {
  def apply(accessKey: String, secretKey: String): StaticCredentialsProvider = apply(new BasicAWSCredentials(accessKey, secretKey))
}

case class StaticCredentialsProvider(credentials: AWSCredentials) extends AWSCredentialsProvider {
  def getCredentials: AWSCredentials = credentials

  def refresh: Unit = Unit // NOOP
}
