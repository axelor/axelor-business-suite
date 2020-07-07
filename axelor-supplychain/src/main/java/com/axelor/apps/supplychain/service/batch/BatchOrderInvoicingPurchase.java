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
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.supplychain.db.SupplychainBatch;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BatchOrderInvoicingPurchase extends BatchOrderInvoicing {

  @Override
  protected void process() {
    SupplychainBatch supplychainBatch = batch.getSupplychainBatch();
    int fetchLimit = getFetchLimit();
    List<String> filterList = new ArrayList<>();
    Query<PurchaseOrder> query = Beans.get(PurchaseOrderRepository.class).all();

    if (supplychainBatch.getCompany() != null) {
      filterList.add("self.company = :company");
      query.bind("company", supplychainBatch.getCompany());
    }

    if (supplychainBatch.getSalespersonOrBuyerSet() != null
        && !supplychainBatch.getSalespersonOrBuyerSet().isEmpty()) {
      filterList.add("self.buyerUser IN (:buyerSet)");
      query.bind("buyerSet", supplychainBatch.getSalespersonOrBuyerSet());
    }

    if (supplychainBatch.getTeam() != null) {
      filterList.add("self.buyerUser IS NOT NULL AND self.buyerUser.activeTeam = :team");
      query.bind("team", supplychainBatch.getTeam());
    }

    if (!Strings.isNullOrEmpty(supplychainBatch.getDeliveryOrReceiptState())) {
      List<Integer> receiptStateList =
          StringTool.getIntegerList(supplychainBatch.getDeliveryOrReceiptState());
      filterList.add("self.receiptState IN (:receiptStateList)");
      query.bind("receiptStateList", receiptStateList);
    }

    if (!Strings.isNullOrEmpty(supplychainBatch.getStatusSelect())) {
      List<Integer> statusSelectList =
          StringTool.getIntegerList(supplychainBatch.getStatusSelect());
      filterList.add("self.statusSelect IN (:statusSelectList)");
      query.bind("statusSelectList", statusSelectList);
    }

    if (supplychainBatch.getOrderUpToDate() != null) {
      filterList.add("self.orderDate <= :orderUpToDate");
      query.bind("orderUpToDate", supplychainBatch.getOrderUpToDate());
    }

    filterList.add("self.amountInvoiced < self.exTaxTotal");

    filterList.add(
        "NOT EXISTS (SELECT 1 FROM Invoice invoice WHERE invoice.statusSelect != :invoiceStatusSelect "
            + "AND (invoice.purchaseOrder = self "
            + "OR invoice.purchaseOrder IS NULL AND EXISTS (SELECT 1 FROM invoice.invoiceLineList invoiceLine "
            + "WHERE invoiceLine.purchaseOrderLine MEMBER OF self.purchaseOrderLineList)))");

    filterList.add(
        "self.supplierPartner.id NOT IN ("
            + Beans.get(BlockingService.class)
                .listOfBlockedPartner(
                    supplychainBatch.getCompany(), BlockingRepository.INVOICING_BLOCKING)
            + ")");

    query.bind("invoiceStatusSelect", InvoiceRepository.STATUS_CANCELED);

    List<Long> anomalyList = Lists.newArrayList(0L);
    filterList.add("self.id NOT IN (:anomalyList)");
    query.bind("anomalyList", anomalyList);

    String filter =
        filterList.stream()
            .map(item -> String.format("(%s)", item))
            .collect(Collectors.joining(" AND "));
    query.filter(filter);

    PurchaseOrderInvoiceService purchaseOrderInvoiceService =
        Beans.get(PurchaseOrderInvoiceService.class);
    Set<Long> treatedSet = new HashSet<>();

    int offset = 0;
    for (List<PurchaseOrder> purchaseOrderList;
        !(purchaseOrderList = query.fetch(fetchLimit, offset)).isEmpty();
        JPA.clear()) {
      offset += purchaseOrderList.size();
      for (PurchaseOrder purchaseOrder : purchaseOrderList) {
        if (treatedSet.contains(purchaseOrder.getId())) {
          throw new IllegalArgumentException("Invoice generation error");
        }

        treatedSet.add(purchaseOrder.getId());

        try {
          purchaseOrderInvoiceService.generateInvoice(purchaseOrder);
          incrementDone();
        } catch (Exception e) {
          incrementAnomaly();
          anomalyList.add(purchaseOrder.getId());
          query.bind("anomalyList", anomalyList);
          TraceBackService.trace(e, ExceptionOriginRepository.INVOICE_ORIGIN, batch.getId());
          e.printStackTrace();
          break;
        }
      }
    }
  }
}
