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

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import akka.actor.ActorSystem
import me.welcomer.framework.Settings
import me.welcomer.framework.pico.repository.PicoPdsRepositoryComponent
import me.welcomer.framework.pico.repository.PicoPdsRepositoryComponentReactiveMongoImpl
import me.welcomer.framework.testUtils.WelcomerFrameworkSingleActorSystemTest
import me.welcomer.framework.utils.DBUtils
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.MongoConnection
import reactivemongo.api.MongoDriver
import reactivemongo.api.DB

trait ComponentRegistryMock
  extends AnyRef
  with PicoPdsRepositoryComponent
  with PicoPdsServiceComponent

trait ComponentRegistryImplMock
  extends AnyRef
  with PicoPdsRepositoryComponentReactiveMongoImpl
  with PicoPdsServiceComponentImpl

class DefaultComponentRegistryImplMock(implicit system: ActorSystem, ec: ExecutionContext) extends ComponentRegistryImplMock with DBUtils {
  val settings: Settings = Settings()

  private val (dbDriverPrivate, dbConnectionTry) = dbConnect(system, settings.Database.WelcomerFramework.uri)
  val dbConnection: MongoConnection = dbConnectionTry match {
    case Success(connection) => connection
    case Failure(e) => throw new RuntimeException("Couldn't parse database uri", e)
  }
  def dbDriver: MongoDriver = dbDriverPrivate // Only expose dbDriver, not dbConnectionTry

  lazy val database: DB = dbConnection.db(settings.Database.WelcomerFramework.name)

  override lazy val _picoPdsCollection = database.collection[JSONCollection](settings.Database.WelcomerFramework.Collections.picoPds)

  def picoPdsService(picoUUIDScope: String): PicoPdsService = _picoPdsService(picoUUIDScope)
}

class PicoPdsServiceSpec extends WelcomerFrameworkSingleActorSystemTest {
  val componentRegistry = new DefaultComponentRegistryImplMock
  val pdsService = componentRegistry.picoPdsService("picoUUID")

  //  def withServices(testCode: (DefaultComponentRegistryImplMock, PicoPdsServiceComponent#PicoPdsService) => Any) = {
  //    testCode(componentRegistry, pdsService)
  //  }

  "PdsService::retrieve" should {
    "be able to retrieve a deepPathed query/projection" in {
      //      withServices { (componentRegistry, pdsService) =>
      val query = Json.obj(
        "mappings.channels" -> Json.obj(
          "$elemMatch" -> Json.obj(
            "type" -> "email",
            "id" -> "a@b.com")))
      val projection = Json.obj("mappings.channels.$" -> 1)

      val resultFuture = pdsService.retrieve(query, projection, Some("user"))

      resultFuture map {
        case Some(result) => println("Retrieved deepPath:" + Json.prettyPrint(result))
        case None => println("None while retrieving")
      }

      assert(resultFuture.futureValue.isDefined)
      //      }
    }
  }

  "PdsService::pushArrayItem" should {
    "correctly push item" in {
      //      withServices { (componentRegistry, pdsService) =>
      val key = "mappings"
      val item = Json.obj(
        "userEci" -> "the-new-user-eci",
        "vendorEci" -> "the-new-vendor-eci",
        "channels" -> Json.arr(
          Json.obj("type" -> "email", "id" -> "a@b.com"),
          Json.obj("type" -> "mobile", "id" -> "0400654321")))
      val namespace = Some("user")

      val resultFuture = pdsService.pushArrayItem(key, item, namespace, unique = true)

      resultFuture onComplete {
        case Success(result) => println("Successfully pushed: " + Json.prettyPrint(item))
        case Failure(e) => println("Error pushing: " + e.getMessage())
      }

      assert(resultFuture.futureValue != null)
      //      }
    }

    "correctly push item with selector" in {
      //      withServices { (componentRegistry, pdsService) =>
      val selector = Some(
        Json.obj(
          "mappings" -> Json.obj(
            "$elemMatch" -> Json.obj(
              "userEci" -> "the-new-user-eci",
              "vendorEci" -> "the-new-vendor-eci"))))

      val arrayKey = "mappings.$.channels"
      val item = Json.obj("type" -> "foo", "id" -> "bar")
      val namespace = Some("user")

      val resultFuture = pdsService.pushArrayItem(arrayKey, item, namespace, selector, unique = true)

      resultFuture onComplete {
        case Success(result) => println("Successfully pushed: " + Json.prettyPrint(item))
        case Failure(e) => println("Error pushing: " + e.getMessage())
      }

      assert(resultFuture.futureValue != null)
      //      }
    }
  }

}
