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
package com.axelor.apps.production.service.manuforder;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.ManufOrder;
import com.axelor.apps.production.db.ProdProduct;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.exception.AxelorException;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ManufOrderService {

  public static int DEFAULT_PRIORITY = 2;
  public static int DEFAULT_PRIORITY_INTERVAL = 10;
  public static boolean IS_TO_INVOICE = false;

  public static int ORIGIN_TYPE_MRP = 1;
  public static int ORIGIN_TYPE_SALE_ORDER = 2;
  public static int ORIGIN_TYPE_OTHER = 3;

  /**
   * @param product
   * @param qtyRequested
   * @param priority
   * @param isToInvoice
   * @param billOfMaterial
   * @param plannedStartDateT
   * @param originType
   *     <li>1 : MRP
   *     <li>2 : Sale order
   *     <li>3 : Other
   * @return
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public ManufOrder generateManufOrder(
      Product product,
      BigDecimal qtyRequested,
      int priority,
      boolean isToInvoice,
      BillOfMaterial billOfMaterial,
      LocalDateTime plannedStartDateT,
      int originType)
      throws AxelorException;

  public void createToConsumeProdProductList(ManufOrder manufOrder);

  /**
   * Compute the quantity on generated prod product line. If the quantity of the bill of material is
   * equal to the quantity of manuf order then the prod product line will have the same quantity as
   * configured line.
   *
   * @param bomQty quantity of the bill of material.
   * @param manufOrderQty quantity configured of the manuf order.
   * @param lineQty quantity of the line.
   * @return the quantity for the prod product line.
   */
  BigDecimal computeToConsumeProdProductLineQuantity(
      BigDecimal bomQty, BigDecimal manufOrderQty, BigDecimal lineQty);

  public void createToProduceProdProductList(ManufOrder manufOrder);

  public ManufOrder createManufOrder(
      Product product,
      BigDecimal qty,
      int priority,
      boolean isToInvoice,
      Company company,
      BillOfMaterial billOfMaterial,
      LocalDateTime plannedStartDateT)
      throws AxelorException;

  @Transactional
  public void preFillOperations(ManufOrder manufOrder) throws AxelorException;

  public String getManufOrderSeq(ManufOrder manufOrder) throws AxelorException;

  public boolean isManagedConsumedProduct(BillOfMaterial billOfMaterial);

  public BigDecimal getProducedQuantity(
      ManufOrder manufOrder, LocalDate previousCostSheetDate, int calculationTypeSelect);

  /**
   * Generate waste stock move.
   *
   * @param manufOrder
   * @return wasteStockMove
   */
  public StockMove generateWasteStockMove(ManufOrder manufOrder) throws AxelorException;

  /**
   * Update planned qty in {@link ManufOrder#toConsumeProdProductList} and {@link
   * ManufOrder#toProduceProdProductList} then update quantity in stock move lines to match the new
   * planned qty.
   *
   * @param manufOrder
   */
  void updatePlannedQty(ManufOrder manufOrder) throws AxelorException;

  /**
   * Update real qty in {@link ManufOrder#consumedStockMoveLineList} and {@link
   * ManufOrder#producedStockMoveLineList}
   *
   * @param manufOrder
   * @param qtyToUpdate
   */
  void updateRealQty(ManufOrder manufOrder, BigDecimal qtyToUpdate) throws AxelorException;

  /**
   * Updates the diff prod product list.
   *
   * @param manufOrder
   * @return the updated manufOrder
   * @throws AxelorException
   */
  ManufOrder updateDiffProdProductList(ManufOrder manufOrder) throws AxelorException;

  /**
   * Compute the difference between the two lists for the given manuf order.
   *
   * @param manufOrder
   * @param prodProductList
   * @param stockMoveLineList
   * @return a list of ProdProduct
   * @throws AxelorException
   */
  List<ProdProduct> createDiffProdProductList(
      ManufOrder manufOrder,
      List<ProdProduct> prodProductList,
      List<StockMoveLine> stockMoveLineList)
      throws AxelorException;

  /**
   * Compute the difference between the two lists.
   *
   * @param prodProductList
   * @param stockMoveLineList
   * @return a list of ProdProduct
   * @throws AxelorException
   */
  List<ProdProduct> createDiffProdProductList(
      List<ProdProduct> prodProductList, List<StockMoveLine> stockMoveLineList)
      throws AxelorException;

  /**
   * On changing {@link ManufOrder#consumedStockMoveLineList}, we also update the stock move.
   *
   * @param manufOrder
   */
  void updateConsumedStockMoveFromManufOrder(ManufOrder manufOrder) throws AxelorException;

  /**
   * On changing {@link ManufOrder#producedStockMoveLineList}, we also update the stock move.
   *
   * @param manufOrder
   * @throws AxelorException
   */
  void updateProducedStockMoveFromManufOrder(ManufOrder manufOrder) throws AxelorException;

  /**
   * Compute {@link ManufOrder#diffConsumeProdProductList}, then add and remove lines to the stock
   * move to match the stock move line list. The list can be from manuf order or operation order.
   *
   * @param stockMoveLineList
   * @param stockMove
   * @throws AxelorException
   */
  void updateStockMoveFromManufOrder(List<StockMoveLine> stockMoveLineList, StockMove stockMove)
      throws AxelorException;
}
