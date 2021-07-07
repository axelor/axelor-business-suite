package com.axelor.apps.stock.web;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.stock.service.batch.StockBatchService;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class StockBatchController {
  private StockBatchService stockBatchService;

  @Inject
  public StockBatchController(StockBatchService stockBatchService) {
    this.stockBatchService = stockBatchService;
  }

  @SuppressWarnings("unused")
  public void runBatch(ActionRequest request, ActionResponse response) throws AxelorException {
    Batch b = stockBatchService.run((String) request.getContext().get("code"));
    response.setFlash(
        StringUtils.isBlank(b.getComments()) ? I18n.get("Batch completed") : b.getComments());
    response.setReload(true);
  }
}
