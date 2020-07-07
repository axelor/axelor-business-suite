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
package com.axelor.apps.supplychain.service.batch;

import com.axelor.apps.account.db.Move;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.db.SupplychainBatch;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.AccountingCutOffService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchAccountingCutOff extends BatchStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AccountingCutOffService cutOffService;
  protected StockMoveRepository stockMoveRepository;

  @Inject
  public BatchAccountingCutOff(
      AccountingCutOffService cutOffService, StockMoveRepository stockMoveRepository) {
    super();
    this.cutOffService = cutOffService;
    this.stockMoveRepository = stockMoveRepository;
  }

  @Override
  protected void process() {

    int offset = 0;
    SupplychainBatch supplychainBatch = batch.getSupplychainBatch();
    LocalDate moveDate = supplychainBatch.getMoveDate();

    LocalDate reverseMoveDate = supplychainBatch.getReverseMoveDate();
    boolean recoveredTax = supplychainBatch.getRecoveredTax();
    boolean ati = supplychainBatch.getAti();
    String moveDescription = supplychainBatch.getMoveDescription();
    int accountingCutOffTypeSelect = supplychainBatch.getAccountingCutOffTypeSelect();
    updateBatch(moveDate, accountingCutOffTypeSelect);
    Company company = supplychainBatch.getCompany();
    boolean includeNotStockManagedProduct = supplychainBatch.getIncludeNotStockManagedProduct();
    int fetchLimit = getFetchLimit();

    if (accountingCutOffTypeSelect == 0) {
      return;
    }

    List<StockMove> stockMoveList;

    while (!(stockMoveList =
            cutOffService.getStockMoves(
                company, accountingCutOffTypeSelect, moveDate, fetchLimit, offset))
        .isEmpty()) {

      findBatch();

      for (StockMove stockMove : stockMoveList) {
        ++offset;

        try {
          List<Move> moveList =
              cutOffService.generateCutOffMoves(
                  stockMove,
                  moveDate,
                  reverseMoveDate,
                  accountingCutOffTypeSelect,
                  recoveredTax,
                  ati,
                  moveDescription,
                  includeNotStockManagedProduct);

          if (moveList != null && !moveList.isEmpty()) {
            updateStockMove(stockMove);

            for (Move move : moveList) {
              updateAccountMove(move, false);
            }
          }

        } catch (AxelorException e) {
          TraceBackService.trace(
              new AxelorException(
                  e, e.getCategory(), I18n.get("StockMove") + " %s", stockMove.getStockMoveSeq()),
              ExceptionOriginRepository.INVOICE_ORIGIN,
              batch.getId());
          incrementAnomaly();
          break;
        } catch (Exception e) {
          TraceBackService.trace(
              new Exception(
                  String.format(I18n.get("StockMove") + " %s", stockMove.getStockMoveSeq()), e),
              ExceptionOriginRepository.INVOICE_ORIGIN,
              batch.getId());
          incrementAnomaly();
          LOG.error("Anomaly generated for the stock move {}", stockMove.getStockMoveSeq());
          break;
        }
      }

      JPA.clear();
    }
  }

  @Transactional
  public void updateBatch(LocalDate moveDate, int accountingCutOffTypeSelect) {
    batch.setMoveDate(moveDate);
    batch.setAccountingCutOffTypeSelect(accountingCutOffTypeSelect);
    batchRepo.save(batch);
  }

  /**
   * As {@code batch} entity can be detached from the session, call {@code Batch.find()} get the
   * entity in the persistant context. Warning : {@code batch} entity have to be saved before.
   */
  @Override
  protected void stop() {

    String comment = I18n.get(IExceptionMessage.ACCOUNTING_CUT_OFF_GENERATION_REPORT) + " ";
    comment +=
        String.format(
            "\t* %s " + I18n.get(IExceptionMessage.ACCOUNTING_CUT_OFF_STOCK_MOVE_PROCESSED) + "\n",
            batch.getDone());
    comment +=
        String.format(
            "\t" + I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ALARM_ENGINE_BATCH_4),
            batch.getAnomaly());

    super.stop();
    addComment(comment);
  }
}
