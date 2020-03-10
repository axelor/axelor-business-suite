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

import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.BankOrderFileFormat;
import com.axelor.apps.bankpayment.db.BankOrderLine;
import com.axelor.apps.bankpayment.db.EbicsPartner;
import com.axelor.apps.bankpayment.db.repo.BankOrderFileFormatRepository;
import com.axelor.apps.bankpayment.db.repo.BankOrderRepository;
import com.axelor.apps.bankpayment.db.repo.EbicsPartnerRepository;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Bank;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BankDetailsRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.CallMethod;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankOrderLineService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected BankDetailsRepository bankDetailsRepo;
  protected CurrencyService currencyService;
  protected BankOrderLineOriginService bankOrderLineOriginService;

  @Inject
  public BankOrderLineService(
      BankDetailsRepository bankDetailsRepo,
      CurrencyService currencyService,
      BankOrderLineOriginService bankOrderLineOriginService) {

    this.bankDetailsRepo = bankDetailsRepo;
    this.currencyService = currencyService;
    this.bankOrderLineOriginService = bankOrderLineOriginService;
  }

  /**
   * Method to create a specific BankOrderLine for SEPA and international transfer and direct debit
   *
   * @param partner
   * @param amount
   * @param receiverReference
   * @param receiverLabel
   * @return
   * @throws AxelorException
   */
  public BankOrderLine createBankOrderLine(
      BankOrderFileFormat bankOrderFileFormat,
      Partner partner,
      BigDecimal amount,
      Currency currency,
      LocalDate bankOrderDate,
      String receiverReference,
      String receiverLabel,
      Model origin)
      throws AxelorException {

    BankDetails receiverBankDetails = bankDetailsRepo.findDefaultByPartner(partner);

    return this.createBankOrderLine(
        bankOrderFileFormat,
        null,
        partner,
        receiverBankDetails,
        amount,
        currency,
        bankOrderDate,
        receiverReference,
        receiverLabel,
        origin);
  }

  /**
   * Method to create a specific BankOrderLine for treasury transfer
   *
   * @param receiverCompany
   * @param amount
   * @param receiverReference
   * @param receiverLabel
   * @return
   * @throws AxelorException
   */
  public BankOrderLine createBankOrderLine(
      BankOrderFileFormat bankOrderFileFormat,
      Company receiverCompany,
      BigDecimal amount,
      Currency currency,
      LocalDate bankOrderDate,
      String receiverReference,
      String receiverLabel,
      Model origin)
      throws AxelorException {

    return this.createBankOrderLine(
        bankOrderFileFormat,
        receiverCompany,
        receiverCompany.getPartner(),
        receiverCompany.getDefaultBankDetails(),
        amount,
        currency,
        bankOrderDate,
        receiverReference,
        receiverLabel,
        origin);
  }

  /**
   * Generic method to create a BankOrderLine
   *
   * @param receiverCompany
   * @param partner
   * @param bankDetails
   * @param amount
   * @param receiverReference
   * @param receiverLabel
   * @return
   * @throws AxelorException
   */
  public BankOrderLine createBankOrderLine(
      BankOrderFileFormat bankOrderFileFormat,
      Company receiverCompany,
      Partner partner,
      BankDetails bankDetails,
      BigDecimal amount,
      Currency currency,
      LocalDate bankOrderDate,
      String receiverReference,
      String receiverLabel,
      Model origin)
      throws AxelorException {

    BankOrderLine bankOrderLine = new BankOrderLine();

    bankOrderLine.setReceiverCompany(receiverCompany);
    bankOrderLine.setPartner(partner);
    bankOrderLine.setReceiverBankDetails(bankDetails);
    bankOrderLine.setBankOrderAmount(amount);

    if (bankOrderFileFormat.getIsMultiCurrency()) {
      bankOrderLine.setBankOrderCurrency(currency);
    }

    if (bankOrderFileFormat.getIsMultiDate()) {
      bankOrderLine.setBankOrderDate(bankOrderDate);
    }

    bankOrderLine.setReceiverReference(receiverReference);
    bankOrderLine.setReceiverLabel(receiverLabel);

    if (origin != null) {
      bankOrderLine.addBankOrderLineOriginListItem(
          bankOrderLineOriginService.createBankOrderLineOrigin(origin));
    }

    if (bankOrderFileFormat
        .getOrderFileFormatSelect()
        .equals(BankOrderFileFormatRepository.FILE_FORMAT_PAIN_XXX_CFONB320_XCT)) {
      bankOrderLine.setBankOrderEconomicReason(bankOrderFileFormat.getBankOrderEconomicReason());
      bankOrderLine.setReceiverCountry(bankOrderFileFormat.getReceiverCountry());

      if (bankDetails != null) {
        Bank bank = bankDetails.getBank();
        if (bank != null && bank.getCountry() != null) {
          bankOrderLine.setReceiverCountry(bank.getCountry());
        }
      } else {
        throw new AxelorException(
            bankOrderLine,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_LINE_BANK_DETAILS_MISSING));
      }

      bankOrderLine.setPaymentModeSelect(bankOrderFileFormat.getPaymentModeSelect());
      bankOrderLine.setFeesImputationModeSelect(bankOrderFileFormat.getFeesImputationModeSelect());
      bankOrderLine.setReceiverAddressStr(getReceiverAddress(partner));
    }

    return bankOrderLine;
  }

  @CallMethod
  public String getReceiverAddress(Partner partner) {

    Address receiverAddress = Beans.get(PartnerService.class).getInvoicingAddress(partner);
    return receiverAddress != null ? receiverAddress.getFullName() : "";
  }

  public void checkPreconditions(BankOrderLine bankOrderLine) throws AxelorException {

    if (bankOrderLine.getBankOrder().getPartnerTypeSelect()
        == BankOrderRepository.PARTNER_TYPE_COMPANY) {
      if (bankOrderLine.getReceiverCompany() == null) {
        throw new AxelorException(
            bankOrderLine,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_LINE_COMPANY_MISSING));
      }
    }
    if (bankOrderLine.getPartner() == null) {
      throw new AxelorException(
          bankOrderLine,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_LINE_PARTNER_MISSING));
    }
    if (bankOrderLine.getReceiverBankDetails() == null) {
      throw new AxelorException(
          bankOrderLine,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_LINE_BANK_DETAILS_MISSING));
    }
    if (bankOrderLine.getBankOrderAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new AxelorException(
          bankOrderLine,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_LINE_AMOUNT_NEGATIVE));
    }
  }

  public String createDomainForBankDetails(BankOrderLine bankOrderLine, BankOrder bankOrder) {
    String domain = "";
    String bankDetailsIds = "";

    if ((bankOrderLine == null) || (bankOrder == null)) {
      return domain;
    }

    // the case where the bank order is for a company
    if (bankOrder.getPartnerTypeSelect() == BankOrderRepository.PARTNER_TYPE_COMPANY) {
      if (bankOrderLine.getReceiverCompany() != null) {

        bankDetailsIds =
            StringTool.getIdListString(bankOrderLine.getReceiverCompany().getBankDetailsSet());

        if (bankOrderLine.getReceiverCompany().getDefaultBankDetails() != null) {
          bankDetailsIds += bankDetailsIds.equals("") ? "" : ",";
          bankDetailsIds +=
              bankOrderLine.getReceiverCompany().getDefaultBankDetails().getId().toString();
        }
      }
    }

    // case where the bank order is for a partner
    else if (bankOrderLine.getPartner() != null) {
      bankDetailsIds = StringTool.getIdListString(bankOrderLine.getPartner().getBankDetailsList());
    }

    if (bankDetailsIds.equals("")) {
      return domain = "";
    }

    domain = "self.id IN(" + bankDetailsIds + ")";

    // filter the result on active bank details
    domain += " AND self.active = true";

    // filter on the result from bankPartner if the option is active.
    EbicsPartner ebicsPartner =
        Beans.get(EbicsPartnerRepository.class)
            .all()
            .filter("? MEMBER OF self.bankDetailsSet", bankOrder.getSenderBankDetails())
            .fetchOne();

    if (ebicsPartnerIsFiltering(ebicsPartner, bankOrder.getOrderTypeSelect())) {
      domain +=
          " AND self.id IN ("
              + StringTool.getIdListString(ebicsPartner.getReceiverBankDetailsSet())
              + ")";
    }

    // filter on the bank details identifier type from the bank order file format
    if (bankOrder.getBankOrderFileFormat() != null) {
      String acceptedIdentifiers = bankOrder.getBankOrderFileFormat().getBankDetailsTypeSelect();
      if (acceptedIdentifiers != null && !acceptedIdentifiers.equals("")) {
        domain += " AND self.bank.bankDetailsTypeSelect IN (" + acceptedIdentifiers + ")";
      }
    }
    // filter on the currency if it is set in bank order and in the bankdetails
    // and if the bankOrder is not multicurrency
    // and if the partner type select is a company
    Currency currency = bankOrder.getBankOrderCurrency();
    if (!bankOrder.getIsMultiCurrency()
        && currency != null
        && bankOrder.getPartnerTypeSelect() == BankOrderRepository.PARTNER_TYPE_COMPANY) {
      String fileFormatCurrencyId = currency.getId().toString();
      domain += " AND (self.currency IS NULL OR self.currency.id = " + fileFormatCurrencyId + ")";
    }
    return domain;
  }

  /**
   * Search the default bank detail in receiver company if partner type select is company. If not
   * company, search default in partner of bank order line. If no default bank detail, return the
   * alone bank detail present if is active in the partner of bank order line.
   *
   * @param bankOrderLine The bank order line
   * @param bankOrder The bank order
   * @return default bank detail if present otherwise the unique bank detail if active
   */
  public BankDetails getDefaultBankDetails(BankOrderLine bankOrderLine, BankOrder bankOrder) {
    BankDetails candidateBankDetails = null;
    if (bankOrder.getPartnerTypeSelect() == BankOrderRepository.PARTNER_TYPE_COMPANY
        && bankOrderLine.getReceiverCompany() != null) {
      candidateBankDetails = bankOrderLine.getReceiverCompany().getDefaultBankDetails();
      if (candidateBankDetails == null) {
        for (BankDetails bankDetails : bankOrderLine.getReceiverCompany().getBankDetailsSet()) {
          if (candidateBankDetails != null && bankDetails.getActive()) {
            candidateBankDetails = null;
            break;
          } else if (bankDetails.getActive()) {
            candidateBankDetails = bankDetails;
          }
        }
      }
    } else if (bankOrder.getPartnerTypeSelect() != BankOrderRepository.PARTNER_TYPE_COMPANY
        && bankOrderLine.getPartner() != null) {
      candidateBankDetails = bankDetailsRepo.findDefaultByPartner(bankOrderLine.getPartner());
      if (candidateBankDetails == null) {
        List<BankDetails> bankDetailsList =
            bankDetailsRepo.findActivesByPartner(bankOrderLine.getPartner(), true).fetch();
        if (bankDetailsList.size() == 1) {
          candidateBankDetails = bankDetailsList.get(0);
        }
      }
    }

    try {
      checkBankDetails(candidateBankDetails, bankOrder);
    } catch (AxelorException e) {
      candidateBankDetails = null;
    }

    return candidateBankDetails;
  }

  public void checkBankDetails(BankDetails bankDetails, BankOrder bankOrder)
      throws AxelorException {
    if (bankDetails == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_LINE_BANK_DETAILS_MISSING));
    }

    // check if the bank details is active
    if (!bankDetails.getActive()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_LINE_BANK_DETAILS_NOT_ACTIVE));
    }

    // filter on the result from bankPartner if the option is active.
    EbicsPartner ebicsPartner =
        Beans.get(EbicsPartnerRepository.class)
            .all()
            .filter("? MEMBER OF self.bankDetailsSet", bankOrder.getSenderBankDetails())
            .fetchOne();

    if (ebicsPartnerIsFiltering(ebicsPartner, bankOrder.getOrderTypeSelect())) {

      if (!ebicsPartner.getReceiverBankDetailsSet().contains(bankDetails)) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_LINE_BANK_DETAILS_FORBIDDEN));
      }
    }

    // filter on the bank details identifier type from the bank order file format
    if (bankOrder.getBankOrderFileFormat() != null) {
      if (!Beans.get(BankOrderService.class)
          .checkBankDetailsTypeCompatible(bankDetails, bankOrder.getBankOrderFileFormat())) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_LINE_BANK_DETAILS_TYPE_NOT_COMPATIBLE));
      }
    }

    // filter on the currency if the bank order is not multicurrency
    // and if the partner type select is a company
    if (!bankOrder.getIsMultiCurrency()
        && bankOrder.getBankOrderCurrency() != null
        && bankOrder.getPartnerTypeSelect() == BankOrderRepository.PARTNER_TYPE_COMPANY) {
      if (!Beans.get(BankOrderService.class)
          .checkBankDetailsCurrencyCompatible(bankDetails, bankOrder)) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_LINE_BANK_DETAILS_CURRENCY_NOT_COMPATIBLE));
      }
    }
  }

  protected boolean ebicsPartnerIsFiltering(EbicsPartner ebicsPartner, int orderType) {
    return (ebicsPartner != null)
        && (ebicsPartner.getFilterReceiverBD())
        && (ebicsPartner.getReceiverBankDetailsSet() != null)
        && (!ebicsPartner.getReceiverBankDetailsSet().isEmpty())
        && (ebicsPartner.getOrderTypeSelect() == orderType);
  }

  public BigDecimal computeCompanyCurrencyAmount(BankOrder bankOrder, BankOrderLine bankOrderLine)
      throws AxelorException {

    LocalDate bankOrderDate = bankOrder.getBankOrderDate();

    if (bankOrder.getIsMultiDate()) {
      bankOrderDate = bankOrderLine.getBankOrderDate();
    }

    Currency bankOrderCurrency = bankOrder.getBankOrderCurrency();

    if (bankOrder.getIsMultiCurrency()) {
      bankOrderCurrency = bankOrderLine.getBankOrderCurrency();
    }

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            bankOrderCurrency,
            bankOrder.getCompanyCurrency(),
            bankOrderLine.getBankOrderAmount(),
            bankOrderDate)
        .setScale(2, RoundingMode.HALF_UP); // TODO Manage the number of decimal for currency
  }
}
