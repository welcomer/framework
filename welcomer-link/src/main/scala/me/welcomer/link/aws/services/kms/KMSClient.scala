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
package me.welcomer.link.aws.services.kms

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.kms.AWSKMSAsyncClient
import com.amazonaws.services.kms.model.DataKeySpec
import com.amazonaws.services.kms.model.DecryptRequest
import com.amazonaws.services.kms.model.DecryptResult
import com.amazonaws.services.kms.model.EncryptRequest
import com.amazonaws.services.kms.model.EncryptResult
import com.amazonaws.services.kms.model.GenerateDataKeyRequest
import com.amazonaws.services.kms.model.GenerateDataKeyResult
import com.amazonaws.services.kms.model.GenerateRandomRequest
import com.amazonaws.services.kms.model.GenerateRandomResult

import me.welcomer.link.aws.AWSUtil

object KMSClient {
  def apply(creds: AWSCredentials): KMSClient = apply(new AWSKMSAsyncClient(creds))
  def apply(creds: AWSCredentialsProvider): KMSClient = apply(new AWSKMSAsyncClient(creds))

  def apply(accessKey: String, secretKey: String): KMSClient = apply(new BasicAWSCredentials(accessKey, secretKey))

  object Helpers {
    import scala.collection.JavaConversions._
    import me.welcomer.link.utils.Implicits._
    import me.welcomer.link.utils.Encryption._

    /**
     * Container for AES256 encrypted text
     * @param ciphertext Base64 UTF8 Encoded ciphertext bytes
     * @param dataKeyCiphertext Base64 UTF8 Encoded dataKey ciphertext bytes
     * @param iv Base64 UTF8 encoded initialisation vector bytes
     */
    case class AES256EncryptedWithDataKey(ciphertext: String, dataKeyCiphertext: String, iv: String)

    /**
     * Helper to encrypt plaintext using AWS KMS dataKey.
     *
     * Requests a dataKey using the supplied master keyId, then encrypts plaintext locally using AES256 CBC.
     *
     * @param plaintext The plaintext to be encrypted
     * @param keyId The AWS KMS master keyId to use
     * @param context The encryption context to use
     *
     * @return The ciphertext, encrypted dataKey ciphertext and initialisation vector used
     */
    def encryptWithDataKey_AES256(
      plaintext: String,
      keyId: String,
      context: Map[String, String] = Map())(implicit ec: ExecutionContext, kms: KMSClient): Future[AES256EncryptedWithDataKey] = {

      val dataKeyRequest = new GenerateDataKeyRequest()
        .withKeyId(keyId)
        .withEncryptionContext(context)
        .withKeySpec(DataKeySpec.AES_256)

      for {
        dataKey <- kms.generateDataKey(dataKeyRequest)
        encrypted <- encrypt_AES_CBC(plaintext.asUTF8ByteBuffer, dataKey.getPlaintext)
      } yield {
        AES256EncryptedWithDataKey(
          encrypted.ciphertext.asBase64UTF8String,
          dataKey.getCiphertextBlob.asBase64UTF8String,
          encrypted.iv.asBase64UTF8String)
      }
    }

    /**
     *  Helper to decrypt ciphertext encrypted using an AWS KMS dataKey
     *
     *  Decrypts the dataKeyCiphertext using the supplied master keyId, then locally decrypts ciphertext using AES256 CBC.
     *
     *  @param ciphertextBase64UTF8 Base64 UTF8 encoded ciphertext
     *  @param dataKeyCiphertextBase64UTF8 Base64 UTF8 encoded dataKey ciphertext
     *  @param ivBase64UTF8 Base64 UTF8 encoded initialisation vector
     *  @param context The encryption context to use
     *
     *  @return The decrypted plaintext.
     */
    def decryptWithDataKey_AES256(
      ciphertextBase64UTF8: String,
      dataKeyCiphertextBase64UTF8: String,
      ivBase64UTF8: String,
      context: Map[String, String] = Map())(implicit ec: ExecutionContext, kms: KMSClient): Future[String] = {

      val decryptRequest = new DecryptRequest()
        .withEncryptionContext(context)
        .withCiphertextBlob(dataKeyCiphertextBase64UTF8.decodeBase64AsUTF8ByteBuffer)

      for {
        decryptedDataKey <- kms.decrypt(decryptRequest)

        dataKey = decryptedDataKey.getPlaintext
        ciphertext = ciphertextBase64UTF8.decodeBase64AsUTF8ByteBuffer
        iv = ivBase64UTF8.decodeBase64AsUTF8ByteBuffer

        decrypted <- decrypt_AES_CBC(ciphertext, dataKey, iv)
      } yield { decrypted.asUTF8String }
    }
  }
}

case class KMSClient(kms: AWSKMSAsyncClient) {
  type Self = KMSClient

  def underlying: AWSKMSAsyncClient = kms

  def withRegion(region: Region): Self = { kms.setRegion(region); this }
  def withRegion(regions: Regions): Self = withRegion(Region.getRegion(regions))

  def withCurrentRegionOr(default: Region): Self = {
    withRegion(Option(Regions.getCurrentRegion) getOrElse { default })
    this
  }
  def withCurrentRegionOr(default: Regions): Self = withCurrentRegionOr(Region.getRegion(default))

  // TODO: Change encrypt/decrypt to use the proper AsyncHandler implementation
  /**
   * @see http://docs.aws.amazon.com/kms/latest/APIReference/API_Encrypt.html
   */
  def encrypt(request: EncryptRequest)(implicit ec: ExecutionContext): Future[EncryptResult] = {
    val handler = AWSUtil.FutureAsyncHandler[EncryptRequest, EncryptResult]
    kms.encryptAsync(request, handler)
    handler.future
  }

  /**
   * @see http://docs.aws.amazon.com/kms/latest/APIReference/API_Decrypt.html
   */
  def decrypt(request: DecryptRequest)(implicit ec: ExecutionContext): Future[DecryptResult] = {
    val handler = AWSUtil.FutureAsyncHandler[DecryptRequest, DecryptResult]
    kms.decryptAsync(request, handler)
    handler.future
  }

  /**
   * @see http://docs.aws.amazon.com/kms/latest/APIReference/API_GenerateDataKey.html
   */
  def generateDataKey(request: GenerateDataKeyRequest): Future[GenerateDataKeyResult] = {
    val handler = AWSUtil.FutureAsyncHandler[GenerateDataKeyRequest, GenerateDataKeyResult]
    kms.generateDataKeyAsync(request, handler)
    handler.future
  }

  /**
   * @see http://docs.aws.amazon.com/kms/latest/APIReference/API_GenerateRandom.html
   */
  def generateRandom(request: GenerateRandomRequest): Future[GenerateRandomResult] = {
    val handler = AWSUtil.FutureAsyncHandler[GenerateRandomRequest, GenerateRandomResult]
    kms.generateRandomAsync(request, handler)
    handler.future
  }
}
