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

import scala.concurrent.duration._

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

private[framework] object Settings {
  def apply() = {
    new Settings
  }

  def apply(config: Config) = {
    new Settings(Option(config))
  }

  def apply(configOption: Option[Config]) = {
    new Settings(configOption)
  }
}

private[framework] class Settings(configOption: Option[Config] = None) {

  val config: Config = configOption getOrElse { ConfigFactory.load() }

  config.checkValid(ConfigFactory.defaultReference())
  //  config.checkValid(ConfigFactory.defaultReference(), "simple-lib")

  object Database {
    object WelcomerFramework {
      val uri: String = config.getString("db.welcomerFramework.uri")
      //      val testUri = config.getString("db.test-uri")
      val name: String = config.getString("db.welcomerFramework.name")

      object Collections {
        val picoContainer: String = config.getString("db.welcomerFramework.collections.picoContainer") // TODO: Rename this to 'picos'?
        val picoPds: String = config.getString("db.welcomerFramework.collections.picoPds")
        val ecis: String = config.getString("db.welcomerFramework.collections.ecis")
      }
    }
  }

  //  object EventedGateway {
  //
  //  }

  object EventedEntityResolver {
    val timeout: FiniteDuration = FiniteDuration(
      config.getDuration("eventedEntityResolver.timeout", TimeUnit.MILLISECONDS),
      TimeUnit.MILLISECONDS)
    val retries: Int = config.getInt("eventedEntityResolver.retries")
    val eventTraceLogDepth: Int = config.getInt("eventedEntityResolver.eventTraceLogDepth")
  }

  object ExternalEventGateway {
    object Bind {
      val interface: String = config.getString("externalEventGateway.bind.interface")
      val port: Int = config.getInt("externalEventGateway.bind.port")
    }
    object EventedFunction {
      val defaultTimeout: FiniteDuration = FiniteDuration(
        config.getDuration("externalEventGateway.eventedFunction.defaultTimeout", TimeUnit.MILLISECONDS),
        TimeUnit.MILLISECONDS)
      val maxTimeout: FiniteDuration = FiniteDuration(
        config.getDuration("externalEventGateway.eventedFunction.maxTimeout", TimeUnit.MILLISECONDS),
        TimeUnit.MILLISECONDS)
    }
  }
}

