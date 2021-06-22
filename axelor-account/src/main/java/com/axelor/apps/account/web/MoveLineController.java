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
package com.axelor.apps.account.web;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.TaxEquiv;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.JournalTypeRepository;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.db.repo.PaymentModeRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.IrrecoverableService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
                  || moveLine.getMove().getStatusSelect() == MoveRepository.STATUS_ACCOUNTED)
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
                  || statusSelect.equals(MoveRepository.STATUS_ACCOUNTED)
                  || statusSelect.equals(MoveRepository.STATUS_SIMULATED)) {
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

  public void computeCurrentRate(ActionRequest request, ActionResponse response) {
    try {
      Context parentContext = request.getContext().getParent();
      BigDecimal currencyRate = BigDecimal.ONE;
      if (parentContext != null && Move.class.equals(parentContext.getContextClass())) {
        Move move = parentContext.asType(Move.class);
        Currency currency = move.getCurrency();
        Currency companyCurrency = move.getCompanyCurrency();
        if (currency != null && companyCurrency != null && !currency.equals(companyCurrency)) {
          System.err.println(move.getMoveLineList().size());
          if (move.getMoveLineList().size() == 0)
            currencyRate =
                Beans.get(CurrencyService.class)
                    .getCurrencyConversionRate(currency, companyCurrency);
          else currencyRate = move.getMoveLineList().get(0).getCurrencyRate();
        }
      }
      response.setValue("currencyRate", currencyRate);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e);
    }
  }

  public void descriptionRequired(ActionRequest request, ActionResponse response) {

    try {
      Context parentContext = request.getContext().getParent();
      if (parentContext != null) {
        Move move = parentContext.asType(Move.class);
        AccountConfig accountConfig =
            Beans.get(AccountConfigService.class).getAccountConfig(move.getCompany());
        response.setValue("$isDescriptionRequired", accountConfig.getIsDescriptionRequired());
      }
    } catch (AxelorException e) {
      TraceBackService.trace(response, e);
    }
  }

  public void loadAccountInformation(ActionRequest request, ActionResponse response) {
    Context parentContext = request.getContext().getParent();
    MoveLine moveLine = request.getContext().asType(MoveLine.class);
    if (parentContext != null) {
      Move move = parentContext.asType(Move.class);
      Partner partner = move.getPartner();

      if (ObjectUtils.isEmpty(partner)) {
        response.setError(I18n.get("Please select a partner"));
      } else {
        List<AccountingSituation> accountConfigs =
            partner.getAccountingSituationList().stream()
                .filter(
                    accountingSituation ->
                        accountingSituation.getCompany().equals(move.getCompany()))
                .collect(Collectors.toList());
        Account accountingAccount = null;
        if (move.getJournal().getJournalType().getTechnicalTypeSelect()
            == JournalTypeRepository.TECHNICAL_TYPE_SELECT_EXPENSE) {
          if (accountConfigs.size() > 0) {
            accountingAccount = accountConfigs.get(0).getDefaultExpenseAccount();
          }
        } else if (move.getJournal().getJournalType().getTechnicalTypeSelect()
            == JournalTypeRepository.TECHNICAL_TYPE_SELECT_SALE) {
          if (accountConfigs.size() > 0)
            accountingAccount = accountConfigs.get(0).getDefaultIncomeAccount();
        } else if (move.getJournal().getJournalType().getTechnicalTypeSelect()
            == JournalTypeRepository.TECHNICAL_TYPE_SELECT_TREASURY) {
          if (move.getPaymentMode() != null) {
            if (move.getPaymentMode().getInOutSelect().equals(PaymentModeRepository.IN)) {
              if (accountConfigs.size() > 0) {
                accountingAccount = accountConfigs.get(0).getCustomerAccount();
              }
            } else if (move.getPaymentMode().getInOutSelect().equals(PaymentModeRepository.OUT)) {
              if (accountConfigs.size() > 0) {
                accountingAccount = accountConfigs.get(0).getSupplierAccount();
              }
            }
          }
        }
        if (accountingAccount != null) {
          response.setValue("account", accountingAccount);
          if (!accountingAccount.getUseForPartnerBalance()) {
            response.setValue("partner", null);
          }
        }
        if (ObjectUtils.isEmpty(partner.getFiscalPosition())) {
          if (accountingAccount != null)
            if (accountingAccount.getDefaultTax() != null)
              response.setValue("taxLine", accountingAccount.getDefaultTax());
        } else {
          List<TaxLine> taxLineList;
          TaxLine taxLine = null;
          for (TaxEquiv taxEquiv : partner.getFiscalPosition().getTaxEquivList()) {
            if (accountingAccount != null)
              if (taxEquiv.getFromTax().equals(accountingAccount.getDefaultTax())) {
                taxLine = taxEquiv.getToTax().getActiveTaxLine();
                if (taxLine == null || !taxLine.getStartDate().isBefore(moveLine.getDate())) {
                  taxLineList =
                      taxEquiv.getToTax().getTaxLineList().stream()
                          .filter(
                              tl ->
                                  !moveLine.getDate().isBefore(tl.getEndDate())
                                      && !tl.getStartDate().isAfter(moveLine.getDate()))
                          .collect(Collectors.toList());
                  if (taxLineList.size() > 0) taxLine = taxLineList.get(0);
                }
                break;
              }
          }
          if (taxLine != null) response.setValue("taxLine", taxLine);
        }
      }
    }
  }
}
