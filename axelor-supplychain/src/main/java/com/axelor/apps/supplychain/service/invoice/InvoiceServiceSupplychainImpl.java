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
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.alarm.AlarmEngineService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.sale.db.AdvancePayment;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.supplychain.service.AccountingSituationSupplychainService;
import com.axelor.apps.supplychain.service.IntercoService;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InvoiceServiceSupplychainImpl extends InvoiceServiceImpl
    implements InvoiceServiceSupplychain {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final ThreadLocal<Integer> invoiceStatusBeforeCancelation = new ThreadLocal<>();
  protected PurchaseOrderInvoiceService purchaseOrderInvoiceService;
  protected PurchaseOrderRepository purchaseOrderRepository;
  protected SaleOrderInvoiceService saleOrderInvoiceService;
  protected SaleOrderRepository saleOrderRepository;
  protected AccountingSituationSupplychainService accountingSituationSupplychainService;
  protected IntercoService intercoService;

  @Inject
  public InvoiceServiceSupplychainImpl(
      PartnerService partnerService,
      AlarmEngineService<Invoice> alarmEngineService,
      InvoiceRepository invoiceRepo,
      AppAccountService appAccountService,
      InvoiceLineService invoiceLineService,
      BlockingService blockingService,
      UserService userService,
      SequenceService sequenceService,
      AccountConfigService accountConfigService,
      MoveService moveService,
      PurchaseOrderInvoiceService purchaseOrderInvoiceService,
      PurchaseOrderRepository purchaseOrderRepository,
      SaleOrderInvoiceService saleOrderInvoiceService,
      SaleOrderRepository saleOrderRepository,
      AccountingSituationSupplychainService accountingSituationSupplychainService,
      IntercoService intercoService) {
    super(
        partnerService,
        alarmEngineService,
        invoiceRepo,
        appAccountService,
        invoiceLineService,
        blockingService,
        userService,
        sequenceService,
        accountConfigService,
        moveService);
    this.purchaseOrderInvoiceService = purchaseOrderInvoiceService;
    this.purchaseOrderRepository = purchaseOrderRepository;
    this.saleOrderInvoiceService = saleOrderInvoiceService;
    this.saleOrderRepository = saleOrderRepository;
    this.accountingSituationSupplychainService = accountingSituationSupplychainService;
    this.intercoService = intercoService;
  }

  @Override
  public Set<Invoice> getDefaultAdvancePaymentInvoice(Invoice invoice) throws AxelorException {
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
    // search sale order in the invoice
    SaleOrder saleOrder = invoice.getSaleOrder();
    // search sale order in invoice lines
    List<SaleOrder> saleOrderList =
        invoice
            .getInvoiceLineList()
            .stream()
            .map(InvoiceLine::getSaleOrder)
            .collect(Collectors.toList());
    saleOrderList.add(saleOrder);

    // remove null value and duplicates
    saleOrderList =
        saleOrderList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());

    if (saleOrderList.isEmpty()) {
      return new ArrayList<>();
    } else {
      // get move lines from sale order
      return saleOrderList
          .stream()
          .flatMap(saleOrder1 -> saleOrder1.getAdvancePaymentList().stream())
          .filter(Objects::nonNull)
          .distinct()
          .map(AdvancePayment::getMove)
          .filter(Objects::nonNull)
          .distinct()
          .flatMap(move -> move.getMoveLineList().stream())
          .collect(Collectors.toList());
    }
  }

  @Override
  public List<InvoiceLine> addSubLines(List<InvoiceLine> invoiceLine) {

    if (invoiceLine == null) {
      return invoiceLine;
    }

    List<InvoiceLine> lines = new ArrayList<InvoiceLine>();
    lines.addAll(invoiceLine);
    for (InvoiceLine line : lines) {
      if (line.getSubLineList() == null) {
        continue;
      }
      for (InvoiceLine subLine : line.getSubLineList()) {
        if (subLine.getInvoice() == null) {
          invoiceLine.add(subLine);
        }
      }
    }
    return invoiceLine;
  }

  @Override
  public List<InvoiceLine> removeSubLines(List<InvoiceLine> invoiceLines) {

    if (invoiceLines == null) {
      return invoiceLines;
    }

    List<InvoiceLine> subLines = new ArrayList<InvoiceLine>();
    for (InvoiceLine packLine : invoiceLines) {
      if (packLine.getTypeSelect() == InvoiceLineRepository.TYPE_PACK
          && packLine.getSubLineList() != null) {
        packLine.getSubLineList().removeIf(it -> it.getId() != null && !invoiceLines.contains(it));
        packLine.setTotalPack(
            packLine
                .getSubLineList()
                .stream()
                .map(it -> it.getExTaxTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        subLines.addAll(packLine.getSubLineList());
      }
    }
    Iterator<InvoiceLine> lines = invoiceLines.iterator();

    while (lines.hasNext()) {
      InvoiceLine subLine = lines.next();
      if (subLine.getId() != null
          && subLine.getParentLine() != null
          && !subLines.contains(subLine)) {
        lines.remove();
      }
    }

    return invoiceLines;
  }

  @Override
  protected void afterValidation(Invoice invoice) throws AxelorException {
    super.afterValidation(invoice);

    if (invoice.getInterco()) {
      intercoService.generateIntercoInvoice(invoice);
    }
  }

  @Override
  protected void afterVentilation(Invoice invoice) throws AxelorException {
    super.afterVentilation(invoice);

    if (InvoiceToolService.isPurchase(invoice)) {
      // Update amount invoiced on PurchaseOrder
      handlePurchaseOrderInvoiceStatusChange(invoice, STATUS_VENTILATED);
    } else {
      // Update amount remaining to invoiced on SaleOrder
      handleSaleOrderInvoiceStatusChange(invoice, STATUS_VENTILATED);
    }
  }

  @Override
  protected void beforeCancelation(Invoice invoice) throws AxelorException {
    super.beforeCancelation(invoice);
    invoiceStatusBeforeCancelation.set(invoice.getStatusSelect());
  }

  @Override
  protected void afterCancelation(Invoice invoice) throws AxelorException {
    super.afterCancelation(invoice);
    if (invoiceStatusBeforeCancelation.get() != InvoiceRepository.STATUS_VENTILATED) return;

    if (InvoiceToolService.isPurchase(invoice)) {
      // Update amount invoiced on PurchaseOrder
      handlePurchaseOrderInvoiceStatusChange(invoice, STATUS_CANCELED);
    } else {
      // Update amount remaining to invoiced on SaleOrder
      handleSaleOrderInvoiceStatusChange(invoice, InvoiceRepository.STATUS_CANCELED);
    }
  }

  private void handleSaleOrderInvoiceStatusChange(Invoice invoice, int newStatus)
      throws AxelorException {

    SaleOrder invoiceSaleOrder = invoice.getSaleOrder();

    if (invoiceSaleOrder != null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Updating invoiced amount for sale order #{} (ref. {})",
            invoiceSaleOrder.getId(),
            invoiceSaleOrder.getSaleOrderSeq());
      }

      saleOrderInvoiceService.update(
          invoiceSaleOrder, invoice.getId(), newStatus == InvoiceRepository.STATUS_CANCELED);
      accountingSituationSupplychainService.updateUsedCredit(invoiceSaleOrder.getClientPartner());

      // determine if the invoice is a balance invoice.
      if (newStatus == STATUS_VENTILATED
          && invoiceSaleOrder.getAmountInvoiced().compareTo(invoiceSaleOrder.getExTaxTotal())
              == 0) {
        invoice.setOperationSubTypeSelect(InvoiceRepository.OPERATION_SUB_TYPE_BALANCE);
      }

    } else {

      // Get all different saleOrders from invoice
      List<SaleOrder> saleOrders = new LinkedList<>();

      for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {

        SaleOrder saleOrder =
            handleSaleOrderInvoiceLineStatusChange(invoice, invoiceLine, newStatus);

        if (saleOrder != null && !saleOrders.contains(saleOrder)) {
          if (log.isDebugEnabled()) {
            log.debug(
                "Updating invoiced amount for sale order #{} (ref. {})",
                saleOrder.getId(),
                saleOrder.getSaleOrderSeq());
          }
          saleOrderInvoiceService.update(saleOrder, invoice.getId(), newStatus == STATUS_CANCELED);
          accountingSituationSupplychainService.updateUsedCredit(saleOrder.getClientPartner());
          saleOrders.add(saleOrder);
        }
      }
    }
  }

  private SaleOrder handleSaleOrderInvoiceLineStatusChange(
      Invoice invoice, InvoiceLine invoiceLine, int newStatus) throws AxelorException {

    SaleOrderLine saleOrderLine = invoiceLine.getSaleOrderLine();

    if (saleOrderLine == null) {
      return null;
    }

    SaleOrder saleOrder = saleOrderLine.getSaleOrder();

    // Update invoiced amount on sale order line
    BigDecimal invoicedAmountToAdd = invoiceLine.getExTaxTotal();

    // If is it a refund invoice, so we negate the amount invoiced
    if (InvoiceToolService.isRefund(invoiceLine.getInvoice())) {
      invoicedAmountToAdd = invoicedAmountToAdd.negate();
    }

    if (!invoice.getCurrency().equals(saleOrder.getCurrency())
        && saleOrderLine.getCompanyExTaxTotal().compareTo(BigDecimal.ZERO) != 0) {
      // If the sale order currency is different from the invoice currency, use company currency to
      // calculate a rate. This rate will be applied to sale order line
      BigDecimal currentCompanyInvoicedAmount = invoiceLine.getCompanyExTaxTotal();
      BigDecimal rate =
          currentCompanyInvoicedAmount.divide(
              saleOrderLine.getCompanyExTaxTotal(), 4, RoundingMode.HALF_UP);
      invoicedAmountToAdd = rate.multiply(saleOrderLine.getExTaxTotal());
    }

    if (newStatus == InvoiceRepository.STATUS_VENTILATED) {
      saleOrderLine.setAmountInvoiced(saleOrderLine.getAmountInvoiced().add(invoicedAmountToAdd));
    } else if (newStatus == InvoiceRepository.STATUS_CANCELED) {
      saleOrderLine.setAmountInvoiced(
          saleOrderLine.getAmountInvoiced().subtract(invoicedAmountToAdd));
    }

    return saleOrder;
  }

  private void handlePurchaseOrderInvoiceStatusChange(Invoice invoice, int newStatus)
      throws AxelorException {

    PurchaseOrder invoicePurchaseOrder = invoice.getPurchaseOrder();

    if (invoicePurchaseOrder != null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Updating invoiced amount for purchase order #{} (ref. {})",
            invoicePurchaseOrder.getId(),
            invoicePurchaseOrder.getPurchaseOrderSeq());
      }
      invoicePurchaseOrder.setAmountInvoiced(
          purchaseOrderInvoiceService.getInvoicedAmount(
              invoicePurchaseOrder, invoice.getId(), newStatus == STATUS_CANCELED));

    } else {

      // Get all different purchaseOrders from invoice
      List<PurchaseOrder> purchaseOrders = new LinkedList<>();

      for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {

        PurchaseOrder purchaseOrder =
            handlePurchaseOrderInvoiceLineStatusChange(invoice, invoiceLine, newStatus);

        if (purchaseOrder != null && !purchaseOrders.contains(purchaseOrder)) {
          purchaseOrders.add(purchaseOrder);
        }
      }

      for (PurchaseOrder purchaseOrder : purchaseOrders) {
        if (log.isDebugEnabled()) {
          log.debug(
              "Updating invoiced amount for purchase order #{} (ref. {})",
              purchaseOrder.getId(),
              purchaseOrder.getPurchaseOrderSeq());
        }

        purchaseOrder.setAmountInvoiced(
            purchaseOrderInvoiceService.getInvoicedAmount(
                purchaseOrder, invoice.getId(), newStatus == STATUS_CANCELED));
        purchaseOrderRepository.save(purchaseOrder);
      }
    }
  }

  private PurchaseOrder handlePurchaseOrderInvoiceLineStatusChange(
      Invoice invoice, InvoiceLine invoiceLine, int newStatus) throws AxelorException {

    PurchaseOrderLine purchaseOrderLine = invoiceLine.getPurchaseOrderLine();

    if (purchaseOrderLine == null) {
      return null;
    }

    PurchaseOrder purchaseOrder = purchaseOrderLine.getPurchaseOrder();

    BigDecimal invoicedAmountToAdd = invoiceLine.getExTaxTotal();

    // If is it a refund invoice, so we negate the amount invoiced
    if (InvoiceToolService.isRefund(invoiceLine.getInvoice())) {
      invoicedAmountToAdd = invoicedAmountToAdd.negate();
    }

    // Update invoiced amount on purchase order line
    if (!invoice.getCurrency().equals(purchaseOrder.getCurrency())
        && purchaseOrderLine.getCompanyExTaxTotal().compareTo(BigDecimal.ZERO) != 0) {
      // If the purchase order currency is different from the invoice currency, use company currency
      // to calculate a rate. This rate will be applied to purchase order line
      BigDecimal currentCompanyInvoicedAmount = invoiceLine.getCompanyExTaxTotal();
      BigDecimal rate =
          currentCompanyInvoicedAmount.divide(
              purchaseOrderLine.getCompanyExTaxTotal(), 4, RoundingMode.HALF_UP);
      invoicedAmountToAdd = rate.multiply(purchaseOrderLine.getExTaxTotal());
    }

    if (newStatus == STATUS_VENTILATED) {
      purchaseOrderLine.setAmountInvoiced(
          purchaseOrderLine.getAmountInvoiced().add(invoicedAmountToAdd));
    } else if (newStatus == STATUS_CANCELED) {
      purchaseOrderLine.setAmountInvoiced(
          purchaseOrderLine.getAmountInvoiced().subtract(invoicedAmountToAdd));
    }

    return purchaseOrder;
  }
}
