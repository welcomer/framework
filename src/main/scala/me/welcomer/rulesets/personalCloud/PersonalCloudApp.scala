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
package me.welcomer.rulesets.personalCloud

import play.api.libs.json._

import me.welcomer.framework.pico.EventedEvent
import me.welcomer.framework.pico.EventedFunction
import me.welcomer.framework.pico.PicoRuleset
import me.welcomer.framework.pico.ruleset.patterns.ChannelMapping
import me.welcomer.framework.pico.ruleset.patterns.ChannelMapping.ChannelDetails
import me.welcomer.framework.pico.service.PicoServicesComponent

class PersonalCloudApp(picoServices: PicoServicesComponent#PicoServices)
  extends PicoRuleset(picoServices)
  with ChannelMapping {

  import context._

  val EVENT_DOMAIN = "personalCloud"
  subscribeToEventDomain(EVENT_DOMAIN)

  override def externalFunctionSharing = true

  def shouldForwardFunc(funcName: String) = Set("retrieveAll", "retrieveItem").contains(funcName)
  def forwardToPds(func: EventedFunction) = forwardEventedForChannel(func.withModuleId("me.welcomer.rulesets.personalCloud.PersonalCloudPds"))

  override def provideFunction = {
    case func @ EventedFunction(_, funcName, _, _) if shouldForwardFunc(funcName) => forwardToPds(func)
  }

  def shouldForwardEvent(eventType: String) = Set("storeAll", "storeItem", "removeAll", "removeItem").contains(eventType)

  override def selectWhen = {
    case event @ EventedEvent(EVENT_DOMAIN, eventType, _, attr, _) => {
      logEventInfo
      eventType match {
        case "init"                             => handleInit(attr)
        case _ if shouldForwardEvent(eventType) => forwardEventedForChannel(event)
        case _                                  => log.debug("Unhandled {} EventedEvent Received ({})", EVENT_DOMAIN, event)
      }
    }
  }

  def handleInit(attr: JsObject): Unit = {
    Json.fromJson[ChannelDetails](attr \ "channelDetails") map { channelDetails =>
      mapNewPico(channelDetails, Set("personalCloud.PersonalCloudPds"))
    } getOrElse { log.error("No ChannelDetails found on event.") }
  }
}
