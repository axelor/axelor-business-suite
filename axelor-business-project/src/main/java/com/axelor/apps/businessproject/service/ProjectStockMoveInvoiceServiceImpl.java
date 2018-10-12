/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.businessproject.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockMoveLineService;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.apps.supplychain.service.StockMoveInvoiceServiceImpl;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import java.util.List;

public class ProjectStockMoveInvoiceServiceImpl extends StockMoveInvoiceServiceImpl {

  @Inject
  public ProjectStockMoveInvoiceServiceImpl(
      SaleOrderInvoiceService saleOrderInvoiceService,
      PurchaseOrderInvoiceService purchaseOrderInvoiceService,
      StockMoveLineService stockMoveLineService,
      InvoiceRepository invoiceRepository,
      StockMoveRepository stockMoveRepo) {
    super(
        saleOrderInvoiceService,
        purchaseOrderInvoiceService,
        stockMoveLineService,
        invoiceRepository,
        stockMoveRepo);
  }

  @Override
  public List<InvoiceLine> createInvoiceLine(Invoice invoice, StockMoveLine stockMoveLine)
      throws AxelorException {

    List<InvoiceLine> invoiceLines = super.createInvoiceLine(invoice, stockMoveLine);
    for (InvoiceLine invoiceLine : invoiceLines) {
      SaleOrderLine saleOrderLine = invoiceLine.getSaleOrderLine();
      if (saleOrderLine != null) {
        invoiceLine.setProject(saleOrderLine.getProject());
      }

      PurchaseOrderLine purchaseOrderLine = invoiceLine.getPurchaseOrderLine();
      if (purchaseOrderLine != null) {
        invoiceLine.setProject(purchaseOrderLine.getProject());
      }
    }

    return invoiceLines;
  }
}
