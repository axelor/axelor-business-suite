package com.axelor.apps.stock.web;

import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.stock.service.WapHistoryService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class StockLocationLineController {

  public void addWapHistoryLine(ActionRequest request, ActionResponse response) {
    try {
      StockLocationLine stockLocationLine = request.getContext().asType(StockLocationLine.class);
      stockLocationLine =
          Beans.get(StockLocationLineRepository.class).find(stockLocationLine.getId());
      Beans.get(WapHistoryService.class).saveWapHistory(stockLocationLine);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
