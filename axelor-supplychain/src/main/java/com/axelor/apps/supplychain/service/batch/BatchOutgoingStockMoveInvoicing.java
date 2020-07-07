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

import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.db.SupplychainBatch;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.StockMoveInvoiceService;
import com.axelor.db.JPA;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import javax.persistence.TypedQuery;

public class BatchOutgoingStockMoveInvoicing extends BatchStrategy {

  private StockMoveInvoiceService stockMoveInvoiceService;

  @Inject
  public BatchOutgoingStockMoveInvoicing(StockMoveInvoiceService stockMoveInvoiceService) {
    this.stockMoveInvoiceService = stockMoveInvoiceService;
  }

  @Override
  protected void process() {
    SupplychainBatch supplychainBatch = batch.getSupplychainBatch();
    int fetchLimit = getFetchLimit();
    List<Long> anomalyList = Lists.newArrayList(0L);
    SaleOrderRepository saleRepo = Beans.get(SaleOrderRepository.class);

    TypedQuery<StockMove> query =
        JPA.em()
            .createQuery(
                "SELECT self FROM StockMove self "
                    + "WHERE self.statusSelect = :statusSelect "
                    + "AND self.originTypeSelect LIKE :typeSaleOrder "
                    + "AND (SELECT count(invoice.id) FROM Invoice invoice WHERE invoice.statusSelect != :invoiceStatusCanceled AND invoice MEMBER OF self.invoiceSet) = 0"
                    + "AND self.id NOT IN (:anomalyList) "
                    + "AND self.partner.id NOT IN ("
                    + Beans.get(BlockingService.class)
                        .listOfBlockedPartner(
                            supplychainBatch.getCompany(), BlockingRepository.INVOICING_BLOCKING)
                    + ") "
                    + "AND :batch NOT MEMBER OF self.batchSet "
                    + "ORDER BY self.id",
                StockMove.class)
            .setParameter("statusSelect", StockMoveRepository.STATUS_REALIZED)
            .setParameter("typeSaleOrder", StockMoveRepository.ORIGIN_SALE_ORDER)
            .setParameter("invoiceStatusCanceled", InvoiceRepository.STATUS_CANCELED)
            .setParameter("anomalyList", anomalyList)
            .setParameter("batch", batch)
            .setMaxResults(fetchLimit);

    int start = 0;
    for (List<StockMove> stockMoveList;
        !(stockMoveList = query.getResultList()).isEmpty();
        JPA.clear(), start += stockMoveList.size(), query.setFirstResult(start)) {
      for (StockMove stockMove : stockMoveList) {
        try {
          stockMoveInvoiceService.createInvoiceFromSaleOrder(
              stockMove, saleRepo.find(stockMove.getOriginId()), null);
          updateStockMove(stockMove);
        } catch (Exception e) {
          incrementAnomaly();
          anomalyList.add(stockMove.getId());
          query.setParameter("anomalyList", anomalyList);
          TraceBackService.trace(e, ExceptionOriginRepository.INVOICE_ORIGIN, batch.getId());
          break;
        }
      }
      JPA.clear();
    }
  }

  @Override
  protected void stop() {
    StringBuilder sb = new StringBuilder();
    sb.append(I18n.get(IExceptionMessage.BATCH_OUTGOING_STOCK_MOVE_INVOICING_REPORT));
    sb.append(
        String.format(
            I18n.get(
                IExceptionMessage.BATCH_OUTGOING_STOCK_MOVE_INVOICING_DONE_SINGULAR,
                IExceptionMessage.BATCH_OUTGOING_STOCK_MOVE_INVOICING_DONE_PLURAL,
                batch.getDone()),
            batch.getDone()));
    sb.append(
        String.format(
            I18n.get(
                com.axelor.apps.base.exceptions.IExceptionMessage.ABSTRACT_BATCH_ANOMALY_SINGULAR,
                com.axelor.apps.base.exceptions.IExceptionMessage.ABSTRACT_BATCH_ANOMALY_PLURAL,
                batch.getAnomaly()),
            batch.getAnomaly()));
    addComment(sb.toString());
    super.stop();
  }
}
