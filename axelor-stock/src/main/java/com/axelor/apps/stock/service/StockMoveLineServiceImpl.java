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
package com.axelor.apps.stock.service;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Country;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.ShippingCoefService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.stock.db.CustomsCodeNomenclature;
import com.axelor.apps.stock.db.LogisticalForm;
import com.axelor.apps.stock.db.LogisticalFormLine;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.TrackingNumber;
import com.axelor.apps.stock.db.TrackingNumberConfiguration;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.db.repo.TrackingNumberRepository;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.service.app.AppStockService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.google.inject.servlet.RequestScoped;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequestScoped
public class StockMoveLineServiceImpl implements StockMoveLineService {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected AppBaseService appBaseService;
  protected AppStockService appStockService;
  protected StockMoveToolService stockMoveToolService;
  protected StockMoveLineRepository stockMoveLineRepository;
  protected StockLocationLineService stockLocationLineService;
  protected UnitConversionService unitConversionService;
  protected TrackingNumberService trackingNumberService;
  protected WeightedAveragePriceService weightedAveragePriceService;
  protected TrackingNumberRepository trackingNumberRepo;
  protected ShippingCoefService shippingCoefService;

  @Inject
  public StockMoveLineServiceImpl(
      TrackingNumberService trackingNumberService,
      AppBaseService appBaseService,
      AppStockService appStockService,
      StockMoveToolService stockMoveToolService,
      StockMoveLineRepository stockMoveLineRepository,
      StockLocationLineService stockLocationLineService,
      UnitConversionService unitConversionService,
      WeightedAveragePriceService weightedAveragePriceService,
      TrackingNumberRepository trackingNumberRepo,
      ShippingCoefService shippingCoefService) {
    this.trackingNumberService = trackingNumberService;
    this.appBaseService = appBaseService;
    this.appStockService = appStockService;
    this.stockMoveToolService = stockMoveToolService;
    this.stockMoveLineRepository = stockMoveLineRepository;
    this.stockLocationLineService = stockLocationLineService;
    this.unitConversionService = unitConversionService;
    this.weightedAveragePriceService = weightedAveragePriceService;
    this.trackingNumberRepo = trackingNumberRepo;
    this.shippingCoefService = shippingCoefService;
  }

  @Override
  public StockMoveLine createStockMoveLine(
      Product product,
      String productName,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal companyUnitPriceUntaxed,
      Unit unit,
      StockMove stockMove,
      int type,
      boolean taxed,
      BigDecimal taxRate)
      throws AxelorException {

    if (product != null) {

      StockMoveLine stockMoveLine =
          generateStockMoveLineConvertingUnitPrice(
              product,
              productName,
              description,
              quantity,
              unitPrice,
              companyUnitPriceUntaxed,
              BigDecimal.ZERO,
              unit,
              stockMove,
              taxed,
              taxRate);
      TrackingNumberConfiguration trackingNumberConfiguration =
          product.getTrackingNumberConfiguration();

      return assignOrGenerateTrackingNumber(
          stockMoveLine, stockMove, product, trackingNumberConfiguration, type);
    } else {
      return this.createStockMoveLine(
          product,
          productName,
          description,
          quantity,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          companyUnitPriceUntaxed,
          BigDecimal.ZERO,
          unit,
          stockMove,
          null);
    }
  }

  protected StockMoveLine generateStockMoveLineConvertingUnitPrice(
      Product product,
      String productName,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal companyUnitPriceUntaxed,
      BigDecimal companyPurchasePrice,
      Unit unit,
      StockMove stockMove,
      boolean taxed,
      BigDecimal taxRate)
      throws AxelorException {
    BigDecimal unitPriceUntaxed;
    BigDecimal unitPriceTaxed;
    if (taxed) {
      unitPriceTaxed =
          unitPrice.setScale(appBaseService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
      unitPriceUntaxed =
          unitPrice.divide(
              taxRate.add(BigDecimal.ONE),
              appBaseService.getNbDecimalDigitForUnitPrice(),
              RoundingMode.HALF_UP);
    } else {
      unitPriceUntaxed =
          unitPrice.setScale(appBaseService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
      unitPriceTaxed =
          unitPrice
              .multiply(taxRate.add(BigDecimal.ONE))
              .setScale(appBaseService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
    }
    return this.createStockMoveLine(
        product,
        productName,
        description,
        quantity,
        unitPriceUntaxed,
        unitPriceTaxed,
        companyUnitPriceUntaxed,
        companyPurchasePrice,
        unit,
        stockMove,
        null);
  }

  @Override
  public StockMoveLine assignOrGenerateTrackingNumber(
      StockMoveLine stockMoveLine,
      StockMove stockMove,
      Product product,
      TrackingNumberConfiguration trackingNumberConfiguration,
      int type)
      throws AxelorException {

    if (trackingNumberConfiguration != null) {

      switch (type) {
        case StockMoveLineService.TYPE_SALES:
          if (trackingNumberConfiguration.getIsSaleTrackingManaged()) {
            if (trackingNumberConfiguration.getGenerateSaleAutoTrackingNbr()) {
              // Générer numéro de série si case cochée
              this.generateTrackingNumber(
                  stockMoveLine,
                  trackingNumberConfiguration,
                  product,
                  trackingNumberConfiguration.getSaleQtyByTracking());

            } else {
              // Rechercher le numéro de suivi d'apèrs FIFO/LIFO
              this.assignTrackingNumber(stockMoveLine, product, stockMove.getFromStockLocation());
            }
          }
          break;
        case StockMoveLineService.TYPE_PURCHASES:
          if (trackingNumberConfiguration.getIsPurchaseTrackingManaged()
              && trackingNumberConfiguration.getGeneratePurchaseAutoTrackingNbr()) {
            // Générer numéro de série si case cochée
            this.generateTrackingNumber(
                stockMoveLine,
                trackingNumberConfiguration,
                product,
                trackingNumberConfiguration.getPurchaseQtyByTracking());
          }
          break;
        case StockMoveLineService.TYPE_OUT_PRODUCTIONS:
          if (trackingNumberConfiguration.getIsProductionTrackingManaged()
              && trackingNumberConfiguration.getGenerateProductionAutoTrackingNbr()) {
            // Générer numéro de série si case cochée
            this.generateTrackingNumber(
                stockMoveLine,
                trackingNumberConfiguration,
                product,
                trackingNumberConfiguration.getProductionQtyByTracking());
          }
          break;
        case StockMoveLineService.TYPE_IN_PRODUCTIONS:
          if (trackingNumberConfiguration.getHasProductAutoSelectTrackingNbr()) {
            // searching for the tracking number using FIFO or LIFO
            this.assignTrackingNumber(stockMoveLine, product, stockMove.getFromStockLocation());
          }
          break;
        case StockMoveLineService.TYPE_WASTE_PRODUCTIONS:
          break;
        default:
          break;
      }
    }
    return stockMoveLine;
  }

  @Override
  public void generateTrackingNumber(
      StockMoveLine stockMoveLine,
      TrackingNumberConfiguration trackingNumberConfiguration,
      Product product,
      BigDecimal qtyByTracking)
      throws AxelorException {

    int generateTrakingNumberCounter = 0;

    StockMove stockMove = stockMoveLine.getStockMove();

    if (qtyByTracking.compareTo(BigDecimal.ZERO) <= 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.STOCK_MOVE_QTY_BY_TRACKING));
    }
    while (stockMoveLine.getQty().compareTo(qtyByTracking) > 0) {

      BigDecimal minQty = stockMoveLine.getQty().min(qtyByTracking);

      this.splitStockMoveLine(
          stockMoveLine,
          minQty,
          trackingNumberService.getTrackingNumber(
              product, qtyByTracking, stockMove.getCompany(), stockMove.getEstimatedDate()));

      generateTrakingNumberCounter++;

      if (generateTrakingNumberCounter == 1000) {
        throw new AxelorException(
            TraceBackRepository.TYPE_TECHNICAL,
            I18n.get(IExceptionMessage.STOCK_MOVE_TOO_MANY_ITERATION));
      }
    }
    if (stockMoveLine.getTrackingNumber() == null) {

      stockMoveLine.setTrackingNumber(
          trackingNumberService.getTrackingNumber(
              product, qtyByTracking, stockMove.getCompany(), stockMove.getEstimatedDate()));
    }
  }

  @Override
  public StockMoveLine createStockMoveLine(
      Product product,
      String productName,
      String description,
      BigDecimal quantity,
      BigDecimal unitPriceUntaxed,
      BigDecimal unitPriceTaxed,
      BigDecimal companyUnitPriceUntaxed,
      BigDecimal companyPurchasePrice,
      Unit unit,
      StockMove stockMove,
      TrackingNumber trackingNumber)
      throws AxelorException {

    StockMoveLine stockMoveLine = new StockMoveLine();
    stockMoveLine.setProduct(product);
    stockMoveLine.setProductName(productName);
    stockMoveLine.setDescription(description);
    stockMoveLine.setQty(quantity);
    stockMoveLine.setRealQty(quantity);
    stockMoveLine.setUnitPriceUntaxed(unitPriceUntaxed);
    stockMoveLine.setUnitPriceTaxed(unitPriceTaxed);
    stockMoveLine.setUnit(unit);
    stockMoveLine.setTrackingNumber(trackingNumber);
    stockMoveLine.setCompanyUnitPriceUntaxed(companyUnitPriceUntaxed);
    stockMoveLine.setCompanyPurchasePrice(companyPurchasePrice);

    if (stockMove != null) {
      stockMove.addStockMoveLineListItem(stockMoveLine);
      stockMoveLine.setNetMass(
          this.computeNetMass(stockMove, stockMoveLine, stockMove.getCompany()));
      stockMoveLine.setSequence(stockMove.getStockMoveLineList().size());
    } else {
      stockMoveLine.setNetMass(this.computeNetMass(stockMove, stockMoveLine, null));
    }

    stockMoveLine.setTotalNetMass(
        stockMoveLine
            .getRealQty()
            .multiply(stockMoveLine.getNetMass())
            .setScale(2, RoundingMode.HALF_EVEN));

    if (product != null) {
      stockMoveLine.setCountryOfOrigin(product.getCountryOfOrigin());
      stockMoveLine.setProductTypeSelect(product.getProductTypeSelect());
    }

    return stockMoveLine;
  }

  @Override
  public void assignTrackingNumber(
      StockMoveLine stockMoveLine, Product product, StockLocation stockLocation)
      throws AxelorException {

    List<? extends StockLocationLine> stockLocationLineList =
        this.getStockLocationLines(product, stockLocation);

    if (stockLocationLineList != null) {
      for (StockLocationLine stockLocationLine : stockLocationLineList) {

        BigDecimal qty = stockLocationLine.getFutureQty();
        if (stockMoveLine.getQty().compareTo(qty) > 0) {
          this.splitStockMoveLine(stockMoveLine, qty, stockLocationLine.getTrackingNumber());
        } else {
          stockMoveLine.setTrackingNumber(stockLocationLine.getTrackingNumber());
          break;
        }
      }
    }
  }

  @Override
  public List<? extends StockLocationLine> getStockLocationLines(
      Product product, StockLocation stockLocation) throws AxelorException {

    List<? extends StockLocationLine> stockLocationLineList =
        Beans.get(StockLocationLineRepository.class)
            .all()
            .filter(
                "self.product = ?1 AND self.futureQty > 0 AND self.trackingNumber IS NOT NULL AND self.detailsStockLocation = ?2"
                    + trackingNumberService.getOrderMethod(
                        product.getTrackingNumberConfiguration()),
                product,
                stockLocation)
            .fetch();

    return stockLocationLineList;
  }

  @Override
  public StockMoveLine splitStockMoveLine(
      StockMoveLine stockMoveLine, BigDecimal qty, TrackingNumber trackingNumber)
      throws AxelorException {

    StockMoveLine newStockMoveLine =
        this.createStockMoveLine(
            stockMoveLine.getProduct(),
            stockMoveLine.getProductName(),
            stockMoveLine.getDescription(),
            qty,
            stockMoveLine.getUnitPriceUntaxed(),
            stockMoveLine.getUnitPriceTaxed(),
            stockMoveLine.getCompanyUnitPriceUntaxed(),
            stockMoveLine.getCompanyPurchasePrice(),
            stockMoveLine.getUnit(),
            stockMoveLine.getStockMove(),
            trackingNumber);

    stockMoveLine.setQty(stockMoveLine.getQty().subtract(qty));
    stockMoveLine.setRealQty(stockMoveLine.getRealQty().subtract(qty));

    return newStockMoveLine;
  }

  @Override
  public void updateLocations(
      StockLocation fromStockLocation,
      StockLocation toStockLocation,
      int fromStatus,
      int toStatus,
      List<StockMoveLine> stockMoveLineList,
      LocalDate lastFutureStockMoveDate,
      boolean realQty)
      throws AxelorException {

    stockMoveLineList = MoreObjects.firstNonNull(stockMoveLineList, Collections.emptyList());

    for (StockMoveLine stockMoveLine : stockMoveLineList) {

      Product product = stockMoveLine.getProduct();

      if (product != null
          && stockMoveLine.getLineTypeSelect() != StockMoveLineRepository.TYPE_PACK
          && product.getProductTypeSelect().equals(ProductRepository.PRODUCT_TYPE_STORABLE)) {

        BigDecimal qty;
        if (realQty) {
          qty = stockMoveLine.getRealQty();
        } else {
          qty = stockMoveLine.getQty();
        }

        this.updateLocations(
            stockMoveLine,
            fromStockLocation,
            toStockLocation,
            stockMoveLine.getProduct(),
            qty,
            fromStatus,
            toStatus,
            lastFutureStockMoveDate,
            stockMoveLine.getTrackingNumber());
        if (toStockLocation.getTypeSelect() != StockLocationRepository.TYPE_VIRTUAL) {
          this.updateAveragePriceLocationLine(toStockLocation, stockMoveLine, fromStatus, toStatus);
        }
        weightedAveragePriceService.computeAvgPriceForProduct(stockMoveLine.getProduct());
      }
    }
  }

  @Override
  public void updateAveragePriceLocationLine(
      StockLocation stockLocation, StockMoveLine stockMoveLine, int fromStatus, int toStatus)
      throws AxelorException {
    StockLocationLine stockLocationLine =
        stockLocationLineService.getOrCreateStockLocationLine(
            stockLocation, stockMoveLine.getProduct());
    if (stockLocationLine == null) {
      return;
    }

    if (toStatus == StockMoveRepository.STATUS_REALIZED) {
      this.computeNewAveragePriceLocationLine(stockLocationLine, stockMoveLine);
    }
  }

  protected void computeNewAveragePriceLocationLine(
      StockLocationLine stockLocationLine, StockMoveLine stockMoveLine) throws AxelorException {
    BigDecimal oldAvgPrice = stockLocationLine.getAvgPrice();
    // avgPrice in stock move line is a bigdecimal but is nullable.
    BigDecimal newQty = stockMoveLine.getRealQty();
    BigDecimal oldQty = stockLocationLine.getCurrentQty().subtract(newQty);
    BigDecimal newPrice =
        stockMoveLine.getWapPrice() != null
            ? stockMoveLine.getWapPrice()
            : stockMoveLine.getCompanyUnitPriceUntaxed();
    BigDecimal newAvgPrice;
    if (oldAvgPrice == null
        || oldQty == null
        || oldAvgPrice.compareTo(BigDecimal.ZERO) == 0
        || oldQty.compareTo(BigDecimal.ZERO) == 0) {
      oldAvgPrice = BigDecimal.ZERO;
      oldQty = BigDecimal.ZERO;
    }

    Unit stockLocationLineUnit = stockLocationLine.getUnit();
    Unit stockMoveLineUnit = stockMoveLine.getUnit();

    if (stockLocationLineUnit != null && !stockLocationLineUnit.equals(stockMoveLineUnit)) {
      newQty =
          unitConversionService.convert(
              stockMoveLineUnit,
              stockLocationLineUnit,
              newQty,
              newQty.scale(),
              stockMoveLine.getProduct());

      newPrice =
          unitConversionService.convert(
              stockMoveLineUnit,
              stockLocationLineUnit,
              newPrice,
              newPrice.scale(),
              stockMoveLine.getProduct());
    }

    log.debug(
        "Old price: {}, Old quantity: {}, New price: {}, New quantity: {}",
        oldAvgPrice,
        oldQty,
        newPrice,
        newQty);
    BigDecimal sum = oldAvgPrice.multiply(oldQty);
    sum = sum.add(newPrice.multiply(newQty));
    BigDecimal denominator = oldQty.add(newQty);
    if (denominator.compareTo(BigDecimal.ZERO) != 0) {
      int scale = appBaseService.getNbDecimalDigitForUnitPrice();
      newAvgPrice = sum.divide(denominator, scale, RoundingMode.HALF_UP);
    } else {
      newAvgPrice = oldAvgPrice;
    }
    stockLocationLineService.updateWap(stockLocationLine, newAvgPrice, stockMoveLine);
  }

  @Override
  public void checkConformitySelection(StockMoveLine stockMoveLine, StockMove stockMove)
      throws AxelorException {
    Product product = stockMoveLine.getProduct();
    // check if the product configuration forces to select a conformity
    if ((product == null) || !product.getControlOnReceipt()) {
      return;
    }
    // check the stock move type
    if (stockMove.getTypeSelect() != StockMoveRepository.TYPE_INCOMING) {
      return;
    }

    // check the conformity
    if (stockMoveLine.getConformitySelect() <= StockMoveLineRepository.CONFORMITY_NONE) {
      throw new AxelorException(
          stockMoveLine,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.STOCK_MOVE_LINE_MUST_FILL_CONFORMITY),
          product.getName());
    }
  }

  @Override
  public void checkConformitySelection(StockMove stockMove) throws AxelorException {

    if (!appStockService.getAppStock().getRequireToFillConformityOnReceipt()) {
      return;
    }

    List<String> productsWithErrors = new ArrayList<>();
    for (StockMoveLine stockMoveLine : stockMove.getStockMoveLineList()) {

      Product product = stockMoveLine.getProduct();

      if (product != null
          && product.getProductTypeSelect().equals(ProductRepository.PRODUCT_TYPE_STORABLE)) {
        try {
          checkConformitySelection(stockMoveLine, stockMove);
        } catch (Exception e) {
          productsWithErrors.add(product.getName());
        }
      }
    }
    if (!productsWithErrors.isEmpty()) {
      String productsWithErrorStr = productsWithErrors.stream().collect(Collectors.joining(", "));
      throw new AxelorException(
          stockMove,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.STOCK_MOVE_LINE_MUST_FILL_CONFORMITY),
          productsWithErrorStr);
    }
  }

  @Override
  public void checkExpirationDates(StockMove stockMove) throws AxelorException {
    List<String> errorList = new ArrayList<>();

    for (StockMoveLine stockMoveLine : stockMove.getStockMoveLineList()) {
      TrackingNumber trackingNumber = stockMoveLine.getTrackingNumber();

      if (trackingNumber == null) {
        continue;
      }

      Product product = trackingNumber.getProduct();

      if (product == null || !product.getCheckExpirationDateAtStockMoveRealization()) {
        continue;
      }

      if (product.getHasWarranty()
              && trackingNumber.getWarrantyExpirationDate().isBefore(appBaseService.getTodayDate())
          || product.getIsPerishable()
              && trackingNumber
                  .getPerishableExpirationDate()
                  .isBefore(appBaseService.getTodayDate())) {
        errorList.add(product.getName());
      }
    }

    if (!errorList.isEmpty()) {
      String errorStr = errorList.stream().collect(Collectors.joining(", "));
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.STOCK_MOVE_LINE_EXPIRED_PRODUCTS),
          errorStr);
    }
  }

  @Override
  public void checkTrackingNumber(StockMove stockMove) throws AxelorException {
    List<String> productsWithErrors = new ArrayList<>();

    for (StockMoveLine stockMoveLine : stockMove.getStockMoveLineList()) {
      if (stockMoveLine.getProduct() == null) {
        continue;
      }

      TrackingNumberConfiguration trackingNumberConfig =
          stockMoveLine.getProduct().getTrackingNumberConfiguration();

      if (stockMoveLine.getProduct() != null
          && trackingNumberConfig != null
          && (trackingNumberConfig.getIsPurchaseTrackingManaged()
              || trackingNumberConfig.getIsProductionTrackingManaged()
              || (trackingNumberConfig.getIsSaleTrackingManaged()
                  && stockMove.getTypeSelect() == StockMoveRepository.TYPE_OUTGOING))
          && stockMoveLine.getTrackingNumber() == null
          && stockMoveLine.getRealQty().compareTo(BigDecimal.ZERO) != 0) {
        if (!productsWithErrors.contains(stockMoveLine.getProduct().getName())) {
          productsWithErrors.add(stockMoveLine.getProduct().getName());
        }
      }
    }

    if (!productsWithErrors.isEmpty()) {
      String productWithErrorsStr = productsWithErrors.stream().collect(Collectors.joining(", "));
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.STOCK_MOVE_LINE_MUST_FILL_TRACKING_NUMBER),
          productWithErrorsStr);
    }
  }

  @Override
  public void updateLocations(
      StockMoveLine stockMoveLine,
      StockLocation fromStockLocation,
      StockLocation toStockLocation,
      Product product,
      BigDecimal qty,
      int fromStatus,
      int toStatus,
      LocalDate lastFutureStockMoveDate,
      TrackingNumber trackingNumber)
      throws AxelorException {
    Unit stockMoveLineUnit = stockMoveLine.getUnit();

    switch (fromStatus) {
      case StockMoveRepository.STATUS_PLANNED:
        stockLocationLineService.updateLocation(
            fromStockLocation,
            product,
            stockMoveLineUnit,
            qty,
            false,
            true,
            true,
            null,
            trackingNumber);
        stockLocationLineService.updateLocation(
            toStockLocation,
            product,
            stockMoveLineUnit,
            qty,
            false,
            true,
            false,
            null,
            trackingNumber);
        break;

      case StockMoveRepository.STATUS_REALIZED:
        stockLocationLineService.updateLocation(
            fromStockLocation,
            product,
            stockMoveLineUnit,
            qty,
            true,
            true,
            true,
            null,
            trackingNumber);
        stockLocationLineService.updateLocation(
            toStockLocation,
            product,
            stockMoveLineUnit,
            qty,
            true,
            true,
            false,
            null,
            trackingNumber);
        break;

      default:
        break;
    }

    switch (toStatus) {
      case StockMoveRepository.STATUS_PLANNED:
        stockLocationLineService.updateLocation(
            fromStockLocation,
            product,
            stockMoveLineUnit,
            qty,
            false,
            true,
            false,
            lastFutureStockMoveDate,
            trackingNumber);
        stockLocationLineService.updateLocation(
            toStockLocation,
            product,
            stockMoveLineUnit,
            qty,
            false,
            true,
            true,
            lastFutureStockMoveDate,
            trackingNumber);
        break;

      case StockMoveRepository.STATUS_REALIZED:
        stockLocationLineService.updateLocation(
            fromStockLocation,
            product,
            stockMoveLineUnit,
            qty,
            true,
            true,
            false,
            null,
            trackingNumber);
        stockLocationLineService.updateLocation(
            toStockLocation,
            product,
            stockMoveLineUnit,
            qty,
            true,
            true,
            true,
            null,
            trackingNumber);
        break;

      default:
        break;
    }
  }

  @Override
  public Unit getStockUnit(StockMoveLine stockMoveLine) {
    Unit stockUnit = stockMoveLine.getUnit();
    if (stockUnit == null && stockMoveLine.getProduct() != null) {
      stockUnit = stockMoveLine.getProduct().getUnit();
    }
    return stockUnit;
  }

  @Override
  public StockMoveLine compute(StockMoveLine stockMoveLine, StockMove stockMove)
      throws AxelorException {
    BigDecimal unitPriceUntaxed = BigDecimal.ZERO;
    BigDecimal companyPurchasePrice = BigDecimal.ZERO;
    if (stockMoveLine.getProduct() != null && stockMove != null) {
      if ((stockMove.getTypeSelect() == StockMoveRepository.TYPE_INCOMING
              && stockMove.getIsReversion())
          || (stockMove.getTypeSelect() == StockMoveRepository.TYPE_OUTGOING
              && !stockMove.getIsReversion())) {
        // customer delivery or customer return
        unitPriceUntaxed = stockMoveLine.getProduct().getSalePrice();
        BigDecimal wapPrice =
            computeFromStockLocation(stockMoveLine, stockMove.getToStockLocation());
        stockMoveLine.setWapPrice(wapPrice);
      } else if ((stockMove.getTypeSelect() == StockMoveRepository.TYPE_OUTGOING
              && stockMove.getIsReversion())
          || (stockMove.getTypeSelect() == StockMoveRepository.TYPE_INCOMING
              && !stockMove.getIsReversion())) {
        // supplier return or supplier delivery
        BigDecimal shippingCoef =
            shippingCoefService.getShippingCoef(
                stockMoveLine.getProduct(),
                stockMove.getPartner(),
                stockMove.getCompany(),
                stockMoveLine.getRealQty());
        companyPurchasePrice = stockMoveLine.getProduct().getPurchasePrice();
        unitPriceUntaxed = companyPurchasePrice.multiply(shippingCoef);
      } else if (stockMove.getTypeSelect() == StockMoveRepository.TYPE_INTERNAL
          && stockMove.getFromStockLocation() != null
          && stockMove.getFromStockLocation().getTypeSelect()
              != StockLocationRepository.TYPE_VIRTUAL) {
        unitPriceUntaxed =
            computeFromStockLocation(stockMoveLine, stockMove.getFromStockLocation());
      } else {
        unitPriceUntaxed = stockMoveLine.getProduct().getCostPrice();
      }
    }
    stockMoveLine.setCompanyPurchasePrice(companyPurchasePrice);
    stockMoveLine.setUnitPriceUntaxed(unitPriceUntaxed);
    stockMoveLine.setUnitPriceTaxed(unitPriceUntaxed);
    stockMoveLine.setCompanyUnitPriceUntaxed(unitPriceUntaxed);
    return stockMoveLine;
  }

  /**
   * Compute the price corresponding to the stock move line in the stock location. The price is the
   * average price in the stock location line with the same product as the stock move line, after
   * converting the unit.
   *
   * @param stockMoveLine a stock move line with a product.
   * @param stockLocation a stock location.
   * @return the computed price.
   * @throws AxelorException if the conversion fails.
   */
  protected BigDecimal computeFromStockLocation(
      StockMoveLine stockMoveLine, StockLocation stockLocation) throws AxelorException {
    Optional<StockLocationLine> stockLocationLine =
        Optional.ofNullable(
            stockLocationLineService.getStockLocationLine(
                stockLocation, stockMoveLine.getProduct()));
    BigDecimal priceFromLocation = BigDecimal.ZERO;
    if (stockLocationLine.isPresent()) {
      priceFromLocation = stockLocationLine.get().getAvgPrice();
      priceFromLocation =
          unitConversionService.convert(
              stockMoveLine.getUnit(),
              getStockUnit(stockMoveLine),
              priceFromLocation,
              priceFromLocation.scale(),
              null);
    }
    return priceFromLocation;
  }

  @Override
  public void storeCustomsCodes(List<StockMoveLine> stockMoveLineList) {
    if (stockMoveLineList == null) {
      return;
    }

    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      Product product = stockMoveLine.getProduct();
      CustomsCodeNomenclature customsCodeNomenclature =
          product != null ? product.getCustomsCodeNomenclature() : null;
      stockMoveLine.setCustomsCodeNomenclature(customsCodeNomenclature);
      stockMoveLine.setCustomsCode(
          customsCodeNomenclature != null ? customsCodeNomenclature.getCode() : null);
    }
  }

  @Override
  public boolean computeFullySpreadOverLogisticalFormLinesFlag(StockMoveLine stockMoveLine) {
    return computeSpreadableQtyOverLogisticalFormLines(stockMoveLine).signum() <= 0;
  }

  @Override
  public BigDecimal computeSpreadableQtyOverLogisticalFormLines(StockMoveLine stockMoveLine) {
    return stockMoveLine != null
        ? computeSpreadableQtyOverLogisticalFormLines(
            stockMoveLine, stockMoveLine.getLogisticalFormLineList())
        : BigDecimal.ZERO;
  }

  @Override
  public BigDecimal computeSpreadableQtyOverLogisticalFormLines(
      StockMoveLine stockMoveLine, LogisticalForm logisticalForm) {

    if (logisticalForm == null && stockMoveLine != null) {
      return computeSpreadableQtyOverLogisticalFormLines(
          stockMoveLine, stockMoveLine.getLogisticalFormLineList());
    }

    List<LogisticalFormLine> updatedLogisticalFormLineList = new ArrayList<>();

    if (stockMoveLine != null && stockMoveLine.getLogisticalFormLineList() != null) {
      for (LogisticalFormLine logisticalFormLine : stockMoveLine.getLogisticalFormLineList()) {
        if (!logisticalForm.equals(logisticalFormLine.getLogisticalForm())) {
          updatedLogisticalFormLineList.add(logisticalFormLine);
        }
      }
    }

    if (logisticalForm.getLogisticalFormLineList() != null) {
      for (LogisticalFormLine logisticalFormLine : logisticalForm.getLogisticalFormLineList()) {
        if (stockMoveLine != null && stockMoveLine.equals(logisticalFormLine.getStockMoveLine())) {
          updatedLogisticalFormLineList.add(logisticalFormLine);
        }
      }
    }

    return computeSpreadableQtyOverLogisticalFormLines(
        stockMoveLine, updatedLogisticalFormLineList);
  }

  protected BigDecimal computeSpreadableQtyOverLogisticalFormLines(
      StockMoveLine stockMoveLine, List<LogisticalFormLine> logisticalFormLineList) {

    if (stockMoveLine == null) {
      return null;
    }

    if (stockMoveLine.getProduct() == null
        || !ProductRepository.PRODUCT_TYPE_STORABLE.equals(
            stockMoveLine.getProduct().getProductTypeSelect())) {
      return BigDecimal.ZERO;
    }

    if (logisticalFormLineList == null) {
      return stockMoveLine.getRealQty();
    }

    BigDecimal qtySpreadOverLogisticalMoveLines =
        logisticalFormLineList
            .stream()
            .map(
                logisticalFormLine ->
                    logisticalFormLine.getQty() != null
                        ? logisticalFormLine.getQty()
                        : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return stockMoveLine.getRealQty().subtract(qtySpreadOverLogisticalMoveLines);
  }

  @Override
  public void setProductInfo(StockMove stockMove, StockMoveLine stockMoveLine, Company company)
      throws AxelorException {
    Preconditions.checkNotNull(stockMoveLine);
    Preconditions.checkNotNull(company);
    Product product = stockMoveLine.getProduct();

    if (product == null) {
      return;
    }

    stockMoveLine.setUnit(product.getUnit());
    stockMoveLine.setProductName(product.getName());

    if (appStockService.getAppStock().getIsEnabledProductDescriptionCopy()) {
      stockMoveLine.setDescription(product.getDescription());
    }

    if (appBaseService.getAppBase().getManageProductVariants()) {
      stockMoveLine.setProductModel(product.getParentProduct());
    }

    stockMoveLine.setCountryOfOrigin(product.getCountryOfOrigin());

    BigDecimal netMass = this.computeNetMass(stockMove, stockMoveLine, company);
    stockMoveLine.setNetMass(netMass);
  }

  public BigDecimal computeNetMass(
      StockMove stockMove, StockMoveLine stockMoveLine, Company company) throws AxelorException {

    BigDecimal netMass;
    Product product = stockMoveLine.getProduct();
    Unit startUnit;
    Unit endUnit;

    if (product == null
        || !product.getProductTypeSelect().equals(ProductRepository.PRODUCT_TYPE_STORABLE)) {
      return null;
    }

    startUnit = product.getMassUnit();
    if (startUnit == null) {

      if (stockMove != null && !checkMassesRequired(stockMove, stockMoveLine)) {
        return product.getNetMass();
      }

      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.MISSING_PRODUCT_MASS_UNIT),
          product.getName());
    }

    if (company != null
        && company.getStockConfig() != null
        && company.getStockConfig().getCustomsMassUnit() != null) {
      endUnit = company.getStockConfig().getCustomsMassUnit();
    } else {
      endUnit = startUnit;
    }

    netMass = unitConversionService.convert(startUnit, endUnit, product.getNetMass(), 10, product);
    return netMass;
  }

  @Override
  public boolean checkMassesRequired(StockMove stockMove, StockMoveLine stockMoveLine) {
    Address fromAddress = stockMoveToolService.getFromAddress(stockMove);
    Address toAddress = stockMoveToolService.getToAddress(stockMove);

    Country fromCountry = fromAddress != null ? fromAddress.getAddressL7Country() : null;
    Country toCountry = toAddress != null ? toAddress.getAddressL7Country() : null;

    return fromCountry != null
        && toCountry != null
        && !fromCountry.equals(toCountry)
        && stockMoveLine.getProduct() != null
        && fromCountry.getEconomicArea() != null
        && fromCountry.getEconomicArea().equals(toCountry.getEconomicArea())
        && stockMoveLine.getProduct().getUsedInDEB();
  }

  @Override
  @Transactional
  public void splitStockMoveLineByTrackingNumber(
      StockMoveLine stockMoveLine, List<LinkedHashMap<String, Object>> trackingNumbers) {
    //    boolean draft = true;
    //    if (stockMoveLine.getStockMove() != null
    //        && stockMoveLine.getStockMove().getStatusSelect() ==
    // StockMoveRepository.STATUS_PLANNED) {
    //      draft = false;
    //    }
    BigDecimal totalSplitQty = BigDecimal.ZERO;
    for (LinkedHashMap<String, Object> trackingNumberItem : trackingNumbers) {
      BigDecimal counter = new BigDecimal(trackingNumberItem.get("counter").toString());
      if (counter.compareTo(BigDecimal.ZERO) == 0) {
        continue;
      }
      totalSplitQty = totalSplitQty.add(counter);

      TrackingNumber trackingNumber =
          trackingNumberRepo
              .all()
              .filter(
                  "self.product.id = ?1 and self.trackingNumberSeq = ?2",
                  stockMoveLine.getProduct(),
                  trackingNumberItem.get("trackingNumberSeq").toString())
              .fetchOne();

      if (trackingNumber == null) {
        trackingNumber = new TrackingNumber();
        trackingNumber.setCounter(counter);
        trackingNumber.setTrackingNumberSeq(trackingNumberItem.get("trackingNumberSeq").toString());
        if (trackingNumberItem.get("warrantyExpirationDate") != null) {
          trackingNumber.setWarrantyExpirationDate(
              LocalDate.parse(trackingNumberItem.get("warrantyExpirationDate").toString()));
        }
        if (trackingNumberItem.get("perishableExpirationDate") != null) {
          trackingNumber.setPerishableExpirationDate(
              LocalDate.parse(trackingNumberItem.get("perishableExpirationDate").toString()));
        }
        trackingNumber.setProduct(stockMoveLine.getProduct());
      }

      StockMoveLine newStockMoveLine = stockMoveLineRepository.copy(stockMoveLine, true);
      //      if (draft) {
      newStockMoveLine.setQty(counter);
      //      } else {
      newStockMoveLine.setRealQty(counter);
      //      }
      newStockMoveLine.setTrackingNumber(trackingNumber);
      newStockMoveLine.setStockMove(stockMoveLine.getStockMove());
      stockMoveLineRepository.save(newStockMoveLine);
    }

    if (totalSplitQty.compareTo(stockMoveLine.getQty()) < 0) {
      BigDecimal remainingQty = stockMoveLine.getQty().subtract(totalSplitQty);
      stockMoveLine.setQty(remainingQty);
      stockMoveLine.setRealQty(remainingQty);
      stockMoveLine.setTrackingNumber(null);
      stockMoveLine.setStockMove(stockMoveLine.getStockMove());
    } else {
      stockMoveLineRepository.remove(stockMoveLine);
    }
  }

  @Override
  public void updateAvailableQty(StockMoveLine stockMoveLine, StockLocation stockLocation) {
    BigDecimal availableQty = BigDecimal.ZERO;
    BigDecimal availableQtyForProduct = BigDecimal.ZERO;

    if (stockMoveLine.getProduct() != null) {
      if (stockMoveLine.getProduct().getTrackingNumberConfiguration() != null) {

        if (stockMoveLine.getTrackingNumber() != null) {
          StockLocationLine stockLocationLine =
              stockLocationLineService.getDetailLocationLine(
                  stockLocation, stockMoveLine.getProduct(), stockMoveLine.getTrackingNumber());

          if (stockLocationLine != null) {
            availableQty = stockLocationLine.getCurrentQty();
          }
        }

        if (availableQty.compareTo(stockMoveLine.getRealQty()) < 0) {
          StockLocationLine stockLocationLineForProduct =
              stockLocationLineService.getStockLocationLine(
                  stockLocation, stockMoveLine.getProduct());

          if (stockLocationLineForProduct != null) {
            availableQtyForProduct = stockLocationLineForProduct.getCurrentQty();
          }
        }
      } else {
        StockLocationLine stockLocationLine =
            stockLocationLineService.getStockLocationLine(
                stockLocation, stockMoveLine.getProduct());

        if (stockLocationLine != null) {
          availableQty = stockLocationLine.getCurrentQty();
        }
      }
    }
    stockMoveLine.setAvailableQty(availableQty);
    stockMoveLine.setAvailableQtyForProduct(availableQtyForProduct);
  }

  @Override
  public String createDomainForProduct(StockMoveLine stockMoveLine, StockMove stockMove) {
    String domain = "self.isModel = false AND self.isShippingCostsProduct = false";
    if (stockMoveLine.getProductModel() != null) {
      domain += " AND self.parentProduct.id = " + stockMoveLine.getProductModel().getId();
    }
    if (stockMoveLine.getFilterOnAvailableProducts()
        && stockMove.getFromStockLocation().getTypeSelect() != 3) {
      domain +=
          " AND self.id in (select sll.product.id from StockLocation sl inner join sl.stockLocationLineList sll WHERE sl.id = "
              + stockMove.getFromStockLocation().getId()
              + " AND sll.currentQty > 0)";
    }
    return domain;
  }

  @Override
  public void setAvailableStatus(StockMoveLine stockMoveLine) {
    if (stockMoveLine.getStockMove() != null) {
      this.updateAvailableQty(stockMoveLine, stockMoveLine.getStockMove().getFromStockLocation());
    }
    if (stockMoveLine.getProduct() != null) {
      BigDecimal availableQty = stockMoveLine.getAvailableQty();
      BigDecimal availableQtyForProduct = stockMoveLine.getAvailableQtyForProduct();
      BigDecimal realQty = stockMoveLine.getRealQty();
      if (availableQty.compareTo(realQty) >= 0 || !stockMoveLine.getProduct().getStockManaged()) {
        stockMoveLine.setAvailableStatus(I18n.get("Available"));
        stockMoveLine.setAvailableStatusSelect(StockMoveLineRepository.STATUS_AVAILABLE);
      } else if (availableQtyForProduct.compareTo(realQty) >= 0) {
        stockMoveLine.setAvailableStatus(I18n.get("Av. for product"));
        stockMoveLine.setAvailableStatusSelect(
            StockMoveLineRepository.STATUS_AVAILABLE_FOR_PRODUCT);
      } else if (availableQty.compareTo(realQty) < 0
          && availableQtyForProduct.compareTo(realQty) < 0) {
        BigDecimal missingQty = BigDecimal.ZERO;
        if (stockMoveLine.getProduct().getTrackingNumberConfiguration() != null) {
          missingQty = availableQtyForProduct.subtract(realQty);
        } else {
          missingQty = availableQty.subtract(realQty);
        }
        stockMoveLine.setAvailableStatus(I18n.get("Missing") + " (" + missingQty + ")");
        stockMoveLine.setAvailableStatusSelect(StockMoveLineRepository.STATUS_MISSING);
      }
    }
  }

  public List<TrackingNumber> getAvailableTrackingNumbers(
      StockMoveLine stockMoveLine, StockMove stockMove) {
    List<TrackingNumber> trackingNumbers = null;
    String domain =
        "self.product.id = "
            + stockMoveLine.getProduct().getId()
            + " AND self.id in (select stockLocationLine.trackingNumber.id from StockLocationLine stockLocationLine join StockLocation sl on sl.id = stockLocationLine.detailsStockLocation.id WHERE sl.id = "
            + stockMove.getFromStockLocation().getId()
            + " )";
    trackingNumbers = trackingNumberRepo.all().filter(domain).fetch();
    return trackingNumbers;
  }

  public void fillRealizeWapPrice(StockMoveLine stockMoveLine) {
    StockLocation stockLocation = stockMoveLine.getStockMove().getFromStockLocation();
    Optional<StockLocationLine> stockLocationLineOpt =
        Optional.ofNullable(
            stockLocationLineService.getStockLocationLine(
                stockLocation, stockMoveLine.getProduct()));

    stockLocationLineOpt.ifPresent(
        stockLocationLine -> stockMoveLine.setWapPrice(stockLocationLine.getAvgPrice()));
  }
}
