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

import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.apps.supplychain.db.SupplyChainConfig;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ReservedQtyServiceImpl implements ReservedQtyService {

  protected StockLocationLineService stockLocationLineService;
  protected StockMoveLineRepository stockMoveLineRepository;
  protected UnitConversionService unitConversionService;
  protected SupplyChainConfigService supplychainConfigService;

  @Inject
  public ReservedQtyServiceImpl(
      StockLocationLineService stockLocationLineService,
      StockMoveLineRepository stockMoveLineRepository,
      UnitConversionService unitConversionService,
      SupplyChainConfigService supplyChainConfigService) {
    this.stockLocationLineService = stockLocationLineService;
    this.stockMoveLineRepository = stockMoveLineRepository;
    this.unitConversionService = unitConversionService;
    this.supplychainConfigService = supplyChainConfigService;
  }

  @Override
  public void updateReservedQuantity(StockMove stockMove, int status) throws AxelorException {
    if (stockMove.getStockMoveLineList() != null) {
      for (StockMoveLine stockMoveLine : stockMove.getStockMoveLineList()) {
        BigDecimal qty = stockMoveLine.getRealQty();
        BigDecimal requestedReservedQty = stockMoveLine.getRequestedReservedQty();
        Product product = stockMoveLine.getProduct();
        if (product == null) {
          continue;
        }

        updateRequestedQuantityInLocations(
            stockMoveLine,
            stockMove.getFromStockLocation(),
            stockMove.getToStockLocation(),
            stockMoveLine.getProduct(),
            qty,
            requestedReservedQty,
            status);
      }
    }
  }

  @Override
  public void updateRequestedQuantityInLocations(
      StockMoveLine stockMoveLine,
      StockLocation fromStockLocation,
      StockLocation toStockLocation,
      Product product,
      BigDecimal qty,
      BigDecimal requestedReservedQty,
      int toStatus)
      throws AxelorException {
    if (fromStockLocation.getTypeSelect() != StockLocationRepository.TYPE_VIRTUAL) {
      updateRequestedQuantityInFromStockLocation(
          stockMoveLine, fromStockLocation, product, toStatus, requestedReservedQty);
    }
    if (toStockLocation.getTypeSelect() != StockLocationRepository.TYPE_VIRTUAL) {
      updateRequestedQuantityInToStockLocation(
          stockMoveLine, toStockLocation, product, toStatus, qty);
    }
  }

  @Override
  public void updateRequestedQuantityInFromStockLocation(
      StockMoveLine stockMoveLine,
      StockLocation stockLocation,
      Product product,
      int toStatus,
      BigDecimal requestedReservedQty)
      throws AxelorException {
    Unit stockMoveLineUnit = stockMoveLine.getUnit();

    StockLocationLine stockLocationLine =
        stockLocationLineService.getStockLocationLine(stockLocation, product);
    if (stockLocationLine == null) {
      return;
    }
    Unit stockLocationLineUnit = stockLocationLine.getUnit();
    // the quantity that will be allocated in stock location line
    BigDecimal realReservedQty;

    // the quantity that will be allocated in stock move line
    BigDecimal realReservedStockMoveQty;

    // if we cancel, subtract the quantity using the previously allocated quantity.
    if (toStatus == StockMoveRepository.STATUS_CANCELED
        || toStatus == StockMoveRepository.STATUS_REALIZED) {
      realReservedStockMoveQty = stockMoveLine.getReservedQty();

      // convert the quantity for stock location line

      realReservedQty =
          convertUnitWithProduct(
              stockMoveLineUnit,
              stockLocationLineUnit,
              realReservedStockMoveQty,
              stockMoveLine.getProduct());

      // update allocated quantity in stock location line
      stockLocationLine.setReservedQty(
          stockLocationLine.getReservedQty().subtract(realReservedQty));

      // reallocate quantity in other stock move lines
      if (isReallocatingQtyOnCancel(stockMoveLine)) {
        reallocateQty(stockMoveLine, stockLocation, stockLocationLine, product, realReservedQty);
      }

      // no more reserved qty in stock move and sale order lines
      updateReservedQuantityFromStockMoveLine(
          stockMoveLine, product, stockMoveLine.getReservedQty().negate());
    } else {
      BigDecimal requestedReservedQtyInLocation =
          convertUnitWithProduct(
              stockMoveLineUnit, stockLocationLine.getUnit(), requestedReservedQty, product);
      realReservedQty = computeRealReservedQty(stockLocationLine, requestedReservedQtyInLocation);
      // convert back the quantity for the stock move line
      realReservedStockMoveQty =
          convertUnitWithProduct(
              stockLocationLineUnit,
              stockMoveLineUnit,
              realReservedQty,
              stockMoveLine.getProduct());
      updateReservedQuantityFromStockMoveLine(stockMoveLine, product, realReservedStockMoveQty);
      stockLocationLine.setReservedQty(stockLocationLine.getReservedQty().add(realReservedQty));
    }

    checkReservedQtyStocks(stockLocationLine, toStatus);
  }

  /**
   * Check in the stock move for cancel reason and return the config in cancel reason.
   *
   * @param stockMoveLine
   * @return the value of the boolean field on cancel reason if found else false.
   */
  protected boolean isReallocatingQtyOnCancel(StockMoveLine stockMoveLine) {
    return Optional.of(stockMoveLine)
        .map(StockMoveLine::getStockMove)
        .map(StockMove::getCancelReason)
        .map(CancelReason::getCancelQuantityAllocation)
        .orElse(false);
  }

  @Override
  public void updateRequestedQuantityInToStockLocation(
      StockMoveLine stockMoveLine,
      StockLocation stockLocation,
      Product product,
      int toStatus,
      BigDecimal qty)
      throws AxelorException {
    StockLocationLine stockLocationLine =
        stockLocationLineService.getStockLocationLine(stockLocation, product);
    if (stockLocationLine == null) {
      return;
    }
    Company company = stockLocationLine.getStockLocation().getCompany();
    SupplyChainConfig supplyChainConfig = supplychainConfigService.getSupplyChainConfig(company);
    if (toStatus == StockMoveRepository.STATUS_REALIZED
        && supplyChainConfig.getAutoAllocateOnReceipt()) {
      reallocateQty(stockMoveLine, stockLocation, stockLocationLine, product, qty);
    }
    checkReservedQtyStocks(stockLocationLine, toStatus);
  }

  /**
   * Reallocate quantity in stock location line after entry into storage.
   *
   * @param stockMoveLine
   * @param stockLocation
   * @param stockLocationLine
   * @param product
   * @param qty the quantity in stock move line unit.
   * @throws AxelorException
   */
  protected void reallocateQty(
      StockMoveLine stockMoveLine,
      StockLocation stockLocation,
      StockLocationLine stockLocationLine,
      Product product,
      BigDecimal qty)
      throws AxelorException {

    Unit stockMoveLineUnit = stockMoveLine.getUnit();
    Unit stockLocationLineUnit = stockLocationLine.getUnit();

    BigDecimal stockLocationQty =
        convertUnitWithProduct(stockMoveLineUnit, stockLocationLineUnit, qty, product);
    // the quantity that will be allocated in stock location line
    BigDecimal realReservedQty;

    // the quantity that will be allocated in stock move line
    BigDecimal realReservedStockMoveQty;
    BigDecimal leftToAllocate =
        stockLocationLine.getRequestedReservedQty().subtract(stockLocationLine.getReservedQty());
    realReservedQty = stockLocationQty.min(leftToAllocate);

    realReservedStockMoveQty =
        convertUnitWithProduct(stockLocationLineUnit, stockMoveLineUnit, realReservedQty, product);
    stockLocationLine.setReservedQty(stockLocationLine.getReservedQty().add(realReservedQty));
    allocateReservedQuantityInSaleOrderLines(
        realReservedStockMoveQty, stockLocation, product, stockLocationLineUnit);
  }

  @Override
  public void allocateReservedQuantityInSaleOrderLines(
      BigDecimal qtyToAllocate,
      StockLocation stockLocation,
      Product product,
      Unit stockLocationLineUnit)
      throws AxelorException {
    List<StockMoveLine> stockMoveLineListToAllocate =
        stockMoveLineRepository
            .all()
            .filter(
                "self.stockMove.fromStockLocation.id = :stockLocationId "
                    + "AND self.product.id = :productId "
                    + "AND self.stockMove.statusSelect = :planned "
                    + "AND self.stockMove.reservationDateTime IS NOT NULL "
                    + "AND self.reservedQty < self.requestedReservedQty")
            .bind("stockLocationId", stockLocation.getId())
            .bind("productId", product.getId())
            .bind("planned", StockMoveRepository.STATUS_PLANNED)
            .fetch();
    stockMoveLineListToAllocate.sort(
        Comparator.comparing(
            stockMoveLine -> stockMoveLine.getStockMove().getReservationDateTime()));
    BigDecimal leftQtyToAllocate = qtyToAllocate;
    for (StockMoveLine stockMoveLine : stockMoveLineListToAllocate) {
      BigDecimal neededQtyToAllocate =
          stockMoveLine.getRequestedReservedQty().subtract(stockMoveLine.getReservedQty());
      BigDecimal allocatedQty = leftQtyToAllocate.min(neededQtyToAllocate);

      BigDecimal allocatedStockMoveQty =
          convertUnitWithProduct(
              stockLocationLineUnit, stockMoveLine.getUnit(), allocatedQty, product);

      // update reserved qty in stock move line and sale order line
      updateReservedQuantityFromStockMoveLine(stockMoveLine, product, allocatedStockMoveQty);
      // update left qty to allocate
      leftQtyToAllocate = leftQtyToAllocate.subtract(allocatedQty);
    }
  }

  @Override
  public void updateReservedQuantityFromStockMoveLine(
      StockMoveLine stockMoveLine, Product product, BigDecimal reservedQtyToAdd)
      throws AxelorException {
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    stockMoveLine.setReservedQty(stockMoveLine.getReservedQty().add(reservedQtyToAdd));
    if (saleOrderLine != null) {
      BigDecimal soLineReservedQtyToAdd =
          convertUnitWithProduct(
              stockMoveLine.getUnit(), saleOrderLine.getUnit(), reservedQtyToAdd, product);
      saleOrderLine.setReservedQty(saleOrderLine.getReservedQty().add(soLineReservedQtyToAdd));
    }
  }

  @Override
  public void updateReservedQuantityInStockMoveLineFromSaleOrderLine(
      SaleOrderLine saleOrderLine, Product product, BigDecimal newReservedQty)
      throws AxelorException {
    saleOrderLine.setReservedQty(newReservedQty);

    List<StockMoveLine> stockMoveLineList = getPlannedStockMoveLines(saleOrderLine);
    BigDecimal allocatedQty = saleOrderLine.getReservedQty();
    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      BigDecimal stockMoveAllocatedQty =
          convertUnitWithProduct(
              saleOrderLine.getUnit(), stockMoveLine.getUnit(), allocatedQty, product);
      BigDecimal reservedQtyInStockMoveLine =
          stockMoveLine.getRequestedReservedQty().min(stockMoveAllocatedQty);
      stockMoveLine.setReservedQty(reservedQtyInStockMoveLine);
      BigDecimal saleOrderReservedQtyInStockMoveLine =
          convertUnitWithProduct(
              stockMoveLine.getUnit(),
              saleOrderLine.getUnit(),
              reservedQtyInStockMoveLine,
              product);
      allocatedQty = allocatedQty.subtract(saleOrderReservedQtyInStockMoveLine);
    }
  }

  @Override
  public void updateRequestedReservedQuantityInStockMoveLines(
      SaleOrderLine saleOrderLine, Product product, BigDecimal newReservedQty)
      throws AxelorException {

    saleOrderLine.setRequestedReservedQty(newReservedQty);
    List<StockMoveLine> stockMoveLineList = getPlannedStockMoveLines(saleOrderLine);
    BigDecimal allocatedRequestedQty = saleOrderLine.getRequestedReservedQty();
    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      BigDecimal stockMoveRequestedQty =
          convertUnitWithProduct(
              saleOrderLine.getUnit(), stockMoveLine.getUnit(), allocatedRequestedQty, product);
      BigDecimal requestedQtyInStockMoveLine =
          stockMoveLine.getRealQty().min(stockMoveRequestedQty);
      stockMoveLine.setRequestedReservedQty(requestedQtyInStockMoveLine);
      BigDecimal saleOrderRequestedQtyInStockMoveLine =
          convertUnitWithProduct(
              stockMoveLine.getUnit(),
              saleOrderLine.getUnit(),
              requestedQtyInStockMoveLine,
              product);
      allocatedRequestedQty = allocatedRequestedQty.subtract(saleOrderRequestedQtyInStockMoveLine);
    }
  }

  protected List<StockMoveLine> getPlannedStockMoveLines(SaleOrderLine saleOrderLine) {
    return stockMoveLineRepository
        .all()
        .filter(
            "self.saleOrderLine.id = :saleOrderLineId "
                + "AND self.stockMove.statusSelect = :planned")
        .bind("saleOrderLineId", saleOrderLine.getId())
        .bind("planned", StockMoveRepository.STATUS_PLANNED)
        .fetch();
  }

  /**
   * Allocated qty cannot be greater than available qty.
   *
   * @param stockLocationLine
   * @throws AxelorException
   */
  protected void checkReservedQtyStocks(StockLocationLine stockLocationLine, int toStatus)
      throws AxelorException {

    if (((toStatus == StockMoveRepository.STATUS_REALIZED)
            || toStatus == StockMoveRepository.STATUS_CANCELED)
        && stockLocationLine.getReservedQty().compareTo(stockLocationLine.getCurrentQty()) > 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LOCATION_LINE_NOT_ENOUGH_AVAILABLE_QTY));
    }
  }

  @Override
  public BigDecimal computeRealReservedQty(
      StockLocationLine stockLocationLine, BigDecimal requestedReservedQty) {

    BigDecimal qtyLeftToBeAllocated =
        stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
    return qtyLeftToBeAllocated.min(requestedReservedQty);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void updateReservedQty(SaleOrderLine saleOrderLine, BigDecimal newReservedQty)
      throws AxelorException {
    StockMoveLine stockMoveLine = getPlannedStockMoveLine(saleOrderLine);

    if (stockMoveLine == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_NO_STOCK_MOVE));
    }

    // update requested reserved qty
    if (newReservedQty.compareTo(saleOrderLine.getRequestedReservedQty()) > 0) {
      updateRequestedReservedQty(saleOrderLine, newReservedQty);
    }

    StockLocationLine stockLocationLine =
        stockLocationLineService.getStockLocationLine(
            stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
    BigDecimal availableQtyToBeReserved =
        stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
    BigDecimal diffReservedQuantity = newReservedQty.subtract(saleOrderLine.getReservedQty());
    Product product = stockMoveLine.getProduct();
    BigDecimal diffReservedQuantityLocation =
        convertUnitWithProduct(
            saleOrderLine.getUnit(), stockLocationLine.getUnit(), diffReservedQuantity, product);
    if (availableQtyToBeReserved.compareTo(diffReservedQuantityLocation) < 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_QTY_NOT_AVAILABLE));
    }
    // update in stock move line and sale order line
    updateReservedQuantityInStockMoveLineFromSaleOrderLine(
        saleOrderLine, stockMoveLine.getProduct(), newReservedQty);

    // update in stock location line
    stockLocationLine.setReservedQty(
        stockLocationLine.getReservedQty().add(diffReservedQuantityLocation));
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void updateRequestedReservedQty(SaleOrderLine saleOrderLine, BigDecimal newReservedQty)
      throws AxelorException {

    StockMoveLine stockMoveLine = getPlannedStockMoveLine(saleOrderLine);

    if (stockMoveLine == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_NO_STOCK_MOVE));
    }

    BigDecimal diffReservedQuantity =
        newReservedQty.subtract(saleOrderLine.getRequestedReservedQty());

    // update in stock move line and sale order line
    updateRequestedReservedQuantityInStockMoveLines(
        saleOrderLine, stockMoveLine.getProduct(), newReservedQty);

    StockLocationLine stockLocationLine =
        stockLocationLineService.getStockLocationLine(
            stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());

    Product product = stockMoveLine.getProduct();
    // update in stock location line
    BigDecimal diffReservedQuantityLocation =
        convertUnitWithProduct(
            stockMoveLine.getUnit(), stockLocationLine.getUnit(), diffReservedQuantity, product);
    stockLocationLine.setRequestedReservedQty(
        stockLocationLine.getRequestedReservedQty().add(diffReservedQuantityLocation));

    // update requested reserved qty
    if (newReservedQty.compareTo(saleOrderLine.getReservedQty()) < 0) {
      updateReservedQty(saleOrderLine, newReservedQty);
    }
  }

  protected StockMoveLine getPlannedStockMoveLine(SaleOrderLine saleOrderLine) {
    return stockMoveLineRepository
        .all()
        .filter(
            "self.saleOrderLine = :saleOrderLine " + "AND self.stockMove.statusSelect = :planned")
        .bind("saleOrderLine", saleOrderLine)
        .bind("planned", StockMoveRepository.STATUS_PLANNED)
        .fetchOne();
  }

  /** Convert but with null check. Return start value if one unit is null. */
  private BigDecimal convertUnitWithProduct(
      Unit startUnit, Unit endUnit, BigDecimal qtyToConvert, Product product)
      throws AxelorException {
    if (startUnit != null && !startUnit.equals(endUnit)) {
      return unitConversionService.convert(
          startUnit, endUnit, qtyToConvert, qtyToConvert.scale(), product);
    } else {
      return qtyToConvert;
    }
  }
}
