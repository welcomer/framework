package me.welcomer.framework.pico.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import me.welcomer.framework.eci.service.EciServiceComponent
import me.welcomer.framework.models.ECI

trait PicoEciServiceComponentMockImpl extends PicoEciServiceComponent { self: EciServiceComponent =>
  override protected def _picoEciService(picoUUID: String): PicoEciService = new PicoEciServiceMockImpl()(picoUUID)

  private[this] class PicoEciServiceMockImpl(implicit val picoUUID: String) extends PicoEciService {
    override def generate(description: Option[String] = None)(implicit ec: ExecutionContext): Future[ECI] = {
      Future(ECI("the-generated-eci", "the-picoUUID", "description"))
    }
  }
}