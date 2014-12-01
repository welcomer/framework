package me.welcomer.framework.utils

import akka.actor.ActorContext
import akka.actor.ActorSystem
import akka.event.Logging
import akka.event.LoggingAdapter

object LoggingUtils {

  def getLoggerWithSystem(implicit system: ActorSystem): LoggingAdapter = {
    Logging.getLogger(system, this)
  }

  def getLoggerWithContext(implicit context: ActorContext): LoggingAdapter = {
    Logging.getLogger(context.system, this)
  }

}