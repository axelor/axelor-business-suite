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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.google.inject.Inject;

public class InvoiceLineSupplychainService extends InvoiceLineService  {
	
	@Inject
	public InvoiceLineSupplychainService(AccountManagementService accountManagementService, CurrencyService currencyService, PriceListService priceListService, 
			AppAccountService appAccountService, AnalyticMoveLineService analyticMoveLineService, ProductService productService)  {
		
		super(accountManagementService, currencyService, priceListService, appAccountService, analyticMoveLineService, productService);
		
	}
	
	@Override
	public Unit getUnit(Product product, boolean isPurchase){
		if(isPurchase){
			if(product.getPurchasesUnit() != null){
				return product.getPurchasesUnit();
			}
			else{
				return product.getUnit();
			}
		}
		else{
			if(product.getSalesUnit() != null){
				return product.getPurchasesUnit();
			}
			else{
				return product.getUnit();
			}
		}
	}
}
