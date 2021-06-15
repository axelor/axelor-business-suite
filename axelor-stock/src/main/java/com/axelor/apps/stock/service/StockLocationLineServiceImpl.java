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
package com.axelor.apps.stock.service;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.StockRules;
import com.axelor.apps.stock.db.TrackingNumber;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.db.repo.StockRulesRepository;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.google.inject.servlet.RequestScoped;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequestScoped
public class StockLocationLineServiceImpl implements StockLocationLineService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected StockLocationLineRepository stockLocationLineRepo;

  protected StockRulesService stockRulesService;

  protected StockMoveLineRepository stockMoveLineRepository;

  protected AppBaseService appBaseService;

  protected WapHistoryService wapHistoryService;

  protected UnitConversionService unitConversionService;

  @Inject
  public StockLocationLineServiceImpl(
      StockLocationLineRepository stockLocationLineRepo,
      StockRulesService stockRulesService,
      StockMoveLineRepository stockMoveLineRepository,
      AppBaseService appBaseService,
      WapHistoryService wapHistoryService,
      UnitConversionService unitConversionService) {
    this.stockLocationLineRepo = stockLocationLineRepo;
    this.stockRulesService = stockRulesService;
    this.stockMoveLineRepository = stockMoveLineRepository;
    this.appBaseService = appBaseService;
    this.wapHistoryService = wapHistoryService;
    this.unitConversionService = unitConversionService;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateLocation(
      StockLocation stockLocation,
      Product product,
      Unit stockMoveLineUnit,
      BigDecimal qty,
      boolean current,
      boolean future,
      boolean isIncrement,
      LocalDate lastFutureStockMoveDate,
      TrackingNumber trackingNumber)
      throws AxelorException {

    this.updateLocation(
        stockLocation,
        product,
        stockMoveLineUnit,
        qty,
        current,
        future,
        isIncrement,
        lastFutureStockMoveDate);

    if (trackingNumber != null) {
      this.updateDetailLocation(
          stockLocation,
          product,
          stockMoveLineUnit,
          qty,
          current,
          future,
          isIncrement,
          lastFutureStockMoveDate,
          trackingNumber);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateLocation(
      StockLocation stockLocation,
      Product product,
      Unit stockMoveLineUnit,
      BigDecimal qty,
      boolean current,
      boolean future,
      boolean isIncrement,
      LocalDate lastFutureStockMoveDate)
      throws AxelorException {

    StockLocationLine stockLocationLine = this.getOrCreateStockLocationLine(stockLocation, product);

    if (stockLocationLine == null) {
      return;
    }

    Unit stockLocationLineUnit = stockLocationLine.getUnit();
    if (stockLocationLineUnit == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LOCATION_LINE_MISSING_UNIT),
          stockLocation.getName(),
          product.getFullName());
    }
    if (!stockLocationLineUnit.equals(stockMoveLineUnit)) {
      qty =
          unitConversionService.convert(
              stockMoveLineUnit, stockLocationLineUnit, qty, qty.scale(), product);
    }

    LOG.debug(
        "Mise à jour du stock : Entrepot? {}, Produit? {}, Quantité? {}, Actuel? {}, Futur? {}, Incrément? {}, Date? {} ",
        stockLocation.getName(),
        product.getCode(),
        qty,
        current,
        future,
        isIncrement,
        lastFutureStockMoveDate);

    if (!isIncrement) {
      minStockRules(product, qty, stockLocationLine, current, future);
    } else {
      maxStockRules(product, qty, stockLocationLine, current, future);
    }

    stockLocationLine =
        this.updateLocation(
            stockLocationLine,
            stockMoveLineUnit,
            product,
            qty,
            current,
            future,
            isIncrement,
            lastFutureStockMoveDate);

    this.checkStockMin(stockLocationLine, false);

    stockLocationLineRepo.save(stockLocationLine);
  }

  @Override
  public void minStockRules(
      Product product,
      BigDecimal qty,
      StockLocationLine stockLocationLine,
      boolean current,
      boolean future)
      throws AxelorException {

    if (current) {
      stockRulesService.generateOrder(
          product, qty, stockLocationLine, StockRulesRepository.TYPE_CURRENT);
    }
    if (future) {
      stockRulesService.generateOrder(
          product, qty, stockLocationLine, StockRulesRepository.TYPE_FUTURE);
    }
  }

  public void maxStockRules(
      Product product,
      BigDecimal qty,
      StockLocationLine stockLocationLine,
      boolean current,
      boolean future)
      throws AxelorException {

    if (current) {
      checkStockMax(
          product,
          qty,
          stockLocationLine,
          StockRulesRepository.TYPE_CURRENT,
          stockLocationLine.getCurrentQty());
    }

    if (future) {
      checkStockMax(
          product,
          qty,
          stockLocationLine,
          StockRulesRepository.TYPE_FUTURE,
          stockLocationLine.getFutureQty());
    }
  }

  protected void checkStockMax(
      Product product,
      BigDecimal qty,
      StockLocationLine stockLocationLine,
      int type,
      BigDecimal baseQty)
      throws AxelorException {
    StockLocation stockLocation = stockLocationLine.getStockLocation();
    StockRules stockRules =
        stockRulesService.getStockRules(
            product, stockLocation, type, StockRulesRepository.USE_CASE_STOCK_CONTROL);

    if (stockRules == null || !stockRules.getUseMaxQty()) {
      return;
    }

    if (baseQty.add(qty).compareTo(stockRules.getMaxQty()) > 0) {
      throw new AxelorException(
          stockLocationLine,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LOCATION_LINE_3),
          stockLocationLine.getProduct().getName(),
          stockLocationLine.getProduct().getCode());
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateDetailLocation(
      StockLocation stockLocation,
      Product product,
      Unit stockMoveLineUnit,
      BigDecimal qty,
      boolean current,
      boolean future,
      boolean isIncrement,
      LocalDate lastFutureStockMoveDate,
      TrackingNumber trackingNumber)
      throws AxelorException {

    StockLocationLine detailLocationLine =
        this.getOrCreateDetailLocationLine(stockLocation, product, trackingNumber);

    if (detailLocationLine == null) {
      return;
    }

    Unit stockLocationLineUnit = detailLocationLine.getUnit();

    if (stockLocationLineUnit == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.DETAIL_LOCATION_LINE_MISSING_UNIT),
          trackingNumber.getTrackingNumberSeq(),
          stockLocation.getName(),
          product.getFullName());
    }
    if (!stockLocationLineUnit.equals(stockMoveLineUnit)) {
      qty =
          unitConversionService.convert(
              stockMoveLineUnit, stockLocationLineUnit, qty, qty.scale(), product);
    }

    LOG.debug(
        "Mise à jour du detail du stock : Entrepot? {}, Produit? {}, Quantité? {}, Actuel? {}, Futur? {}, Incrément? {}, Date? {}, Num de suivi? {} ",
        stockLocation.getName(),
        product.getCode(),
        qty,
        current,
        future,
        isIncrement,
        lastFutureStockMoveDate,
        trackingNumber);

    detailLocationLine =
        this.updateLocation(
            detailLocationLine,
            stockMoveLineUnit,
            product,
            qty,
            current,
            future,
            isIncrement,
            lastFutureStockMoveDate);

    this.checkStockMin(detailLocationLine, true);

    stockLocationLineRepo.save(detailLocationLine);
  }

  @Override
  public void checkStockMin(StockLocationLine stockLocationLine, boolean isDetailLocationLine)
      throws AxelorException {
    if (!isDetailLocationLine
        && stockLocationLine.getCurrentQty().compareTo(BigDecimal.ZERO) < 0
        && stockLocationLine.getStockLocation().getTypeSelect()
            != StockLocationRepository.TYPE_VIRTUAL) {
      throw new AxelorException(
          stockLocationLine,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LOCATION_LINE_1),
          stockLocationLine.getProduct().getName(),
          stockLocationLine.getProduct().getCode());

    } else if (isDetailLocationLine
        && stockLocationLine.getCurrentQty().compareTo(BigDecimal.ZERO) < 0
        && ((stockLocationLine.getStockLocation() != null
                && stockLocationLine.getStockLocation().getTypeSelect()
                    != StockLocationRepository.TYPE_VIRTUAL)
            || (stockLocationLine.getDetailsStockLocation() != null
                && stockLocationLine.getDetailsStockLocation().getTypeSelect()
                    != StockLocationRepository.TYPE_VIRTUAL))) {

      String trackingNumber = "";
      if (stockLocationLine.getTrackingNumber() != null) {
        trackingNumber = stockLocationLine.getTrackingNumber().getTrackingNumberSeq();
      }

      throw new AxelorException(
          stockLocationLine,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LOCATION_LINE_2),
          stockLocationLine.getProduct().getName(),
          stockLocationLine.getProduct().getCode(),
          trackingNumber);
    }
  }

  @Override
  public void checkIfEnoughStock(StockLocation stockLocation, Product product, BigDecimal qty)
      throws AxelorException {

    if (!product.getStockManaged()) {
      return;
    }

    StockLocationLine stockLocationLine = this.getStockLocationLine(stockLocation, product);

    if (stockLocationLine != null && stockLocationLine.getCurrentQty().compareTo(qty) < 0) {
      throw new AxelorException(
          stockLocationLine,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LOCATION_LINE_1),
          stockLocationLine.getProduct().getName(),
          stockLocationLine.getProduct().getCode());
    }
  }

  @Override
  public StockLocationLine updateLocation(
      StockLocationLine stockLocationLine,
      Unit stockMoveLineUnit,
      Product product,
      BigDecimal qty,
      boolean current,
      boolean future,
      boolean isIncrement,
      LocalDate lastFutureStockMoveDate)
      throws AxelorException {

    if (current) {
      if (isIncrement) {
        stockLocationLine.setCurrentQty(stockLocationLine.getCurrentQty().add(qty));
      } else {
        stockLocationLine.setCurrentQty(stockLocationLine.getCurrentQty().subtract(qty));
      }
    }
    if (future) {
      stockLocationLine.setFutureQty(computeFutureQty(stockLocationLine));
      stockLocationLine.setLastFutureStockMoveDate(lastFutureStockMoveDate);
    }

    return stockLocationLine;
  }

  @Override
  public StockLocationLine getOrCreateStockLocationLine(
      StockLocation stockLocation, Product product) {

    if (!product.getStockManaged()) {
      return null;
    }

    StockLocationLine stockLocationLine = this.getStockLocationLine(stockLocation, product);

    if (stockLocationLine == null) {
      stockLocationLine = this.createLocationLine(stockLocation, product);
    }

    LOG.debug(
        "Récupération ligne de stock: Entrepot? {}, Produit? {}, Qté actuelle? {}, Qté future? {}, Date? {} ",
        stockLocationLine.getStockLocation().getName(),
        product.getCode(),
        stockLocationLine.getCurrentQty(),
        stockLocationLine.getFutureQty(),
        stockLocationLine.getLastFutureStockMoveDate());

    return stockLocationLine;
  }

  @Override
  public StockLocationLine getOrCreateDetailLocationLine(
      StockLocation detailLocation, Product product, TrackingNumber trackingNumber) {

    StockLocationLine detailLocationLine =
        this.getDetailLocationLine(detailLocation, product, trackingNumber);

    if (detailLocationLine == null) {

      detailLocationLine = this.createDetailLocationLine(detailLocation, product, trackingNumber);
    }

    LOG.debug(
        "Récupération ligne de détail de stock: Entrepot? {}, Produit? {}, Qté actuelle? {}, Qté future? {}, Date? {}, Variante? {}, Num de suivi? {} ",
        detailLocationLine.getDetailsStockLocation().getName(),
        product.getCode(),
        detailLocationLine.getCurrentQty(),
        detailLocationLine.getFutureQty(),
        detailLocationLine.getLastFutureStockMoveDate(),
        detailLocationLine.getTrackingNumber());

    return detailLocationLine;
  }

  @Override
  public StockLocationLine getStockLocationLine(StockLocation stockLocation, Product product) {

    if (product == null || !product.getStockManaged()) {
      return null;
    }

    return stockLocationLineRepo
        .all()
        .filter("self.stockLocation.id = :_stockLocationId " + "AND self.product.id = :_productId")
        .bind("_stockLocationId", stockLocation.getId())
        .bind("_productId", product.getId())
        .fetchOne();
  }

  @Override
  public List<StockLocationLine> getStockLocationLines(Product product) {

    if (product != null && !product.getStockManaged()) {
      return null;
    }

    return stockLocationLineRepo
        .all()
        .filter("self.product.id = :_productId")
        .bind("_productId", product.getId())
        .fetch();
  }

  @Override
  public StockLocationLine getDetailLocationLine(
      StockLocation stockLocation, Product product, TrackingNumber trackingNumber) {
    return stockLocationLineRepo
        .all()
        .filter(
            "self.detailsStockLocation.id = :_stockLocationId "
                + "AND self.product.id = :_productId "
                + "AND self.trackingNumber.id = :_trackingNumberId")
        .bind("_stockLocationId", stockLocation.getId())
        .bind("_productId", product.getId())
        .bind("_trackingNumberId", trackingNumber.getId())
        .fetchOne();
  }

  @Override
  public StockLocationLine createLocationLine(StockLocation stockLocation, Product product) {

    LOG.debug(
        "Création d'une ligne de stock : Entrepot? {}, Produit? {} ",
        stockLocation.getName(),
        product.getCode());

    StockLocationLine stockLocationLine = new StockLocationLine();

    stockLocationLine.setStockLocation(stockLocation);
    stockLocation.addStockLocationLineListItem(stockLocationLine);
    stockLocationLine.setProduct(product);
    stockLocationLine.setUnit(product.getUnit());
    stockLocationLine.setCurrentQty(BigDecimal.ZERO);
    stockLocationLine.setFutureQty(BigDecimal.ZERO);

    return stockLocationLine;
  }

  @Override
  public StockLocationLine createDetailLocationLine(
      StockLocation stockLocation, Product product, TrackingNumber trackingNumber) {

    LOG.debug(
        "Création d'une ligne de détail de stock : Entrepot? {}, Produit? {}, Num de suivi? {} ",
        stockLocation.getName(),
        product.getCode(),
        trackingNumber.getTrackingNumberSeq());

    StockLocationLine detailLocationLine = new StockLocationLine();

    detailLocationLine.setDetailsStockLocation(stockLocation);
    stockLocation.addDetailsStockLocationLineListItem(detailLocationLine);
    detailLocationLine.setProduct(product);
    detailLocationLine.setUnit(product.getUnit());
    detailLocationLine.setCurrentQty(BigDecimal.ZERO);
    detailLocationLine.setFutureQty(BigDecimal.ZERO);
    detailLocationLine.setTrackingNumber(trackingNumber);

    return detailLocationLine;
  }

  @Override
  public BigDecimal getAvailableQty(StockLocation stockLocation, Product product) {
    StockLocationLine stockLocationLine = getStockLocationLine(stockLocation, product);
    BigDecimal availableQty = BigDecimal.ZERO;
    if (stockLocationLine != null) {
      availableQty = stockLocationLine.getCurrentQty();
    }
    return availableQty;
  }

  @Override
  public BigDecimal getTrackingNumberAvailableQty(
      StockLocation stockLocation, TrackingNumber trackingNumber) {
    StockLocationLine detailStockLocationLine =
        getDetailLocationLine(stockLocation, trackingNumber.getProduct(), trackingNumber);

    BigDecimal availableQty = BigDecimal.ZERO;

    if (detailStockLocationLine != null) {
      availableQty = detailStockLocationLine.getCurrentQty();
    }
    return availableQty;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateStockLocationFromProduct(StockLocationLine stockLocationLine, Product product)
      throws AxelorException {

    stockLocationLine = this.updateLocationFromProduct(stockLocationLine, product);
    stockLocationLineRepo.save(stockLocationLine);
  }

  @Override
  public StockLocationLine updateLocationFromProduct(
      StockLocationLine stockLocationLine, Product product) throws AxelorException {
    Unit productUnit = product.getUnit();
    Unit stockLocationUnit = stockLocationLine.getUnit();

    if (productUnit != null && !productUnit.equals(stockLocationUnit)) {
      int scale = appBaseService.getNbDecimalDigitForUnitPrice();
      int qtyScale = appBaseService.getNbDecimalDigitForQty();
      BigDecimal oldQty = stockLocationLine.getCurrentQty();
      BigDecimal oldAvgPrice = stockLocationLine.getAvgPrice();

      BigDecimal currentQty =
          unitConversionService.convert(
              stockLocationUnit,
              productUnit,
              stockLocationLine.getCurrentQty(),
              stockLocationLine.getCurrentQty().scale(),
              product);
      stockLocationLine.setCurrentQty(currentQty);

      stockLocationLine.setUnit(product.getUnit());
      stockLocationLine.setFutureQty(computeFutureQty(stockLocationLine));

      BigDecimal avgQty = BigDecimal.ZERO;
      if (currentQty.compareTo(BigDecimal.ZERO) != 0) {
        avgQty = oldQty.divide(currentQty, qtyScale, RoundingMode.HALF_UP);
      }
      BigDecimal newAvgPrice = oldAvgPrice.multiply(avgQty);
      updateWap(stockLocationLine, newAvgPrice.setScale(scale, RoundingMode.HALF_UP));
    }
    return stockLocationLine;
  }

  protected static final String STOCK_MOVE_LINE_FILTER =
      "(self.stockMove.archived IS NULL OR self.archived IS FALSE) "
          + "AND self.stockMove.statusSelect = :planned "
          + "AND self.product.id = :productId ";

  @Override
  public BigDecimal computeFutureQty(StockLocationLine stockLocationLine) throws AxelorException {
    // future quantity is current quantity minus planned outgoing stock move lines plus planned
    // incoming stock move lines.

    Product product = stockLocationLine.getProduct();

    BigDecimal futureQty = stockLocationLine.getCurrentQty();

    List<StockMoveLine> incomingStockMoveLineList =
        findIncomingPlannedStockMoveLines(stockLocationLine);
    List<StockMoveLine> outgoingStockMoveLineList =
        findOutgoingPlannedStockMoveLines(stockLocationLine);

    if (stockLocationLine.getUnit() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LOCATION_LINE_MISSING_UNIT),
          stockLocationLine.getStockLocation().getName(),
          product.getFullName());
    }

    for (StockMoveLine incomingStockMoveLine : incomingStockMoveLineList) {
      BigDecimal qtyToAdd =
          unitConversionService.convert(
              incomingStockMoveLine.getUnit(),
              stockLocationLine.getUnit(),
              incomingStockMoveLine.getRealQty(),
              incomingStockMoveLine.getRealQty().scale(),
              product);
      futureQty = futureQty.add(qtyToAdd);
    }

    for (StockMoveLine outgoingStockMoveLine : outgoingStockMoveLineList) {
      BigDecimal qtyToSubtract =
          unitConversionService.convert(
              outgoingStockMoveLine.getUnit(),
              stockLocationLine.getUnit(),
              outgoingStockMoveLine.getRealQty(),
              outgoingStockMoveLine.getRealQty().scale(),
              product);
      futureQty = futureQty.subtract(qtyToSubtract);
    }

    return futureQty;
  }

  protected List<StockMoveLine> findIncomingPlannedStockMoveLines(
      StockLocationLine stockLocationLine) {
    boolean isDetailsStockLocationLine = stockLocationLine.getDetailsStockLocation() != null;
    String incomingStockMoveLineFilter =
        STOCK_MOVE_LINE_FILTER + "AND self.stockMove.toStockLocation.id = :stockLocationId";
    if (isDetailsStockLocationLine) {
      incomingStockMoveLineFilter =
          incomingStockMoveLineFilter + " AND self.trackingNumber.id = :trackingNumberId";
    }
    Query<StockMoveLine> stockMoveLineQuery =
        stockMoveLineRepository
            .all()
            .filter(incomingStockMoveLineFilter)
            .bind("planned", StockMoveRepository.STATUS_PLANNED)
            .bind("productId", stockLocationLine.getProduct().getId());
    if (isDetailsStockLocationLine) {
      stockMoveLineQuery
          .bind("stockLocationId", stockLocationLine.getDetailsStockLocation().getId())
          .bind("trackingNumberId", stockLocationLine.getTrackingNumber().getId());
    } else {
      stockMoveLineQuery.bind("stockLocationId", stockLocationLine.getStockLocation().getId());
    }
    return stockMoveLineQuery.fetch();
  }

  protected List<StockMoveLine> findOutgoingPlannedStockMoveLines(
      StockLocationLine stockLocationLine) {
    boolean isDetailsStockLocationLine = stockLocationLine.getDetailsStockLocation() != null;
    String outgoingStockMoveLineFilter =
        STOCK_MOVE_LINE_FILTER + "AND self.stockMove.fromStockLocation.id = :stockLocationId";
    if (isDetailsStockLocationLine) {
      outgoingStockMoveLineFilter =
          outgoingStockMoveLineFilter + " AND self.trackingNumber.id = :trackingNumberId";
    }
    Query<StockMoveLine> stockMoveLineQuery =
        stockMoveLineRepository
            .all()
            .filter(outgoingStockMoveLineFilter)
            .bind("planned", StockMoveRepository.STATUS_PLANNED)
            .bind("productId", stockLocationLine.getProduct().getId());

    if (isDetailsStockLocationLine) {
      stockMoveLineQuery
          .bind("stockLocationId", stockLocationLine.getDetailsStockLocation().getId())
          .bind("trackingNumberId", stockLocationLine.getTrackingNumber().getId());
    } else {
      stockMoveLineQuery.bind("stockLocationId", stockLocationLine.getStockLocation().getId());
    }
    return stockMoveLineQuery.fetch();
  }

  @Override
  public String getStockLocationLineListForAProduct(
      Long productId, Long companyId, Long stockLocationId) {

    String query =
        "self.product.id = "
            + productId
            + " AND self.stockLocation.typeSelect != "
            + StockLocationRepository.TYPE_VIRTUAL;

    if (companyId != 0L) {
      query += " AND self.stockLocation.company.id = " + companyId;
      if (stockLocationId != 0L) {
        StockLocation stockLocation =
            Beans.get(StockLocationRepository.class).find(stockLocationId);
        List<StockLocation> stockLocationList =
            Beans.get(StockLocationService.class)
                .getAllLocationAndSubLocation(stockLocation, false);
        if (!stockLocationList.isEmpty() && stockLocation.getCompany().getId().equals(companyId)) {
          query +=
              " AND self.stockLocation.id IN ("
                  + StringTool.getIdListString(stockLocationList)
                  + ") ";
        }
      }
    }
    return query;
  }

  @Override
  public String getAvailableStockForAProduct(Long productId, Long companyId, Long stockLocationId) {
    String query = this.getStockLocationLineListForAProduct(productId, companyId, stockLocationId);
    query +=
        " AND (self.currentQty != 0 OR self.futureQty != 0) "
            + " AND (self.stockLocation.isNotInCalculStock = false OR self.stockLocation.isNotInCalculStock IS NULL)";
    return query;
  }

  @Override
  public String getRequestedReservedQtyForAProduct(
      Long productId, Long companyId, Long stockLocationId) {
    String query = this.getStockLocationLineListForAProduct(productId, companyId, stockLocationId);
    query += " AND self.requestedReservedQty > 0";
    return query;
  }

  @Override
  public void updateWap(StockLocationLine stockLocationLine, BigDecimal wap) {
    updateWap(stockLocationLine, wap, null);
  }

  @Override
  public void updateWap(
      StockLocationLine stockLocationLine, BigDecimal wap, StockMoveLine stockMoveLine) {
    stockLocationLine.setAvgPrice(wap);
    wapHistoryService.saveWapHistory(stockLocationLine, stockMoveLine);
  }
}
