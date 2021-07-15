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
package com.axelor.apps.purchase.service;

import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.TaxEquiv;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.ProductMultipleQtyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.base.service.tax.FiscalPositionService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.SupplierCatalog;
import com.axelor.apps.purchase.exception.IExceptionMessage;
import com.axelor.apps.purchase.service.app.AppPurchaseService;
import com.axelor.apps.tool.ContextTool;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurchaseOrderLineServiceImpl implements PurchaseOrderLineService {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject protected CurrencyService currencyService;

  @Inject protected AccountManagementService accountManagementService;

  @Inject protected PriceListService priceListService;

  @Inject protected AppBaseService appBaseService;

  @Inject protected PurchaseProductService productService;

  @Inject protected ProductMultipleQtyService productMultipleQtyService;

  @Inject protected AppPurchaseService appPurchaseService;

  @Inject protected SupplierCatalogService supplierCatalogService;

  @Inject protected ProductCompanyService productCompanyService;

  @Deprecated private int sequence = 0;

  @Override
  public Map<String, BigDecimal> compute(
      PurchaseOrderLine purchaseOrderLine, PurchaseOrder purchaseOrder) throws AxelorException {

    HashMap<String, BigDecimal> map = new HashMap<>();
    if (purchaseOrder == null
        || purchaseOrderLine.getPrice() == null
        || purchaseOrderLine.getInTaxPrice() == null
        || purchaseOrderLine.getQty() == null) {
      return map;
    }

    BigDecimal exTaxTotal;
    BigDecimal companyExTaxTotal;
    BigDecimal inTaxTotal;
    BigDecimal companyInTaxTotal;
    BigDecimal priceDiscounted = this.computeDiscount(purchaseOrderLine, purchaseOrder.getInAti());
    BigDecimal taxRate = BigDecimal.ZERO;

    if (purchaseOrderLine.getTaxLine() != null) {
      taxRate = purchaseOrderLine.getTaxLine().getValue();
    }

    if (!purchaseOrder.getInAti()) {
      exTaxTotal = computeAmount(purchaseOrderLine.getQty(), priceDiscounted);
      inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
      companyExTaxTotal = getCompanyExTaxTotal(exTaxTotal, purchaseOrder);
      companyInTaxTotal = companyExTaxTotal.add(companyExTaxTotal.multiply(taxRate));
    } else {
      inTaxTotal = computeAmount(purchaseOrderLine.getQty(), priceDiscounted);
      exTaxTotal = inTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
      companyInTaxTotal = getCompanyExTaxTotal(inTaxTotal, purchaseOrder);
      companyExTaxTotal =
          companyInTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
    }

    if (purchaseOrderLine.getProduct() != null) {
      map.put("maxPurchasePrice", getPurchaseMaxPrice(purchaseOrder, purchaseOrderLine));
    }
    map.put("exTaxTotal", exTaxTotal);
    map.put("inTaxTotal", inTaxTotal);
    map.put("companyExTaxTotal", companyExTaxTotal);
    map.put("companyInTaxTotal", companyInTaxTotal);
    map.put("priceDiscounted", priceDiscounted);
    purchaseOrderLine.setExTaxTotal(exTaxTotal);
    purchaseOrderLine.setInTaxTotal(inTaxTotal);
    purchaseOrderLine.setPriceDiscounted(priceDiscounted);
    purchaseOrderLine.setCompanyExTaxTotal(companyExTaxTotal);
    purchaseOrderLine.setCompanyInTaxTotal(companyInTaxTotal);
    purchaseOrderLine.setMaxPurchasePrice(getPurchaseMaxPrice(purchaseOrder, purchaseOrderLine));
    return map;
  }

  /**
   * Calculer le montant HT d'une ligne de commande.
   *
   * @param quantity Quantité.
   * @param price Le prix.
   * @return Le montant HT de la ligne.
   */
  public static BigDecimal computeAmount(BigDecimal quantity, BigDecimal price) {

    BigDecimal amount =
        quantity
            .multiply(price)
            .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);

    LOG.debug(
        "Calcul du montant HT avec une quantité de {} pour {} : {}",
        new Object[] {quantity, price, amount});

    return amount;
  }

  public String[] getProductSupplierInfos(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine) throws AxelorException {

    Product product = purchaseOrderLine.getProduct();
    String productName = "";
    String productCode = "";

    if (product == null) {
      return new String[] {productName, productCode};
    }

    SupplierCatalog supplierCatalog =
        supplierCatalogService.getSupplierCatalog(
            product, purchaseOrder.getSupplierPartner(), purchaseOrder.getCompany());

    if (supplierCatalog != null) {
      productName = supplierCatalog.getProductSupplierName();
      productCode = supplierCatalog.getProductSupplierCode();
    }

    return new String[] {
      Strings.isNullOrEmpty(productName) ? product.getName() : productName,
      Strings.isNullOrEmpty(productCode) ? product.getCode() : productCode
    };
  }

  /**
   * Returns the ex. tax unit price of the purchase order line or null if the product is not
   * available for purchase at the supplier of the purchase order
   */
  @Override
  public BigDecimal getExTaxUnitPrice(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine, TaxLine taxLine)
      throws AxelorException {
    return this.getUnitPrice(purchaseOrder, purchaseOrderLine, taxLine, false);
  }

  /**
   * Returns the incl. tax unit price of the purchase order line or null if the product is not
   * available for purchase at the supplier of the purchase order
   */
  @Override
  public BigDecimal getInTaxUnitPrice(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine, TaxLine taxLine)
      throws AxelorException {
    return this.getUnitPrice(purchaseOrder, purchaseOrderLine, taxLine, true);
  }

  /**
   * A function used to get the unit price of a purchase order line, either in ati or wt
   *
   * @param purchaseOrder the purchase order containing the purchase order line
   * @param purchaseOrderLine
   * @param taxLine the tax applied to the unit price
   * @param resultInAti whether or not you want the result to be in ati
   * @return the unit price of the purchase order line or null if the product is not available for
   *     purchase at the supplier of the purchase order
   * @throws AxelorException
   */
  private BigDecimal getUnitPrice(
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      TaxLine taxLine,
      boolean resultInAti)
      throws AxelorException {
    BigDecimal purchasePrice = new BigDecimal(0);
    Currency purchaseCurrency = null;
    Product product = purchaseOrderLine.getProduct();
    SupplierCatalog supplierCatalog =
        supplierCatalogService.getSupplierCatalog(
            product, purchaseOrder.getSupplierPartner(), purchaseOrder.getCompany());

    if (supplierCatalog != null) {
      purchasePrice = supplierCatalog.getPrice();
      purchaseCurrency = supplierCatalog.getSupplierPartner().getCurrency();
    } else {
      if (product != null) {
        purchasePrice =
            (BigDecimal)
                productCompanyService.get(product, "purchasePrice", purchaseOrder.getCompany());
        purchaseCurrency =
            (Currency)
                productCompanyService.get(product, "purchaseCurrency", purchaseOrder.getCompany());
      }
    }

    Boolean inAti =
        (Boolean) productCompanyService.get(product, "inAti", purchaseOrder.getCompany());
    BigDecimal price =
        (inAti == resultInAti)
            ? purchasePrice
            : this.convertUnitPrice(inAti, taxLine, purchasePrice);

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            purchaseCurrency, purchaseOrder.getCurrency(), price, purchaseOrder.getOrderDate())
        .setScale(appBaseService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
  }

  public PurchaseOrderLine fill(PurchaseOrderLine line, PurchaseOrder purchaseOrder)
      throws AxelorException {
    Preconditions.checkNotNull(line, I18n.get("The line cannot be null."));
    Preconditions.checkNotNull(
        purchaseOrder, I18n.get("You need a purchase order associated to line."));
    Partner supplierPartner = purchaseOrder.getSupplierPartner();
    Product product = line.getProduct();

    String[] productSupplierInfos = getProductSupplierInfos(purchaseOrder, line);
    if (!line.getEnableFreezeFields()) {
      line.setProductName(productSupplierInfos[0]);
      line.setQty(getQty(purchaseOrder, line));
    }
    line.setProductCode(productSupplierInfos[1]);
    line.setUnit(getPurchaseUnit(line));

    if (appPurchaseService.getAppPurchase().getIsEnabledProductDescriptionCopy()) {
      line.setDescription(product.getDescription());
    }

    TaxLine taxLine = getTaxLine(purchaseOrder, line);
    line.setTaxLine(taxLine);

    BigDecimal price = getExTaxUnitPrice(purchaseOrder, line, taxLine);
    BigDecimal inTaxPrice = getInTaxUnitPrice(purchaseOrder, line, taxLine);

    if (price == null || inTaxPrice == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.PURCHASE_ORDER_LINE_NO_SUPPLIER_CATALOG));
    }

    Tax tax =
        accountManagementService.getProductTax(
            product, purchaseOrder.getCompany(), supplierPartner.getFiscalPosition(), true);

    TaxEquiv taxEquiv =
        Beans.get(FiscalPositionService.class)
            .getTaxEquiv(supplierPartner.getFiscalPosition(), tax);
    line.setTaxEquiv(taxEquiv);

    Map<String, Object> discounts =
        getDiscountsFromPriceLists(
            purchaseOrder, line, purchaseOrder.getInAti() ? inTaxPrice : price);

    if (discounts != null) {
      if (discounts.get("price") != null) {
        BigDecimal discountPrice = (BigDecimal) discounts.get("price");
        if (product.getInAti()) {
          inTaxPrice = discountPrice;
          price = this.convertUnitPrice(true, line.getTaxLine(), discountPrice);
        } else {
          price = discountPrice;
          inTaxPrice = this.convertUnitPrice(false, line.getTaxLine(), discountPrice);
        }
      }
      if (product.getInAti() != purchaseOrder.getInAti()
          && (Integer) discounts.get("discountTypeSelect")
              != PriceListLineRepository.AMOUNT_TYPE_PERCENT) {
        line.setDiscountAmount(
            this.convertUnitPrice(
                product.getInAti(),
                line.getTaxLine(),
                (BigDecimal) discounts.get("discountAmount")));
      } else {
        line.setDiscountAmount((BigDecimal) discounts.get("discountAmount"));
      }
      line.setDiscountTypeSelect((Integer) discounts.get("discountTypeSelect"));
    }
    if (!line.getEnableFreezeFields()) {
      line.setPrice(price);
    }
    line.setInTaxPrice(inTaxPrice);

    line.setMaxPurchasePrice(getPurchaseMaxPrice(purchaseOrder, line));
    return line;
  }

  @Override
  public PurchaseOrderLine reset(PurchaseOrderLine line) {
    if (!line.getEnableFreezeFields()) {
      line.setQty(BigDecimal.ZERO);
      line.setPrice(null);
      line.setProductName(null);
    }
    line.setTaxLine(null);
    line.setUnit(null);
    line.setDiscountAmount(null);
    line.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
    line.setInTaxPrice(null);
    line.setMaxPurchasePrice(null);
    line.setExTaxTotal(null);
    line.setInTaxTotal(null);
    line.setCompanyInTaxTotal(null);
    line.setCompanyExTaxTotal(null);
    line.setProductCode(null);
    if (appPurchaseService.getAppPurchase().getIsEnabledProductDescriptionCopy()) {
      line.setDescription(null);
    }
    return line;
  }

  @Override
  public BigDecimal getPurchaseMaxPrice(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine) throws AxelorException {

    try {

      Product product = purchaseOrderLine.getProduct();

      if (product == null
          || !((Boolean)
              productCompanyService.get(product, "sellable", purchaseOrder.getCompany()))) {
        return BigDecimal.ZERO;
      }

      TaxLine saleTaxLine =
          accountManagementService.getTaxLine(
              purchaseOrder.getOrderDate(),
              purchaseOrderLine.getProduct(),
              purchaseOrder.getCompany(),
              purchaseOrder.getSupplierPartner().getFiscalPosition(),
              false);

      BigDecimal price;
      if (purchaseOrder.getInAti()
          != (Boolean) productCompanyService.get(product, "inAti", purchaseOrder.getCompany())) {
        price =
            this.convertUnitPrice(
                (Boolean) productCompanyService.get(product, "inAti", purchaseOrder.getCompany()),
                saleTaxLine,
                ((BigDecimal)
                        productCompanyService.get(product, "salePrice", purchaseOrder.getCompany()))
                    .divide(
                        product.getManagPriceCoef().signum() == 0
                            ? BigDecimal.ONE
                            : product.getManagPriceCoef(),
                        appBaseService.getNbDecimalDigitForUnitPrice(),
                        RoundingMode.HALF_UP));
      } else {
        price =
            ((BigDecimal)
                    productCompanyService.get(product, "salePrice", purchaseOrder.getCompany()))
                .divide(
                    product.getManagPriceCoef().signum() == 0
                        ? BigDecimal.ONE
                        : product.getManagPriceCoef(),
                    appBaseService.getNbDecimalDigitForUnitPrice(),
                    RoundingMode.HALF_UP);
      }
      return currencyService
          .getAmountCurrencyConvertedAtDate(
              (Currency)
                  productCompanyService.get(product, "saleCurrency", purchaseOrder.getCompany()),
              purchaseOrder.getCurrency(),
              price,
              purchaseOrder.getOrderDate())
          .setScale(appBaseService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);

    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  @Override
  public TaxLine getTaxLine(PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine)
      throws AxelorException {

    return accountManagementService.getTaxLine(
        purchaseOrder.getOrderDate(),
        purchaseOrderLine.getProduct(),
        purchaseOrder.getCompany(),
        purchaseOrder.getSupplierPartner().getFiscalPosition(),
        true);
  }

  @Override
  public Optional<TaxLine> getOptionalTaxLine(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine) {
    try {
      return Optional.of(getTaxLine(purchaseOrder, purchaseOrderLine));
    } catch (AxelorException e) {
      return Optional.empty();
    }
  }

  @Override
  public BigDecimal computePurchaseOrderLine(PurchaseOrderLine purchaseOrderLine) {

    return purchaseOrderLine.getExTaxTotal();
  }

  @Override
  public BigDecimal getCompanyExTaxTotal(BigDecimal exTaxTotal, PurchaseOrder purchaseOrder)
      throws AxelorException {

    return currencyService
        .getAmountCurrencyConvertedAtDate(
            purchaseOrder.getCurrency(),
            purchaseOrder.getCompany().getCurrency(),
            exTaxTotal,
            purchaseOrder.getOrderDate())
        .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
  }

  @Override
  public PriceListLine getPriceListLine(
      PurchaseOrderLine purchaseOrderLine, PriceList priceList, BigDecimal price) {

    return priceListService.getPriceListLine(
        purchaseOrderLine.getProduct(), purchaseOrderLine.getQty(), priceList, price);
  }

  @Override
  public BigDecimal computeDiscount(PurchaseOrderLine purchaseOrderLine, Boolean inAti) {

    BigDecimal price = inAti ? purchaseOrderLine.getInTaxPrice() : purchaseOrderLine.getPrice();

    return priceListService.computeDiscount(
        price, purchaseOrderLine.getDiscountTypeSelect(), purchaseOrderLine.getDiscountAmount());
  }

  @Override
  public PurchaseOrderLine createPurchaseOrderLine(
      PurchaseOrder purchaseOrder,
      Product product,
      String productName,
      String description,
      BigDecimal qty,
      Unit unit)
      throws AxelorException {

    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    purchaseOrderLine.setPurchaseOrder(purchaseOrder);

    purchaseOrderLine.setEstimatedDelivDate(purchaseOrder.getDeliveryDate());

    if (product != null) {
      purchaseOrderLine.setProduct(product);
      fill(purchaseOrderLine, purchaseOrder);
      if (qty != null) {
        purchaseOrderLine.setQty(qty);
      }
      compute(purchaseOrderLine, purchaseOrder);
    }

    if (description != null) {
      purchaseOrderLine.setDescription(description);
    }

    purchaseOrderLine.setIsOrdered(false);

    purchaseOrderLine.setSequence(sequence);
    sequence++;

    if (unit != null) {
      purchaseOrderLine.setUnit(unit);
    }

    if (productName != null) {
      purchaseOrderLine.setProductName(productName);
    }

    return purchaseOrderLine;
  }

  @Override
  public BigDecimal getQty(PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine)
      throws AxelorException {

    SupplierCatalog supplierCatalog = this.getSupplierCatalog(purchaseOrder, purchaseOrderLine);

    if (supplierCatalog != null) {

      return supplierCatalog.getMinQty();
    }

    return BigDecimal.ONE;
  }

  @Override
  public SupplierCatalog getSupplierCatalog(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine) throws AxelorException {

    Product product = purchaseOrderLine.getProduct();

    SupplierCatalog supplierCatalog =
        supplierCatalogService.getSupplierCatalog(
            product, purchaseOrder.getSupplierPartner(), purchaseOrder.getCompany());

    //		If there is no catalog for supplier, then we don't take the default catalog.

    //		if(supplierCatalog == null)  {
    //
    //			supplierCatalog = this.getSupplierCatalog(product, product.getDefaultSupplierPartner());
    //		}

    return supplierCatalog;
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
  public Map<String, Object> updateInfoFromCatalog(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine) throws AxelorException {
    return supplierCatalogService.updateInfoFromCatalog(
        purchaseOrderLine.getProduct(),
        purchaseOrderLine.getQty(),
        purchaseOrder.getSupplierPartner(),
        purchaseOrder.getCurrency(),
        purchaseOrder.getOrderDate(),
        purchaseOrder.getCompany());
  }

  @Override
  public Map<String, Object> getDiscountsFromPriceLists(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine, BigDecimal price) {

    Map<String, Object> discounts = null;

    PriceList priceList = purchaseOrder.getPriceList();

    if (priceList != null) {
      PriceListLine priceListLine = this.getPriceListLine(purchaseOrderLine, priceList, price);
      discounts = priceListService.getReplacedPriceAndDiscounts(priceList, priceListLine, price);
    }

    return discounts;
  }

  @Override
  public int getDiscountTypeSelect(
      PurchaseOrderLine purchaseOrderLine, PurchaseOrder purchaseOrder, BigDecimal price) {
    PriceList priceList = purchaseOrder.getPriceList();
    if (priceList != null) {
      PriceListLine priceListLine = this.getPriceListLine(purchaseOrderLine, priceList, price);

      return priceListLine.getTypeSelect();
    }
    return 0;
  }

  @Override
  public Unit getPurchaseUnit(PurchaseOrderLine purchaseOrderLine) {
    Unit unit = purchaseOrderLine.getProduct().getPurchasesUnit();
    if (unit == null) {
      unit = purchaseOrderLine.getProduct().getUnit();
    }
    return unit;
  }

  @Override
  public BigDecimal getMinQty(PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine)
      throws AxelorException {
    SupplierCatalog supplierCatalog = getSupplierCatalog(purchaseOrder, purchaseOrderLine);
    return supplierCatalog != null ? supplierCatalog.getMinQty() : BigDecimal.ONE;
  }

  @Override
  public void checkMinQty(
      PurchaseOrder purchaseOrder,
      PurchaseOrderLine purchaseOrderLine,
      ActionRequest request,
      ActionResponse response)
      throws AxelorException {

    BigDecimal minQty = this.getMinQty(purchaseOrder, purchaseOrderLine);

    if (purchaseOrderLine.getQty().compareTo(minQty) < 0) {
      String msg = String.format(I18n.get(IExceptionMessage.PURCHASE_ORDER_LINE_MIN_QTY), minQty);

      if (request.getAction().endsWith("onchange")) {
        response.setFlash(msg);
      }

      String title = ContextTool.formatLabel(msg, ContextTool.SPAN_CLASS_WARNING, 75);

      response.setAttr("minQtyNotRespectedLabel", "title", title);
      response.setAttr("minQtyNotRespectedLabel", "hidden", false);

    } else {
      response.setAttr("minQtyNotRespectedLabel", "hidden", true);
    }
  }

  @Override
  public void checkMultipleQty(PurchaseOrderLine purchaseOrderLine, ActionResponse response) {

    Product product = purchaseOrderLine.getProduct();

    if (product == null) {
      return;
    }

    productMultipleQtyService.checkMultipleQty(
        purchaseOrderLine.getQty(),
        product.getPurchaseProductMultipleQtyList(),
        product.getAllowToForcePurchaseQty(),
        response);
  }

  @Override
  public void checkDifferentSupplier(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine, ActionResponse response) {
    if (!appBaseService.getAppBase().getEnableTradingNamesManagement()) {
      return;
    }

    Product product = purchaseOrderLine.getProduct();
    TradingName tradingName = purchaseOrder.getTradingName();

    if (product == null || tradingName == null) {
      return;
    }

    Partner supplierOnPurchaseOrder = purchaseOrder.getSupplierPartner();
    Partner defaultSupplierOnProduct = product.getDefaultSupplierPartner();
    if (defaultSupplierOnProduct == null) {
      return;
    }

    if (supplierOnPurchaseOrder != defaultSupplierOnProduct) {

      String message = String.format(I18n.get(IExceptionMessage.DIFFERENT_SUPPLIER));
      String title =
          String.format(
              "<span class='label %s'>%s</span>", ContextTool.SPAN_CLASS_WARNING, message);

      response.setAttr("differentSupplierLabel", "title", title);
      response.setAttr("differentSupplierLabel", "hidden", false);
    } else {
      response.setAttr("differentSupplierLabel", "hidden", true);
    }
  }
}
