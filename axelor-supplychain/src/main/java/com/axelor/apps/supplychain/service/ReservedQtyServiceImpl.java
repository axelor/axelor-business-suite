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

import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
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
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** This is the main implementation for {@link ReservedQtyService}. */
public class ReservedQtyServiceImpl implements ReservedQtyService {

  protected StockLocationLineService stockLocationLineService;
  protected StockMoveLineRepository stockMoveLineRepository;
  protected UnitConversionService unitConversionService;
  protected SupplyChainConfigService supplychainConfigService;
  protected AppBaseService appBaseService;

  @Inject
  public ReservedQtyServiceImpl(
      StockLocationLineService stockLocationLineService,
      StockMoveLineRepository stockMoveLineRepository,
      UnitConversionService unitConversionService,
      SupplyChainConfigService supplyChainConfigService,
      AppBaseService appBaseService) {
    this.stockLocationLineService = stockLocationLineService;
    this.stockMoveLineRepository = stockMoveLineRepository;
    this.unitConversionService = unitConversionService;
    this.supplychainConfigService = supplyChainConfigService;
    this.appBaseService = appBaseService;
  }

  @Override
  public void updateReservedQuantity(StockMove stockMove, int status) throws AxelorException {
    List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();
    if (stockMoveLineList != null) {
      stockMoveLineList =
          stockMoveLineList
              .stream()
              .filter(
                  smLine -> smLine.getProduct() != null && smLine.getProduct().getStockManaged())
              .collect(Collectors.toList());
      // check quantities in stock move lines
      for (StockMoveLine stockMoveLine : stockMoveLineList) {
        if (status == StockMoveRepository.STATUS_PLANNED) {
          changeRequestedQtyLowerThanQty(stockMoveLine);
        }
        checkRequestedAndReservedQty(stockMoveLine);
      }
      if (status == StockMoveRepository.STATUS_REALIZED) {
        consolidateReservedQtyInStockMoveLineByProduct(stockMove);
      }
      stockMoveLineList.sort(Comparator.comparing(StockMoveLine::getId));
      for (StockMoveLine stockMoveLine : stockMoveLineList) {
        BigDecimal qty = stockMoveLine.getRealQty();
        // requested quantity is quantity requested is the line subtracted by the quantity already
        // allocated
        BigDecimal requestedReservedQty =
            stockMoveLine.getRequestedReservedQty().subtract(stockMoveLine.getReservedQty());
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

  /**
   * On planning, we want the requested quantity to be equal or lower to the quantity of the line.
   * So, if the requested quantity is greater than the quantity, we change it to be equal.
   *
   * @param stockMoveLine
   * @throws AxelorException
   */
  protected void changeRequestedQtyLowerThanQty(StockMoveLine stockMoveLine)
      throws AxelorException {
    BigDecimal qty = stockMoveLine.getRealQty().max(BigDecimal.ZERO);
    BigDecimal requestedReservedQty = stockMoveLine.getRequestedReservedQty();
    if (requestedReservedQty.compareTo(qty) > 0) {
      Product product = stockMoveLine.getProduct();
      BigDecimal diffRequestedQty = requestedReservedQty.subtract(qty);
      stockMoveLine.setRequestedReservedQty(qty);
      // update in stock location line
      StockLocationLine stockLocationLine =
          stockLocationLineService.getOrCreateStockLocationLine(
              stockMoveLine.getStockMove().getFromStockLocation(), product);
      BigDecimal diffRequestedQuantityLocation =
          convertUnitWithProduct(
              stockMoveLine.getUnit(), stockLocationLine.getUnit(), diffRequestedQty, product);
      stockLocationLine.setRequestedReservedQty(
          stockLocationLine.getRequestedReservedQty().add(diffRequestedQuantityLocation));
    }
  }

  /**
   * Check value of requested and reserved qty in stock move line.
   *
   * @param stockMoveLine the stock move line to be checked
   * @throws AxelorException if the quantities are negative or superior to the planned qty.
   */
  protected void checkRequestedAndReservedQty(StockMoveLine stockMoveLine) throws AxelorException {
    BigDecimal plannedQty = stockMoveLine.getQty().max(BigDecimal.ZERO);
    BigDecimal requestedReservedQty = stockMoveLine.getRequestedReservedQty();
    BigDecimal reservedQty = stockMoveLine.getReservedQty();

    String stockMoveLineSeq =
        stockMoveLine.getStockMove() == null
            ? stockMoveLine.getId().toString()
            : stockMoveLine.getStockMove().getStockMoveSeq() + "-" + stockMoveLine.getSequence();

    if (reservedQty.signum() < 0 || requestedReservedQty.signum() < 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_RESERVATION_QTY_NEGATIVE));
    }
    if (requestedReservedQty.compareTo(plannedQty) > 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_REQUESTED_QTY_TOO_HIGH),
          stockMoveLineSeq);
    }
    if (reservedQty.compareTo(plannedQty) > 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_ALLOCATED_QTY_TOO_HIGH),
          stockMoveLineSeq);
    }
  }

  @Override
  public void consolidateReservedQtyInStockMoveLineByProduct(StockMove stockMove) {
    if (stockMove.getStockMoveLineList() == null) {
      return;
    }
    List<Product> productList =
        stockMove
            .getStockMoveLineList()
            .stream()
            .map(StockMoveLine::getProduct)
            .filter(Objects::nonNull)
            .filter(Product::getStockManaged)
            .distinct()
            .collect(Collectors.toList());
    for (Product product : productList) {
      if (product != null) {
        List<StockMoveLine> stockMoveLineListToConsolidate =
            stockMove
                .getStockMoveLineList()
                .stream()
                .filter(stockMoveLine1 -> product.equals(stockMoveLine1.getProduct()))
                .collect(Collectors.toList());
        if (stockMoveLineListToConsolidate.size() > 1) {
          stockMoveLineListToConsolidate.sort(Comparator.comparing(StockMoveLine::getId));
          BigDecimal reservedQtySum =
              stockMoveLineListToConsolidate
                  .stream()
                  .map(StockMoveLine::getReservedQty)
                  .reduce(BigDecimal::add)
                  .orElse(BigDecimal.ZERO);
          stockMoveLineListToConsolidate.forEach(
              toConsolidateStockMoveLine ->
                  toConsolidateStockMoveLine.setReservedQty(BigDecimal.ZERO));
          stockMoveLineListToConsolidate.get(0).setReservedQty(reservedQtySum);
        }
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
    if (product == null || !product.getStockManaged()) {
      return;
    }
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

      // reallocate quantity in other stock move lines
      if (isReallocatingQtyOnCancel(stockMoveLine)) {
        reallocateQty(stockMoveLine, stockLocation, stockLocationLine, product, realReservedQty);
      }

      // no more reserved qty in stock move and sale order lines
      updateReservedQuantityFromStockMoveLine(
          stockMoveLine, product, stockMoveLine.getReservedQty().negate());

      // update requested quantity in sale order line
      SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
      if (saleOrderLine != null) {
        // requested quantity should never be below delivered quantity.
        if (toStatus == StockMoveRepository.STATUS_REALIZED) {
          saleOrderLine.setRequestedReservedQty(
              saleOrderLine.getRequestedReservedQty().max(saleOrderLine.getDeliveredQty()));
        } else if (!saleOrderLine.getIsQtyRequested()) {
          // if we cancel and do not want to request quantity, the requested quantity become the new
          // delivered quantity.
          saleOrderLine.setRequestedReservedQty(saleOrderLine.getDeliveredQty());
        }
      }

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

      // reallocate quantity in other stock move lines
      if (supplychainConfigService
          .getSupplyChainConfig(stockLocation.getCompany())
          .getAutoAllocateOnAllocation()) {
        BigDecimal availableQuantityInLocation =
            stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
        availableQuantityInLocation =
            convertUnitWithProduct(
                stockLocationLineUnit, stockMoveLineUnit, availableQuantityInLocation, product);
        BigDecimal qtyRemainingToAllocate =
            availableQuantityInLocation.subtract(realReservedStockMoveQty);
        reallocateQty(
            stockMoveLine, stockLocation, stockLocationLine, product, qtyRemainingToAllocate);
      }
    }

    updateReservedQty(stockLocationLine);
    updateRequestedReservedQty(stockLocationLine);
    checkReservedQtyStocks(stockLocationLine, stockMoveLine, toStatus);
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
    if (product == null || !product.getStockManaged()) {
      return;
    }
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
    updateRequestedReservedQty(stockLocationLine);
    checkReservedQtyStocks(stockLocationLine, stockMoveLine, toStatus);
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
    BigDecimal leftToAllocate =
        stockLocationLine.getRequestedReservedQty().subtract(stockLocationLine.getReservedQty());
    realReservedQty = stockLocationQty.min(leftToAllocate);

    allocateReservedQuantityInSaleOrderLines(
        realReservedQty, stockLocation, product, stockLocationLineUnit, Optional.of(stockMoveLine));
    updateReservedQty(stockLocationLine);
  }

  @Override
  public BigDecimal allocateReservedQuantityInSaleOrderLines(
      BigDecimal qtyToAllocate,
      StockLocation stockLocation,
      Product product,
      Unit stockLocationLineUnit)
      throws AxelorException {
    if (product == null || !product.getStockManaged()) {
      return BigDecimal.ZERO;
    }
    return allocateReservedQuantityInSaleOrderLines(
        qtyToAllocate, stockLocation, product, stockLocationLineUnit, Optional.empty());
  }

  /**
   * The new parameter allocated stock move line is used if we are allocating a stock move line.
   * This method will reallocate the lines with the same stock move (and the same product) before
   * other stock move lines.
   *
   * <p>We are using an optional because in the basic use of the method, the argument is empty.
   */
  protected BigDecimal allocateReservedQuantityInSaleOrderLines(
      BigDecimal qtyToAllocate,
      StockLocation stockLocation,
      Product product,
      Unit stockLocationLineUnit,
      Optional<StockMoveLine> allocatedStockMoveLine)
      throws AxelorException {
    List<StockMoveLine> stockMoveLineListToAllocate =
        stockMoveLineRepository
            .all()
            .filter(
                "self.stockMove.fromStockLocation.id = :stockLocationId "
                    + "AND self.product.id = :productId "
                    + "AND self.stockMove.statusSelect = :planned "
                    + "AND self.reservationDateTime IS NOT NULL "
                    + "AND self.reservedQty < self.requestedReservedQty")
            .bind("stockLocationId", stockLocation.getId())
            .bind("productId", product.getId())
            .bind("planned", StockMoveRepository.STATUS_PLANNED)
            .order("reservationDateTime")
            .order("stockMove.estimatedDate")
            .fetch();

    // put stock move lines with the same stock move on the beginning of the list.
    allocatedStockMoveLine.ifPresent(
        stockMoveLine ->
            stockMoveLineListToAllocate.sort(
                // Note: this comparator imposes orderings that are inconsistent with equals.
                (sml1, sml2) -> {
                  if (sml1.getStockMove().equals(sml2.getStockMove())) {
                    return 0;
                  } else if (sml1.getStockMove().equals(stockMoveLine.getStockMove())) {
                    return -1;
                  } else if (sml2.getStockMove().equals(stockMoveLine.getStockMove())) {
                    return 1;
                  } else {
                    return 0;
                  }
                }));
    BigDecimal leftQtyToAllocate = qtyToAllocate;
    for (StockMoveLine stockMoveLine : stockMoveLineListToAllocate) {
      BigDecimal leftQtyToAllocateStockMove =
          convertUnitWithProduct(
              stockLocationLineUnit, stockMoveLine.getUnit(), leftQtyToAllocate, product);
      BigDecimal neededQtyToAllocate =
          stockMoveLine.getRequestedReservedQty().subtract(stockMoveLine.getReservedQty());
      BigDecimal allocatedStockMoveQty = leftQtyToAllocateStockMove.min(neededQtyToAllocate);

      BigDecimal allocatedQty =
          convertUnitWithProduct(
              stockMoveLine.getUnit(), stockLocationLineUnit, allocatedStockMoveQty, product);

      // update reserved qty in stock move line and sale order line
      updateReservedQuantityFromStockMoveLine(stockMoveLine, product, allocatedStockMoveQty);
      // update left qty to allocate
      leftQtyToAllocate = leftQtyToAllocate.subtract(allocatedQty);
    }

    return qtyToAllocate.subtract(leftQtyToAllocate);
  }

  @Override
  public void updateReservedQuantityFromStockMoveLine(
      StockMoveLine stockMoveLine, Product product, BigDecimal reservedQtyToAdd)
      throws AxelorException {
    if (product == null || !product.getStockManaged()) {
      return;
    }
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    stockMoveLine.setReservedQty(stockMoveLine.getReservedQty().add(reservedQtyToAdd));
    if (saleOrderLine != null) {
      updateReservedQty(saleOrderLine);
    }
  }

  @Override
  public void updateReservedQuantityInStockMoveLineFromSaleOrderLine(
      SaleOrderLine saleOrderLine, Product product, BigDecimal newReservedQty)
      throws AxelorException {
    if (product == null || !product.getStockManaged()) {
      return;
    }

    List<StockMoveLine> stockMoveLineList = getPlannedStockMoveLines(saleOrderLine);
    BigDecimal allocatedQty = newReservedQty;
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
    updateReservedQty(saleOrderLine);
  }

  @Override
  public BigDecimal updateRequestedReservedQuantityInStockMoveLines(
      SaleOrderLine saleOrderLine, Product product, BigDecimal newReservedQty)
      throws AxelorException {
    if (product == null || !product.getStockManaged()) {
      return BigDecimal.ZERO;
    }
    List<StockMoveLine> stockMoveLineList = getPlannedStockMoveLines(saleOrderLine);
    BigDecimal deliveredQty = saleOrderLine.getDeliveredQty();
    BigDecimal allocatedRequestedQty = newReservedQty.subtract(deliveredQty);
    if (allocatedRequestedQty.signum() < 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_REQUESTED_QTY_TOO_LOW));
    }
    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      BigDecimal stockMoveRequestedQty =
          convertUnitWithProduct(
              saleOrderLine.getUnit(), stockMoveLine.getUnit(), allocatedRequestedQty, product);
      BigDecimal requestedQtyInStockMoveLine = stockMoveLine.getQty().min(stockMoveRequestedQty);
      stockMoveLine.setRequestedReservedQty(requestedQtyInStockMoveLine);
      BigDecimal saleOrderRequestedQtyInStockMoveLine =
          convertUnitWithProduct(
              stockMoveLine.getUnit(),
              saleOrderLine.getUnit(),
              requestedQtyInStockMoveLine,
              product);
      allocatedRequestedQty = allocatedRequestedQty.subtract(saleOrderRequestedQtyInStockMoveLine);
    }
    saleOrderLine.setRequestedReservedQty(newReservedQty.subtract(allocatedRequestedQty));
    return saleOrderLine.getRequestedReservedQty().subtract(deliveredQty);
  }

  protected List<StockMoveLine> getPlannedStockMoveLines(SaleOrderLine saleOrderLine) {
    return stockMoveLineRepository
        .all()
        .filter(
            "self.saleOrderLine.id = :saleOrderLineId "
                + "AND self.stockMove.statusSelect = :planned")
        .bind("saleOrderLineId", saleOrderLine.getId())
        .bind("planned", StockMoveRepository.STATUS_PLANNED)
        .order("id")
        .fetch();
  }

  /**
   * Allocated qty cannot be greater than available qty.
   *
   * @param stockLocationLine
   * @param stockMoveLine
   * @throws AxelorException
   */
  protected void checkReservedQtyStocks(
      StockLocationLine stockLocationLine, StockMoveLine stockMoveLine, int toStatus)
      throws AxelorException {

    if (((toStatus == StockMoveRepository.STATUS_REALIZED)
            || toStatus == StockMoveRepository.STATUS_CANCELED)
        && stockLocationLine.getReservedQty().compareTo(stockLocationLine.getCurrentQty()) > 0) {
      BigDecimal convertedAvailableQtyInStockMove =
          convertUnitWithProduct(
              stockMoveLine.getUnit(),
              stockLocationLine.getUnit(),
              stockMoveLine.getRealQty(),
              stockLocationLine.getProduct());
      BigDecimal convertedReservedQtyInStockMove =
          convertUnitWithProduct(
              stockMoveLine.getUnit(),
              stockLocationLine.getUnit(),
              stockMoveLine.getReservedQty(),
              stockLocationLine.getProduct());

      BigDecimal availableQty =
          convertedAvailableQtyInStockMove
              .add(stockLocationLine.getCurrentQty())
              .subtract(convertedReservedQtyInStockMove.add(stockLocationLine.getReservedQty()));
      BigDecimal neededQty =
          convertedAvailableQtyInStockMove.subtract(convertedReservedQtyInStockMove);
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LOCATION_LINE_NOT_ENOUGH_AVAILABLE_QTY),
          stockLocationLine.getProduct().getFullName(),
          availableQty,
          neededQty);
    }
  }

  @Override
  public BigDecimal computeRealReservedQty(
      StockLocationLine stockLocationLine, BigDecimal requestedReservedQty) {

    BigDecimal qtyLeftToBeAllocated =
        stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
    return qtyLeftToBeAllocated.min(requestedReservedQty).max(BigDecimal.ZERO);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateReservedQty(SaleOrderLine saleOrderLine, BigDecimal newReservedQty)
      throws AxelorException {
    if (saleOrderLine.getProduct() == null || !saleOrderLine.getProduct().getStockManaged()) {
      return;
    }
    StockMoveLine stockMoveLine = getPlannedStockMoveLine(saleOrderLine);

    checkBeforeUpdatingQties(stockMoveLine, newReservedQty);
    if (Beans.get(AppSupplychainService.class)
        .getAppSupplychain()
        .getBlockDeallocationOnAvailabilityRequest()) {
      checkAvailabilityRequest(stockMoveLine, newReservedQty, false);
    }

    BigDecimal newRequestedReservedQty = newReservedQty.add(saleOrderLine.getDeliveredQty());
    // update requested reserved qty
    if (newRequestedReservedQty.compareTo(saleOrderLine.getRequestedReservedQty()) > 0
        && newReservedQty.compareTo(BigDecimal.ZERO) > 0) {
      requestQty(saleOrderLine);
    }

    StockLocationLine stockLocationLine =
        stockLocationLineService.getOrCreateStockLocationLine(
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
    updateReservedQty(stockLocationLine);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateRequestedReservedQty(SaleOrderLine saleOrderLine, BigDecimal newReservedQty)
      throws AxelorException {
    if (saleOrderLine.getProduct() == null || !saleOrderLine.getProduct().getStockManaged()) {
      return;
    }

    StockMoveLine stockMoveLine = getPlannedStockMoveLine(saleOrderLine);

    if (stockMoveLine == null) {
      // only change requested quantity in sale order line
      saleOrderLine.setRequestedReservedQty(newReservedQty);
      return;
    }

    checkBeforeUpdatingQties(stockMoveLine, newReservedQty);
    if (Beans.get(AppSupplychainService.class)
        .getAppSupplychain()
        .getBlockDeallocationOnAvailabilityRequest()) {
      checkAvailabilityRequest(stockMoveLine, newReservedQty, true);
    }

    BigDecimal diffReservedQuantity =
        newReservedQty.subtract(saleOrderLine.getRequestedReservedQty());

    // update in stock move line and sale order line
    BigDecimal newAllocatedQty =
        updateRequestedReservedQuantityInStockMoveLines(
            saleOrderLine, stockMoveLine.getProduct(), newReservedQty);

    StockLocationLine stockLocationLine =
        stockLocationLineService.getOrCreateStockLocationLine(
            stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());

    Product product = stockMoveLine.getProduct();
    // update in stock location line
    BigDecimal diffReservedQuantityLocation =
        convertUnitWithProduct(
            stockMoveLine.getUnit(), stockLocationLine.getUnit(), diffReservedQuantity, product);
    stockLocationLine.setRequestedReservedQty(
        stockLocationLine.getRequestedReservedQty().add(diffReservedQuantityLocation));

    // update reserved qty
    if (newAllocatedQty.compareTo(saleOrderLine.getReservedQty()) < 0) {
      updateReservedQty(saleOrderLine, newAllocatedQty);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateReservedQty(StockMoveLine stockMoveLine, BigDecimal newReservedQty)
      throws AxelorException {
    StockLocationLine stockLocationLine =
        stockLocationLineService.getOrCreateStockLocationLine(
            stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
    updateReservedQty(stockLocationLine, stockMoveLine, newReservedQty);
  }

  protected void updateReservedQty(
      StockLocationLine stockLocationLine, StockMoveLine stockMoveLine, BigDecimal newReservedQty)
      throws AxelorException {
    if (stockMoveLine.getProduct() == null || !stockMoveLine.getProduct().getStockManaged()) {
      return;
    }
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    if (saleOrderLine != null) {
      updateReservedQty(saleOrderLine, newReservedQty);
    } else {
      checkBeforeUpdatingQties(stockMoveLine, newReservedQty);
      if (Beans.get(AppSupplychainService.class)
          .getAppSupplychain()
          .getBlockDeallocationOnAvailabilityRequest()) {
        checkAvailabilityRequest(stockMoveLine, newReservedQty, false);
      }
      if (stockMoveLine.getRequestedReservedQty().compareTo(newReservedQty) < 0
          && newReservedQty.compareTo(BigDecimal.ZERO) > 0) {
        updateRequestedReservedQty(stockLocationLine, stockMoveLine, newReservedQty);
        requestQty(stockMoveLine);
      }

      BigDecimal availableQtyToBeReserved =
          stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
      BigDecimal diffReservedQuantity = newReservedQty.subtract(stockMoveLine.getReservedQty());
      Product product = stockMoveLine.getProduct();
      BigDecimal diffReservedQuantityLocation =
          convertUnitWithProduct(
              stockMoveLine.getUnit(), stockLocationLine.getUnit(), diffReservedQuantity, product);
      if (availableQtyToBeReserved.compareTo(diffReservedQuantityLocation) < 0) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.SALE_ORDER_LINE_QTY_NOT_AVAILABLE));
      }
      stockMoveLine.setReservedQty(newReservedQty);

      // update in stock location line
      updateReservedQty(stockLocationLine);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateRequestedReservedQty(StockMoveLine stockMoveLine, BigDecimal newReservedQty)
      throws AxelorException {
    StockLocationLine stockLocationLine =
        stockLocationLineService.getOrCreateStockLocationLine(
            stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
    updateRequestedReservedQty(stockLocationLine, stockMoveLine, newReservedQty);
  }

  protected void updateRequestedReservedQty(
      StockLocationLine stockLocationLine, StockMoveLine stockMoveLine, BigDecimal newReservedQty)
      throws AxelorException {

    BigDecimal diffReservedQuantity =
        newReservedQty.subtract(stockMoveLine.getRequestedReservedQty());
    BigDecimal diffReservedQuantityLocation =
        convertUnitWithProduct(
            stockMoveLine.getUnit(),
            stockLocationLine.getUnit(),
            diffReservedQuantity,
            stockMoveLine.getProduct());
    stockLocationLine.setRequestedReservedQty(
        stockLocationLine.getRequestedReservedQty().add(diffReservedQuantityLocation));

    stockMoveLine.setRequestedReservedQty(newReservedQty);
  }

  /**
   * StockMoveLine cannot be null and quantity cannot be negative. Throws {@link AxelorException} if
   * these conditions are false.
   */
  protected void checkBeforeUpdatingQties(StockMoveLine stockMoveLine, BigDecimal qty)
      throws AxelorException {
    if (stockMoveLine == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_NO_STOCK_MOVE));
    }
    if (qty.signum() < 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_RESERVATION_QTY_NEGATIVE));
    }
  }

  /**
   * If the stock move is planned and with an availability request, we cannot lower its quantity.
   *
   * @param stockMoveLine a stock move line.
   * @param qty the quantity that can be requested or reserved.
   * @param isRequested whether the quantity is requested or reserved.
   * @throws AxelorException if we try to change the quantity of a stock move with availability
   *     request equals to true.
   */
  protected void checkAvailabilityRequest(
      StockMoveLine stockMoveLine, BigDecimal qty, boolean isRequested) throws AxelorException {
    BigDecimal stockMoveLineQty =
        isRequested ? stockMoveLine.getRequestedReservedQty() : stockMoveLine.getReservedQty();
    if (stockMoveLine.getStockMove().getAvailabilityRequest()
        && stockMoveLineQty.compareTo(qty) > 0) {
      throw new AxelorException(
          stockMoveLine.getStockMove(),
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_AVAILABILITY_REQUEST));
    }
  }

  @Override
  public void deallocateStockMoveLineAfterSplit(
      StockMoveLine stockMoveLine, BigDecimal amountToDeallocate) throws AxelorException {

    if (stockMoveLine.getProduct() == null || !stockMoveLine.getProduct().getStockManaged()) {
      return;
    }
    // deallocate in sale order line
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    if (saleOrderLine != null) {
      updateReservedQty(saleOrderLine);
    }
    // deallocate in stock location line
    if (stockMoveLine.getStockMove() != null) {
      StockLocationLine stockLocationLine =
          stockLocationLineService.getStockLocationLine(
              stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
      if (stockLocationLine != null) {
        updateReservedQty(stockLocationLine);
      }
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
  protected BigDecimal convertUnitWithProduct(
      Unit startUnit, Unit endUnit, BigDecimal qtyToConvert, Product product)
      throws AxelorException {
    if (startUnit != null && !startUnit.equals(endUnit)) {
      return unitConversionService.convert(
          startUnit, endUnit, qtyToConvert, qtyToConvert.scale(), product);
    } else {
      return qtyToConvert;
    }
  }

  @Override
  public void updateRequestedReservedQty(StockLocationLine stockLocationLine)
      throws AxelorException {
    // compute from stock move lines
    List<StockMoveLine> stockMoveLineList =
        stockMoveLineRepository
            .all()
            .filter(
                "self.product.id = :productId "
                    + "AND self.stockMove.fromStockLocation.id = :stockLocationId "
                    + "AND self.stockMove.statusSelect = :planned")
            .bind("productId", stockLocationLine.getProduct().getId())
            .bind("stockLocationId", stockLocationLine.getStockLocation().getId())
            .bind("planned", StockMoveRepository.STATUS_PLANNED)
            .fetch();
    BigDecimal requestedReservedQty = BigDecimal.ZERO;
    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      requestedReservedQty =
          requestedReservedQty.add(
              convertUnitWithProduct(
                  stockMoveLine.getUnit(),
                  stockLocationLine.getUnit(),
                  stockMoveLine.getRequestedReservedQty(),
                  stockLocationLine.getProduct()));
    }
    stockLocationLine.setRequestedReservedQty(requestedReservedQty);
  }

  @Override
  public void updateReservedQty(SaleOrderLine saleOrderLine) throws AxelorException {
    // compute from stock move lines
    List<StockMoveLine> stockMoveLineList =
        stockMoveLineRepository
            .all()
            .filter(
                "self.saleOrderLine.id = :saleOrderLineId "
                    + "AND self.stockMove.statusSelect = :planned")
            .bind("saleOrderLineId", saleOrderLine.getId())
            .bind("planned", StockMoveRepository.STATUS_PLANNED)
            .fetch();
    BigDecimal reservedQty = BigDecimal.ZERO;
    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      reservedQty =
          reservedQty.add(
              convertUnitWithProduct(
                  stockMoveLine.getUnit(),
                  saleOrderLine.getUnit(),
                  stockMoveLine.getReservedQty(),
                  saleOrderLine.getProduct()));
    }
    saleOrderLine.setReservedQty(reservedQty);
  }

  @Override
  public void updateReservedQty(StockLocationLine stockLocationLine) throws AxelorException {
    // compute from stock move lines
    List<StockMoveLine> stockMoveLineList =
        stockMoveLineRepository
            .all()
            .filter(
                "self.product.id = :productId "
                    + "AND self.stockMove.fromStockLocation.id = :stockLocationId "
                    + "AND self.stockMove.statusSelect = :planned")
            .bind("productId", stockLocationLine.getProduct().getId())
            .bind("stockLocationId", stockLocationLine.getStockLocation().getId())
            .bind("planned", StockMoveRepository.STATUS_PLANNED)
            .fetch();
    BigDecimal reservedQty = BigDecimal.ZERO;
    for (StockMoveLine stockMoveLine : stockMoveLineList) {
      reservedQty =
          reservedQty.add(
              convertUnitWithProduct(
                  stockMoveLine.getUnit(),
                  stockLocationLine.getUnit(),
                  stockMoveLine.getReservedQty(),
                  stockLocationLine.getProduct()));
    }
    stockLocationLine.setReservedQty(reservedQty);
  }

  @Override
  public void allocateAll(SaleOrderLine saleOrderLine) throws AxelorException {
    if (saleOrderLine.getProduct() == null || !saleOrderLine.getProduct().getStockManaged()) {
      return;
    }
    // request the maximum quantity
    requestQty(saleOrderLine);
    StockMoveLine stockMoveLine = getPlannedStockMoveLine(saleOrderLine);

    if (stockMoveLine == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_NO_STOCK_MOVE));
    }
    // search for the maximum quantity that can be allocated.
    StockLocationLine stockLocationLine =
        stockLocationLineService.getOrCreateStockLocationLine(
            stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
    BigDecimal availableQtyToBeReserved =
        stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
    Product product = stockMoveLine.getProduct();
    BigDecimal availableQtyToBeReservedSaleOrderLine =
        convertUnitWithProduct(
                saleOrderLine.getUnit(),
                stockLocationLine.getUnit(),
                availableQtyToBeReserved,
                product)
            .add(saleOrderLine.getReservedQty());
    BigDecimal qtyThatWillBeAllocated =
        saleOrderLine.getQty().min(availableQtyToBeReservedSaleOrderLine);

    // allocate it
    if (qtyThatWillBeAllocated.compareTo(saleOrderLine.getReservedQty()) > 0) {
      updateReservedQty(saleOrderLine, qtyThatWillBeAllocated);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void allocateAll(StockMoveLine stockMoveLine) throws AxelorException {
    requestQty(stockMoveLine);
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    if (saleOrderLine != null) {
      allocateAll(saleOrderLine);
    } else {
      // search for the maximum quantity that can be allocated in the stock move line.
      StockLocationLine stockLocationLine =
          stockLocationLineService.getOrCreateStockLocationLine(
              stockMoveLine.getStockMove().getFromStockLocation(), stockMoveLine.getProduct());
      BigDecimal availableQtyToBeReserved =
          stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
      Product product = stockMoveLine.getProduct();
      BigDecimal availableQtyToBeReservedStockMoveLine =
          convertUnitWithProduct(
                  stockMoveLine.getUnit(),
                  stockLocationLine.getUnit(),
                  availableQtyToBeReserved,
                  product)
              .add(stockMoveLine.getReservedQty());
      BigDecimal qtyThatWillBeAllocated =
          stockMoveLine.getQty().min(availableQtyToBeReservedStockMoveLine);

      // allocate it
      if (qtyThatWillBeAllocated.compareTo(stockMoveLine.getReservedQty()) > 0) {
        updateReservedQty(stockLocationLine, stockMoveLine, qtyThatWillBeAllocated);
      }
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void requestQty(SaleOrderLine saleOrderLine) throws AxelorException {
    if (saleOrderLine.getProduct() == null || !saleOrderLine.getProduct().getStockManaged()) {
      return;
    }
    saleOrderLine.setIsQtyRequested(true);
    if (saleOrderLine.getQty().signum() < 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SALE_ORDER_LINE_REQUEST_QTY_NEGATIVE));
    }
    StockMoveLine stockMoveLine = getPlannedStockMoveLine(saleOrderLine);
    if (stockMoveLine != null) {
      stockMoveLine.setIsQtyRequested(true);
    }
    this.updateRequestedReservedQty(saleOrderLine, saleOrderLine.getQty());
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void requestQty(StockMoveLine stockMoveLine) throws AxelorException {
    if (stockMoveLine.getProduct() == null || !stockMoveLine.getProduct().getStockManaged()) {
      return;
    }
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    if (saleOrderLine != null) {
      requestQty(saleOrderLine);
    } else {
      stockMoveLine.setReservationDateTime(appBaseService.getTodayDateTime().toLocalDateTime());
      stockMoveLine.setIsQtyRequested(true);
      if (stockMoveLine.getQty().signum() < 0) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.SALE_ORDER_LINE_REQUEST_QTY_NEGATIVE));
      }
      this.updateRequestedReservedQty(stockMoveLine, stockMoveLine.getQty());
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void cancelReservation(SaleOrderLine saleOrderLine) throws AxelorException {
    if (saleOrderLine.getProduct() == null || !saleOrderLine.getProduct().getStockManaged()) {
      return;
    }
    saleOrderLine.setIsQtyRequested(false);
    StockMoveLine stockMoveLine = getPlannedStockMoveLine(saleOrderLine);
    if (stockMoveLine != null) {
      stockMoveLine.setIsQtyRequested(false);
    }
    this.updateRequestedReservedQty(saleOrderLine, saleOrderLine.getDeliveredQty());
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void cancelReservation(StockMoveLine stockMoveLine) throws AxelorException {
    if (stockMoveLine.getProduct() == null || !stockMoveLine.getProduct().getStockManaged()) {
      return;
    }
    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    if (saleOrderLine != null) {
      cancelReservation(saleOrderLine);
    } else {
      stockMoveLine.setIsQtyRequested(false);
      stockMoveLine.setReservationDateTime(null);
      this.updateRequestedReservedQty(stockMoveLine, BigDecimal.ZERO);
    }
  }
}
