package com.axelor.apps.stock.service;

import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.WapHistory;

public interface WapHistoryService {

  /**
   * Create a new wap history line in given stock location line. The WAP ({@link
   * StockLocationLine#avgPrice} must already be updated in the stock location line.
   *
   * <p>The origin will be {@link
   * com.axelor.apps.stock.db.repo.WapHistoryRepository#ORIGIN_MANUAL_CORRECTION}
   *
   * @param stockLocationLine a stock location line with updated WAP
   * @return the saved wap history
   */
  WapHistory saveWapHistory(StockLocationLine stockLocationLine);

  /**
   * Create a new wap history line in given stock location line. The WAP ({@link
   * StockLocationLine#avgPrice} must already be updated in the stock location line.
   *
   * <p>Set the origin using given stock move line.
   *
   * @param stockLocationLine a stock location line with updated WAP
   * @param stockMoveLine the stock move line that caused the WAP change
   * @return the saved wap history
   */
  WapHistory saveWapHistory(StockLocationLine stockLocationLine, StockMoveLine stockMoveLine);
}
