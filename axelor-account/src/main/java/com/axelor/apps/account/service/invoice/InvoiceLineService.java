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
package com.axelor.apps.account.service.invoice;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface InvoiceLineService {

  public List<AnalyticMoveLine> getAndComputeAnalyticDistribution(
      InvoiceLine invoiceLine, Invoice invoice) throws AxelorException;

  List<AnalyticMoveLine> computeAnalyticDistribution(InvoiceLine invoiceLine)
      throws AxelorException;

  List<AnalyticMoveLine> createAnalyticDistributionWithTemplate(InvoiceLine invoiceLine)
      throws AxelorException;

  TaxLine getTaxLine(Invoice invoice, InvoiceLine invoiceLine, boolean isPurchase)
      throws AxelorException;

  BigDecimal getExTaxUnitPrice(
      Invoice invoice, InvoiceLine invoiceLine, TaxLine taxLine, boolean isPurchase)
      throws AxelorException;

  BigDecimal getInTaxUnitPrice(
      Invoice invoice, InvoiceLine invoiceLine, TaxLine taxLine, boolean isPurchase)
      throws AxelorException;

  BigDecimal getAccountingExTaxTotal(BigDecimal exTaxTotal, Invoice invoice) throws AxelorException;

  BigDecimal getCompanyExTaxTotal(BigDecimal exTaxTotal, Invoice invoice) throws AxelorException;

  PriceListLine getPriceListLine(InvoiceLine invoiceLine, PriceList priceList, BigDecimal price);

  BigDecimal computeDiscount(InvoiceLine invoiceLine, Boolean inAti);

  BigDecimal convertUnitPrice(Boolean priceIsAti, TaxLine taxLine, BigDecimal price);

  Map<String, Object> getDiscount(Invoice invoice, InvoiceLine invoiceLine, BigDecimal price)
      throws AxelorException;

  Map<String, Object> getDiscountsFromPriceLists(
      Invoice invoice, InvoiceLine invoiceLine, BigDecimal price);

  int getDiscountTypeSelect(Invoice invoice, InvoiceLine invoiceLine, BigDecimal price);

  Unit getUnit(Product product, boolean isPurchase);

  Map<String, Object> resetProductInformation(Invoice invoice) throws AxelorException;

  boolean isAccountRequired(InvoiceLine invoiceLine);

  Map<String, Object> fillProductInformation(Invoice invoice, InvoiceLine invoiceLine)
      throws AxelorException;

  public Map<String, Object> fillPriceAndAccount(
      Invoice invoice, InvoiceLine invoiceLine, boolean isPurchase) throws AxelorException;

  public InvoiceLine cleanAnalytic(InvoiceLine invoiceLine) throws AxelorException;
}
