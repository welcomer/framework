package me.welcomer.framework.pico.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import me.welcomer.framework.models.ECI
import me.welcomer.framework.models.Pico
import me.welcomer.framework.picocontainer.service.PicoManagementServiceComponent

trait PicoSafeManagementServiceComponentMockImpl extends PicoSafeManagementServiceComponent {
  self: PicoManagementServiceComponent =>
  override protected def _picoSafeManagementService: PicoSafeManagementService = new PicoSafeManagementServiceMockImpl()

  class PicoSafeManagementServiceMockImpl() extends PicoSafeManagementService {
    override def createNewPico(rulesets: Set[String] = Set())(implicit ec: ExecutionContext): Future[String] = {
      Future("the-new-eci")
    }
  }
}