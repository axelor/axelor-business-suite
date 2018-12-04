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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;
import java.util.List;

public interface PurchaseOrderStockService {

  /**
   * Méthode permettant de créer un StockMove à partir d'un PurchaseOrder.
   *
   * @param purchaseOrder une commande
   * @throws AxelorException Aucune séquence de StockMove n'a été configurée
   */
  public List<Long> createStockMoveFromPurchaseOrder(PurchaseOrder purchaseOrder)
      throws AxelorException;

  public StockMoveLine createStockMoveLine(
      StockMove stockMove,
      StockMove qualityStockMove,
      PurchaseOrderLine purchaseOrderLine,
      BigDecimal qty)
      throws AxelorException;

  public void cancelReceipt(PurchaseOrder purchaseOrder) throws AxelorException;

  public boolean isStockMoveProduct(PurchaseOrderLine purchaseOrderLine) throws AxelorException;

  public boolean isStockMoveProduct(
      PurchaseOrderLine purchaseOrderLine, PurchaseOrder purchaseOrder) throws AxelorException;

  // Check if existing at least one stockMove not canceled for the purchaseOrder
  public boolean existActiveStockMoveForPurchaseOrder(Long purchaseOrderId);

  public void updateReceiptState(PurchaseOrder purchaseOrder) throws AxelorException;
}
