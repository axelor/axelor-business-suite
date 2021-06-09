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
package com.axelor.apps.bankpayment.service;

import com.axelor.apps.account.db.InterbankCodeLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PaymentSchedule;
import com.axelor.apps.account.db.PaymentScheduleLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.repo.InterbankCodeLineRepository;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.PaymentScheduleLineRepository;
import com.axelor.apps.account.db.repo.PaymentScheduleRepository;
import com.axelor.apps.account.db.repo.ReconcileRepository;
import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.account.service.PaymentScheduleLineServiceImpl;
import com.axelor.apps.account.service.PaymentScheduleService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.account.service.move.MoveValidateService;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.account.service.payment.PaymentService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCancelService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PaymentScheduleLineBankPaymentServiceImpl extends PaymentScheduleLineServiceImpl
    implements PaymentScheduleLineBankPaymentService {

  protected InvoicePaymentCancelService invoicePaymentCancelService;
  protected InterbankCodeLineRepository interbankCodeLineRepo;
  protected ReconcileRepository reconcileRepo;
  protected InvoicePaymentRepository invoicePaymentRepo;

  @Inject
  public PaymentScheduleLineBankPaymentServiceImpl(
      AppBaseService appBaseService,
      PaymentScheduleService paymentScheduleService,
      MoveService moveService,
      PaymentModeService paymentModeService,
      SequenceService sequenceService,
      AccountingSituationService accountingSituationService,
      MoveToolService moveToolService,
      PaymentService paymentService,
      InvoicePaymentCancelService invoicePaymentCancelService,
      InterbankCodeLineRepository interbankCodeLineRepo,
      MoveLineRepository moveLineRepo,
      PaymentScheduleLineRepository paymentScheduleLineRepo,
      ReconcileRepository reconcileRepo,
      InvoicePaymentRepository invoicePaymentRepo) {
    super(
        appBaseService,
        paymentScheduleService,
        moveService,
        paymentModeService,
        sequenceService,
        accountingSituationService,
        moveToolService,
        paymentService,
        moveLineRepo,
        paymentScheduleLineRepo);
    this.invoicePaymentCancelService = invoicePaymentCancelService;
    this.interbankCodeLineRepo = interbankCodeLineRepo;
    this.reconcileRepo = reconcileRepo;
    this.invoicePaymentRepo = invoicePaymentRepo;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void reject(
      PaymentScheduleLine paymentScheduleLine, InterbankCodeLine rejectionReason, boolean represent)
      throws AxelorException {
    Preconditions.checkNotNull(
        paymentScheduleLine, I18n.get("Payment schedule line cannot be null."));
    PaymentSchedule paymentSchedule = paymentScheduleLine.getPaymentSchedule();
    Preconditions.checkNotNull(
        paymentSchedule, I18n.get("Parent payment schedule cannot be null."));

    if (paymentScheduleLine.getStatusSelect() != PaymentScheduleLineRepository.STATUS_VALIDATED) {
      throw new AxelorException(
          paymentScheduleLine,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get("Only validated payment schedule lines can be rejected."));
    }

    Move rejectionMove = createRejectionMove(paymentScheduleLine);

    if (paymentSchedule.getTypeSelect() == PaymentScheduleRepository.TYPE_TERMS) {
      cancelInvoicePayments(paymentScheduleLine);
    }

    if (represent) {
      representPaymentScheduleLine(paymentScheduleLine);
    }

    if (rejectionReason == null) {
      rejectionReason = getDefaultRejectionReason();
    }

    MoveLine rejectionMoveLine =
        moveService.findMoveLineByAccount(
            rejectionMove, paymentScheduleLine.getAdvanceMoveLine().getAccount());

    paymentScheduleLine.setInterbankCodeLine(rejectionReason);
    paymentScheduleLine.setRejectMoveLine(rejectionMoveLine);
    paymentScheduleLine.setRejectDate(rejectionMove.getDate());
    paymentScheduleLine.setAmountRejected(paymentScheduleLine.getRejectMoveLine().getDebit());
    paymentScheduleLine.setRejectedOk(true);
    paymentScheduleLine.setStatusSelect(PaymentScheduleLineRepository.STATUS_CLOSED);
  }

  @Override
  public void reject(
      String paymentScheduleLineName, InterbankCodeLine rejectionReason, boolean represent)
      throws AxelorException {
    PaymentScheduleLine paymentScheduleLine =
        paymentScheduleLineRepo.findByName(paymentScheduleLineName);
    reject(paymentScheduleLine, rejectionReason, represent);
  }

  @Override
  public InterbankCodeLine getDefaultRejectionReason() {
    return interbankCodeLineRepo.findByCode("A3");
  }

  @Transactional(rollbackOn = {Exception.class})
  protected Move createRejectionMove(PaymentScheduleLine paymentScheduleLine)
      throws AxelorException {
    MoveValidateService moveValidateService = moveService.getMoveValidateService();
    MoveLineService moveLineService = moveService.getMoveLineService();

    Move advanceOrPaymentMove = paymentScheduleLine.getAdvanceOrPaymentMove();
    Move rejectionMove =
        moveService.generateReverse(
            advanceOrPaymentMove, true, true, false, advanceOrPaymentMove.getDate());
    rejectionMove.setRejectOk(true);
    moveValidateService.validate(rejectionMove);

    List<MoveLine> moveLineList = new ArrayList<>();
    moveLineList.addAll(advanceOrPaymentMove.getMoveLineList());
    moveLineList.addAll(rejectionMove.getMoveLineList());
    moveLineService.reconcileMoveLines(moveLineList);

    return rejectionMove;
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void cancelInvoicePayments(PaymentScheduleLine paymentScheduleLine)
      throws AxelorException {
    MoveLineService moveLineService = moveService.getMoveLineService();
    PaymentSchedule paymentSchedule = paymentScheduleLine.getPaymentSchedule();
    MoveLine creditMoveLine = paymentScheduleLine.getAdvanceMoveLine();
    Set<Invoice> invoiceSet =
        MoreObjects.firstNonNull(paymentSchedule.getInvoiceSet(), Collections.emptySet());

    for (Invoice invoice : invoiceSet) {
      MoveLine debitMoveLine = moveLineService.getDebitCustomerMoveLine(invoice);
      Reconcile reconcile = reconcileRepo.findByMoveLines(debitMoveLine, creditMoveLine);

      if (reconcile == null) {
        continue;
      }

      for (InvoicePayment invoicePayment :
          invoicePaymentRepo.findByReconcileId(reconcile.getId()).fetch()) {
        invoicePaymentCancelService.cancel(invoicePayment);
      }
    }
  }

  @Transactional
  protected PaymentScheduleLine representPaymentScheduleLine(
      PaymentScheduleLine paymentScheduleLine) {
    PaymentSchedule paymentSchedule = paymentScheduleLine.getPaymentSchedule();
    BigDecimal inTaxAmount = paymentScheduleLine.getInTaxAmount();
    int scheduleLineSeq = paymentScheduleService.getNextScheduleLineSeq(paymentSchedule);
    LocalDate scheduleDate = paymentScheduleLine.getScheduleDate();
    PaymentScheduleLine representedPaymentScheduleLine =
        createPaymentScheduleLine(paymentSchedule, inTaxAmount, scheduleLineSeq, scheduleDate);
    representedPaymentScheduleLine.setFromReject(true);
    representedPaymentScheduleLine.setStatusSelect(
        PaymentScheduleLineRepository.STATUS_IN_PROGRESS);
    return representedPaymentScheduleLine;
  }

  protected void refindPaymentScheduleLines(
      List<PaymentScheduleLine> paymentScheduleLines, int index) {
    List<Long> idList =
        paymentScheduleLines
            .subList(Math.max(index, paymentScheduleLines.size()), paymentScheduleLines.size())
            .stream()
            .map(PaymentScheduleLine::getId)
            .collect(Collectors.toList());

    if (!idList.isEmpty()) {
      List<PaymentScheduleLine> foundPaymentScheduleLines =
          paymentScheduleLineRepo.findByIdList(idList).fetch();

      if (foundPaymentScheduleLines.size() != idList.size()) {
        throw new IllegalStateException(
            String.format(
                "Expected size: %d, got: %d", idList.size(), foundPaymentScheduleLines.size()));
      }

      for (int i = 0; i < foundPaymentScheduleLines.size(); ++i) {
        paymentScheduleLines.set(index + i, foundPaymentScheduleLines.get(i));
      }
    }
  }

  @Override
  public void reject(long id, InterbankCodeLine rejectionReason, boolean represent)
      throws AxelorException {
    PaymentScheduleLine paymentScheduleLine = paymentScheduleLineRepo.find(id);
    reject(paymentScheduleLine, rejectionReason, represent);
  }

  @Override
  public int rejectFromIdMap(Map<Long, InterbankCodeLine> idMap, boolean represent) {
    return rejectFromMap(idMap, represent, paymentScheduleLineRepo::find);
  }

  @Override
  public int rejectFromNameMap(Map<String, InterbankCodeLine> nameMap, boolean represent) {
    return rejectFromMap(nameMap, represent, paymentScheduleLineRepo::findByName);
  }

  protected <T> int rejectFromMap(
      Map<T, InterbankCodeLine> map, boolean represent, Function<T, PaymentScheduleLine> findFunc) {

    int errorCount = 0;

    for (Entry<T, InterbankCodeLine> entry : map.entrySet()) {
      T key = entry.getKey();
      InterbankCodeLine rejectionReason = entry.getValue();
      PaymentScheduleLine paymentScheduleLine = findFunc.apply(key);

      try {
        reject(paymentScheduleLine, rejectionReason, represent);
      } catch (Exception e) {
        TraceBackService.trace(e);
        ++errorCount;
      }
    }

    return errorCount;
  }
}
