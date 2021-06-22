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
package com.axelor.apps.bankpayment.service.move;

import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveCreateService;
import com.axelor.apps.account.service.move.MoveDueService;
import com.axelor.apps.account.service.move.MoveExcessPaymentService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveRemoveService;
import com.axelor.apps.account.service.move.MoveServiceImpl;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.account.service.move.MoveValidateService;
import com.axelor.apps.account.service.payment.PaymentService;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.util.Map;

public class MoveServiceBankPaymentImpl extends MoveServiceImpl {

  @Inject
  public MoveServiceBankPaymentImpl(
      AppAccountService appAccountService,
      MoveLineService moveLineService,
      MoveCreateService moveCreateService,
      MoveValidateService moveValidateService,
      MoveToolService moveToolService,
      MoveRemoveService moveRemoveService,
      ReconcileService reconcileService,
      MoveDueService moveDueService,
      PaymentService paymentService,
      MoveExcessPaymentService moveExcessPaymentService,
      MoveRepository moveRepository,
      AccountConfigService accountConfigService,
      CurrencyService currencyService) {
    super(
        appAccountService,
        moveLineService,
        moveCreateService,
        moveValidateService,
        moveToolService,
        moveRemoveService,
        reconcileService,
        moveDueService,
        paymentService,
        moveExcessPaymentService,
        moveRepository,
        accountConfigService,
        currencyService);
  }

  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public Move generateReverse(Move move, Map<String, Object> assistantMap) throws AxelorException {
    Move newMove = super.generateReverse(move, assistantMap);

    boolean isHiddenMoveLinesInBankReconciliation =
        (boolean) assistantMap.get("isHiddenMoveLinesInBankReconciliation");
    if (isHiddenMoveLinesInBankReconciliation) {
      move = this.updateBankAmountReconcile(move);
    }
    return newMove;
  }

  protected MoveLine generateReverseMoveLine(
      Move reverseMove, MoveLine orgineMoveLine, LocalDate dateOfReversion, boolean isDebit)
      throws AxelorException {
    MoveLine reverseMoveLine =
        super.generateReverseMoveLine(reverseMove, orgineMoveLine, dateOfReversion, isDebit);
    reverseMoveLine.setBankReconciledAmount(
        reverseMoveLine
            .getDebit()
            .add(reverseMoveLine.getCredit().subtract(orgineMoveLine.getBankReconciledAmount())));
    return reverseMoveLine;
  }

  protected Move updateBankAmountReconcile(Move move) {
    for (MoveLine moveLine : move.getMoveLineList()) {
      moveLine.setBankReconciledAmount(moveLine.getDebit().add(moveLine.getCredit()));
    }
    return move;
  }
}
