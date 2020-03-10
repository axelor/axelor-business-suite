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
package com.axelor.apps.account.service.invoice;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AnalyticDistributionTemplate;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.TaxEquiv;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.service.AccountManagementAccountService;
import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.AppAccountRepository;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.tax.FiscalPositionService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.common.base.MoreObjects;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvoiceLineServiceImpl implements InvoiceLineService {

  protected AccountManagementAccountService accountManagementAccountService;
  protected CurrencyService currencyService;
  protected PriceListService priceListService;
  protected AppAccountService appAccountService;
  protected AnalyticMoveLineService analyticMoveLineService;

  @Inject
  public InvoiceLineServiceImpl(
      CurrencyService currencyService,
      PriceListService priceListService,
      AppAccountService appAccountService,
      AnalyticMoveLineService analyticMoveLineService,
      AccountManagementAccountService accountManagementAccountService) {

    this.accountManagementAccountService = accountManagementAccountService;
    this.currencyService = currencyService;
    this.priceListService = priceListService;
    this.appAccountService = appAccountService;
    this.analyticMoveLineService = analyticMoveLineService;
  }

  public List<AnalyticMoveLine> getAndComputeAnalyticDistribution(
      InvoiceLine invoiceLine, Invoice invoice) {

    if (appAccountService.getAppAccount().getAnalyticDistributionTypeSelect()
        == AppAccountRepository.DISTRIBUTION_TYPE_FREE) {
      return MoreObjects.firstNonNull(invoiceLine.getAnalyticMoveLineList(), new ArrayList<>());
    }

    AnalyticDistributionTemplate analyticDistributionTemplate =
        analyticMoveLineService.getAnalyticDistributionTemplate(
            invoice.getPartner(), invoiceLine.getProduct(), invoice.getCompany());

    invoiceLine.setAnalyticDistributionTemplate(analyticDistributionTemplate);

    if (invoiceLine.getAnalyticMoveLineList() != null) {
      invoiceLine.getAnalyticMoveLineList().clear();
    }

    return this.computeAnalyticDistribution(invoiceLine);
  }

  @Override
  public List<AnalyticMoveLine> computeAnalyticDistribution(InvoiceLine invoiceLine) {

    List<AnalyticMoveLine> analyticMoveLineList = invoiceLine.getAnalyticMoveLineList();

    if ((analyticMoveLineList == null || analyticMoveLineList.isEmpty())) {
      return createAnalyticDistributionWithTemplate(invoiceLine);
    } else {
      LocalDate date = appAccountService.getTodayDate();
      for (AnalyticMoveLine analyticMoveLine : analyticMoveLineList) {
        analyticMoveLineService.updateAnalyticMoveLine(
            analyticMoveLine, invoiceLine.getCompanyExTaxTotal(), date);
      }
      return analyticMoveLineList;
    }
  }

  @Override
  public List<AnalyticMoveLine> createAnalyticDistributionWithTemplate(InvoiceLine invoiceLine) {
    List<AnalyticMoveLine> analyticMoveLineList =
        analyticMoveLineService.generateLines(
            invoiceLine.getAnalyticDistributionTemplate(),
            invoiceLine.getCompanyExTaxTotal(),
            AnalyticMoveLineRepository.STATUS_FORECAST_INVOICE,
            appAccountService.getTodayDate());

    return analyticMoveLineList;
  }

  @Override
  public TaxLine getTaxLine(Invoice invoice, InvoiceLine invoiceLine, boolean isPurchase)
      throws AxelorException {

    return accountManagementAccountService.getTaxLine(
        appAccountService.getTodayDate(),
        invoiceLine.getProduct(),
        invoice.getCompany(),
        invoice.getPartner().getFiscalPosition(),
        isPurchase);
  }

  @Override
  public BigDecimal getExTaxUnitPrice(
      Invoice invoice, InvoiceLine invoiceLine, TaxLine taxLine, boolean isPurchase)
      throws AxelorException {

    return this.getUnitPrice(invoice, invoiceLine, taxLine, isPurchase, false);
  }

  @Override
  public BigDecimal getInTaxUnitPrice(
      Invoice invoice, InvoiceLine invoiceLine, TaxLine taxLine, boolean isPurchase)
      throws AxelorException {

    return this.getUnitPrice(invoice, invoiceLine, taxLine, isPurchase, true);
  }

  /**
   * A function used to get the unit price of an invoice line, either in ati or wt
   *
   * @param invoice the invoice containing the invoice line
   * @param invoiceLine
   * @param taxLine the tax line applied to the unit price
   * @param isPurchase
   * @param resultInAti whether or not you want the result unit price in ati
   * @return the unit price of the invoice line
   * @throws AxelorException
   */
  protected BigDecimal getUnitPrice(
      Invoice invoice,
      InvoiceLine invoiceLine,
      TaxLine taxLine,
      boolean isPurchase,
      boolean resultInAti)
      throws AxelorException {
    Product product = invoiceLine.getProduct();

    BigDecimal price = null;
    Currency productCurrency;

    if (isPurchase) {
      price = product.getPurchasePrice();
      productCurrency = product.getPurchaseCurrency();
    } else {
      price = product.getSalePrice();
      productCurrency = product.getSaleCurrency();
    }

    if (product.getInAti() != resultInAti) {
      price = this.convertUnitPrice(product.getInAti(), taxLine, price);
    }

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            productCurrency, invoice.getCurrency(), price, invoice.getInvoiceDate())
        .setScale(appAccountService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
  }

  @Override
  public BigDecimal getAccountingExTaxTotal(BigDecimal exTaxTotal, Invoice invoice)
      throws AxelorException {

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            invoice.getCurrency(),
            invoice.getPartner().getCurrency(),
            exTaxTotal,
            invoice.getInvoiceDate())
        .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
  }

  @Override
  public BigDecimal getCompanyExTaxTotal(BigDecimal exTaxTotal, Invoice invoice)
      throws AxelorException {

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            invoice.getCurrency(),
            invoice.getCompany().getCurrency(),
            exTaxTotal,
            invoice.getInvoiceDate())
        .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
  }

  @Override
  public PriceListLine getPriceListLine(
      InvoiceLine invoiceLine, PriceList priceList, BigDecimal price) {

    return priceListService.getPriceListLine(
        invoiceLine.getProduct(), invoiceLine.getQty(), priceList, price);
  }

  @Override
  public BigDecimal computeDiscount(InvoiceLine invoiceLine, Boolean inAti) {

    BigDecimal unitPrice = inAti ? invoiceLine.getInTaxPrice() : invoiceLine.getPrice();

    return priceListService.computeDiscount(
        unitPrice, invoiceLine.getDiscountTypeSelect(), invoiceLine.getDiscountAmount());
  }

  @Override
  public BigDecimal convertUnitPrice(Boolean priceIsAti, TaxLine taxLine, BigDecimal price) {

    if (taxLine == null) {
      return price;
    }

    if (priceIsAti) {
      price = price.divide(taxLine.getValue().add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
    } else {
      price = price.add(price.multiply(taxLine.getValue()));
    }
    return price;
  }

  @Override
  public Map<String, Object> getDiscount(Invoice invoice, InvoiceLine invoiceLine, BigDecimal price)
      throws AxelorException {

    Map<String, Object> rawDiscounts = getDiscountsFromPriceLists(invoice, invoiceLine, price);

    Map<String, Object> processedDiscounts = new HashMap<>();

    if (rawDiscounts != null) {
      if (rawDiscounts.get("price") != null) {
        price = (BigDecimal) rawDiscounts.get("price");
      }
      if (invoiceLine.getProduct().getInAti() != invoice.getInAti()
          && (Integer) rawDiscounts.get("discountTypeSelect")
              != PriceListLineRepository.AMOUNT_TYPE_PERCENT) {
        processedDiscounts.put(
            "discountAmount",
            this.convertUnitPrice(
                invoiceLine.getProduct().getInAti(),
                invoiceLine.getTaxLine(),
                (BigDecimal) rawDiscounts.get("discountAmount")));
      } else {
        processedDiscounts.put("discountAmount", rawDiscounts.get("discountAmount"));
      }
      processedDiscounts.put("discountTypeSelect", rawDiscounts.get("discountTypeSelect"));
    }

    if (price.compareTo(
            invoiceLine.getProduct().getInAti()
                ? invoiceLine.getInTaxPrice()
                : invoiceLine.getPrice())
        != 0) {
      if (invoiceLine.getProduct().getInAti()) {
        processedDiscounts.put("inTaxPrice", price);
        processedDiscounts.put(
            "price", this.convertUnitPrice(true, invoiceLine.getTaxLine(), price));
      } else {
        processedDiscounts.put("price", price);
        processedDiscounts.put(
            "inTaxPrice", this.convertUnitPrice(false, invoiceLine.getTaxLine(), price));
      }
    }

    return processedDiscounts;
  }

  @Override
  public Map<String, Object> getDiscountsFromPriceLists(
      Invoice invoice, InvoiceLine invoiceLine, BigDecimal price) {

    Map<String, Object> discounts = null;

    PriceList priceList = invoice.getPriceList();

    if (priceList != null) {
      PriceListLine priceListLine = this.getPriceListLine(invoiceLine, priceList, price);
      discounts = priceListService.getReplacedPriceAndDiscounts(priceList, priceListLine, price);
    }

    return discounts;
  }

  @Override
  public int getDiscountTypeSelect(Invoice invoice, InvoiceLine invoiceLine, BigDecimal price) {
    PriceList priceList = invoice.getPriceList();
    if (priceList != null) {
      PriceListLine priceListLine = this.getPriceListLine(invoiceLine, priceList, price);

      return priceListLine.getTypeSelect();
    }
    return 0;
  }

  @Override
  public Unit getUnit(Product product, boolean isPurchase) {
    return product.getUnit();
  }

  @Override
  public Map<String, Object> resetProductInformation(Invoice invoice) throws AxelorException {
    Map<String, Object> productInformation = new HashMap<>();
    productInformation.put("taxLine", null);
    productInformation.put("taxEquiv", null);
    productInformation.put("taxCode", null);
    productInformation.put("taxRate", null);
    productInformation.put("productName", null);
    productInformation.put("unit", null);
    productInformation.put("discountAmount", null);
    productInformation.put("discountTypeSelect", PriceListLineRepository.AMOUNT_TYPE_NONE);
    productInformation.put("price", null);
    productInformation.put("inTaxPrice", null);
    productInformation.put("exTaxTotal", null);
    productInformation.put("inTaxTotal", null);
    productInformation.put("companyInTaxTotal", null);
    productInformation.put("companyExTaxTotal", null);
    productInformation.put("subLineList", null);
    productInformation.put("totalPack", null);
    productInformation.put("packPriceSelect", 0);
    productInformation.put("typeSelect", InvoiceLineRepository.TYPE_NORMAL);
    boolean isPurchase = InvoiceToolService.isPurchase(invoice);
    if ((isPurchase
            && appAccountService.getAppInvoice().getIsEnabledProductDescriptionCopyForCustomers())
        || (!isPurchase
            && appAccountService
                .getAppInvoice()
                .getIsEnabledProductDescriptionCopyForSuppliers())) {
      productInformation.put("description", null);
    }
    if (appAccountService.getAppAccount().getAnalyticDistributionTypeSelect()
        == AppAccountRepository.DISTRIBUTION_TYPE_PRODUCT) {
      productInformation.put("analyticMoveLineList", null);
    }
    return productInformation;
  }

  @Override
  public Map<String, Object> fillProductInformation(Invoice invoice, InvoiceLine invoiceLine)
      throws AxelorException {

    boolean isPurchase = InvoiceToolService.isPurchase(invoice);
    Map<String, Object> productInformation = fillPriceAndAccount(invoice, invoiceLine, isPurchase);
    productInformation.put("productName", invoiceLine.getProduct().getName());
    productInformation.put("productCode", invoiceLine.getProduct().getCode());
    productInformation.put("unit", this.getUnit(invoiceLine.getProduct(), isPurchase));

    if ((isPurchase
            && appAccountService.getAppInvoice().getIsEnabledProductDescriptionCopyForCustomers())
        || (!isPurchase
            && appAccountService
                .getAppInvoice()
                .getIsEnabledProductDescriptionCopyForSuppliers())) {
      productInformation.put("description", invoiceLine.getProduct().getDescription());
    }

    return productInformation;
  }

  @Override
  public Map<String, Object> fillPriceAndAccount(
      Invoice invoice, InvoiceLine invoiceLine, boolean isPurchase) throws AxelorException {

    Map<String, Object> productInformation = resetProductInformation(invoice);

    boolean isAccountRequired = isAccountRequired(invoiceLine);
    Product product = invoiceLine.getProduct();
    TaxLine taxLine = null;
    Company company = invoice.getCompany();
    FiscalPosition fiscalPosition = invoice.getPartner().getFiscalPosition();
    try {
      taxLine = this.getTaxLine(invoice, invoiceLine, isPurchase);
      invoiceLine.setTaxLine(taxLine);
      productInformation.put("taxLine", taxLine);
      productInformation.put("taxRate", taxLine.getValue());
      productInformation.put("taxCode", taxLine.getTax().getCode());

      Tax tax = accountManagementAccountService.getProductTax(product, company, null, isPurchase);
      TaxEquiv taxEquiv = Beans.get(FiscalPositionService.class).getTaxEquiv(fiscalPosition, tax);
      productInformation.put("taxEquiv", taxEquiv);

      Account account =
          accountManagementAccountService.getProductAccount(
              product, company, fiscalPosition, isPurchase, invoiceLine.getFixedAssets());
      productInformation.put("account", account);

    } catch (AxelorException e) {
      productInformation.put("error", e.getMessage());
    }

    if (isAccountRequired) {
      BigDecimal price = this.getExTaxUnitPrice(invoice, invoiceLine, taxLine, isPurchase);
      BigDecimal inTaxPrice = this.getInTaxUnitPrice(invoice, invoiceLine, taxLine, isPurchase);

      productInformation.put("price", price);
      productInformation.put("inTaxPrice", inTaxPrice);

      productInformation.putAll(
          this.getDiscount(invoice, invoiceLine, product.getInAti() ? inTaxPrice : price));

      productInformation.put("productName", invoiceLine.getProduct().getName());
    }

    return productInformation;
  }

  @Override
  public boolean isAccountRequired(InvoiceLine invoiceLine) {
    return true;
  }
}
