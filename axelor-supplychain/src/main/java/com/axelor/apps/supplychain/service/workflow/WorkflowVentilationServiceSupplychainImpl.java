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
package com.axelor.apps.supplychain.service.workflow;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.account.service.invoice.workflow.ventilate.WorkflowVentilationServiceImpl;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCreateService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.AccountingSituationSupplychainService;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.apps.supplychain.service.StockMoveInvoiceService;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowVentilationServiceSupplychainImpl extends WorkflowVentilationServiceImpl {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private SaleOrderInvoiceService saleOrderInvoiceService;

  private PurchaseOrderInvoiceService purchaseOrderInvoiceService;

  private SaleOrderRepository saleOrderRepository;

  private PurchaseOrderRepository purchaseOrderRepository;

  private AccountingSituationSupplychainService accountingSituationSupplychainService;

  private AppSupplychainService appSupplychainService;

  private StockMoveInvoiceService stockMoveInvoiceService;

  @Inject
  public WorkflowVentilationServiceSupplychainImpl(
      AccountConfigService accountConfigService,
      InvoicePaymentRepository invoicePaymentRepo,
      InvoicePaymentCreateService invoicePaymentCreateService,
      SaleOrderInvoiceService saleOrderInvoiceService,
      PurchaseOrderInvoiceService purchaseOrderInvoiceService,
      SaleOrderRepository saleOrderRepository,
      PurchaseOrderRepository purchaseOrderRepository,
      AccountingSituationSupplychainService accountingSituationSupplychainService,
      AppSupplychainService appSupplychainService,
      StockMoveInvoiceService stockMoveInvoiceService) {

    super(accountConfigService, invoicePaymentRepo, invoicePaymentCreateService);
    this.saleOrderInvoiceService = saleOrderInvoiceService;
    this.purchaseOrderInvoiceService = purchaseOrderInvoiceService;
    this.saleOrderRepository = saleOrderRepository;
    this.purchaseOrderRepository = purchaseOrderRepository;
    this.accountingSituationSupplychainService = accountingSituationSupplychainService;
    this.appSupplychainService = appSupplychainService;
    this.stockMoveInvoiceService = stockMoveInvoiceService;
  }

  public void afterVentilation(Invoice invoice) throws AxelorException {
    super.afterVentilation(invoice);
    if (InvoiceToolService.isPurchase(invoice)) {

      // Update amount invoiced on PurchaseOrder
      this.purchaseOrderProcess(invoice);

    } else {

      // Update amount remaining to invoiced on SaleOrder
      this.saleOrderProcess(invoice);
    }
    if (invoice.getInterco() || invoice.getCreatedByInterco()) {
      updateIntercoReference(invoice);
    }
    if (invoice.getStockMoveSet() != null && !invoice.getStockMoveSet().isEmpty()) {
      stockMoveProcess(invoice);
    }
  }

  /**
   * Update external reference in the corresponding interco invoice.
   *
   * @param invoice
   */
  protected void updateIntercoReference(Invoice invoice) {
    Invoice intercoInvoice =
        Beans.get(InvoiceRepository.class)
            .all()
            .filter("self.invoiceId = :invoiceId")
            .bind("invoiceId", invoice.getExternalReference())
            .fetchOne();
    if (intercoInvoice != null) {
      intercoInvoice.setExternalReference(invoice.getInvoiceId());
    }
  }

  protected void saleOrderProcess(Invoice invoice) throws AxelorException {

    // Get all different saleOrders from invoice
    Set<SaleOrder> saleOrderSet = new HashSet<>();

    for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {
      SaleOrder saleOrder = null;
      saleOrder = this.saleOrderLineProcess(invoice, invoiceLine);
      if (saleOrder != null) {
        saleOrderSet.add(saleOrder);
      }
    }

    for (SaleOrder saleOrder : saleOrderSet) {
      log.debug("Update the invoiced amount of the sale order : {}", saleOrder.getSaleOrderSeq());
      saleOrderInvoiceService.update(saleOrder, invoice.getId(), false);
      saleOrderRepository.save(saleOrder);
      accountingSituationSupplychainService.updateUsedCredit(saleOrder.getClientPartner());

      // determine if the invoice is a balance invoice.
      if (saleOrder.getAmountInvoiced().compareTo(saleOrder.getExTaxTotal()) == 0) {
        invoice.setOperationSubTypeSelect(InvoiceRepository.OPERATION_SUB_TYPE_BALANCE);
      }
    }
  }

  protected SaleOrder saleOrderLineProcess(Invoice invoice, InvoiceLine invoiceLine)
      throws AxelorException {

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

    saleOrderLine.setAmountInvoiced(saleOrderLine.getAmountInvoiced().add(invoicedAmountToAdd));

    return saleOrder;
  }

  protected void purchaseOrderProcess(Invoice invoice) throws AxelorException {

    // Get all different purchaseOrders from invoice
    Set<PurchaseOrder> purchaseOrderSet = new HashSet<>();

    for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {
      PurchaseOrder purchaseOrder = null;
      purchaseOrder = this.purchaseOrderLineProcess(invoice, invoiceLine);
      if (purchaseOrder != null) {
        purchaseOrderSet.add(purchaseOrder);
      }
    }

    for (PurchaseOrder purchaseOrder : purchaseOrderSet) {
      log.debug(
          "Update the invoiced amount of the purchase order : {}",
          purchaseOrder.getPurchaseOrderSeq());
      purchaseOrder.setAmountInvoiced(
          purchaseOrderInvoiceService.getInvoicedAmount(purchaseOrder, invoice.getId(), false));
      purchaseOrderRepository.save(purchaseOrder);
    }
  }

  protected PurchaseOrder purchaseOrderLineProcess(Invoice invoice, InvoiceLine invoiceLine)
      throws AxelorException {

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

    purchaseOrderLine.setAmountInvoiced(
        purchaseOrderLine.getAmountInvoiced().add(invoicedAmountToAdd));

    return purchaseOrder;
  }

  protected void stockMoveProcess(Invoice invoice) throws AxelorException {
    // update qty invoiced in stock move line
    for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {
      StockMoveLine stockMoveLine = invoiceLine.getStockMoveLine();
      if (stockMoveLine != null) {
        BigDecimal qty = stockMoveLine.getQtyInvoiced();
        StockMove stockMove = stockMoveLine.getStockMove();

        if (stockMoveInvoiceService.isInvoiceRefundingStockMove(stockMove, invoice)) {
          qty = qty.subtract(invoiceLine.getQty());
        } else {
          qty = qty.add(invoiceLine.getQty());
        }
        if (stockMoveLine.getRealQty().compareTo(qty) >= 0) {
          stockMoveLine.setQtyInvoiced(qty);
        } else {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(IExceptionMessage.STOCK_MOVE_INVOICE_QTY_MAX));
        }
      }
    }

    // update stock moves invoicing status
    for (StockMove stockMove : invoice.getStockMoveSet()) {
      stockMoveInvoiceService.computeStockMoveInvoicingStatus(stockMove);
    }
  }
}
