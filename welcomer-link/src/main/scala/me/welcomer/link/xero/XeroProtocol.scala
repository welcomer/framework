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
import java.security.KeyPair

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
              middleNames: Option[String],
              lastName: String,
              dateOfBirth: String,
              email: String,
              homeAddress: Employee.HomeAddress): Employee = {

      val middle = middleNames map (EmployeeStringField("MiddleNames", _))

      val allFields = Seq(
        EmployeeStringField("FirstName", firstName),
        EmployeeStringField("LastName", lastName),
        EmployeeStringField("DateOfBirth", dateOfBirth),
        EmployeeStringField("Email", email)) ++ middle

      Employee()
        .withFields(allFields)
        .withField(homeAddress)
    }

    def apply(js: JsObject): Employee = {
      val firstName = (js \ "FirstName").asOpt[String].getOrElse("")
      val middleNames = (js \ "MiddleNames").asOpt[String]
      val lastName = (js \ "LastName").asOpt[String].getOrElse("")
      val dateOfBirth = (js \ "DateOfBirth").asOpt[String].getOrElse("")
      val email = (js \ "Email").asOpt[String].getOrElse("")

      val middle = middleNames map (EmployeeStringField("MiddleNames", _))

      val allFields = Seq(
        EmployeeStringField("FirstName", firstName),
        EmployeeStringField("LastName", lastName),
        EmployeeStringField("DateOfBirth", dateOfBirth),
        EmployeeStringField("Email", email)) ++ middle

      Employee()
        .withFields(allFields)
        .withField(Json.fromJson[HomeAddress](js).get)
        .withField(BankAccounts(js))
        .withField(SuperMemberships(js))
        .withField(Json.fromJson[TaxDeclaration](js).get)

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

    implicit lazy val HomeAddressFormat: OFormat[HomeAddress] = (
      (__ \ "HomeAddress" \ "AddressLine1").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)) ~
      (__ \ "HomeAddress" \ "AddressLine2").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)) ~
      (__ \ "HomeAddress" \ "City").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)) ~
      (__ \ "HomeAddress" \ "Region").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)) ~
      (__ \ "HomeAddress" \ "PostalCode").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)) ~
      (__ \ "HomeAddress" \ "Country").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)))(HomeAddress.apply, unlift(HomeAddress.unapply))

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

      def apply(js: JsObject): SuperMemberships = {
        (js \ "SuperMemberships").asOpt[List[JsObject]] match {
          case Some(superList) => SuperMemberships(superList.foldLeft(List[SuperMemberships.SuperMembership]())((acc, x) => Json.fromJson[SuperMemberships.SuperMembership](x).get :: acc))
          case None            => SuperMemberships(Seq())
        }
      }

      case class SuperMembership(superFundId: String, employeeNumber: String, superMembershipId: Option[String] = None) {
        def toXml: Elem = {
          <SuperMembership>
            <SuperFundID>{ superFundId }</SuperFundID>
            <EmployeeNumber>{ employeeNumber }</EmployeeNumber>
						{ if (superMembershipId.isDefined) <SuperMembershipID>{ superMembershipId.get }</SuperMembershipID> }
          </SuperMembership>
        }
      }

      implicit lazy val SuperMembershipFormat: OFormat[SuperMembership] = (
        (__ \ "SuperFundID").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)) ~
        (__ \ "EmployeeNumber").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)) ~
        (__ \ "SuperMembershipID").formatNullable[String])(SuperMembership.apply, unlift(SuperMembership.unapply))
      
    }

    case class BankAccounts(children: Seq[BankAccounts.BankAccount]) extends EmployeeField {
      def key = "BankAccounts"
      def toXml: Elem = {
        <BankAccounts>
          { children.foldLeft(new NodeBuffer()) { _ += _.toXml } }
        </BankAccounts>
      }
    }

    case object BankAccounts {
      def apply(bankAccount: BankAccount): BankAccounts = {
        BankAccounts(Seq(bankAccount))
      }

      def apply(js: JsObject): BankAccounts = {
        (js \ "BankAccounts").asOpt[List[JsObject]] match {
          case Some(bankList) => BankAccounts(bankList.foldLeft(List[BankAccounts.BankAccount]())((acc, x) => Json.fromJson[BankAccounts.BankAccount](x).get :: acc))
          case None           => BankAccounts(Seq())
        }
      }

      //      implicit lazy val BankAccountsFormat: OFormat[BankAccounts] = (
      //        (__ \ "BankAccounts").format[List[BankAccounts.BankAccount]])(BankAccounts.apply, unlift(BankAccounts.unapply))

      case class BankAccount(accountName: String, bsb: String, accNum: String, remainder: Boolean = true, amount: Option[Double] = None) {
        def toXml: Elem = {
          <BankAccount>
            <StatementText>Salary</StatementText>
            <AccountName>{ accountName }</AccountName>
            <BSB>{ bsb }</BSB>
            <AccountNumber>{ accNum }</AccountNumber>
            <Remainder>{ remainder }</Remainder>
            { if (amount.isDefined) <Amount>{ amount.get }</Amount> }
          </BankAccount>
        }
      }

      implicit lazy val BankAccountFormat: OFormat[BankAccount] = (
        (__ \ "AccountName").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)) ~
        (__ \ "BSB").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)) ~
        (__ \ "AccountNumber").formatNullable[String].inmap[String](_.getOrElse(""), Some(_)) ~
        (__ \ "Remainder").formatNullable[Boolean].inmap[Boolean](_.getOrElse(false), Some(_)) ~
        (__ \ "Amount").formatNullable[Double])(BankAccount.apply, unlift(BankAccount.unapply))

    }

    case class TaxDeclaration(
      employmentBasis: Option[String] = None,
      tfnExemptionType: Option[String] = None,
      taxFileNumber: Option[String] = None,
      australianResidentForTaxPurposes: Option[Boolean] = None,
      taxFreeThresholdClaimed: Option[Boolean] = None,
      taxOffsetEstimatedAmount: Option[Double] = None,
      hasHelpDebt: Option[Boolean] = None,
      hasSfssDebt: Option[Boolean] = None,
      upwardVariationTaxWitholdingAmount: Option[Double] = None,
      eligibleToReceiveLeaveLoading: Option[Boolean] = None,
      approvedWitholdingVariationPercentage: Option[Double] = None,
      seniorsOffset: Option[Boolean] = None) extends EmployeeField {
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

    implicit lazy val TaxDeclarationFormat: OFormat[TaxDeclaration] = (
      (__ \ "TaxDeclaration" \ "EmploymentBasis").formatNullable[String] ~
      (__ \ "TaxDeclaration" \ "TFNExemptionType").formatNullable[String] ~
      (__ \ "TaxDeclaration" \ "TaxFileNumber").formatNullable[String] ~
      (__ \ "TaxDeclaration" \ "AustralianResidentForTaxPurposes").formatNullable[Boolean] ~
      (__ \ "TaxDeclaration" \ "TaxFreeThresholdClaimed").formatNullable[Boolean] ~
      (__ \ "TaxDeclaration" \ "TaxOffsetEstimatedAmount").formatNullable[Double] ~
      (__ \ "TaxDeclaration" \ "HasHELPDebt").formatNullable[Boolean] ~
      (__ \ "TaxDeclaration" \ "HasSFSSDebt").formatNullable[Boolean] ~
      (__ \ "TaxDeclaration" \ "UpwardVariationTaxWithholdingAmount").formatNullable[Double] ~
      (__ \ "TaxDeclaration" \ "EligibleToReceiveLeaveLoading").formatNullable[Boolean] ~
      (__ \ "TaxDeclaration" \ "ApprovedWithholdingVariationPercentage").formatNullable[Double] ~
      (__ \ "TaxDeclaration" \ "SeniorNotInXeroYet").formatNullable[Boolean])(TaxDeclaration.apply, unlift(TaxDeclaration.unapply))
  }

  def loadKeyPair(key: String): KeyPair = {
    import java.security.Security
    import org.bouncycastle.jce.provider.BouncyCastleProvider
    import org.bouncycastle.openssl.PEMParser
    import org.bouncycastle.openssl.PEMKeyPair
    import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

    Security.addProvider(new BouncyCastleProvider());

    val keyHeader = "-----BEGIN RSA PRIVATE KEY-----"
    val keyFooter = "-----END RSA PRIVATE KEY-----"
    val strippedKey = keyHeader + "\n" +
      key.replaceFirst(keyHeader, "").replaceFirst(keyFooter, "").replaceAll(" ", "") +
      "\n" + keyFooter

    val pemParser: PEMParser = new PEMParser(new java.io.StringReader(strippedKey));
    //  PEMParser pemParser = new PEMParser(new BufferedReader(new FileReader(keyPath)));

    val kp: PEMKeyPair = pemParser.readObject.asInstanceOf[PEMKeyPair]
    pemParser.close()

    val keyConverter: JcaPEMKeyConverter = new JcaPEMKeyConverter()
    keyConverter.getKeyPair(kp)
  }
}
