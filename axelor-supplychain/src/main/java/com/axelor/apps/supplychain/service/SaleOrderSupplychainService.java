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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;

public interface SaleOrderSupplychainService {

  public void updateToConfirmedStatus(SaleOrder saleOrder);

  public String createShipmentCostLine(SaleOrder saleOrder)
      throws AxelorException, ClassNotFoundException;

  boolean alreadyHasShippingCostLine(SaleOrder saleOrder, Product shippingCostProduct);

  SaleOrderLine createShippingCostLine(SaleOrder saleOrder, Product shippingCostProduct)
      throws AxelorException, ClassNotFoundException;

  String removeShipmentCostLine(SaleOrder saleOrder);

  BigDecimal computeExTaxTotalWithoutShippingLines(SaleOrder saleOrder);

  public void setDefaultInvoicedAndDeliveredPartnersAndAddresses(SaleOrder saleOrder);
}
