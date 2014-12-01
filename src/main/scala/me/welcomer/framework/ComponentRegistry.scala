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

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success
import akka.actor.ActorSystem
import me.welcomer.framework.eci.repository.EciRepositoryComponent
import me.welcomer.framework.eci.repository.EciRepositoryComponentReactiveMongoImpl
import me.welcomer.framework.eci.service.EciResolverServiceComponent
import me.welcomer.framework.eci.service.EciResolverServiceComponentImpl
import me.welcomer.framework.eci.service.EciServiceComponent
import me.welcomer.framework.eci.service.EciServiceComponentImpl
import me.welcomer.framework.pico.repository.PicoPdsRepositoryComponent
import me.welcomer.framework.pico.repository.PicoPdsRepositoryComponentReactiveMongoImpl
import me.welcomer.framework.pico.service.PicoEciServiceComponent
import me.welcomer.framework.pico.service.PicoEciServiceComponentImpl
import me.welcomer.framework.pico.service.PicoPdsServiceComponent
import me.welcomer.framework.pico.service.PicoPdsServiceComponentImpl
import me.welcomer.framework.pico.service.PicoSafeManagementServiceComponent
import me.welcomer.framework.pico.service.PicoSafeManagementServiceComponentImpl
import me.welcomer.framework.pico.service.PicoServicesComponent
import me.welcomer.framework.pico.service.PicoServicesComponentImpl
import me.welcomer.framework.picocontainer.repository.PicoContainerRepositoryComponent
import me.welcomer.framework.picocontainer.repository.PicoContainerRepositoryComponentReactiveMongoImpl
import me.welcomer.framework.picocontainer.service.PicoContainerServiceComponent
import me.welcomer.framework.picocontainer.service.PicoContainerServiceComponentImpl
import me.welcomer.framework.picocontainer.service.PicoManagementServiceComponent
import me.welcomer.framework.picocontainer.service.PicoManagementServiceComponentImpl
import me.welcomer.framework.utils.DBUtils
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.MongoConnection
import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.api.DB
import me.welcomer.framework.pico.service.PicoLinkServiceComponent
import me.welcomer.framework.pico.service.PicoLinkServiceComponentImpl
import me.welcomer.link.mandrill.MandrillRepositoryComponent
import me.welcomer.link.mandrill.MandrillRepositoryComponentImpl

private[framework] trait ComponentRegistry
  extends AnyRef
  with EciRepositoryComponent
  with EciServiceComponent
  with EciResolverServiceComponent
  with PicoContainerRepositoryComponent
  with PicoContainerServiceComponent
  with PicoManagementServiceComponent
  with PicoPdsRepositoryComponent
  with PicoServicesComponent
  with PicoPdsServiceComponent
  with PicoEciServiceComponent
  with PicoSafeManagementServiceComponent
  with PicoLinkServiceComponent
  with MandrillRepositoryComponent {
}

private[framework] trait ComponentRegistryImpl
  extends AnyRef
  with EciRepositoryComponentReactiveMongoImpl
  with EciServiceComponentImpl
  with EciResolverServiceComponentImpl
  with PicoContainerRepositoryComponentReactiveMongoImpl
  with PicoContainerServiceComponentImpl
  with PicoManagementServiceComponentImpl
  with PicoPdsRepositoryComponentReactiveMongoImpl
  with PicoServicesComponentImpl
  with PicoPdsServiceComponentImpl
  with PicoEciServiceComponentImpl
  with PicoSafeManagementServiceComponentImpl
  with PicoLinkServiceComponentImpl
  with MandrillRepositoryComponentImpl

private[framework] class DefaultComponentRegistryImpl(private val system: ActorSystem, val settings: Settings = Settings())(implicit ec: ExecutionContext)
  extends ComponentRegistryImpl with DBUtils {

  // Connect to DB server
  private val (dbDriverPrivate, dbConnectionTry) = dbConnect(system, settings.Database.WelcomerFramework.uri)
  val dbConnection: MongoConnection = dbConnectionTry match {
    case Success(connection) => connection
    case Failure(e) => throw new RuntimeException("Couldn't parse database uri", e)
  }
  def dbDriver: MongoDriver = dbDriverPrivate // Only expose dbDriver, not dbConnectionTry

  // Open DB
  lazy val database: DB = dbConnection.db(settings.Database.WelcomerFramework.name)

  // Open Collections
  override protected lazy val _eciCollection = database.collection[BSONCollection](settings.Database.WelcomerFramework.Collections.ecis)
  override protected lazy val _picoContainerCollection = database.collection[BSONCollection](settings.Database.WelcomerFramework.Collections.picoContainer)
  override protected lazy val _picoPdsCollection = database.collection[JSONCollection](settings.Database.WelcomerFramework.Collections.picoPds)

  // Expose services
  lazy val eciService: EciService = _eciService
  lazy val eciResolverService: EciResolverService = _eciResolverService
  lazy val picoContainerService: PicoContainerService = _picoContainerService

  // These are now provided via the picoContainer service, so probably not needed here
  //  def picoServices(picoUUIDScope: String): PicoServices = _picoServices(picoUUIDScope)
  //  def picoPdsService(picoUUIDScope: String): PicoPdsService = _picoPdsService(picoUUIDScope)
  //  def picoEciService(picoUUIDScope: String): PicoEciService = _picoEciService(picoUUIDScope)
}