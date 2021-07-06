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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.purchase.db.IPurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.SupplierCatalog;
import com.axelor.apps.purchase.db.repo.PurchaseOrderLineRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockRules;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockRulesRepository;
import com.axelor.apps.stock.service.StockRulesService;
import com.axelor.apps.supplychain.db.Mrp;
import com.axelor.apps.supplychain.db.MrpFamily;
import com.axelor.apps.supplychain.db.MrpForecast;
import com.axelor.apps.supplychain.db.MrpLine;
import com.axelor.apps.supplychain.db.MrpLineOrigin;
import com.axelor.apps.supplychain.db.MrpLineType;
import com.axelor.apps.supplychain.db.repo.MrpForecastRepository;
import com.axelor.apps.supplychain.db.repo.MrpLineRepository;
import com.axelor.apps.supplychain.db.repo.MrpLineTypeRepository;
import com.axelor.apps.supplychain.db.repo.MrpRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MrpServiceImpl implements MrpService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected MrpRepository mrpRepository;
  protected StockLocationRepository stockLocationRepository;
  protected ProductRepository productRepository;
  protected StockLocationLineRepository stockLocationLineRepository;
  protected MrpLineTypeRepository mrpLineTypeRepository;
  protected PurchaseOrderLineRepository purchaseOrderLineRepository;
  protected SaleOrderLineRepository saleOrderLineRepository;
  protected MrpLineRepository mrpLineRepository;
  protected StockRulesService stockRulesService;
  protected MrpLineService mrpLineService;
  protected MrpForecastRepository mrpForecastRepository;

  protected AppBaseService appBaseService;

  protected List<StockLocation> stockLocationList;
  protected Map<Long, Integer> productMap;
  protected Mrp mrp;

  @Inject
  public MrpServiceImpl(
      AppBaseService appBaseService,
      MrpRepository mrpRepository,
      StockLocationRepository stockLocationRepository,
      ProductRepository productRepository,
      StockLocationLineRepository stockLocationLineRepository,
      MrpLineTypeRepository mrpLineTypeRepository,
      PurchaseOrderLineRepository purchaseOrderLineRepository,
      SaleOrderLineRepository saleOrderLineRepository,
      MrpLineRepository mrpLineRepository,
      StockRulesService stockRulesService,
      MrpLineService mrpLineService,
      MrpForecastRepository mrpForecastRepository) {

    this.mrpRepository = mrpRepository;
    this.stockLocationRepository = stockLocationRepository;
    this.productRepository = productRepository;
    this.stockLocationLineRepository = stockLocationLineRepository;
    this.mrpLineTypeRepository = mrpLineTypeRepository;
    this.purchaseOrderLineRepository = purchaseOrderLineRepository;
    this.saleOrderLineRepository = saleOrderLineRepository;
    this.mrpLineRepository = mrpLineRepository;
    this.stockRulesService = stockRulesService;
    this.mrpLineService = mrpLineService;
    this.mrpForecastRepository = mrpForecastRepository;

    this.appBaseService = appBaseService;
  }

  @Override
  public void runCalculation(Mrp mrp) throws AxelorException {

    this.startMrp(mrpRepository.find(mrp.getId()));
    this.completeMrp(mrpRepository.find(mrp.getId()));
    this.doCalulation(mrpRepository.find(mrp.getId()));
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  protected void startMrp(Mrp mrp) {

    log.debug("Start MRP");

    mrp.setStatusSelect(MrpRepository.STATUS_CALCULATION_STARTED);

    // TODO check that the different types used for purchase/manufOrder proposal are in stock type
    // TODO check that all types exist + override the method on production module

    mrp.clearMrpLineList();

    mrpRepository.save(mrp);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void reset(Mrp mrp) {

    mrp.setStatusSelect(MrpRepository.STATUS_DRAFT);

    mrp.clearMrpLineList();

    mrpRepository.save(mrp);
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  protected void completeMrp(Mrp mrp) throws AxelorException {

    log.debug("Complete MRP");

    // Initialize
    this.mrp = mrp;
    this.stockLocationList = this.getAllLocationAndSubLocation(mrp.getStockLocation());
    this.assignProductAndLevel(this.getProductList());

    // Get the stock for each product on each stock location
    this.createAvailableStockMrpLines();

    this.createPurchaseMrpLines();

    this.createSaleOrderMrpLines();

    this.createSaleForecastMrpLines();

    mrpRepository.save(mrp);
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  protected void doCalulation(Mrp mrp) throws AxelorException {

    log.debug("Do calculation");

    mrpRepository.save(mrp);

    this.checkInsufficientCumulativeQty();

    //		this.consolidateMrp(mrp);

    mrp.setStatusSelect(MrpRepository.STATUS_CALCULATION_ENDED);
  }

  protected void checkInsufficientCumulativeQty() throws AxelorException {

    for (int level = 0; level <= this.getMaxLevel(); level++) {

      for (Product product : this.getProductList(level)) {

        this.checkInsufficientCumulativeQty(product);
      }
    }
  }

  /**
   * Get the list of product for a level
   *
   * @param level
   * @return
   */
  protected List<Product> getProductList(int level) {

    List<Product> productList = Lists.newArrayList();

    for (Long productId : this.productMap.keySet()) {

      if (this.productMap.get(productId) == level) {
        productList.add(productRepository.find(productId));
      }
    }

    return productList;
  }

  protected int getMaxLevel() {

    int maxLevel = 0;

    for (int level : this.productMap.values()) {

      if (level > maxLevel) {
        maxLevel = level;
      }
    }

    return maxLevel;
  }

  protected void checkInsufficientCumulativeQty(Product product) throws AxelorException {

    boolean doASecondPass = false;

    this.computeCumulativeQty(product);

    List<MrpLine> mrpLineList =
        mrpLineRepository
            .all()
            .filter("self.mrp = ?1 AND self.product = ?2", mrp, product)
            .order("maturityDate")
            .order("mrpLineType.typeSelect")
            .order("mrpLineType.sequence")
            .order("id")
            .fetch();

    for (MrpLine mrpLine : mrpLineList) {

      BigDecimal cumulativeQty = mrpLine.getCumulativeQty();

      MrpLineType mrpLineType = mrpLine.getMrpLineType();

      boolean isProposalElement = this.isProposalElement(mrpLineType);

      BigDecimal minQty = mrpLine.getMinQty();

      if (mrpLine.getMrpLineType().getElementSelect()
              != MrpLineTypeRepository.ELEMENT_AVAILABLE_STOCK
          && (!isProposalElement || mrpLineType.getTypeSelect() == MrpLineTypeRepository.TYPE_OUT)
          && cumulativeQty.compareTo(mrpLine.getMinQty()) < 0) {

        log.debug(
            "Cumulative qty ({} < {}) is insufficient for product ({}) at the maturity date ({})",
            cumulativeQty,
            minQty,
            product.getFullName(),
            mrpLine.getMaturityDate());

        BigDecimal reorderQty = minQty.subtract(cumulativeQty);

        StockRules stockRules =
            stockRulesService.getStockRules(
                product,
                mrpLine.getStockLocation(),
                StockRulesRepository.TYPE_FUTURE,
                StockRulesRepository.USE_CASE_USED_FOR_MRP);

        if (stockRules != null) {
          reorderQty = reorderQty.max(stockRules.getReOrderQty());
        }

        MrpLineType mrpLineTypeProposal = this.getMrpLineTypeForProposal(stockRules, product);

        this.createProposalMrpLine(
            product,
            mrpLineTypeProposal,
            reorderQty,
            mrpLine.getStockLocation(),
            mrpLine.getMaturityDate(),
            mrpLine.getMrpLineOriginList(),
            mrpLine.getRelatedToSelectName());

        doASecondPass = true;

        break;
      }
    }

    if (doASecondPass) {
      mrpRepository.save(mrp);

      this.checkInsufficientCumulativeQty(product);
    }
  }

  public MrpLine getPreviousProposalMrpLine(
      Product product,
      MrpLineType mrpLineType,
      StockLocation stockLocation,
      LocalDate maturityDate) {

    LocalDate startPeriodDate = maturityDate;

    MrpFamily mrpFamily = product.getMrpFamily();

    if (mrpFamily != null) {

      if (mrpFamily.getDayNb() == 0) {
        return null;
      }

      startPeriodDate = maturityDate.minusDays(mrpFamily.getDayNb());
    }

    return mrpLineRepository
        .all()
        .filter(
            "self.mrp = ?1 AND self.product = ?2 AND self.mrpLineType = ?3 AND self.stockLocation = ?4 AND self.maturityDate > ?5 AND self.maturityDate <= ?6",
            mrp,
            product,
            mrpLineType,
            stockLocation,
            startPeriodDate,
            maturityDate)
        .fetchOne();
  }

  protected void createProposalMrpLine(
      Product product,
      MrpLineType mrpLineType,
      BigDecimal reorderQty,
      StockLocation stockLocation,
      LocalDate maturityDate,
      List<MrpLineOrigin> mrpLineOriginList,
      String relatedToSelectName)
      throws AxelorException {

    if (mrpLineType.getElementSelect() == MrpLineTypeRepository.ELEMENT_PURCHASE_PROPOSAL) {
      maturityDate = maturityDate.minusDays(product.getSupplierDeliveryTime());
      reorderQty = reorderQty.max(this.getSupplierCatalogMinQty(product));
    }

    MrpLine mrpLine =
        this.getPreviousProposalMrpLine(product, mrpLineType, stockLocation, maturityDate);

    if (mrpLine != null) {
      if (mrpLineType.getTypeSelect() == MrpLineTypeRepository.TYPE_OUT) {
        reorderQty = reorderQty.negate();
      }
      mrpLine.setQty(mrpLine.getQty().add(reorderQty));
      mrpLine.setRelatedToSelectName(null);

    } else {

      MrpLine createdMrpLine =
          this.createMrpLine(
              product, mrpLineType, reorderQty, maturityDate, BigDecimal.ZERO, stockLocation);
      if (createdMrpLine != null) {
        mrpLine = mrpLineRepository.save(createdMrpLine);
        mrp.addMrpLineListItem(mrpLine);
      }
      mrpLine.setRelatedToSelectName(relatedToSelectName);
    }

    this.copyMrpLineOrigins(mrpLine, mrpLineOriginList);
  }

  protected BigDecimal getSupplierCatalogMinQty(Product product) {

    Partner supplierPartner = product.getDefaultSupplierPartner();

    if (supplierPartner != null) {

      for (SupplierCatalog supplierCatalog : product.getSupplierCatalogList()) {

        if (supplierCatalog.getSupplierPartner().equals(supplierPartner)) {
          return supplierCatalog.getMinQty();
        }
      }
    }
    return BigDecimal.ZERO;
  }

  protected MrpLineType getMrpLineTypeForProposal(StockRules stockRules, Product product)
      throws AxelorException {

    return this.getMrpLineType(MrpLineTypeRepository.ELEMENT_PURCHASE_PROPOSAL);
  }

  protected void consolidateMrp() {

    List<MrpLine> mrpLineList =
        mrpLineRepository
            .all()
            .filter("self.mrp = ?1", mrp)
            .order("product.code")
            .order("maturityDate")
            .order("mrpLineType.typeSelect")
            .order("mrpLineType.sequence")
            .order("id")
            .fetch();

    Map<List<Object>, MrpLine> map = Maps.newHashMap();
    MrpLine consolidateMrpLine = null;
    List<Object> keys = new ArrayList<>();

    for (MrpLine mrpLine : mrpLineList) {

      MrpLineType mrpLineType = mrpLine.getMrpLineType();

      keys.clear();
      keys.add(mrpLineType);
      keys.add(mrpLine.getProduct());
      keys.add(mrpLine.getMaturityDate());
      keys.add(mrpLine.getStockLocation());

      if (map.containsKey(keys)) {

        consolidateMrpLine = map.get(keys);
        consolidateMrpLine.setQty(consolidateMrpLine.getQty().add(mrpLine.getQty()));
        consolidateMrpLine.setCumulativeQty(
            consolidateMrpLine.getCumulativeQty().add(mrpLine.getCumulativeQty()));

      } else {
        map.put(keys, mrpLine);
      }
    }

    mrp.getMrpLineList().clear();

    mrp.getMrpLineList().addAll(map.values());
  }

  protected boolean isProposalElement(MrpLineType mrpLineType) {

    if (mrpLineType.getElementSelect() == MrpLineTypeRepository.ELEMENT_PURCHASE_PROPOSAL) {

      return true;
    }

    return false;
  }

  protected void computeCumulativeQty() {

    for (Long productId : this.productMap.keySet()) {

      this.computeCumulativeQty(productRepository.find(productId));
    }
  }

  protected void computeCumulativeQty(Product product) {

    List<MrpLine> mrpLineList =
        mrpLineRepository
            .all()
            .filter("self.mrp = ?1 AND self.product = ?2", mrp, product)
            .order("maturityDate")
            .order("mrpLineType.typeSelect")
            .order("mrpLineType.sequence")
            .order("id")
            .fetch();

    BigDecimal previousCumulativeQty = BigDecimal.ZERO;

    for (MrpLine mrpLine : mrpLineList) {

      if (mrpLine.getMrpLineType().getElementSelect()
          == MrpLineTypeRepository.ELEMENT_AVAILABLE_STOCK) {

        mrpLine.setCumulativeQty(mrpLine.getQty());
      } else {

        mrpLine.setCumulativeQty(previousCumulativeQty.add(mrpLine.getQty()));
      }

      previousCumulativeQty = mrpLine.getCumulativeQty();

      log.debug(
          "Cumulative qty is ({}) for product ({}) and move ({}) at the maturity date ({})",
          previousCumulativeQty,
          mrpLine.getProduct().getFullName(),
          mrpLine.getMrpLineType().getName(),
          mrpLine.getMaturityDate());
    }
  }

  protected void createPurchaseMrpLines() throws AxelorException {

    MrpLineType purchaseProposalMrpLineType =
        this.getMrpLineType(MrpLineTypeRepository.ELEMENT_PURCHASE_ORDER);
    String statusSelect = purchaseProposalMrpLineType.getStatusSelect();
    List<Integer> statusList = StringTool.getIntegerList(statusSelect);

    if (statusList.isEmpty()) {
      statusList.add(IPurchaseOrder.STATUS_VALIDATED);
    }

    // TODO : Manage the case where order is partially delivered
    List<PurchaseOrderLine> purchaseOrderLineList =
        purchaseOrderLineRepository
            .all()
            .filter(
                "self.product.id in (?1) AND self.purchaseOrder.stockLocation in (?2) AND self.purchaseOrder.receiptState = ?3 "
                    + "AND self.purchaseOrder.statusSelect IN (?4)",
                this.productMap.keySet(),
                this.stockLocationList,
                IPurchaseOrder.STATE_NOT_RECEIVED,
                statusList)
            .fetch();

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLineList) {

      PurchaseOrder purchaseOrder = purchaseOrderLine.getPurchaseOrder();

      LocalDate maturityDate = purchaseOrderLine.getEstimatedDelivDate();

      if (maturityDate == null) {
        maturityDate = purchaseOrder.getDeliveryDate();
      }
      if (maturityDate == null) {
        maturityDate = purchaseOrder.getOrderDate();
      }

      if (this.isBeforeEndDate(maturityDate)) {
        Unit unit = purchaseOrderLine.getProduct().getUnit();
        BigDecimal qty = purchaseOrderLine.getQty();
        if (!unit.equals(purchaseOrderLine.getUnit())) {
          qty =
              Beans.get(UnitConversionService.class)
                  .convert(
                      purchaseOrderLine.getUnit(),
                      unit,
                      qty,
                      qty.scale(),
                      purchaseOrderLine.getProduct());
        }
        MrpLine mrpLine =
            this.createMrpLine(
                purchaseOrderLine.getProduct(),
                purchaseProposalMrpLineType,
                qty,
                maturityDate,
                BigDecimal.ZERO,
                purchaseOrder.getStockLocation(),
                purchaseOrderLine);
        if (mrpLine != null) {
          mrp.addMrpLineListItem(mrpLine);
        }
      }
    }
  }

  protected void createSaleOrderMrpLines() throws AxelorException {

    MrpLineType saleForecastMrpLineType =
        this.getMrpLineType(MrpLineTypeRepository.ELEMENT_SALE_ORDER);
    String statusSelect = saleForecastMrpLineType.getStatusSelect();
    List<Integer> statusList = StringTool.getIntegerList(statusSelect);

    if (statusList.isEmpty()) {
      statusList.add(SaleOrderRepository.STATUS_ORDER_CONFIRMED);
    }

    List<SaleOrderLine> saleOrderLineList = new ArrayList<>();

    if (mrp.getSaleOrderLineSet().isEmpty()) {

      saleOrderLineList.addAll(
          saleOrderLineRepository
              .all()
              .filter(
                  "self.product.id in (?1) AND self.saleOrder.stockLocation in (?2) AND self.deliveryState != ?3 "
                      + "AND self.saleOrder.statusSelect IN (?4)",
                  this.productMap.keySet(),
                  this.stockLocationList,
                  SaleOrderLineRepository.DELIVERY_STATE_DELIVERED,
                  statusList)
              .fetch());

    } else {

      saleOrderLineList.addAll(mrp.getSaleOrderLineSet());
    }

    for (SaleOrderLine saleOrderLine : saleOrderLineList) {

      SaleOrder saleOrder = saleOrderLine.getSaleOrder();

      LocalDate maturityDate = saleOrderLine.getEstimatedDelivDate();

      if (maturityDate == null) {
        maturityDate = saleOrder.getDeliveryDate();
      }
      if (maturityDate == null) {
        maturityDate = saleOrder.getOrderDate();
      }
      if (maturityDate == null) {
        maturityDate = saleOrder.getCreationDate();
      }

      if (this.isBeforeEndDate(maturityDate)) {
        Unit unit = saleOrderLine.getProduct().getUnit();
        BigDecimal qty = saleOrderLine.getQty().subtract(saleOrderLine.getDeliveredQty());
        if (!unit.equals(saleOrderLine.getUnit())) {
          qty =
              Beans.get(UnitConversionService.class)
                  .convert(
                      saleOrderLine.getUnit(),
                      unit,
                      qty,
                      saleOrderLine.getQty().scale(),
                      saleOrderLine.getProduct());
        }
        MrpLine mrpLine =
            this.createMrpLine(
                saleOrderLine.getProduct(),
                saleForecastMrpLineType,
                qty,
                maturityDate,
                BigDecimal.ZERO,
                saleOrder.getStockLocation(),
                saleOrderLine);
        if (mrpLine != null) {
          mrp.addMrpLineListItem(mrpLine);
        }
      }
    }
  }

  protected void createSaleForecastMrpLines() throws AxelorException {

    MrpLineType saleForecastMrpLineType =
        this.getMrpLineType(MrpLineTypeRepository.ELEMENT_SALE_FORECAST);

    List<MrpForecast> mrpForecastList = new ArrayList<>();

    if (mrp.getMrpForecastSet().isEmpty()) {

      LocalDate today = appBaseService.getTodayDate();
      mrpForecastList.addAll(
          mrpForecastRepository
              .all()
              .filter(
                  "self.product.id in (?1) AND self.stockLocation in (?2) AND self.forecastDate >= ?3",
                  this.productMap.keySet(),
                  this.stockLocationList,
                  today,
                  today)
              .fetch());

    } else {
      mrpForecastList.addAll(mrp.getMrpForecastSet());
    }

    for (MrpForecast mrpForecast : mrpForecastList) {

      LocalDate maturityDate = mrpForecast.getForecastDate();

      if (this.isBeforeEndDate(maturityDate)) {
        Unit unit = mrpForecast.getProduct().getUnit();
        BigDecimal qty = mrpForecast.getQty();
        if (!unit.equals(mrpForecast.getUnit())) {
          qty =
              Beans.get(UnitConversionService.class)
                  .convert(mrpForecast.getUnit(), unit, qty, qty.scale(), mrpForecast.getProduct());
        }
        MrpLine mrpLine =
            this.createMrpLine(
                mrpForecast.getProduct(),
                saleForecastMrpLineType,
                qty,
                maturityDate,
                BigDecimal.ZERO,
                mrpForecast.getStockLocation(),
                mrpForecast);
        if (mrpLine != null) {
          mrp.addMrpLineListItem(mrpLine);
        }
      }
    }
  }

  public boolean isBeforeEndDate(LocalDate maturityDate) {

    if (maturityDate != null
        && !maturityDate.isBefore(appBaseService.getTodayDate())
        && (mrp.getEndDate() == null || !maturityDate.isAfter(mrp.getEndDate()))) {

      return true;
    }

    return false;
  }

  protected void createAvailableStockMrpLines() throws AxelorException {

    MrpLineType availableStockMrpLineType =
        this.getMrpLineType(MrpLineTypeRepository.ELEMENT_AVAILABLE_STOCK);

    for (Long productId : this.productMap.keySet()) {

      for (StockLocation stockLocation : this.stockLocationList) {

        mrp.addMrpLineListItem(
            this.createAvailableStockMrpLine(
                productRepository.find(productId), stockLocation, availableStockMrpLineType));
      }
    }
  }

  protected MrpLine createAvailableStockMrpLine(
      Product product, StockLocation stockLocation, MrpLineType availableStockMrpLineType) {

    BigDecimal qty = BigDecimal.ZERO;

    StockLocationLine stockLocationLine = this.getStockLocationLine(product, stockLocation);

    if (stockLocationLine != null) {

      qty = stockLocationLine.getCurrentQty();
    }

    return this.createMrpLine(
        product, availableStockMrpLineType, qty, appBaseService.getTodayDate(), qty, stockLocation);
  }

  protected MrpLineType getMrpLineType(int elementSelect) throws AxelorException {

    MrpLineType mrpLineType =
        mrpLineTypeRepository.all().filter("self.elementSelect = ?1", elementSelect).fetchOne();

    if (mrpLineType != null) {
      return mrpLineType;
    }

    throw new AxelorException(
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        I18n.get(IExceptionMessage.MRP_MISSING_MRP_LINE_TYPE),
        elementSelect);

    // TODO get the right label in fact of integer value

  }

  protected StockLocationLine getStockLocationLine(Product product, StockLocation stockLocation) {

    return stockLocationLineRepository
        .all()
        .filter("self.stockLocation = ?1 AND self.product = ?2", stockLocation, product)
        .fetchOne();
  }

  protected Set<Product> getProductList() throws AxelorException {

    Set<Product> productSet = Sets.newHashSet();

    if (!mrp.getProductSet().isEmpty()) {

      productSet.addAll(mrp.getProductSet());
    }

    if (!mrp.getProductCategorySet().isEmpty()) {

      productSet.addAll(
          productRepository
              .all()
              .filter(
                  "self.productCategory in (?1) AND self.productTypeSelect = ?2 AND self.excludeFromMrp = false",
                  mrp.getProductCategorySet(),
                  ProductRepository.PRODUCT_TYPE_STORABLE)
              .fetch());
    }

    if (!mrp.getProductFamilySet().isEmpty()) {

      productSet.addAll(
          productRepository
              .all()
              .filter(
                  "self.productFamily in (?1) AND self.productTypeSelect = ?2 AND self.excludeFromMrp = false",
                  mrp.getProductFamilySet(),
                  ProductRepository.PRODUCT_TYPE_STORABLE)
              .fetch());
    }

    for (SaleOrderLine saleOrderLine : mrp.getSaleOrderLineSet()) {

      productSet.add(saleOrderLine.getProduct());
    }

    for (MrpForecast mrpForecast : mrp.getMrpForecastSet()) {

      productSet.add(mrpForecast.getProduct());
    }

    if (productSet.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.MRP_NO_PRODUCT));
    }

    return productSet;
  }

  public boolean isMrpProduct(Product product) {

    if (product != null
        && !product.getExcludeFromMrp()
        && product.getProductTypeSelect().equals(ProductRepository.PRODUCT_TYPE_STORABLE)) {

      return true;
    }

    return false;
  }

  protected void assignProductAndLevel(Set<Product> productList) {

    productMap = Maps.newHashMap();

    for (Product product : productList) {

      this.assignProductAndLevel(product);
    }
  }

  protected void assignProductAndLevel(Product product) {

    log.debug("Add of the product : {}", product.getFullName());
    this.productMap.put(product.getId(), 0);
  }

  protected MrpLine createMrpLine(
      Product product,
      MrpLineType mrpLineType,
      BigDecimal qty,
      LocalDate maturityDate,
      BigDecimal cumulativeQty,
      StockLocation stockLocation,
      Model... models) {
    productMap = null;
    if (product != null && productMap != null) {
      return mrpLineService.createMrpLine(
          product,
          this.productMap.get(product.getId()),
          mrpLineType,
          qty,
          maturityDate,
          cumulativeQty,
          stockLocation,
          models);
    }
    return null;
  }

  protected void copyMrpLineOrigins(MrpLine mrpLine, List<MrpLineOrigin> mrpLineOriginList) {

    if (mrpLineOriginList != null) {

      for (MrpLineOrigin mrpLineOrigin : mrpLineOriginList) {

        mrpLine.addMrpLineOriginListItem(mrpLineService.copyMrpLineOrigin(mrpLineOrigin));
      }
    }
  }

  protected List<StockLocation> getAllLocationAndSubLocation(StockLocation stockLocation) {

    List<StockLocation> subLocationList =
        stockLocationRepository
            .all()
            .filter(
                "self.parentStockLocation.id = :stockLocationId AND self.typeSelect != :virtual")
            .bind("stockLocationId", stockLocation.getId())
            .bind("virtual", StockLocationRepository.TYPE_VIRTUAL)
            .fetch();

    for (StockLocation subLocation : subLocationList) {

      subLocationList.addAll(this.getAllLocationAndSubLocation(subLocation));
    }

    subLocationList.add(stockLocation);

    return subLocationList;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void generateProposals(Mrp mrp) throws AxelorException {

    Map<Pair<Partner, LocalDate>, PurchaseOrder> purchaseOrders = new HashMap<>();

    for (MrpLine mrpLine : mrp.getMrpLineList()) {

      if (!mrpLine.getProposalGenerated()) {
        mrpLineService.generateProposal(mrpLine, purchaseOrders);
      }
    }
  }

  @Override
  public LocalDate findMrpEndDate(Mrp mrp) {
    if (mrp.getEndDate() != null) {
      return mrp.getEndDate();
    }
    return mrp.getMrpLineList()
        .stream()
        .max(Comparator.comparing(MrpLine::getMaturityDate))
        .get()
        .getMaturityDate();
  }
}
