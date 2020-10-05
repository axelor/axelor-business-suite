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
package com.axelor.apps.account.web;

import com.axelor.apps.account.db.AccountType;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.IrrecoverableService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.base.db.Wizard;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MoveLineController {

  public void computeAnalyticDistribution(ActionRequest request, ActionResponse response) {

    MoveLine moveLine = request.getContext().asType(MoveLine.class);

    try {
      if (Beans.get(AppAccountService.class).getAppAccount().getManageAnalyticAccounting()) {
        moveLine = Beans.get(MoveLineService.class).computeAnalyticDistribution(moveLine);
        response.setValue("analyticMoveLineList", moveLine.getAnalyticMoveLineList());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void createAnalyticDistributionWithTemplate(ActionRequest request, ActionResponse response)
      throws AxelorException {
    try {

      MoveLine moveLine = request.getContext().asType(MoveLine.class);

      moveLine = Beans.get(MoveLineService.class).createAnalyticDistributionWithTemplate(moveLine);
      response.setValue("analyticMoveLineList", moveLine.getAnalyticMoveLineList());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void usherProcess(ActionRequest request, ActionResponse response) {

    MoveLine moveLine = request.getContext().asType(MoveLine.class);
    moveLine = Beans.get(MoveLineRepository.class).find(moveLine.getId());

    try {
      Beans.get(MoveLineService.class).usherProcess(moveLine);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void passInIrrecoverable(ActionRequest request, ActionResponse response) {

    MoveLine moveLine = request.getContext().asType(MoveLine.class);
    moveLine = Beans.get(MoveLineRepository.class).find(moveLine.getId());

    try {
      Beans.get(IrrecoverableService.class).passInIrrecoverable(moveLine, true, true);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void notPassInIrrecoverable(ActionRequest request, ActionResponse response) {

    MoveLine moveLine = request.getContext().asType(MoveLine.class);
    moveLine = Beans.get(MoveLineRepository.class).find(moveLine.getId());

    try {
      Beans.get(IrrecoverableService.class).notPassInIrrecoverable(moveLine, true);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void accountingReconcile(ActionRequest request, ActionResponse response) {

    List<MoveLine> moveLineList = new ArrayList<>();

    @SuppressWarnings("unchecked")
    List<Integer> idList = (List<Integer>) request.getContext().get("_ids");

    try {
      if (idList != null) {
        for (Integer it : idList) {
          MoveLine moveLine = Beans.get(MoveLineRepository.class).find(it.longValue());
          if ((moveLine.getMove().getStatusSelect() == MoveRepository.STATUS_VALIDATED
                  || moveLine.getMove().getStatusSelect() == MoveRepository.STATUS_DAYBOOK)
              && moveLine.getAmountRemaining().compareTo(BigDecimal.ZERO) > 0) {
            moveLineList.add(moveLine);
          }
        }
      }

      if (!moveLineList.isEmpty()) {
        Beans.get(MoveLineService.class).reconcileMoveLinesWithCacheManagement(moveLineList);
        response.setReload(true);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void showCalculatedBalance(ActionRequest request, ActionResponse response) {
    BigDecimal totalCredit = new BigDecimal(0), totalDebit = new BigDecimal(0), finalBalance;
    @SuppressWarnings("unchecked")
    List<Integer> idList = (List<Integer>) request.getContext().get("_ids");

    try {
      if (idList != null && !idList.isEmpty()) {
        MoveLineRepository moveLineRepository = Beans.get(MoveLineRepository.class);
        for (Integer id : idList) {
          if (id != null) {
            MoveLine moveLine = moveLineRepository.find(id.longValue());
            if (moveLine != null && moveLine.getMove() != null) {
              Integer statusSelect = moveLine.getMove().getStatusSelect();
              if (statusSelect.equals(MoveRepository.STATUS_VALIDATED)
                  || statusSelect.equals(MoveRepository.STATUS_DAYBOOK)) {
                totalCredit = totalCredit.add(moveLine.getCredit());
                totalDebit = totalDebit.add(moveLine.getDebit());
              }
            } else {
              throw new AxelorException(
                  TraceBackRepository.CATEGORY_NO_VALUE,
                  I18n.get("Cannot find the move line with id: %s"),
                  id.longValue());
            }
          } else {
            throw new AxelorException(
                MoveLine.class, TraceBackRepository.CATEGORY_NO_VALUE, I18n.get("One id is null"));
          }
        }
        finalBalance = totalDebit.subtract(totalCredit);

        response.setView(
            ActionView.define("Calculation")
                .model(Wizard.class.getName())
                .add("form", "account-move-line-calculation-wizard-form")
                .param("popup", "true")
                .param("show-toolbar", "false")
                .param("show-confirm", "false")
                .param("width", "500")
                .param("popup-save", "false")
                .context("_credit", totalCredit)
                .context("_debit", totalDebit)
                .context("_balance", finalBalance)
                .map());
      } else {
        response.setAlert(I18n.get(IExceptionMessage.NO_MOVE_LINE_SELECTED));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void computeTaxAmount(ActionRequest request, ActionResponse response) {

    try {
      MoveLine moveLine = request.getContext().asType(MoveLine.class);
      moveLine = Beans.get(MoveLineService.class).computeTaxAmount(moveLine);
      response.setValues(moveLine);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void filterPartner(ActionRequest request, ActionResponse response) {
    Move move = request.getContext().getParent().asType(Move.class);
    if (move != null) {
      String domain = Beans.get(MoveService.class).filterPartner(move);
      response.setAttr("partner", "domain", domain);
    }
  }

  public void setAnalyticMoveLineAmount(ActionRequest request, ActionResponse response) {
    MoveLine moveLine = request.getContext().asType(MoveLine.class);

    if (moveLine.getAnalyticMoveLineList() != null
        && !moveLine.getAnalyticMoveLineList().isEmpty()) {
      for (AnalyticMoveLine analyticMoveLine : moveLine.getAnalyticMoveLineList()) {
        analyticMoveLine.setAmount(
            analyticMoveLine
                .getPercentage()
                .multiply(moveLine.getCredit().add(moveLine.getDebit()))
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP));
      }

      response.setValue("analyticMoveLineList", moveLine.getAnalyticMoveLineList());
    }
  }

  public void setAnalyticMoveLineDate(ActionRequest request, ActionResponse response) {
    MoveLine moveLine = request.getContext().asType(MoveLine.class);

    if (moveLine.getAnalyticMoveLineList() != null
        && !moveLine.getAnalyticMoveLineList().isEmpty()) {

      LocalDate date = moveLine.getDate();

      for (AnalyticMoveLine analyticMoveLine : moveLine.getAnalyticMoveLineList()) {
        analyticMoveLine.setDate(date);
      }

      response.setValue("analyticMoveLineList", moveLine.getAnalyticMoveLineList());
    }
  }

  public void setAnalyticMoveLineAccountTypeSelect(ActionRequest request, ActionResponse response) {
    MoveLine moveLine = request.getContext().asType(MoveLine.class);

    if (moveLine.getAccount() != null
        && moveLine.getAnalyticMoveLineList() != null
        && !moveLine.getAnalyticMoveLineList().isEmpty()) {

      AccountType AccountTypeSelect = moveLine.getAccount().getAccountType();

      for (AnalyticMoveLine analyticMoveLine : moveLine.getAnalyticMoveLineList()) {
        analyticMoveLine.setAccountType(AccountTypeSelect);
      }

      response.setValue("analyticMoveLineList", moveLine.getAnalyticMoveLineList());
    }
  }
}
