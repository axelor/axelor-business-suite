/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.exception.AxelorException;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;

public interface WeightedAveragePriceService {

  @Transactional
  public void computeAvgPriceForProduct(Product product) throws AxelorException;

  public BigDecimal computeAvgPriceForCompany(Product product, Company company);

  /**
   * Only used when we want to get WAP per company but there is no quantity available to compute the
   * correct WAP. This method will simply take the average of WAP.
   *
   * @param product a product with the sum of quantities in stock locations from the given company
   *     equals to 0
   * @param company a company
   * @return the average WAP from all stock location from the given company
   */
  BigDecimal getNotWeightedAveragePricePerCompany(Product product, Company company);
}
