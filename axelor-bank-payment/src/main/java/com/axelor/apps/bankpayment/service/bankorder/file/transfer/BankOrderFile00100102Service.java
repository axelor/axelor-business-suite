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
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.AccountIdentification3Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.AmountType2Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.BranchAndFinancialInstitutionIdentification3;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.CashAccount7;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.CreditTransferTransactionInformation1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.CurrencyAndAmount;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.Document;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.FinancialInstitutionIdentification5Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.GenericIdentification3;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.GroupHeader1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.Grouping1Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.ObjectFactory;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.Pain00100102;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.PartyIdentification8;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.PaymentIdentification1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.PaymentInstructionInformation1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.PaymentMethod3Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.PaymentTypeInformation1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.RemittanceInformation1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.ServiceLevel1Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02.ServiceLevel2Choice;
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

public class BankOrderFile00100102Service extends BankOrderFileService {

  protected static final String BIC_NOT_PROVIDED = "NOTPROVIDED";

  @Inject
  public BankOrderFile00100102Service(BankOrder bankOrder) {

    super(bankOrder);

    context = "com.axelor.apps.bankpayment.xsd.sepa.pain_001_001_02";
    fileExtension = FILE_EXTENSION_XML;
  }

  /**
   * Method to create an XML file for SEPA transfer pain.001.001.02
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

    ServiceLevel2Choice svcLvl = factory.createServiceLevel2Choice();
    svcLvl.setCd(ServiceLevel1Code.SEPA);

    PaymentTypeInformation1 pmtTpInf = factory.createPaymentTypeInformation1();
    pmtTpInf.setSvcLvl(svcLvl);

    // Payer
    PartyIdentification8 dbtr = factory.createPartyIdentification8();
    dbtr.setNm(senderBankDetails.getOwnerName());

    // IBAN
    AccountIdentification3Choice iban = factory.createAccountIdentification3Choice();
    iban.setIBAN(senderBankDetails.getIban());

    CashAccount7 dbtrAcct = factory.createCashAccount7();
    dbtrAcct.setId(iban);

    // BIC
    FinancialInstitutionIdentification5Choice finInstnId =
        factory.createFinancialInstitutionIdentification5Choice();

    fillBic(finInstnId, senderBankDetails.getBank());

    BranchAndFinancialInstitutionIdentification3 dbtrAgt =
        factory.createBranchAndFinancialInstitutionIdentification3();
    dbtrAgt.setFinInstnId(finInstnId);

    PaymentInstructionInformation1 pmtInf = factory.createPaymentInstructionInformation1();
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

    CreditTransferTransactionInformation1 cdtTrfTxInf = null;
    PaymentIdentification1 pmtId = null;
    AmountType2Choice amt = null;
    CurrencyAndAmount instdAmt = null;
    PartyIdentification8 cbtr = null;
    CashAccount7 cbtrAcct = null;
    BranchAndFinancialInstitutionIdentification3 cbtrAgt = null;
    RemittanceInformation1 rmtInf = null;

    for (BankOrderLine bankOrderLine : bankOrderLineList) {

      BankDetails receiverBankDetails = bankOrderLine.getReceiverBankDetails();

      // Reference
      pmtId = factory.createPaymentIdentification1();
      //			pmtId.setInstrId(bankOrderLine.getSequence());
      pmtId.setEndToEndId(bankOrderLine.getSequence());

      // Amount
      instdAmt = factory.createCurrencyAndAmount();
      instdAmt.setCcy(bankOrderCurrency.getCode());
      instdAmt.setValue(bankOrderLine.getBankOrderAmount());

      amt = factory.createAmountType2Choice();
      amt.setInstdAmt(instdAmt);

      // Receiver
      cbtr = factory.createPartyIdentification8();
      cbtr.setNm(receiverBankDetails.getOwnerName());

      // IBAN
      iban = factory.createAccountIdentification3Choice();
      iban.setIBAN(receiverBankDetails.getIban());

      cbtrAcct = factory.createCashAccount7();
      cbtrAcct.setId(iban);

      // BIC
      finInstnId = factory.createFinancialInstitutionIdentification5Choice();

      fillBic(finInstnId, receiverBankDetails.getBank());

      cbtrAgt = factory.createBranchAndFinancialInstitutionIdentification3();
      cbtrAgt.setFinInstnId(finInstnId);

      rmtInf = factory.createRemittanceInformation1();

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

      //			StructuredRemittanceInformation6 strd = factory.createStructuredRemittanceInformation6();
      //
      //			CreditorReferenceInformation1 cdtrRefInf = factory.createCreditorReferenceInformation1();
      //			cdtrRefInf.setCdtrRef(bankOrderLine.getReceiverReference());
      //
      //			strd.setCdtrRefInf(cdtrRefInf);
      //
      //			rmtInf.getStrd().add(strd);

      // Transaction
      cdtTrfTxInf = factory.createCreditTransferTransactionInformation1();
      cdtTrfTxInf.setPmtId(pmtId);
      cdtTrfTxInf.setAmt(amt);
      cdtTrfTxInf.setCdtr(cbtr);
      cdtTrfTxInf.setCdtrAcct(cbtrAcct);
      cdtTrfTxInf.setCdtrAgt(cbtrAgt);
      cdtTrfTxInf.setRmtInf(rmtInf);

      pmtInf.getCdtTrfTxInf().add(cdtTrfTxInf);
    }

    // Header
    GroupHeader1 grpHdr = factory.createGroupHeader1();

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
    grpHdr.setGrpg(Grouping1Code.MIXD);
    grpHdr.setInitgPty(dbtr);

    // Parent
    Pain00100102 pain00100102 = factory.createPain00100102();
    pain00100102.setGrpHdr(grpHdr);
    pain00100102.getPmtInf().add(pmtInf);

    // Document
    Document xml = factory.createDocument();
    xml.setPain00100102(pain00100102);

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
  protected void fillBic(FinancialInstitutionIdentification5Choice finInstnId, Bank bank) {

    if (bankOrderFileFormat.getIbanOnly()
        || bank == null
        || Strings.isNullOrEmpty(bank.getCode())) {
      GenericIdentification3 genId = new GenericIdentification3();
      genId.setId(BIC_NOT_PROVIDED);
      finInstnId.setPrtryId(genId);
    } else {
      finInstnId.setBIC(bank.getCode());
    }
  }
}
