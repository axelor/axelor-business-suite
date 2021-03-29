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
package com.axelor.apps.supplychain.web;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.generator.line.InvoiceLineManagement;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.supplychain.service.InvoiceLineSupplychainService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class InvoiceLineController {

  public List<InvoiceLine> updateQty(
      List<InvoiceLine> invoiceLines, BigDecimal oldKitQty, BigDecimal newKitQty, Invoice invoice)
      throws AxelorException {

    BigDecimal qty = BigDecimal.ZERO;
    BigDecimal exTaxTotal = BigDecimal.ZERO;
    BigDecimal companyExTaxTotal = BigDecimal.ZERO;
    BigDecimal inTaxTotal = BigDecimal.ZERO;
    BigDecimal companyInTaxTotal = BigDecimal.ZERO;
    BigDecimal priceDiscounted = BigDecimal.ZERO;
    BigDecimal taxRate = BigDecimal.ZERO;

    AppBaseService appBaseService = Beans.get(AppBaseService.class);
    InvoiceLineService invoiceLineService = Beans.get(InvoiceLineService.class);

    int scale = appBaseService.getNbDecimalDigitForQty();

    if (invoiceLines != null) {
      if (newKitQty.compareTo(BigDecimal.ZERO) != 0) {
        for (InvoiceLine line : invoiceLines) {
          qty =
              (line.getQty().divide(oldKitQty, scale, RoundingMode.HALF_EVEN))
                  .multiply(newKitQty)
                  .setScale(scale, RoundingMode.HALF_EVEN);
          priceDiscounted = invoiceLineService.computeDiscount(line, invoice.getInAti());

          if (line.getTaxLine() != null) {
            taxRate = line.getTaxLine().getValue();
          }

          if (!invoice.getInAti()) {
            exTaxTotal = InvoiceLineManagement.computeAmount(qty, priceDiscounted);
            inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate.divide(new BigDecimal(100))));
          } else {
            inTaxTotal = InvoiceLineManagement.computeAmount(qty, priceDiscounted);
            exTaxTotal =
                inTaxTotal.divide(
                    taxRate.divide(new BigDecimal(100)).add(BigDecimal.ONE),
                    2,
                    BigDecimal.ROUND_HALF_UP);
          }

          companyExTaxTotal = invoiceLineService.getCompanyExTaxTotal(exTaxTotal, invoice);
          companyInTaxTotal = invoiceLineService.getCompanyExTaxTotal(inTaxTotal, invoice);

          line.setQty(qty);
          line.setExTaxTotal(exTaxTotal);
          line.setCompanyExTaxTotal(companyExTaxTotal);
          line.setInTaxTotal(inTaxTotal);
          line.setCompanyInTaxTotal(companyInTaxTotal);
          line.setPriceDiscounted(priceDiscounted);
          line.setTaxRate(taxRate);
        }
      } else {
        for (InvoiceLine line : invoiceLines) {
          line.setQty(qty);
        }
      }
    }

    return invoiceLines;
  }

  public Invoice getInvoice(Context context) {

    Context parentContext = context.getParent();

    Invoice invoice = parentContext.asType(Invoice.class);

    if (!parentContext.getContextClass().toString().equals(Invoice.class.toString())) {

      InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

      invoice = invoiceLine.getInvoice();
    }

    return invoice;
  }

  public void computeBudgetDistributionSumAmount(ActionRequest request, ActionResponse response) {
    InvoiceLine invoiceLine = request.getContext().asType(InvoiceLine.class);
    Invoice invoice = request.getContext().getParent().asType(Invoice.class);

    Beans.get(InvoiceLineSupplychainService.class)
        .computeBudgetDistributionSumAmount(invoiceLine, invoice);

    response.setValue("budgetDistributionSumAmount", invoiceLine.getBudgetDistributionSumAmount());
    response.setValue("budgetDistributionList", invoiceLine.getBudgetDistributionList());
  }
}
