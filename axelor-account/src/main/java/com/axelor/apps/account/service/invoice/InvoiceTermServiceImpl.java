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
package com.axelor.apps.account.service.invoice;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.InvoiceTerm;
import com.axelor.apps.account.db.InvoiceTermPayment;
import com.axelor.apps.account.db.PaymentConditionLine;
import com.axelor.apps.account.db.repo.InvoiceTermRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;

public class InvoiceTermServiceImpl implements InvoiceTermService {

  protected InvoiceTermRepository invoiceTermRepo;

  @Inject
  public InvoiceTermServiceImpl(InvoiceTermRepository invoiceTermRepo) {
    this.invoiceTermRepo = invoiceTermRepo;
  }

  @Override
  public Invoice computeInvoiceTerms(Invoice invoice) throws AxelorException {

    if (invoice.getPaymentCondition() == null
        || CollectionUtils.isEmpty(invoice.getPaymentCondition().getPaymentConditionLineList())) {
      return null;
    }

    invoice.clearInvoiceTermList();

    Set<PaymentConditionLine> paymentConditionLines =
        new HashSet<>(invoice.getPaymentCondition().getPaymentConditionLineList());
    Iterator<PaymentConditionLine> iterator = paymentConditionLines.iterator();
    BigDecimal total = BigDecimal.ZERO;
    while (iterator.hasNext()) {
      PaymentConditionLine paymentConditionLine = iterator.next();
      InvoiceTerm invoiceTerm = computeInvoiceTerm(invoice, paymentConditionLine);
      if (!iterator.hasNext()) {
        invoiceTerm.setAmount(invoice.getInTaxTotal().subtract(total));
        invoiceTerm.setAmountRemaining(invoice.getInTaxTotal().subtract(total));
      } else {
        total = total.add(invoiceTerm.getAmount());
      }
      invoice.addInvoiceTermListItem(invoiceTerm);
    }

    return invoice;
  }

  @Override
  public InvoiceTerm computeInvoiceTerm(Invoice invoice, PaymentConditionLine paymentConditionLine)
      throws AxelorException {

    InvoiceTerm invoiceTerm = new InvoiceTerm();

    invoiceTerm.setPaymentConditionLine(paymentConditionLine);
    BigDecimal amount =
        invoice
            .getInTaxTotal()
            .multiply(paymentConditionLine.getPaymentPercentage())
            .divide(
                BigDecimal.valueOf(100),
                AppBaseService.DEFAULT_NB_DECIMAL_DIGITS,
                RoundingMode.HALF_UP);
    invoiceTerm.setAmount(amount);
    invoiceTerm.setAmountRemaining(amount);

    invoiceTerm.setIsHoldBack(paymentConditionLine.getIsHoldback());
    invoiceTerm.setIsPaid(false);
    return invoiceTerm;
  }

  @Override
  public Invoice setDueDates(Invoice invoice, LocalDate invoiceDate) {

    if (invoice.getPaymentCondition() == null
        || CollectionUtils.isEmpty(invoice.getInvoiceTermList())) {
      return invoice;
    }

    LocalDate nDaysDate = null;
    for (InvoiceTerm invoiceTerm : invoice.getInvoiceTermList()) {
      LocalDate dueDate =
          InvoiceToolService.getDueDate(invoiceTerm.getPaymentConditionLine(), invoiceDate);
      invoiceTerm.setDueDate(dueDate);

      if (nDaysDate == null || dueDate.isBefore(nDaysDate)) {
        nDaysDate = dueDate;
      }
    }

    invoice.getInvoiceTermList().sort(Comparator.comparing(InvoiceTerm::getDueDate));
    return invoice;
  }

  @Override
  public List<InvoiceTerm> getUnpaidInvoiceTerms(Invoice invoice) {

    return invoiceTermRepo
        .all()
        .filter("self.invoice = ?1 AND self.isPaid is not true", invoice)
        .order("dueDate")
        .fetch();
  }

  @Override
  public List<InvoiceTerm> filterInvoiceTermsByHoldBack(List<InvoiceTerm> invoiceTerms) {

    if (CollectionUtils.isEmpty(invoiceTerms)) {
      return invoiceTerms;
    }

    boolean isFirstHoldBack = invoiceTerms.get(0).getIsHoldBack();
    invoiceTerms.removeIf(it -> it.getIsHoldBack() != isFirstHoldBack);

    return invoiceTerms;
  }

  @Override
  public List<InvoiceTerm> getUnpaidInvoiceTermsFiltered(Invoice invoice) {

    return filterInvoiceTermsByHoldBack(getUnpaidInvoiceTerms(invoice));
  }

  @Override
  public void updateInvoiceTermsPaidAmount(InvoicePayment invoicePayment) throws AxelorException {

    if (CollectionUtils.isEmpty(invoicePayment.getInvoiceTermPaymentList())) {
      return;
    }

    for (InvoiceTermPayment invoiceTermPayment : invoicePayment.getInvoiceTermPaymentList()) {
      InvoiceTerm invoiceTerm = invoiceTermPayment.getInvoiceTerm();
      BigDecimal paidAmount = invoiceTermPayment.getPaidAmount();
      BigDecimal amountRemaining = invoiceTerm.getAmountRemaining().subtract(paidAmount);

      if (amountRemaining.compareTo(BigDecimal.ZERO) <= 0) {
        amountRemaining = BigDecimal.ZERO;
        invoiceTerm.setIsPaid(true);
      }

      invoiceTerm.setAmountRemaining(amountRemaining);
    }
  }
}
