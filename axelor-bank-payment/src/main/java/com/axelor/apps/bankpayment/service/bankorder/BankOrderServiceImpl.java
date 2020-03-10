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
package com.axelor.apps.bankpayment.service.bankorder;

import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCancelService;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.BankOrderFileFormat;
import com.axelor.apps.bankpayment.db.BankOrderLine;
import com.axelor.apps.bankpayment.db.BankPaymentConfig;
import com.axelor.apps.bankpayment.db.EbicsPartner;
import com.axelor.apps.bankpayment.db.EbicsUser;
import com.axelor.apps.bankpayment.db.repo.BankOrderFileFormatRepository;
import com.axelor.apps.bankpayment.db.repo.BankOrderRepository;
import com.axelor.apps.bankpayment.db.repo.EbicsPartnerRepository;
import com.axelor.apps.bankpayment.ebics.service.EbicsService;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.apps.bankpayment.service.app.AppBankPaymentService;
import com.axelor.apps.bankpayment.service.bankorder.file.directdebit.BankOrderFile00800101Service;
import com.axelor.apps.bankpayment.service.bankorder.file.directdebit.BankOrderFile00800102Service;
import com.axelor.apps.bankpayment.service.bankorder.file.directdebit.BankOrderFile008Service;
import com.axelor.apps.bankpayment.service.bankorder.file.transfer.BankOrderFile00100102Service;
import com.axelor.apps.bankpayment.service.bankorder.file.transfer.BankOrderFile00100103Service;
import com.axelor.apps.bankpayment.service.bankorder.file.transfer.BankOrderFileAFB160ICTService;
import com.axelor.apps.bankpayment.service.bankorder.file.transfer.BankOrderFileAFB320XCTService;
import com.axelor.apps.bankpayment.service.config.BankPaymentConfigService;
import com.axelor.apps.bankpayment.service.invoice.payment.InvoicePaymentValidateServiceBankPayImpl;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Sequence;
import com.axelor.apps.base.service.BankDetailsService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.mail.db.repo.MailFollowerRepository;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankOrderServiceImpl implements BankOrderService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected BankOrderRepository bankOrderRepo;
  protected InvoicePaymentRepository invoicePaymentRepo;
  protected BankOrderLineService bankOrderLineService;
  protected EbicsService ebicsService;
  protected InvoicePaymentCancelService invoicePaymentCancelService;
  protected BankPaymentConfigService bankPaymentConfigService;
  protected SequenceService sequenceService;
  protected BankOrderLineOriginService bankOrderLineOriginService;
  protected BankOrderMoveService bankOrderMoveService;

  @Inject
  public BankOrderServiceImpl(
      BankOrderRepository bankOrderRepo,
      InvoicePaymentRepository invoicePaymentRepo,
      BankOrderLineService bankOrderLineService,
      EbicsService ebicsService,
      InvoicePaymentCancelService invoicePaymentCancelService,
      BankPaymentConfigService bankPaymentConfigService,
      SequenceService sequenceService,
      BankOrderLineOriginService bankOrderLineOriginService,
      BankOrderMoveService bankOrderMoveService) {

    this.bankOrderRepo = bankOrderRepo;
    this.invoicePaymentRepo = invoicePaymentRepo;
    this.bankOrderLineService = bankOrderLineService;
    this.ebicsService = ebicsService;
    this.invoicePaymentCancelService = invoicePaymentCancelService;
    this.bankPaymentConfigService = bankPaymentConfigService;
    this.sequenceService = sequenceService;
    this.bankOrderLineOriginService = bankOrderLineOriginService;
    this.bankOrderMoveService = bankOrderMoveService;
  }

  public void checkPreconditions(BankOrder bankOrder) throws AxelorException {

    LocalDate brankOrderDate = bankOrder.getBankOrderDate();

    if (brankOrderDate != null) {
      if (brankOrderDate.isBefore(LocalDate.now())) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_DATE));
      }
    } else {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_DATE_MISSING));
    }

    if (bankOrder.getOrderTypeSelect() == 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_TYPE_MISSING));
    }
    if (bankOrder.getPartnerTypeSelect() == 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_PARTNER_TYPE_MISSING));
    }
    if (bankOrder.getPaymentMode() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_PAYMENT_MODE_MISSING));
    }
    if (bankOrder.getSenderCompany() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_COMPANY_MISSING));
    }
    if (bankOrder.getSenderBankDetails() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_BANK_DETAILS_MISSING));
    }
    if (!bankOrder.getIsMultiCurrency() && bankOrder.getBankOrderCurrency() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_CURRENCY_MISSING));
    }
    if (bankOrder.getSignatoryUser() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_SIGNATORY_MISSING));
    }
  }

  @Override
  public BigDecimal computeBankOrderTotalAmount(BankOrder bankOrder) throws AxelorException {

    BigDecimal bankOrderTotalAmount = BigDecimal.ZERO;

    List<BankOrderLine> bankOrderLines = bankOrder.getBankOrderLineList();
    if (bankOrderLines != null) {
      for (BankOrderLine bankOrderLine : bankOrderLines) {
        BigDecimal amount = bankOrderLine.getBankOrderAmount();
        if (amount != null) {
          bankOrderTotalAmount = bankOrderTotalAmount.add(amount);
        }
      }
    }
    return bankOrderTotalAmount;
  }

  @Override
  public BigDecimal computeCompanyCurrencyTotalAmount(BankOrder bankOrder) throws AxelorException {

    BigDecimal companyCurrencyTotalAmount = BigDecimal.ZERO;

    List<BankOrderLine> bankOrderLines = bankOrder.getBankOrderLineList();
    if (bankOrderLines != null) {
      for (BankOrderLine bankOrderLine : bankOrderLines) {
        BigDecimal amount = bankOrderLine.getCompanyCurrencyAmount();
        if (amount != null) {
          companyCurrencyTotalAmount = companyCurrencyTotalAmount.add(amount);
        }
      }
    }
    return companyCurrencyTotalAmount;
  }

  @Override
  public void updateTotalAmounts(BankOrder bankOrder) throws AxelorException {
    if (bankOrder.getOrderTypeSelect().equals(BankOrderRepository.ORDER_TYPE_SEND_BANK_ORDER)) {
      bankOrder.setArithmeticTotal(bankOrder.getBankOrderTotalAmount());
    } else {
      bankOrder.setArithmeticTotal(this.computeBankOrderTotalAmount(bankOrder));
    }

    if (!bankOrder.getIsMultiCurrency()) {
      bankOrder.setBankOrderTotalAmount(bankOrder.getArithmeticTotal());
    }

    bankOrder.setCompanyCurrencyTotalAmount(this.computeCompanyCurrencyTotalAmount(bankOrder));
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public BankOrder generateSequence(BankOrder bankOrder) throws AxelorException {
    if (bankOrder.getBankOrderSeq() == null) {

      Sequence sequence = getSequence(bankOrder);
      setBankOrderSeq(bankOrder, sequence);
      bankOrderRepo.save(bankOrder);
    }
    return bankOrder;
  }

  @Override
  public void checkLines(BankOrder bankOrder) throws AxelorException {
    List<BankOrderLine> bankOrderLines = bankOrder.getBankOrderLineList();
    if (bankOrderLines.isEmpty()) {
      throw new AxelorException(
          bankOrder,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_LINES_MISSING));
    } else {
      validateBankOrderLines(
          bankOrderLines, bankOrder.getOrderTypeSelect(), bankOrder.getArithmeticTotal());
    }
  }

  public void validateBankOrderLines(
      List<BankOrderLine> bankOrderLines, int orderType, BigDecimal arithmeticTotal)
      throws AxelorException {
    BigDecimal totalAmount = BigDecimal.ZERO;
    for (BankOrderLine bankOrderLine : bankOrderLines) {

      bankOrderLineService.checkPreconditions(bankOrderLine);
      totalAmount = totalAmount.add(bankOrderLine.getBankOrderAmount());
      bankOrderLineService.checkBankDetails(
          bankOrderLine.getReceiverBankDetails(), bankOrderLine.getBankOrder());
    }
    if (!totalAmount.equals(arithmeticTotal)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_LINE_TOTAL_AMOUNT_INVALID));
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void validatePayment(BankOrder bankOrder) throws AxelorException {

    List<InvoicePayment> invoicePaymentList = invoicePaymentRepo.findByBankOrder(bankOrder).fetch();

    InvoicePaymentValidateServiceBankPayImpl invoicePaymentValidateServiceBankPayImpl =
        Beans.get(InvoicePaymentValidateServiceBankPayImpl.class);

    for (InvoicePayment invoicePayment : invoicePaymentList) {
      if (invoicePayment != null
          && invoicePayment.getStatusSelect() != InvoicePaymentRepository.STATUS_VALIDATED
          && invoicePayment.getInvoice() != null) {

        if (bankOrderLineOriginService.existBankOrderLineOrigin(
            bankOrder, invoicePayment.getInvoice())) {

          invoicePaymentValidateServiceBankPayImpl.validateFromBankOrder(invoicePayment, true);
        }
      }
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void cancelPayment(BankOrder bankOrder) throws AxelorException {

    List<InvoicePayment> invoicePaymentList = invoicePaymentRepo.findByBankOrder(bankOrder).fetch();

    for (InvoicePayment invoicePayment : invoicePaymentList) {
      if (invoicePayment != null
          && invoicePayment.getStatusSelect() != InvoicePaymentRepository.STATUS_CANCELED) {
        invoicePaymentCancelService.cancel(invoicePayment);
      }
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void confirm(BankOrder bankOrder)
      throws AxelorException, JAXBException, IOException, DatatypeConfigurationException {
    checkBankDetails(bankOrder.getSenderBankDetails(), bankOrder);

    if (bankOrder.getGeneratedMetaFile() == null) {
      checkLines(bankOrder);
    }

    setNbOfLines(bankOrder);

    setSequenceOnBankOrderLines(bankOrder);

    generateFile(bankOrder);

    if (Beans.get(AppBankPaymentService.class).getAppBankPayment().getEnableEbicsModule()) {

      bankOrder.setConfirmationDateTime(
          Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime());
      bankOrder.setStatusSelect(BankOrderRepository.STATUS_AWAITING_SIGNATURE);
      makeEbicsUserFollow(bankOrder);

      bankOrderRepo.save(bankOrder);
    } else {
      validate(bankOrder);
    }
  }

  @Override
  @Transactional
  public void sign(BankOrder bankOrder) {

    // TODO

  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void validate(BankOrder bankOrder) throws AxelorException {

    bankOrder.setValidationDateTime(LocalDateTime.now());

    bankOrder.setStatusSelect(BankOrderRepository.STATUS_VALIDATED);

    if (bankPaymentConfigService
        .getBankPaymentConfig(bankOrder.getSenderCompany())
        .getGenerateMoveOnBankOrderValidation()) {
      bankOrderMoveService.generateMoves(bankOrder);
      validatePayment(bankOrder);
    }

    bankOrderRepo.save(bankOrder);
  }

  @Override
  public void realize(BankOrder bankOrder) throws AxelorException {

    if (Beans.get(AppBankPaymentService.class).getAppBankPayment().getEnableEbicsModule()) {
      if (bankOrder.getSignatoryEbicsUser() == null) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.EBICS_MISSING_SIGNATORY_EBICS_USER));
      }
      if (bankOrder.getSignatoryEbicsUser().getEbicsPartner().getTransportEbicsUser() == null) {
        throw new AxelorException(
            bankOrder.getSignatoryEbicsUser().getEbicsPartner(),
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.EBICS_MISSING_USER_TRANSPORT));
      }

      sendBankOrderFile(bankOrder);
    }
    realizeBankOrder(bankOrder);
  }

  protected void sendBankOrderFile(BankOrder bankOrder) throws AxelorException {

    File dataFileToSend = null;
    File signatureFileToSend = null;

    if (bankOrder.getSignatoryEbicsUser().getEbicsPartner().getEbicsTypeSelect()
        == EbicsPartnerRepository.EBICS_TYPE_TS) {
      if (bankOrder.getSignedMetaFile() == null) {
        throw new AxelorException(
            I18n.get(IExceptionMessage.BANK_ORDER_NOT_PROPERLY_SIGNED),
            TraceBackRepository.CATEGORY_NO_VALUE);
      }

      signatureFileToSend = MetaFiles.getPath(bankOrder.getSignedMetaFile()).toFile();
    }
    dataFileToSend = MetaFiles.getPath(bankOrder.getGeneratedMetaFile()).toFile();

    sendFile(bankOrder, dataFileToSend, signatureFileToSend);
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void realizeBankOrder(BankOrder bankOrder) throws AxelorException {

    AppBaseService appBaseService = Beans.get(AppBaseService.class);

    if (!bankPaymentConfigService
        .getBankPaymentConfig(bankOrder.getSenderCompany())
        .getGenerateMoveOnBankOrderValidation()) {
      bankOrderMoveService.generateMoves(bankOrder);
      validatePayment(bankOrder);
    }

    bankOrder.setSendingDateTime(appBaseService.getTodayDateTime().toLocalDateTime());
    bankOrder.setStatusSelect(BankOrderRepository.STATUS_CARRIED_OUT);

    if (Beans.get(AppBankPaymentService.class).getAppBankPayment().getEnableEbicsModule()) {
      bankOrder.setTestMode(bankOrder.getSignatoryEbicsUser().getEbicsPartner().getTestMode());
    }

    bankOrderRepo.save(bankOrder);
  }

  protected void sendFile(BankOrder bankOrder, File dataFileToSend, File signatureFileToSend)
      throws AxelorException {

    PaymentMode paymentMode = bankOrder.getPaymentMode();

    if (paymentMode != null && !paymentMode.getAutomaticTransmission()) {
      return;
    }

    EbicsUser signatoryEbicsUser = bankOrder.getSignatoryEbicsUser();

    ebicsService.sendFULRequest(
        signatoryEbicsUser.getEbicsPartner().getTransportEbicsUser(),
        signatoryEbicsUser,
        null,
        dataFileToSend,
        bankOrder.getBankOrderFileFormat(),
        signatureFileToSend);
  }

  @Override
  public void setSequenceOnBankOrderLines(BankOrder bankOrder) {

    if (bankOrder.getBankOrderLineList() == null) {
      return;
    }

    String bankOrderSeq = bankOrder.getBankOrderSeq();

    int counter = 1;

    for (BankOrderLine bankOrderLine : bankOrder.getBankOrderLineList()) {

      bankOrderLine.setCounter(counter);
      bankOrderLine.setSequence(bankOrderSeq + "-" + Integer.toString(counter++));
    }
  }

  protected void setNbOfLines(BankOrder bankOrder) {

    if (bankOrder.getBankOrderLineList() == null) {
      return;
    }

    bankOrder.setNbOfLines(bankOrder.getBankOrderLineList().size());
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void cancelBankOrder(BankOrder bankOrder) throws AxelorException {

    bankOrder.setStatusSelect(BankOrderRepository.STATUS_CANCELED);

    this.cancelPayment(bankOrder);

    bankOrderRepo.save(bankOrder);
  }

  @Override
  @Transactional
  public EbicsUser getDefaultEbicsUserFromBankDetails(BankDetails bankDetails) {
    EbicsPartner ebicsPartner =
        Beans.get(EbicsPartnerRepository.class)
            .all()
            .filter("? MEMBER OF self.bankDetailsSet", bankDetails)
            .fetchOne();
    if (ebicsPartner != null) {
      return ebicsPartner.getDefaultSignatoryEbicsUser();
    } else {
      return null;
    }
  }

  @Override
  public String createDomainForBankDetails(BankOrder bankOrder) {

    String domain =
        Beans.get(BankDetailsService.class)
            .getActiveCompanyBankDetails(bankOrder.getSenderCompany());

    // filter on the bank details identifier type from the bank order file
    // format
    if (bankOrder.getBankOrderFileFormat() != null) {
      String acceptedIdentifiers = bankOrder.getBankOrderFileFormat().getBankDetailsTypeSelect();
      if (acceptedIdentifiers != null && !acceptedIdentifiers.equals("")) {
        domain += " AND self.bank.bankDetailsTypeSelect IN (" + acceptedIdentifiers + ")";
      }
    }

    // filter on the currency if it is set in file format and in the bankdetails
    Currency currency = bankOrder.getBankOrderCurrency();
    if (currency != null
        && !bankOrder.getBankOrderFileFormat().getAllowOrderCurrDiffFromBankDetails()) {
      String fileFormatCurrencyId = currency.getId().toString();
      domain += " AND (self.currency IS NULL OR self.currency.id = " + fileFormatCurrencyId + ")";
    }
    return domain;
  }

  @Override
  public BankDetails getDefaultBankDetails(BankOrder bankOrder) {
    BankDetails candidateBankDetails;
    if (bankOrder.getSenderCompany() == null) {
      return null;
    }

    candidateBankDetails = bankOrder.getSenderCompany().getDefaultBankDetails();

    try {
      this.checkBankDetails(candidateBankDetails, bankOrder);
    } catch (AxelorException e) {
      return null;
    }

    return candidateBankDetails;
  }

  @Override
  public void checkBankDetails(BankDetails bankDetails, BankOrder bankOrder)
      throws AxelorException {
    if (bankDetails == null) {
      throw new AxelorException(
          bankOrder,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_BANK_DETAILS_MISSING));
    }
    if (!bankDetails.getActive()) {
      throw new AxelorException(
          bankOrder,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_BANK_DETAILS_NOT_ACTIVE));
    }

    if (bankOrder.getBankOrderFileFormat() != null) {
      if (!this.checkBankDetailsTypeCompatible(bankDetails, bankOrder.getBankOrderFileFormat())) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_BANK_DETAILS_TYPE_NOT_COMPATIBLE));
      }
      if (!bankOrder.getBankOrderFileFormat().getAllowOrderCurrDiffFromBankDetails()
          && !this.checkBankDetailsCurrencyCompatible(bankDetails, bankOrder)) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_BANK_DETAILS_CURRENCY_NOT_COMPATIBLE));
      }
    }

    if (bankOrder.getBankOrderFileFormat() != null
        && bankOrder.getBankOrderFileFormat().getAllowOrderCurrDiffFromBankDetails()
        && bankDetails.getCurrency() == null) {
      throw new AxelorException(
          I18n.get(IExceptionMessage.BANK_ORDER_BANK_DETAILS_MISSING_CURRENCY),
          TraceBackRepository.CATEGORY_MISSING_FIELD);
    }
  }

  @Override
  public boolean checkBankDetailsTypeCompatible(
      BankDetails bankDetails, BankOrderFileFormat bankOrderFileFormat) {
    // filter on the bank details identifier type from the bank order file
    // format
    String acceptedIdentifiers = bankOrderFileFormat.getBankDetailsTypeSelect();
    if (acceptedIdentifiers != null && !acceptedIdentifiers.equals("")) {
      String[] identifiers = acceptedIdentifiers.replaceAll("\\s", "").split(",");
      int i = 0;
      while (i < identifiers.length
          && bankDetails.getBank().getBankDetailsTypeSelect() != Integer.parseInt(identifiers[i])) {
        i++;
      }
      if (i == identifiers.length) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean checkBankDetailsCurrencyCompatible(BankDetails bankDetails, BankOrder bankOrder) {
    // filter on the currency if it is set in file format
    if (bankOrder.getBankOrderCurrency() != null) {
      if (bankDetails.getCurrency() != null
          && bankDetails.getCurrency() != bankOrder.getBankOrderCurrency()) {
        return false;
      }
    }

    return true;
  }

  @Override
  public File generateFile(BankOrder bankOrder)
      throws JAXBException, IOException, AxelorException, DatatypeConfigurationException {

    if (bankOrder.getBankOrderLineList() == null || bankOrder.getBankOrderLineList().isEmpty()) {
      return null;
    }

    bankOrder.setFileGenerationDateTime(LocalDateTime.now());

    BankOrderFileFormat bankOrderFileFormat = bankOrder.getBankOrderFileFormat();

    File file = null;

    switch (bankOrderFileFormat.getOrderFileFormatSelect()) {
      case BankOrderFileFormatRepository.FILE_FORMAT_PAIN_001_001_02_SCT:
        file = new BankOrderFile00100102Service(bankOrder).generateFile();
        break;

      case BankOrderFileFormatRepository.FILE_FORMAT_PAIN_001_001_03_SCT:
        file = new BankOrderFile00100103Service(bankOrder).generateFile();
        break;

      case BankOrderFileFormatRepository.FILE_FORMAT_PAIN_XXX_CFONB320_XCT:
        file = new BankOrderFileAFB320XCTService(bankOrder).generateFile();
        break;

      case BankOrderFileFormatRepository.FILE_FORMAT_PAIN_XXX_CFONB160_ICT:
        file = new BankOrderFileAFB160ICTService(bankOrder).generateFile();
        break;

      case BankOrderFileFormatRepository.FILE_FORMAT_PAIN_008_001_01_SDD:
        file =
            new BankOrderFile00800101Service(bankOrder, BankOrderFile008Service.SEPA_TYPE_CORE)
                .generateFile();
        break;

      case BankOrderFileFormatRepository.FILE_FORMAT_PAIN_008_001_01_SBB:
        file =
            new BankOrderFile00800101Service(bankOrder, BankOrderFile008Service.SEPA_TYPE_SBB)
                .generateFile();
        break;

      case BankOrderFileFormatRepository.FILE_FORMAT_PAIN_008_001_02_SDD:
        file =
            new BankOrderFile00800102Service(bankOrder, BankOrderFile008Service.SEPA_TYPE_CORE)
                .generateFile();
        break;

      case BankOrderFileFormatRepository.FILE_FORMAT_PAIN_008_001_02_SBB:
        file =
            new BankOrderFile00800102Service(bankOrder, BankOrderFile008Service.SEPA_TYPE_SBB)
                .generateFile();
        break;

      default:
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_FILE_UNKNOWN_FORMAT));
    }

    if (file == null) {
      throw new AxelorException(
          bankOrder,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_ISSUE_DURING_FILE_GENERATION),
          bankOrder.getBankOrderSeq());
    }

    MetaFiles metaFiles = Beans.get(MetaFiles.class);

    try (InputStream is = new FileInputStream(file)) {
      metaFiles.attach(is, file.getName(), bankOrder);
      bankOrder.setGeneratedMetaFile(metaFiles.upload(file));
    }

    return file;
  }

  protected Sequence getSequence(BankOrder bankOrder) throws AxelorException {
    BankPaymentConfig bankPaymentConfig =
        Beans.get(BankPaymentConfigService.class)
            .getBankPaymentConfig(bankOrder.getSenderCompany());

    switch (bankOrder.getOrderTypeSelect()) {
      case BankOrderRepository.ORDER_TYPE_SEPA_DIRECT_DEBIT:
        return bankPaymentConfigService.getSepaDirectDebitSequence(bankPaymentConfig);

      case BankOrderRepository.ORDER_TYPE_SEPA_CREDIT_TRANSFER:
        return bankPaymentConfigService.getSepaCreditTransSequence(bankPaymentConfig);

      case BankOrderRepository.ORDER_TYPE_INTERNATIONAL_DIRECT_DEBIT:
        return bankPaymentConfigService.getIntDirectDebitSequence(bankPaymentConfig);

      case BankOrderRepository.ORDER_TYPE_INTERNATIONAL_CREDIT_TRANSFER:
        return bankPaymentConfigService.getIntCreditTransSequence(bankPaymentConfig);

      case BankOrderRepository.ORDER_TYPE_NATIONAL_TREASURY_TRANSFER:
        return bankPaymentConfigService.getNatTreasuryTransSequence(bankPaymentConfig);

      case BankOrderRepository.ORDER_TYPE_INTERNATIONAL_TREASURY_TRANSFER:
        return bankPaymentConfigService.getIntTreasuryTransSequence(bankPaymentConfig);

      default:
        return bankPaymentConfigService.getOtherBankOrderSequence(bankPaymentConfig);
    }
  }

  protected void setBankOrderSeq(BankOrder bankOrder, Sequence sequence) throws AxelorException {
    bankOrder.setBankOrderSeq(
        (sequenceService.getSequenceNumber(sequence, bankOrder.getBankOrderDate())));

    if (bankOrder.getBankOrderSeq() != null) {
      return;
    }

    throw new AxelorException(
        bankOrder,
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        I18n.get(IExceptionMessage.BANK_ORDER_COMPANY_NO_SEQUENCE),
        bankOrder.getSenderCompany().getName());
  }

  /**
   * The signatory ebics user will follow the bank order record
   *
   * @param bankOrder
   */
  protected void makeEbicsUserFollow(BankOrder bankOrder) {
    EbicsUser ebicsUser = bankOrder.getSignatoryEbicsUser();
    if (ebicsUser != null) {
      User signatoryUser = ebicsUser.getAssociatedUser();
      Beans.get(MailFollowerRepository.class).follow(bankOrder, signatoryUser);
    }
  }

  @Override
  public void resetReceivers(BankOrder bankOrder) {
    if (ObjectUtils.isEmpty(bankOrder.getBankOrderLineList())) {
      return;
    }

    resetPartners(bankOrder);
    resetBankDetails(bankOrder);
  }

  protected void resetPartners(BankOrder bankOrder) {
    if (bankOrder.getPartnerTypeSelect() == BankOrderRepository.PARTNER_TYPE_COMPANY) {
      for (BankOrderLine bankOrderLine : bankOrder.getBankOrderLineList()) {
        bankOrderLine.setPartner(null);
      }

      return;
    }

    for (BankOrderLine bankOrderLine : bankOrder.getBankOrderLineList()) {
      bankOrderLine.setReceiverCompany(null);
      Partner partner = bankOrderLine.getPartner();

      if (partner == null) {
        continue;
      }

      boolean keep;

      switch (bankOrder.getPartnerTypeSelect()) {
        case BankOrderRepository.PARTNER_TYPE_SUPPLIER:
          keep = partner.getIsSupplier();
          break;
        case BankOrderRepository.PARTNER_TYPE_EMPLOYEE:
          keep = partner.getIsEmployee();
          break;
        case BankOrderRepository.PARTNER_TYPE_CUSTOMER:
          keep = partner.getIsCustomer();
          break;
        default:
          keep = false;
      }

      if (!keep) {
        bankOrderLine.setPartner(null);
      }
    }
  }

  protected void resetBankDetails(BankOrder bankOrder) {
    for (BankOrderLine bankOrderLine : bankOrder.getBankOrderLineList()) {
      if (bankOrderLine.getReceiverBankDetails() == null) {
        continue;
      }

      Collection<BankDetails> bankDetailsCollection;

      if (bankOrderLine.getReceiverCompany() != null) {
        bankDetailsCollection = bankOrderLine.getReceiverCompany().getBankDetailsSet();
      } else if (bankOrderLine.getPartner() != null) {
        bankDetailsCollection = bankOrderLine.getPartner().getBankDetailsList();
      } else {
        bankDetailsCollection = Collections.emptyList();
      }

      if (ObjectUtils.isEmpty(bankDetailsCollection)
          || !bankDetailsCollection.contains(bankOrderLine.getReceiverBankDetails())) {
        bankOrderLine.setReceiverBankDetails(null);
      }
    }
  }

  @Override
  public ActionViewBuilder buildBankOrderLineView(
      String gridViewName, String formViewName, String viewDomain) {
    ActionViewBuilder actionViewBuilder =
        ActionView.define(I18n.get("Bank Order Lines"))
            .model(BankOrderLine.class.getName())
            .add("grid", gridViewName)
            .add("form", formViewName)
            .domain(viewDomain);
    return actionViewBuilder;
  }
}
