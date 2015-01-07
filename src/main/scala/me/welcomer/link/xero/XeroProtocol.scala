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

import scala.xml.Elem
import scala.xml.NodeBuffer
import java.text.SimpleDateFormat
import java.util.Date
import play.api.libs.functional.syntax._
import play.api.libs.json._
import scala.xml.XML

// TODO: I can't help but think there is a nice monadic way to handle this..
// Need: Status code, XeroError, XeroResponse[ResponseType], ?XeroRequet[RequestType]?

object XeroProtocol {
  sealed trait Xero

  sealed trait XeroRequest extends Xero {
    //    def key: String
  }

  sealed trait XeroResponse extends Xero
  sealed trait XeroSuccessResponse extends XeroResponse
  sealed trait XeroErrorResponse extends XeroResponse

  case class Employee(fields: Map[String, Employee.EmployeeField] = Map()) {
    import Employee._

    def withField[T <: Employee.EmployeeField](newField: T): Employee = {
      copy(fields = fields + (newField.key -> newField))
    }

    def withField(key: String, value: String): Employee = {
      withField(EmployeeStringField(key, value))
    }

    def withFields[T <: Employee.EmployeeField](newFields: Seq[T]): Employee = {
      val newFieldsMap = newFields.foldLeft(Map[String, Employee.EmployeeField]()) { (map, field) =>
        map + (field.key -> field)
      }

      copy(fields = fields ++ newFieldsMap)
    }

    def toXml: Elem = {
      <Employee>
        {
          fields.foldLeft(new NodeBuffer()) {
            case (buffer, (key, field)) => buffer += field.toXml
          }
        }
      </Employee>
    }
  }

  object Employee {
    import scala.language.implicitConversions

    def apply(field: EmployeeField): Employee = {
      Employee(Map(field.key -> field))
    }

    def apply(firstName: String,
              lastName: String,
              dateOfBirth: String,
              homeAddress: Employee.HomeAddress): Employee = {
      Employee()
        .withFields(Seq(
          EmployeeStringField("FirstName", firstName),
          EmployeeStringField("LastName", lastName),
          EmployeeStringField("DateOfBirth", dateOfBirth)))
        .withField(homeAddress)
    }

    implicit def employeeToXml(employee: Employee): Elem = employee.toXml

    sealed trait EmployeeField {
      def key: String
      def toXml: Elem
    }

    case class EmployeeStringField(key: String, value: String) extends EmployeeField {
      def toXml: Elem = XML.loadString(s"<$key>$value</$key>")
    }

    case class HomeAddress(
      addressLine1: String,
      addressLine2: String,
      city: String,
      state: String,
      postalCode: String,
      country: String) extends EmployeeField {
      def key = "HomeAddress"
      def toXml: Elem = {
        <HomeAddress>
          <AddressLine1>{ addressLine1 }</AddressLine1>
          <AddressLine2>{ addressLine2 }</AddressLine2>
          <City>{ city }</City>
          <Region>{ state }</Region>
          <PostalCode>{ postalCode }</PostalCode>
          <Country>{ country }</Country>
        </HomeAddress>
      }
    }

    case class SuperMemberships(children: Seq[SuperMemberships.SuperMembership]) extends EmployeeField {
      def key = "SuperMemberships"
      def toXml: Elem = {
        <SuperMemberships>
          { children.foldLeft(new NodeBuffer()) { _ += _.toXml } }
        </SuperMemberships>
      }
    }

    case object SuperMemberships {
      def apply(superMembership: SuperMembership): SuperMemberships = {
        SuperMemberships(Seq(superMembership))
      }

      case class SuperMembership(superFundId: String, employeeNumber: String) {
        def toXml: Elem = {
          <SuperMembership>
            <SuperFundID>{ superFundId }</SuperFundID>
            <EmployeeNumber>{ employeeNumber }</EmployeeNumber>
          </SuperMembership>
        }
      }
    }

    case class TaxDeclaration(
      employmentBasis: Option[String] = None,
      tfnExemptionType: Option[String] = None,
      taxFileNumber: Option[String] = None,
      australianResidentForTaxPurposes: Option[Boolean] = None,
      taxFreeThresholdClaimed: Option[Boolean] = None,
      taxOffsetEstimatedAmount: Option[Int] = None,
      hasHelpDebt: Option[Boolean] = None,
      hasSfssDebt: Option[Boolean] = None,
      upwardVariationTaxWitholdingAmount: Option[Int] = None,
      eligibleToReceiveLeaveLoading: Option[Boolean] = None,
      approvedWitholdingVariationPercentage: Option[Int] = None) extends EmployeeField {
      def key = "TaxDeclaration"
      def toXml: Elem = {
        <TaxDeclaration>
          { if (employmentBasis.isDefined) <EmploymentBasis>{ employmentBasis.get }</EmploymentBasis> }
          { if (tfnExemptionType.isDefined) <TFNExemptionType>{ tfnExemptionType.get }</TFNExemptionType> }
          { if (taxFileNumber.isDefined) <TaxFileNumber>{ taxFileNumber.get }</TaxFileNumber> }
          { if (australianResidentForTaxPurposes.isDefined) <AustralianResidentForTaxPurposes>{ australianResidentForTaxPurposes.get }</AustralianResidentForTaxPurposes> }
          { if (taxFreeThresholdClaimed.isDefined) <TaxFreeThresholdClaimed>{ taxFreeThresholdClaimed.get }</TaxFreeThresholdClaimed> }
          { if (taxOffsetEstimatedAmount.isDefined) <TaxOffsetEstimatedAmount>{ taxOffsetEstimatedAmount.get }</TaxOffsetEstimatedAmount> }
          { if (hasHelpDebt.isDefined) <HasHELPDebt>{ hasHelpDebt.get }</HasHELPDebt> }
          { if (hasSfssDebt.isDefined) <HasSFSSDebt>{ hasSfssDebt.get }</HasSFSSDebt> }
          { if (upwardVariationTaxWitholdingAmount.isDefined) <UpwardVariationTaxWithholdingAmount>{ upwardVariationTaxWitholdingAmount.get }</UpwardVariationTaxWithholdingAmount> }
          { if (eligibleToReceiveLeaveLoading.isDefined) <EligibleToReceiveLeaveLoading>{ eligibleToReceiveLeaveLoading.get }</EligibleToReceiveLeaveLoading> }
          { if (approvedWitholdingVariationPercentage.isDefined) <ApprovedWithholdingVariationPercentage>{ approvedWitholdingVariationPercentage.get }</ApprovedWithholdingVariationPercentage> }
        </TaxDeclaration>
      }
    }
  }
}