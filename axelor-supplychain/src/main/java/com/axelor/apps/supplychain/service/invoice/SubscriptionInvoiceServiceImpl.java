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
package com.axelor.apps.supplychain.service.invoice;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;

public class SubscriptionInvoiceServiceImpl implements SubscriptionInvoiceService {

  @Inject private AppBaseService appBaseService;

  @Inject private SaleOrderRepository saleOrderRepo;

  @Inject private SaleOrderInvoiceService saleOrderInvoiceService;

  @Override
  public List<Invoice> generateSubscriptionInvoices() throws AxelorException {

    List<Invoice> invoices = new ArrayList<Invoice>();

    for (SaleOrder saleOrder : getSubscriptionOrders().fetch()) {
      Invoice invoice = generateSubscriptionInvoice(saleOrder);
      invoices.add(invoice);
    }

    return invoices;
  }

  @Override
  public Query<SaleOrder> getSubscriptionOrders() {

    Query<SaleOrder> query =
        saleOrderRepo
            .all()
            .filter(
                "self.saleOrderTypeSelect = :saleOrderType "
                    + "AND self.statusSelect = :saleOrderStatus "
                    + "AND :subScriptionDate >= self.nextInvoicingDate "
                    + "AND (self.contractEndDate IS NULL OR self.contractEndDate >= :subScriptionDate)")
            .bind("saleOrderType", SaleOrderRepository.SALE_ORDER_TYPE_SUBSCRIPTION)
            .bind("saleOrderStatus", SaleOrderRepository.STATUS_ORDER_CONFIRMED)
            .bind("subScriptionDate", appBaseService.getTodayDate());

    return query;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateSubscriptionInvoice(SaleOrder saleOrder) throws AxelorException {

    TemporalUnit temporalUnit = ChronoUnit.MONTHS;

    Invoice invoice =
        saleOrderInvoiceService.generateInvoice(saleOrderRepo.find(saleOrder.getId()));

    if (invoice != null) {
      if (saleOrder.getPeriodicityTypeSelect() == 1) {
        temporalUnit = ChronoUnit.DAYS;
      }
      invoice.setInvoiceDate(appBaseService.getTodayDate());
      invoice.setOperationSubTypeSelect(InvoiceRepository.OPERATION_SUB_TYPE_SUBSCRIPTION);

      LocalDate invoicingPeriodStartDate = saleOrder.getNextInvoicingStartPeriodDate();
      invoice.setSubscriptionFromDate(invoicingPeriodStartDate);
      invoice.setSubscriptionToDate(saleOrder.getNextInvoicingEndPeriodDate());
      if (invoicingPeriodStartDate != null) {
        LocalDate nextInvoicingStartPeriodDate =
            invoicingPeriodStartDate.plus(saleOrder.getNumberOfPeriods(), temporalUnit);
        saleOrder.setNextInvoicingStartPeriodDate(nextInvoicingStartPeriodDate);
        LocalDate nextInvoicingEndPeriodDate =
            nextInvoicingStartPeriodDate
                .plus(saleOrder.getNumberOfPeriods(), temporalUnit)
                .minusDays(1);
        saleOrder.setNextInvoicingEndPeriodDate(nextInvoicingEndPeriodDate);
      }

      LocalDate nextInvoicingDate = saleOrder.getNextInvoicingDate();
      if (nextInvoicingDate != null) {
        nextInvoicingDate = nextInvoicingDate.plus(saleOrder.getNumberOfPeriods(), temporalUnit);
      }
      saleOrder.setNextInvoicingDate(nextInvoicingDate);
    }

    return invoice;
  }
}
