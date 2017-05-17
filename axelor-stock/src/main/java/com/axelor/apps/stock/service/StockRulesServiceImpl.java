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
package com.axelor.apps.stock.service;

import java.math.BigDecimal;

import java.time.LocalDate;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.stock.db.Location;
import com.axelor.apps.stock.db.LocationLine;
import com.axelor.apps.stock.db.StockRules;
import com.axelor.apps.stock.db.repo.StockRulesRepository;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class StockRulesServiceImpl implements StockRulesService  {

	protected LocalDate today;

	protected User user;
	
	@Inject
	protected StockRulesRepository stockRuleRepo;

	@Inject
	public StockRulesServiceImpl() {

		this.today = Beans.get(AppBaseService.class).getTodayDate();
		this.user = AuthUtils.getUser();
	}


	public void generateOrder(Product product, BigDecimal qty, LocationLine locationLine, int type) throws AxelorException{
		this.generatePurchaseOrder(product, qty, locationLine, type);
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void generatePurchaseOrder(Product product, BigDecimal qty, LocationLine locationLine, int type) throws AxelorException  {

		Location location = locationLine.getLocation();

		//TODO à supprimer après suppression des variantes
		if(location == null)  {
			return;
		}

		StockRules stockRules = this.getStockRules(product, location, type);

		if(stockRules == null)  {
			return;
		}

		if(this.useMinStockRules(locationLine, stockRules, qty, type))  {

			if(stockRules.getOrderAlertSelect() == StockRulesRepository.ORDER_ALERT_ALERT)  {

				//TODO
			}


		}

	}

	@Override
	public BigDecimal getQtyToOrder(BigDecimal qty, LocationLine locationLine, int type, StockRules stockRules) {
		BigDecimal qtyToOrder;
		if (type == StockRulesRepository.TYPE_CURRENT) {
			qtyToOrder = locationLine.getCurrentQty().subtract(qty);
		}
		else {
			qtyToOrder = locationLine.getFutureQty().subtract(qty);
		}
		qtyToOrder = stockRules.getIdealQty().subtract(qtyToOrder);
		qtyToOrder = qtyToOrder.max(stockRules.getReOrderQty());
		return qtyToOrder;
	}

	@Override
	public boolean useMinStockRules(LocationLine locationLine, StockRules stockRules, BigDecimal qty, int type)  {

		BigDecimal currentQty = locationLine.getCurrentQty();
		BigDecimal futureQty = locationLine.getFutureQty();

		BigDecimal minQty = stockRules.getMinQty();

		if(type == StockRulesRepository.TYPE_CURRENT)  {

			if(currentQty.compareTo(minQty) >= 0 && (currentQty.subtract(qty)).compareTo(minQty) == -1)  {
				return true;
			}

		}
		else  if(type == StockRulesRepository.TYPE_FUTURE){

			if(futureQty.compareTo(minQty) >= 0 && (futureQty.subtract(qty)).compareTo(minQty) == -1)  {
				return true;
			}

		}
		return false;

	}

	@Override
	public StockRules getStockRules(Product product, Location location, int type)  {

		return stockRuleRepo.all().filter("self.product = ?1 AND self.location = ?2 AND self.typeSelect = ?3", product, location, type).fetchOne();

		//TODO , plusieurs régles min de stock par produit (achat a 500 et production a 100)...

	}


}
