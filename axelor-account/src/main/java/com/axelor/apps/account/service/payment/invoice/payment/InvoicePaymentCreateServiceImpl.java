/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.PaymentVoucher;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.ReconcileRepository;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class InvoicePaymentCreateServiceImpl implements InvoicePaymentCreateService {

  protected InvoicePaymentRepository invoicePaymentRepository;
  protected InvoicePaymentToolService invoicePaymentToolService;
  protected CurrencyService currencyService;
  protected AppBaseService appBaseService;

  @Inject
  public InvoicePaymentCreateServiceImpl(
      InvoicePaymentRepository invoicePaymentRepository,
      InvoicePaymentToolService invoicePaymentToolService,
      CurrencyService currencyService,
      AppBaseService appBaseService) {

    this.invoicePaymentRepository = invoicePaymentRepository;
    this.invoicePaymentToolService = invoicePaymentToolService;
    this.currencyService = currencyService;
    this.appBaseService = appBaseService;
  }

  /**
   * @param amount
   * @param paymentDate
   * @param currency
   * @param paymentMode
   * @param invoice
   * @param typeSelect 1 : Advance Payment 2 : Payment 3 : Refund invoice 4 : Other
   * @return
   */
  public InvoicePayment createInvoicePayment(
      Invoice invoice,
      BigDecimal amount,
      LocalDate paymentDate,
      Currency currency,
      PaymentMode paymentMode,
      int typeSelect) {

    return new InvoicePayment(
        amount,
        paymentDate,
        currency,
        paymentMode,
        invoice,
        typeSelect,
        InvoicePaymentRepository.STATUS_DRAFT);
  }

  /**
   * @param invoice
   * @param amount
   * @param paymentDate
   * @param paymentMove
   * @return
   */
  public InvoicePayment createInvoicePayment(Invoice invoice, BigDecimal amount, Move paymentMove)
      throws AxelorException {

    LocalDate paymentDate = paymentMove.getDate();
    BigDecimal amountConverted =
        currencyService.getAmountCurrencyConvertedAtDate(
            paymentMove.getCompanyCurrency(), paymentMove.getCurrency(), amount, paymentDate);
    int typePaymentMove = this.determineType(paymentMove);
    Currency currency = paymentMove.getCurrency();
    PaymentMode paymentMode;
    InvoicePayment invoicePayment;
    if (typePaymentMove == InvoicePaymentRepository.TYPE_REFUND_INVOICE
        || typePaymentMove == InvoicePaymentRepository.TYPE_INVOICE) {
      paymentMode = null;
    } else {
      paymentMode = paymentMove.getPaymentMode();
    }
    invoicePayment =
        this.createInvoicePayment(
            invoice, amountConverted, paymentDate, currency, paymentMode, typePaymentMove);
    invoicePayment.setMove(paymentMove);
    invoicePayment.setStatusSelect(InvoicePaymentRepository.STATUS_VALIDATED);
    PaymentVoucher paymentVoucher = paymentMove.getPaymentVoucher();
    if (paymentVoucher != null) {
      invoicePayment.setCompanyBankDetails(paymentVoucher.getCompanyBankDetails());
    } else if (invoice.getSchedulePaymentOk() && invoice.getPaymentSchedule() != null) {
      BankDetails companyBankDetails = invoice.getPaymentSchedule().getCompanyBankDetails();
      invoicePayment.setCompanyBankDetails(companyBankDetails);
    }
    computeAdvancePaymentImputation(invoicePayment, paymentMove);
    invoice.addInvoicePaymentListItem(invoicePayment);
    invoicePaymentToolService.updateAmountPaid(invoice);
    invoicePaymentRepository.save(invoicePayment);

    return invoicePayment;
  }

  protected int determineType(Move move) {

    Invoice invoice = move.getInvoice();
    if (invoice != null) {
      if (invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_CLIENT_SALE
          || invoice.getOperationTypeSelect()
              == InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE) {
        return InvoicePaymentRepository.TYPE_INVOICE;
      } else {
        return InvoicePaymentRepository.TYPE_REFUND_INVOICE;
      }
    } else if (move.getPaymentVoucher() != null) {
      return InvoicePaymentRepository.TYPE_PAYMENT;
    } else {
      return InvoicePaymentRepository.TYPE_OTHER;
    }
  }

  protected void computeAdvancePaymentImputation(InvoicePayment invoicePayment, Move paymentMove) {

    // check if the payment is an advance payment imputation
    Invoice advanceInvoice = determineIfReconcileFromInvoice(paymentMove);
    if (advanceInvoice != null) {
      List<InvoicePayment> invoicePaymentList = advanceInvoice.getInvoicePaymentList();
      if (invoicePaymentList != null && !invoicePaymentList.isEmpty()) {
        // set right type
        invoicePayment.setTypeSelect(InvoicePaymentRepository.TYPE_ADV_PAYMENT_IMPUTATION);

        // create link between advance payment and its imputation
        InvoicePayment advancePayment = advanceInvoice.getInvoicePaymentList().get(0);
        advancePayment.setImputedBy(invoicePayment);
        invoicePaymentRepository.save(advancePayment);

        // set the imputed payment currency
        invoicePayment.setCurrency(advancePayment.getCurrency());

        BigDecimal currentImputedAmount = invoicePayment.getAmount();

        // we force the payment amount to be equal to the advance
        // invoice amount, so we get the right amount in the
        // right currency.
        BigDecimal totalAmountInAdvanceInvoice = advancePayment.getInvoice().getCompanyInTaxTotal();

        BigDecimal convertedImputedAmount =
            currentImputedAmount
                .divide(totalAmountInAdvanceInvoice, 2, BigDecimal.ROUND_HALF_EVEN)
                .multiply(advancePayment.getAmount());

        invoicePayment.setAmount(convertedImputedAmount);
      }
    }
  }

  /**
   * We try to get to the status of the invoice from the reconcile to see if this move was created
   * from a payment for an advance payment invoice.
   *
   * @param move
   * @return the found advance invoice if the move is from a payment that comes from this invoice.
   *     null in other cases
   */
  protected Invoice determineIfReconcileFromInvoice(Move move) {
    List<MoveLine> moveLineList = move.getMoveLineList();
    if (moveLineList == null || moveLineList.size() != 2) {
      return null;
    }
    InvoicePaymentRepository invoicePaymentRepo = Beans.get(InvoicePaymentRepository.class);
    for (MoveLine moveLine : moveLineList) {
      // search for the reconcile between the debit line
      if (moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0) {
        Reconcile reconcile =
            Beans.get(ReconcileRepository.class)
                .all()
                .filter("self.debitMoveLine = ?", moveLine)
                .fetchOne();
        if (reconcile == null) {
          return null;
        }
        // in the reconcile, search for the credit line to get the
        // associated payment
        if (reconcile.getCreditMoveLine() == null
            || reconcile.getCreditMoveLine().getMove() == null) {
          continue;
        }
        Move candidatePaymentMove = reconcile.getCreditMoveLine().getMove();
        InvoicePayment invoicePayment =
            invoicePaymentRepo
                .all()
                .filter("self.move = :_move")
                .bind("_move", candidatePaymentMove)
                .fetchOne();
        // if the invoice linked to the payment is an advance
        // payment, then return true.
        if (invoicePayment != null
            && invoicePayment.getInvoice() != null
            && invoicePayment.getInvoice().getOperationSubTypeSelect()
                == InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE) {
          return invoicePayment.getInvoice();
        }
      }
    }
    return null;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public InvoicePayment createInvoicePayment(Invoice invoice, BankDetails companyBankDetails) {
    InvoicePayment invoicePayment =
        createInvoicePayment(
            invoice,
            invoice.getInTaxTotal().subtract(invoice.getAmountPaid()),
            appBaseService.getTodayDate(),
            invoice.getCurrency(),
            invoice.getPaymentMode(),
            InvoicePaymentRepository.TYPE_PAYMENT);
    invoicePayment.setCompanyBankDetails(companyBankDetails);
    return invoicePaymentRepository.save(invoicePayment);
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public InvoicePayment createInvoicePayment(
      Invoice invoice,
      PaymentMode paymentMode,
      BankDetails companyBankDetails,
      LocalDate paymentDate,
      LocalDate bankDepositDate,
      String chequeNumber) {
    InvoicePayment invoicePayment =
        createInvoicePayment(
            invoice,
            invoice.getInTaxTotal().subtract(invoice.getAmountPaid()),
            paymentDate,
            invoice.getCurrency(),
            paymentMode,
            InvoicePaymentRepository.TYPE_PAYMENT);
    invoicePayment.setCompanyBankDetails(companyBankDetails);
    invoicePayment.setBankDepositDate(bankDepositDate);
    invoicePayment.setChequeNumber(chequeNumber);
    return invoicePaymentRepository.save(invoicePayment);
  }

  @Override
  public List<InvoicePayment> createMassInvoicePayment(
      List<Long> invoiceList,
      PaymentMode paymentMode,
      BankDetails companyBankDetails,
      LocalDate paymentDate,
      LocalDate bankDepositDate,
      String chequeNumber)
      throws AxelorException {

    List<InvoicePayment> invoicePaymentList = new ArrayList<>();

    InvoiceRepository invoiceRepository = Beans.get(InvoiceRepository.class);

    for (Long invoiceId : invoiceList) {

      invoicePaymentList.add(
          this.createInvoicePayment(
              invoiceRepository.find(invoiceId),
              paymentMode,
              companyBankDetails,
              paymentDate,
              bankDepositDate,
              chequeNumber));
    }

    return invoicePaymentList;
  }
}
