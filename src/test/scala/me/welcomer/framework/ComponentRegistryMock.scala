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
package me.welcomer.framework

import me.welcomer.framework.eci.repository.EciRepositoryComponent
import me.welcomer.framework.eci.repository.EciRepositoryComponentMockImpl
import me.welcomer.framework.eci.service.EciServiceComponent
import me.welcomer.framework.eci.service.EciServiceComponentImpl
import me.welcomer.framework.pico.repository.PicoPdsRepositoryComponent
import me.welcomer.framework.pico.repository.PicoPdsRepositoryComponentMockImpl
import me.welcomer.framework.pico.service.PicoEciServiceComponent
import me.welcomer.framework.pico.service.PicoEciServiceComponentImpl
import me.welcomer.framework.pico.service.PicoPdsServiceComponent
import me.welcomer.framework.pico.service.PicoPdsServiceComponentImpl
import me.welcomer.framework.pico.service.PicoSafeManagementServiceComponent
import me.welcomer.framework.pico.service.PicoSafeManagementServiceComponentMockImpl
import me.welcomer.framework.pico.service.PicoServicesComponent
import me.welcomer.framework.pico.service.PicoServicesComponentImpl
import me.welcomer.framework.picocontainer.repository.PicoContainerRepositoryComponent
import me.welcomer.framework.picocontainer.repository.PicoContainerRepositoryComponentMockImpl
import me.welcomer.framework.picocontainer.service.PicoManagementServiceComponent
import me.welcomer.framework.picocontainer.service.PicoManagementServiceComponentImpl
import me.welcomer.framework.pico.service.PicoEciServiceComponentMockImpl
import me.welcomer.framework.pico.service.PicoPdsServiceComponentMockImpl
import me.welcomer.framework.pico.service.PicoLinkServiceComponentImpl
import me.welcomer.framework.pico.service.PicoLinkServiceComponent
import me.welcomer.link.mandrill.MandrillRepositoryComponent
import akka.actor.ActorSystem
import me.welcomer.link.mandrill.MandrillRepositoryComponentImpl

//trait ComponentRegistryMock {
//
//}

trait PicoServicesComponentRegistryMock extends AnyRef
  with PicoServicesComponent
  with PicoEciServiceComponent
  with EciServiceComponent
  with EciRepositoryComponent
  with PicoPdsServiceComponent
  with PicoPdsRepositoryComponent
  with PicoSafeManagementServiceComponent
  with PicoManagementServiceComponent
  with PicoContainerRepositoryComponent
  with PicoLinkServiceComponent
  with MandrillRepositoryComponent {

  implicit def system: ActorSystem

  def picoServices(picoUUID: String): PicoServices = _picoServices(picoUUID)
}

trait PicoServicesComponentRegistryMockImpl extends PicoServicesComponentRegistryMock
  with PicoServicesComponentImpl
  with PicoEciServiceComponentMockImpl
  with EciServiceComponentImpl
  with EciRepositoryComponentMockImpl
  with PicoPdsServiceComponentMockImpl
  with PicoPdsRepositoryComponentMockImpl
  with PicoSafeManagementServiceComponentMockImpl
  with PicoManagementServiceComponentImpl
  with PicoContainerRepositoryComponentMockImpl
  with PicoLinkServiceComponentImpl
  with MandrillRepositoryComponentImpl {

}