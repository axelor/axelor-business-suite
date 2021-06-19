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
package com.axelor.apps.account.db.repo;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.move.MoveSequenceService;
import com.axelor.apps.account.service.move.MoveValidateService;
import com.axelor.apps.base.db.Period;
import com.axelor.apps.base.db.repo.YearRepository;
import com.axelor.apps.base.service.PeriodService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.persistence.PersistenceException;

public class MoveManagementRepository extends MoveRepository {

  @Override
  public Move copy(Move entity, boolean deep) {

    Move copy = super.copy(entity, deep);

    copy.setDate(Beans.get(AppBaseService.class).getTodayDate(copy.getCompany()));

    Period period = null;
    try {
      period =
          Beans.get(PeriodService.class)
              .getActivePeriod(copy.getDate(), entity.getCompany(), YearRepository.TYPE_FISCAL);
    } catch (AxelorException e) {
      throw new PersistenceException(e);
    }
    copy.setStatusSelect(STATUS_NEW);
    copy.setTechnicalOriginSelect(MoveRepository.TECHNICAL_ORIGIN_ENTRY);
    copy.setReference(null);
    copy.setExportNumber(null);
    copy.setExportDate(null);
    copy.setAccountingReport(null);
    copy.setValidationDate(null);
    copy.setPeriod(period);
    copy.setAccountingOk(false);
    copy.setIgnoreInDebtRecoveryOk(false);
    copy.setPaymentVoucher(null);
    copy.setRejectOk(false);
    copy.setInvoice(null);

    List<MoveLine> moveLineList = copy.getMoveLineList();

    if (moveLineList != null) {
      moveLineList.forEach(moveLine -> resetMoveLine(moveLine, copy.getDate()));
    }

    return copy;
  }

  public void resetMoveLine(MoveLine moveLine, LocalDate date) {
    moveLine.setInvoiceReject(null);
    moveLine.setDate(date);
    moveLine.setExportedDirectDebitOk(false);
    moveLine.setReimbursementStatusSelect(MoveLineRepository.REIMBURSEMENT_STATUS_NULL);
    moveLine.setReconcileGroup(null);
    moveLine.setAmountPaid(BigDecimal.ZERO);
    moveLine.setTaxPaymentMoveLineList(null);
    moveLine.setTaxAmount(BigDecimal.ZERO);

    List<AnalyticMoveLine> analyticMoveLineList = moveLine.getAnalyticMoveLineList();

    if (analyticMoveLineList != null) {
      moveLine.getAnalyticMoveLineList().forEach(line -> line.setDate(moveLine.getDate()));
    }
  }

  @Override
  public Move save(Move move) {
    try {
      if (move.getStatusSelect() == MoveRepository.STATUS_DAYBOOK) {
        Beans.get(MoveValidateService.class).checkPreconditions(move);
      }

      Beans.get(MoveSequenceService.class).setDraftSequence(move);
      List<MoveLine> moveLineList = move.getMoveLineList();
      if (moveLineList != null) {
        for (MoveLine moveLine : moveLineList) {
          List<AnalyticMoveLine> analyticMoveLineList = moveLine.getAnalyticMoveLineList();
          if (analyticMoveLineList != null) {
            for (AnalyticMoveLine analyticMoveLine : analyticMoveLineList) {
              analyticMoveLine.setAccount(moveLine.getAccount());
              analyticMoveLine.setAccountType(moveLine.getAccount().getAccountType());
            }
          }
        }
      }
      return super.save(move);
    } catch (Exception e) {
      TraceBackService.traceExceptionFromSaveMethod(e);
      throw new PersistenceException(e);
    }
  }

  @Override
  public void remove(Move entity) {

    if (!entity.getStatusSelect().equals(MoveRepository.STATUS_NEW)) {
      try {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.MOVE_REMOVE_NOT_OK),
            entity.getReference());
      } catch (AxelorException e) {
        throw new PersistenceException(e);
      }
    } else {
      super.remove(entity);
    }
  }
}
