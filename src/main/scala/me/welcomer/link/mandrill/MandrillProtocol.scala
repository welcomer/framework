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

import play.api.libs.functional.syntax._
import play.api.libs.json._
import me.welcomer.framework.utils.Jsonable
import me.welcomer.framework.utils.Jsonable.jsonableToJsObject

object MandrillProtocol {
  sealed trait Mandrill

  sealed trait MandrillRequest extends Mandrill {
    def key: String
  }

  sealed trait MandrillResponse extends Mandrill
  sealed trait MandrillSuccessResponse extends MandrillResponse
  sealed trait MandrillErrorResponse extends MandrillResponse

  // General

  case class MandrillError(status: String, code: Int, name: String, message: String) extends MandrillErrorResponse
  implicit lazy val errorFormat: OFormat[MandrillError] = Json.format[MandrillError]

  // Messages (https://mandrillapp.com/api/docs/messages.JSON.html)

  // Messages:Send (https://mandrillapp.com/api/docs/messages.JSON.html#method=send)

  case class Send(key: String, message: Message) extends MandrillRequest
  implicit lazy val sendFormat: OFormat[Send] = Json.format[Send]

  //  abstract class Message extends Jsonable

  case class Message(
    to: List[ToStruct],
    from: FromStruct,
    subject: String = "",
    html: Option[String] = None,
    text: Option[String] = None,
    raw: JsObject = Json.obj()) extends Jsonable {
    def withMergedJson(j: JsObject) = this.copy(raw = raw ++ j)
    def withAttachments(a: Attachments) = withMergedJson(a)

    def attachments: Option[Attachments] = raw.asOpt[Attachments]
  }

  implicit lazy val messageFormat: OFormat[Message] = (
    (__ \ "to").format[List[ToStruct]] ~
    fromStructFormat ~
    (__ \ "subject").format[String] ~
    (__ \ "html").formatNullable[String] ~
    (__ \ "text").formatNullable[String] ~
    (__).format[JsObject])(Message.apply, unlift(Message.unapply))

  case class FromStruct(
    email: String,
    name: Option[String] = None) extends Jsonable

  implicit lazy val fromStructFormat: OFormat[FromStruct] = (
    (__ \ "from_email").format[String](Reads.email) ~
    (__ \ "from_name").formatNullable[String])(FromStruct.apply, unlift(FromStruct.unapply))

  case class ToStruct(
    email: String,
    name: Option[String] = None,
    sendType: SendType = SendType.To) extends Jsonable

  implicit lazy val toStructFormat: OFormat[ToStruct] = (
    (__ \ "email").format[String](Reads.email) ~
    (__ \ "name").formatNullable[String] ~
    sendTypeFormat)(ToStruct.apply, unlift(ToStruct.unapply))

  sealed abstract class SendType(sendType: String) extends Jsonable {
    override def toString = sendType
  }

  object SendType {
    def apply(sendType: String): SendType = sendType match {
      case "to" => To
      case "cc" => Cc
      case "bcc" => Bcc
    }

    def unapply(sendType: SendType): String = {
      sendType.toString
    }

    case object To extends SendType("to")
    case object Cc extends SendType("cc")
    case object Bcc extends SendType("bcc")
  }

  implicit lazy val sendTypeFormat: OFormat[SendType] =
    (__ \ "type").format[String].inmap(SendType(_), _.toString)

  case class Attachments(attachments: List[Attachment]) extends Jsonable

  implicit lazy val attachmentsFormat: OFormat[Attachments] =
    (__ \ "attachments").format[List[Attachment]].inmap(Attachments(_), _.attachments)

  case class Attachment(
    mimeType: String,
    name: String,
    content: String) extends Jsonable

  implicit lazy val attachmentFormat: OFormat[Attachment] = (
    (__ \ "type").format[String] ~
    (__ \ "name").format[String] ~
    (__ \ "content").format[String])(Attachment.apply, unlift(Attachment.unapply))

  case class SendResponse(responses: List[SendResponseStruct]) extends MandrillSuccessResponse

  implicit lazy val sendResponseFormat: OFormat[SendResponse] =
    (__).format[List[SendResponseStruct]].inmap(SendResponse(_), _.responses)

  case class SendResponseStruct(
    email: String,
    status: String,
    rejectReason: Option[String],
    id: String)

  implicit lazy val sendResponseStructFormat: OFormat[SendResponseStruct] = (
    (__ \ "email").format[String](Reads.email) ~
    (__ \ "status").format[String] ~
    (__ \ "reject_reason").formatNullable[String] ~
    (__ \ "_id").format[String])(SendResponseStruct.apply, unlift(SendResponseStruct.unapply))

}