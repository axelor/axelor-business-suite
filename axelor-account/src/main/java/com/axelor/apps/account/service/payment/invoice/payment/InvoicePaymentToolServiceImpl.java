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
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.PaymentModeRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvoicePaymentToolServiceImpl implements InvoicePaymentToolService {

  protected InvoiceRepository invoiceRepo;
  protected MoveToolService moveToolService;
  protected InvoicePaymentRepository invoicePaymentRepo;

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject
  public InvoicePaymentToolServiceImpl(
      InvoiceRepository invoiceRepo,
      MoveToolService moveToolService,
      InvoicePaymentRepository invoicePaymentRepo) {

    this.invoiceRepo = invoiceRepo;
    this.moveToolService = moveToolService;
    this.invoicePaymentRepo = invoicePaymentRepo;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateAmountPaid(Invoice invoice) throws AxelorException {

    invoice.setAmountPaid(computeAmountPaid(invoice));
    invoice.setAmountRemaining(computeAmountRemaining(invoice));
    updateHasPendingPayments(invoice);
    invoiceRepo.save(invoice);
    log.debug("Invoice : {}, amount paid : {}", invoice.getInvoiceId(), invoice.getAmountPaid());
  }

  @Override
  @Transactional
  public void updateHasPendingPayments(Invoice invoice) {
    invoice.setHasPendingPayments(checkPendingPayments(invoice));
  }

  protected BigDecimal computeAmountRemaining(Invoice invoice) throws AxelorException {

    BigDecimal amountRemaining = BigDecimal.ZERO;

    if (CollectionUtils.isEmpty(invoice.getInvoiceTermList())) {
      return amountRemaining;
    }

    for (InvoiceTerm invoiceTerm : invoice.getInvoiceTermList()) {
      amountRemaining = amountRemaining.add(invoiceTerm.getAmountRemaining());
    }

    boolean isMinus = moveToolService.isMinus(invoice);
    if (isMinus) {
      amountRemaining = amountRemaining.negate();
    }

    log.debug("Amount remaining total : {}", amountRemaining);

    return amountRemaining;
  }

  protected BigDecimal computeAmountPaid(Invoice invoice) throws AxelorException {

    BigDecimal amountPaid = BigDecimal.ZERO;

    if (invoice.getInvoicePaymentList() == null) {
      return amountPaid;
    }

    CurrencyService currencyService = Beans.get(CurrencyService.class);

    Currency invoiceCurrency = invoice.getCurrency();

    for (InvoicePayment invoicePayment : invoice.getInvoicePaymentList()) {

      if (invoicePayment.getStatusSelect() == InvoicePaymentRepository.STATUS_VALIDATED) {

        log.debug("Amount paid without move : {}", invoicePayment.getAmount());

        amountPaid =
            amountPaid.add(
                currencyService.getAmountCurrencyConvertedAtDate(
                    invoicePayment.getCurrency(),
                    invoiceCurrency,
                    invoicePayment.getAmount(),
                    invoicePayment.getPaymentDate()));
      }
    }

    boolean isMinus = moveToolService.isMinus(invoice);
    if (isMinus) {
      amountPaid = amountPaid.negate();
    }

    log.debug("Amount paid total : {}", amountPaid);

    return amountPaid;
  }

  /**
   * Check whether the sum of pending payments equals or exceeds the remaining amount of the
   * invoice.
   *
   * @param invoice
   * @return
   */
  protected boolean checkPendingPayments(Invoice invoice) {
    BigDecimal pendingAmount = BigDecimal.ZERO;

    if (invoice.getInvoicePaymentList() != null) {
      for (InvoicePayment invoicePayment : invoice.getInvoicePaymentList()) {
        if (invoicePayment.getStatusSelect() == InvoicePaymentRepository.STATUS_PENDING) {
          pendingAmount = pendingAmount.add(invoicePayment.getAmount());
        }
      }
    }

    return invoice.getAmountRemaining().compareTo(pendingAmount) <= 0;
  }

  /** @inheritDoc */
  @Override
  public List<BankDetails> findCompatibleBankDetails(
      Company company, InvoicePayment invoicePayment) {
    PaymentMode paymentMode = invoicePayment.getPaymentMode();
    if (company == null || paymentMode == null) {
      return new ArrayList<BankDetails>();
    }
    paymentMode =
        Beans.get(PaymentModeRepository.class).find(invoicePayment.getPaymentMode().getId());
    return Beans.get(PaymentModeService.class).getCompatibleBankDetailsList(paymentMode, company);
  }

  @Override
  public List<InvoicePayment> assignAdvancePayment(Invoice invoice, Invoice advancePayment) {
    List<InvoicePayment> advancePaymentList = advancePayment.getInvoicePaymentList();
    if (advancePaymentList == null || advancePaymentList.isEmpty()) {
      return advancePaymentList;
    }

    for (InvoicePayment invoicePayment : advancePaymentList) {
      invoice.addInvoicePaymentListItem(invoicePayment);
    }
    return advancePaymentList;
  }

  @Override
  public List<MoveLine> getCreditMoveLinesFromPayments(List<InvoicePayment> payments) {
    List<MoveLine> moveLines = new ArrayList<>();
    for (InvoicePayment payment : payments) {
      Move move = payment.getMove();
      if (move == null || move.getMoveLineList() == null || move.getMoveLineList().isEmpty()) {
        continue;
      }
      moveLines.addAll(moveToolService.getToReconcileCreditMoveLines(move));
    }
    return moveLines;
  }

  /**
   * Method who check if the payment is possible before validation
   *
   * @param invoicePayment the invoice payment to test
   * @throws AxelorException
   */
  @Override
  public void checkConditionBeforeSave(InvoicePayment invoicePayment) throws AxelorException {
    if (invoicePayment.getInvoice() != null
        && invoicePayment.getInvoice().getAmountRemaining().compareTo(BigDecimal.ZERO) <= 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.INVOICE_PAYMENT_NO_AMOUNT_REMAINING),
          invoicePayment.getInvoice().getInvoiceId());
    }
  }
}
