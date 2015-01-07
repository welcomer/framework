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
package me.welcomer.link.xero

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import java.security.KeyPair
import java.text.SimpleDateFormat

import akka.util.Timeout

import play.api.libs.json._

import com.typesafe.config.ConfigFactory

import me.welcomer.framework.testUtils.WelcomerFrameworkSingleActorSystemTest

import spray.http.HttpResponse

class XeroSpec extends WelcomerFrameworkSingleActorSystemTest {
  import me.welcomer.framework.utils.ImplicitConversions._
  import scala.concurrent.ExecutionContext.Implicits.global
  import me.welcomer.link.xero.XeroProtocol._

  implicit val timeout: Timeout = 5.seconds

  // TODO: Move this to a util somewhere?
  def loadKeyPair(key: String): KeyPair = {
    import java.security.Security
    import org.bouncycastle.jce.provider.BouncyCastleProvider
    import org.bouncycastle.openssl.PEMParser
    import org.bouncycastle.openssl.PEMKeyPair
    import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

    Security.addProvider(new BouncyCastleProvider());

    val keyHeader = "-----BEGIN RSA PRIVATE KEY-----"
    val keyFooter = "-----END RSA PRIVATE KEY-----"
    val strippedKey = keyHeader +
      key.replaceFirst(keyHeader, "").replaceFirst(keyFooter, "").replaceAll(" ", "") +
      keyFooter

    val pemParser: PEMParser = new PEMParser(new java.io.StringReader(strippedKey));
    //  PEMParser pemParser = new PEMParser(new BufferedReader(new FileReader(keyPath)));

    val kp: PEMKeyPair = pemParser.readObject.asInstanceOf[PEMKeyPair]
    pemParser.close()

    val keyConverter: JcaPEMKeyConverter = new JcaPEMKeyConverter()
    keyConverter.getKeyPair(kp)
  }

  val conf = ConfigFactory.load()

  val consumerKey = conf.getString("link.xero.test-consumerKey")
  val consumerSecret = conf.getString("link.xero.test-consumerSecret")
  val privateKey = loadKeyPair(conf.getString("link.xero.test-privateKey")).getPrivate()

  val xeroService: XeroApi = new XeroApiImpl(consumerKey, consumerSecret, privateKey)

  def debugLogOnComplete(f: Future[(HttpResponse, Try[JsValue])]) {
    f onComplete {
      case Success((response, jsTry)) => {
        println(response)

        jsTry match {
          case Success(js) => println(Json.prettyPrint(js))
          case Failure(e) => e.printStackTrace()
        }
      }
      case Failure(e) => e.printStackTrace()
    }
  }

  "XeroService" should {

    "list all super funds" in {
      val f = xeroService.superFunds.listAll

      debugLogOnComplete(f)

      val (r, jsTry) = f.futureValue

      r.status.intValue shouldBe 200
      jsTry.isSuccess shouldBe true
    }

    "get a single super fund" in {
      val f = xeroService.superFunds.get(Some("6d9c69e7-64bf-4b4e-a357-81942d22f104"))

      debugLogOnComplete(f)

      val (r, jsTry) = f.futureValue

      r.status.intValue shouldBe 200
      jsTry.isSuccess shouldBe true

      val js = jsTry.get
      (js \ "SuperFunds")(0) shouldBe Json.obj(
        "SuperFundID" -> "6d9c69e7-64bf-4b4e-a357-81942d22f104",
        "Name" -> "HESTA Super Fund (Health Employees Superannuation Trust Australia)",
        "Type" -> "REGULATED",
        "ABN" -> "64971749321",
        "SPIN" -> "HST0100AU",
        "EmployerNumber" -> "1234",
        "UpdatedDateUTC" -> "/Date(1417091290000+1300)/")
    }

    "create a new regulated super fund" in {
      val f = xeroService.superFunds.create.regulated("64971749321", "HST0100AU")

      debugLogOnComplete(f)

      val (r, jsTry) = f.futureValue

      // We're testing for a known 'already exists' error to imply a valid call

      r.status.intValue shouldBe 400
      jsTry.isSuccess shouldBe true

      val js = jsTry.get
      (((js \ "SuperFunds")(0) \ "ValidationErrors")(0) \ "Message").asOpt[String] shouldBe Some("Superannuation fund already exists")
    }

    "create a new self managed super fund" in {
      val f = xeroService.superFunds.create.selfManaged(
        abn = "11001032511",
        fundName = "Clive Monk Superannuation Fund",
        bsb = "159357",
        accountNumber = "111222333",
        accountName = "Test",
        employerNumber = Some("999555666"))

      debugLogOnComplete(f)

      val (r, jsTry) = f.futureValue

      r.status.intValue shouldBe 200
      jsTry.isSuccess shouldBe true
    }

    "list all employees" in {
      val f = xeroService.employees.listAll

      debugLogOnComplete(f)

      val (r, jsTry) = f.futureValue

      r.status.intValue shouldBe 200
      jsTry.isSuccess shouldBe true
    }

    "create a new employee" in {
      val homeAddress = Employee.HomeAddress(
        addressLine1 = "123 Fake St",
        addressLine2 = "",
        city = "Canberra",
        state = "ACT",
        postalCode = "2600",
        country = "Australia")

      val employee = Employee(
        firstName = "John",
        lastName = "Smith",
        dateOfBirth = "1990-01-02",
        homeAddress = homeAddress)

      val f = xeroService.employees.create(employee)

      debugLogOnComplete(f)

      val (r, jsTry) = f.futureValue

      r.status.intValue shouldBe 200
      jsTry.isSuccess shouldBe true
    }

    "update employee tax declaration" in {
      val taxDeclaration = Employee.TaxDeclaration(
        employmentBasis = "FULLTIME",
        taxFileNumber = "123456782")

      val f = xeroService.employees.updateTaxDeclaration(
        "93cf526b-28d4-4349-8be1-eb006485692f",
        taxDeclaration)

      debugLogOnComplete(f)

      val (r, jsTry) = f.futureValue

      r.status.intValue shouldBe 200
      jsTry.isSuccess shouldBe true

      val js = jsTry.get
      ((js \ "Employees")(0) \ "TaxDeclaration") shouldBe Json.obj(
        "TaxFileNumber" -> "123456782",
        "EmploymentBasis" -> "FULLTIME")
    }

    "update employee super membership" in {
      val superMembership = Employee.SuperMemberships.SuperMembership(
        superFundId = "6d9c69e7-64bf-4b4e-a357-81942d22f104",
        employeeNumber = "123456")

      val f = xeroService.employees.updateSuperMembership(
        "c3616309-0eef-4b8b-9f24-63d31dfdca15",
        superMembership)

      debugLogOnComplete(f)

      val (r, jsTry) = f.futureValue

      r.status.intValue shouldBe 200
      jsTry.isSuccess shouldBe true

      val js = jsTry.get
      (((js \ "Employees")(0) \ "SuperMemberships")(0) \ "SuperFundID").as[String] shouldBe "6d9c69e7-64bf-4b4e-a357-81942d22f104"
    }

  }
}
