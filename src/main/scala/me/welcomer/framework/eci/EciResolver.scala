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
package me.welcomer.framework.eci

import scala.collection.mutable.HashMap
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import me.welcomer.framework.Settings
import me.welcomer.framework.eci.service.EciResolverServiceComponent
import scala.concurrent.Future

private[framework] object EciResolver {
  sealed abstract class EciResolverMsg {
    val eci: String
  }

  case class ResolveECIToPicoUUID(eci: String) extends EciResolverMsg
  case class ECIResolvedToPicoUUID(eci: String, picoUUID: String) extends EciResolverMsg
  case class InvalidECI(eci: String) extends EciResolverMsg

  /**
   * Create Props for an actor of this type.
   * @param settings The Settings to be passed to this actor's constructor.
   * @param eciResolverService The EciResolverService instance to be passed to this actor's constructor.
   * @return a Props for creating this actor, which can then be further configured
   *         (e.g. calling `.withDispatcher()` on it)
   */
  def props(settings: Settings, eciResolverService: EciResolverServiceComponent#EciResolverService): Props =
    Props(classOf[EciResolver], settings, eciResolverService)
}

private[framework] class EciResolver(
  settings: Settings,
  eciResolverService: EciResolverServiceComponent#EciResolverService)
  extends Actor with ActorLogging {
  import context._
  import EciResolver._

  @transient protected var eciCache = HashMap[String, String]() // TODO: Make this a proper cache with size limit and expiry
  // TODO: Add a buffer for EventedEvents awaiting ECI->PicoUUID resolution?

  //  protected lazy val eciResolverBus = new EciResolverBusImpl(1024)

  override def preStart(): Unit = {
    log.info("EciResolver Initialising..")

    log.info("EciResolver Initialisation complete.")
  }

  def receive = {
    case ResolveECIToPicoUUID(eci) => {
      log.debug(s"[ResolveECIToPicoUUID::{}] {}", sender.path.toString(), eci)

      // Remember who we need to respond to
      //      eciResolverBus.subscribe(sender, eci)
      val requestor = sender

      // Lookup eci mapping in cache or DB
      val resolvedEci: Future[Option[String]] = eciCache.get(eci) match {
        case Some(picoUUID) => {
          log.debug("[ResolveECIToPicoUUID::Cached] {}->{}", eci, picoUUID)
          Future(Option(picoUUID)) // Cached, so use it
        }
        case None => eciResolverService.resolveEciToPicoUUID(eci)
      }

      // When future completes, send result if found, otherwise send error
      resolvedEci map { optionEci =>
        val resolveEvent: EciResolverMsg = optionEci match {
          case Some(picoUUID) => ECIResolvedToPicoUUID(eci, picoUUID)
          case None           => InvalidECI(eci)
        }

        //        eciResolverBus.publish(resolveEvent)
        self ! resolveEvent // Notify ourselves so we can cache/etc
        requestor ! resolveEvent
      }
    }
    case ECIResolvedToPicoUUID(eci, picoUUID) => {
      if (sender == self) {
        log.info(s"[ECIResolvedToPicoUUID] {}->{}", eci, picoUUID)
        eciCache += (eci -> picoUUID)
      }
    }
    case InvalidECI(eci) => {
      if (sender == self) {
        log.debug("[InvalidECI] {}", eci)
        eciCache -= eci
      }
    }
  }

  override def postStop(): Unit = {
    log.info("EciResolver Shutting down..")
  }

}
