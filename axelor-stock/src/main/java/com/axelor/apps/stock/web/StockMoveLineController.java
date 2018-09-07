/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.stock.web;

import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.service.StockMoveLineService;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.Map;

@Singleton
public class StockMoveLineController {

  @Inject protected StockMoveLineService stockMoveLineService;

  public void compute(ActionRequest request, ActionResponse response) throws AxelorException {
    StockMoveLine stockMoveLine = request.getContext().asType(StockMoveLine.class);
    StockMove stockMove = stockMoveLine.getStockMove();
    if (stockMove == null) {
      Context parentContext = request.getContext().getParent();
      Context superParentContext = parentContext.getParent();
      if (parentContext.getContextClass().equals(StockMove.class)) {
        stockMove = parentContext.asType(StockMove.class);
      } else if (superParentContext.getContextClass().equals(StockMove.class)) {
        stockMove = superParentContext.asType(StockMove.class);
      } else {
        return;
      }
    }
    stockMoveLine = stockMoveLineService.compute(stockMoveLine, stockMove);
    response.setValue("unitPriceUntaxed", stockMoveLine.getUnitPriceUntaxed());
    response.setValue("unitPriceTaxed", stockMoveLine.getUnitPriceTaxed());
  }

  public void setProductInfo(ActionRequest request, ActionResponse response) {

    StockMoveLine stockMoveLine;

    try {
      stockMoveLine = request.getContext().asType(StockMoveLine.class);
      StockMove stockMove = stockMoveLine.getStockMove();

      if (stockMove == null) {
        stockMove = request.getContext().getParent().asType(StockMove.class);
      }

      stockMoveLineService.setProductInfo(stockMoveLine, stockMove.getCompany());
      response.setValues(stockMoveLine);
    } catch (Exception e) {
      stockMoveLine = new StockMoveLine();
      response.setValues(Mapper.toMap(stockMoveLine));
      TraceBackService.trace(response, e, ResponseMessageType.INFORMATION);
    }
  }

  public void emptyLine(ActionRequest request, ActionResponse response) {
    StockMoveLine stockMoveLine = request.getContext().asType(StockMoveLine.class);
    if (stockMoveLine.getLineTypeSelect() != StockMoveLineRepository.TYPE_NORMAL) {
      Map<String, Object> newStockMoveLine = Mapper.toMap(new StockMoveLine());
      newStockMoveLine.put("qty", BigDecimal.ZERO);
      newStockMoveLine.put("id", stockMoveLine.getId());
      newStockMoveLine.put("version", stockMoveLine.getVersion());
      newStockMoveLine.put("lineTypeSelect", stockMoveLine.getLineTypeSelect());
      response.setValues(newStockMoveLine);
    }
  }
}
