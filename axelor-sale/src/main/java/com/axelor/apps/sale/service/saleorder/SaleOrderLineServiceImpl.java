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
package com.axelor.apps.sale.service.saleorder;

import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.TaxEquiv;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Pricing;
import com.axelor.apps.base.db.PricingLine;
import com.axelor.apps.base.db.PricingRule;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.db.repo.PricingRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCategoryService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.ProductMultipleQtyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.base.service.tax.FiscalPositionService;
import com.axelor.apps.sale.db.ComplementaryProduct;
import com.axelor.apps.sale.db.ComplementaryProductSelected;
import com.axelor.apps.sale.db.Pack;
import com.axelor.apps.sale.db.PackLine;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.ComplementaryProductRepository;
import com.axelor.apps.sale.db.repo.PackLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.translation.ITranslation;
import com.axelor.common.ObjectUtils;
import com.axelor.db.EntityHelper;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.loader.ModuleManager;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.axelor.script.GroovyScriptHelper;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleOrderLineServiceImpl implements SaleOrderLineService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected CurrencyService currencyService;
  protected PriceListService priceListService;
  protected ProductMultipleQtyService productMultipleQtyService;
  protected AppBaseService appBaseService;
  protected AppSaleService appSaleService;
  protected AccountManagementService accountManagementService;
  protected SaleOrderLineRepository saleOrderLineRepo;

  @Inject
  public SaleOrderLineServiceImpl(
      CurrencyService currencyService,
      PriceListService priceListService,
      ProductMultipleQtyService productMultipleQtyService,
      AppBaseService appBaseService,
      AppSaleService appSaleService,
      AccountManagementService accountManagementService,
      SaleOrderLineRepository saleOrderLineRepo) {
    this.currencyService = currencyService;
    this.priceListService = priceListService;
    this.productMultipleQtyService = productMultipleQtyService;
    this.appBaseService = appBaseService;
    this.appSaleService = appSaleService;
    this.accountManagementService = accountManagementService;
    this.saleOrderLineRepo = saleOrderLineRepo;
  }

  @Inject protected ProductCategoryService productCategoryService;
  @Inject protected PricingRepository pricingRepository;
  @Inject protected ProductCompanyService productCompanyService;

  @Override
  public void computeProductInformation(SaleOrderLine saleOrderLine, SaleOrder saleOrder)
      throws AxelorException, ClassNotFoundException {

    // Reset fields which are going to recalculate in this method
    resetProductInformation(saleOrderLine);

    if (!saleOrderLine.getEnableFreezeFields()) {
      saleOrderLine.setProductName(saleOrderLine.getProduct().getName());
    }
    saleOrderLine.setUnit(this.getSaleUnit(saleOrderLine));
    if (appSaleService.getAppSale().getIsEnabledProductDescriptionCopy()) {
      saleOrderLine.setDescription(saleOrderLine.getProduct().getDescription());
    }

    saleOrderLine.setTypeSelect(SaleOrderLineRepository.TYPE_NORMAL);
    fillPrice(saleOrderLine, saleOrder);
    fillComplementaryProductList(saleOrderLine);
  }

  @Override
  public void fillPrice(SaleOrderLine saleOrderLine, SaleOrder saleOrder)
      throws AxelorException, ClassNotFoundException {

    // Populate fields from pricing scale before starting process of fillPrice
    computePricingScale(saleOrder, saleOrderLine);

    fillTaxInformation(saleOrderLine, saleOrder);
    saleOrderLine.setCompanyCostPrice(this.getCompanyCostPrice(saleOrder, saleOrderLine));
    BigDecimal exTaxPrice;
    BigDecimal inTaxPrice;
    if (saleOrderLine.getProduct().getInAti()) {
      inTaxPrice = this.getInTaxUnitPrice(saleOrder, saleOrderLine, saleOrderLine.getTaxLine());
      inTaxPrice = fillDiscount(saleOrderLine, saleOrder, inTaxPrice);
      if (!saleOrderLine.getEnableFreezeFields()) {
        saleOrderLine.setPrice(convertUnitPrice(true, saleOrderLine.getTaxLine(), inTaxPrice));
        saleOrderLine.setInTaxPrice(inTaxPrice);
      }
    } else {
      exTaxPrice = this.getExTaxUnitPrice(saleOrder, saleOrderLine, saleOrderLine.getTaxLine());
      exTaxPrice = fillDiscount(saleOrderLine, saleOrder, exTaxPrice);
      if (!saleOrderLine.getEnableFreezeFields()) {
        saleOrderLine.setPrice(exTaxPrice);
        saleOrderLine.setInTaxPrice(
            convertUnitPrice(false, saleOrderLine.getTaxLine(), exTaxPrice));
      }
    }
  }

  @Override
  public void fillComplementaryProductList(SaleOrderLine saleOrderLine) {
    if (saleOrderLine.getProduct() != null
        && saleOrderLine.getProduct().getComplementaryProductList() != null) {
      if (saleOrderLine.getSelectedComplementaryProductList() == null) {
        saleOrderLine.setSelectedComplementaryProductList(new ArrayList<>());
      }
      saleOrderLine.clearSelectedComplementaryProductList();
      for (ComplementaryProduct complProduct :
          saleOrderLine.getProduct().getComplementaryProductList()) {
        ComplementaryProductSelected newComplProductLine = new ComplementaryProductSelected();

        newComplProductLine.setProduct(complProduct.getProduct());
        newComplProductLine.setQty(complProduct.getQty());
        newComplProductLine.setOptional(complProduct.getOptional());

        newComplProductLine.setIsSelected(!complProduct.getOptional());
        newComplProductLine.setSaleOrderLine(saleOrderLine);
        saleOrderLine.addSelectedComplementaryProductListItem(newComplProductLine);
      }
    }
  }

  protected BigDecimal fillDiscount(
      SaleOrderLine saleOrderLine, SaleOrder saleOrder, BigDecimal price) {

    Map<String, Object> discounts =
        this.getDiscountsFromPriceLists(saleOrder, saleOrderLine, price);

    if (discounts != null) {
      if (discounts.get("price") != null) {
        price = (BigDecimal) discounts.get("price");
      }
      if (saleOrderLine.getProduct().getInAti() != saleOrder.getInAti()
          && (Integer) discounts.get("discountTypeSelect")
              != PriceListLineRepository.AMOUNT_TYPE_PERCENT) {
        saleOrderLine.setDiscountAmount(
            this.convertUnitPrice(
                saleOrderLine.getProduct().getInAti(),
                saleOrderLine.getTaxLine(),
                (BigDecimal) discounts.get("discountAmount")));
      } else {
        saleOrderLine.setDiscountAmount((BigDecimal) discounts.get("discountAmount"));
      }
      saleOrderLine.setDiscountTypeSelect((Integer) discounts.get("discountTypeSelect"));
    } else if (!saleOrder.getTemplate()) {
      saleOrderLine.setDiscountAmount(BigDecimal.ZERO);
      saleOrderLine.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
    }

    return price;
  }

  protected void fillTaxInformation(SaleOrderLine saleOrderLine, SaleOrder saleOrder)
      throws AxelorException {

    if (saleOrder.getClientPartner() != null) {
      TaxLine taxLine = this.getTaxLine(saleOrder, saleOrderLine);
      saleOrderLine.setTaxLine(taxLine);

      FiscalPosition fiscalPosition = saleOrder.getClientPartner().getFiscalPosition();

      Tax tax =
          accountManagementService.getProductTax(
              saleOrderLine.getProduct(), saleOrder.getCompany(), fiscalPosition, false);

      TaxEquiv taxEquiv = Beans.get(FiscalPositionService.class).getTaxEquiv(fiscalPosition, tax);

      saleOrderLine.setTaxEquiv(taxEquiv);
    } else {
      saleOrderLine.setTaxLine(null);
      saleOrderLine.setTaxEquiv(null);
    }
  }

  @Override
  public SaleOrderLine resetProductInformation(SaleOrderLine line) {
    if (!line.getEnableFreezeFields()) {
      line.setProductName(null);
      line.setPrice(null);
    }
    line.setTaxLine(null);
    line.setTaxEquiv(null);
    line.setUnit(null);
    line.setCompanyCostPrice(null);
    line.setDiscountAmount(null);
    line.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
    line.setInTaxPrice(null);
    line.setExTaxTotal(null);
    line.setInTaxTotal(null);
    line.setCompanyInTaxTotal(null);
    line.setCompanyExTaxTotal(null);
    if (appSaleService.getAppSale().getIsEnabledProductDescriptionCopy()) {
      line.setDescription(null);
    }
    line.setSelectedComplementaryProductList(null);
    return line;
  }

  @Override
  public Map<String, BigDecimal> computeValues(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException {

    HashMap<String, BigDecimal> map = new HashMap<>();
    if (saleOrder == null
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
    BigDecimal subTotalCostPrice = BigDecimal.ZERO;

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

    if (saleOrderLine.getProduct() != null
        && ((BigDecimal)
                    productCompanyService.get(
                        saleOrderLine.getProduct(), "costPrice", saleOrder.getCompany()))
                .compareTo(BigDecimal.ZERO)
            != 0) {
      subTotalCostPrice =
          ((BigDecimal)
                  productCompanyService.get(
                      saleOrderLine.getProduct(), "costPrice", saleOrder.getCompany()))
              .multiply(saleOrderLine.getQty());
    }

    saleOrderLine.setInTaxTotal(inTaxTotal);
    saleOrderLine.setExTaxTotal(exTaxTotal);
    saleOrderLine.setPriceDiscounted(priceDiscounted);
    saleOrderLine.setCompanyInTaxTotal(companyInTaxTotal);
    saleOrderLine.setCompanyExTaxTotal(companyExTaxTotal);
    saleOrderLine.setSubTotalCostPrice(subTotalCostPrice);
    map.put("inTaxTotal", inTaxTotal);
    map.put("exTaxTotal", exTaxTotal);
    map.put("priceDiscounted", priceDiscounted);
    map.put("companyExTaxTotal", companyExTaxTotal);
    map.put("companyInTaxTotal", companyInTaxTotal);
    map.put("subTotalCostPrice", subTotalCostPrice);

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
            .setScale(AppSaleService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);

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

    Boolean productInAti =
        (Boolean) productCompanyService.get(product, "inAti", saleOrder.getCompany());

    // Consider price if already computed from pricing scale else get it from product
    BigDecimal productSalePrice = saleOrderLine.getPrice();

    if (productSalePrice.compareTo(BigDecimal.ZERO) == 0) {
      productSalePrice =
          (BigDecimal) productCompanyService.get(product, "salePrice", saleOrder.getCompany());
    }

    BigDecimal price =
        (productInAti == resultInAti)
            ? productSalePrice
            : this.convertUnitPrice(productInAti, taxLine, productSalePrice);

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            (Currency) productCompanyService.get(product, "saleCurrency", saleOrder.getCompany()),
            saleOrder.getCurrency(),
            price,
            saleOrder.getCreationDate())
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
            (Currency)
                productCompanyService.get(product, "purchaseCurrency", saleOrder.getCompany()),
            saleOrder.getCompany().getCurrency(),
            (BigDecimal) productCompanyService.get(product, "costPrice", saleOrder.getCompany()),
            saleOrder.getCreationDate())
        .setScale(AppSaleService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
  }

  @Override
  public PriceListLine getPriceListLine(
      SaleOrderLine saleOrderLine, PriceList priceList, BigDecimal price) {

    return priceListService.getPriceListLine(
        saleOrderLine.getProduct(), saleOrderLine.getQty(), priceList, price);
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
  public Map<String, Object> getDiscountsFromPriceLists(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, BigDecimal price) {

    Map<String, Object> discounts = null;

    PriceList priceList = saleOrder.getPriceList();

    if (priceList != null) {
      PriceListLine priceListLine = this.getPriceListLine(saleOrderLine, priceList, price);
      discounts = priceListService.getReplacedPriceAndDiscounts(priceList, priceListLine, price);

      if (saleOrder.getTemplate()) {
        Integer manualDiscountAmountType = saleOrderLine.getDiscountTypeSelect();
        BigDecimal manualDiscountAmount = saleOrderLine.getDiscountAmount();
        Integer priceListDiscountAmountType = (Integer) discounts.get("discountTypeSelect");
        BigDecimal priceListDiscountAmount = (BigDecimal) discounts.get("discountAmount");

        if (!manualDiscountAmountType.equals(priceListDiscountAmountType)
            && manualDiscountAmountType.equals(PriceListLineRepository.AMOUNT_TYPE_PERCENT)
            && priceListDiscountAmountType.equals(PriceListLineRepository.AMOUNT_TYPE_FIXED)) {
          priceListDiscountAmount =
              priceListDiscountAmount
                  .multiply(new BigDecimal(100))
                  .divide(price, 2, RoundingMode.HALF_UP);
        } else if (!manualDiscountAmountType.equals(priceListDiscountAmountType)
            && manualDiscountAmountType.equals(PriceListLineRepository.AMOUNT_TYPE_FIXED)
            && priceListDiscountAmountType.equals(PriceListLineRepository.AMOUNT_TYPE_PERCENT)) {
          priceListDiscountAmount =
              priceListDiscountAmount
                  .multiply(price)
                  .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        }

        if (manualDiscountAmount.compareTo(priceListDiscountAmount) > 0) {
          discounts.put("discountAmount", manualDiscountAmount);
          discounts.put("discountTypeSelect", manualDiscountAmountType);
        }
      }
    }

    return discounts;
  }

  @Override
  public int getDiscountTypeSelect(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, BigDecimal price) {
    PriceList priceList = saleOrder.getPriceList();
    if (priceList != null) {
      PriceListLine priceListLine = this.getPriceListLine(saleOrderLine, priceList, price);

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
  public SaleOrder getSaleOrder(Context context) {

    Context parentContext = context.getParent();

    SaleOrderLine saleOrderLine = context.asType(SaleOrderLine.class);
    SaleOrder saleOrder = saleOrderLine.getSaleOrder();

    if (parentContext != null && !parentContext.getContextClass().equals(SaleOrder.class)) {
      parentContext = parentContext.getParent();
    }

    if (parentContext != null && parentContext.getContextClass().equals(SaleOrder.class)) {
      saleOrder = parentContext.asType(SaleOrder.class);
    }

    return saleOrder;
  }

  @Override
  public Map<String, BigDecimal> computeSubMargin(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException {

    HashMap<String, BigDecimal> map = new HashMap<>();

    BigDecimal subTotalCostPrice = BigDecimal.ZERO;
    BigDecimal subTotalGrossProfit = BigDecimal.ZERO;
    BigDecimal subMarginRate = BigDecimal.ZERO;
    BigDecimal subTotalMarkup = BigDecimal.ZERO;
    BigDecimal totalWT = BigDecimal.ZERO;

    if (saleOrderLine.getProduct() != null
        && ((BigDecimal)
                    productCompanyService.get(
                        saleOrderLine.getProduct(), "costPrice", saleOrder.getCompany()))
                .compareTo(BigDecimal.ZERO)
            != 0
        && saleOrderLine.getExTaxTotal().compareTo(BigDecimal.ZERO) != 0) {

      totalWT =
          currencyService.getAmountCurrencyConvertedAtDate(
              saleOrder.getCurrency(),
              saleOrder.getCompany().getCurrency(),
              saleOrderLine.getExTaxTotal(),
              null);

      logger.debug("Total WT in company currency: {}", totalWT);
      subTotalCostPrice = saleOrderLine.getSubTotalCostPrice();
      logger.debug("Subtotal cost price: {}", subTotalCostPrice);
      subTotalGrossProfit = totalWT.subtract(subTotalCostPrice);
      logger.debug("Subtotal gross margin: {}", subTotalGrossProfit);
      subMarginRate =
          subTotalGrossProfit
              .multiply(new BigDecimal(100))
              .divide(totalWT, 2, RoundingMode.HALF_UP);
      logger.debug("Subtotal gross margin rate: {}", subMarginRate);

      if (subTotalCostPrice.compareTo(BigDecimal.ZERO) != 0) {
        subTotalMarkup =
            subTotalGrossProfit
                .multiply(new BigDecimal(100))
                .divide(subTotalCostPrice, 2, RoundingMode.HALF_UP);
        logger.debug("Subtotal markup: {}", subTotalMarkup);
      }
    }

    saleOrderLine.setSubTotalGrossMargin(subTotalGrossProfit);
    saleOrderLine.setSubMarginRate(subMarginRate);
    saleOrderLine.setSubTotalMarkup(subTotalMarkup);

    map.put("subTotalGrossMargin", subTotalGrossProfit);
    map.put("subMarginRate", subMarginRate);
    map.put("subTotalMarkup", subTotalMarkup);
    return map;
  }

  @Override
  public BigDecimal getAvailableStock(SaleOrder saleOrder, SaleOrderLine saleOrderLine) {
    // defined in supplychain
    return BigDecimal.ZERO;
  }

  @Override
  public BigDecimal getAllocatedStock(SaleOrder saleOrder, SaleOrderLine saleOrderLine) {
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

  @Override
  public SaleOrderLine createSaleOrderLine(
      PackLine packLine,
      SaleOrder saleOrder,
      BigDecimal packQty,
      BigDecimal conversionRate,
      Integer sequence) {

    if (packLine.getTypeSelect() == PackLineRepository.TYPE_START_OF_PACK
        || packLine.getTypeSelect() == PackLineRepository.TYPE_END_OF_PACK) {
      return createStartOfPackAndEndOfPackTypeSaleOrderLine(
          packLine.getPack(), saleOrder, packQty, packLine, packLine.getTypeSelect(), sequence);
    }

    if (packLine.getProductName() != null) {
      SaleOrderLine soLine = new SaleOrderLine();

      Product product = packLine.getProduct();
      soLine.setProduct(product);
      soLine.setProductName(packLine.getProductName());
      if (packLine.getQuantity() != null) {
        soLine.setQty(
            packLine
                .getQuantity()
                .multiply(packQty)
                .setScale(appBaseService.getNbDecimalDigitForQty(), RoundingMode.HALF_UP));
      }
      soLine.setUnit(packLine.getUnit());
      soLine.setTypeSelect(packLine.getTypeSelect());
      soLine.setSequence(sequence);
      if (packLine.getPrice() != null) {
        soLine.setPrice(packLine.getPrice().multiply(conversionRate));
      }

      if (product != null) {
        if (appSaleService.getAppSale().getIsEnabledProductDescriptionCopy()) {
          soLine.setDescription(product.getDescription());
        }
        try {
          this.fillPriceFromPackLine(soLine, saleOrder);
          this.computeValues(saleOrder, soLine);
        } catch (AxelorException e) {
          TraceBackService.trace(e);
        }
      }
      return soLine;
    }
    return null;
  }

  @Override
  public BigDecimal computeMaxDiscount(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException {
    Optional<BigDecimal> maxDiscount = Optional.empty();
    Product product = saleOrderLine.getProduct();
    if (product != null && product.getProductCategory() != null) {
      maxDiscount = productCategoryService.computeMaxDiscount(product.getProductCategory());
    }
    if (!maxDiscount.isPresent()
        || saleOrderLine.getDiscountTypeSelect() == PriceListLineRepository.AMOUNT_TYPE_NONE
        || saleOrder == null
        || (saleOrder.getStatusSelect() != SaleOrderRepository.STATUS_DRAFT_QUOTATION
            && (saleOrder.getStatusSelect() != SaleOrderRepository.STATUS_ORDER_CONFIRMED
                || !saleOrder.getOrderBeingEdited()))) {
      return null;
    } else {
      return maxDiscount.get();
    }
  }

  @Override
  public boolean isSaleOrderLineDiscountGreaterThanMaxDiscount(
      SaleOrderLine saleOrderLine, BigDecimal maxDiscount) {
    return (saleOrderLine.getDiscountTypeSelect() == PriceListLineRepository.AMOUNT_TYPE_PERCENT
            && saleOrderLine.getDiscountAmount().compareTo(maxDiscount) > 0)
        || (saleOrderLine.getDiscountTypeSelect() == PriceListLineRepository.AMOUNT_TYPE_FIXED
            && saleOrderLine.getPrice().signum() != 0
            && saleOrderLine
                    .getDiscountAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(saleOrderLine.getPrice(), 2, RoundingMode.HALF_UP)
                    .compareTo(maxDiscount)
                > 0);
  }

  @Override
  public List<SaleOrderLine> createNonStandardSOLineFromPack(
      Pack pack,
      SaleOrder saleOrder,
      BigDecimal packQty,
      List<SaleOrderLine> saleOrderLineList,
      Integer sequence) {
    SaleOrderLine saleOrderLine;
    Set<Integer> packLineTypeSet = getPackLineTypes(pack.getComponents());
    int typeSelect = SaleOrderLineRepository.TYPE_START_OF_PACK;
    for (int i = 0; i < 2; i++) {
      if (packLineTypeSet == null || !packLineTypeSet.contains(typeSelect)) {
        saleOrderLine =
            this.createStartOfPackAndEndOfPackTypeSaleOrderLine(
                pack, saleOrder, packQty, null, typeSelect, sequence);
        saleOrderLineList.add(saleOrderLine);
      }
      if (typeSelect == SaleOrderLineRepository.TYPE_START_OF_PACK) {
        sequence += pack.getComponents().size() + 1;
        typeSelect = SaleOrderLineRepository.TYPE_END_OF_PACK;
      }
    }

    return saleOrderLineList;
  }

  @Override
  public SaleOrderLine createStartOfPackAndEndOfPackTypeSaleOrderLine(
      Pack pack,
      SaleOrder saleOrder,
      BigDecimal packqty,
      PackLine packLine,
      Integer typeSelect,
      Integer sequence) {

    SaleOrderLine saleOrderLine = new SaleOrderLine();
    saleOrderLine.setTypeSelect(typeSelect);
    switch (typeSelect) {
      case SaleOrderLineRepository.TYPE_START_OF_PACK:
        saleOrderLine.setProductName(packLine == null ? pack.getName() : packLine.getProductName());
        saleOrderLine.setQty(
            packLine != null && packLine.getQuantity() != null
                ? packLine
                    .getQuantity()
                    .multiply(packqty)
                    .setScale(appBaseService.getNbDecimalDigitForQty(), RoundingMode.HALF_EVEN)
                : packqty);
        break;

      case SaleOrderLineRepository.TYPE_END_OF_PACK:
        saleOrderLine.setProductName(
            packLine == null
                ? I18n.get(ITranslation.SALE_ORDER_LINE_END_OF_PACK)
                : packLine.getProductName());
        saleOrderLine.setIsShowTotal(pack.getIsShowTotal());
        saleOrderLine.setIsHideUnitAmounts(pack.getIsHideUnitAmounts());
        break;
      default:
        return null;
    }
    saleOrderLine.setSaleOrder(saleOrder);
    saleOrderLine.setSequence(sequence);
    return saleOrderLine;
  }

  @Override
  public boolean hasEndOfPackTypeLine(List<SaleOrderLine> saleOrderLineList) {
    return ObjectUtils.isEmpty(saleOrderLineList)
        ? Boolean.FALSE
        : saleOrderLineList.stream()
            .anyMatch(
                saleOrderLine ->
                    saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_END_OF_PACK);
  }

  @Override
  public SaleOrderLine updateProductQty(
      SaleOrderLine saleOrderLine, SaleOrder saleOrder, BigDecimal oldQty, BigDecimal newQty)
      throws AxelorException {
    if (saleOrderLine.getTypeSelect() != SaleOrderLineRepository.TYPE_NORMAL) {
      return saleOrderLine;
    }
    this.fillPriceFromPackLine(saleOrderLine, saleOrder);
    this.computeValues(saleOrder, saleOrderLine);
    return saleOrderLine;
  }

  @Override
  public boolean isStartOfPackTypeLineQtyChanged(List<SaleOrderLine> saleOrderLineList) {

    if (ObjectUtils.isEmpty(saleOrderLineList)) {
      return false;
    }
    for (SaleOrderLine saleOrderLine : saleOrderLineList) {
      if (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_START_OF_PACK
          && saleOrderLine.getId() != null) {
        SaleOrderLine oldSaleOrderLine = saleOrderLineRepo.find(saleOrderLine.getId());
        if (oldSaleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_START_OF_PACK
            && saleOrderLine.getQty().compareTo(oldSaleOrderLine.getQty()) != 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void fillPriceFromPackLine(SaleOrderLine saleOrderLine, SaleOrder saleOrder)
      throws AxelorException {
    this.fillTaxInformation(saleOrderLine, saleOrder);
    saleOrderLine.setCompanyCostPrice(this.getCompanyCostPrice(saleOrder, saleOrderLine));
    BigDecimal exTaxPrice;
    BigDecimal inTaxPrice;
    if (saleOrderLine.getProduct().getInAti()) {
      inTaxPrice =
          this.getInTaxUnitPriceFromPackLine(saleOrder, saleOrderLine, saleOrderLine.getTaxLine());
      inTaxPrice = fillDiscount(saleOrderLine, saleOrder, inTaxPrice);
      if (!saleOrderLine.getEnableFreezeFields()) {
        saleOrderLine.setPrice(convertUnitPrice(true, saleOrderLine.getTaxLine(), inTaxPrice));
        saleOrderLine.setInTaxPrice(inTaxPrice);
      }
    } else {
      exTaxPrice =
          this.getExTaxUnitPriceFromPackLine(saleOrder, saleOrderLine, saleOrderLine.getTaxLine());
      exTaxPrice = fillDiscount(saleOrderLine, saleOrder, exTaxPrice);
      if (!saleOrderLine.getEnableFreezeFields()) {
        saleOrderLine.setPrice(exTaxPrice);
        saleOrderLine.setInTaxPrice(
            convertUnitPrice(false, saleOrderLine.getTaxLine(), exTaxPrice));
      }
    }
  }

  @Override
  public BigDecimal getExTaxUnitPriceFromPackLine(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine) throws AxelorException {
    return this.getUnitPriceFromPackLine(saleOrder, saleOrderLine, taxLine, false);
  }

  @Override
  public BigDecimal getInTaxUnitPriceFromPackLine(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine) throws AxelorException {
    return this.getUnitPriceFromPackLine(saleOrder, saleOrderLine, taxLine, true);
  }

  /**
   * A method used to get the unit price of a sale order line from pack line, either in ati or wt
   *
   * @param saleOrder the sale order containing the sale order line
   * @param saleOrderLine
   * @param taxLine the tax applied to the unit price
   * @param resultInAti whether you want the result in ati or not
   * @return the unit price of the sale order line
   * @throws AxelorException
   */
  protected BigDecimal getUnitPriceFromPackLine(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine, boolean resultInAti)
      throws AxelorException {

    Product product = saleOrderLine.getProduct();

    Boolean productInAti =
        (Boolean) productCompanyService.get(product, "inAti", saleOrder.getCompany());
    BigDecimal productSalePrice = saleOrderLine.getPrice();

    BigDecimal price =
        (productInAti == resultInAti)
            ? productSalePrice
            : this.convertUnitPrice(productInAti, taxLine, productSalePrice);

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            (Currency) productCompanyService.get(product, "saleCurrency", saleOrder.getCompany()),
            saleOrder.getCurrency(),
            price,
            saleOrder.getCreationDate())
        .setScale(appSaleService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
  }

  @Override
  public Set<Integer> getPackLineTypes(List<PackLine> packLineList) {
    Set<Integer> packLineTypeSet = new HashSet<>();
    packLineList.stream()
        .forEach(
            packLine -> {
              if (packLine.getTypeSelect() == PackLineRepository.TYPE_START_OF_PACK) {
                packLineTypeSet.add(PackLineRepository.TYPE_START_OF_PACK);
              } else if (packLine.getTypeSelect() == PackLineRepository.TYPE_END_OF_PACK) {
                packLineTypeSet.add(PackLineRepository.TYPE_END_OF_PACK);
              }
            });
    return packLineTypeSet;
  }

  @Override
  public String computeProductDomain(SaleOrderLine saleOrderLine, SaleOrder saleOrder) {
    String domain =
        "self.isModel = false"
            + " and (self.endDate = null or self.endDate > :__date__)"
            + " and self.dtype = 'Product'";

    if (appBaseService.getAppBase().getCompanySpecificProductFieldsSet() != null
        && appBaseService.getAppBase().getCompanySpecificProductFieldsSet().stream()
            .anyMatch(it -> "sellable".equals(it.getName()))
        && saleOrder != null
        && saleOrder.getCompany() != null) {
      domain +=
          " and (SELECT sellable "
              + "FROM ProductCompany productCompany "
              + "WHERE productCompany.product.id = self.id "
              + "AND productCompany.company.id = "
              + saleOrder.getCompany().getId()
              + ") IS TRUE ";
    } else {
      domain += " and self.sellable = true ";
    }

    if (appSaleService.getAppSale().getEnableSalesProductByTradName()
        && saleOrder != null
        && saleOrder.getTradingName() != null
        && saleOrder.getCompany() != null
        && saleOrder.getCompany().getTradingNameSet() != null
        && !saleOrder.getCompany().getTradingNameSet().isEmpty()) {
      domain +=
          " AND " + saleOrder.getTradingName().getId() + " member of self.tradingNameSellerSet";
    }

    // The standard way to do this would be to override the method in HR module.
    // But here, we have to do this because overriding a sale service in hr module will prevent the
    // override in supplychain, business-project, and business production module.
    if (ModuleManager.isInstalled("axelor-human-resource")) {
      domain += " AND self.expense = false ";
    }

    return domain;
  }

  @Override
  public List<SaleOrderLine> manageComplementaryProductSaleOrderLine(
      ComplementaryProduct complementaryProduct, SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException, ClassNotFoundException {

    List<SaleOrderLine> newComplementarySOLines = new ArrayList<>();
    if (saleOrderLine.getMainSaleOrderLine() != null) {
      return newComplementarySOLines;
    }

    if (saleOrderLine.getComplementarySaleOrderLineList() == null) {
      saleOrderLine.setComplementarySaleOrderLineList(new ArrayList<>());
    }

    SaleOrderLine complementarySOLine =
        getOrCreateComplementryLine(
            complementaryProduct.getProduct(), saleOrderLine, newComplementarySOLines);

    complementarySOLine.setQty(complementaryProduct.getQty());
    complementarySOLine.setIsComplementaryPartnerProductsHandled(
        complementaryProduct.getGenerationTypeSelect()
            == ComplementaryProductRepository.GENERATION_TYPE_SALE_ORDER);
    this.computeProductInformation(complementarySOLine, saleOrder);
    this.computeValues(saleOrder, complementarySOLine);
    saleOrderLineRepo.save(complementarySOLine);
    return newComplementarySOLines;
  }

  protected SaleOrderLine getOrCreateComplementryLine(
      Product product, SaleOrderLine saleOrderLine, List<SaleOrderLine> newComplementarySOLines) {
    SaleOrderLine complementarySOLine;
    Optional<SaleOrderLine> complementarySOLineOpt =
        saleOrderLine.getComplementarySaleOrderLineList().stream()
            .filter(
                line -> line.getMainSaleOrderLine() != null && line.getProduct().equals(product))
            .findFirst();
    if (complementarySOLineOpt.isPresent()) {
      complementarySOLine = complementarySOLineOpt.get();
    } else {
      complementarySOLine = new SaleOrderLine();
      complementarySOLine.setSequence(saleOrderLine.getSequence());
      complementarySOLine.setProduct(product);
      saleOrderLine.addComplementarySaleOrderLineListItem(complementarySOLine);
      newComplementarySOLines.add(complementarySOLine);
    }
    return complementarySOLine;
  }

  @Override
  public void computePricingScale(SaleOrder saleOrder, SaleOrderLine orderLine)
      throws ClassNotFoundException {

    if (orderLine.getProduct() == null) {
      return;
    }

    // (1) Get the Pricing

    Pricing pricing = getRelatedPricing(orderLine.getProduct(), saleOrder);

    if (pricing == null
        || pricing.getClass1PricingRule() == null
        || pricing.getResult1PricingRule() == null) {
      return;
    }

    // (2) Compute the classification formulas
    // (3) Search the Pricing Line

    Context scriptContext =
        new Context(
            Mapper.toMap(orderLine), Class.forName(pricing.getConcernedModel().getFullName()));

    scriptContext.put("saleOrder", EntityHelper.getEntity(saleOrder));
    GroovyScriptHelper scriptHelper = new GroovyScriptHelper(scriptContext);

    PricingLine pricingLine =
        searchPricingLine(
            pricing,
            computeClassificationFormula(scriptHelper, pricing.getClass1PricingRule()),
            computeClassificationFormula(scriptHelper, pricing.getClass2PricingRule()),
            computeClassificationFormula(scriptHelper, pricing.getClass3PricingRule()),
            computeClassificationFormula(scriptHelper, pricing.getClass4PricingRule()));

    if (pricingLine == null) {
      return;
    }

    // (4) Compute the result formulas
    // (6) Apply the results

    scriptContext.put("pricingLine", EntityHelper.getEntity(pricingLine));
    scriptHelper = new GroovyScriptHelper(scriptContext);

    computeResultFormulaAndApply(scriptHelper, pricing, orderLine);

    return;
  }

  public Pricing getRelatedPricing(Product product, SaleOrder saleOrder) {

    StringBuilder filter = new StringBuilder();

    filter.append("(self.product = :product");

    if (product.getParentProduct() != null) {
      filter.append(" OR self.product = :parentProduct");
    }

    if (product.getProductCategory() != null) {
      filter.append(" OR self.productCategory = :productCategory");
    }

    filter.append(
        ") AND self.previousPricing = null AND self.company = :company AND self.startDate <= :todayDate AND self.concernedModel.name = :modelName");

    return pricingRepository
        .all()
        .filter(filter.toString())
        .bind("product", product)
        .bind("productCategory", product.getProductCategory())
        .bind("parentProduct", product.getParentProduct())
        .bind("company", saleOrder.getCompany())
        .bind("todayDate", appBaseService.getTodayDate(saleOrder.getCompany()))
        .bind("modelName", SaleOrderLine.class.getSimpleName())
        .fetchOne();
  }

  public String computeClassificationFormula(
      GroovyScriptHelper scriptHelper, PricingRule classPricingRule) {

    return classPricingRule != null
        ? (String) scriptHelper.eval(classPricingRule.getFormula())
        : null;
  }

  public PricingLine searchPricingLine(
      Pricing pricing,
      String class1PricingRuleValue,
      String class2PricingRuleValue,
      String class3PricingRuleValue,
      String class4PricingRuleValue) {

    Stream<PricingLine> pricingLineStream =
        Optional.ofNullable(pricing.getPricingLineList()).orElse(Collections.emptyList()).stream();

    if (class4PricingRuleValue != null) {
      pricingLineStream =
          pricingLineStream.filter(
              pricingLine ->
                  pricingLine.getClassificationParam1().equals(class1PricingRuleValue)
                      && pricingLine.getClassificationParam2().equals(class2PricingRuleValue)
                      && pricingLine.getClassificationParam3().equals(class3PricingRuleValue)
                      && pricingLine.getClassificationParam4().equals(class4PricingRuleValue));
    } else if (class3PricingRuleValue != null) {
      pricingLineStream =
          pricingLineStream.filter(
              pricingLine ->
                  pricingLine.getClassificationParam1().equals(class1PricingRuleValue)
                      && pricingLine.getClassificationParam2().equals(class2PricingRuleValue)
                      && pricingLine.getClassificationParam3().equals(class3PricingRuleValue));
    } else if (class2PricingRuleValue != null) {
      pricingLineStream =
          pricingLineStream.filter(
              pricingLine ->
                  pricingLine.getClassificationParam1().equals(class1PricingRuleValue)
                      && pricingLine.getClassificationParam2().equals(class2PricingRuleValue));
    } else {
      pricingLineStream =
          pricingLineStream.filter(
              pricingLine -> pricingLine.getClassificationParam1().equals(class1PricingRuleValue));
    }

    Optional<PricingLine> pricingLineValue = pricingLineStream.findFirst();

    return pricingLineValue.isPresent() ? pricingLineValue.get() : null;
  }

  public void computeResultFormulaAndApply(
      GroovyScriptHelper scriptHelper, Pricing pricing, SaleOrderLine orderLine) {

    List<PricingRule> resultPricingRuleList = new ArrayList<>();
    resultPricingRuleList.add(pricing.getResult1PricingRule());
    resultPricingRuleList.add(pricing.getResult2PricingRule());
    resultPricingRuleList.add(pricing.getResult3PricingRule());
    resultPricingRuleList.add(pricing.getResult4PricingRule());

    resultPricingRuleList.stream()
        .filter(Objects::nonNull)
        .forEach(
            resultPricingRule ->
                Mapper.of(SaleOrderLine.class)
                    .set(
                        orderLine,
                        resultPricingRule.getFieldToPopulate().getName(),
                        scriptHelper.eval(resultPricingRule.getFormula())));
  }
}
