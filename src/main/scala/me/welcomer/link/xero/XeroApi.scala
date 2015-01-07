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

  trait Employees {
    /** @see http://developer.xero.com/documentation/payroll-api/employees/#GET */
    def listAll: Future[(HttpResponse, Try[JsValue])]

    /** @see http://developer.xero.com/documentation/payroll-api/employees/#GET */
    def get(idOpt: Option[String] = None): Future[(HttpResponse, Try[JsValue])]

    /** @see http://developer.xero.com/documentation/payroll-api/employees/#POST */
    def create(employee: Employee): Future[(HttpResponse, Try[JsValue])]

    /** @see http://developer.xero.com/documentation/payroll-api/employees/#POST */
    def update(id: String, employee: Employee): Future[(HttpResponse, Try[JsValue])]

    def updateTaxDeclaration(
      employeeId: String,
      taxDeclaration: Employee.TaxDeclaration): Future[(HttpResponse, Try[JsValue])]

    def updateSuperMembership(
      employeeId: String,
      superMembership: Employee.SuperMemberships.SuperMembership): Future[(HttpResponse, Try[JsValue])]
  }

  trait SuperFunds {
    /** @see http://developer.xero.com/documentation/payroll-api/superfunds/#GET */
    def listAll: Future[(HttpResponse, Try[JsValue])]

    /** @see http://developer.xero.com/documentation/payroll-api/superfunds/#GET */
    def get(idOpt: Option[String] = None): Future[(HttpResponse, Try[JsValue])]

    /** @see http://developer.xero.com/documentation/payroll-api/superfunds/#POST */
    def create: SuperFundsCreate

    trait SuperFundsCreate {
      def regulated(
        abn: String,
        usi: String,
        employeeNumber: Option[String] = None): Future[(HttpResponse, Try[JsValue])]

      def selfManaged(
        abn: String,
        fundName: String,
        bsb: String,
        accountNumber: String,
        accountName: String,
        employerNumber: Option[String] = None): Future[(HttpResponse, Try[JsValue])]
    }
  }
}

class XeroApiImpl(
  consumerKey: String,
  consumerSecret: String,
  privateKey: PrivateKey)(implicit val system: ActorSystem, val timeout: Timeout)
  extends ApiService
  with XeroApi
  with SprayRequestOAuthSigner { self =>

  protected val oAuthCalc = new SprayOAuthConsumer(consumerKey, consumerSecret)
  oAuthCalc.setMessageSigner(new RsaSha1MessageSigner(privateKey))

  override protected lazy val pipeline: SendReceive = {
    addHeader(Accept(MediaTypes.`application/json`)) ~>
      signOAuth(oAuthCalc) ~>
      Request.debug ~>
      sendReceive
  }

  //
  //  override type SuccessResponse = XeroSuccessResponse
  //  override type GenericResponse = XeroResponse
  //
  //  override protected def unmarshalResponse[T <: SuccessResponse](r: HttpResponse)(implicit readsT: Reads[T]): GenericResponse = {
  //    ???
  //  }

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
        case None => buffer
      }
    }
  }

  // API Implementation

  lazy val employees = new Employees {
    import Employee._

    def listAll: Future[(HttpResponse, Try[JsValue])] = get(None)

    def get(idOpt: Option[String] = None): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson {
      val endpoint = idOpt match {
        case Some(id) => XeroEndpoint.employees + s"/$id"
        case None => XeroEndpoint.employees
      }

      Get(endpoint)
    }

    def create(employee: Employee): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson {
      self.create(XeroEndpoint.employees, employee.toXml)
    }

    def update(id: String, employee: Employee): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson {
      self.update(XeroEndpoint.employees, id, employee.toXml)
    }

    def updateTaxDeclaration(
      employeeId: String,
      taxDeclaration: TaxDeclaration): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson {
      self.update(
        XeroEndpoint.employees,
        employeeId,
        Employee(taxDeclaration))
    }

    def updateSuperMembership(
      employeeId: String,
      superMembership: SuperMemberships.SuperMembership): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson {
      self.update(
        XeroEndpoint.employees,
        employeeId,
        Employee(SuperMemberships(superMembership)))
    }
  }

  lazy val superFunds = new SuperFunds {
    def listAll: Future[(HttpResponse, Try[JsValue])] = get(None)

    def get(idOpt: Option[String] = None): Future[(HttpResponse, Try[JsValue])] = pipelineAsJson {
      val endpoint = idOpt match {
        case Some(id) => XeroEndpoint.superFunds + s"/$id"
        case None => XeroEndpoint.superFunds
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
        employerNumber: Option[String] = None): Future[(HttpResponse, Try[JsValue])] = {

        val details = Map(
          "Type" -> Some("REGULATED"),
          "ABN" -> Some(abn),
          "USI" -> Some(usi),
          "EmployerNumber" -> employerNumber)

        val xml = superFundXml(details)

        pipelineAsJson {
          self.create(XeroEndpoint.superFunds, xml)
        }
      }

      def selfManaged(
        abn: String,
        fundName: String,
        bsb: String,
        accountNumber: String,
        accountName: String,
        employerNumber: Option[String] = None): Future[(HttpResponse, Try[JsValue])] = {

        val details = Map(
          "Type" -> Some("SMSF"),
          "ABN" -> Some(abn),
          "Name" -> Some(fundName),
          "BSB" -> Some(bsb),
          "AccountNumber" -> Some(accountNumber),
          "AccountName" -> Some(accountName),
          "EmployerNumber" -> employerNumber)

        val xml = superFundXml(details)

        pipelineAsJson {
          self.create(XeroEndpoint.superFunds, xml)
        }
      }
    }

  }
}

protected object XeroEndpoint {
  val BASE_URL = "https://api.xero.com/payroll.xro/1.0"

  val employees = BASE_URL + "/Employees"
  val superFunds = BASE_URL + "/SuperFunds"
}