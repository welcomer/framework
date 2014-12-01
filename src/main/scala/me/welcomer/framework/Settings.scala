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

import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

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

private[framework] class Settings(val configOption: Option[Config] = scala.None) {

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

  object ExternalEventGateway {
    object Bind {
      val interface: String = config.getString("externalEventGateway.bind.interface")
      val port: Int = config.getInt("externalEventGateway.bind.port")
    }
  }
}

