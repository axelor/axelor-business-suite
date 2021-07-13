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
package com.axelor.apps.sale.service.saleorder;

import com.axelor.apps.sale.db.Pack;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public interface SaleOrderService {

  public String getFileName(SaleOrder saleOrder);

  public SaleOrder computeEndOfValidityDate(SaleOrder saleOrder);

  @Deprecated
  public String getReportLink(
      SaleOrder saleOrder, String name, String language, boolean proforma, String format)
      throws AxelorException;

  /**
   * Fill {@link SaleOrder#mainInvoicingAddressStr} and {@link SaleOrder#deliveryAddressStr}
   *
   * @param saleOrder
   */
  public void computeAddressStr(SaleOrder saleOrder);

  /**
   * Enable edit order.
   *
   * @param saleOrder
   * @throws AxelorException
   */
  boolean enableEditOrder(SaleOrder saleOrder) throws AxelorException;

  /**
   * Check modified confirmed order before saving it.
   *
   * @param saleOrder
   * @param saleOrderView
   * @throws AxelorException
   */
  void checkModifiedConfirmedOrder(SaleOrder saleOrder, SaleOrder saleOrderView)
      throws AxelorException;

  /**
   * Validate changes.
   *
   * @param saleOrder
   * @throws AxelorException
   */
  void validateChanges(SaleOrder saleOrder) throws AxelorException;

  /**
   * Sort detail lines by sequence.
   *
   * @param saleOrder
   */
  void sortSaleOrderLineList(SaleOrder saleOrder);

  /**
   * Convert PackLines of pack into SaleOrderLines.
   *
   * @param saleOrder
   */
  SaleOrder addPack(SaleOrder saleOrder, Pack pack, BigDecimal packQty);

  /**
   * Handle the creation / updating of complementary products. Called onChange of saleOrderLineList.
   *
   * @param saleOrder
   * @return
   * @throws ClassNotFoundException
   */
  public List<SaleOrderLine> handleComplementaryProducts(SaleOrder saleOrder)
      throws AxelorException, ClassNotFoundException;

  /**
   * Blocks if the given sale order has line with a discount superior to the max authorized
   * discount.
   *
   * @param saleOrder a sale order
   * @throws AxelorException if the sale order is in anomaly
   */
  void checkUnauthorizedDiscounts(SaleOrder saleOrder) throws AxelorException;

  /**
   * To update product quantity with pack header quantity.
   *
   * @param saleOrder
   * @return {@link SaleOrder}
   * @throws AxelorException
   */
  public SaleOrder updateProductQtyWithPackHeaderQty(SaleOrder saleOrder) throws AxelorException;

  /**
   * To manage Complementary Product sale order lines.
   *
   * @param saleOrder
   * @throws AxelorException
   * @throws ClassNotFoundException
   */
  public void manageComplementaryProductSOLines(SaleOrder saleOrder)
      throws AxelorException, ClassNotFoundException;

  SaleOrder seperateInNewQuotation(
      SaleOrder saleOrder, ArrayList<LinkedHashMap<String, Object>> saleOrderLines)
      throws AxelorException;
}
