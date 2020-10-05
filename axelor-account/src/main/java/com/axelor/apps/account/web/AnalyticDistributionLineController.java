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

import com.axelor.apps.account.db.AnalyticDistributionTemplate;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Singleton
public class AnalyticDistributionLineController {

  public void computeAmount(ActionRequest request, ActionResponse response) {
    AnalyticMoveLine analyticMoveLine = request.getContext().asType(AnalyticMoveLine.class);

    Context parentContext = request.getContext().getParent();

    if (parentContext != null && parentContext.get("_model").equals(MoveLine.class.getName())) {

      MoveLine moveLine = parentContext.asType(MoveLine.class);

      response.setValue(
          "amount",
          analyticMoveLine
              .getPercentage()
              .multiply(moveLine.getCredit().add(moveLine.getDebit()))
              .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP));
    } else {
      response.setValue(
          "amount", Beans.get(AnalyticMoveLineService.class).computeAmount(analyticMoveLine));
    }
  }

  public void validateLines(ActionRequest request, ActionResponse response) {
    AnalyticDistributionTemplate analyticDistributionTemplate =
        request.getContext().asType(AnalyticDistributionTemplate.class);
    if (!Beans.get(AnalyticMoveLineService.class)
        .validateLines(analyticDistributionTemplate.getAnalyticDistributionLineList())) {
      response.setError(
          "The distribution is wrong, some axes percentage values are higher than 100%");
    }
  }
}
