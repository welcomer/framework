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
package me.welcomer.framework.picocontainer.service

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success
import akka.actor.ActorContext
import akka.actor.actorRef2Scala
import me.welcomer.framework.models.PicoSchema
import me.welcomer.framework.pico.service.PicoServicesComponent
import me.welcomer.framework.picocontainer.PicoContainer.StartPico
import me.welcomer.framework.picocontainer.repository.PicoContainerRepositoryComponent
import me.welcomer.framework.utils.DBUtils
import me.welcomer.framework.utils.LoggingUtils
import play.api.libs.iteratee.Iteratee
import reactivemongo.bson.BSONDocument
import akka.actor.ActorRef
import me.welcomer.framework.pico.PicoOverlord
import scala.concurrent.Future
import me.welcomer.framework.models.Pico
import scala.util.Try
import akka.actor.PoisonPill
import akka.actor.ActorSystem

/**
 * PicoContainer Service Component
 */
trait PicoContainerServiceComponent {
  self: PicoContainerRepositoryComponent with PicoManagementServiceComponent with PicoServicesComponent =>

  protected def _picoContainerService: PicoContainerService

  /**
   * PicoContainer Service
   */
  trait PicoContainerService {
    /**
     * Retrieve a single pico matching picoUUID
     *
     * @param picoUUID the picoUUID of the pico to retrieve
     */
    def retrievePico(picoUUID: String)(implicit ec: ExecutionContext, context: ActorContext): Future[Option[Pico]]

    /**
     * Load a single pico matching picoUUID and queue it to start.
     *
     * @param picoUUID the picoUUID of the pico to load.
     */
    def loadPico(picoUUID: String)(implicit ec: ExecutionContext, context: ActorContext): Unit

    /**
     * Load picos matching query and queue them to start.
     *
     * @param query the query to select picos to load.
     */
    def loadPicos(query: BSONDocument)(implicit ec: ExecutionContext, context: ActorContext): Unit

    /**
     * Load all picos and queue them to start.
     */
    def loadAllPicos()(implicit ec: ExecutionContext, context: ActorContext): Unit

    /**
     * Retrieve then Start pico using supplied details
     *
     * @param picoUUID the picoUUID of the pico to load.
     */
    def startPico(picoUUID: String)(implicit ec: ExecutionContext, context: ActorContext): Future[Try[ActorRef]]

    /**
     * Start pico using supplied details
     *
     * @param picoUUID the picoUUID of the pico to load.
     * @param rulesets the rulesets to start the pico with
     */
    def startPico(picoUUID: String, rulesets: Set[String])(implicit context: ActorContext): Try[ActorRef]

    /**
     * Stop pico
     *
     * @param picoUUID the picoUUID of the pico to stop
     */
    def stopPico(picoUUID: String)(implicit context: ActorContext): Unit

    /**
     * Provide access to the picoManagementService.
     */
    def picoManagementService: PicoManagementServiceComponent#PicoManagementService

    /**
     * Create a new picoServices instance scoped to the supplied picoUUID
     *
     * @param picoUUID The picoUUID to scope the services to
     * @return The scoped picoServices instance
     */
    def picoServicesCreator(picoUUID: String)(implicit system: ActorSystem): PicoServicesComponent#PicoServices
  }
}

/**
 * Implementation of [[me.welcomer.framework.picocontainer.service.PicoContainerServiceComponent]]
 */
trait PicoContainerServiceComponentImpl
  extends PicoContainerServiceComponent {
  self: PicoContainerRepositoryComponent with PicoManagementServiceComponent with PicoServicesComponent =>
  override protected def _picoContainerService: PicoContainerService = new PicoContainerServiceImpl

  private def getLogger(implicit context: ActorContext) = LoggingUtils.getLoggerWithContext

  /**
   * Implementation of [[me.welcomer.framework.picocontainer.service.PicoContainerServiceComponent#PicoContainerService]]
   */
  private[this] class PicoContainerServiceImpl extends PicoContainerService with DBUtils {
    // TODO: LoadPicoWithEci ??

    override def retrievePico(picoUUID: String)(implicit ec: ExecutionContext, context: ActorContext): Future[Option[Pico]] = {
      _picoContainerRepository.findOne(PicoSchema.picoUUID, picoUUID)
    }

    override def loadPico(picoUUID: String)(implicit ec: ExecutionContext, context: ActorContext): Unit = { //self: PicoContainer with ActorRef =>
      lazy val log = getLogger

      log.info("Loading pico {}", picoUUID)

      retrievePico(picoUUID) map {
        case Some(pico) => {
          log.info(s"Loaded pico {}", pico.picoUUID)

          // TODO: Is it sufficient to assume this will only ever be called from the PicoContainer?
          context.self ! StartPico(pico.picoUUID, pico.picoUUID, pico.rulesets)
        }
        case None => log.error("Error while loading pico ({}). Unknown PicoUUID.", picoUUID)
      }
    }

    override def loadPicos(query: BSONDocument)(implicit ec: ExecutionContext, context: ActorContext): Unit = { //self: PicoContainer with ActorRef =>
      lazy val log = getLogger

      log.info("Loading picos..")

      _picoContainerRepository.enumerate[Unit](
        query,
        Iteratee.foreach { pico =>
          log.info(s"Loaded pico {}", pico.picoUUID)

          // TODO: Is it sufficient to assume this will only ever be called from the PicoContainer?
          context.self ! StartPico(pico.picoUUID, pico.picoUUID, pico.rulesets)
        }).onComplete(x => x match {
          case Success(_) => log.info("Loading picos complete.")
          case Failure(e) => log.error(e, "Error while loading picos.")
        })
    }

    override def loadAllPicos()(implicit ec: ExecutionContext, context: ActorContext): Unit = { //self: PicoContainer with ActorRef =>
      loadPicos(dbQueryAll)
    }

    override def startPico(picoUUID: String)(implicit ec: ExecutionContext, context: ActorContext): Future[Try[ActorRef]] = {
      lazy val log = getLogger

      retrievePico(picoUUID) map {
        case Some(pico) => startPico(pico.picoUUID, pico.rulesets)
        case None => {
          val e = new Throwable(s"picoUUID=$picoUUID not found")

          log.error(e, "Error starting pico: " + e.getMessage())

          Failure(e)
        }
      }
    }

    override def startPico(picoUUID: String, rulesets: Set[String])(implicit context: ActorContext): Try[ActorRef] = {
      lazy val log = getLogger

      val picoRefTry = context.child(picoUUID) match {
        case Some(picoRef) => Success(picoRef)
        case None => {
          lazy val picoServices = picoServicesCreator(picoUUID)(context.system)

          Try(context.actorOf(
            PicoOverlord.props(picoUUID, rulesets, picoServices),
            picoUUID))
        }
      }

      picoRefTry match {
        case Success(picoRef) => {
          log.info("Pico started/running: {}=>{}", picoUUID, rulesets)
          log.debug("Pico started/running: {} ({}=>{})", picoRef, picoUUID, rulesets)
        }
        case Failure(e) => log.error(e, "Error starting pico: {} ({}=>{})", e.getMessage(), picoUUID, rulesets)
      }

      picoRefTry
    }

    override def stopPico(picoUUID: String)(implicit context: ActorContext): Unit = {
      context.child(picoUUID) map { picoRef =>
        context.unwatch(picoRef)
        picoRef ! PoisonPill
      }
    }

    override def picoManagementService: PicoManagementServiceComponent#PicoManagementService = {
      _picoManagementService
    }

    override def picoServicesCreator(picoUUID: String)(implicit system: ActorSystem): PicoServicesComponent#PicoServices = {
      _picoServices(picoUUID)
    }

  }
}