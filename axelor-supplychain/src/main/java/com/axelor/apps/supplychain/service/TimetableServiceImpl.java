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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.supplychain.db.Timetable;
import com.axelor.apps.supplychain.db.TimetableTemplate;
import com.axelor.apps.supplychain.db.TimetableTemplateLine;
import com.axelor.apps.supplychain.db.repo.TimetableRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TimetableServiceImpl implements TimetableService {

  @Inject SaleOrderInvoiceService saleOrderInvoiceService;

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateInvoice(Timetable timetable) throws AxelorException {
    Invoice invoice = this.createInvoice(timetable);
    Beans.get(InvoiceRepository.class).save(invoice);
    timetable.setInvoice(invoice);
    Beans.get(TimetableRepository.class).save(timetable);
    return invoice;
  }

  @Override
  public Invoice createInvoice(Timetable timetable) throws AxelorException {
    SaleOrder saleOrder = timetable.getSaleOrder();
    PurchaseOrder purchaseOrder = timetable.getPurchaseOrder();

    if (saleOrder != null) {
      if (saleOrder.getCurrency() == null) {
        throw new AxelorException(
            timetable,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.SO_INVOICE_6),
            saleOrder.getSaleOrderSeq());
      }
      List<Long> timetableId = new ArrayList<>();
      timetableId.add(timetable.getId());
      Invoice invoice =
          saleOrderInvoiceService.generateInvoice(
              saleOrder,
              SaleOrderRepository.INVOICE_TIMETABLES,
              BigDecimal.ZERO,
              true,
              null,
              timetableId);

      return invoice;
    }

    if (purchaseOrder != null) {
      if (purchaseOrder.getCurrency() == null) {
        throw new AxelorException(
            timetable,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.SO_INVOICE_6),
            purchaseOrder.getPurchaseOrderSeq());
      }
      List<Long> timetableId = new ArrayList<>();
      timetableId.add(timetable.getId());
      return Beans.get(PurchaseOrderInvoiceServiceImpl.class)
          .generateInvoiceFromTimetableForPurchaseOrder(purchaseOrder, timetableId);
    }

    return null;
  }

  public List<Timetable> applyTemplate(
      TimetableTemplate template, BigDecimal exTaxTotal, LocalDate computationDate) {
    List<Timetable> timetables = new ArrayList<>();

    for (TimetableTemplateLine templateLine : template.getTimetableTemplateLineList()) {
      Timetable timetable = new Timetable();
      timetable.setEstimatedDate(
          InvoiceToolService.getDueDate(
              templateLine.getTypeSelect(),
              templateLine.getPaymentTime(),
              templateLine.getPeriodTypeSelect(),
              templateLine.getDaySelect(),
              computationDate));

      timetable.setPercentage(templateLine.getPercentage());
      timetable.setAmount(
          exTaxTotal.multiply(templateLine.getPercentage()).divide(BigDecimal.valueOf(100)));
      timetables.add(timetable);
    }

    timetables.sort(Comparator.comparing(Timetable::getEstimatedDate));
    return timetables;
  }
}
