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
package com.axelor.apps.bankpayment.service.bankorder.file.directdebit;

import com.axelor.apps.account.db.Umr;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.BankOrderLine;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.apps.bankpayment.service.config.BankPaymentConfigService;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.AccountIdentification3Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.BranchAndFinancialInstitutionIdentification3;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.CashAccount7;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.ChargeBearerType1Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.CurrencyAndAmount;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.DirectDebitTransaction1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.DirectDebitTransactionInformation1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.Document;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.FinancialInstitutionIdentification5Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.GenericIdentification3;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.GenericIdentification4;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.GroupHeader1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.Grouping1Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.LocalInstrument1Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.MandateRelatedInformation1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.ObjectFactory;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.Pain00800101;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.Party2Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.PartyIdentification8;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.PaymentIdentification1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.PaymentInstructionInformation2;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.PaymentMethod2Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.PaymentTypeInformation2;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.PersonIdentification3;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.RemittanceInformation1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.SequenceType1Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.ServiceLevel2Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01.ServiceLevel3Choice;
import com.axelor.apps.base.db.Bank;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.tool.StringTool;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

public class BankOrderFile00800101Service extends BankOrderFile008Service {

  protected ObjectFactory factory;
  protected String sepaType;

  @Inject
  public BankOrderFile00800101Service(BankOrder bankOrder, String sepaType) {
    super(bankOrder);

    context = "com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_01";

    factory = new ObjectFactory();
    this.sepaType = sepaType;
  }

  /**
   * Generates the XML SEPA Direct Debit file (pain.008.001.01)
   *
   * @return the SEPA Direct Debit file (pain.008.001.01)
   * @throws JAXBException
   * @throws IOException
   * @throws AxelorException
   * @throws DatatypeConfigurationException
   */
  @Override
  public File generateFile()
      throws JAXBException, IOException, AxelorException, DatatypeConfigurationException {
    // Creditor
    PartyIdentification8 creditor = factory.createPartyIdentification8();
    creditor.setNm(senderBankDetails.getOwnerName());

    /*
     * Hierarchy of a XML file
     *
     * GroupHeader                          : This building block is mandatory and present once.
     *                                        It contains elements such as Message Identification,
     *                                        Creation Date And Time, Grouping indicator.
     * Payment Information                  : This building block is mandatory and repetitive.
     *                                        It contains, among other things, elements related
     *                                        to the Credit side of the transaction, such as
     *                                        Creditor and Payment Type Information.
     * Direct Debit Transaction Information : This building block is mandatory and repetitive.
     *                                        It contains, among other things, elements related
     *                                        to the debit side of the transaction, such as
     *                                        Debtor and Remittance Information Rules.
     *
     * <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
     * <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.008.001.01">
     *     <pain.008.001.01>
     *         <GrpHdr>                 <-- occ : 1..1
     *         </GrpHdr>
     *         <PmtInf>                 <-- occ : 1..n
     *             <DrctDbtTxInf>       <-- occ : 1..n
     *             </DrctDbtTxInf>
     *         </PmtInf>
     *     </pain.008.001.01>
     * </Document>
     */

    /*
     * Document, <Document> tag
     */
    Document document = factory.createDocument();

    /*
     * pain.008.001.01, <pain.008.001.01> tag
     */
    Pain00800101 pain00800101 = factory.createPain00800101();
    document.setPain00800101(pain00800101);

    /*
     * Group Header, <GrpHdr> tag
     * Set of characteristics shared by all individual transactions included in the message.
     */
    GroupHeader1 groupHeader = factory.createGroupHeader1();
    createGrpHdr(groupHeader, creditor);
    pain00800101.setGrpHdr(groupHeader);

    /*
     * Payment Information, <PmtInf> tag
     * Does not need to set the List<PaymentInstructionInformation2> to the pain00800101 object (see doc).
     */
    createPmtInf(pain00800101.getPmtInf(), creditor);

    fileToCreate = factory.createDocument(document);
    return super.generateFile();
  }

  /**
   * Builds the GroupHeader part ({@code <GrpHdr>} tag) of the file, into the provided {@link
   * GroupHeader1} object
   *
   * @param groupHeader the {@link GroupHeader1} to build
   * @param creditor the creditor of the SEPA Direct Debit file
   * @throws DatatypeConfigurationException
   */
  protected void createGrpHdr(GroupHeader1 groupHeader, PartyIdentification8 creditor)
      throws DatatypeConfigurationException {
    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

    /*
     * Message Identification (mandatory)
     * Point to point reference assigned by the instructing party and sent to the next party in the chain to unambiguously identify the message.
     */
    groupHeader.setMsgId(bankOrderSeq);

    /*
     * Creation Date Time (mandatory)
     * Date and time at which a (group of) payment instruction(s) was created by the instructing party.
     *
     * Format : YYYY-MM-DDThh:mm:ss
     * Example : <CreDtTm>2009-12-02T08:35:30</CreDtTm>
     */
    groupHeader.setCreDtTm(
        datatypeFactory.newXMLGregorianCalendar(
            generationDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));

    /*
     * Batch Booking (optional)
     * Identifies whether a single entry per individual transaction or a batch entry for the sum of the amounts of all transactions in the message is required.
     *
     * Usage : Recommended "true". If absent then default "true".
     *
     * 'true'  if : Identifies that a batch entry for the sum of the amounts of all
     *              transactions in a Payment Information Block is required.
     *              (one credit for all transactions in a Payment Information Block)
     * 'false' if : Identifies that a single entry for each of the transactions
     *              in a message is required.
     *
     */
    groupHeader.setBtchBookg(true);

    /*
     * Number Of Transactions (mandatory)
     * Number of individual transactions contained in the message.
     */
    groupHeader.setNbOfTxs(Integer.toString(nbOfLines));

    /*
     * Pas documenté dans le fichier, repris du fichier BankOrderFile00100103Service.java
     */
    groupHeader.setCtrlSum(arithmeticTotal);

    /*
     * Grouping (mandatory)
     * Indicates whether common accounting information in the transaction is included once for all transactions or repeated for each single transaction.
     */
    groupHeader.setGrpg(Grouping1Code.MIXD); // TO CHECK

    /*
     * Initiating Party (mandatory)
     * Party initiating the payment. In the direct debit context, this can be the creditor, or the party that initiates the payment on behalf of the creditor.
     */
    groupHeader.setInitgPty(creditor);

    /*
     * Pas documenté dans le fichier
     */
    // groupHeader.setFwdgAgt(???);
  }

  /**
   * Builds the PaymentInformation part ({@code <PmtInf>} tag) of the file, and adds it into the
   * provided {@link PaymentInstructionInformation2} list
   *
   * @param paymentInstructionInformationList the list to add the {@link
   *     PaymentInstructionInformation2} objects into
   * @param creditor the creditor of the SEPA Direct Debit file
   * @throws DatatypeConfigurationException
   */
  protected void createPmtInf(
      List<PaymentInstructionInformation2> paymentInstructionInformationList,
      PartyIdentification8 creditor)
      throws AxelorException, DatatypeConfigurationException {
    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

    /*
     * Payment Information (mandatory)
     * Set of characteristics that apply to the credit side of the payment transactions included in the direct debit transaction initiation.
     */
    PaymentInstructionInformation2 paymentInstructionInformation2 =
        factory.createPaymentInstructionInformation2();
    paymentInstructionInformationList.add(paymentInstructionInformation2);

    /*
     * Payment Information Identification (optional)
     * Reference assigned by a sending party to unambiguously identify the payment information block within the message.
     */
    paymentInstructionInformation2.setPmtInfId(bankOrderSeq);

    /*
     * Payment Method (mandatory, always 'DD')
     * Specifies the means of payment that will be used to move the amount of money.
     */
    paymentInstructionInformation2.setPmtMtd(PaymentMethod2Code.DD);

    /*
     * Payment Type Information (mandatory)
     * Set of elements that further specifies the type of transaction.
     */
    PaymentTypeInformation2 paymentTypeInformation2 = factory.createPaymentTypeInformation2();
    paymentInstructionInformation2.setPmtTpInf(paymentTypeInformation2);

    /*
     * ServiceLevel (mandatory)
     * Agreement under which or rules under which the transaction should be processed.
     */
    ServiceLevel3Choice serviceLevel3Choice = factory.createServiceLevel3Choice();
    paymentTypeInformation2.setSvcLvl(serviceLevel3Choice);
    /*
     * Code (mandatory, always 'SEPA')
     * Identification of a pre-agreed level of service between the parties in a coded form.
     */
    serviceLevel3Choice.setCd(ServiceLevel2Code.SEPA);

    /*
     * Local Instrument (mandatory)
     * User community specific instrument.
     */
    LocalInstrument1Choice localInstrument1Choice = factory.createLocalInstrument1Choice();
    /*
     * Code (mandatory)
     *
     * Format : either 'CORE' or 'B2B'
     * Rule : The mixing of Core Direct Debits and Business-to-Business Direct Debits is not
     *        allowed in the same message.
     */
    switch (sepaType) {
      case SEPA_TYPE_CORE:
        localInstrument1Choice.setCd(SEPA_TYPE_CORE);
        break;
      case SEPA_TYPE_SBB:
        localInstrument1Choice.setCd(SEPA_TYPE_SBB);
        break;
      default:
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.BANK_ORDER_FILE_UNKNOWN_SEPA_TYPE));
    }
    paymentTypeInformation2.setLclInstrm(localInstrument1Choice);

    /*
     * Sequence Type (mandatory) // TO CHECK
     * Identifies the direct debit sequence, e.g. first, recurrent, final or one-off.
     *
     * Either one of the following values.
     * CODE  Name       Definition
     * ------------------------------------------------------------------------------------
     * FRST  First      First collection of a series of direct debit instructions.
     * RCUR  Recurrent  Direct debit instruction where the debtor's authorisation is used for
     *                  regular direct debit transactions initiated by the creditor.
     * FNAL  Final      Final collection of a series of direct debit instructions.
     * OOFF  One Off    Direct debit instruction where the debtor's authorisation is used to
     *                  initiate one single direct debit transaction.
     */
    paymentTypeInformation2.setSeqTp(SequenceType1Code.FRST);

    /*
     * Category Purpose (optional)
     * Specifies the purpose of the payment based on a set of pre-defined categories.
     *
     * iso20022.org -> 'PaymentCategoryPurpose1Code' for all codes available and definitions.
     * Rule : The usage and impact of these codes is to be agreed with your bank.
     */
    // paymentTypeInformation2.setCtgyPurp(PaymentCategoryPurpose1Code.CASH);

    /*
     * Requested Collection Date (mandatory)
     * Date at which the creditor requests the amount of money to be collected from the debtor.
     *
     * Format : YYYY-MM-DD
     * Usage  : The minimum delay between sending date and requested collection date is depending
     *          on the type of direct debit (B2B or CORE) and on the sequence type (FRST, OOFF,
     *          RCUR, FNAL).
     */
    paymentInstructionInformation2.setReqdColltnDt(
        datatypeFactory.newXMLGregorianCalendar(
            bankOrderDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));

    /*
     * Creditor (mandatory)
     * Party to which an amount of money is due.
     */
    paymentInstructionInformation2.setCdtr(creditor);

    /*
     * Creditor Account (mandatory)
     * Unambiguous identification of the account of the creditor to which a credit entry will be posted as a result of the payment transaction.
     */
    AccountIdentification3Choice accountIdentification3Choice =
        factory.createAccountIdentification3Choice();
    accountIdentification3Choice.setIBAN(senderBankDetails.getIban());
    CashAccount7 cashAccount7 = factory.createCashAccount7();
    cashAccount7.setId(accountIdentification3Choice);
    paymentInstructionInformation2.setCdtrAcct(cashAccount7);

    /*
     * Creditor Agent (mandatory)
     * Financial institution servicing an account for the creditor.
     *
     * Note : The Bank Identifier Code (BIC) is composed of 8 or 11 characters, of which only the
     *        first 8 characters are significant.
     */
    FinancialInstitutionIdentification5Choice financialInstitutionIdentification5Choice =
        factory.createFinancialInstitutionIdentification5Choice();
    financialInstitutionIdentification5Choice.setBIC(senderBankDetails.getBank().getCode()); // BIC
    BranchAndFinancialInstitutionIdentification3 branchAndFinancialInstitutionIdentification3 =
        factory.createBranchAndFinancialInstitutionIdentification3();
    branchAndFinancialInstitutionIdentification3.setFinInstnId(
        financialInstitutionIdentification5Choice);
    paymentInstructionInformation2.setCdtrAgt(branchAndFinancialInstitutionIdentification3);

    /*
     * Ultimate Creditor (optional)
     * Ultimate party to which an amount of money is due. Ultimate Creditor is only to be used if different from Creditor.
     */
    // paymentInstructionInformation2.setUltmtCdtr();

    /*
     * Charge Bearer (mandatory) // TO CHECK : option dans la vue BankOrder
     * Specifies which party/parties will bear the charges associated with the processing of the payment transaction.
     *
     * CODE  Name                   Description
     * ----------------------------------------------------------------------------------------
     * DEBT  BorneByDebtor          All transaction charges are to be borne by the debtor
     * CRED  BorneByCreditor        All transaction charges are to be borne by the creditor
     * SHAR  Shared                 In a direct debit context, means that transaction charges on
     *                              the sender side are to be borne by the creditor, transaction
     *                              charges on the receiver side are to be borne by the debtor.
     * SLEV  FollowingServiceLevel  Charges are to be applied following the rules agreed in the
     *                              service level and/or scheme.
     */
    paymentInstructionInformation2.setChrgBr(ChargeBearerType1Code.SLEV);

    /*
     * Direct Debit Transaction Information, <DrctDbtTxInf> tag
     * Does not need to set the List<DirectDebitTransactionInformation1> to the paymentInstructionInformation2 object (see doc)
     */
    createDrctDbtTxInf(paymentInstructionInformation2.getDrctDbtTxInf(), creditor);
  }

  /**
   * Builds the DirectDebitTransactionInformation part ({@code <DrctDbtTxInf>} tag) of the file, and
   * adds it into the provided {@link DirectDebitTransactionInformation1} list
   *
   * @param directDebitTransactionInformation1List the list to add the {@link
   *     DirectDebitTransactionInformation1} objects into
   * @param creditor the creditor of the SEPA Direct Debit file
   * @throws DatatypeConfigurationException
   * @throws AxelorException
   */
  protected void createDrctDbtTxInf(
      List<DirectDebitTransactionInformation1> directDebitTransactionInformation1List,
      PartyIdentification8 creditor)
      throws DatatypeConfigurationException, AxelorException {
    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

    for (BankOrderLine bankOrderLine : bankOrderLineList) {

      BankDetails receiverBankDetails = bankOrderLine.getReceiverBankDetails();
      Umr receiverUmr = bankOrderLine.getPartner().getActiveUmr();

      if (receiverUmr == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.DIRECT_DEBIT_MISSING_PARTNER_ACTIVE_UMR));
      }

      /*
       * Direct Debit Transaction Information (mandatory)
       * Set of elements providing information specific to the individual transaction(s) included in the message.
       */
      DirectDebitTransactionInformation1 directDebitTransactionInformation1 =
          factory.createDirectDebitTransactionInformation1();
      directDebitTransactionInformation1List.add(directDebitTransactionInformation1);

      /*
       * Payment Identification (mandatory)
       * Set of elements to reference a payment instruction.
       */
      PaymentIdentification1 paymentIdentification1 = factory.createPaymentIdentification1();
      directDebitTransactionInformation1.setPmtId(paymentIdentification1);
      /*
       * Instruction Identification (optional)
       * The Instruction Identification is a unique reference assigned by the Initiator to unambiguously identify the transaction.
       * It can be used in status messages related to the transaction.
       */
      // paymentIdentification1.setInstrId();
      /*
       * End To End Identification (mandatory)
       * Unique identification assigned by the initiating party to unumbiguously identify the transaction.
       * This identification is passed on, unchanged, throughout the entire end-to-end chain.
       */
      paymentIdentification1.setEndToEndId(bankOrderLine.getSequence());

      /*
       * Instructed Amount (mandatory)
       * Amount of the direct debit, expressed in euro.
       *
       * Format : Max. 11 digits of which 2 for the fractional part.
       *          Decimal separator is "."
       *          Currency "EUR" is explicit, and included in the XML tag.
       * Usage  : Amount must be between 0.01 and 999999999.99
       */
      CurrencyAndAmount currencyAndAmount = factory.createCurrencyAndAmount();
      currencyAndAmount.setCcy(CURRENCY_CODE);
      currencyAndAmount.setValue(bankOrderLine.getBankOrderAmount());
      directDebitTransactionInformation1.setInstdAmt(currencyAndAmount);

      /*
       * Direct Debit Transaction (mandatory)
       * Set of elements providing information specific to the direct debit mandate.
       */
      DirectDebitTransaction1 directDebitTransaction1 = factory.createDirectDebitTransaction1();
      directDebitTransactionInformation1.setDrctDbtTx(directDebitTransaction1);
      /*
       * Mandate Related Information (mandatory)
       * Set of elements used to provide further details related to a direct debit mandate signed between the creditor and the debtor.
       */
      MandateRelatedInformation1 mandateRelatedInformation1 =
          factory.createMandateRelatedInformation1();
      directDebitTransaction1.setMndtRltdInf(mandateRelatedInformation1);
      /*
       * Mandate Identification (mandatory)
       * Reference of the direct debit mandate that has been signed between by the debtor and the creditor.
       */
      mandateRelatedInformation1.setMndtId(receiverUmr.getUmrNumber());
      /*
       * Date of Signature (mandatory)
       * Date on which the direct debit mandate has been signed by the debtor.
       *
       * Format : YYYY-MM-DD
       */
      mandateRelatedInformation1.setDtOfSgntr(
          datatypeFactory.newXMLGregorianCalendar(
              receiverUmr
                  .getMandateSignatureDate()
                  .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
      /*
       * Amendment Indicator (optional)
       * Indicator notifying whether the underlying mandate is amended or not.
       *
       * Usage : - If not present, considered as "false".
       *         - If true, 'Amendment Information Details' is mandatory.
       *
       * 'true'  if : The mandate is amended or migrated from Dom'80.
       * 'false' if : The mandate is not amended.
       */
      // mandateRelatedInformation1.setAmdmntInd(???);
      /*
       * Amendment Info Details (optional)
       * List of direct debit mandate elements that have been modified.
       */
      // AmendmentInformationDetails1 amendmentInformationDetails1 =
      // factory.createAmendmentInformationDetails1();
      // mandateRelatedInformation1.setAmdmntInfDtls(amendmentInformationDetails1);
      // amendmentInformationDetails1.setOrgnlMndtId(???);
      // amendmentInformationDetails1.setOrgnlCdtrSchmeId(???);
      // amendmentInformationDetails1.setOrgnlDbtrAcct(???);
      // amendmentInformationDetails1.setOrgnlDbtrAgt(???);
      /*
       * Electronic Signature (optional)
       * Digital signature as provided by the creditor.
       *
       * Usage : - If the direct debit is based on an electronic mandate, this data
       *           element must contain the reference of the Mandate Acceptance Report.
       *         - If the direct debit is based on a paper mandate, this data element
       *           is not allowed.
       */
      // mandateRelatedInformation1.setElctrncSgntr(???);
      /*
       * Creditor Scheme Identification (mandatory)
       * Credit party that signs the direct debit mandate.
       */
      PartyIdentification8 creditorSchemeId = factory.createPartyIdentification8();
      directDebitTransaction1.setCdtrSchmeId(creditorSchemeId);
      Party2Choice party2Choice = factory.createParty2Choice();
      creditorSchemeId.setId(party2Choice);
      PersonIdentification3 personIdentification3 = factory.createPersonIdentification3();
      party2Choice.getPrvtId().add(personIdentification3);
      GenericIdentification4 genericIdentification4 = factory.createGenericIdentification4();
      personIdentification3.setOthrId(genericIdentification4);
      genericIdentification4.setId(
          Beans.get(BankPaymentConfigService.class)
              .getIcsNumber(senderCompany.getBankPaymentConfig()));
      genericIdentification4.setIdTp("SEPA");

      /*
       * Ultimate Creditor (optional)
       * Ultimate party to which an amount of money is due. Ultimate Creditor is only to be used if different from Creditor.
       */
      // directDebitTransaction1.setUltmtCdtr();

      /*
       * Debtor Agent (mandatory)
       * Financial institution servicing an account for the debtor.
       */
      BranchAndFinancialInstitutionIdentification3 branchAndFinancialInstitutionIdentification3 =
          factory.createBranchAndFinancialInstitutionIdentification3();
      FinancialInstitutionIdentification5Choice financialInstitutionIdentification5Choice =
          factory.createFinancialInstitutionIdentification5Choice();

      fillBic(financialInstitutionIdentification5Choice, receiverBankDetails.getBank()); // BIC

      branchAndFinancialInstitutionIdentification3.setFinInstnId(
          financialInstitutionIdentification5Choice);
      directDebitTransactionInformation1.setDbtrAgt(branchAndFinancialInstitutionIdentification3);

      /*
       * Debtor (mandatory)
       * Party that owes an amount of money to the (ultimate) creditor.
       */
      PartyIdentification8 debtor = factory.createPartyIdentification8();
      debtor.setNm(receiverBankDetails.getOwnerName());
      directDebitTransactionInformation1.setDbtr(debtor);

      /*
       * Debtor Account (mandatory)
       * Identification of the account of the debtor to which a debit entry will be made to execute the transfer.
       */
      AccountIdentification3Choice accountIdentification3Choice =
          factory.createAccountIdentification3Choice();
      accountIdentification3Choice.setIBAN(receiverBankDetails.getIban());
      CashAccount7 cashAccount7 = factory.createCashAccount7();
      cashAccount7.setId(accountIdentification3Choice);
      directDebitTransactionInformation1.setDbtrAcct(cashAccount7);

      /*
       * Ultimate Debtor (optional)
       * Ultimate party that owes an amount of money to the (ultimate) creditor. Ultimate Debtor is only to be used if different from Debtor.
       */
      // directDebitTransactionInformation1.setUltmtDbtr(???);

      /*
       * Purpose (optional)
       * Underlying reason for the payment transaction.
       * Purpose is used by the Debtor to provide information to the Creditor, concerning thenature of the payment transaction.
       * It is not used for processing by any of the banks involved.
       */
      // Purpose1Choice purpose1Choice = factory.createPurpose1Choice();
      // directDebitTransactionInformation1.setPurp(purpose1Choice);
      /*
       * Code (mandatory)
       * Specifies the underlying reason of the payment transaction.
       */
      // purpose1Choice.setCd(???);

      /*
       * Remittance Information (optional)
       * Information that enables the matching, ie, reconciliation, of a payment with the items that the payment
       * is intended to settle, eg, commercial invoices in an account receivable system.
       *
       * Usage : Either Structured or Unstructured, but not both.
       */
      RemittanceInformation1 remittanceInformation1 = factory.createRemittanceInformation1();
      directDebitTransactionInformation1.setRmtInf(remittanceInformation1);
      /*
       * Unstructured (choice 1 of 2)
       * Information supplied to enable the matching of an entry with the items that the transfer is intended
       * to settle, eg, commercial invoices in an accounts' receivable system in an unstructured form.
       */
      remittanceInformation1
          .getUstrd()
          .add(StringTool.truncRight(bankOrderLine.getReceiverReference(), 140));

      /*
       * Structured   (choice 2 of 2)
       * Information supplied to enable the matching of an entry with the items that the transfer is intended
       * to settle, eg, commercial invoices in an accounts' receivable system in a structured form.
       */
      // StructuredRemittanceInformation6 structuredRemittanceInformation6 =
      // factory.createStructuredRemittanceInformation6();
      // remittanceInformation1.getStrd().add(structuredRemittanceInformation6);
    }
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
