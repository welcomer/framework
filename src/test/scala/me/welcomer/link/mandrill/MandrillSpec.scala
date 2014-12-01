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
package me.welcomer.link.mandrill

import scala.concurrent.duration._
import scala.language.postfixOps
import play.api.libs.json._
import me.welcomer.framework.testUtils.WelcomerFrameworkSingleActorSystemTest
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

class MandrillSpec extends WelcomerFrameworkSingleActorSystemTest {
  import me.welcomer.framework.utils.ImplicitConversions._
  import me.welcomer.link.mandrill.MandrillProtocol._

  implicit val timeout: Timeout = 5 seconds

  val mandrillKey = ConfigFactory.load().getString("link.mandrill.test-key")
  val mandrillService: MandrillRepository = new MandrillApi(mandrillKey)

  def assertAllSent(r: MandrillResponse) = {
    r shouldBe a[SendResponse]

    val allSent = r.asInstanceOf[SendResponse].responses.foldLeft(true) { _ && _.status == "sent" }
    assert(allSent, "Not all messages sent successfully: " + r)
  }

  "MandrillService" should {

    "correctly send 'Message' email" in {
      val msg = Message(
        to = ToStruct("testTo@example.com", "Test To", SendType.To),
        from = FromStruct("testFrom@welcomer.me"),
        subject = "Test Subject",
        text = "Test Message")

      val r = mandrillService.messages.send(msg).futureValue

      println(r)

      assertAllSent(r)
    }

    "correctly handle/send 'Message' withMergedJson (from_email)" in {
      val msg = Message(
        to = ToStruct("testTo@example.com", "Test To", SendType.To),
        from = FromStruct("testFrom@welcomer.me"),
        subject = "Test Subject",
        text = "Test Message").withMergedJson(Json.obj("from_email" -> "overridedFrom@welcomer.me"))

      (msg \ "from_email").as[String] shouldEqual "overridedFrom@welcomer.me"

      val r = mandrillService.messages.send(msg).futureValue

      println(r)

      assertAllSent(r)
    }

    "correctly handle/send 'Message' withAttachments" in {
      val msg = Message(
        to = ToStruct("testTo@example.com", "Test To", SendType.To),
        from = FromStruct("testFrom@welcomer.me"),
        subject = "Test Subject",
        text = "Test Message")

      val attachments = Attachments(Attachment("mime", "name", "content"))
      val msgWithAttachments = msg.withAttachments(attachments)

      println(Json.prettyPrint(attachments))
      println(Json.prettyPrint(msgWithAttachments))

      val validAttachmentJson = Json.arr(
        Json.obj(
          "type" -> "mime",
          "name" -> "name",
          "content" -> "content"))

      (msgWithAttachments \ "attachments").asOpt[JsArray] shouldEqual Some(validAttachmentJson)
      msgWithAttachments.as[Attachments] shouldEqual attachments
      msgWithAttachments.attachments shouldEqual Some(attachments)

      val r = mandrillService.messages.send(msg).futureValue

      println(r)

      assertAllSent(r)
    }

  }
}
