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
package me.welcomer.framework.pico.service

import akka.actor.ActorSystem

trait PicoServicesComponent {
  self: PicoEciServiceComponent with PicoPdsServiceComponent with PicoSafeManagementServiceComponent with PicoLinkServiceComponent =>
  protected def _picoServices(picoUUID: String)(implicit system: ActorSystem): PicoServices

  trait PicoServices {
    implicit val system: ActorSystem
    implicit val picoUUID: String

    def eci: PicoEciServiceComponent#PicoEciService
    def pds: PicoPdsServiceComponent#PicoPdsService
    def picoManagement: PicoSafeManagementServiceComponent#PicoSafeManagementService

    def link: PicoLinkServiceComponent#PicoLinkService
  }
}

trait PicoServicesComponentImpl extends PicoServicesComponent {
  self: PicoEciServiceComponent with PicoPdsServiceComponent with PicoSafeManagementServiceComponent with PicoLinkServiceComponent =>
  override protected def _picoServices(picoUUID: String)(implicit system: ActorSystem): PicoServices = new PicoServicesImpl()(system, picoUUID)

  private[this] class PicoServicesImpl(implicit val system: ActorSystem, val picoUUID: String) extends PicoServices {
    lazy val eci: PicoEciServiceComponent#PicoEciService = _picoEciService(picoUUID)
    lazy val pds: PicoPdsServiceComponent#PicoPdsService = _picoPdsService(picoUUID)
    lazy val picoManagement: PicoSafeManagementServiceComponent#PicoSafeManagementService = _picoSafeManagementService

    lazy val link: PicoLinkServiceComponent#PicoLinkService = _picoLinkService(picoUUID)
  }
}