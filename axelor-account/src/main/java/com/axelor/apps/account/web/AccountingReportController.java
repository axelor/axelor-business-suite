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
import com.axelor.apps.account.db.AccountingReport;
import com.axelor.apps.account.db.JournalType;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.AccountingReportRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountingReportService;
import com.axelor.apps.account.service.MoveLineExportService;
import com.axelor.apps.base.db.App;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Joiner;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AccountingReportController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * @param request
   * @param response
   */
  public void searchMoveLine(ActionRequest request, ActionResponse response) {

    AccountingReport accountingReport = request.getContext().asType(AccountingReport.class);
    AccountingReportService accountingReportService = Beans.get(AccountingReportService.class);

    try {
      accountingReport = Beans.get(AccountingReportRepository.class).find(accountingReport.getId());

      if (accountingReport.getReportType().getTypeSelect()
              == AccountingReportRepository.REPORT_FEES_DECLARATION_PREPERATORY_PROCESS
          || accountingReport.getReportType().getTypeSelect()
              == AccountingReportRepository.REPORT_FEES_DECLARATION_SUPPORT) {

        if (accountingReportService.isThereAlreadyDraftReportInPeriod(accountingReport)) {
          response.setError(
              I18n.get(
                  "There is already an ongoing accounting report of this type in draft status for this same period."));
        }
        if (accountingReportService.isThereAlreadyOngoingDas2ExportInPeriod(accountingReport)) {
          response.setAlert(
              I18n.get(
                  "There is already an ongoing DAS2 export for this period that has not been exported yet. Do you want to proceed ?"));
        }

        List<BigInteger> moveLineIdList =
            accountingReportService.getAccountingReportDas2Pieces(accountingReport, true);
        ActionViewBuilder actionViewBuilder =
            ActionView.define(I18n.get(IExceptionMessage.ACCOUNTING_REPORT_3));
        actionViewBuilder.model(MoveLine.class.getName());
        actionViewBuilder.add("grid", "move-line-das2-grid");
        actionViewBuilder.add("form", "move-line-form");
        actionViewBuilder.param("search-filters", "move-line-filters");
        actionViewBuilder.domain("self.id in (" + Joiner.on(",").join(moveLineIdList) + ")");

        response.setReload(true);
        response.setView(actionViewBuilder.map());

      } else {
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
        actionViewBuilder.param("search-filters", "move-line-filters");
        actionViewBuilder.domain(query);

        response.setView(actionViewBuilder.map());
      }

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
    accountingReport = Beans.get(AccountingReportRepository.class).find(accountingReport.getId());
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
    accountingReport = Beans.get(AccountingReportRepository.class).find(accountingReport.getId());
    AccountingReportService accountingReportService = Beans.get(AccountingReportService.class);

    try {

      int typeSelect = accountingReport.getReportType().getTypeSelect();

      if (accountingReport.getExportTypeSelect() == null
          || accountingReport.getExportTypeSelect().isEmpty()
          || typeSelect == 0) {
        response.setFlash(I18n.get(IExceptionMessage.ACCOUNTING_REPORT_4));
        response.setReload(true);
        return;
      }

      if (accountingReportService.isThereTooManyLines(accountingReport)) {
        response.setAlert(
            I18n.get(
                "A large number of recording has been fetched in this period. Edition can take a while. Do you want to proceed ?"));
      }

      logger.debug("Type selected : {}", typeSelect);

      if (accountingReport.getReportType().getTypeSelect()
              == AccountingReportRepository.REPORT_FEES_DECLARATION_PREPERATORY_PROCESS
          || accountingReport.getReportType().getTypeSelect()
              == AccountingReportRepository.REPORT_FEES_DECLARATION_SUPPORT) {

        if (accountingReportService.isThereAlreadyDraftReportInPeriod(accountingReport)) {
          response.setError(
              I18n.get(
                  "There is already an ongoing accounting report of this type in draft status for this same period."));
        }
        if (accountingReportService.isThereAlreadyOngoingDas2ExportInPeriod(accountingReport)) {
          response.setAlert(
              I18n.get(
                  "There is already an ongoing DAS2 export for this period that has not been exported yet. Do you want to proceed ?"));
        }

        accountingReportService.processAccountingReportMoveLines(accountingReport);
      }

      if ((typeSelect >= AccountingReportRepository.EXPORT_ADMINISTRATION
          && typeSelect < AccountingReportRepository.REPORT_ANALYTIC_BALANCE)) {
        MoveLineExportService moveLineExportService = Beans.get(MoveLineExportService.class);

        MetaFile accesssFile = moveLineExportService.exportMoveLine(accountingReport);
        if (typeSelect == AccountingReportRepository.EXPORT_ADMINISTRATION && accesssFile != null) {

          response.setView(
              ActionView.define(I18n.get("Export file"))
                  .model(App.class.getName())
                  .add(
                      "html",
                      "ws/rest/com.axelor.meta.db.MetaFile/"
                          + accesssFile.getId()
                          + "/content/download?v="
                          + accesssFile.getVersion())
                  .param("download", "true")
                  .map());
        }
      } else {
        accountingReportService.setPublicationDateTime(accountingReport);

        String name = accountingReport.getReportType().getName() + " " + accountingReport.getRef();

        String fileLink = accountingReportService.getReportFileLink(accountingReport, name);

        logger.debug("Printing " + name);

        response.setView(ActionView.define(name).add("html", fileLink).map());

        accountingReportService.setStatus(accountingReport);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void createExportFromReport(ActionRequest request, ActionResponse response) {

    AccountingReport accountingReport = request.getContext().asType(AccountingReport.class);
    accountingReport = Beans.get(AccountingReportRepository.class).find(accountingReport.getId());

    AccountingReportService accountingReportService = Beans.get(AccountingReportService.class);
    try {
      AccountingReport accountingExport =
          accountingReportService.createAccountingExportFromReport(
              accountingReport, AccountingReportRepository.EXPORT_N4DS);

      response.setView(
          ActionView.define(I18n.get(IExceptionMessage.ACCOUNTING_REPORT_8))
              .model(AccountingReport.class.getName())
              .add("form", "accounting-report-export-form")
              .add("grid", "accounting-report-export-grid")
              .domain("self.reportType.typeSelect >= 1000 and self.reportType.typeSelect < 2000")
              .param("forceEdit", "true")
              .context("_showRecord", String.valueOf(accountingExport.getId()))
              .map());
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
    actionViewBuilder.param("search-filters", "move-filters");
    actionViewBuilder.domain("self.accountingReport.id = :_accountingReportId");
    actionViewBuilder.context("_accountingReportId", accountingReport.getId());

    response.setView(actionViewBuilder.map());
  }
}
