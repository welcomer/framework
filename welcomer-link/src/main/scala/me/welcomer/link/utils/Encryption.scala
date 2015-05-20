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
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import java.nio.ByteBuffer

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV

object Encryption {
  /**
   * Generate secure random bytes using [[java.security.SecureRandom]]
   *
   * @param size Number of bytes to generate
   */
  def getSecureRandomBytes(size: Int): ByteBuffer = {
    val iv = new Array[Byte](size)
    new java.security.SecureRandom().nextBytes(iv)

    ByteBuffer.wrap(iv)
  }

  def getCipher_AES_CBC(key: ByteBuffer, iv: ByteBuffer, forEncryption: Boolean): Try[PaddedBufferedBlockCipher] = {
    Try {
      val cipher: PaddedBufferedBlockCipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()))
      cipher.init(forEncryption, new ParametersWithIV(new KeyParameter(key.array), iv.array))
      cipher
    }
  }

  case class AESCBCEncrypted(ciphertext: ByteBuffer, iv: ByteBuffer)

  /**
   * Encrypt the plaintext using AES.
   *
   * By  for the initialisation vector.
   *
   * Note: Key size of 32 bytes/256 bits == AES256
   *
   * @param plaintext The plaintext to be encrypted
   * @param key The key to use for encryption
   * @param iv The initialisation vector to use for encrypting. Must be 16 bytes. (Default: uses [[getSecureRandomBytes]])
   *
   * @return Future containing the encrypted ciphertext and initialisation vector used
   */
  def encrypt_AES_CBC(
    plaintext: ByteBuffer,
    key: ByteBuffer,
    iv: ByteBuffer = getSecureRandomBytes(16))(implicit ec: ExecutionContext): Future[AESCBCEncrypted] = {
    //      assert key.length == 32; // 32 bytes == 256 bits
    getCipher_AES_CBC(key, iv, true) match {
      case Success(cipher) => applyCipher(plaintext, cipher) map { AESCBCEncrypted(_, iv) }
      case Failure(e)      => Future.failed(e)
    }
  }

  /**
   * Decrypt the ciphertext using AES.
   *
   * Note: Key size of 32 bytes/256 bits == AES256
   *
   * @param ciphertext The ciphertext to be decrypted
   * @param key The key to use for decryption
   * @param iv The initialisation vector used when encrypting
   *
   * @return Future containing the encrypted ciphertext and initialisation vector used
   */
  def decrypt_AES_CBC(
    ciphertext: ByteBuffer,
    key: ByteBuffer,
    iv: ByteBuffer)(implicit ec: ExecutionContext): Future[ByteBuffer] = {
    getCipher_AES_CBC(key, iv, false) match {
      case Success(cipher) => applyCipher(ciphertext, cipher)
      case Failure(e)      => Future.failed(e)
    }
  }

  /**
   * Helper that does the body of the cipher/uncipher process, depending on how the passed in cipher is configured.
   *
   * Note: If you want something that "Just Works" then look at something like [[encrypt_AES_CBC]]/[[decrypt_AES_CBC]]
   *
   * @param bb ByteBuffer containing your plaintext/ciphertext
   * @param cipher A preconfigured and init'd PaddedBufferedBlockCipher
   *
   * @return Future containing the ciphered/unciphered bytes of bb
   */
  def applyCipher(bb: ByteBuffer, cipher: PaddedBufferedBlockCipher)(implicit ec: ExecutionContext): Future[ByteBuffer] = {
    Future {
      val inputBytes: Array[Byte] = bb.array
      val outputBytes = new Array[Byte](cipher.getOutputSize(inputBytes.length))

      val outputLen1 = cipher.processBytes(inputBytes, 0, inputBytes.length, outputBytes, 0)
      val outputLen2 = cipher.doFinal(outputBytes, outputLen1)
      val actualOutputLen = outputLen1 + outputLen2

      val finalBytes = new Array[Byte](actualOutputLen)
      Array.copy(outputBytes, 0, finalBytes, 0, finalBytes.length)

      ByteBuffer.wrap(finalBytes)
    }
  }
}
