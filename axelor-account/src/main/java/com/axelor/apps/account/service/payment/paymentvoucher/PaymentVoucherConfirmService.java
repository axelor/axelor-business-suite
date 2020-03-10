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
package com.axelor.apps.account.service.payment.paymentvoucher;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PayVoucherElementToPay;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.PaymentVoucher;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.db.repo.PayVoucherElementToPayRepository;
import com.axelor.apps.account.db.repo.PaymentVoucherRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountCustomerService;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.account.service.payment.PaymentService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentVoucherConfirmService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected ReconcileService reconcileService;
  protected MoveLineService moveLineService;
  protected MoveService moveService;
  protected PaymentService paymentService;
  protected PaymentModeService paymentModeService;
  protected PaymentVoucherSequenceService paymentVoucherSequenceService;
  protected PaymentVoucherControlService paymentVoucherControlService;
  protected PaymentVoucherToolService paymentVoucherToolService;
  protected PayVoucherElementToPayRepository payVoucherElementToPayRepo;
  protected PaymentVoucherRepository paymentVoucherRepository;

  @Inject
  public PaymentVoucherConfirmService(
      ReconcileService reconcileService,
      MoveLineService moveLineService,
      MoveService moveService,
      PaymentService paymentService,
      PaymentModeService paymentModeService,
      PaymentVoucherSequenceService paymentVoucherSequenceService,
      PaymentVoucherControlService paymentVoucherControlService,
      PaymentVoucherToolService paymentVoucherToolService,
      PayVoucherElementToPayRepository payVoucherElementToPayRepo,
      PaymentVoucherRepository paymentVoucherRepository) {

    this.reconcileService = reconcileService;
    this.moveLineService = moveLineService;
    this.moveService = moveService;
    this.paymentService = paymentService;
    this.paymentModeService = paymentModeService;
    this.paymentVoucherSequenceService = paymentVoucherSequenceService;
    this.paymentVoucherControlService = paymentVoucherControlService;
    this.paymentVoucherToolService = paymentVoucherToolService;
    this.payVoucherElementToPayRepo = payVoucherElementToPayRepo;
    this.paymentVoucherRepository = paymentVoucherRepository;
  }

  /**
   * Confirms the payment voucher if the selected lines PiToPay 2nd O2M belongs to different
   * companies -> error I - Payment with an amount If we pay a classical moveLine (invoice, reject
   * ..) -> just create a payment If we pay a schedule 2 payments are created 1st reconciled with
   * the invoice and the second reconciled with the schedule II - Payment with an excess Payment If
   * we pay a moveLine having the same account, we just reconcile If we pay a with different account
   * -> 1- switch money to the good account 2- reconcile then
   *
   * @param paymentVoucher
   */
  @Transactional(rollbackOn = {Exception.class})
  public void confirmPaymentVoucher(PaymentVoucher paymentVoucher) throws AxelorException {
    log.debug("In confirmPaymentVoucherService ....");
    paymentVoucherSequenceService.setReference(paymentVoucher);

    PaymentMode paymentMode = paymentVoucher.getPaymentMode();
    Company company = paymentVoucher.getCompany();
    BankDetails companyBankDetails = paymentVoucher.getCompanyBankDetails();
    Journal journal =
        paymentModeService.getPaymentModeJournal(paymentMode, company, companyBankDetails);

    Account paymentModeAccount =
        paymentModeService.getPaymentModeAccount(paymentMode, company, companyBankDetails);

    paymentVoucherControlService.checkPaymentVoucherField(
        paymentVoucher, company, paymentModeAccount, journal);

    if (paymentVoucher.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0
        && !journal.getExcessPaymentOk()) {
      throw new AxelorException(
          paymentVoucher,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.PAYMENT_AMOUNT_EXCEEDING),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION));
    }

    // TODO VEIRIFER QUE LES ELEMENTS A PAYER NE CONCERNE QU'UNE SEULE DEVISE

    // TODO RECUPERER DEVISE DE LA PREMIERE DETTE
    //		Currency currencyToPay = null;

    AppAccountService appAccountService = Beans.get(AppAccountService.class);

    if (appAccountService.getAppAccount().getPaymentVouchersOnInvoice()
        && paymentVoucher.getPaymentMode().getValidatePaymentByDepositSlipPublication()) {
      waitForDepositSlip(paymentVoucher);
    } else {
      createMoveAndConfirm(paymentVoucher);
    }

    paymentVoucherSequenceService.setReceiptNo(paymentVoucher, company, journal);
    paymentVoucherRepository.save(paymentVoucher);
  }

  protected void waitForDepositSlip(PaymentVoucher paymentVoucher) {
    for (PayVoucherElementToPay payVoucherElementToPay :
        paymentVoucher.getPayVoucherElementToPayList()) {
      Invoice invoice = payVoucherElementToPay.getMoveLine().getMove().getInvoice();
      boolean hasPendingPayments =
          payVoucherElementToPay.getRemainingAmountAfterPayment().signum() <= 0;
      invoice.setHasPendingPayments(hasPendingPayments);
    }

    paymentVoucher.setStatusSelect(PaymentVoucherRepository.STATUS_WAITING_FOR_DEPOSIT_SLIP);
  }

  /**
   * Confirm payment voucher and create move.
   *
   * @param paymentVoucher
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  public void createMoveAndConfirm(PaymentVoucher paymentVoucher) throws AxelorException {
    Partner payerPartner = paymentVoucher.getPartner();
    PaymentMode paymentMode = paymentVoucher.getPaymentMode();
    Company company = paymentVoucher.getCompany();
    BankDetails companyBankDetails = paymentVoucher.getCompanyBankDetails();
    Journal journal =
        paymentModeService.getPaymentModeJournal(paymentMode, company, companyBankDetails);
    LocalDate paymentDate = paymentVoucher.getPaymentDate();
    boolean scheduleToBePaid = false;
    Account paymentModeAccount =
        paymentModeService.getPaymentModeAccount(paymentMode, company, companyBankDetails);

    // If paid by a moveline check if all the lines selected have the same account + company
    // Excess payment
    boolean allRight =
        paymentVoucherControlService.checkIfSameAccount(
            paymentVoucher.getPayVoucherElementToPayList(), paymentVoucher.getMoveLine());
    // Check if allright=true (means companies and accounts in lines are all the same and same as in
    // move line selected for paying
    log.debug("allRight : {}", allRight);

    if (allRight) {
      scheduleToBePaid =
          this.toPayWithExcessPayment(
              paymentVoucher.getPayVoucherElementToPayList(),
              paymentVoucher.getMoveLine(),
              scheduleToBePaid,
              paymentDate);
    }

    if (paymentVoucher.getMoveLine() == null
        || (paymentVoucher.getMoveLine() != null && !allRight)
        || (scheduleToBePaid && !allRight && paymentVoucher.getMoveLine() != null)) {

      // Manage all the cases in the same way. As if a move line (Excess payment) is selected, we
      // cancel it first
      Move move =
          moveService
              .getMoveCreateService()
              .createMoveWithPaymentVoucher(
                  journal,
                  company,
                  paymentVoucher,
                  payerPartner,
                  paymentDate,
                  paymentMode,
                  MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC);

      move.setPaymentVoucher(paymentVoucher);

      paymentVoucher.setGeneratedMove(move);
      // Create move lines for payment lines
      BigDecimal paidLineTotal = BigDecimal.ZERO;
      int moveLineNo = 1;

      boolean isDebitToPay = paymentVoucherToolService.isDebitToPay(paymentVoucher);

      for (PayVoucherElementToPay payVoucherElementToPay :
          this.getPayVoucherElementToPayList(paymentVoucher)) {
        MoveLine moveLineToPay = payVoucherElementToPay.getMoveLine();
        log.debug("PV moveLineToPay debit : {}", moveLineToPay.getDebit());
        log.debug("PV moveLineToPay amountPaid : {}", moveLineToPay.getAmountPaid());

        BigDecimal amountToPay = payVoucherElementToPay.getAmountToPayCurrency();

        if (amountToPay.compareTo(BigDecimal.ZERO) > 0) {

          paidLineTotal = paidLineTotal.add(amountToPay);

          this.payMoveLine(
              move,
              moveLineNo++,
              payerPartner,
              moveLineToPay,
              amountToPay,
              payVoucherElementToPay,
              isDebitToPay,
              paymentDate);
        }
      }
      // Create move line for the payment amount
      MoveLine moveLine = null;

      // cancelling the moveLine (excess payment) by creating the balance of all the payments
      // on the same account as the moveLine (excess payment)
      // in the else case we create a classical balance on the bank account of the payment mode
      if (paymentVoucher.getMoveLine() != null) {
        moveLine =
            moveLineService.createMoveLine(
                move,
                paymentVoucher.getPartner(),
                paymentVoucher.getMoveLine().getAccount(),
                paymentVoucher.getPaidAmount(),
                isDebitToPay,
                paymentDate,
                moveLineNo++,
                paymentVoucher.getRef(),
                null);

        Reconcile reconcile =
            reconcileService.createReconcile(
                moveLine, paymentVoucher.getMoveLine(), moveLine.getDebit(), !isDebitToPay);
        if (reconcile != null) {
          reconcileService.confirmReconcile(reconcile, true);
        }
      } else {

        moveLine =
            moveLineService.createMoveLine(
                move,
                payerPartner,
                paymentModeAccount,
                paymentVoucher.getPaidAmount(),
                isDebitToPay,
                paymentDate,
                moveLineNo++,
                paymentVoucher.getRef(),
                null);
      }
      move.getMoveLineList().add(moveLine);
      // Check if the paid amount is > paid lines total
      // Then Use Excess payment on old invoices / moveLines
      if (paymentVoucher.getPaidAmount().compareTo(paidLineTotal) > 0) {
        BigDecimal remainingPaidAmount = paymentVoucher.getRemainingAmount();

        // TODO rajouter le process d'imputation automatique
        //              if(paymentVoucher.getHasAutoInput())  {
        //
        //                  List<MoveLine> debitMoveLines =
        // Lists.newArrayList(pas.getDebitLinesToPay(contractLine,
        // paymentVoucher.getPaymentScheduleToPay()));
        //                  pas.createExcessPaymentWithAmount(debitMoveLines, remainingPaidAmount,
        // move, moveLineNo,
        //                          paymentVoucher.getPayerPartner(), company, contractLine, null,
        // paymentDate, updateCustomerAccount);
        //              }
        //              else  {

        Account partnerAccount =
            Beans.get(AccountCustomerService.class)
                .getPartnerAccount(
                    payerPartner, company, paymentVoucherToolService.isPurchase(paymentVoucher));

        moveLine =
            moveLineService.createMoveLine(
                move,
                paymentVoucher.getPartner(),
                partnerAccount,
                remainingPaidAmount,
                !isDebitToPay,
                paymentDate,
                moveLineNo++,
                paymentVoucher.getRef(),
                null);
        move.getMoveLineList().add(moveLine);

        if (isDebitToPay) {
          reconcileService.balanceCredit(moveLine);
        }
      }
      moveService.getMoveValidateService().validate(move);
      paymentVoucher.setGeneratedMove(move);
    }
    paymentVoucher.setStatusSelect(PaymentVoucherRepository.STATUS_CONFIRMED);

    deleteUnPaidLines(paymentVoucher);
  }

  public void deleteUnPaidLines(PaymentVoucher paymentVoucher) {

    if (paymentVoucher.getPayVoucherElementToPayList() == null) {
      return;
    }

    paymentVoucher.getPayVoucherDueElementList().clear();

    List<PayVoucherElementToPay> payVoucherElementToPayToRemove = new ArrayList<>();

    for (PayVoucherElementToPay payVoucherElementToPay :
        paymentVoucher.getPayVoucherElementToPayList()) {

      if (payVoucherElementToPay.getAmountToPay().compareTo(BigDecimal.ZERO) == 0
          && payVoucherElementToPay.getMoveLineGenerated() == null) {

        payVoucherElementToPayToRemove.add(payVoucherElementToPay);
      }
    }

    paymentVoucher.getPayVoucherElementToPayList().removeAll(payVoucherElementToPayToRemove);
  }

  /**
   * Récupérer les éléments à payer dans le bon ordre
   *
   * @return
   */
  public List<? extends PayVoucherElementToPay> getPayVoucherElementToPayList(
      PaymentVoucher paymentVoucher) {

    return payVoucherElementToPayRepo
        .all()
        .filter("self.paymentVoucher = ?1 ORDER by self.sequence ASC", paymentVoucher)
        .fetch();
  }

  /**
   * If paid by a moveline check if all the lines selected have the same account + company Excess
   * payment Check if allright=true (means companies and accounts in lines are all the same and same
   * as in move line selected for paying
   *
   * @param payVoucherElementToPayList Liste des paiement a réaliser
   * @param creditMoveLine Le trop-perçu
   * @param scheduleToBePaid
   * @return Une échéance doit-elle être payée?
   * @throws AxelorException
   */
  public boolean toPayWithExcessPayment(
      List<PayVoucherElementToPay> payVoucherElementToPayList,
      MoveLine creditMoveLine,
      boolean scheduleToBePaid,
      LocalDate paymentDate)
      throws AxelorException {
    boolean scheduleToBePaid2 = scheduleToBePaid;

    List<MoveLine> debitMoveLines = new ArrayList<MoveLine>();
    for (PayVoucherElementToPay payVoucherElementToPay : payVoucherElementToPayList) {

      debitMoveLines.add(payVoucherElementToPay.getMoveLine());
    }
    List<MoveLine> creditMoveLines = new ArrayList<MoveLine>();
    creditMoveLines.add(creditMoveLine);
    paymentService.useExcessPaymentOnMoveLines(debitMoveLines, creditMoveLines);
    return scheduleToBePaid2;
  }

  /**
   * @param paymentMove
   * @param moveLineSeq
   * @param payerPartner
   * @param moveLineToPay
   * @param amountToPay
   * @param payVoucherElementToPay
   * @return
   * @throws AxelorException
   */
  public MoveLine payMoveLine(
      Move paymentMove,
      int moveLineSeq,
      Partner payerPartner,
      MoveLine moveLineToPay,
      BigDecimal amountToPay,
      PayVoucherElementToPay payVoucherElementToPay,
      boolean isDebitToPay,
      LocalDate paymentDate)
      throws AxelorException {
    String invoiceName = "";
    if (moveLineToPay.getMove().getInvoice() != null) {
      invoiceName = moveLineToPay.getMove().getInvoice().getInvoiceId();
    } else {
      invoiceName = payVoucherElementToPay.getPaymentVoucher().getRef();
    }
    MoveLine moveLine =
        moveLineService.createMoveLine(
            paymentMove,
            payerPartner,
            moveLineToPay.getAccount(),
            amountToPay,
            !isDebitToPay,
            paymentDate,
            moveLineSeq,
            invoiceName,
            null);

    paymentMove.addMoveLineListItem(moveLine);
    payVoucherElementToPay.setMoveLineGenerated(moveLine);

    BigDecimal amountInCompanyCurrency = moveLine.getDebit().add(moveLine.getCredit());

    Reconcile reconcile =
        reconcileService.createReconcile(moveLineToPay, moveLine, amountInCompanyCurrency, true);
    if (reconcile != null) {
      log.debug("Reconcile : : : {}", reconcile);
      reconcileService.confirmReconcile(reconcile, true);
    }
    return moveLine;
  }
}
