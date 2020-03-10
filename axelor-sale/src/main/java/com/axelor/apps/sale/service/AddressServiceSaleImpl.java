/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.sale.service;

import com.axelor.apps.base.service.AddressServiceImpl;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.db.JPA;

public class AddressServiceSaleImpl extends AddressServiceImpl {
  static {
    registerCheckUsedFunc(AddressServiceSaleImpl::checkAddressUsedSale);
  }

  protected static boolean checkAddressUsedSale(Long addressId) {
    return JPA.all(SaleOrder.class)
            .filter("self.mainInvoicingAddress.id = ?1 OR self.deliveryAddress.id = ?1", addressId)
            .fetchOne()
        != null;
  }
}
