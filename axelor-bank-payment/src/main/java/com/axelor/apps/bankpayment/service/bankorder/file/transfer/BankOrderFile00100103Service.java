/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.bankpayment.service.bankorder.file.transfer;

import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.BankOrderLine;
import com.axelor.apps.bankpayment.service.bankorder.file.BankOrderFileService;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.AccountIdentification4Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.ActiveOrHistoricCurrencyAndAmount;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.AmountType3Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.BranchAndFinancialInstitutionIdentification4;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.CashAccount16;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.CreditTransferTransactionInformation10;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.CustomerCreditTransferInitiationV03;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.Document;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.FinancialInstitutionIdentification7;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.GenericFinancialIdentification1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.GroupHeader32;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.ObjectFactory;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.PartyIdentification32;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.PaymentIdentification1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.PaymentInstructionInformation3;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.PaymentMethod3Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.PaymentTypeInformation19;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.RemittanceInformation5;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03.ServiceLevel8Choice;
import com.axelor.apps.base.db.Bank;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.tool.StringTool;
import com.axelor.exception.AxelorException;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

public class BankOrderFile00100103Service extends BankOrderFileService {

  protected static final String BIC_NOT_PROVIDED = "NOTPROVIDED";

  @Inject
  public BankOrderFile00100103Service(BankOrder bankOrder) {

    super(bankOrder);

    context = "com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_03";
    fileExtension = FILE_EXTENSION_XML;
  }

  /**
   * Method to create an XML file for SEPA transfer pain.001.001.03
   *
   * @throws AxelorException
   * @throws DatatypeConfigurationException
   * @throws JAXBException
   * @throws IOException
   */
  @Override
  public File generateFile()
      throws JAXBException, IOException, AxelorException, DatatypeConfigurationException {

    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

    ObjectFactory factory = new ObjectFactory();

    ServiceLevel8Choice svcLvl = factory.createServiceLevel8Choice();
    svcLvl.setCd("SEPA");

    PaymentTypeInformation19 pmtTpInf = factory.createPaymentTypeInformation19();
    pmtTpInf.setSvcLvl(svcLvl);

    // Payer
    PartyIdentification32 dbtr = factory.createPartyIdentification32();
    dbtr.setNm(senderBankDetails.getOwnerName());

    // IBAN
    AccountIdentification4Choice iban = factory.createAccountIdentification4Choice();
    iban.setIBAN(senderBankDetails.getIban());

    CashAccount16 dbtrAcct = factory.createCashAccount16();
    dbtrAcct.setId(iban);

    // BIC
    FinancialInstitutionIdentification7 finInstnId =
        factory.createFinancialInstitutionIdentification7();

    fillBic(finInstnId, senderBankDetails.getBank());

    BranchAndFinancialInstitutionIdentification4 dbtrAgt =
        factory.createBranchAndFinancialInstitutionIdentification4();
    dbtrAgt.setFinInstnId(finInstnId);

    PaymentInstructionInformation3 pmtInf = factory.createPaymentInstructionInformation3();
    pmtInf.setPmtInfId(bankOrderSeq);
    pmtInf.setPmtMtd(PaymentMethod3Code.TRF);
    pmtInf.setPmtTpInf(pmtTpInf);

    /**
     * RequestedExecutionDate Definition : Date at which the initiating party asks the Debtor's Bank
     * to process the payment. This is the date on which the debtor's account(s) is (are) to be
     * debited. XML Tag : <ReqdExctnDt> Occurrences : [1..1] Format : YYYY-MM-DD Rules : date is
     * limited to maximum one year in the future.
     */
    pmtInf.setReqdExctnDt(
        datatypeFactory.newXMLGregorianCalendar(
            bankOrderDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
    pmtInf.setDbtr(dbtr);
    pmtInf.setDbtrAcct(dbtrAcct);
    pmtInf.setDbtrAgt(dbtrAgt);

    CreditTransferTransactionInformation10 cdtTrfTxInf = null;
    PaymentIdentification1 pmtId = null;
    AmountType3Choice amt = null;
    ActiveOrHistoricCurrencyAndAmount instdAmt = null;
    PartyIdentification32 cbtr = null;
    CashAccount16 cbtrAcct = null;
    BranchAndFinancialInstitutionIdentification4 cbtrAgt = null;
    RemittanceInformation5 rmtInf = null;

    for (BankOrderLine bankOrderLine : bankOrderLineList) {

      BankDetails receiverBankDetails = bankOrderLine.getReceiverBankDetails();

      // Reference
      pmtId = factory.createPaymentIdentification1();
      //			pmtId.setInstrId(bankOrderLine.getSequence());
      pmtId.setEndToEndId(bankOrderLine.getSequence());

      // Amount
      instdAmt = factory.createActiveOrHistoricCurrencyAndAmount();
      instdAmt.setCcy(bankOrderCurrency.getCode());
      instdAmt.setValue(bankOrderLine.getBankOrderAmount());

      amt = factory.createAmountType3Choice();
      amt.setInstdAmt(instdAmt);

      // Receiver
      cbtr = factory.createPartyIdentification32();
      cbtr.setNm(receiverBankDetails.getOwnerName());

      // IBAN
      iban = factory.createAccountIdentification4Choice();
      iban.setIBAN(receiverBankDetails.getIban());

      cbtrAcct = factory.createCashAccount16();
      cbtrAcct.setId(iban);

      // BIC
      finInstnId = factory.createFinancialInstitutionIdentification7();

      fillBic(finInstnId, receiverBankDetails.getBank());

      cbtrAgt = factory.createBranchAndFinancialInstitutionIdentification4();
      cbtrAgt.setFinInstnId(finInstnId);

      rmtInf = factory.createRemittanceInformation5();

      String ustrd = "";
      if (!Strings.isNullOrEmpty(bankOrderLine.getReceiverReference())) {
        ustrd += StringTool.truncRight(bankOrderLine.getReceiverReference(), 140);
      }
      if (!Strings.isNullOrEmpty(bankOrderLine.getReceiverLabel())) {
        if (!Strings.isNullOrEmpty(ustrd)) {
          ustrd += " - ";
        }
        ustrd += bankOrderLine.getReceiverLabel();
      }

      if (!Strings.isNullOrEmpty(ustrd)) {
        rmtInf.getUstrd().add(ustrd);
      }

      //			StructuredRemittanceInformation7 strd = factory.createStructuredRemittanceInformation7();
      //
      //			CreditorReferenceInformation2 cdtrRefInf = factory.createCreditorReferenceInformation2();
      //			cdtrRefInf.setRef(bankOrderLine.getReceiverReference());
      //
      //			strd.setCdtrRefInf(cdtrRefInf);
      //
      //			rmtInf.getStrd().add(strd);

      // Transaction
      cdtTrfTxInf = factory.createCreditTransferTransactionInformation10();
      cdtTrfTxInf.setPmtId(pmtId);
      cdtTrfTxInf.setAmt(amt);
      cdtTrfTxInf.setCdtr(cbtr);
      cdtTrfTxInf.setCdtrAcct(cbtrAcct);
      cdtTrfTxInf.setCdtrAgt(cbtrAgt);
      cdtTrfTxInf.setRmtInf(rmtInf);

      pmtInf.getCdtTrfTxInf().add(cdtTrfTxInf);
    }

    // Header
    GroupHeader32 grpHdr = factory.createGroupHeader32();

    /** Référence du message qui n'est pas utilisée comme référence fonctionnelle. */
    grpHdr.setMsgId(bankOrderSeq);

    /**
     * CreationDateTime Definition : Date and Time at which a (group of) payment instruction(s) was
     * created by the instructing party. XML Tag : <CreDtTm> Occurrences : [1..1] Format :
     * YYYY-MM-DDThh:mm:ss
     */
    grpHdr.setCreDtTm(
        datatypeFactory.newXMLGregorianCalendar(
            generationDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));
    grpHdr.setNbOfTxs(Integer.toString(nbOfLines));
    grpHdr.setCtrlSum(arithmeticTotal);
    grpHdr.setInitgPty(dbtr);

    // Parent
    CustomerCreditTransferInitiationV03 customerCreditTransferInitiationV03 =
        factory.createCustomerCreditTransferInitiationV03();
    customerCreditTransferInitiationV03.setGrpHdr(grpHdr);
    customerCreditTransferInitiationV03.getPmtInf().add(pmtInf);

    // Document
    Document xml = factory.createDocument();
    xml.setCstmrCdtTrfInitn(customerCreditTransferInitiationV03);

    fileToCreate = factory.createDocument(xml);

    return super.generateFile();
  }

  /**
   * Method to fill the BIC information. If the BIC is not provided or in Iban only mode, we put
   * NOTPROVIDED value. In this case, the bank ignore the BIC and use the Iban only.
   *
   * @param finInstnId The financial instituation identification tag of the generated file.
   * @param bank The bank from which the BIC is get.
   */
  protected void fillBic(FinancialInstitutionIdentification7 finInstnId, Bank bank) {

    if (bankOrderFileFormat.getIbanOnly()
        || bank == null
        || Strings.isNullOrEmpty(bank.getCode())) {
      GenericFinancialIdentification1 genFinId = new GenericFinancialIdentification1();
      genFinId.setId(BIC_NOT_PROVIDED);
      finInstnId.setOthr(genFinId);
    } else {
      finInstnId.setBIC(bank.getCode());
    }
  }
}
