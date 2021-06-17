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
package com.axelor.apps.bankpayment.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.bankpayment.db.BankReconciliation;
import com.axelor.apps.bankpayment.db.BankReconciliationLine;
import com.axelor.apps.bankpayment.db.repo.BankReconciliationLineRepository;
import com.axelor.apps.bankpayment.db.repo.BankReconciliationRepository;
import com.axelor.apps.bankpayment.report.IReport;
import com.axelor.apps.bankpayment.service.bankreconciliation.BankReconciliationService;
import com.axelor.apps.bankpayment.service.bankreconciliation.BankReconciliationValidateService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.db.EntityHelper;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class BankReconciliationController {

  public void loadBankStatement(ActionRequest request, ActionResponse response) {

    try {
      BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
      Beans.get(BankReconciliationService.class)
          .loadBankStatement(
              Beans.get(BankReconciliationRepository.class).find(bankReconciliation.getId()));
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void loadOtherBankStatement(ActionRequest request, ActionResponse response) {

    try {
      BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
      bankReconciliation =
          Beans.get(BankReconciliationRepository.class).find(bankReconciliation.getId());
      bankReconciliation.setIncludeOtherBankStatements(true);
      Beans.get(BankReconciliationService.class).loadBankStatement(bankReconciliation, false);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void compute(ActionRequest request, ActionResponse response) {

    try {
      BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
      Beans.get(BankReconciliationService.class)
          .compute(Beans.get(BankReconciliationRepository.class).find(bankReconciliation.getId()));
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void validate(ActionRequest request, ActionResponse response) {

    try {
      BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
      Beans.get(BankReconciliationValidateService.class)
          .validate(Beans.get(BankReconciliationRepository.class).find(bankReconciliation.getId()));
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void validateMultipleReconcile(ActionRequest request, ActionResponse response) {

    try {
      Context context = request.getContext();

      Map<String, Object> bankReconciliationContext =
          (Map<String, Object>) context.get("_bankReconciliation");

      BankReconciliation bankReconciliation =
          Beans.get(BankReconciliationRepository.class)
              .find(((Integer) bankReconciliationContext.get("id")).longValue());

      List<HashMap<String, Object>> moveLinesToReconcileContext =
          (List<HashMap<String, Object>>) context.get("toReconcileMoveLineSet");

      Map<String, Object> selectedBankReconciliationLineContext =
          (Map<String, Object>) context.get("_selectedBankReconciliationLine");
      BankReconciliationLine bankReconciliationLine =
          Beans.get(BankReconciliationLineRepository.class)
              .find(((Integer) selectedBankReconciliationLineContext.get("id")).longValue());

      Beans.get(BankReconciliationValidateService.class)
          .validateMultipleBankReconciles(
              bankReconciliation, bankReconciliationLine, moveLinesToReconcileContext);
      response.setCanClose(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void setBankDetailsDomain(ActionRequest request, ActionResponse response) {
    BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
    String domain =
        Beans.get(BankReconciliationService.class).createDomainForBankDetails(bankReconciliation);
    // if nothing was found for the domain, we set it at a default value.
    if (domain.equals("")) {
      response.setAttr("bankDetails", "domain", "self.id IN (0)");
    } else {
      response.setAttr("bankDetails", "domain", domain);
    }
  }

  public void printBankReconciliation(ActionRequest request, ActionResponse response) {
    BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
    try {
      String fileLink =
          ReportFactory.createReport(
                  "bankPaymentBankReconciliation",
                  bankReconciliation.getCompany(),
                  IReport.BANK_RECONCILIATION,
                  "Bank Reconciliation" + "-${date}")
              .addParam("BankReconciliationId", bankReconciliation.getId())
              .addParam("Locale", ReportSettings.getPrintingLocale(null))
              .addParam(
                  "Timezone",
                  bankReconciliation.getCompany() != null
                      ? bankReconciliation.getCompany().getTimezone()
                      : null)
              .addFormat("pdf")
              .toAttach(bankReconciliation)
              .generate()
              .getFileLink();

      response.setView(ActionView.define("Bank Reconciliation").add("html", fileLink).map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void setJournalDomain(ActionRequest request, ActionResponse response) {

    BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
    String journalIds = null;

    if (EntityHelper.getEntity(bankReconciliation).getBankDetails() != null) {
      journalIds = Beans.get(BankReconciliationService.class).getJournalDomain(bankReconciliation);
    }

    if (Strings.isNullOrEmpty(journalIds)) {
      response.setAttr("journal", "domain", "self.id IN (0)");
    } else {
      response.setAttr("journal", "domain", "self.id IN(" + journalIds + ")");
    }
  }

  public void setJournal(ActionRequest request, ActionResponse response) {

    BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
    Journal journal = null;

    if (EntityHelper.getEntity(bankReconciliation).getBankDetails() != null) {
      journal = Beans.get(BankReconciliationService.class).getJournal(bankReconciliation);
    }
    response.setValue("journal", journal);
  }

  public void setCashAccountDomain(ActionRequest request, ActionResponse response) {

    BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
    String cashAccountIds = null;

    if (EntityHelper.getEntity(bankReconciliation).getBankDetails() != null) {
      cashAccountIds =
          Beans.get(BankReconciliationService.class).getCashAccountDomain(bankReconciliation);
    }

    if (Strings.isNullOrEmpty(cashAccountIds)) {
      response.setAttr("cashAccount", "domain", "self.id IN (0)");
    } else {
      response.setAttr("cashAccount", "domain", "self.id IN(" + cashAccountIds + ")");
    }
  }

  public void setCashAccount(ActionRequest request, ActionResponse response) {

    BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
    Account cashAccount = null;

    if (EntityHelper.getEntity(bankReconciliation).getBankDetails() != null) {
      cashAccount = Beans.get(BankReconciliationService.class).getCashAccount(bankReconciliation);
    }
    response.setValue("cashAccount", cashAccount);
  }
}
