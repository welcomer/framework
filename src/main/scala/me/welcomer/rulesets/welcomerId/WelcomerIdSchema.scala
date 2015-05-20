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
package me.welcomer.rulesets.welcomerId

import scala.annotation.implicitNotFound
import play.api.libs.functional.syntax._
import play.api.libs.json._
import me.welcomer.utils.Jsonable

object WelcomerIdSchema {
  import me.welcomer.framework.pico.dsl.PicoEventSchema._

  object EventDomain {
    val WELCOMER_ID = "welcomerId"
    val VENDOR = "welcomerId.vendor"
    val USER = "welcomerId.user"
  }

  object EventType {
    val UNKNOWN_USER = "unknownUser"

    val INITIALISE_USER = "initialiseUser"

    val RETRIEVE_USER_DATA = "retrieveUserData"
    val USER_DATA = "userData"
    val USER_DATA_AVAILABLE = "userDataAvailable"

    val VENDOR_DATA_AVAILABLE = "vendorDataAvailable"

    // TODO: Should we change the 'eventDomain' for these events to 'edentiti'?
    val RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA = "retrieveEdentitiIdOrNewUserData"
    val EDENTITI_ID = "edentitiId"
    val EDENTITI_NEW_USER_DATA = "edentitiNewUserData"
    val EDENTITI_CREATE_NEW_USER = "edentitiCreateNewUser"
    val EDENTITI_NEW_USER = "edentitiNewUser"
    val EDENTITI_DATA_AVAILABLE = "edentitiDataAvailable"

    val USER_VERIFICATION_NOTIFICATION = "userVerificationNotification"

    val CREATED = "created"
  }

  val TRANSACTION_ID = "transactionId"
  val CHANNEL = "channel"
  val REPLY_TO = "replyTo"

  val FILTER = "filter"
  val DATA = "data"

  val EDENTITI_ID = "edentitiId"

  sealed abstract class WelcomerIdEvent extends Jsonable {
    //    def eventDomain: String // TODO: Should this be included?
    def eventType: String
  }

  // -----------
  // Components
  // -----------

  //  sealed trait ChannelType
  //  case object Email extends ChannelType
  //  case object Edentiti extends ChannelType

  case class ChannelDetails(channelType: String, channelId: String) extends Jsonable

  // TODO: Should we make 'type' an enum/objects? (see above)
  implicit lazy val channelDetailsFormat: Format[ChannelDetails] = (
    (__ \ "type").format[String] ~
    (__ \ "id").format[String])(ChannelDetails.apply, unlift(ChannelDetails.unapply))

  case class UserPicoMapping(userEci: String, vendorEci: String, channels: Set[ChannelDetails]) extends Jsonable

  implicit lazy val userPicoMappingFormat: Format[UserPicoMapping] = (
    (__ \ "userEci").format[String] ~
    (__ \ "vendorEci").format[String] ~
    (__ \ "channels").format[Set[ChannelDetails]])(UserPicoMapping.apply, unlift(UserPicoMapping.unapply))

  case class VendorPreferences(
    active: Boolean,
    ruleset: Option[String],
    redirectUrl: String,
    redirectImmediately: Boolean,
    emailNotification: Option[VendorNotificationEmailPreferences],
    callbackNotification: Option[VendorNotificationCallbackPreferences],
    apiKeys: VendorApiKeys) extends Jsonable

  implicit lazy val vendorPreferencesFormat: Format[VendorPreferences] = (
    (__ \ "active").format[Boolean] ~
    (__ \ "ruleset").formatNullable[String] ~
    (__ \ "redirectUrl").format[String] ~
    (__ \ "redirectImmediately").format[Boolean] ~
    (__ \ "emailNotification").formatNullable[VendorNotificationEmailPreferences] ~
    (__ \ "callbackNotification").formatNullable[VendorNotificationCallbackPreferences] ~
    (__ \ "apiKeys").format[VendorApiKeys])(VendorPreferences.apply, unlift(VendorPreferences.unapply))

  case class VendorNotificationEmailPreferences(
    active: Boolean,
    email: String) extends Jsonable

  implicit lazy val vendorEmailNotificationPreferencesFormat: Format[VendorNotificationEmailPreferences] = (
    (__ \ "active").format[Boolean] ~
    (__ \ "email").format[String])(VendorNotificationEmailPreferences.apply, unlift(VendorNotificationEmailPreferences.unapply))

  case class VendorNotificationCallbackPreferences(
    active: Boolean,
    url: String,
    template: String) extends Jsonable

  implicit lazy val vendorCallbackPreferencesFormat: Format[VendorNotificationCallbackPreferences] = (
    (__ \ "active").format[Boolean] ~
    (__ \ "url").format[String] ~
    (__ \ "template").format[String])(VendorNotificationCallbackPreferences.apply, unlift(VendorNotificationCallbackPreferences.unapply))

  case class VendorApiKeys(mandrill: Option[String] = None) extends Jsonable

  implicit lazy val vendorApiKeysFormat = Json.format[VendorApiKeys]

  // Events

  // --------
  // General
  // --------

  case class UnknownUser(
    transactionId: String,
    channelDetails: ChannelDetails) extends WelcomerIdEvent {
    def eventType = EventType.UNKNOWN_USER
  }

  implicit lazy val unknownUserFormat: Format[UnknownUser] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ CHANNEL).format[ChannelDetails])(UnknownUser.apply, unlift(UnknownUser.unapply))

  case class EdentitiId(
    transactionId: String,
    edentitiId: String) extends WelcomerIdEvent {
    def eventType = EventType.EDENTITI_ID
  }

  implicit lazy val edentitiIdFormat: Format[EdentitiId] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ EDENTITI_ID).format[String])(EdentitiId.apply, unlift(EdentitiId.unapply))

  // ---------------
  // Vendor Ruleset
  // ---------------

  case class InitialiseUser(
    //    transactionId: TransactionId,
    transactionId: String,
    channelDetails: ChannelDetails) extends WelcomerIdEvent {
    def eventType = EventType.INITIALISE_USER
  }

  implicit lazy val initialiseUserFormat: Format[InitialiseUser] = (
    //    __.read[TransactionId] ~
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ CHANNEL).format[ChannelDetails])(InitialiseUser.apply, unlift(InitialiseUser.unapply))

  // TODO: Can we combine this with the user version?
  case class VendorRetrieveUserData(
    transactionId: String,
    channelDetails: ChannelDetails,
    filter: List[String],
    replyTo: String) extends WelcomerIdEvent {
    def eventType = EventType.RETRIEVE_USER_DATA
  }

  implicit lazy val vendorRetrieveUserDataFormat: Format[VendorRetrieveUserData] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ CHANNEL).format[ChannelDetails] ~
    (__ \ FILTER).format[List[String]] ~
    (__ \ REPLY_TO).format[String])(VendorRetrieveUserData.apply, unlift(VendorRetrieveUserData.unapply))

  // TODO: Can we combine this with the user version?
  case class VendorUserDataAvailable(
    transactionId: String,
    channelDetails: ChannelDetails,
    data: JsObject) extends WelcomerIdEvent {
    def eventType = EventType.USER_DATA_AVAILABLE
  }

  implicit lazy val vendorUserDataAvailableFormat: Format[VendorUserDataAvailable] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ CHANNEL).format[ChannelDetails] ~
    (__ \ DATA).format[JsObject])(VendorUserDataAvailable.apply, unlift(VendorUserDataAvailable.unapply))

  case class VendorRetrieveEdentitiIdOrNewUserData(
    transactionId: String,
    channelDetails: ChannelDetails,
    //    filter: List[String],
    replyTo: String) extends WelcomerIdEvent {
    def eventType = EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA
  }

  implicit lazy val vendorRetrieveEdentitiIdOrNewUserDataFormat: Format[VendorRetrieveEdentitiIdOrNewUserData] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ CHANNEL).format[ChannelDetails] ~
    //    (__ \ FILTER).format[List[String]] ~
    (__ \ REPLY_TO).format[String])(VendorRetrieveEdentitiIdOrNewUserData.apply, unlift(VendorRetrieveEdentitiIdOrNewUserData.unapply))

  case class EdentitiNewUser(
    transactionId: String,
    channelDetails: ChannelDetails,
    edentitiId: String, // TODO: Is this needed? Included in data as userId
    data: JsObject) extends WelcomerIdEvent {
    def eventType = EventType.EDENTITI_NEW_USER
  }

  implicit lazy val edentitiNewUserFormat: Format[EdentitiNewUser] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ CHANNEL).format[ChannelDetails] ~
    (__ \ EDENTITI_ID).format[String] ~
    (__ \ DATA).format[JsObject])(EdentitiNewUser.apply, unlift(EdentitiNewUser.unapply))

  case class UserVerificationNotification(
    transactionId: String,
    channelDetails: ChannelDetails,
    data: JsObject) extends WelcomerIdEvent {
    def eventType = EventType.USER_VERIFICATION_NOTIFICATION
  }

  implicit lazy val userVerificationNotificationFormat: Format[UserVerificationNotification] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ CHANNEL).format[ChannelDetails] ~
    (__ \ DATA).format[JsObject])(UserVerificationNotification.apply, unlift(UserVerificationNotification.unapply))

  // -------------
  // User Ruleset
  // -------------

  case class Created(transactionId: String, vendorData: JsObject, userData: JsObject) extends WelcomerIdEvent {
    def eventType = EventType.CREATED
  }

  implicit lazy val createdFormat: Format[Created] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ "vendor").format[JsObject] ~
    (__ \ "user").format[JsObject])(Created.apply, unlift(Created.unapply))

  // TODO: Can we combine this with the vendor version?
  case class UserRetrieveUserData(
    transactionId: String,
    filter: List[String],
    replyTo: String) extends WelcomerIdEvent {
    def eventType = EventType.RETRIEVE_USER_DATA
  }

  implicit lazy val userRetrieveUserDataFormat: Format[UserRetrieveUserData] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ FILTER).format[List[String]] ~
    (__ \ REPLY_TO).format[String])(UserRetrieveUserData.apply, unlift(UserRetrieveUserData.unapply))

  // TODO: Can we combine this with the vendor version?
  case class UserUserDataAvailable(
    transactionId: String,
    data: JsObject) extends WelcomerIdEvent {
    def eventType = EventType.USER_DATA_AVAILABLE
  }

  implicit lazy val userUserDataAvailableFormat: Format[UserUserDataAvailable] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ DATA).format[JsObject])(UserUserDataAvailable.apply, unlift(UserUserDataAvailable.unapply))

  // TODO: Can we combine this with UserDataAvailable?
  case class UserData(
    transactionId: String,
    filter: List[String], data: JsObject) extends WelcomerIdEvent {
    def eventType = EventType.USER_DATA
  }

  implicit lazy val userDataFormat: Format[UserData] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ FILTER).format[List[String]] ~
    (__ \ DATA).format[JsObject])(UserData.apply, unlift(UserData.unapply))

  case class UserRetrieveEdentitiIdOrNewUserData(
    transactionId: String,
    //    filter: List[String],
    replyTo: String) extends WelcomerIdEvent {
    def eventType = EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA
  }

  implicit lazy val userRetrieveEdentitiIdOrNewUserDataFormat: Format[UserRetrieveEdentitiIdOrNewUserData] = (
    (__ \ TRANSACTION_ID).format[String] ~
    //    (__ \ FILTER).format[List[String]] ~
    (__ \ REPLY_TO).format[String])(UserRetrieveEdentitiIdOrNewUserData.apply, unlift(UserRetrieveEdentitiIdOrNewUserData.unapply))

  case class EdentitiNewUserData(
    transactionId: String,
    data: JsObject) extends WelcomerIdEvent {
    def eventType = EventType.EDENTITI_NEW_USER_DATA
  }

  implicit lazy val edentitiNewUserDataFormat: Format[EdentitiNewUserData] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ DATA).format[JsObject])(EdentitiNewUserData.apply, unlift(EdentitiNewUserData.unapply))

  case class EdentitiDataAvailable(
    transactionId: String,
    edentitiId: String,
    data: JsObject) extends WelcomerIdEvent {
    def eventType = EventType.EDENTITI_DATA_AVAILABLE
  }

  implicit lazy val edentitiDataAvailableFormat: Format[EdentitiDataAvailable] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ EDENTITI_ID).format[String] ~
    (__ \ DATA).format[JsObject])(EdentitiDataAvailable.apply, unlift(EdentitiDataAvailable.unapply))

  // -------------
  // Welcomer Ruleset
  // -------------

  case class EdentitiCreateNewUser(
    transactionId: String,
    data: JsObject,
    ruleset: Option[String]) extends WelcomerIdEvent {
    def eventType = EventType.EDENTITI_NEW_USER_DATA
  }

  implicit lazy val edentitiCreateNewUserFormat: Format[EdentitiCreateNewUser] = (
    (__ \ TRANSACTION_ID).format[String] ~
    (__ \ DATA).format[JsObject] ~
    (__ \ "ruleset").format[Option[String]])(EdentitiCreateNewUser.apply, unlift(EdentitiCreateNewUser.unapply))
}
