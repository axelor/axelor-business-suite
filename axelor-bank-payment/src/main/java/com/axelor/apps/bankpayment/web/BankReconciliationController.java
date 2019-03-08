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
package com.axelor.apps.bankpayment.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.bankpayment.db.BankReconciliation;
import com.axelor.apps.bankpayment.db.BankReconciliationLine;
import com.axelor.apps.bankpayment.db.repo.BankReconciliationLineRepository;
import com.axelor.apps.bankpayment.db.repo.BankReconciliationRepository;
import com.axelor.apps.bankpayment.report.IReport;
import com.axelor.apps.bankpayment.service.bankreconciliation.BankReconciliationService;
import com.axelor.apps.bankpayment.service.bankreconciliation.BankReconciliationValidateService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class BankReconciliationController {

  @Inject BankReconciliationService bankReconciliationService;

  @Inject BankReconciliationValidateService bankReconciliationValidateService;

  @Inject BankReconciliationRepository bankReconciliationRepo;

  @Inject BankReconciliationLineRepository bankReconciliationLineRepo;

  public void loadBankStatement(ActionRequest request, ActionResponse response) {

    try {
      BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
      bankReconciliationService.loadBankStatement(
          bankReconciliationRepo.find(bankReconciliation.getId()));
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void loadOtherBankStatement(ActionRequest request, ActionResponse response) {

    try {
      BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
      bankReconciliation = bankReconciliationRepo.find(bankReconciliation.getId());
      bankReconciliation.setIncludeOtherBankStatements(true);
      bankReconciliationService.loadBankStatement(bankReconciliation, false);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void compute(ActionRequest request, ActionResponse response) {

    try {
      BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
      bankReconciliationService.compute(bankReconciliationRepo.find(bankReconciliation.getId()));
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void validate(ActionRequest request, ActionResponse response) {

    try {
      BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
      bankReconciliationValidateService.validate(
          bankReconciliationRepo.find(bankReconciliation.getId()));
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
          bankReconciliationRepo.find(((Integer) bankReconciliationContext.get("id")).longValue());

      List<HashMap<String, Object>> moveLinesToReconcileContext =
          (List<HashMap<String, Object>>) context.get("toReconcileMoveLineSet");

      Map<String, Object> selectedBankReconciliationLineContext =
          (Map<String, Object>) context.get("_selectedBankReconciliationLine");
      BankReconciliationLine bankReconciliationLine =
          bankReconciliationLineRepo.find(
              ((Integer) selectedBankReconciliationLineContext.get("id")).longValue());

      bankReconciliationValidateService.validateMultipleBankReconciles(
          bankReconciliation, bankReconciliationLine, moveLinesToReconcileContext);
      response.setCanClose(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void setBankDetailsDomain(ActionRequest request, ActionResponse response) {
    BankReconciliation bankReconciliation = request.getContext().asType(BankReconciliation.class);
    String domain = bankReconciliationService.createDomainForBankDetails(bankReconciliation);
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
              .addFormat("pdf")
              .toAttach(bankReconciliation)
              .generate()
              .getFileLink();

      response.setView(ActionView.define("Bank Reconciliation").add("html", fileLink).map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
