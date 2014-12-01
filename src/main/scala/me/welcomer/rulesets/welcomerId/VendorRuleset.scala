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
import scala.collection.mutable.HashMap
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import me.welcomer.framework.models.{ ECI, Pico }
import me.welcomer.framework.pico.{ EventedEvent, PicoRuleset }
import me.welcomer.framework.pico.service.PicoServicesComponent
import me.welcomer.rulesets.welcomerId.WelcomerIdSchema._
import play.api.libs.json._
import scala.util.control.NonFatal

class VendorRuleset(picoServices: PicoServicesComponent#PicoServices) extends PicoRuleset(picoServices) with VendorRulesetHelper {
  import context._
  import me.welcomer.rulesets.welcomerId.WelcomerIdSchema._

  subscribeToEventDomain(EventDomain.WELCOMER_ID)
  subscribeToEventDomain(EventDomain.VENDOR)
  subscribeToEventDomain(EventDomain.USER)

  // TODO: This is likely a fairly common pattern.. can we extract it?
  var transactions = HashMap[String, Option[String]]() // TODO: Get rid of Option, make it just String?

  def selectWhen = {
    case event @ EventedEvent(EventDomain.VENDOR, eventType, _, _, _) => {
      logEventInfo
      eventType match {
        case EventType.INITIALISE_USER => handleEvent[InitialiseUser](handleInitialiseUser)
        case EventType.RETRIEVE_USER_DATA => handleEvent[VendorRetrieveUserData](handleRetrieveUserData)
        case EventType.USER_DATA_AVAILABLE => handleEvent[VendorUserDataAvailable](handleUserDataAvailable)
        case EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA => handleEvent[VendorRetrieveEdentitiIdOrNewUserData](handleVendorRetrieveEdentitiIdOrNewUserData)
        case EventType.EDENTITI_NEW_USER => handleEvent[EdentitiNewUser](handleEdentitiNewUser)
        case EventType.USER_VERIFICATION_NOTIFICATION => handleEvent[UserVerificationNotification](handleUserVerificationNotification)
        case _ => log.debug("Unhandled {} EventedEvent Received ({})", EventDomain.VENDOR, event)
      }
    }
    case event @ EventedEvent(EventDomain.USER, eventType, _, _, _) => {
      logEventInfo
      eventType match {
        case EventType.USER_DATA => handleEvent[UserData](handleUserData)
        case EventType.EDENTITI_ID => handleEvent[EdentitiId](handleEdentitiId)
        case EventType.EDENTITI_NEW_USER_DATA => handleEvent[EdentitiNewUserData](handleEdentitiNewUserData)
        case _ => log.debug("Unhandled {} EventedEvent Received ({})", EventDomain.USER, event)
      }
    }
  }
}

trait VendorRulesetHelper extends AnyRef { this: VendorRuleset =>
  import me.welcomer.rulesets.welcomerId.WelcomerIdSchema._
  import me.welcomer.framework.utils.ImplicitConversions._

  val USER_NAMESPACE = "user"
  val VENDOR_PREFERENCES_NAMESPACE = "vendorPreferences"

  val USER_RULESETS = Set("welcomerId.UserRuleset")

  // -------
  // Vendor 
  // -------

  protected def handleInitialiseUser(o: InitialiseUser, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    val transactionId = curTransactionId

    retrieveUserMapping(o.channelDetails) map {
      case Some(mapping) => log.info("ECI found for user, nothing to do: {} ({})", mapping.userEci, event)
      case None => {
        mapNewUserPico(o.channelDetails) onComplete {
          case Success((userPico, userEci, vendorEci, storeResult)) => {
            log.info("User pico created & mapped: {}->{} ({})", o.channelDetails, userEci.eci, storeResult);

            val vendorData = Json.obj(
              "vendorId" -> vendorEci.eci, // Do we actually have/need a 'vendorId' anymore?
              "vendorEci" -> vendorEci.eci)

            val userData = Json.obj() // TODO: Will we ever have any data to store here? Handle certain known channels maybe? (email, mobile, etc)

            raiseRemoteEvent(
              EventDomain.USER,
              EventType.CREATED,
              Created(o.transactionId, vendorData, userData),
              userEci.eci)
          }
          case Failure(e) => log.error(e, "Error creating/mapping new User pico: {} ({}, {})", e.getMessage(), o, event);
        }
      }
    }
  }

  protected def handleRetrieveUserData(o: VendorRetrieveUserData, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    mapReplyToEci(o.transactionId, o.replyTo)

    // Mapping found, send event to user pico
    def success(m: UserPicoMapping) = {
      log.info("Sending {}::{} to {} ({})", EventDomain.USER, EventType.RETRIEVE_USER_DATA, m.userEci, o.transactionId);

      EventedEvent(
        EventDomain.USER,
        EventType.RETRIEVE_USER_DATA,
        attributes = UserRetrieveUserData(o.transactionId, o.filter, m.vendorEci),
        entityId = m.userEci)
    }

    def failure = unknownUserFailure(o.transactionId, o.channelDetails)

    raiseRemoteEventWithChannel(o.channelDetails, success, failure)
  }

  protected def handleUserDataAvailable(o: VendorUserDataAvailable, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    mapReplyToEci(o.transactionId)

    def success(m: UserPicoMapping) = {
      log.info("Sending {}::{} to {} ({})", EventDomain.USER, EventType.USER_DATA_AVAILABLE, m.userEci, o.transactionId);

      EventedEvent(
        EventDomain.USER,
        EventType.USER_DATA_AVAILABLE,
        attributes = UserUserDataAvailable(o.transactionId, o.data),
        entityId = m.userEci)
    }

    def failure = unknownUserFailure(o.transactionId, o.channelDetails)

    raiseRemoteEventWithChannel(o.channelDetails, success, failure)
  }

  protected def handleVendorRetrieveEdentitiIdOrNewUserData(o: VendorRetrieveEdentitiIdOrNewUserData, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    mapReplyToEci(o.transactionId, o.replyTo)

    def success(m: UserPicoMapping) = {
      log.info("Sending {}::{} to {} ({})", EventDomain.USER, EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA, m.userEci, o.transactionId);

      EventedEvent(
        EventDomain.USER,
        EventType.RETRIEVE_EDENTITI_ID_OR_NEW_USER_DATA,
        attributes = UserRetrieveEdentitiIdOrNewUserData(o.transactionId, m.vendorEci),
        entityId = m.userEci)
    }

    def failure = unknownUserFailure(o.transactionId, o.channelDetails)

    raiseRemoteEventWithChannel(o.channelDetails, success, failure)
  }

  protected def handleEdentitiNewUser(o: EdentitiNewUser, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    //    mapReplyToEci(o.transactionId)

    def success(m: UserPicoMapping) = {
      log.debug("handleEdentitiNewUser Success: {} ({})", o, event)

      val newChannelDetails = ChannelDetails(EDENTITI_ID, o.edentitiId)

      val result = m.channels.contains(newChannelDetails) match {
        case false => storeNewChannelDetails(m.userEci, newChannelDetails)
        case true => Future(Json.obj()) // Do Nothing
      }

      result onComplete {
        case Success(s) => {
          log.info("Sending {}::{} to {} ({})", EventDomain.USER, EventType.EDENTITI_DATA_AVAILABLE, m.userEci, o.transactionId);

          raiseRemoteEvent(
            EventedEvent(
              EventDomain.USER,
              EventType.EDENTITI_DATA_AVAILABLE,
              attributes = EdentitiDataAvailable(o.transactionId, o.edentitiId, o.data),
              entityId = m.userEci))
        }
        case Failure(e) => log.error(e, e.getMessage())
      }
    }

    def failure = unknownUserFailure(o.transactionId, o.channelDetails) map { raiseRemoteEvent(_) }

    forChannel(o.channelDetails, success, failure)
  }

  protected def handleUserVerificationNotification(o: UserVerificationNotification, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    //    mapReplyToEci(o.transactionId)

    def success(m: UserPicoMapping) = {
      log.debug("handleUserVerificationNotification Success: {} ({})", o, event)

      // Send data to user pico
      raiseRemoteEvent(
        EventedEvent(
          EventDomain.USER,
          EventType.EDENTITI_DATA_AVAILABLE,
          attributes = EdentitiDataAvailable(o.transactionId, o.channelDetails.channelId, o.data),
          entityId = m.userEci))

      // Handle callbacks if verified
      (for {
        pref <- retrieveVendorPreferences
        if isVerified(o.data)
      } yield {
        // TODO: These should probably be handled in their own events..
        handleNotificationEmail(o.data, pref)
        handleNotificationCallback(o.data, pref)
      }) recover {
        case NonFatal(e) => log.error(e, "handleUserVerificationNotification: Error: " + e.getMessage())
      }
    }

    def failure = unknownUserFailure(o.transactionId, o.channelDetails) map { raiseRemoteEvent(_) }

    forChannel(o.channelDetails, success, failure)
  }

  def isVerified(result: JsObject): Boolean = {
    // TODO: This should probably be made 'vendor customizable" at some point in the future
    val verifiedStates = List[String]("VERIFIED", "VERIFIED_ADMIN", "VERIFIED_WITH_CHANGES", "PENDING")

    val isVerified = (result \ "outcome").asOpt[String] match {
      case Some(outcome) => verifiedStates.contains(outcome)
      case None => false
    }

    log.debug("isVerified={}", isVerified)

    isVerified
  }

  // TODO: Implement this
  def handleNotificationEmail(result: JsObject, pref: VendorPreferences)(implicit ec: ExecutionContext): Unit = {
    import me.welcomer.link.mandrill.MandrillProtocol._
    log.debug("WelcomerIdVendor::handleNotificationEmail")

    // TODO: Load/generate this from a template?
    val emailBody = "WelcomerID - Verification Result ID: TODOID TODONAME is verified. This occurred on 01 Jan 2014, 01:00am\n\n" + Json.prettyPrint(result)

    def verifiedMessage(email: String) = Message(
      List(ToStruct(email)),
      FromStruct("verification@welcomerid.me"), // TODO: Move this to config?
      "WelcomerID - Verified user", // TODO: Move this to config?
      text = emailBody)

    val emailPrefs = for {
      key <- pref.apiKeys.mandrill
      emailPrefs <- pref.emailNotification
    } yield { (key, emailPrefs.email, emailPrefs.active) }

    emailPrefs map {
      case (key, email, active) =>
        val mandrill = _picoServices.link.mandrill(key)

        (for {
          response <- mandrill.messages.send(verifiedMessage(email)) // TODO: Add PDF attachment of verification outcome
        } yield {
          response
        }) onComplete {
          case default => log.debug("EmailSendResponse: {}", default)
        }
    } getOrElse { log.debug("Not sending notification email due to prefs: {}", emailPrefs) }
  }

  // TODO: Implement this
  def handleNotificationCallback(result: JsObject, pref: VendorPreferences)(implicit ec: ExecutionContext): Unit = {
    log.debug("WelcomerIdVendor::handleNotificationCallback")

    pref.callbackNotification map { p =>
      if (p.active) {
        val filledTemplate = p.template
          .replace("<email>", "TODO GET EMAIL FROM AUTH OR SOMEWHERE")
          .replace("<userId>", (result \ "userId").asOpt[String].getOrElse("")); // TODO: Replace placeholders with parts from ?result?

        val api = _picoServices.link.rawJsonApi
        api.post(p.url, Json.parse(p.template)) onComplete {
          case default => log.debug("CallbackSendResponse: {}", default)
        }
      } else { log.debug("Not sending notification callback due to prefs: active={}", p.active) }
    } getOrElse { log.debug("Not sending notification callback due to prefs: {}", pref.callbackNotification) }
  }

  // -----
  // User
  // -----

  protected def handleUserData(userData: UserData, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    replyToTransaction(
      EventDomain.WELCOMER_ID,
      EventType.USER_DATA,
      userData)(event, userData.transactionId)
  }

  protected def handleEdentitiId(edentitiId: EdentitiId, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    replyToTransaction(
      EventDomain.WELCOMER_ID,
      EventType.EDENTITI_ID,
      edentitiId)(event, edentitiId.transactionId)
  }

  protected def handleEdentitiNewUserData(o: EdentitiNewUserData, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    retrieveVendorPreferences map { pref =>
      replyToTransaction(
        EventDomain.WELCOMER_ID,
        EventType.EDENTITI_CREATE_NEW_USER,
        EdentitiCreateNewUser(o.transactionId, o.data, pref.ruleset))(event, o.transactionId)
    }
  }

  // --------
  // Helpers
  // --------

  protected def mapNewUserPico(channelDetails: ChannelDetails)(implicit ec: ExecutionContext): Future[(Pico, ECI, ECI, JsObject)] = {
    def channelType = channelDetails.channelType
    def channelId = channelDetails.channelId

    lazy val eciDescription = s"[UserPico] $channelType->$channelId"

    for {
      (userPico, userEci) <- _picoServices.picoManagement.createNewPico(USER_RULESETS)
      vendorEci <- _picoServices.eci.generate(Some(eciDescription))
      storeResult <- storePicoMapping(UserPicoMapping(userEci.eci, vendorEci.eci, Set(channelDetails)))
    } yield { (userPico, userEci, vendorEci, storeResult) }
  }

  // TODO: This is likely a fairly common pattern.. can we extract it?
  def retrieveUserMapping(channelDetails: ChannelDetails)(implicit ec: ExecutionContext): Future[Option[UserPicoMapping]] = {
    val selector = Json.obj(
      "mappings.channels" -> Json.obj(
        "$elemMatch" -> Json.obj(
          "type" -> channelDetails.channelType,
          "id" -> channelDetails.channelId)))
    val projection = Json.obj("mappings.channels.$" -> 1)

    _picoServices.pds.retrieve(selector, projection, USER_NAMESPACE) map {
      case Some(json) => (json \ "mappings")(0).asOpt[UserPicoMapping]
      case None => None
    }
  }

  def storeNewChannelDetails(eci: String, newChannelDetails: ChannelDetails)(implicit ec: ExecutionContext): Future[JsObject] = {
    val selector = Json.obj("mappings.userEci" -> eci)
    val arrayKey = "mappings.$.channels"

    _picoServices.pds.pushArrayItem(arrayKey, newChannelDetails, USER_NAMESPACE, selector, unique = true) map { result =>
      (result \ "n").asOpt[Int] match {
        case Some(n) if n > 0 => result
        case _ => throw new Throwable(s"No documents were updated ($result) arrayKey=$arrayKey, arrayItem=$newChannelDetails")
      }
    }
  }

  def retrieveVendorPreferences(implicit ec: ExecutionContext): Future[VendorPreferences] = {
    _picoServices.pds.retrieveAllItems(VENDOR_PREFERENCES_NAMESPACE) map {
      case Some(pref) => pref.as[VendorPreferences]
      case None => throw new Throwable("Vendor preferences weren't found")
    }
  }

  // TODO: This is likely a fairly common pattern.. can we extract it?
  protected def storePicoMapping(userPicoMapping: UserPicoMapping)(implicit ec: ExecutionContext): Future[JsObject] = {
    val arrayKey = "mappings"
    val arrayItem = Json.toJson(userPicoMapping)

    _picoServices.pds.pushArrayItem(arrayKey, arrayItem, USER_NAMESPACE, unique = true) map { result =>
      (result \ "n").asOpt[Int] match {
        case Some(n) if n > 0 => result
        case _ => throw new Throwable(s"No documents were updated ($result) arrayKey=$arrayKey, arrayItem=$arrayItem")
      }
    }
  }

  // TODO: This is likely a fairly common pattern.. can we extract it?
  protected def unknownUserFailure(transactionId: String, channelDetails: ChannelDetails): Option[EventedEvent] = {
    retrieveReplyToEci(transactionId, true) map { replyTo =>
      log.warning(s"Sending {}::{}({}) to ${replyTo} ({})", EventDomain.WELCOMER_ID, EventType.UNKNOWN_USER, channelDetails, transactionId);

      Some(
        EventedEvent(
          EventDomain.WELCOMER_ID,
          EventType.UNKNOWN_USER,
          attributes = UnknownUser(transactionId, channelDetails),
          entityId = replyTo))
    } getOrElse {
      log.error("Giving Up: UnknownUser and no replyTo ECI found for transactionId ({})", transactionId)

      None
    }
  }

  // TODO: This is likely a fairly common pattern.. can we extract it?
  protected def forChannel(
    channelDetails: ChannelDetails,
    validChannelHandler: UserPicoMapping => Unit,
    invalidChannelHandler: => Unit)(implicit ec: ExecutionContext): Unit = {
    retrieveUserMapping(channelDetails) map {
      case Some(mapping) => {
        log.debug("[forChannel] ValidChannel: " + channelDetails)
        validChannelHandler(mapping)
      }
      case None => {
        log.debug("[forChannel] InvalidChannel: " + channelDetails)
        invalidChannelHandler
      }
    } recover {
      case NonFatal(e) => log.error(e, "[forChannel] Error: " + e.getMessage())
    }
  }

  def raiseRemoteEventWithChannel(
    channelDetails: ChannelDetails,
    successEvent: UserPicoMapping => Future[Option[EventedEvent]],
    failureEvent: => Future[Option[EventedEvent]])(implicit ec: ExecutionContext): Unit = {
    def success(m: UserPicoMapping) = successEvent(m) map { _ map { raiseRemoteEvent(_) } }
    def failure = failureEvent map { _ map { raiseRemoteEvent(_) } }

    forChannel(channelDetails, success, failure)
  }

  // TODO: This is likely a fairly common pattern.. can we extract it?
  def mapReplyToEci(transactionId: String, replyTo: Option[String] = None) /*(implicit event: EventedEvent)*/ = {
    //    val storeReplyTo = replyTo match {
    //      case replyTo @ Some(_) => replyTo
    //      case None => event.entityId // This shouldn't be entityId, it should somehow map to the ECI allowed to use this entityId, as we want to know where it came from..
    //    }
    transactions += (transactionId -> replyTo)
  }

  // TODO: This is likely a fairly common pattern.. can we extract it?
  def retrieveReplyToEci(transactionId: String, popEntry: Boolean): Option[String] = {
    transactions.get(transactionId) flatMap { replyToOpt =>
      if (popEntry) transactions -= transactionId // TODO: Is closing over this a bad idea? Maybe send a message so we can update it syncronously? (probably pull it up into PicoRuleset)

      replyToOpt
    }
  }

  // TODO: This is likely a fairly common pattern.. can we extract it?
  def replyToTransaction(eventDomain: String, eventType: String, attributes: JsObject)(implicit event: EventedEvent, transactionIdOpt: Option[String]) = {
    transactionIdOpt map { implicit transactionId =>
      retrieveReplyToEci(transactionId, true) match {
        //      transactions.get(transactionId) map { replyToOpt =>
        //        transactions = transactions -= transactionId // TODO: Is closing over this a bad idea? Maybe send a message so we can update it syncronously? (probably pull it up into PicoRuleset)

        //        replyToOpt match {
        case Some(replyTo) => raiseRemoteEvent(eventDomain, eventType, attributes, replyTo)
        case None => raisePicoEvent(eventDomain, eventType, attributes)
      }
      //      } getOrElse { log.error("'{}' not found in map, don't know who to reply to ({})", TRANSACTION_ID, event) }
    } getOrElse { log.error("No '{}' to lookup so we can't map it to '{}' ({})", TRANSACTION_ID, REPLY_TO, event) }
  }
}