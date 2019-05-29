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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.account.db.Move;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.exception.AxelorException;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.util.List;

public interface AccountingCutOffService {

  public List<StockMove> getStockMoves(
      Company company,
      int accountingCutOffTypeSelect,
      LocalDate moveDate,
      Integer limit,
      Integer offset);

  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public List<Move> generateCutOffMoves(
      StockMove stockMove,
      LocalDate moveDate,
      LocalDate reverseMoveDate,
      int accountingCutOffTypeSelect,
      boolean recoveredTax,
      boolean ati,
      String moveDescription,
      boolean includeNotStockManagedProduct)
      throws AxelorException;

  public Move generateCutOffMove(
      StockMove stockMove,
      List<StockMoveLine> sortedStockMoveLine,
      LocalDate moveDate,
      LocalDate originDate,
      boolean isPurchase,
      boolean recoveredTax,
      boolean ati,
      String moveDescription,
      boolean includeNotStockManagedProduct,
      boolean isReverse)
      throws AxelorException;

  List<Long> getStockMoveLines(Batch batch);
}
