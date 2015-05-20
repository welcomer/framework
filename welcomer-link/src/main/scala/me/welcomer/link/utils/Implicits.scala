/*  Copyright 2015 White Label Personal Clouds Pty Ltd
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
package me.welcomer.link.utils

import scala.concurrent.ExecutionContext

import java.nio.ByteBuffer
import java.nio.charset.Charset

import org.apache.commons.codec.binary.Base64

object Implicits {
  import scala.language.implicitConversions

  implicit def jFuture2sFuture[T](jFuture: java.util.concurrent.Future[T])(implicit ec: ExecutionContext): scala.concurrent.Future[T] = scala.concurrent.Future[T](jFuture.get)

  implicit def string2UTF8ByteBuffer(s: String): ByteBuffer = new BetterString(s).asUTF8ByteBuffer
  implicit def utf8ByteBuffer2String(bb: ByteBuffer): String = new BetterByteBuffer(bb).asUTF8String

  lazy val UTF8 = Charset.forName("UTF-8")

  object BetterByteBuffer {
    def fromBase64UTF8String(s: String): ByteBuffer = new BetterString(s).decodeBase64AsUTF8ByteBuffer
  }

  implicit class BetterByteBuffer(bb: ByteBuffer) {
    def asUTF8String: String = new String(bb.array(), UTF8);

    def asBase64: Array[Byte] = Base64.encodeBase64(bb.array())
    def asBase64UTF8String: String = new String(asBase64, UTF8)
  }

  implicit class BetterString(s: String) {
    def asUTF8ByteBuffer: ByteBuffer = ByteBuffer.wrap(s.getBytes(UTF8))

    def decodeBase64AsUTF8ByteBuffer: ByteBuffer = ByteBuffer.wrap(Base64.decodeBase64(s.getBytes(UTF8)))
  }
}
