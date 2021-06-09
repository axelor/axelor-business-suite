/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.bankpayment.service.invoice.payment;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCreateServiceImpl;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentToolService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoiceTermPaymentService;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.repo.BankOrderRepository;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderMergeService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.util.List;
import javax.persistence.TypedQuery;

public class InvoicePaymentCreateServiceBankPayImpl extends InvoicePaymentCreateServiceImpl
    implements InvoicePaymentCreateServiceBankPay {

  @Inject
  public InvoicePaymentCreateServiceBankPayImpl(
      InvoicePaymentRepository invoicePaymentRepository,
      InvoicePaymentToolService invoicePaymentToolService,
      CurrencyService currencyService,
      AppBaseService appBaseService,
      InvoiceTermPaymentService invoiceTermPaymentService,
      InvoiceTermService invoiceTermService) {

    super(
        invoicePaymentRepository,
        invoicePaymentToolService,
        currencyService,
        appBaseService,
        invoiceTermPaymentService,
        invoiceTermService);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public List<InvoicePayment> createMassInvoicePayment(
      List<Long> invoiceList,
      PaymentMode paymentMode,
      BankDetails companyBankDetails,
      LocalDate paymentDate,
      LocalDate bankDepositDate,
      String chequeNumber)
      throws AxelorException {

    List<InvoicePayment> invoicePaymentList =
        super.createMassInvoicePayment(
            invoiceList,
            paymentMode,
            companyBankDetails,
            paymentDate,
            bankDepositDate,
            chequeNumber);

    if (!Beans.get(AppBaseService.class).isApp("bank-payment")) {
      return invoicePaymentList;
    }

    if (invoicePaymentList.isEmpty()) {
      return invoicePaymentList;
    }

    if (paymentMode.getGenerateBankOrder()) {
      Beans.get(BankOrderMergeService.class).mergeFromInvoicePayments(invoicePaymentList);
    }
    return invoicePaymentList;
  }

  @Override
  public List<Long> getInvoiceIdsToPay(List<Long> invoiceIdList) throws AxelorException {
    List<Long> invoiceToPay = super.getInvoiceIdsToPay(invoiceIdList);

    if (!Beans.get(AppBaseService.class).isApp("bank-payment")) {
      return invoiceToPay;
    }

    for (Long invoiceId : invoiceToPay) {
      Invoice invoice = Beans.get(InvoiceRepository.class).find(invoiceId);
      if (invoice.getPaymentMode().getGenerateBankOrder()) {
        this.checkBankOrderAlreadyExist(invoice);
      }
    }
    return invoiceToPay;
  }

  @Override
  public void checkBankOrderAlreadyExist(Invoice invoice) throws AxelorException {
    TypedQuery<BankOrder> q =
        JPA.em()
            .createQuery(
                "select bankOrder "
                    + "FROM Invoice invoice "
                    + "LEFT JOIN InvoicePayment invoicePayment on invoice.id = invoicePayment.invoice "
                    + "LEFT JOIN BankOrder as bankOrder on invoicePayment.bankOrder = bankOrder.id "
                    + "WHERE invoice.id = :id "
                    + "AND (bankOrder.statusSelect = :statusSelectDraft "
                    + "OR bankOrder.statusSelect = :statusSelectAwaiting)",
                BankOrder.class);
    q.setParameter("id", invoice.getId());
    q.setParameter("statusSelectDraft", BankOrderRepository.STATUS_DRAFT);
    q.setParameter("statusSelectAwaiting", BankOrderRepository.STATUS_AWAITING_SIGNATURE);
    List<BankOrder> listbankOrder = q.getResultList();
    if (listbankOrder != null && !listbankOrder.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.INVOICE_BANK_ORDER_ALREADY_EXIST),
          listbankOrder.get(0).getBankOrderSeq(),
          invoice.getInvoiceId());
    }
  }
}
