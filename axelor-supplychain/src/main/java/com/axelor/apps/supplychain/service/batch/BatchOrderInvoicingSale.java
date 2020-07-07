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
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.supplychain.db.SupplychainBatch;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
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

public class BatchOrderInvoicingSale extends BatchOrderInvoicing {

  @Override
  protected void process() {
    SupplychainBatch supplychainBatch = batch.getSupplychainBatch();
    int fetchLimit = getFetchLimit();
    List<String> filterList = new ArrayList<>();
    Query<SaleOrder> query = Beans.get(SaleOrderRepository.class).all();

    if (supplychainBatch.getCompany() != null) {
      filterList.add("self.company = :company");
      query.bind("company", supplychainBatch.getCompany());
    }

    if (supplychainBatch.getSalespersonOrBuyerSet() != null
        && !supplychainBatch.getSalespersonOrBuyerSet().isEmpty()) {
      filterList.add("self.salespersonUser IN (:salespersonSet)");
      query.bind("salespersonSet", supplychainBatch.getSalespersonOrBuyerSet());
    }

    if (supplychainBatch.getTeam() != null) {
      filterList.add(
          "self.team = :team "
              + "OR self.team IS NULL AND self.salespersonUser IS NOT NULL AND self.salespersonUser.activeTeam = :team");
      query.bind("team", supplychainBatch.getTeam());
    }

    if (!Strings.isNullOrEmpty(supplychainBatch.getDeliveryOrReceiptState())) {
      List<Integer> delivereyStateList =
          StringTool.getIntegerList(supplychainBatch.getDeliveryOrReceiptState());
      filterList.add("self.deliveryState IN (:delivereyStateList)");
      query.bind("delivereyStateList", delivereyStateList);
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
            + "AND (invoice.saleOrder = self "
            + "OR invoice.saleOrder IS NULL AND EXISTS (SELECT 1 FROM invoice.invoiceLineList invoiceLine "
            + "WHERE invoiceLine.saleOrderLine MEMBER OF self.saleOrderLineList)))");

    filterList.add(
        "self.clientPartner.id NOT IN ("
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

    SaleOrderInvoiceService saleOrderInvoiceService = Beans.get(SaleOrderInvoiceService.class);
    Set<Long> treatedSet = new HashSet<>();

    int offset = 0;
    for (List<SaleOrder> saleOrderList;
        !(saleOrderList = query.fetch(fetchLimit, offset)).isEmpty();
        JPA.clear()) {
      for (SaleOrder saleOrder : saleOrderList) {
        ++offset;
        if (treatedSet.contains(saleOrder.getId())) {
          throw new IllegalArgumentException("Invoice generation error");
        }

        treatedSet.add(saleOrder.getId());

        try {
          saleOrderInvoiceService.generateInvoice(saleOrder);
          incrementDone();
        } catch (Exception e) {
          incrementAnomaly();
          anomalyList.add(saleOrder.getId());
          query.bind("anomalyList", anomalyList);
          TraceBackService.trace(e, ExceptionOriginRepository.INVOICE_ORIGIN, batch.getId());
          e.printStackTrace();
          break;
        }
      }
    }
  }
}
