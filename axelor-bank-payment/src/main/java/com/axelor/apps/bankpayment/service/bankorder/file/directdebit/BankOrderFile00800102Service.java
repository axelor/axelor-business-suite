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
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.AccountIdentification4Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.ActiveOrHistoricCurrencyAndAmount;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.BranchAndFinancialInstitutionIdentification4;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.CashAccount16;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.ChargeBearerType1Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.CustomerDirectDebitInitiationV02;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.DirectDebitTransaction6;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.DirectDebitTransactionInformation9;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.Document;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.FinancialInstitutionIdentification7;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.GenericFinancialIdentification1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.GenericPersonIdentification1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.GroupHeader39;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.LocalInstrument2Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.MandateRelatedInformation6;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.ObjectFactory;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.Party6Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.PartyIdentification32;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.PaymentIdentification1;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.PaymentInstructionInformation4;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.PaymentMethod2Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.PaymentTypeInformation20;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.PersonIdentification5;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.PersonIdentificationSchemeName1Choice;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.RemittanceInformation5;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.SequenceType1Code;
import com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02.ServiceLevel8Choice;
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

public class BankOrderFile00800102Service extends BankOrderFile008Service {

  protected ObjectFactory factory;
  protected String sepaType;

  @Inject
  public BankOrderFile00800102Service(BankOrder bankOrder, String sepaType) {
    super(bankOrder);

    context = "com.axelor.apps.bankpayment.xsd.sepa.pain_008_001_02";

    factory = new ObjectFactory();
    this.sepaType = sepaType;
  }

  /**
   * Generates the XML SEPA Direct Debit file (pain.008.001.02)
   *
   * @return the SEPA Direct Debit file (pain.008.001.02)
   * @throws JAXBException
   * @throws IOException
   * @throws AxelorException
   * @throws DatatypeConfigurationException
   */
  @Override
  public File generateFile()
      throws JAXBException, IOException, AxelorException, DatatypeConfigurationException {
    // Creditor
    PartyIdentification32 creditor = factory.createPartyIdentification32();
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
     * <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.008.001.02">
     *     <CstmrDrctDbtInitn>
     *         <GrpHdr>                 <-- occ : 1..1
     *         </GrpHdr>
     *         <PmtInf>                 <-- occ : 1..n
     *             <DrctDbtTxInf>       <-- occ : 1..n
     *             </DrctDbtTxInf>
     *         </PmtInf>
     *     </CstmrDrctDbtInitn>
     * </Document>
     */

    /*
     * Document, <Document> tag
     */
    Document document = factory.createDocument();

    /*
     * Customer Direct Debit Initiation, <CstmrDrctDbtInitn> tag
     */
    CustomerDirectDebitInitiationV02 customerDirectDebitInitiationV02 =
        factory.createCustomerDirectDebitInitiationV02();
    document.setCstmrDrctDbtInitn(customerDirectDebitInitiationV02);

    /*
     * Group Header, <GrpHdr> tag
     * Set of characteristics shared by all individual transactions included in the message.
     */
    GroupHeader39 groupHeader = factory.createGroupHeader39();
    createGrpHdr(groupHeader, creditor);
    customerDirectDebitInitiationV02.setGrpHdr(groupHeader);

    /*
     * Payment Information, <PmtInf> tag
     * Does not need to set the List<PaymentInstructionInformation4> to the customerDirectDebitInitiationV02 object (see doc).
     */
    createPmtInf(customerDirectDebitInitiationV02.getPmtInf(), creditor);

    fileToCreate = factory.createDocument(document);
    return super.generateFile();
  }

  /**
   * Builds the GroupHeader part ({@code <GrpHdr>} tag) of the file, into the provided {@link
   * GroupHeader39} object
   *
   * @param groupHeader the {@link GroupHeader39} to build
   * @param creditor the creditor of the SEPA Direct Debit file
   * @throws DatatypeConfigurationException
   */
  protected void createGrpHdr(GroupHeader39 groupHeader, PartyIdentification32 creditor)
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
     * Example : <CreDtTm>2010-12-02T08:35:30</CreDtTm>
     */
    groupHeader.setCreDtTm(
        datatypeFactory.newXMLGregorianCalendar(
            generationDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))));

    /*
     * Number Of Transactions (mandatory)
     * Number of individual transactions contained in the message.
     */
    groupHeader.setNbOfTxs(Integer.toString(nbOfLines));

    /*
     * Control Sum
     * Total of all individual amounts included in the message, irrespective of currencies
     *
     * Format : Max. 18 digits of which 2 for the fractional part.
     *          Decimal separator is "."
     */
    groupHeader.setCtrlSum(arithmeticTotal);

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
   * provided {@link PaymentInstructionInformation4} list
   *
   * @param paymentInstructionInformationList the list to add the {@link
   *     PaymentInstructionInformation4} objects into
   * @param creditor the creditor of the SEPA Direct Debit file
   * @throws DatatypeConfigurationException
   */
  protected void createPmtInf(
      List<PaymentInstructionInformation4> paymentInstructionInformationList,
      PartyIdentification32 creditor)
      throws AxelorException, DatatypeConfigurationException {
    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

    /*
     * Payment Information (mandatory)
     * Set of characteristics that apply to the credit side of the payment transactions included in the direct debit transaction initiation.
     */
    PaymentInstructionInformation4 paymentInstructionInformation4 =
        factory.createPaymentInstructionInformation4();
    paymentInstructionInformationList.add(paymentInstructionInformation4);

    /*
     * Payment Information Identification (mandatory)
     * Reference assigned by a sending party to unambiguously identify the payment information block within the message.
     */
    paymentInstructionInformation4.setPmtInfId(bankOrderSeq);

    /*
     * Payment Method (mandatory, always 'DD')
     * Specifies the means of payment that will be used to move the amount of money.
     */
    paymentInstructionInformation4.setPmtMtd(PaymentMethod2Code.DD);

    /*
     * Batch Booking (optional)
     * Identifies whether a single entry per individual transaction or a batch entry for the sum of the amounts of all transactions in the group is required.
     *
     * Usage : Recommended "true". If absent then default "true".
     *
     * 'true'  if : Identifies that a batch entry for the sum of the amounts of all
     *              transactions in a Payment Information Block is required.
     *              (one credit for all transactions in a Payment Information Block)
     * 'false' if : Identifies that a single entry for each of the transactions
     *              in a message is required.
     */
    paymentInstructionInformation4.setBtchBookg(true);

    /*
     * Number Of Transactions (optional)
     * Number of individual transactions contained in the message.
     */
    paymentInstructionInformation4.setNbOfTxs(Integer.toString(nbOfLines));

    /*
     * Control Sum (optional)
     * Total of all individual amounts included in the payment block, irrespective of currencies.
     *
     * Format : Max. 18 digits of which 2 for the fractional part.
     *          Decimal separator is "."
     */
    paymentInstructionInformation4.setCtrlSum(arithmeticTotal);

    /*
     * Payment Type Information (mandatory)
     * Set of elements that further specifies the type of transaction.
     */
    PaymentTypeInformation20 paymentTypeInformation20 = factory.createPaymentTypeInformation20();
    paymentInstructionInformation4.setPmtTpInf(paymentTypeInformation20);

    /*
     * ServiceLevel (mandatory)
     * Agreement under which or rules under which the transaction should be processed.
     */
    ServiceLevel8Choice serviceLevel8Choice = factory.createServiceLevel8Choice();
    paymentTypeInformation20.setSvcLvl(serviceLevel8Choice);
    /*
     * Code (mandatory, always 'SEPA')
     * Identification of a pre-agreed level of service between the parties in a coded form.
     */
    serviceLevel8Choice.setCd("SEPA");

    /*
     * Local Instrument (mandatory)
     * User community specific instrument.
     */
    LocalInstrument2Choice localInstrument2Choice = factory.createLocalInstrument2Choice();
    /*
     * Code (mandatory)
     *
     * Format : either 'CORE' or 'B2B'
     * Rule : The mixing of Core Direct Debits and Business-to-Business Direct Debits is not
     *        allowed in the same message.
     */
    switch (sepaType) {
      case SEPA_TYPE_CORE:
        localInstrument2Choice.setCd(SEPA_TYPE_CORE);
        break;
      case SEPA_TYPE_SBB:
        localInstrument2Choice.setCd(SEPA_TYPE_SBB);
        break;
      default:
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.BANK_ORDER_FILE_UNKNOWN_SEPA_TYPE));
    }
    paymentTypeInformation20.setLclInstrm(localInstrument2Choice);

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
    paymentTypeInformation20.setSeqTp(SequenceType1Code.FRST);

    /*
     * Category Purpose (optional)
     * Specifies the purpose of the payment based on a set of pre-defined categories.
     */
    // CategoryPurpose1Choice categoryPurpose1Choice = factory.createCategoryPurpose1Choice();
    // paymentTypeInformation20.setCtgyPurp(categoryPurpose1Choice);
    /*
     * Code (mandatory)
     * Specifies the underlying reason of the payment transaction.
     *
     * iso20022.org -> 'PaymentCategoryPurpose1Code' for all codes available and definitions.
     */
    // categoryPurpose1Choice.setCd("CASH");

    /*
     * Requested Collection Date (mandatory)
     * Date at which the creditor requests the amount of money to be collected from the debtor.
     *
     * Format : YYYY-MM-DD
     * Usage  : The minimum delay between sending date and requested collection date is depending
     *          on the type of direct debit (B2B or CORE) and on the sequence type (FRST, OOFF,
     *          RCUR, FNAL).
     */
    paymentInstructionInformation4.setReqdColltnDt(
        datatypeFactory.newXMLGregorianCalendar(
            bankOrderDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));

    /*
     * Creditor (mandatory)
     * Party to which an amount of money is due.
     */
    paymentInstructionInformation4.setCdtr(creditor);

    /*
     * Creditor Account (mandatory)
     * Unambiguous identification of the account of the creditor to which a credit entry will be posted as a result of the payment transaction.
     */
    CashAccount16 cashAccount16 = factory.createCashAccount16();
    /*
     * IBAN (mandatory)
     */
    AccountIdentification4Choice accountIdentification4Choice =
        factory.createAccountIdentification4Choice();
    accountIdentification4Choice.setIBAN(senderBankDetails.getIban());
    cashAccount16.setId(accountIdentification4Choice);
    /*
     * Currency (optional)
     *
     * Rule : Currency of the account must be EUR. For usage of another currency, please
     *        contact your bank.
     */
    cashAccount16.setCcy(CURRENCY_CODE);

    paymentInstructionInformation4.setCdtrAcct(cashAccount16);

    /*
     * Creditor Agent (mandatory)
     * Financial institution servicing an account for the creditor.
     *
     * Note : The Bank Identifier Code (BIC) is composed of 8 or 11 characters, of which only the
     *        first 8 characters are significant.
     */
    FinancialInstitutionIdentification7 financialInstitutionIdentification7 =
        factory.createFinancialInstitutionIdentification7();

    fillBic(financialInstitutionIdentification7, senderBankDetails.getBank()); // BIC

    BranchAndFinancialInstitutionIdentification4 branchAndFinancialInstitutionIdentification4 =
        factory.createBranchAndFinancialInstitutionIdentification4();
    branchAndFinancialInstitutionIdentification4.setFinInstnId(financialInstitutionIdentification7);
    paymentInstructionInformation4.setCdtrAgt(branchAndFinancialInstitutionIdentification4);

    /*
     * Ultimate Creditor (optional)
     * Ultimate party to which an amount of money is due. Ultimate Creditor is only to be used if different from Creditor.
     */
    // paymentInstructionInformation4.setUltmtCdtr();

    /*
     * Charge Bearer (mandatory) // TO CHECK
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
    paymentInstructionInformation4.setChrgBr(ChargeBearerType1Code.SLEV);

    /*
     * Creditor Scheme Identification (optional)
     * Credit party that signs the Direct Debit mandate.
     */
    // paymentInstructionInformation4.setCdtrSchmeId(creditor);

    /*
     * Direct Debit Transaction Information, <DrctDbtTxInf> tag
     * Does not need to set the List<DirectDebitTransactionInformation1> to the paymentInstructionInformation2 object (see doc)
     */
    createDrctDbtTxInf(paymentInstructionInformation4.getDrctDbtTxInf(), creditor);
  }

  /**
   * Builds the DirectDebitTransactionInformation part ({@code <DrctDbtTxInf>} tag) of the file, and
   * adds it into the provided {@link DirectDebitTransactionInformation9} list
   *
   * @param directDebitTransactionInformation9List the list to add the {@link
   *     DirectDebitTransactionInformation9} objects into
   * @param creditor the creditor of the SEPA Direct Debit file
   * @throws DatatypeConfigurationException
   * @throws AxelorException
   */
  protected void createDrctDbtTxInf(
      List<DirectDebitTransactionInformation9> directDebitTransactionInformation9List,
      PartyIdentification32 creditor)
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
      DirectDebitTransactionInformation9 directDebitTransactionInformation9 =
          factory.createDirectDebitTransactionInformation9();
      directDebitTransactionInformation9List.add(directDebitTransactionInformation9);

      /*
       * Payment Identification (mandatory)
       * Set of elements to reference a payment instruction.
       */
      PaymentIdentification1 paymentIdentification1 = factory.createPaymentIdentification1();
      directDebitTransactionInformation9.setPmtId(paymentIdentification1);
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
      ActiveOrHistoricCurrencyAndAmount activeOrHistoricCurrencyAndAmount =
          factory.createActiveOrHistoricCurrencyAndAmount();
      activeOrHistoricCurrencyAndAmount.setCcy(CURRENCY_CODE);
      activeOrHistoricCurrencyAndAmount.setValue(bankOrderLine.getBankOrderAmount());
      directDebitTransactionInformation9.setInstdAmt(activeOrHistoricCurrencyAndAmount);

      /*
       * Direct Debit Transaction (mandatory)
       * Set of elements providing information specific to the direct debit mandate.
       */
      DirectDebitTransaction6 directDebitTransaction6 = factory.createDirectDebitTransaction6();
      directDebitTransactionInformation9.setDrctDbtTx(directDebitTransaction6);
      /*
       * Mandate Related Information (mandatory)
       * Set of elements used to provide further details related to a direct debit mandate signed between the creditor and the debtor.
       */
      MandateRelatedInformation6 mandateRelatedInformation6 =
          factory.createMandateRelatedInformation6();
      directDebitTransaction6.setMndtRltdInf(mandateRelatedInformation6);
      /*
       * Mandate Identification (mandatory)
       * Reference of the direct debit mandate that has been signed between by the debtor and the creditor.
       */
      mandateRelatedInformation6.setMndtId(receiverUmr.getUmrNumber());
      /*
       * Date of Signature (mandatory)
       * Date on which the direct debit mandate has been signed by the debtor.
       *
       * Format : YYYY-MM-DD
       */
      mandateRelatedInformation6.setDtOfSgntr(
          datatypeFactory.newXMLGregorianCalendar(
              receiverUmr
                  .getMandateSignatureDate()
                  .format(DateTimeFormatter.ofPattern(("yyyy-MM-dd")))));
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
      // mandateRelatedInformation6.setAmdmntInd(???);
      /*
       * Amendment Info Details (optional)
       * List of direct debit mandate elements that have been modified.
       */
      // AmendmentInformationDetails6 amendmentInformationDetails6 =
      // factory.createAmendmentInformationDetails6();
      // mandateRelatedInformation6.setAmdmntInfDtls(amendmentInformationDetails6);
      // amendmentInformationDetails6.setOrgnlMndtId(???);
      // amendmentInformationDetails6.setOrgnlCdtrSchmeId(???);
      // amendmentInformationDetails6.setOrgnlDbtrAcct(???);
      // amendmentInformationDetails6.setOrgnlDbtrAgt(???);
      /*
       * Electronic Signature (optional)
       * Digital signature as provided by the creditor.
       *
       * Usage : - If the direct debit is based on an electronic mandate, this data
       *           element must contain the reference of the Mandate Acceptance Report.
       *         - If the direct debit is based on a paper mandate, this data element
       *           is not allowed.
       */
      // mandateRelatedInformation6.setElctrncSgntr(???);
      /*
       * Creditor Scheme Identification
       * Creditor identification as given by his bank.
       */
      PartyIdentification32 creditorSchemeId = factory.createPartyIdentification32();
      directDebitTransaction6.setCdtrSchmeId(creditorSchemeId);
      Party6Choice party6Choice = factory.createParty6Choice();
      creditorSchemeId.setId(party6Choice);
      PersonIdentification5 personIdentification5 = factory.createPersonIdentification5();
      party6Choice.setPrvtId(personIdentification5);
      GenericPersonIdentification1 genericPersonIdentification1 =
          factory.createGenericPersonIdentification1();
      personIdentification5.getOthr().add(genericPersonIdentification1);
      genericPersonIdentification1.setId(
          Beans.get(BankPaymentConfigService.class)
              .getIcsNumber(senderCompany.getBankPaymentConfig()));
      PersonIdentificationSchemeName1Choice personIdentificationSchemeName1Choice =
          factory.createPersonIdentificationSchemeName1Choice();
      genericPersonIdentification1.setSchmeNm(personIdentificationSchemeName1Choice);
      personIdentificationSchemeName1Choice.setPrtry("SEPA");

      /*
       * Ultimate Creditor (optional)
       * Ultimate party to which an amount of money is due. Ultimate Creditor is only to be used if different from Creditor.
       */
      // directDebitTransaction6.setUltmtCdtr();

      /*
       * Debtor Agent (mandatory)
       * Financial institution servicing an account for the debtor.
       */
      BranchAndFinancialInstitutionIdentification4 branchAndFinancialInstitutionIdentification4 =
          factory.createBranchAndFinancialInstitutionIdentification4();
      FinancialInstitutionIdentification7 financialInstitutionIdentification7 =
          factory.createFinancialInstitutionIdentification7();

      fillBic(financialInstitutionIdentification7, receiverBankDetails.getBank()); // BIC

      branchAndFinancialInstitutionIdentification4.setFinInstnId(
          financialInstitutionIdentification7);
      directDebitTransactionInformation9.setDbtrAgt(branchAndFinancialInstitutionIdentification4);

      /*
       * Debtor (mandatory)
       * Party that owes an amount of money to the (ultimate) creditor.
       */
      PartyIdentification32 debtor = factory.createPartyIdentification32();
      debtor.setNm(receiverBankDetails.getOwnerName());
      directDebitTransactionInformation9.setDbtr(debtor);

      /*
       * Debtor Account (mandatory)
       * Identification of the account of the debtor to which a debit entry will be made to execute the transfer.
       */
      AccountIdentification4Choice accountIdentification4Choice =
          factory.createAccountIdentification4Choice();
      accountIdentification4Choice.setIBAN(receiverBankDetails.getIban());
      CashAccount16 cashAccount16 = factory.createCashAccount16();
      cashAccount16.setId(accountIdentification4Choice);
      directDebitTransactionInformation9.setDbtrAcct(cashAccount16);

      /*
       * Ultimate Debtor (optional)
       * Ultimate party that owes an amount of money to the (ultimate) creditor. Ultimate Debtor is only to be used if different from Debtor.
       */
      // directDebitTransactionInformation9.setUltmtDbtr(???);

      /*
       * Purpose (optional)
       * Underlying reason for the payment transaction.
       * Purpose is used by the Debtor to provide information to the Creditor, concerning thenature of the payment transaction.
       * It is not used for processing by any of the banks involved.
       */
      // Purpose2Choice purpose2Choice = factory.createPurpose2Choice();
      // directDebitTransactionInformation9.setPurp(purpose2Choice);
      /*
       * Code (mandatory)
       * Specifies the underlying reason of the payment transaction.
       */
      // purpose2Choice.setCd(???);

      /*
       * Remittance Information (optional)
       * Information that enables the matching, ie, reconciliation, of a payment with the items that the payment
       * is intended to settle, eg, commercial invoices in an account receivable system.
       *
       * Usage : Either Structured or Unstructured, but not both.
       */
      RemittanceInformation5 remittanceInformation5 = factory.createRemittanceInformation5();
      directDebitTransactionInformation9.setRmtInf(remittanceInformation5);
      /*
       * Unstructured (choice 1 of 2)
       * Information supplied to enable the matching of an entry with the items that the transfer is intended
       * to settle, eg, commercial invoices in an accounts' receivable system in an unstructured form.
       */
      remittanceInformation5
          .getUstrd()
          .add(StringTool.truncRight(bankOrderLine.getReceiverReference(), 140));

      /*
       * Structured   (choice 2 of 2)
       * Information supplied to enable the matching of an entry with the items that the transfer is intended
       * to settle, eg, commercial invoices in an accounts' receivable system in a structured form.
       */
      // StructuredRemittanceInformation7 structuredRemittanceInformation7 =
      // factory.createStructuredRemittanceInformation7();
      // remittanceInformation5.getStrd().add(structuredRemittanceInformation7);
    }
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
