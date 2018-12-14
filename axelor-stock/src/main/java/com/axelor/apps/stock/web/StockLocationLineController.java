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

import java.util.Map;

import com.axelor.apps.base.db.Unit;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.exception.AxelorException;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class StockLocationLineController {

  @Inject private StockLocationLineRepository stockLocationLineRepo;

  @Inject private StockLocationLineService stockLocationLineService;

  public void updateQty(ActionRequest request, ActionResponse response) throws AxelorException {
    StockLocationLine stockLocationLine = request.getContext().asType(StockLocationLine.class);
    Unit newUnit = stockLocationLine.getUnit();

    if (stockLocationLine.getId() != null) {
      stockLocationLine = stockLocationLineRepo.find(stockLocationLine.getId());
    }

    Map<String, Object> values = stockLocationLineService.updateQty(stockLocationLine, newUnit);
    response.setValues(values);
  }
}
