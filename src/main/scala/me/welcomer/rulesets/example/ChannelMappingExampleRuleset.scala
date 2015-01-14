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
package me.welcomer.rulesets.example

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import play.api.libs.functional.syntax._
import play.api.libs.json._
import me.welcomer.framework.pico.BasicError
import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.EventedFailure
import me.welcomer.framework.pico.EventedFunction
import me.welcomer.framework.pico.EventedSuccess
import me.welcomer.framework.pico.PicoRuleset
import me.welcomer.framework.pico.ruleset.patterns.ChannelMapping
import me.welcomer.framework.pico.ruleset.patterns.ChannelMapping.ChannelDetails
import me.welcomer.framework.pico.service.PicoServicesComponent
import me.welcomer.framework.pico.EventedResult

class ChannelMappingExampleRuleset(picoServices: PicoServicesComponent#PicoServices)
  extends PicoRuleset(picoServices)
  with ChannelMapping {

  import context._
  import ChannelMappingExampleRuleset._

  val EVENT_DOMAIN = "ChannelMapping"

  subscribeToEventDomain(EVENT_DOMAIN)

  override def externalFunctionSharing = true

  override def provideFunction = {
    case EventedFunction(_, "isMapped", args, _) => handleIsMapped(args)
  }

  override def selectWhen = {
    case event @ EventedEvent(EVENT_DOMAIN, eventType, _, _, _) => {
      logEventInfo
      eventType match {
        case "mapChannel" => handleEvent[MapChannel](handleMapChannel)
      }
    }
  }

  protected def handleMapChannel(o: MapChannel, event: EventedEvent)(implicit ec: ExecutionContext): Unit = {
    val rulesets = Set("HelloWorldRuleset")

    retrieveMapping(o.channelDetails) map {
      case Some(mapping) => log.info("ECI found for channel, nothing to do: {} ({})", mapping.picoEci, event)
      case None => {
        mapNewPico(o.channelDetails, rulesets) onComplete {
          case Success((picoEci, replyToEci, storeResult)) => log.info("Pico created & mapped: {}->{} ({})", o.channelDetails, picoEci, storeResult);
          case Failure(e)                                  => log.error(e, "Error creating/mapping new pico: {} ({}, {})", e.getMessage(), o, event);
        }
      }
    }
  }

  protected def handleIsMapped(args: JsObject): Future[EventedResult[_]] = {
    Json.fromJson[ChannelDetails](args) match {
      case JsSuccess(channelDetails, path) =>
        retrieveMapping(channelDetails) map {
          case Some(mapping) => EventedSuccess(Json.toJson(mapping))
          case None          => EventedFailure(BasicError("No mapping found"))
        }
      case JsError(e) => Future(EventedFailure(BasicError(e.toString)))
    }
  }
}

object ChannelMappingExampleRuleset {
  case class MapChannel(transactionId: String, channelDetails: ChannelDetails) {
    def eventType = "mapChannel"
  }

  implicit lazy val mapChannelFormat: Format[MapChannel] = (
    (__ \ "transactionId").format[String] ~
    (__ \ "channel").format[ChannelDetails])(MapChannel.apply, unlift(MapChannel.unapply))
}
