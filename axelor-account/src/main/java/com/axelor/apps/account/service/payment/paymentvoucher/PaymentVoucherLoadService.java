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
package com.axelor.apps.account.service.payment.paymentvoucher;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PayVoucherDueElement;
import com.axelor.apps.account.db.PayVoucherElementToPay;
import com.axelor.apps.account.db.PaymentVoucher;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.db.repo.PayVoucherDueElementRepository;
import com.axelor.apps.account.db.repo.PaymentVoucherRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.service.BankDetailsService;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PaymentVoucherLoadService {

  protected CurrencyService currencyService;
  protected PaymentVoucherToolService paymentVoucherToolService;
  protected PayVoucherDueElementRepository payVoucherDueElementRepo;
  protected PaymentVoucherRepository paymentVoucherRepository;

  @Inject
  public PaymentVoucherLoadService(
      CurrencyService currencyService,
      PaymentVoucherToolService paymentVoucherToolService,
      PayVoucherDueElementRepository payVoucherDueElementRepo,
      PaymentVoucherRepository paymentVoucherRepository) {

    this.currencyService = currencyService;
    this.paymentVoucherToolService = paymentVoucherToolService;
    this.payVoucherDueElementRepo = payVoucherDueElementRepo;
    this.paymentVoucherRepository = paymentVoucherRepository;
  }

  /**
   * Searching move lines to pay
   *
   * @param pv paymentVoucher
   * @param mlToIgnore moveLine list to ignore
   * @return moveLines a list of moveLines
   * @throws AxelorException
   */
  public List<MoveLine> getMoveLines(PaymentVoucher paymentVoucher) throws AxelorException {

    MoveLineRepository moveLineRepo = Beans.get(MoveLineRepository.class);

    String query =
        "self.partner = ?1 "
            + "and self.account.useForPartnerBalance = 't' "
            + "and self.amountRemaining > 0 "
            + "and (self.move.statusSelect = ?3 OR self.move.statusSelect = ?4)"
            + "and self.move.ignoreInDebtRecoveryOk = 'f' "
            + "and self.move.company = ?2 "
            + "and self.move.invoice.pfpValidateStatusSelect != ?5 "
            + "and (?6 IS NULL OR self.move.tradingName = ?6)";

    if (paymentVoucherToolService.isDebitToPay(paymentVoucher)) {
      query += " and self.debit > 0 ";
    } else {
      query += " and self.credit > 0 ";
    }

    return moveLineRepo
        .all()
        .filter(
            query,
            paymentVoucher.getPartner(),
            paymentVoucher.getCompany(),
            MoveRepository.STATUS_VALIDATED,
            MoveRepository.STATUS_ACCOUNTED,
            InvoiceRepository.PFP_STATUS_LITIGATION,
            paymentVoucher.getTradingName())
        .fetch();
  }

  @Transactional(rollbackOn = {Exception.class})
  public void searchDueElements(PaymentVoucher paymentVoucher) throws AxelorException {

    if (paymentVoucher.getPayVoucherElementToPayList() != null) {
      paymentVoucher.getPayVoucherElementToPayList().clear();
    }

    if (paymentVoucher.getPayVoucherDueElementList() != null) {
      paymentVoucher.getPayVoucherDueElementList().clear();
    }

    for (MoveLine moveLine : this.getMoveLines(paymentVoucher)) {

      paymentVoucher.addPayVoucherDueElementListItem(this.createPayVoucherDueElement(moveLine));
    }

    paymentVoucherRepository.save(paymentVoucher);
  }

  public PayVoucherDueElement createPayVoucherDueElement(MoveLine moveLine) throws AxelorException {

    Move move = moveLine.getMove();

    PayVoucherDueElement payVoucherDueElement = new PayVoucherDueElement();

    payVoucherDueElement.setMoveLine(moveLine);

    payVoucherDueElement.setDueAmount(moveLine.getCurrencyAmount());

    BigDecimal paidAmountInElementCurrency =
        currencyService
            .getAmountCurrencyConvertedAtDate(
                move.getCompanyCurrency(),
                move.getCurrency(),
                moveLine.getAmountPaid(),
                moveLine.getDate())
            .setScale(2, RoundingMode.HALF_UP);

    payVoucherDueElement.setPaidAmount(paidAmountInElementCurrency);

    payVoucherDueElement.setAmountRemaining(
        payVoucherDueElement.getDueAmount().subtract(payVoucherDueElement.getPaidAmount()));

    payVoucherDueElement.setCurrency(move.getCurrency());

    return payVoucherDueElement;
  }

  @Transactional(rollbackOn = {Exception.class})
  public void loadSelectedLines(PaymentVoucher paymentVoucher, PaymentVoucher paymentVoucherContext)
      throws AxelorException {

    if (paymentVoucherContext.getPayVoucherElementToPayList() != null) {

      if (paymentVoucherContext.getPaidAmount() == null) {
        throw new AxelorException(
            paymentVoucher,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.PAYMENT_VOUCHER_LOAD_1),
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION));
      }

      paymentVoucher.setPaidAmount(paymentVoucherContext.getPaidAmount());

      this.completeElementToPay(paymentVoucher, paymentVoucherContext);
    }

    paymentVoucher.setPaidAmount(paymentVoucherContext.getPaidAmount());

    paymentVoucherRepository.save(paymentVoucher);
  }

  /**
   * Allows to load selected lines (from 1st 02M) to the 2nd O2M and dispatching amounts according
   * to amountRemainnig for the loaded move and the paid amount remaining of the paymentVoucher
   *
   * @param paymentVoucher
   * @param paymentVoucherContext
   * @return
   * @return
   * @return values Map of data
   * @throws AxelorException
   */
  public void completeElementToPay(
      PaymentVoucher paymentVoucher, PaymentVoucher paymentVoucherContext) throws AxelorException {

    int sequence = paymentVoucher.getPayVoucherElementToPayList().size() + 1;

    for (PayVoucherDueElement payVoucherDueElementContext :
        paymentVoucherContext.getPayVoucherDueElementList()) {
      PayVoucherDueElement payVoucherDueElement =
          payVoucherDueElementRepo.find(payVoucherDueElementContext.getId());

      if (payVoucherDueElementContext.isSelected()) {

        paymentVoucher.addPayVoucherElementToPayListItem(
            this.createPayVoucherElementToPay(payVoucherDueElement, sequence++));

        // Remove the line from the due elements lists
        paymentVoucher.removePayVoucherDueElementListItem(payVoucherDueElement);
      }
    }
  }

  public PayVoucherElementToPay createPayVoucherElementToPay(
      PayVoucherDueElement payVoucherDueElement, int sequence) throws AxelorException {

    PaymentVoucher paymentVoucher = payVoucherDueElement.getPaymentVoucher();
    BigDecimal amountRemaining = paymentVoucher.getRemainingAmount();
    LocalDate paymentDate = paymentVoucher.getPaymentDate();

    PayVoucherElementToPay payVoucherElementToPay = new PayVoucherElementToPay();

    payVoucherElementToPay.setSequence(sequence);
    payVoucherElementToPay.setMoveLine(payVoucherDueElement.getMoveLine());
    payVoucherElementToPay.setTotalAmount(payVoucherDueElement.getDueAmount());
    payVoucherElementToPay.setRemainingAmount(payVoucherDueElement.getAmountRemaining());
    payVoucherElementToPay.setCurrency(payVoucherDueElement.getCurrency());

    BigDecimal amountRemainingInElementCurrency =
        currencyService
            .getAmountCurrencyConvertedAtDate(
                paymentVoucher.getCurrency(),
                payVoucherElementToPay.getCurrency(),
                amountRemaining,
                paymentDate)
            .setScale(2, RoundingMode.HALF_UP);

    BigDecimal amountImputedInElementCurrency =
        amountRemainingInElementCurrency.min(payVoucherElementToPay.getRemainingAmount());

    BigDecimal amountImputedInPayVouchCurrency =
        currencyService
            .getAmountCurrencyConvertedAtDate(
                payVoucherElementToPay.getCurrency(),
                paymentVoucher.getCurrency(),
                amountImputedInElementCurrency,
                paymentDate)
            .setScale(2, RoundingMode.HALF_UP);

    payVoucherElementToPay.setAmountToPay(amountImputedInElementCurrency);
    payVoucherElementToPay.setAmountToPayCurrency(amountImputedInPayVouchCurrency);
    payVoucherElementToPay.setRemainingAmountAfterPayment(
        payVoucherElementToPay.getRemainingAmount().subtract(amountImputedInElementCurrency));

    return payVoucherElementToPay;
  }

  /**
   * Fonction vérifiant si l'ensemble des lignes à payer ont le même compte et que ce compte est le
   * même que celui du trop-perçu
   *
   * @param payVoucherElementToPayList La liste des lignes à payer
   * @param moveLine Le trop-perçu à utiliser
   * @return
   */
  public boolean checkIfSameAccount(
      List<PayVoucherElementToPay> payVoucherElementToPayList, MoveLine moveLine) {
    if (moveLine != null) {
      Account account = moveLine.getAccount();
      for (PayVoucherElementToPay payVoucherElementToPay : payVoucherElementToPayList) {
        if (!payVoucherElementToPay.getMoveLine().getAccount().equals(account)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public boolean mustBeBalanced(
      MoveLine moveLineToPay, PaymentVoucher paymentVoucher, BigDecimal amountToPay) {

    Invoice invoice = moveLineToPay.getMove().getInvoice();

    Currency invoiceCurrency = invoice.getCurrency();

    Currency paymentVoucherCurrency = paymentVoucher.getCurrency();

    // Si la devise de paiement est la même que le devise de la facture,
    // Et que le montant déjà payé en devise sur la facture, plus le montant réglé
    // par la nouvelle saisie paiement en devise, est égale au montant total en
    // devise de la facture
    // Alors on solde la facture
    if (paymentVoucherCurrency.equals(invoiceCurrency)
        && invoice.getAmountPaid().add(amountToPay).compareTo(invoice.getInTaxTotal()) == 0) {
      // SOLDER
      return true;
    }

    return false;
  }

  /**
   * @param moveLineInvoiceToPay Les lignes de factures récupérées depuis l'échéance
   * @param payVoucherElementToPay La Ligne de saisie paiement
   * @return
   */
  public List<MoveLine> assignMaxAmountToReconcile(
      List<MoveLine> moveLineInvoiceToPay, BigDecimal amountToPay) {
    List<MoveLine> debitMoveLines = new ArrayList<MoveLine>();
    if (moveLineInvoiceToPay != null && moveLineInvoiceToPay.size() != 0) {
      // Récupération du montant imputé sur l'échéance, et assignation de la valeur
      // dans la moveLine (champ temporaire)
      BigDecimal maxAmountToPayRemaining = amountToPay;
      for (MoveLine moveLine : moveLineInvoiceToPay) {
        if (maxAmountToPayRemaining.compareTo(BigDecimal.ZERO) > 0) {
          BigDecimal amountPay = maxAmountToPayRemaining.min(moveLine.getAmountRemaining());
          moveLine.setMaxAmountToReconcile(amountPay);
          debitMoveLines.add(moveLine);
          maxAmountToPayRemaining = maxAmountToPayRemaining.subtract(amountPay);
        }
      }
    }
    return debitMoveLines;
  }

  /**
   * Initialize a payment voucher from an invoice.
   *
   * @param paymentVoucher
   * @param invoice
   * @throws AxelorException
   */
  public void initFromInvoice(PaymentVoucher paymentVoucher, Invoice invoice)
      throws AxelorException {
    paymentVoucher.setOperationTypeSelect(invoice.getOperationTypeSelect());
    paymentVoucher.setPartner(invoice.getPartner());
    paymentVoucher.setPaymentMode(invoice.getPaymentMode());
    paymentVoucher.setCurrency(invoice.getCurrency());
    paymentVoucher.clearPayVoucherDueElementList();
    paymentVoucher.clearPayVoucherElementToPayList();
    paymentVoucher.setCompany(invoice.getCompany());
    BankDetails companyBankDetails;

    if (invoice.getCompanyBankDetails() != null) {
      companyBankDetails = invoice.getCompanyBankDetails();
    } else {
      companyBankDetails =
          Beans.get(BankDetailsService.class)
              .getDefaultCompanyBankDetails(
                  invoice.getCompany(), invoice.getPaymentMode(), invoice.getPartner(), null);
    }

    paymentVoucher.setCompanyBankDetails(companyBankDetails);
    BigDecimal amount = BigDecimal.ZERO;
    List<MoveLine> moveLineList = getMoveLines(paymentVoucher);

    for (MoveLine moveLine : moveLineList) {
      PayVoucherDueElement payVoucherDueElement = createPayVoucherDueElement(moveLine);
      paymentVoucher.addPayVoucherDueElementListItem(payVoucherDueElement);

      if (invoice.equals(payVoucherDueElement.getMoveLine().getMove().getInvoice())) {
        amount = amount.add(payVoucherDueElement.getAmountRemaining());
      }
    }

    paymentVoucher.setPaidAmount(amount);
    paymentVoucher.clearPayVoucherDueElementList();

    for (MoveLine moveLine : moveLineList) {
      paymentVoucher.addPayVoucherDueElementListItem(createPayVoucherDueElement(moveLine));
    }

    if (paymentVoucher.getPayVoucherDueElementList() == null) {
      return;
    }

    int sequence = 0;

    for (Iterator<PayVoucherDueElement> it =
            paymentVoucher.getPayVoucherDueElementList().iterator();
        it.hasNext(); ) {
      PayVoucherDueElement payVoucherDueElement = it.next();

      if (invoice.equals(payVoucherDueElement.getMoveLine().getMove().getInvoice())
          && paymentVoucher.getCurrency().equals(payVoucherDueElement.getCurrency())) {
        paymentVoucher.addPayVoucherElementToPayListItem(
            createPayVoucherElementToPay(payVoucherDueElement, ++sequence));
        it.remove();
      }
    }
  }
}
