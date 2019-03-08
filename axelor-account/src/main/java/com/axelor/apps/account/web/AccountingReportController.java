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

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountingReport;
import com.axelor.apps.account.db.JournalType;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.AccountingReportRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.report.IReport;
import com.axelor.apps.account.service.AccountingReportService;
import com.axelor.apps.account.service.MoveLineExportService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AccountingReportController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject AccountingReportService accountingReportService;

  @Inject AccountingReportRepository accountingReportRepo;
  /**
   * @param request
   * @param response
   */
  public void searchMoveLine(ActionRequest request, ActionResponse response) {

    AccountingReport accountingReport = request.getContext().asType(AccountingReport.class);

    try {
      accountingReport = accountingReportRepo.find(accountingReport.getId());

      String query = accountingReportService.getMoveLineList(accountingReport);
      BigDecimal debitBalance = accountingReportService.getDebitBalance();
      BigDecimal creditBalance = accountingReportService.getCreditBalance();

      response.setValue("totalDebit", debitBalance);
      response.setValue("totalCredit", creditBalance);
      response.setValue("balance", debitBalance.subtract(creditBalance));

      ActionViewBuilder actionViewBuilder =
          ActionView.define(I18n.get(IExceptionMessage.ACCOUNTING_REPORT_3));
      actionViewBuilder.model(MoveLine.class.getName());
      actionViewBuilder.add("grid", "move-line-grid");
      actionViewBuilder.add("form", "move-line-form");
      actionViewBuilder.domain(query);

      response.setView(actionViewBuilder.map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * @param request
   * @param response
   */
  public void getJournalType(ActionRequest request, ActionResponse response) {

    AccountingReport accountingReport = request.getContext().asType(AccountingReport.class);

    try {

      JournalType journalType =
          Beans.get(AccountingReportService.class).getJournalType(accountingReport);
      if (journalType != null) {
        String domainQuery = "self.journalType.id = " + journalType.getId();
        response.setAttr("journal", "domain", domainQuery);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * @param request
   * @param response
   */
  public void getAccount(ActionRequest request, ActionResponse response) {

    AccountingReport accountingReport = request.getContext().asType(AccountingReport.class);

    try {
      Account account = Beans.get(AccountingReportService.class).getAccount(accountingReport);
      logger.debug("Compte : {}", account);
      response.setValue("account", account);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * @param request
   * @param response
   */
  public void getReload(ActionRequest request, ActionResponse response) {

    response.setReload(true);
  }

  /**
   * @param request
   * @param response
   */
  public void replayExport(ActionRequest request, ActionResponse response) {

    AccountingReport accountingReport = request.getContext().asType(AccountingReport.class);
    accountingReport = accountingReportRepo.find(accountingReport.getId());
    MoveLineExportService moveLineExportService = Beans.get(MoveLineExportService.class);

    try {
      moveLineExportService.replayExportMoveLine(accountingReport);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * @param request
   * @param response
   */
  public void printExportMoveLine(ActionRequest request, ActionResponse response) {

    AccountingReport accountingReport = request.getContext().asType(AccountingReport.class);
    accountingReport = accountingReportRepo.find(accountingReport.getId());

    try {

      int typeSelect = accountingReport.getTypeSelect();

      if (accountingReport.getExportTypeSelect() == null
          || accountingReport.getExportTypeSelect().isEmpty()
          || accountingReport.getTypeSelect() == 0) {
        response.setFlash(I18n.get(IExceptionMessage.ACCOUNTING_REPORT_4));
        response.setReload(true);
        return;
      }

      logger.debug("Type selected : {}", typeSelect);

      if ((typeSelect >= AccountingReportRepository.EXPORT_ADMINISTRATION
          && typeSelect < AccountingReportRepository.REPORT_ANALYTIC_BALANCE)) {

        MoveLineExportService moveLineExportService = Beans.get(MoveLineExportService.class);

        moveLineExportService.exportMoveLine(accountingReport);

      } else {

        if (typeSelect == AccountingReportRepository.REPORT_PARNER_GENERAL_LEDGER) {
          typeSelect = AccountingReportRepository.REPORT_GENERAL_LEDGER;
        }

        accountingReportService.setPublicationDateTime(accountingReport);

        String name =
            I18n.get(
                    MetaStore.getSelectionItem(
                            "accounting.report.type.select",
                            accountingReport.getTypeSelect().toString())
                        .getTitle())
                + " "
                + accountingReport.getRef();

        String fileLink =
            ReportFactory.createReport(
                    String.format("accountAccountingReportType%d", typeSelect),
                    accountingReport.getCompany(),
                    String.format(IReport.ACCOUNTING_REPORT_TYPE, typeSelect),
                    name + "-${date}")
                .addParam("AccountingReportId", accountingReport.getId())
                .addParam("Locale", ReportSettings.getPrintingLocale(null))
                .addFormat(accountingReport.getExportTypeSelect())
                .toAttach(accountingReport)
                .generate()
                .getFileLink();

        logger.debug("Printing " + name);

        response.setView(ActionView.define(name).add("html", fileLink).map());

        accountingReportService.setStatus(accountingReport);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void showMoveExported(ActionRequest request, ActionResponse response) {
    AccountingReport accountingReport = request.getContext().asType(AccountingReport.class);

    ActionViewBuilder actionViewBuilder =
        ActionView.define(I18n.get(IExceptionMessage.ACCOUNTING_REPORT_6));
    actionViewBuilder.model(Move.class.getName());
    actionViewBuilder.add("grid", "move-grid");
    actionViewBuilder.domain("self.accountingReport.id = :_accountingReportId");
    actionViewBuilder.context("_accountingReportId", accountingReport.getId());

    response.setView(actionViewBuilder.map());
  }
}
