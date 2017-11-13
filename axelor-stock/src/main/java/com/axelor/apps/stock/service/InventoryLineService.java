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

import com.axelor.apps.base.db.Product;
import com.axelor.apps.stock.db.TrackingNumber;
import com.axelor.inject.Beans;
import com.axelor.apps.stock.db.Inventory;
import com.axelor.apps.stock.db.InventoryLine;
import com.axelor.apps.stock.db.Location;
import com.axelor.apps.stock.db.LocationLine;

public class InventoryLineService {
	

	public InventoryLine createInventoryLine(Inventory inventory, Product product, BigDecimal currentQty, String rack, TrackingNumber trackingNumber)  {
		
		InventoryLine inventoryLine = new InventoryLine();
		inventoryLine.setInventory(inventory);
		inventoryLine.setProduct(product);
		inventoryLine.setRack(rack);
		inventoryLine.setCurrentQty(currentQty);
		inventoryLine.setTrackingNumber(trackingNumber);
		
		return inventoryLine;
		
	}

	public InventoryLine updateInventoryLine(InventoryLine inventoryLine) {
		
		Location location = inventoryLine.getInventory().getLocation();
		Product product = inventoryLine.getProduct();
		
		if (product != null) {
			LocationLine locationLine = Beans.get(LocationLineService.class).getLocationLine(location, product);
			
			if (locationLine != null) {
				inventoryLine.setCurrentQty(locationLine.getCurrentQty());
				inventoryLine.setRack(locationLine.getRack());
			} else {
				inventoryLine.setCurrentQty(null);
				inventoryLine.setRack(null);
			}
		}
		
		return inventoryLine;
	}

	
}
