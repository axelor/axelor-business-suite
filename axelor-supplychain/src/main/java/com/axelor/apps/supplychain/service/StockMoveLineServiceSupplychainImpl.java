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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.TrackingNumber;
import com.axelor.apps.stock.db.TrackingNumberConfiguration;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.apps.stock.service.StockMoveLineServiceImpl;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.TrackingNumberService;
import com.axelor.apps.stock.service.app.AppStockService;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.google.inject.servlet.RequestScoped;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RequestScoped
public class StockMoveLineServiceSupplychainImpl extends StockMoveLineServiceImpl
    implements StockMoveLineServiceSupplychain {

  protected AccountManagementService accountManagementService;

  protected PriceListService priceListService;

  @Inject
  public StockMoveLineServiceSupplychainImpl(
      TrackingNumberService trackingNumberService,
      AppBaseService appBaseService,
      AppStockService appStockService,
      StockMoveService stockMoveService,
      AccountManagementService accountManagementService,
      PriceListService priceListService,
      UnitConversionService unitConversionService,
      StockMoveLineRepository stockMoveLineRepository,
      StockLocationLineService stockLocationLineService) {
    super(
        trackingNumberService,
        appBaseService,
        appStockService,
        stockMoveService,
        stockMoveLineRepository,
        stockLocationLineService,
        unitConversionService);
    this.accountManagementService = accountManagementService;
    this.priceListService = priceListService;
  }

  @Override
  public StockMoveLine createStockMoveLine(
      Product product,
      String productName,
      String description,
      BigDecimal quantity,
      BigDecimal reservedQty,
      BigDecimal unitPrice,
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
              unit,
              stockMove,
              taxed,
              taxRate);
      stockMoveLine.setReservedQty(reservedQty);
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
          unit,
          stockMove,
          null);
    }
  }

  @Override
  public StockMoveLine compute(StockMoveLine stockMoveLine, StockMove stockMove)
      throws AxelorException {

    // the case when stockMove is null is made in super.
    if (stockMove == null) {
      return super.compute(stockMoveLine, null);
    }

    // if this is a pack do not compute price
    if (stockMoveLine.getProduct() == null
        || (stockMoveLine.getLineTypeSelect() != null
            && stockMoveLine.getLineTypeSelect() == StockMoveLineRepository.TYPE_PACK)) {
      return stockMoveLine;
    }

    if (stockMove.getOriginId() != null && stockMove.getOriginId() != 0L) {
      // the stock move comes from a sale or purchase order, we take the price from the order.
      stockMoveLine = computeFromOrder(stockMoveLine, stockMove);
    } else {
      stockMoveLine = super.compute(stockMoveLine, stockMove);
    }
    return stockMoveLine;
  }

  protected StockMoveLine computeFromOrder(StockMoveLine stockMoveLine, StockMove stockMove)
      throws AxelorException {
    BigDecimal unitPriceUntaxed = stockMoveLine.getUnitPriceUntaxed();
    BigDecimal unitPriceTaxed = stockMoveLine.getUnitPriceTaxed();
    Unit orderUnit = null;
    if (StockMoveRepository.ORIGIN_SALE_ORDER.equals(stockMove.getOriginTypeSelect())) {
      SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
      if (saleOrderLine == null) {
        // log the exception
        TraceBackService.trace(
            new AxelorException(
                TraceBackRepository.TYPE_TECHNICAL,
                IExceptionMessage.STOCK_MOVE_MISSING_SALE_ORDER,
                stockMove.getOriginId(),
                stockMove.getName()));
      } else {
        unitPriceUntaxed = saleOrderLine.getPrice();
        unitPriceTaxed = saleOrderLine.getInTaxPrice();
        orderUnit = saleOrderLine.getUnit();
      }
    } else if (StockMoveRepository.ORIGIN_PURCHASE_ORDER.equals(stockMove.getOriginTypeSelect())) {
      PurchaseOrderLine purchaseOrderLine = stockMoveLine.getPurchaseOrderLine();
      if (purchaseOrderLine == null) {
        // log the exception
        TraceBackService.trace(
            new AxelorException(
                TraceBackRepository.TYPE_TECHNICAL,
                IExceptionMessage.STOCK_MOVE_MISSING_PURCHASE_ORDER,
                stockMove.getOriginId(),
                stockMove.getName()));
      } else {
        unitPriceUntaxed = purchaseOrderLine.getPrice();
        unitPriceTaxed = purchaseOrderLine.getInTaxPrice();
        orderUnit = purchaseOrderLine.getUnit();
      }
    }

    stockMoveLine.setUnitPriceUntaxed(unitPriceUntaxed);
    stockMoveLine.setUnitPriceTaxed(unitPriceTaxed);

    Unit stockUnit = getStockUnit(stockMoveLine);
    return convertUnitPrice(stockMoveLine, orderUnit, stockUnit);
  }

  protected StockMoveLine convertUnitPrice(StockMoveLine stockMoveLine, Unit fromUnit, Unit toUnit)
      throws AxelorException {
    // convert units
    if (toUnit != null && fromUnit != null) {
      BigDecimal unitPriceUntaxed =
          unitConversionService.convert(
              fromUnit,
              toUnit,
              stockMoveLine.getUnitPriceUntaxed(),
              appBaseService.getNbDecimalDigitForUnitPrice(),
              null);
      BigDecimal unitPriceTaxed =
          unitConversionService.convert(
              fromUnit,
              toUnit,
              stockMoveLine.getUnitPriceTaxed(),
              appBaseService.getNbDecimalDigitForUnitPrice(),
              null);
      stockMoveLine.setUnitPriceUntaxed(unitPriceUntaxed);
      stockMoveLine.setUnitPriceTaxed(unitPriceTaxed);
    }
    return stockMoveLine;
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void updateReservedQty(StockMoveLine stockMoveLine, BigDecimal reservedQty)
      throws AxelorException {
    StockMove stockMove = stockMoveLine.getStockMove();
    int statusSelect = stockMove.getStatusSelect();
    if (statusSelect == StockMoveRepository.STATUS_PLANNED
        || statusSelect == StockMoveRepository.STATUS_REALIZED) {
      StockMoveService stockMoveService = Beans.get(StockMoveService.class);
      stockMoveService.cancel(stockMoveLine.getStockMove());
      stockMoveLine.setReservedQty(reservedQty);
      stockMoveService.goBackToDraft(stockMove);
      stockMoveService.plan(stockMove);
      if (statusSelect == StockMoveRepository.STATUS_REALIZED) {
        stockMoveService.realize(stockMove);
      }
    } else {
      stockMoveLine.setReservedQty(stockMoveLine.getReservedQty());
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
      TrackingNumber trackingNumber,
      BigDecimal reservedQty)
      throws AxelorException {
    BigDecimal realReservedQty = stockMoveLine.getReservedQty();
    Unit productUnit = product.getUnit();
    Unit stockMoveLineUnit = stockMoveLine.getUnit();
    if (productUnit != null && !productUnit.equals(stockMoveLineUnit)) {
      qty =
          unitConversionService.convert(
              stockMoveLineUnit, productUnit, qty, qty.scale(), stockMoveLine.getProduct());
      realReservedQty =
          unitConversionService.convert(
              stockMoveLineUnit,
              productUnit,
              realReservedQty,
              realReservedQty.scale(),
              stockMoveLine.getProduct());
    }
    super.updateLocations(
        stockMoveLine,
        fromStockLocation,
        toStockLocation,
        product,
        qty,
        fromStatus,
        toStatus,
        lastFutureStockMoveDate,
        trackingNumber,
        realReservedQty);
  }

  @Override
  public StockMoveLine splitStockMoveLine(
      StockMoveLine stockMoveLine, BigDecimal qty, TrackingNumber trackingNumber)
      throws AxelorException {

    StockMoveLine newStockMoveLine = super.splitStockMoveLine(stockMoveLine, qty, trackingNumber);

    BigDecimal reservedQtyAfterSplit =
        BigDecimal.ZERO.max(stockMoveLine.getReservedQty().subtract(qty));
    BigDecimal reservedQtyInNewLine = stockMoveLine.getReservedQty().min(qty);
    stockMoveLine.setReservedQty(reservedQtyAfterSplit);
    newStockMoveLine.setReservedQty(reservedQtyInNewLine);
    newStockMoveLine.setPurchaseOrderLine(stockMoveLine.getPurchaseOrderLine());
    newStockMoveLine.setSaleOrderLine(stockMoveLine.getSaleOrderLine());
    return newStockMoveLine;
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
            availableQty =
                stockLocationLine
                    .getCurrentQty()
                    .add(stockMoveLine.getReservedQty())
                    .subtract(stockLocationLine.getReservedQty());
          }
        }

        if (availableQty.compareTo(stockMoveLine.getRealQty()) < 0) {
          StockLocationLine stockLocationLineForProduct =
              stockLocationLineService.getStockLocationLine(
                  stockLocation, stockMoveLine.getProduct());

          if (stockLocationLineForProduct != null) {
            availableQtyForProduct =
                stockLocationLineForProduct
                    .getCurrentQty()
                    .add(stockMoveLine.getReservedQty())
                    .subtract(stockLocationLineForProduct.getReservedQty());
          }
        }
      } else {
        StockLocationLine stockLocationLine =
            stockLocationLineService.getStockLocationLine(
                stockLocation, stockMoveLine.getProduct());

        if (stockLocationLine != null) {
          availableQty =
              stockLocationLine
                  .getCurrentQty()
                  .add(stockMoveLine.getReservedQty())
                  .subtract(stockLocationLine.getReservedQty());
        }
      }
    }
    stockMoveLine.setAvailableQty(availableQty);
    stockMoveLine.setAvailableQtyForProduct(availableQtyForProduct);
  }

  @Override
  public StockMoveLine getMergedStockMoveLine(List<StockMoveLine> stockMoveLineList)
      throws AxelorException {

    if (stockMoveLineList == null || stockMoveLineList.isEmpty()) {
      return null;
    }

    if (stockMoveLineList.size() == 1) {
      return stockMoveLineList.get(0);
    }

    SaleOrderLine saleOrderLine = stockMoveLineList.get(0).getSaleOrderLine();
    PurchaseOrderLine purchaseOrderLine = stockMoveLineList.get(0).getPurchaseOrderLine();

    Product product;
    String productName;
    String description;
    BigDecimal quantity = BigDecimal.ZERO;
    Unit unit;

    if (saleOrderLine != null) {
      product = saleOrderLine.getProduct();
      productName = saleOrderLine.getProductName();
      description = saleOrderLine.getDescription();
      unit = saleOrderLine.getUnit();

    } else if (purchaseOrderLine != null) {
      product = purchaseOrderLine.getProduct();
      productName = purchaseOrderLine.getProductName();
      description = purchaseOrderLine.getDescription();
      unit = purchaseOrderLine.getUnit();

    } else {
      return null; // shouldn't ever happen or you misused this function
    }

    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      quantity = quantity.add(stockMoveLine.getRealQty());
    }

    StockMoveLine generatedStockMoveLine =
        createStockMoveLine(
            product, productName, description, quantity, null, null, unit, null, null);

    generatedStockMoveLine.setSaleOrderLine(saleOrderLine);
    generatedStockMoveLine.setPurchaseOrderLine(purchaseOrderLine);
    return generatedStockMoveLine;
  }
}
