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
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.xml.Elem
import scala.xml.NodeBuffer
import scala.xml.XML

import java.security.PrivateKey

import akka.actor.ActorSystem
import akka.util.Timeout

import play.api.libs.json._

import me.welcomer.link.ApiService
import me.welcomer.link.xero.XeroProtocol._
import me.welcomer.signpost.RsaSha1MessageSigner
import me.welcomer.signpost.spray.SprayOAuthConsumer
import me.welcomer.signpost.spray.SprayRequestOAuthSigner

import oauth.signpost.signature.HmacSha1MessageSigner
import spray.client.pipelining._
import spray.http.FormData
import spray.http.HttpHeaders.Accept
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.MediaTypes

// @see http://developer.xero.com/documentation/payroll-api/overview/
// @see http://developer.xero.com/documentation/payroll-api/types-and-codes/

trait XeroApi {
  def employees: Employees
  def superFunds: SuperFunds
  def organisation: Organisation
  def superFundProducts: SuperFundProducts

  trait Employees {
    /** @see http://developer.xero.com/documentation/payroll-api/employees/#GET */
    def listAll: Future[XeroResult[JsObject]]

    /** @see http://developer.xero.com/documentation/payroll-api/employees/#GET */
    def get(idOpt: Option[String] = None): Future[XeroResult[JsObject]]

    /** @see http://developer.xero.com/documentation/payroll-api/employees/#POST */
    def create(employee: Employee): Future[XeroResult[JsObject]]

    /** @see http://developer.xero.com/documentation/payroll-api/employees/#POST */
    def update(id: String, employee: Employee): Future[XeroResult[JsObject]]

    def updateTaxDeclaration(
      employeeId: String,
      taxDeclaration: Employee.TaxDeclaration): Future[XeroResult[JsObject]]

    def updateBankInformation(
      employeeId: String,
      bankInformation: Employee.BankAccounts): Future[XeroResult[JsObject]]

    def updateSuperMembership(
      employeeId: String,
      superMembership: Employee.SuperMemberships.SuperMembership): Future[XeroResult[JsObject]]
  }

  trait SuperFunds {
    /** @see http://developer.xero.com/documentation/payroll-api/superfunds/#GET */
    def listAll: Future[XeroResult[JsObject]]

    /** @see http://developer.xero.com/documentation/payroll-api/superfunds/#GET */
    def get(idOpt: Option[String] = None): Future[XeroResult[JsObject]]

    /** @see http://developer.xero.com/documentation/payroll-api/superfunds/#POST */
    def create: SuperFundsCreate

    trait SuperFundsCreate {
      def regulated(
        abn: String,
        usi: String,
        employeeNumber: Option[String] = None): Future[XeroResult[JsObject]]

      def selfManaged(
        abn: String,
        fundName: String,
        bsb: String,
        accountNumber: String,
        accountName: String,
        employerNumber: Option[String] = None): Future[XeroResult[JsObject]]
    }
  }

  trait SuperFundProducts {
    /** @see http://developer.xero.com/documentation/payroll-api/superfundproducts/#GET */
    def get(abn: Option[String] = None, usi: Option[String] = None): Future[XeroResult[JsObject]]
  }

  trait Organisation {
    /** @see http://developer.xero.com/documentation/api/organisation/#GET */
    def get: Future[XeroResult[JsObject]]
  }
}

// Header: 'If-Modified-Since'
// Page: ?page=2
// Where: ?where=
// OrderBy: ?order=EmailAddress%20DESC

object XeroApi {
  /**
   * Instance with a SprayOAuthConsumer
   *
   * @param oAuthConsumer The Spray OAuthConsumer
   */
  def apply(oAuthConsumer: SprayOAuthConsumer)(implicit system: ActorSystem, timeout: Timeout): XeroApi = {

    new XeroApiImpl(oAuthConsumer)
  }

  /**
   * Instance for a 'public application'
   *
   * @param consumerKey Application consumer key
   * @param consumerSecret Application consumer secret
   *
   * @see http://developer.xero.com/documentation/getting-started/public-applications/
   */
  def apply(
    consumerKey: String,
    consumerSecret: String)(implicit system: ActorSystem, timeout: Timeout): XeroApi = {
    val oAuthConsumer = new SprayOAuthConsumer(consumerKey, consumerSecret)
    oAuthConsumer.setMessageSigner(new HmacSha1MessageSigner)

    XeroApi(oAuthConsumer)
  }

  /**
   * Instance for a 'private application'
   *
   * @param consumerKey Application consumer key
   * @param consumerSecret Application consumer secret
   * @param privateKey Application private key file
   *
   * @see http://developer.xero.com/documentation/getting-started/private-applications/
   */
  def apply(
    consumerKey: String,
    consumerSecret: String,
    privateKey: PrivateKey)(implicit system: ActorSystem, timeout: Timeout): XeroApi = {
    val oAuthConsumer = new SprayOAuthConsumer(consumerKey, consumerSecret)
    oAuthConsumer.setMessageSigner(new RsaSha1MessageSigner(privateKey))

    XeroApi(oAuthConsumer)
  }
}

protected class XeroApiImpl(
  oAuthConsumer: SprayOAuthConsumer)(implicit val system: ActorSystem, val timeout: Timeout)
  extends ApiService
  with XeroApi
  with SprayRequestOAuthSigner { self =>

  import spray.httpx.unmarshalling._
  //  import spray.httpx.marshalling._

  override protected lazy val pipeline: SendReceive = {
    addHeader(Accept(MediaTypes.`application/json`)) ~>
      signOAuth(oAuthConsumer) ~>
      Request.debug ~>
      sendReceive ~>
      Response.debug
  }

  // TODO: Probably change JsObject to T <: XeroModel or similar
  def pipelineAsXeroResult = pipeline ~> xeroUnmarshaller[JsObject]

  protected def xeroUnmarshaller[T](implicit unmarshaller: Unmarshaller[T]): HttpResponse => XeroResult[T] = { r =>
    r.status.intValue match {
      case 200 => {
        r.entity.as[T] match {
          case Left(err)    => XeroFailure(SprayDeserialisationError(err), Some(r))
          case Right(value) => XeroSuccess(value, Some(r))
        }
      }
      case statusCode => {
        // TODO: This can be improved dramatically to nicely capture all of the information from the error in a useful object
        // Instead of just pulling out the messages as a string
        // Probably also want to use r.entity.as[OurNewXeroErrorType] instead and make sure we have an Unmarshaller defined for it
        // That unmarshaller can handle the json part as normal, and any weird xero quirks as required (eg. xml responses where json is expected)
        // Ref: http://developer.xero.com/documentation/getting-started/http-response-codes/
        Try(Json.parse(r.entity.asString)) match {
          case Success(json) => {
            val messageList = (json \\ "Message").map(_.as[String])
            val basicErrors = messageList.map(BasicError(_))

            XeroFailure(basicErrors, Some(r))
          }
          case Failure(_) => {
            val err = BasicError(s"[XeroBadJsonErrorResponse] statusCode=$statusCode, entity=" + r.entity.asString)

            XeroFailure(err, Some(r))
          }
        }
      }
    }
  }

  /** @see http://developer.xero.com/documentation/getting-started/http-requests-and-responses/ */
  protected def create(endpoint: String, xml: Elem): HttpRequest = {
    // Apparently this 405's... who wanted to trust the doco anyway..
    //    Put(endpoint, FormData(Map("xml" -> xml.toString)))
    createOrUpdate(endpoint, xml)
  }

  /** @see http://developer.xero.com/documentation/getting-started/http-requests-and-responses/ */
  protected def createOrUpdate(endpoint: String, xml: Elem): HttpRequest = {
    Post(endpoint, FormData(Map("xml" -> xml.toString)))
  }

  /** @see http://developer.xero.com/documentation/getting-started/http-requests-and-responses/ */
  protected def update(endpoint: String, id: String, xml: Elem): HttpRequest = {
    createOrUpdate(endpoint + "/" + id, xml)
  }

  private def xmlFromMap(map: Map[String, Option[String]]): NodeBuffer = {
    map.foldLeft(new NodeBuffer()) {
      case (buffer, (key, valueOpt)) => valueOpt match {
        case Some(value) => buffer += XML.loadString(s"<$key>$value</$key>")
        case None        => buffer
      }
    }
  }

  // API Implementation

  lazy val superFundProducts = new SuperFundProducts {
    def get(abn: Option[String] = None, usi: Option[String] = None): Future[XeroResult[JsObject]] = pipelineAsXeroResult {
      val abnString = abn match { case Some(a) => s"ABN=$a"; case None => "" }
      val usiString = usi match { case Some(u) => s"USI=$u"; case None => "" }
      val searchString = (abn, usi) match {
        case (Some(_), Some(_)) => s"$abnString&$usiString"
        case (Some(_), None)    => abnString
        case (None, Some(_))    => usiString
        case _                  => ""
      }
      Get(XeroEndpoint.superFundProducts + s"?$searchString")
    }
  }

  lazy val organisation = new Organisation {
    def get: Future[XeroResult[JsObject]] = pipelineAsXeroResult {
      Get(XeroEndpoint.organisation)
    }
  }

  lazy val employees = new Employees {
    import Employee._

    def listAll: Future[XeroResult[JsObject]] = get(None)

    def get(idOpt: Option[String] = None): Future[XeroResult[JsObject]] = pipelineAsXeroResult {
      val endpoint = idOpt match {
        case Some(id) => XeroEndpoint.employees + s"/$id"
        case None     => XeroEndpoint.employees
      }

      Get(endpoint)
    }

    def create(employee: Employee): Future[XeroResult[JsObject]] = pipelineAsXeroResult {
      self.create(XeroEndpoint.employees, employee.toXml)
    }

    def update(id: String, employee: Employee): Future[XeroResult[JsObject]] = pipelineAsXeroResult {
      self.update(XeroEndpoint.employees, id, employee.toXml)
    }

    def updateTaxDeclaration(
      employeeId: String,
      taxDeclaration: TaxDeclaration): Future[XeroResult[JsObject]] = pipelineAsXeroResult {
      self.update(
        XeroEndpoint.employees,
        employeeId,
        Employee(taxDeclaration))
    }

    def updateBankInformation(
      employeeId: String,
      bankInformation: BankAccounts): Future[XeroResult[JsObject]] = pipelineAsXeroResult {
      self.update(
        XeroEndpoint.employees,
        employeeId,
        Employee(bankInformation))
    }

    def updateSuperMembership(
      employeeId: String,
      superMembership: SuperMemberships.SuperMembership): Future[XeroResult[JsObject]] = pipelineAsXeroResult {
      self.update(
        XeroEndpoint.employees,
        employeeId,
        Employee(SuperMemberships(superMembership)))
    }
  }

  lazy val superFunds = new SuperFunds {
    def listAll: Future[XeroResult[JsObject]] = get(None)

    def get(idOpt: Option[String] = None): Future[XeroResult[JsObject]] = pipelineAsXeroResult {
      val endpoint = idOpt match {
        case Some(id) => XeroEndpoint.superFunds + s"/$id"
        case None     => XeroEndpoint.superFunds
      }

      Get(endpoint)
    }

    lazy val create = new SuperFundsCreate {
      private def superFundXml(map: Map[String, Option[String]]): Elem = {
        <SuperFunds>
          <SuperFund>
            { xmlFromMap(map) }
          </SuperFund>
        </SuperFunds>
      }

      def regulated(
        abn: String,
        usi: String,
        employerNumber: Option[String] = None): Future[XeroResult[JsObject]] = pipelineAsXeroResult {

        val details = Map(
          "Type" -> Some("REGULATED"),
          "ABN" -> Some(abn),
          "USI" -> Some(usi),
          "EmployerNumber" -> employerNumber)

        val xml = superFundXml(details)

        self.create(XeroEndpoint.superFunds, xml)
      }

      def selfManaged(
        abn: String,
        fundName: String,
        bsb: String,
        accountNumber: String,
        accountName: String,
        employerNumber: Option[String] = None): Future[XeroResult[JsObject]] = pipelineAsXeroResult {

        val details = Map(
          "Type" -> Some("SMSF"),
          "ABN" -> Some(abn),
          "Name" -> Some(fundName),
          "BSB" -> Some(bsb),
          "AccountNumber" -> Some(accountNumber),
          "AccountName" -> Some(accountName),
          "EmployerNumber" -> employerNumber)

        val xml = superFundXml(details)

        self.create(XeroEndpoint.superFunds, xml)
      }
    }

  }
}

protected object XeroEndpoint {
  val PAYROLL_BASE_URL = "https://api.xero.com/payroll.xro/1.0"
  val ACCOUNTING_BASE_URL = "https://api.xero.com/api.xro/2.0"

  val employees = PAYROLL_BASE_URL + "/Employees"
  val superFunds = PAYROLL_BASE_URL + "/SuperFunds"
  val organisation = ACCOUNTING_BASE_URL + "/Organisation"
  val superFundProducts = PAYROLL_BASE_URL + "/SuperFundProducts"
}
