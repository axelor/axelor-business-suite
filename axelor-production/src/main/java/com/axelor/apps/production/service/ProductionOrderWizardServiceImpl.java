/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2014 Axelor (<http://axelor.com>).
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
package com.axelor.apps.production.service;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
//import com.axelor.apps.organisation.db.Project;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.ProductionOrder;
import com.axelor.apps.production.db.repo.ProductionOrderRepository;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.rpc.Context;
import com.google.inject.Inject;

public class ProductionOrderWizardServiceImpl extends ProductionOrderRepository implements ProductionOrderWizardService {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Inject
	protected ProductionOrderService productionOrderService;
	
	@Inject
	protected BillOfMaterialService billOfMaterialService;
	
	@Inject
	protected ProductRepository productRepo;
	
	
	public Long validate(Context context) throws AxelorException  {
	
		Map<String, Object> bomContext = (Map<String, Object>) context.get("billOfMaterial");
		BillOfMaterial billOfMaterial = billOfMaterialService.find(((Integer) bomContext.get("id")).longValue());
		
		BigDecimal qty = new BigDecimal((String)context.get("qty"));
		
		Product product = null;
		
		if(context.get("product") != null)  {
			Map<String, Object> productContext = (Map<String, Object>) context.get("product");
			product = productRepo.find(((Integer) productContext.get("id")).longValue());
		}
		else  {
			product = billOfMaterial.getProduct();
		}
		ProductionOrder productionOrder = productionOrderService.generateProductionOrder(product, billOfMaterial, qty);
		
		if(productionOrder != null)  {
			return productionOrder.getId();
		}
		else  {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.PRODUCTION_ORDER_2)),IException.CONFIGURATION_ERROR);
		}
	}
	
}
