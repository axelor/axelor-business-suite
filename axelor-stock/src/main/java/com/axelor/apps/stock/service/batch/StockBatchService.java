package com.axelor.apps.stock.service.batch;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.administration.AbstractBatchService;
import com.axelor.apps.stock.db.StockBatch;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Singleton;

@Singleton
public class StockBatchService extends AbstractBatchService {
  @Override
  protected Class<? extends Model> getModelClass() {
    return StockBatch.class;
  }

  @Override
  public Batch run(Model model) throws AxelorException {
    StockBatch stockBatch = (StockBatch) model;

    switch (stockBatch.getAction()) {
      case RECOMPUTE_STOCK_VALUES:
        // We've to use Beans.get as BatchRecomputeStockValues is not a singleton
        return Beans.get(BatchRecomputeStockValues.class).run(stockBatch);
      default:
        throw new AxelorException(
            model,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BASE_BATCH_1),
            stockBatch.getAction().getValue(),
            stockBatch.getCode());
    }
  }
}
