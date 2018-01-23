/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
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
package com.axelor.apps.supplychain.web;

import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.supplychain.service.StockMoveServiceSupplychain;
import com.axelor.exception.service.TraceBackService;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;

public class StockMoveController {

	@Inject
	private StockMoveServiceSupplychain stockMoveService;
	
	public void addSubLines(ActionRequest request, ActionResponse response) {
    	try {
            StockMove stockMove = request.getContext().asType(StockMove.class);
            response.setValue("stockMoveLineList",  stockMoveService.addSubLines(stockMove.getStockMoveLineList()));
        } catch (Exception e) {
            TraceBackService.trace(response, e);
            response.setReload(true);
        }
    }
    
    public void removeSubLines(ActionRequest request, ActionResponse response) {
        try {
        	StockMove stockMove = request.getContext().asType(StockMove.class);
            response.setValue("stockMoveLineList",  stockMoveService.removeSubLines(stockMove.getStockMoveLineList()));
        } catch (Exception e) {
            TraceBackService.trace(response, e);
            response.setReload(true);
        }
    }
}
