/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.stock.db.repo;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.service.WeightedAveragePriceService;
import com.axelor.inject.Beans;

public class StockLocationLineStockRepository extends StockLocationLineRepository {

  @Override
  public StockLocationLine save(StockLocationLine entity) {
    Product product = entity.getProduct();
    product.setIsAvgPriceChanged(false);
    if (entity.getIsAvgPriceChanged()) {
	  product.setIsAvgPriceChanged(true);
	  Beans.get(WeightedAveragePriceService.class).computeAvgPriceForProduct(product);
      Beans.get(StockLocationService.class).computeAvgPriceForProduct(product);
    }
    return super.save(entity);
  }
}
