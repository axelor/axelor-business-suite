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
package com.axelor.apps.account.web;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.AccountRepository;
import com.axelor.apps.account.service.AccountService;
import com.axelor.apps.account.translation.ITranslation;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.shiro.util.CollectionUtils;

@Singleton
public class AccountController {

  @Inject private AccountRepository accountRepository;

  @Inject private AccountService accountService;

  public void computeBalance(ActionRequest request, ActionResponse response) {
    try {
      Account account = request.getContext().asType(Account.class);
      if (account.getId() == null) {
        return;
      }
      account = accountRepository.find(account.getId());

      BigDecimal balance =
          accountService.computeBalance(account, AccountService.BALANCE_TYPE_DEBIT_BALANCE);

      if (balance.compareTo(BigDecimal.ZERO) >= 0) {
        response.setAttr("$balanceBtn", "title", I18n.get(ITranslation.ACCOUNT_DEBIT_BALANCE));
      } else {
        balance = balance.multiply(new BigDecimal(-1));
        response.setAttr("$balanceBtn", "title", I18n.get(ITranslation.ACCOUNT_CREDIT_BALANCE));
      }

      response.setValue("$balanceBtn", balance);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void analyticDistributionAuthorizedBacameFalse(
      ActionRequest request, ActionResponse response) {
    try {
      Account account = request.getContext().asType(Account.class);
      account = Beans.get(AccountRepository.class).find(account.getId());
      if (account.getAnalyticDistributionAuthorized() == false) {
        List<InvoiceLine> invoiceLineList =
            Beans.get(AccountService.class).searchInvoiceLinesByAccountAndAnalytic(account, true);

        List<MoveLine> moveLineList =
            Beans.get(AccountService.class).searchMoveLinesByAccountAndAnalytic(account, true);

        if (!CollectionUtils.isEmpty(invoiceLineList) || !CollectionUtils.isEmpty(moveLineList)) {
          List<Long> idInvoiceLineList = new ArrayList<Long>();
          idInvoiceLineList.add((long) 0);
          if (!CollectionUtils.isEmpty(invoiceLineList)) {
            idInvoiceLineList =
                invoiceLineList.stream().map(InvoiceLine::getId).collect(Collectors.toList());
          }

          List<Long> idMoveLineList = new ArrayList<Long>();
          idMoveLineList.add((long) 0);
          if (!CollectionUtils.isEmpty(moveLineList)) {
            idMoveLineList =
                moveLineList.stream().map(MoveLine::getId).collect(Collectors.toList());
          }

          response.setView(
              ActionView.define("Move/Invoice lines with analytic to clean")
                  .model(Account.class.getName())
                  .add("form", "account-move-and-invoice-lines-to-clean-form")
                  .param("popup", "true")
                  .param("show-toolbar", "false")
                  .param("show-confirm", "false")
                  .param("popup-save", "false")
                  .context("_showRecord", account.getId())
                  .context("_idInvoiceLineList", idInvoiceLineList)
                  .context("_idMoveLineList", idMoveLineList)
                  .map());
        }
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void analyticDistributionRequiredOnInvoiceLinesBacameTrue(
      ActionRequest request, ActionResponse response) {
    try {
      Account account = request.getContext().asType(Account.class);
      account = Beans.get(AccountRepository.class).find(account.getId());
      if (account.getAnalyticDistributionRequiredOnInvoiceLines() == true) {
        List<InvoiceLine> invoiceLineList =
            Beans.get(AccountService.class).searchInvoiceLinesByAccountAndAnalytic(account, false);
        if (!CollectionUtils.isEmpty(invoiceLineList)) {
          List<Long> idInvoiceLineList =
              invoiceLineList.stream().map(InvoiceLine::getId).collect(Collectors.toList());
          response.setView(
              ActionView.define("Invoice lines without analytic to complete")
                  .model(Account.class.getName())
                  .add("form", "account-invoice-lines-to-complete-form")
                  .param("popup", "true")
                  .param("show-toolbar", "false")
                  .param("show-confirm", "false")
                  .param("popup-save", "false")
                  .context("_showRecord", account.getId())
                  .context("_idInvoiceLineList", idInvoiceLineList)
                  .map());
        }
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void analyticDistributionRequiredOnMoveLinesBacameTrue(
      ActionRequest request, ActionResponse response) {
    try {
      Account account = request.getContext().asType(Account.class);
      account = Beans.get(AccountRepository.class).find(account.getId());
      if (account.getAnalyticDistributionRequiredOnMoveLines() == true) {
        List<MoveLine> moveLineList =
            Beans.get(AccountService.class).searchMoveLinesByAccountAndAnalytic(account, false);
        if (!CollectionUtils.isEmpty(moveLineList)) {
          List<Long> idMoveLineList =
              moveLineList.stream().map(MoveLine::getId).collect(Collectors.toList());
          response.setView(
              ActionView.define("Move lines without analytic to complete")
                  .model(Account.class.getName())
                  .add("form", "account-move-lines-to-complete-form")
                  .param("popup", "true")
                  .param("show-toolbar", "false")
                  .param("show-confirm", "false")
                  .param("popup-save", "false")
                  .context("_showRecord", account.getId())
                  .context("_idMoveLineList", idMoveLineList)
                  .map());
        }
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void cleanAnalytic(ActionRequest request, ActionResponse response) {
    try {
      Account account = request.getContext().asType(Account.class);
      account = Beans.get(AccountRepository.class).find(account.getId());
      Beans.get(AccountService.class).cleanAnalytic(account);
      response.setCanClose(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
