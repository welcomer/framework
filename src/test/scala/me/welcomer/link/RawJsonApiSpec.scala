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
package me.welcomer.link

import scala.concurrent.duration._
import scala.util.Success
import scala.language.postfixOps

import akka.util.Timeout
import akka.util.Timeout.durationToTimeout

import play.api.libs.json._

import me.welcomer.framework.testUtils.WelcomerFrameworkSingleActorSystemTest

class RawJsonApiSpec extends WelcomerFrameworkSingleActorSystemTest {
  import me.welcomer.framework.utils.ImplicitConversions._

  implicit val timeout: Timeout = 5 seconds

  val rawJsonApi: RawJsonApi = new RawJsonApi

  "RawJsonApi" should {

    "correctly 'get'" in {
      val validSuccessJson = Success(Json.obj("foo" -> "bar"))

      val (r, rJson) = rawJsonApi.get("http://echo.jsontest.com/foo/bar").futureValue

      println(r)

      r.status.intValue shouldEqual 200
      rJson shouldEqual validSuccessJson
    }

    "correctly 'post'" in {
      val postJson = None

      val validSuccessJson = Success(Json.obj("foo" -> "bar"))

      val (r, rJson) = rawJsonApi.post("http://echo.jsontest.com/foo/bar", postJson).futureValue

      println(r)

      r.status.intValue shouldEqual 200
      rJson shouldEqual validSuccessJson
    }

  }
}
