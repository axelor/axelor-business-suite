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
package com.axelor.apps.supplychain.service.invoice;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceServiceImpl;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.account.service.invoice.factory.CancelFactory;
import com.axelor.apps.account.service.invoice.factory.ValidateFactory;
import com.axelor.apps.account.service.invoice.factory.VentilateFactory;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.alarm.AlarmEngineService;
import com.axelor.apps.sale.db.AdvancePayment;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.supplychain.db.Timetable;
import com.axelor.apps.supplychain.db.repo.TimetableRepository;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.common.ObjectUtils;
import com.axelor.db.EntityHelper;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class InvoiceServiceSupplychainImpl extends InvoiceServiceImpl
    implements InvoiceServiceSupplychain {

  protected InvoiceLineRepository invoiceLineRepo;

  @Inject
  public InvoiceServiceSupplychainImpl(
      ValidateFactory validateFactory,
      VentilateFactory ventilateFactory,
      CancelFactory cancelFactory,
      AlarmEngineService<Invoice> alarmEngineService,
      InvoiceRepository invoiceRepo,
      AppAccountService appAccountService,
      PartnerService partnerService,
      InvoiceLineService invoiceLineService,
      AccountConfigService accountConfigService,
      MoveToolService moveToolService,
      InvoiceTermService invoiceTermService,
      InvoiceLineRepository invoiceLineRepo) {
    super(
        validateFactory,
        ventilateFactory,
        cancelFactory,
        alarmEngineService,
        invoiceRepo,
        appAccountService,
        partnerService,
        invoiceLineService,
        accountConfigService,
        moveToolService,
        invoiceTermService);
    this.invoiceLineRepo = invoiceLineRepo;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void ventilate(Invoice invoice) throws AxelorException {
    super.ventilate(invoice);

    TimetableRepository timeTableRepo = Beans.get(TimetableRepository.class);

    List<Timetable> timetableList =
        timeTableRepo.all().filter("self.invoice.id = ?1", invoice.getId()).fetch();

    for (Timetable timetable : timetableList) {
      timetable.setInvoiced(true);
      timeTableRepo.save(timetable);
    }
  }

  @Override
  public Set<Invoice> getDefaultAdvancePaymentInvoice(Invoice invoice) throws AxelorException {

    if (!Beans.get(AppSupplychainService.class).isApp("supplychain")) {
      return super.getDefaultAdvancePaymentInvoice(invoice);
    }

    SaleOrder saleOrder = invoice.getSaleOrder();
    Company company = invoice.getCompany();
    Currency currency = invoice.getCurrency();
    if (company == null || saleOrder == null) {
      return super.getDefaultAdvancePaymentInvoice(invoice);
    }
    boolean generateMoveForInvoicePayment =
        Beans.get(AccountConfigService.class)
            .getAccountConfig(company)
            .getGenerateMoveForInvoicePayment();

    String filter = writeGeneralFilterForAdvancePayment();
    filter += " AND self.saleOrder = :_saleOrder";

    if (!generateMoveForInvoicePayment) {
      filter += " AND self.currency = :_currency";
    }
    Query<Invoice> query =
        Beans.get(InvoiceRepository.class)
            .all()
            .filter(filter)
            .bind("_status", InvoiceRepository.STATUS_VALIDATED)
            .bind("_operationSubType", InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE)
            .bind("_saleOrder", saleOrder);

    if (!generateMoveForInvoicePayment) {
      if (currency == null) {
        return new HashSet<>();
      }
      query.bind("_currency", currency);
    }

    Set<Invoice> advancePaymentInvoices = new HashSet<>(query.fetch());
    filterAdvancePaymentInvoice(invoice, advancePaymentInvoices);
    return advancePaymentInvoices;
  }

  @Override
  public List<MoveLine> getMoveLinesFromSOAdvancePayments(Invoice invoice) {

    if (!Beans.get(AppSupplychainService.class).isApp("supplychain")) {
      return super.getMoveLinesFromSOAdvancePayments(invoice);
    }

    // search sale order in the invoice
    SaleOrder saleOrder = invoice.getSaleOrder();
    // search sale order in invoice lines
    List<SaleOrder> saleOrderList =
        invoice.getInvoiceLineList().stream()
            .map(invoiceLine -> invoice.getSaleOrder())
            .collect(Collectors.toList());

    saleOrderList.add(saleOrder);

    // remove null value and duplicates
    saleOrderList =
        saleOrderList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());

    if (saleOrderList.isEmpty()) {
      return new ArrayList<>();
    } else {
      // get move lines from sale order
      return saleOrderList.stream()
          .flatMap(saleOrder1 -> saleOrder1.getAdvancePaymentList().stream())
          .filter(Objects::nonNull)
          .distinct()
          .map(AdvancePayment::getMove)
          .filter(Objects::nonNull)
          .distinct()
          .flatMap(move -> moveToolService.getToReconcileCreditMoveLines(move).stream())
          .collect(Collectors.toList());
    }
  }

  @Override
  public void computePackTotal(Invoice invoice) {
    List<InvoiceLine> invoiceLineList = invoice.getInvoiceLineList();

    if (!invoiceLineService.hasEndOfPackTypeLine(invoiceLineList)) {
      return;
    }
    invoiceLineList.sort(Comparator.comparing(InvoiceLine::getSequence));
    BigDecimal totalExTaxTotal = BigDecimal.ZERO;
    BigDecimal totalInTaxTotal = BigDecimal.ZERO;

    for (InvoiceLine invoiceLine : invoiceLineList) {
      switch (invoiceLine.getTypeSelect()) {
        case InvoiceLineRepository.TYPE_NORMAL:
          totalExTaxTotal = totalExTaxTotal.add(invoiceLine.getExTaxTotal());
          totalInTaxTotal = totalInTaxTotal.add(invoiceLine.getInTaxTotal());
          break;

        case InvoiceLineRepository.TYPE_TITLE:
          break;

        case InvoiceLineRepository.TYPE_START_OF_PACK:
          totalExTaxTotal = totalInTaxTotal = BigDecimal.ZERO;
          break;

        case InvoiceLineRepository.TYPE_END_OF_PACK:
          invoiceLine.setQty(BigDecimal.ZERO);
          invoiceLine.setExTaxTotal(
              invoiceLine.getIsShowTotal() ? totalExTaxTotal : BigDecimal.ZERO);
          invoiceLine.setInTaxTotal(
              invoiceLine.getIsShowTotal() ? totalInTaxTotal : BigDecimal.ZERO);
          totalExTaxTotal = totalInTaxTotal = BigDecimal.ZERO;
          break;

        default:
          break;
      }
    }
    invoice.setInvoiceLineList(invoiceLineList);
  }

  @Override
  public void resetPackTotal(Invoice invoice) {
    List<InvoiceLine> invoiceLineList = invoice.getInvoiceLineList();
    if (ObjectUtils.isEmpty(invoiceLineList)) {
      return;
    }
    invoiceLineList.stream()
        .filter(
            invoiceLine -> invoiceLine.getTypeSelect() == InvoiceLineRepository.TYPE_END_OF_PACK)
        .forEach(
            invoiceLine -> {
              invoiceLine.setIsHideUnitAmounts(Boolean.FALSE);
              invoiceLine.setIsShowTotal(Boolean.FALSE);
              invoiceLine.setExTaxTotal(BigDecimal.ZERO);
              invoiceLine.setInTaxTotal(BigDecimal.ZERO);
            });
    invoice.setInvoiceLineList(invoiceLineList);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice updateProductQtyWithPackHeaderQty(Invoice invoice) throws AxelorException {
    List<InvoiceLine> invoiceLineList = invoice.getInvoiceLineList();
    invoiceLineList.sort(Comparator.comparing(InvoiceLine::getSequence));
    boolean isStartOfPack = false;
    BigDecimal oldQty = BigDecimal.ZERO;
    BigDecimal newQty = BigDecimal.ZERO;
    for (InvoiceLine invoiceLine : invoiceLineList) {
      if (invoiceLine.getTypeSelect() == InvoiceLineRepository.TYPE_START_OF_PACK
          && !isStartOfPack) {
        InvoiceLine oldInvoiceLine = invoiceLineRepo.find(invoiceLine.getId());
        oldQty = oldInvoiceLine.getQty();
        newQty = invoiceLine.getQty();
        if (newQty.compareTo(oldQty) != 0) {
          isStartOfPack = true;
          oldInvoiceLine = EntityHelper.getEntity(invoiceLine);
          oldInvoiceLine.setSubLineList(invoiceLine.getSubLineList());
          invoiceLineRepo.save(oldInvoiceLine);
        }
      } else if (isStartOfPack) {
        if (invoiceLine.getTypeSelect() == InvoiceLineRepository.TYPE_END_OF_PACK) {
          break;
        }
        invoiceLineService.updateProductQty(invoiceLine, invoice, oldQty, newQty);
      }
    }
    return invoice;
  }
}
