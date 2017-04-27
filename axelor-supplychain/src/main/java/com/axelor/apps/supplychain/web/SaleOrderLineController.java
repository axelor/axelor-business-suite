/**
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

import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.stock.service.LocationLineService;
import com.axelor.apps.supplychain.service.SaleOrderLineServiceSupplyChainImpl;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;

import java.math.BigDecimal;

public class SaleOrderLineController {
	
	@Inject
	protected SaleOrderLineServiceSupplyChainImpl saleOrderLineServiceSupplyChainImpl;
	
	public void computeAnalyticDistribution(ActionRequest request, ActionResponse response) throws AxelorException{
		SaleOrderLine saleOrderLine = request.getContext().asType(SaleOrderLine.class);
		SaleOrder saleOrder = saleOrderLine.getSaleOrder();
		if(saleOrder == null){
			saleOrder = request.getContext().getParentContext().asType(SaleOrder.class);
			saleOrderLine.setSaleOrder(saleOrder);
		}
		if(Beans.get(AppAccountService.class).getAppAccount().getManageAnalyticAccounting()){
			saleOrderLine = saleOrderLineServiceSupplyChainImpl.computeAnalyticDistribution(saleOrderLine);
			response.setValue("analyticMoveLineList", saleOrderLine.getAnalyticMoveLineList());
		}
	}
	
	public void createAnalyticDistributionWithTemplate(ActionRequest request, ActionResponse response) throws AxelorException{
		SaleOrderLine saleOrderLine = request.getContext().asType(SaleOrderLine.class);
		SaleOrder saleOrder = saleOrderLine.getSaleOrder();
		if(saleOrder == null){
			saleOrder = request.getContext().getParentContext().asType(SaleOrder.class);
			saleOrderLine.setSaleOrder(saleOrder);
		}
		if(saleOrderLine.getAnalyticDistributionTemplate() != null){
			saleOrderLine = saleOrderLineServiceSupplyChainImpl.createAnalyticDistributionWithTemplate(saleOrderLine);
			response.setValue("analyticMoveLineList", saleOrderLine.getAnalyticMoveLineList());
		}
		else{
			throw new AxelorException(I18n.get("No template selected"), IException.CONFIGURATION_ERROR);
		}
	}

	public void checkStocks(ActionRequest request, ActionResponse response) {
	    SaleOrderLine saleOrderLine = request.getContext().asType(SaleOrderLine.class);
		SaleOrder saleOrder = request.getContext().getParentContext().asType(SaleOrder.class);
		if (saleOrder.getLocation() == null) {
			return;
		}
		try {
			if (saleOrderLine.getSaleSupplySelect() != SaleOrderLineRepository.SALE_SUPPLY_FROM_STOCK) {
				return;
			}
			//Use the unit to get the right quantity
			Unit unit = saleOrderLine.getProduct().getUnit();
			BigDecimal qty = saleOrderLine.getQty();
			if(unit != null && !unit.equals(saleOrderLine.getUnit())){
				qty = Beans.get(UnitConversionService.class).convertWithProduct(saleOrderLine.getUnit(), unit, qty, saleOrderLine.getProduct());
			}
			Beans.get(LocationLineService.class).checkIfEnoughStock(
					saleOrder.getLocation(),
					saleOrderLine.getProduct(),
					qty
			);
		} catch (AxelorException e) {
			response.setAlert(e.getLocalizedMessage());
		}
	}
}
