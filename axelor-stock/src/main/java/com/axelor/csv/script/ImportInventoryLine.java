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
package com.axelor.csv.script;

import java.math.BigDecimal;
import java.util.Map;

import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.stock.db.InventoryLine;
import com.axelor.apps.stock.db.TrackingNumber;
import com.axelor.apps.stock.db.TrackingNumberConfiguration;
import com.axelor.apps.stock.db.repo.InventoryLineRepository;
import com.axelor.apps.stock.service.InventoryLineService;
import com.axelor.apps.stock.service.TrackingNumberService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class ImportInventoryLine {

	@Inject
	private InventoryLineRepository inventoryLineRepo;

	@Inject
	private InventoryLineService inventoryLineService;

	@Inject
	private TrackingNumberService trackingNumberService;

	@Inject
	private AppBaseService appBaseService;

	@Transactional
	public Object importInventoryLine(Object bean, Map<String,Object> values) throws AxelorException {

		assert bean instanceof InventoryLine;

		InventoryLine inventoryLine = (InventoryLine) bean;

		TrackingNumberConfiguration trackingNumberConfig = inventoryLine.getProduct().getTrackingNumberConfiguration();

		BigDecimal qtyByTracking = BigDecimal.ONE;

		BigDecimal realQtyRemaning = inventoryLine.getRealQty();
		
		TrackingNumber trackingNumber;

		if (trackingNumberConfig != null) {

			if (trackingNumberConfig.getGenerateProductionAutoTrackingNbr()) {
				qtyByTracking = trackingNumberConfig.getProductionQtyByTracking();
			} else if (trackingNumberConfig.getGeneratePurchaseAutoTrackingNbr()) {
				qtyByTracking = trackingNumberConfig.getPurchaseQtyByTracking();
			} else {
				qtyByTracking = trackingNumberConfig.getSaleQtyByTracking();
			}

			InventoryLine inventoryLineNew;

			for (int i=0;i<inventoryLine.getRealQty().intValue();i+=qtyByTracking.intValue()) {
				
				trackingNumber = trackingNumberService.createTrackingNumber(
						inventoryLine.getProduct(),
						inventoryLine.getInventory().getStockLocation().getCompany(),
						appBaseService.getTodayDate()
						);
				
				if (realQtyRemaning.compareTo(qtyByTracking)<0) {
					trackingNumber.setLotSize(realQtyRemaning);
				} else {
					trackingNumber.setLotSize(qtyByTracking);
				}

				inventoryLineNew = inventoryLineService.createInventoryLine(
						inventoryLine.getInventory(),
						inventoryLine.getProduct(),
						inventoryLine.getCurrentQty(),
						inventoryLine.getRack(),
						trackingNumber
						);
				
				if (realQtyRemaning.compareTo(qtyByTracking)<0) {
					inventoryLineNew.setRealQty(realQtyRemaning);
				} else {
					inventoryLineNew.setRealQty(qtyByTracking);
					realQtyRemaning = realQtyRemaning.subtract(qtyByTracking);
				}

				inventoryLineRepo.save(inventoryLineNew);
			}
			return null;
		}
		return bean;
	}
}