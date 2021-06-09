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
package com.axelor.apps.account.service.payment.invoice.payment;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.InvoiceTerm;
import com.axelor.apps.account.db.InvoiceTermPayment;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;

public class InvoiceTermPaymentServiceImpl implements InvoiceTermPaymentService {

  protected CurrencyService currencyService;
  protected InvoiceTermService invoiceTermService;
  protected AppAccountService appAccountService;

  @Inject
  public InvoiceTermPaymentServiceImpl(
      CurrencyService currencyService,
      InvoiceTermService invoiceTermService,
      AppAccountService appAccountService) {
    this.currencyService = currencyService;
    this.invoiceTermService = invoiceTermService;
    this.appAccountService = appAccountService;
  }

  @Override
  public List<InvoiceTermPayment> initInvoiceTermPayments(
      InvoicePayment invoicePayment, List<InvoiceTerm> invoiceTermsToPay) {

    List<InvoiceTermPayment> invoiceTermPayments = Lists.newArrayList();
    for (InvoiceTerm invoiceTerm : invoiceTermsToPay) {
      invoiceTermPayments.add(
          createInvoiceTermPayment(invoicePayment, invoiceTerm, invoiceTerm.getAmountRemaining()));
    }

    return invoiceTermPayments;
  }

  @Override
  public void createInvoicePaymentTerms(InvoicePayment invoicePayment) throws AxelorException {

    Invoice invoice = invoicePayment.getInvoice();
    if (invoice == null
        || CollectionUtils.isEmpty(invoicePayment.getInvoice().getInvoiceTermList())) {
      return;
    }
    List<InvoiceTerm> invoiceTerms = invoiceTermService.getUnpaidInvoiceTermsFiltered(invoice);
    if (CollectionUtils.isEmpty(invoiceTerms)) {
      return;
    }
    invoicePayment.setInvoiceTermPaymentList(
        initInvoiceTermPaymentsWithAmount(
            invoicePayment, invoiceTerms, invoicePayment.getAmount()));
  }

  @Override
  public List<InvoiceTermPayment> initInvoiceTermPaymentsWithAmount(
      InvoicePayment invoicePayment,
      List<InvoiceTerm> invoiceTermsToPay,
      BigDecimal availableAmount)
      throws AxelorException {

    List<InvoiceTermPayment> invoiceTermPayments = Lists.newArrayList();

    availableAmount =
        currencyService
            .getAmountCurrencyConvertedAtDate(
                invoicePayment.getCurrency(),
                invoicePayment.getInvoice().getCurrency(),
                availableAmount,
                appAccountService.getTodayDate(invoicePayment.getInvoice().getCompany()))
            .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);

    for (InvoiceTerm invoiceTerm : invoiceTermsToPay) {
      if (availableAmount.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal invoiceTermAmount = invoiceTerm.getAmountRemaining();
        if (invoiceTermAmount.compareTo(availableAmount) >= 0) {
          invoiceTermPayments.add(
              createInvoiceTermPayment(invoicePayment, invoiceTerm, availableAmount));
          availableAmount = BigDecimal.ZERO;
        } else {
          invoiceTermPayments.add(
              createInvoiceTermPayment(invoicePayment, invoiceTerm, invoiceTermAmount));
          availableAmount = availableAmount.subtract(invoiceTermAmount);
        }
      }
    }
    return invoiceTermPayments;
  }

  @Override
  public InvoiceTermPayment createInvoiceTermPayment(
      InvoicePayment invoicePayment, InvoiceTerm invoiceTermToPay, BigDecimal paidAmount) {

    InvoiceTermPayment invoiceTermPayment = new InvoiceTermPayment();
    invoiceTermPayment.setInvoicePayment(invoicePayment);
    invoiceTermPayment.setInvoiceTerm(invoiceTermToPay);
    invoiceTermPayment.setPaidAmount(paidAmount);
    return invoiceTermPayment;
  }

  @Override
  public InvoicePayment updateInvoicePaymentAmount(InvoicePayment invoicePayment)
      throws AxelorException {

    invoicePayment.setAmount(
        computeInvoicePaymentAmount(invoicePayment, invoicePayment.getInvoiceTermPaymentList()));

    return invoicePayment;
  }

  @Override
  public BigDecimal computeInvoicePaymentAmount(
      InvoicePayment invoicePayment, List<InvoiceTermPayment> invoiceTermPayments)
      throws AxelorException {

    BigDecimal sum = BigDecimal.ZERO;
    for (InvoiceTermPayment invoiceTermPayment : invoiceTermPayments) {
      sum = sum.add(invoiceTermPayment.getPaidAmount());
    }

    sum =
        currencyService
            .getAmountCurrencyConvertedAtDate(
                invoicePayment.getInvoice().getCurrency(),
                invoicePayment.getCurrency(),
                sum,
                Beans.get(AppAccountService.class)
                    .getTodayDate(invoicePayment.getInvoice().getCompany()))
            .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);

    return sum;
  }
}
