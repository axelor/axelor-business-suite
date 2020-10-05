package com.axelor.apps.account.web;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;

public class AnalyticMoveLineController {

  public void setDefaultVaules(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    Context parentContext = context.getParent();

    if (parentContext != null && parentContext.get("_model").equals(MoveLine.class.getName())) {
      AnalyticMoveLine analyticMoveLine = context.asType(AnalyticMoveLine.class);

      MoveLine moveLine = parentContext.asType(MoveLine.class);

      response.setValue("typeSelect", AnalyticMoveLineRepository.STATUS_REAL_ACCOUNTING);

      if (moveLine.getAccount() != null) {
        response.setValue("accountType", moveLine.getAccount().getAccountType());
      }

      if (moveLine.getDate() != null) {
        response.setValue("date", moveLine.getDate());
      }
    }
  }
}
