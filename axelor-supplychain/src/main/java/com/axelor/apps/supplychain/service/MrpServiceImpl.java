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

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.SupplierCatalog;
import com.axelor.apps.purchase.db.repo.PurchaseOrderLineRepository;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.purchase.service.app.AppPurchaseService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockRules;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockRulesRepository;
import com.axelor.apps.stock.service.StockLocationService;
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
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaStore;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
  protected StockLocationService stockLocationService;

  protected AppBaseService appBaseService;

  protected List<StockLocation> stockLocationList;
  protected Map<Long, Integer> productMap;
  protected Mrp mrp;
  protected LocalDate today;

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
      MrpForecastRepository mrpForecastRepository,
      StockLocationService stockLocationService) {

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
    this.stockLocationService = stockLocationService;
  }

  @Override
  public void runCalculation(Mrp mrp) throws AxelorException {

    this.reset(mrp);

    this.startMrp(mrpRepository.find(mrp.getId()));
    this.completeMrp(mrpRepository.find(mrp.getId()));
    this.doCalulation(mrpRepository.find(mrp.getId()));
    this.finish(mrpRepository.find(mrp.getId()));
  }

  @Transactional
  protected void startMrp(Mrp mrp) {

    mrp.setStartDateTime(appBaseService.getTodayDateTime().toLocalDateTime());
    log.debug("Start MRP");

    mrp.setStatusSelect(MrpRepository.STATUS_CALCULATION_STARTED);

    // TODO check that the different types used for purchase/manufOrder proposal are in stock type
    // TODO check that all types exist + override the method on production module

    today = appBaseService.getTodayDate();

    mrpRepository.save(mrp);
  }

  @Override
  @Transactional
  public void reset(Mrp mrp) {

    mrpLineRepository
        .all()
        .filter("self.mrp.id = ?1 AND self.isEditedByUser = ?2", mrp.getId(), false)
        .remove();

    mrp.setStatusSelect(MrpRepository.STATUS_DRAFT);

    mrpRepository.save(mrp);
  }

  protected void completeMrp(Mrp mrp) throws AxelorException {

    log.debug("Complete MRP");

    // Initialize
    this.mrp = mrp;
    List<StockLocation> tmpSlList = new ArrayList<>();
    for (StockLocation stockLocation : mrp.getStockLocationSet()) {
      tmpSlList.addAll(stockLocationService.getAllLocationAndSubLocation(stockLocation, false));
    }

    List<StockLocation> slList =
        tmpSlList.stream().distinct().filter(x -> !x.getIsNotInMrp()).collect(Collectors.toList());
    this.stockLocationList = slList;

    this.assignProductAndLevel(this.getProductList());
    if (stockLocationList.isEmpty()) {
      throw new AxelorException(
          Mrp.class,
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.MRP_MISSING_STOCK_LOCATION_VALID));
    }
    // Get the stock for each product on each stock location
    this.createAvailableStockMrpLines();

    this.createPurchaseMrpLines();

    this.createSaleOrderMrpLines();

    this.createSaleForecastMrpLines();
  }

  protected void doCalulation(Mrp mrp) throws AxelorException {

    log.debug("Do calculation");

    this.mrp = mrp;

    this.checkInsufficientCumulativeQty();

    //		this.consolidateMrp(mrp);

  }

  @Transactional
  protected void finish(Mrp mrp) {

    log.debug("Finish MRP");

    mrp.setStatusSelect(MrpRepository.STATUS_CALCULATION_ENDED);
    mrp.setEndDateTime(appBaseService.getTodayDateTime().toLocalDateTime());
    mrpRepository.save(mrp);
  }

  protected void checkInsufficientCumulativeQty() throws AxelorException {

    for (int level = 0; level <= this.getMaxLevel(); level++) {

      for (Product product : this.getProductList(level)) {

        this.checkInsufficientCumulativeQty(product, true);
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

  protected void checkInsufficientCumulativeQty(Product product, boolean firstPass)
      throws AxelorException {

    boolean doASecondPass = false;

    this.computeCumulativeQty(productRepository.find(product.getId()));

    JPA.clear();

    List<MrpLine> mrpLineList =
        mrpLineRepository
            .all()
            .filter("self.mrp.id = ?1 AND self.product.id = ?2", mrp.getId(), product.getId())
            .order("maturityDate")
            .order("mrpLineType.typeSelect")
            .order("mrpLineType.sequence")
            .order("id")
            .fetch();

    for (MrpLine mrpLine : mrpLineList) {

      doASecondPass =
          this.checkInsufficientCumulativeQty(
              mrpLineRepository.find(mrpLine.getId()),
              productRepository.find(product.getId()),
              firstPass);
      JPA.clear();
      if (doASecondPass) {
        break;
      }
    }

    if (doASecondPass) {

      this.checkInsufficientCumulativeQty(product, false);
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected boolean checkInsufficientCumulativeQty(
      MrpLine mrpLine, Product product, boolean firstPass) throws AxelorException {

    BigDecimal cumulativeQty = mrpLine.getCumulativeQty();

    MrpLineType mrpLineType = mrpLine.getMrpLineType();

    boolean isProposalElement = this.isProposalElement(mrpLineType);

    BigDecimal minQty = mrpLine.getMinQty();

    if ((((mrpLine.getMrpLineType().getElementSelect()
                    != MrpLineTypeRepository.ELEMENT_AVAILABLE_STOCK)
                && (!isProposalElement
                    || mrpLineType.getTypeSelect() == MrpLineTypeRepository.TYPE_OUT))
            || (mrpLine.getMrpLineType().getElementSelect()
                    == MrpLineTypeRepository.ELEMENT_AVAILABLE_STOCK
                && firstPass))
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

      long duplicateCount =
          mrpLineRepository
              .all()
              .filter(
                  "self.mrp.id = ?1  AND self.isEditedByUser = ?2 AND self.product = ?3 AND self.relatedToSelectName = ?4",
                  mrp.getId(),
                  true,
                  product,
                  mrpLine.getRelatedToSelectName())
              .count();

      if (duplicateCount != 0) {
        return false;
      }

      this.createProposalMrpLine(
          mrpLine.getMrp(),
          product,
          mrpLineTypeProposal,
          reorderQty,
          mrpLine.getStockLocation(),
          mrpLine.getMaturityDate(),
          mrpLine.getMrpLineOriginList(),
          mrpLine.getRelatedToSelectName());

      return true;
    }

    return false;
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
            "self.mrp.id = ?1 AND self.product = ?2 AND self.mrpLineType = ?3 AND self.stockLocation = ?4 AND self.maturityDate > ?5 AND self.maturityDate <= ?6",
            mrp.getId(),
            product,
            mrpLineType,
            stockLocation,
            startPeriodDate,
            maturityDate)
        .fetchOne();
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void createProposalMrpLine(
      Mrp mrp,
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

    if (maturityDate.isBefore(today)) {
      maturityDate = today;
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
      MrpLine createdmrpLine =
          this.createMrpLine(
              mrp,
              product,
              mrpLineType,
              reorderQty,
              maturityDate,
              BigDecimal.ZERO,
              stockLocation,
              null);
      if (createdmrpLine != null) {
        mrpLine = mrpLineRepository.save(createdmrpLine);
      }
      mrpLine.setRelatedToSelectName(relatedToSelectName);
    }

    this.copyMrpLineOrigins(mrpLine, mrpLineOriginList);
  }

  protected BigDecimal getSupplierCatalogMinQty(Product product) {

    Partner supplierPartner = product.getDefaultSupplierPartner();

    if (supplierPartner != null
        && Beans.get(AppPurchaseService.class).getAppPurchase().getManageSupplierCatalog()) {

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
            .filter("self.mrp.id = ?1", mrp.getId())
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
        mrpLineRepository.remove(mrpLine);

      } else {
        map.put(keys, mrpLine);
      }
    }
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

  @Transactional
  protected void computeCumulativeQty(Product product) {

    List<MrpLine> mrpLineList =
        mrpLineRepository
            .all()
            .filter("self.mrp.id = ?1 AND self.product.id = ?2", mrp.getId(), product.getId())
            .order("maturityDate")
            .order("mrpLineType.typeSelect")
            .order("mrpLineType.sequence")
            .order("id")
            .fetch();

    BigDecimal previousCumulativeQty = BigDecimal.ZERO;

    for (MrpLine mrpLine : mrpLineList) {
      mrpLine.setCumulativeQty(previousCumulativeQty.add(mrpLine.getQty()));
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

    MrpLineType purchaseOrderMrpLineType =
        this.getMrpLineType(MrpLineTypeRepository.ELEMENT_PURCHASE_ORDER);
    String statusSelect = purchaseOrderMrpLineType.getStatusSelect();
    List<Integer> statusList = StringTool.getIntegerList(statusSelect);

    if (statusList.isEmpty()) {
      statusList.add(PurchaseOrderRepository.STATUS_VALIDATED);
    }

    // TODO : Manage the case where order is partially delivered
    List<PurchaseOrderLine> purchaseOrderLineList =
        purchaseOrderLineRepository
            .all()
            .filter(
                "self.product.id in (?1) AND self.purchaseOrder.stockLocation in (?2) AND self.receiptState != ?3 "
                    + "AND self.purchaseOrder.statusSelect IN (?4)",
                this.productMap.keySet(),
                this.stockLocationList,
                PurchaseOrderRepository.STATE_RECEIVED,
                statusList)
            .fetch();

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLineList) {

      this.createPurchaseMrpLines(
          mrpRepository.find(mrp.getId()),
          purchaseOrderLineRepository.find(purchaseOrderLine.getId()),
          mrpLineTypeRepository.find(purchaseOrderMrpLineType.getId()));
      JPA.clear();
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void createPurchaseMrpLines(
      Mrp mrp, PurchaseOrderLine purchaseOrderLine, MrpLineType purchaseOrderMrpLineType)
      throws AxelorException {

    PurchaseOrder purchaseOrder = purchaseOrderLine.getPurchaseOrder();

    LocalDate maturityDate = purchaseOrderLine.getEstimatedDelivDate();

    if (maturityDate == null) {
      maturityDate = purchaseOrder.getDeliveryDate();
    }
    if (maturityDate == null) {
      maturityDate = purchaseOrderLine.getDesiredDelivDate();
    }

    maturityDate = this.computeMaturityDate(maturityDate, purchaseOrderMrpLineType);

    if (this.isBeforeEndDate(maturityDate) || purchaseOrderMrpLineType.getIgnoreEndDate()) {

      Unit unit = purchaseOrderLine.getProduct().getUnit();
      BigDecimal qty = purchaseOrderLine.getQty().subtract(purchaseOrderLine.getReceivedQty());
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
              mrp,
              purchaseOrderLine.getProduct(),
              purchaseOrderMrpLineType,
              qty,
              maturityDate,
              BigDecimal.ZERO,
              purchaseOrder.getStockLocation(),
              purchaseOrderLine);
      if (mrpLine != null) {
        mrpLineRepository.save(mrpLine);
      }
    }
  }

  protected void createSaleOrderMrpLines() throws AxelorException {

    MrpLineType saleOrderMrpLineType =
        this.getMrpLineType(MrpLineTypeRepository.ELEMENT_SALE_ORDER);
    String statusSelect = saleOrderMrpLineType.getStatusSelect();
    List<Integer> statusList = StringTool.getIntegerList(statusSelect);

    List<SaleOrderLine> saleOrderLineList = new ArrayList<>();

    mrp = mrpRepository.find(mrp.getId());

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

      this.createSaleOrderMrpLines(
          mrpRepository.find(mrp.getId()),
          saleOrderLineRepository.find(saleOrderLine.getId()),
          mrpLineTypeRepository.find(saleOrderMrpLineType.getId()),
          statusList);
      JPA.clear();
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void createSaleOrderMrpLines(
      Mrp mrp,
      SaleOrderLine saleOrderLine,
      MrpLineType saleOrderMrpLineType,
      List<Integer> statusList)
      throws AxelorException {

    SaleOrder saleOrder = saleOrderLine.getSaleOrder();

    if (!this.stockLocationList.contains(saleOrder.getStockLocation())) {
      return;
    }
    if (!statusList.contains(saleOrder.getStatusSelect())) {
      return;
    }
    if (saleOrderLine.getDeliveryState() == SaleOrderLineRepository.DELIVERY_STATE_DELIVERED) {
      return;
    }

    LocalDate maturityDate = saleOrderLine.getEstimatedDelivDate();

    if (maturityDate == null) {
      maturityDate = saleOrder.getDeliveryDate();
    }
    if (maturityDate == null) {
      maturityDate = saleOrderLine.getDesiredDelivDate();
    }
    maturityDate = this.computeMaturityDate(maturityDate, saleOrderMrpLineType);

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
              mrp,
              saleOrderLine.getProduct(),
              saleOrderMrpLineType,
              qty,
              maturityDate,
              BigDecimal.ZERO,
              saleOrder.getStockLocation(),
              saleOrderLine);
      if (mrpLine != null) {
        mrpLineRepository.save(mrpLine);
      }
    }
  }

  protected void createSaleForecastMrpLines() throws AxelorException {

    MrpLineType saleForecastMrpLineType =
        this.getMrpLineType(MrpLineTypeRepository.ELEMENT_SALE_FORECAST);

    List<MrpForecast> mrpForecastList = new ArrayList<>();

    mrp = mrpRepository.find(mrp.getId());

    if (mrp.getMrpForecastSet().isEmpty()) {

      mrpForecastList.addAll(
          mrpForecastRepository
              .all()
              .filter(
                  "self.product.id in (?1) AND self.stockLocation in (?2) AND self.forecastDate >= ?3 AND self.statusSelect = ?4",
                  this.productMap.keySet(),
                  this.stockLocationList,
                  today,
                  MrpForecastRepository.STATUS_CONFIRMED)
              .fetch());

    } else {
      mrpForecastList.addAll(mrp.getMrpForecastSet());
    }

    for (MrpForecast mrpForecast : mrpForecastList) {

      this.createSaleForecastMrpLines(
          mrpRepository.find(mrp.getId()),
          mrpForecastRepository.find(mrpForecast.getId()),
          mrpLineTypeRepository.find(saleForecastMrpLineType.getId()));
      JPA.clear();
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void createSaleForecastMrpLines(
      Mrp mrp, MrpForecast mrpForecast, MrpLineType saleForecastMrpLineType)
      throws AxelorException {

    LocalDate maturityDate = mrpForecast.getForecastDate();

    if (maturityDate != null
        && !maturityDate.isBefore(today)
        && this.isBeforeEndDate(maturityDate)) {
      Unit unit = mrpForecast.getProduct().getUnit();
      BigDecimal qty = mrpForecast.getQty();
      if (!unit.equals(mrpForecast.getUnit())) {
        qty =
            Beans.get(UnitConversionService.class)
                .convert(mrpForecast.getUnit(), unit, qty, qty.scale(), mrpForecast.getProduct());
      }
      MrpLine mrpLine =
          this.createMrpLine(
              mrp,
              mrpForecast.getProduct(),
              saleForecastMrpLineType,
              qty,
              maturityDate,
              BigDecimal.ZERO,
              mrpForecast.getStockLocation(),
              mrpForecast);
      if (mrpLine != null) {
        mrpLineRepository.save(mrpLine);
      }
    }
  }

  protected LocalDate computeMaturityDate(LocalDate maturityDate, MrpLineType mrpLineType) {
    if ((maturityDate != null && maturityDate.isBefore(today))
        || (maturityDate == null && mrpLineType.getIncludeElementWithoutDate())) {
      maturityDate = today;
    }
    return maturityDate;
  }

  public boolean isBeforeEndDate(LocalDate maturityDate) {

    if (maturityDate != null
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
        this.createAvailableStockMrpLine(
            mrpRepository.find(mrp.getId()),
            productRepository.find(productId),
            stockLocationRepository.find(stockLocation.getId()),
            mrpLineTypeRepository.find(availableStockMrpLineType.getId()));
      }
      JPA.clear();
    }
  }

  @Transactional
  protected MrpLine createAvailableStockMrpLine(
      Mrp mrp,
      Product product,
      StockLocation stockLocation,
      MrpLineType availableStockMrpLineType) {

    BigDecimal qty = BigDecimal.ZERO;

    StockLocationLine stockLocationLine = this.getStockLocationLine(product, stockLocation);

    if (stockLocationLine != null) {

      qty = stockLocationLine.getCurrentQty();
    }

    return mrpLineRepository.save(
        this.createMrpLine(
            mrp, product, availableStockMrpLineType, qty, today, qty, stockLocation, null));
  }

  protected MrpLineType getMrpLineType(int elementSelect) throws AxelorException {

    MrpLineType mrpLineType =
        mrpLineTypeRepository.all().filter("self.elementSelect = ?1", elementSelect).fetchOne();

    if (mrpLineType != null) {
      return mrpLineType;
    }

    // for ticket-24571 finded the corresponding title of the value selected from selection.
    throw new AxelorException(
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        I18n.get(IExceptionMessage.MRP_MISSING_MRP_LINE_TYPE),
        I18n.get(
            MetaStore.getSelectionItem(
                    "supplychain.mrp.line.element.select", Integer.toString(elementSelect))
                .getTitle()));
  }

  protected StockLocationLine getStockLocationLine(Product product, StockLocation stockLocation) {

    return stockLocationLineRepository
        .all()
        .filter(
            "self.stockLocation.id = ?1 AND self.product.id = ?2",
            stockLocation.getId(),
            product.getId())
        .fetchOne();
  }

  protected Set<Product> getProductList() throws AxelorException {

    Set<Product> productSet = Sets.newHashSet();

    if (mrp.getProductSet() != null && !mrp.getProductSet().isEmpty()) {

      productSet.addAll(mrp.getProductSet());
    }

    if (mrp.getProductCategorySet() != null && !mrp.getProductCategorySet().isEmpty()) {

      productSet.addAll(
          productRepository
              .all()
              .filter(
                  "self.productCategory in (?1) AND self.productTypeSelect = ?2 AND self.excludeFromMrp = false AND self.stockManaged = true",
                  mrp.getProductCategorySet(),
                  ProductRepository.PRODUCT_TYPE_STORABLE)
              .fetch());
    }

    if (mrp.getProductFamilySet() != null && !mrp.getProductFamilySet().isEmpty()) {

      productSet.addAll(
          productRepository
              .all()
              .filter(
                  "self.productFamily in (?1) AND self.productTypeSelect = ?2 AND self.excludeFromMrp = false AND self.stockManaged = true",
                  mrp.getProductFamilySet(),
                  ProductRepository.PRODUCT_TYPE_STORABLE)
              .fetch());
    }
    if (mrp.getSaleOrderLineSet() != null) {
      for (SaleOrderLine saleOrderLine : mrp.getSaleOrderLineSet()) {
        productSet.add(saleOrderLine.getProduct());
      }
    }

    if (mrp.getMrpForecastSet() != null) {
      for (MrpForecast mrpForecast : mrp.getMrpForecastSet()) {

        productSet.add(mrpForecast.getProduct());
      }
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
      Mrp mrp,
      Product product,
      MrpLineType mrpLineType,
      BigDecimal qty,
      LocalDate maturityDate,
      BigDecimal cumulativeQty,
      StockLocation stockLocation,
      Model model) {

    if (productMap != null && product != null) {
      return mrpLineService.createMrpLine(
          mrp,
          product,
          this.productMap.get(product.getId()),
          mrpLineType,
          qty,
          maturityDate,
          cumulativeQty,
          stockLocation,
          model);
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

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void generateProposals(Mrp mrp, boolean isProposalPerSupplier) throws AxelorException {

    Map<Pair<Partner, LocalDate>, PurchaseOrder> purchaseOrders = new HashMap<>();
    Map<Partner, PurchaseOrder> purchaseOrdersPerSupplier = new HashMap<>();
    List<MrpLine> mrpLineList =
        mrpLineRepository
            .all()
            .filter("self.mrp.id = ?1", mrp.getId())
            .order("maturityDate")
            .fetch();

    for (MrpLine mrpLine : mrpLineList) {

      if (!mrpLine.getProposalGenerated()) {
        mrpLineService.generateProposal(
            mrpLine, purchaseOrders, purchaseOrdersPerSupplier, isProposalPerSupplier);
      }
    }
  }

  @Override
  public LocalDate findMrpEndDate(Mrp mrp) {
    if (mrp.getEndDate() != null) {
      return mrp.getEndDate();
    }

    MrpLine mrpLine =
        mrpLineRepository
            .all()
            .filter("self.mrp.id = ?1", mrp.getId())
            .order("-maturityDate")
            .fetchOne();

    if (mrpLine != null && mrpLine.getMaturityDate() != null) {
      return mrpLine.getMaturityDate();
    }

    return today;
  }

  @Override
  public Mrp createProjectedStock(
      Mrp mrp, Product product, Company company, StockLocation stockLocation)
      throws AxelorException {
    this.completeProjectedStock(mrp, product, company, stockLocation);
    this.computeCumulativeQty(product);
    return mrp;
  }

  protected Mrp completeProjectedStock(
      Mrp mrp, Product product, Company company, StockLocation stockLocation)
      throws AxelorException {
    this.mrp = mrp;
    mrp.addProductSetItem(product);
    if (stockLocation != null) {
      this.stockLocationList =
          stockLocationService.getAllLocationAndSubLocation(stockLocation, false);
    } else if (company != null) {
      this.stockLocationList =
          stockLocationRepository
              .all()
              .filter(
                  "self.company.id = ?1 AND self.typeSelect != ?2",
                  company.getId(),
                  StockLocationRepository.TYPE_VIRTUAL)
              .fetch();
    } else {
      this.stockLocationList =
          stockLocationRepository
              .all()
              .filter("self.typeSelect != ?1", StockLocationRepository.TYPE_VIRTUAL)
              .fetch();
    }
    this.startMrp(mrpRepository.find(mrp.getId()));
    this.assignProductAndLevel(this.getProductList());

    // Get the stock for each product on each stock location
    this.createAvailableStockMrpLines();

    this.createPurchaseMrpLines();

    this.createSaleOrderMrpLines();

    return mrp;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void undoManualChanges(Mrp mrp) {
    mrpLineRepository.all().filter("self.mrp.id = ?1", mrp.getId()).update("isEditedByUser", false);
  }
}
