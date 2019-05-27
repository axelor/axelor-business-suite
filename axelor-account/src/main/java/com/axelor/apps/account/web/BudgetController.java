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

import com.axelor.apps.account.db.Budget;
import com.axelor.apps.account.db.BudgetLine;
import com.axelor.apps.account.db.repo.BudgetRepository;
import com.axelor.apps.account.service.BudgetService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class BudgetController {

  @Inject protected BudgetService budgetService;

  public void compute(ActionRequest request, ActionResponse response) {
    Budget budget = request.getContext().asType(Budget.class);
    response.setValue("totalAmountExpected", budgetService.compute(budget));
  }

  public void updateLines(ActionRequest request, ActionResponse response) {
    Budget budget = request.getContext().asType(Budget.class);
    budget = Beans.get(BudgetRepository.class).find(budget.getId());
    List<BudgetLine> budgetLineList = budgetService.updateLines(budget);
    response.setValue("budgetLineList", budgetLineList);
  }

  public void generatePeriods(ActionRequest request, ActionResponse response) {
    try {
      Budget budget = request.getContext().asType(Budget.class);
      response.setValue("budgetLineList", budgetService.generatePeriods(budget));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void checkSharedDates(ActionRequest request, ActionResponse response) {
    try {
      Budget budget = request.getContext().asType(Budget.class);
      budgetService.checkSharedDates(budget);
    } catch (Exception e) {
      response.setError(e.getMessage());
    }
  }
}
