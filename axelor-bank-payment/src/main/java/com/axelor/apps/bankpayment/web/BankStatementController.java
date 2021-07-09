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

import com.axelor.apps.bankpayment.db.BankReconciliation;
import com.axelor.apps.bankpayment.db.BankStatement;
import com.axelor.apps.bankpayment.db.repo.BankStatementRepository;
import com.axelor.apps.bankpayment.service.bankreconciliation.BankReconciliationCreateService;
import com.axelor.apps.bankpayment.service.bankstatement.BankStatementService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class BankStatementController {

  protected BankStatementService bankStatementService;
  protected BankStatementRepository bankStatementRepo;

  @Inject
  public BankStatementController(
      BankStatementService bankStatementService, BankStatementRepository bankStatementRepo) {

    this.bankStatementService = bankStatementService;
    this.bankStatementRepo = bankStatementRepo;
  }

  public void runImport(ActionRequest request, ActionResponse response) {
    try {
      BankStatement bankStatement = request.getContext().asType(BankStatement.class);
      bankStatement = bankStatementRepo.find(bankStatement.getId());
      bankStatementService.runImport(bankStatement, true);
      bankStatementService.generateMoves(bankStatement);

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
    response.setReload(true);
  }

  public void print(ActionRequest request, ActionResponse response) {
    try {
      BankStatement bankStatement = request.getContext().asType(BankStatement.class);
      bankStatement = bankStatementRepo.find(bankStatement.getId());
      String name = bankStatement.getName();
      String fileLink = Beans.get(BankStatementService.class).print(bankStatement);
      response.setView(ActionView.define(name).add("html", fileLink).map());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }

    response.setReload(true);
  }

  public void runBankReconciliation(ActionRequest request, ActionResponse response) {

    try {
      BankStatement bankStatement = request.getContext().asType(BankStatement.class);
      bankStatement = bankStatementRepo.find(bankStatement.getId());
      List<BankReconciliation> bankReconciliationList =
          Beans.get(BankReconciliationCreateService.class)
              .createAllFromBankStatement(bankStatement);

      if (bankReconciliationList != null) {
        response.setView(
            ActionView.define(I18n.get("Bank reconciliations"))
                .model(BankReconciliation.class.getName())
                .add("grid", "bank-reconciliation-grid")
                .add("form", "bank-reconciliation-form")
                .param("search-filters", "bank-reconciliation-filters")
                .domain(
                    "self.id in ("
                        + Joiner.on(",")
                            .join(
                                bankReconciliationList.stream()
                                    .map(BankReconciliation::getId)
                                    .toArray())
                        + ")")
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
    response.setReload(true);
  }

  public void generateMovesFromBankStatement(ActionRequest request, ActionResponse response) {
    BankStatement bankStatement =
        bankStatementRepo.find(request.getContext().asType(BankStatement.class).getId());
    bankStatementService.generateMoves(bankStatement);
  }
}
