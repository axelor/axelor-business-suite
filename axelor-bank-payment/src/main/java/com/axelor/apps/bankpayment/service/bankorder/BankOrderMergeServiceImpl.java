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

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.PaymentScheduleLine;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.PaymentScheduleLineRepository;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.BankOrderLine;
import com.axelor.apps.bankpayment.db.BankOrderLineOrigin;
import com.axelor.apps.bankpayment.db.repo.BankOrderLineOriginRepository;
import com.axelor.apps.bankpayment.db.repo.BankOrderRepository;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.tool.StringTool;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankOrderMergeServiceImpl implements BankOrderMergeService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected BankOrderRepository bankOrderRepo;
  protected InvoicePaymentRepository invoicePaymentRepo;
  protected BankOrderService bankOrderService;
  protected InvoiceRepository invoiceRepository;
  protected PaymentScheduleLineRepository paymentScheduleLineRepository;

  @Inject
  public BankOrderMergeServiceImpl(
      BankOrderRepository bankOrderRepo,
      InvoicePaymentRepository invoicePaymentRepo,
      BankOrderService bankOrderService,
      InvoiceRepository invoiceRepository,
      PaymentScheduleLineRepository paymentScheduleLineRepository) {

    this.bankOrderRepo = bankOrderRepo;
    this.invoicePaymentRepo = invoicePaymentRepo;
    this.bankOrderService = bankOrderService;
    this.invoiceRepository = invoiceRepository;
    this.paymentScheduleLineRepository = paymentScheduleLineRepository;
  }

  @Transactional(rollbackOn = {Exception.class})
  public BankOrder mergeBankOrders(Collection<BankOrder> bankOrders) throws AxelorException {

    if (bankOrders == null || bankOrders.size() <= 1) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          IExceptionMessage.BANK_ORDER_MERGE_AT_LEAST_TWO_BANK_ORDERS);
    }

    this.checkSameElements(bankOrders);

    BankOrder bankOrder = bankOrders.iterator().next();

    bankOrders.remove(bankOrder);

    bankOrder.setSenderLabel(null);
    bankOrder.setSenderReference(null);
    bankOrder.setBankOrderDate(Beans.get(AppBaseService.class).getTodayDate());
    bankOrder.setSignatoryUser(null);
    bankOrder.setSignatoryEbicsUser(null);

    PaymentMode paymentMode = bankOrder.getPaymentMode();

    for (BankOrderLine bankOrderLine : this.getAllBankOrderLineList(bankOrders)) {

      bankOrder.addBankOrderLineListItem(bankOrderLine);
    }

    bankOrderRepo.save(bankOrder);

    for (BankOrder bankOrderToRemove : bankOrders) {

      bankOrderToRemove = bankOrderRepo.find(bankOrderToRemove.getId());

      List<InvoicePayment> invoicePaymentList =
          invoicePaymentRepo.findByBankOrder(bankOrderToRemove).fetch();

      for (InvoicePayment invoicePayment : invoicePaymentList) {

        invoicePayment.setBankOrder(bankOrder);
      }

      bankOrderRepo.remove(bankOrderToRemove);
    }

    if (paymentMode.getConsoBankOrderLinePerPartner()) {

      consolidatePerPartner(bankOrder);
    }

    bankOrderService.updateTotalAmounts(bankOrder);

    return bankOrderRepo.save(bankOrder);
  }

  protected void checkSameElements(Collection<BankOrder> bankOrders) throws AxelorException {

    BankOrder refBankOrder = bankOrders.iterator().next();

    int refStatusSelect = refBankOrder.getStatusSelect();
    int orderTypeSelect = refBankOrder.getOrderTypeSelect();
    PaymentMode refPaymentMode = refBankOrder.getPaymentMode();
    int refPartnerTypeSelect = refBankOrder.getPartnerTypeSelect();
    Company refSenderCompany = refBankOrder.getSenderCompany();
    BankDetails refSenderBankDetails = refBankOrder.getSenderBankDetails();
    Currency refCurrency = refBankOrder.getBankOrderCurrency();
    boolean isMultiCurrency = refBankOrder.getIsMultiCurrency();

    for (BankOrder bankOrder : bankOrders) {

      int statusSelect = bankOrder.getStatusSelect();
      if (statusSelect != BankOrderRepository.STATUS_DRAFT
          && statusSelect != BankOrderRepository.STATUS_AWAITING_SIGNATURE) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_MERGE_STATUS));
      }

      if (statusSelect != refStatusSelect) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_STATUS));
      }

      if (!bankOrder.getOrderTypeSelect().equals(orderTypeSelect)) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_ORDER_TYPE_SELECT));
      }

      if (!bankOrder.getPaymentMode().equals(refPaymentMode)) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_PAYMENT_MODE));
      }

      if (!bankOrder.getPartnerTypeSelect().equals(refPartnerTypeSelect)) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_PARTNER_TYPE_SELECT));
      }

      if (!bankOrder.getSenderCompany().equals(refSenderCompany)) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_SENDER_COMPANY));
      }

      if (bankOrder.getSenderBankDetails() == null && refSenderBankDetails != null
          || (bankOrder.getSenderBankDetails() != null
              && !bankOrder.getSenderBankDetails().equals(refSenderBankDetails))) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_SENDER_BANK_DETAILS));
      }

      if (bankOrder.getIsMultiCurrency() != isMultiCurrency
          || !bankOrder.getIsMultiCurrency()
              && !bankOrder.getBankOrderCurrency().equals(refCurrency)) {
        throw new AxelorException(
            bankOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_CURRENCY));
      }
    }
  }

  protected List<BankOrderLine> getAllBankOrderLineList(Collection<BankOrder> bankOrders) {

    List<BankOrderLine> bankOrderLineList = Lists.newArrayList();

    for (BankOrder bankOrder : bankOrders) {

      bankOrderLineList.addAll(bankOrder.getBankOrderLineList());
    }

    return bankOrderLineList;
  }

  public void consolidatePerPartner(BankOrder bankOrder) {

    Map<List<Object>, BankOrderLine> bankOrderLineMap = new HashMap<List<Object>, BankOrderLine>();

    int counter = 1;

    for (BankOrderLine bankOrderLine : bankOrder.getBankOrderLineList()) {

      List<Object> keys = new ArrayList<Object>();
      keys.add(bankOrderLine.getPartner());
      keys.add(bankOrderLine.getBankOrderCurrency());
      keys.add(bankOrderLine.getBankOrderDate());
      keys.add(bankOrderLine.getBankOrderEconomicReason());
      keys.add(bankOrderLine.getFeesImputationModeSelect());
      keys.add(bankOrderLine.getPaymentModeSelect());
      keys.add(bankOrderLine.getReceiverBankDetails());
      keys.add(bankOrderLine.getReceiverCompany());

      if (bankOrderLineMap.containsKey(keys)) {
        BankOrderLine consolidateBankOrderLine = bankOrderLineMap.get(keys);
        if (consolidateBankOrderLine.getBankOrderLineOriginList() == null) {
          consolidateBankOrderLine.setBankOrderLineOriginList(new ArrayList<>());
        }
        if (bankOrderLine.getBankOrderLineOriginList() != null) {
          bankOrderLine
              .getBankOrderLineOriginList()
              .stream()
              .forEach(consolidateBankOrderLine::addBankOrderLineOriginListItem);
        }
        consolidateBankOrderLine.setBankOrderAmount(
            consolidateBankOrderLine.getBankOrderAmount().add(bankOrderLine.getBankOrderAmount()));
        consolidateBankOrderLine.setCompanyCurrencyAmount(
            consolidateBankOrderLine
                .getCompanyCurrencyAmount()
                .add(bankOrderLine.getCompanyCurrencyAmount()));
      } else {
        bankOrderLine.setCounter(counter++);
        bankOrderLineMap.put(keys, bankOrderLine);
      }
    }

    bankOrder.getBankOrderLineList().clear();

    for (BankOrderLine bankOrderLine : bankOrderLineMap.values()) {

      Pair<String, LocalDate> lastReferences = getLastReferences(bankOrderLine);

      bankOrderLine.setReceiverReference(lastReferences.getLeft());
      bankOrderLine.setBankOrderDate(lastReferences.getRight());
      bankOrder.addBankOrderLineListItem(bankOrderLine);
    }
  }

  protected Pair<String, LocalDate> getLastReferences(BankOrderLine bankOrderLine) {

    String lastReferenceId = "";
    LocalDate lastReferenceDate = null;

    List<BankOrderLineOrigin> orderByDateBankOrderLineOriginList =
        bankOrderLine.getBankOrderLineOriginList();

    Collections.sort(
        orderByDateBankOrderLineOriginList,
        Comparator.comparing(BankOrderLineOrigin::getRelatedToSelectDate));

    for (BankOrderLineOrigin bankOrderLineOrigin : orderByDateBankOrderLineOriginList) {
      LocalDate originDate = null;
      String originReferenceId = null;

      switch (bankOrderLineOrigin.getRelatedToSelect()) {
        case BankOrderLineOriginRepository.RELATED_TO_INVOICE:
          Invoice invoice = invoiceRepository.find(bankOrderLineOrigin.getRelatedToSelectId());
          if (!Strings.isNullOrEmpty(invoice.getSupplierInvoiceNb())) {
            originReferenceId += invoice.getSupplierInvoiceNb() + "/";
          } else {
            originReferenceId += invoice.getInvoiceId() + "/";
          }
          if (!Strings.isNullOrEmpty(invoice.getSupplierInvoiceNb())) {
            originDate = invoice.getOriginDate();
          } else {
            originDate = invoice.getInvoiceDate();
          }
          break;

        case BankOrderLineOriginRepository.RELATED_TO_PAYMENT_SCHEDULE_LINE:
          PaymentScheduleLine paymentScheduleLine =
              paymentScheduleLineRepository.find(bankOrderLineOrigin.getRelatedToSelectId());
          originReferenceId += paymentScheduleLine.getName() + "/";
          originDate = paymentScheduleLine.getScheduleDate();
          break;

        default:
          break;
      }

      if (originDate != null
          && (lastReferenceDate == null || lastReferenceDate.isBefore(originDate))) {
        lastReferenceDate = originDate;
        lastReferenceId = originReferenceId;
      }
    }

    return Pair.of(
        StringTool.truncRight(StringUtils.chop(lastReferenceId), 255), lastReferenceDate);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public BankOrder mergeFromInvoicePayments(Collection<InvoicePayment> invoicePayments)
      throws AxelorException {

    if (invoicePayments == null || invoicePayments.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_MERGE_NO_BANK_ORDERS));
    }

    Collection<InvoicePayment> invoicePaymentsWithBankOrders = new ArrayList<>();
    Collection<BankOrder> bankOrders = new LinkedHashSet<>();

    for (InvoicePayment invoicePayment : invoicePayments) {
      BankOrder bankOrder = invoicePayment.getBankOrder();

      if (bankOrder != null) {
        invoicePaymentsWithBankOrders.add(invoicePayment);
        bankOrders.add(bankOrder);
      }
    }

    if (bankOrders.size() > 1) {
      LocalDate bankOrderDate = bankOrders.iterator().next().getBankOrderDate();
      BankOrder mergedBankOrder = mergeBankOrders(bankOrders);
      mergedBankOrder.setBankOrderDate(bankOrderDate);

      for (InvoicePayment invoicePayment : invoicePaymentsWithBankOrders) {
        invoicePayment.setBankOrder(mergedBankOrder);
      }

      return mergedBankOrder;
    }

    if (!bankOrders.isEmpty()) {
      return bankOrders.iterator().next();
    }

    throw new AxelorException(
        TraceBackRepository.CATEGORY_INCONSISTENCY,
        I18n.get(IExceptionMessage.BANK_ORDER_MERGE_NO_BANK_ORDERS));
  }
}
