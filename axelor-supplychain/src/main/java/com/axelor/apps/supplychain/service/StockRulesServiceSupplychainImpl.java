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
package com.axelor.apps.supplychain.service;

import java.math.BigDecimal;
import java.util.List;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.SupplierCatalog;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.purchase.service.PurchaseOrderLineService;
import com.axelor.apps.purchase.service.config.PurchaseConfigService;
import com.axelor.apps.stock.db.Location;
import com.axelor.apps.stock.db.LocationLine;
import com.axelor.apps.stock.db.StockRules;
import com.axelor.apps.stock.db.repo.StockRulesRepository;
import com.axelor.apps.stock.service.StockRulesServiceImpl;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class StockRulesServiceSupplychainImpl extends StockRulesServiceImpl  {

	@Inject
	protected PurchaseOrderServiceSupplychainImpl purchaseOrderServiceSupplychainImpl;

	@Inject
	protected PurchaseOrderLineService purchaseOrderLineService;

	@Inject
	protected PurchaseConfigService purchaseConfigService;

	protected User user;

	@Inject
	private PurchaseOrderRepository purchaseOrderRepo;

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void generatePurchaseOrder(Product product, BigDecimal qty, LocationLine locationLine, int type) throws AxelorException  {

		Location location = locationLine.getLocation();

		//TODO à supprimer après suppression des variantes
		if(location == null)  {
			return;
		}

		StockRules stockRules = this.getStockRules(product, location, type, StockRulesRepository.USE_CASE_STOCK_CONTROL);

		if(stockRules == null)  {
			return;
		}

		if(this.useMinStockRules(locationLine, stockRules, qty, type))  {

			if(stockRules.getOrderAlertSelect() ==  StockRulesRepository.ORDER_ALERT_ALERT)  {

				//TODO

			}
			else if(stockRules.getOrderAlertSelect() == StockRulesRepository.ORDER_ALERT_PRODUCTION_ORDER)  {


			}
			else if(stockRules.getOrderAlertSelect() == StockRulesRepository.ORDER_ALERT_PURCHASE_ORDER)  {

				BigDecimal minReorderQty = getDefaultSupplierMinQty(product);
				BigDecimal qtyToOrder = this.getQtyToOrder(qty, locationLine, type, stockRules, minReorderQty);
				Partner supplierPartner = product.getDefaultSupplierPartner();

				if(supplierPartner != null)  {

					Company company = location.getCompany();

					PurchaseOrder purchaseOrder = purchaseOrderRepo.save(purchaseOrderServiceSupplychainImpl.createPurchaseOrder(
							this.user,
							company,
							null,
							supplierPartner.getCurrency(),
							this.today.plusDays(supplierPartner.getDeliveryDelay()),
							stockRules.getName(),
							null,
							location,
							this.today,
							supplierPartner.getPurchasePriceList(),
							supplierPartner));

					purchaseOrder.addPurchaseOrderLineListItem(
							purchaseOrderLineService.createPurchaseOrderLine(
									purchaseOrder,
									product,
									null,
									null,
									qtyToOrder,
									product.getUnit()));

					purchaseOrderServiceSupplychainImpl.computePurchaseOrder(purchaseOrder);

					purchaseOrderRepo.save(purchaseOrder);

				}


			}




		}

	}

	/**
	 * Get minimum quantity from default supplier.
	 * 
	 * @param product
	 * @return
	 */
	private BigDecimal getDefaultSupplierMinQty(Product product) {
		Partner defaultSupplierPartner = product.getDefaultSupplierPartner();
		List<SupplierCatalog> supplierCatalogList = product.getSupplierCatalogList();
		if (defaultSupplierPartner != null && supplierCatalogList != null) {
			for (SupplierCatalog supplierCatalog : supplierCatalogList) {
				if (supplierCatalog.getSupplierPartner().equals(defaultSupplierPartner)) {
					return supplierCatalog.getMinQty();
				}
			}
		}
		return BigDecimal.ZERO;
	}

}
