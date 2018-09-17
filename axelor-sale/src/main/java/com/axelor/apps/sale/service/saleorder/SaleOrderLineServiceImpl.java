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
package com.axelor.apps.sale.service.saleorder;

import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.TaxEquiv;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductMultipleQtyService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.base.service.tax.FiscalPositionService;
import com.axelor.apps.sale.db.PackLine;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleOrderLineServiceImpl implements SaleOrderLineService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject protected CurrencyService currencyService;

  @Inject protected PriceListService priceListService;

  @Inject protected ProductMultipleQtyService productMultipleQtyService;

  @Inject protected AppSaleService appSaleService;

  @Inject protected AccountManagementService accountManagementService;

  @Override
  public void computeProductInformation(
      SaleOrderLine saleOrderLine, SaleOrder saleOrder, Integer packPriceSelect)
      throws AxelorException {
    saleOrderLine.setProductName(saleOrderLine.getProduct().getName());
    saleOrderLine.setUnit(this.getSaleUnit(saleOrderLine));
    saleOrderLine.setTypeSelect(SaleOrderLineRepository.TYPE_NORMAL);
    saleOrderLine.setSubLineList(null);
    saleOrderLine.setPackPriceSelect(null);

    if (appSaleService.getAppSale().getProductPackMgt()
        && saleOrderLine.getProduct().getIsPack()
        && !saleOrderLine.getIsSubLine()) {
      saleOrderLine.setTypeSelect(SaleOrderLineRepository.TYPE_PACK);
      saleOrderLine.setPackPriceSelect(packPriceSelect);
      saleOrderLine.setSubLineList(createPackLines(saleOrderLine, saleOrder));
    }
    ;

    fillPrice(saleOrderLine, saleOrder, packPriceSelect);
  }

  @Override
  public void fillPrice(SaleOrderLine saleOrderLine, SaleOrder saleOrder, Integer packPriceSelect)
      throws AxelorException {

    boolean taxRequired = checkTaxRequired(saleOrderLine, packPriceSelect);

    if (taxRequired) {
      fillTaxInformation(saleOrderLine, saleOrder, packPriceSelect);
      saleOrderLine.setCompanyCostPrice(this.getCompanyCostPrice(saleOrder, saleOrderLine));
      BigDecimal exTaxPrice;
      BigDecimal inTaxPrice;
      if (saleOrder.getInAti()) {
        inTaxPrice = this.getInTaxUnitPrice(saleOrder, saleOrderLine, saleOrderLine.getTaxLine());
        saleOrderLine.setInTaxPrice(fillDiscount(saleOrderLine, saleOrder, inTaxPrice));
        saleOrderLine.setPrice(convertUnitPrice(true, saleOrderLine.getTaxLine(), inTaxPrice));
      } else {
        exTaxPrice = this.getExTaxUnitPrice(saleOrder, saleOrderLine, saleOrderLine.getTaxLine());
        saleOrderLine.setPrice(fillDiscount(saleOrderLine, saleOrder, exTaxPrice));
        saleOrderLine.setInTaxPrice(
            convertUnitPrice(false, saleOrderLine.getTaxLine(), exTaxPrice));
      }
    } else {
      saleOrderLine.setPrice(BigDecimal.ZERO);
      saleOrderLine.setInTaxPrice(BigDecimal.ZERO);
      saleOrderLine.setDiscountAmount(BigDecimal.ZERO);
      saleOrderLine.setCompanyCostPrice(BigDecimal.ZERO);
    }
  }

  private List<SaleOrderLine> createPackLines(SaleOrderLine saleOrderLine, SaleOrder saleOrder)
      throws AxelorException {
    List<SaleOrderLine> subLines = new ArrayList<SaleOrderLine>();

    for (PackLine packLine : saleOrderLine.getProduct().getPackLines()) {
      SaleOrderLine subLine = new SaleOrderLine();
      Product subProduct = packLine.getProduct();
      subLine.setProduct(subProduct);
      subLine.setQty(new BigDecimal(packLine.getQuantity()));
      subLine.setIsSubLine(true);
      computeProductInformation(subLine, saleOrder, saleOrderLine.getPackPriceSelect());
      computeValues(saleOrder, subLine);
      subLines.add(subLine);
    }

    return subLines;
  }

  private BigDecimal fillDiscount(
      SaleOrderLine saleOrderLine, SaleOrder saleOrder, BigDecimal price) {

    Map<String, Object> discounts = this.getDiscount(saleOrder, saleOrderLine, price);

    if (discounts != null) {
      saleOrderLine.setDiscountAmount(new BigDecimal(discounts.get("discountAmount").toString()));
      saleOrderLine.setDiscountTypeSelect((Integer) discounts.get("discountTypeSelect"));
      if (discounts.get("price") != null) {
        price = (BigDecimal) discounts.get("price");
      }
    }

    return price;
  }

  private void fillTaxInformation(
      SaleOrderLine saleOrderLine, SaleOrder saleOrder, Integer packPriceSelect)
      throws AxelorException {

    TaxLine taxLine = this.getTaxLine(saleOrder, saleOrderLine);
    saleOrderLine.setTaxLine(taxLine);

    AccountManagement accountManagement =
        accountManagementService.getAccountManagement(
            saleOrderLine.getProduct(), saleOrder.getCompany());
    Tax tax = accountManagementService.getProductTax(accountManagement, false);
    TaxEquiv taxEquiv =
        Beans.get(FiscalPositionService.class)
            .getTaxEquiv(saleOrder.getClientPartner().getFiscalPosition(), tax);

    saleOrderLine.setTaxEquiv(taxEquiv);
  }

  @Override
  public boolean checkTaxRequired(SaleOrderLine saleOrderLine, Integer packPriceSelect) {

    if (appSaleService.getAppSale().getProductPackMgt()) {

      if (saleOrderLine.getIsSubLine()
          && packPriceSelect == SaleOrderLineRepository.PACK_PRICE_ONLY) {
        return false;
      }
      if (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_PACK
          && packPriceSelect == SaleOrderLineRepository.SUBLINE_PRICE_ONLY) {
        return false;
      }
    }

    return true;
  }

  @Override
  public void computeProductInformation(
      SaleOrderLine saleOrderLine,
      SaleOrder saleOrder,
      boolean taxLineIsOptional,
      Integer packPriceSelect)
      throws AxelorException {

    Product product = saleOrderLine.getProduct();
    TaxLine taxLine;

    try {
      taxLine = getTaxLine(saleOrder, saleOrderLine);
      saleOrderLine.setTaxLine(taxLine);

      Tax tax =
          accountManagementService.getProductTax(
              accountManagementService.getAccountManagement(product, saleOrder.getCompany()),
              false);
      TaxEquiv taxEquiv =
          Beans.get(FiscalPositionService.class)
              .getTaxEquiv(saleOrder.getClientPartner().getFiscalPosition(), tax);

      saleOrderLine.setTaxEquiv(taxEquiv);
    } catch (AxelorException e) {
      if (!taxLineIsOptional) {
        throw e;
      }
      saleOrderLine.setTaxLine(null);
      saleOrderLine.setTaxEquiv(null);
    }

    if (appSaleService.getAppSale().getIsEnabledProductDescriptionCopy()) {
      saleOrderLine.setDescription(product.getDescription());
    }
  }

  @Override
  public SaleOrderLine resetProductInformation(SaleOrderLine line) {
    line.setTaxLine(null);
    line.setTaxEquiv(null);
    line.setProductName(null);
    line.setUnit(null);
    line.setCompanyCostPrice(null);
    line.setDiscountAmount(null);
    line.setDiscountTypeSelect(null);
    line.setPrice(null);
    line.setInTaxPrice(null);
    line.setExTaxTotal(null);
    line.setInTaxTotal(null);
    line.setCompanyInTaxTotal(null);
    line.setCompanyExTaxTotal(null);
    if (appSaleService.getAppSale().getIsEnabledProductDescriptionCopy()) {
      line.setDescription(null);
    }
    return line;
  }

  @Override
  public Map<String, BigDecimal> computeValues(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException {

    HashMap<String, BigDecimal> map = new HashMap<>();
    if (saleOrder == null
        || (saleOrderLine.getProduct() == null && saleOrderLine.getProductName() == null)
        || saleOrderLine.getPrice() == null
        || saleOrderLine.getInTaxPrice() == null
        || saleOrderLine.getQty() == null) {
      return map;
    }

    BigDecimal exTaxTotal;
    BigDecimal companyExTaxTotal;
    BigDecimal inTaxTotal;
    BigDecimal companyInTaxTotal;
    BigDecimal priceDiscounted = this.computeDiscount(saleOrderLine, saleOrder.getInAti());
    BigDecimal taxRate = BigDecimal.ZERO;

    if (saleOrderLine.getTaxLine() != null) {
      taxRate = saleOrderLine.getTaxLine().getValue();
    }

    if (!saleOrder.getInAti()) {
      exTaxTotal = this.computeAmount(saleOrderLine.getQty(), priceDiscounted);
      inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
      companyExTaxTotal = this.getAmountInCompanyCurrency(exTaxTotal, saleOrder);
      companyInTaxTotal = companyExTaxTotal.add(companyExTaxTotal.multiply(taxRate));
    } else {
      inTaxTotal = this.computeAmount(saleOrderLine.getQty(), priceDiscounted);
      exTaxTotal = inTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
      companyInTaxTotal = this.getAmountInCompanyCurrency(inTaxTotal, saleOrder);
      companyExTaxTotal =
          companyInTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
    }

    saleOrderLine.setInTaxTotal(inTaxTotal);
    saleOrderLine.setExTaxTotal(exTaxTotal);
    saleOrderLine.setPriceDiscounted(priceDiscounted);
    saleOrderLine.setCompanyInTaxTotal(companyInTaxTotal);
    saleOrderLine.setCompanyExTaxTotal(companyExTaxTotal);
    map.put("inTaxTotal", inTaxTotal);
    map.put("exTaxTotal", exTaxTotal);
    map.put("priceDiscounted", priceDiscounted);
    map.put("companyExTaxTotal", companyExTaxTotal);
    map.put("companyInTaxTotal", companyInTaxTotal);

    map.putAll(this.computeSubMargin(saleOrder, saleOrderLine));

    return map;
  }

  /**
   * Compute the excluded tax total amount of a sale order line.
   *
   * @param quantity The quantity.
   * @param price The unit price.
   * @return The excluded tax total amount.
   */
  @Override
  public BigDecimal computeAmount(SaleOrderLine saleOrderLine) {

    BigDecimal price = this.computeDiscount(saleOrderLine, false);

    return computeAmount(saleOrderLine.getQty(), price);
  }

  @Override
  public BigDecimal computeAmount(BigDecimal quantity, BigDecimal price) {

    BigDecimal amount =
        quantity
            .multiply(price)
            .setScale(AppSaleService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_EVEN);

    logger.debug(
        "Calcul du montant HT avec une quantité de {} pour {} : {}",
        new Object[] {quantity, price, amount});

    return amount;
  }

  @Override
  public BigDecimal getExTaxUnitPrice(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine) throws AxelorException {
    return this.getUnitPrice(saleOrder, saleOrderLine, taxLine, false);
  }

  @Override
  public BigDecimal getInTaxUnitPrice(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine) throws AxelorException {
    return this.getUnitPrice(saleOrder, saleOrderLine, taxLine, true);
  }

  /**
   * A function used to get the unit price of a sale order line, either in ati or wt
   *
   * @param saleOrder the sale order containing the sale order line
   * @param saleOrderLine
   * @param taxLine the tax applied to the unit price
   * @param resultInAti whether you want the result in ati or not
   * @return the unit price of the sale order line
   * @throws AxelorException
   */
  private BigDecimal getUnitPrice(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine, boolean resultInAti)
      throws AxelorException {
    Product product = saleOrderLine.getProduct();

    BigDecimal price =
        (product.getInAti() == resultInAti)
            ? product.getSalePrice()
            : this.convertUnitPrice(product.getInAti(), taxLine, product.getSalePrice());

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            product.getSaleCurrency(), saleOrder.getCurrency(), price, saleOrder.getCreationDate())
        .setScale(appSaleService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
  }

  @Override
  public TaxLine getTaxLine(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException {

    return Beans.get(AccountManagementService.class)
        .getTaxLine(
            saleOrder.getCreationDate(),
            saleOrderLine.getProduct(),
            saleOrder.getCompany(),
            saleOrder.getClientPartner().getFiscalPosition(),
            false);
  }

  @Override
  public BigDecimal getAmountInCompanyCurrency(BigDecimal exTaxTotal, SaleOrder saleOrder)
      throws AxelorException {

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            saleOrder.getCurrency(),
            saleOrder.getCompany().getCurrency(),
            exTaxTotal,
            saleOrder.getCreationDate())
        .setScale(AppSaleService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
  }

  @Override
  public BigDecimal getCompanyCostPrice(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException {

    Product product = saleOrderLine.getProduct();

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            product.getPurchaseCurrency(),
            saleOrder.getCompany().getCurrency(),
            product.getCostPrice(),
            saleOrder.getCreationDate())
        .setScale(AppSaleService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
  }

  @Override
  public PriceListLine getPriceListLine(SaleOrderLine saleOrderLine, PriceList priceList) {

    return priceListService.getPriceListLine(
        saleOrderLine.getProduct(), saleOrderLine.getQty(), priceList);
  }

  @Override
  public BigDecimal computeDiscount(SaleOrderLine saleOrderLine, Boolean inAti) {

    BigDecimal price = inAti ? saleOrderLine.getInTaxPrice() : saleOrderLine.getPrice();

    return priceListService.computeDiscount(
        price, saleOrderLine.getDiscountTypeSelect(), saleOrderLine.getDiscountAmount());
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
  public Map<String, Object> getDiscount(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, BigDecimal price) {

    PriceList priceList = saleOrder.getPriceList();
    if (priceList == null) {
      return null;
    }

    PriceListLine priceListLine = this.getPriceListLine(saleOrderLine, priceList);
    return priceListService.getReplacedPriceAndDiscounts(priceList, priceListLine, price);
  }

  @Override
  public int getDiscountTypeSelect(SaleOrder saleOrder, SaleOrderLine saleOrderLine) {
    PriceList priceList = saleOrder.getPriceList();
    if (priceList != null) {
      PriceListLine priceListLine = this.getPriceListLine(saleOrderLine, priceList);

      return priceListLine.getTypeSelect();
    }
    return 0;
  }

  @Override
  public Unit getSaleUnit(SaleOrderLine saleOrderLine) {
    Unit unit = saleOrderLine.getProduct().getSalesUnit();
    if (unit == null) {
      unit = saleOrderLine.getProduct().getUnit();
    }
    return unit;
  }

  @Override
  public BigDecimal computeTotalPack(SaleOrderLine saleOrderLine) {

    BigDecimal totalPack = BigDecimal.ZERO;

    if (saleOrderLine.getSubLineList() != null) {
      for (SaleOrderLine subLine : saleOrderLine.getSubLineList()) {
        totalPack = totalPack.add(subLine.getInTaxTotal());
      }
    }

    return totalPack;
  }

  @Override
  public SaleOrder getSaleOrder(Context context) {

    Context parentContext = context.getParent();

    if (!parentContext.getContextClass().toString().equals(SaleOrder.class.toString())) {

      parentContext = parentContext.getParent();
    }

    if (parentContext == null) {
      return null;
    }

    SaleOrder saleOrder = parentContext.asType(SaleOrder.class);

    if (!parentContext.getContextClass().toString().equals(SaleOrder.class.toString())) {

      SaleOrderLine saleOrderLine = context.asType(SaleOrderLine.class);

      saleOrder = saleOrderLine.getSaleOrder();
    }

    return saleOrder;
  }

  @Override
  public Map<String, BigDecimal> computeSubMargin(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException {

    HashMap<String, BigDecimal> map = new HashMap<>();

    BigDecimal subTotalCostPrice = BigDecimal.ZERO;
    BigDecimal subTotalGrossMargin = BigDecimal.ZERO;
    BigDecimal subMarginRate = BigDecimal.ZERO;
    BigDecimal totalWT = BigDecimal.ZERO;

    if (saleOrderLine.getProduct() != null
        && saleOrderLine.getProduct().getCostPrice().compareTo(BigDecimal.ZERO) != 0
        && saleOrderLine.getExTaxTotal().compareTo(BigDecimal.ZERO) != 0) {

      totalWT =
          currencyService.getAmountCurrencyConvertedAtDate(
              saleOrder.getCurrency(),
              saleOrder.getCompany().getCurrency(),
              saleOrderLine.getExTaxTotal(),
              null);

      logger.debug("Total WT in company currency: {}", totalWT);
      subTotalCostPrice =
          saleOrderLine.getProduct().getCostPrice().multiply(saleOrderLine.getQty());
      logger.debug("Subtotal cost price: {}", subTotalCostPrice);
      subTotalGrossMargin = totalWT.subtract(subTotalCostPrice);
      logger.debug("Subtotal gross margin: {}", subTotalGrossMargin);
      subMarginRate =
          subTotalGrossMargin
              .divide(subTotalCostPrice, RoundingMode.HALF_EVEN)
              .multiply(new BigDecimal(100));
      logger.debug("Subtotal gross margin rate: {}", subMarginRate);
    }

    saleOrderLine.setSubTotalCostPrice(subTotalCostPrice);
    saleOrderLine.setSubTotalGrossMargin(subTotalGrossMargin);
    saleOrderLine.setSubMarginRate(subMarginRate);

    map.put("subTotalCostPrice", subTotalCostPrice);
    map.put("subTotalGrossMargin", subTotalGrossMargin);
    map.put("subMarginRate", subMarginRate);
    return map;
  }

  @Override
  public BigDecimal getAvailableStock(SaleOrderLine saleOrderLine) {
    // defined in supplychain
    return BigDecimal.ZERO;
  }

  @Override
  public void checkMultipleQty(SaleOrderLine saleOrderLine, ActionResponse response) {

    Product product = saleOrderLine.getProduct();

    if (product == null) {
      return;
    }

    productMultipleQtyService.checkMultipleQty(
        saleOrderLine.getQty(),
        product.getSaleProductMultipleQtyList(),
        product.getAllowToForceSaleQty(),
        response);
  }
}
