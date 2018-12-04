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

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.PartnerStockSettings;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockMoveLineService;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.supplychain.db.SupplyChainConfig;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class SaleOrderStockServiceImpl implements SaleOrderStockService {

  protected StockMoveService stockMoveService;
  protected StockMoveLineService stockMoveLineService;
  protected StockConfigService stockConfigService;
  protected UnitConversionService unitConversionService;
  protected SaleOrderLineServiceSupplyChain saleOrderLineServiceSupplyChain;
  protected AppBaseService appBaseService;

  @Inject
  public SaleOrderStockServiceImpl(
      StockMoveService stockMoveService,
      StockMoveLineService stockMoveLineService,
      StockConfigService stockConfigService,
      UnitConversionService unitConversionService,
      SaleOrderLineServiceSupplyChain saleOrderLineServiceSupplyChain,
      AppBaseService appBaseService) {

    this.stockMoveService = stockMoveService;
    this.stockMoveLineService = stockMoveLineService;
    this.stockConfigService = stockConfigService;
    this.unitConversionService = unitConversionService;
    this.saleOrderLineServiceSupplyChain = saleOrderLineServiceSupplyChain;
    this.appBaseService = appBaseService;
  }

  @Override
  public StockMove createStocksMovesFromSaleOrder(SaleOrder saleOrder) throws AxelorException {

    if (!this.isSaleOrderWithProductsToDeliver(saleOrder)) {
      return null;
    }

    Optional<StockMove> activeStockMove = findActiveStockMoveForSaleOrder(saleOrder);

    if (activeStockMove.isPresent()) {
      throw new AxelorException(
          activeStockMove.get(),
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.SO_ACTIVE_DELIVERY_STOCK_MOVE_ALREADY_EXISTS),
          activeStockMove.get().getName(),
          saleOrder.getSaleOrderSeq());
    }

    if (saleOrder.getStockLocation() == null) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.SO_MISSING_STOCK_LOCATION),
          saleOrder.getSaleOrderSeq());
    }

    StockMove stockMove = this.createStockMove(saleOrder, saleOrder.getCompany());

    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {

      if (saleOrderLine.getTypeSelect().equals(SaleOrderLineRepository.TYPE_TITLE)) {
        createStockMoveLine(stockMove, saleOrderLine);
      } else {
        if (saleOrderLine.getProduct() != null
            || saleOrderLine.getTypeSelect().equals(SaleOrderLineRepository.TYPE_PACK)) {
          BigDecimal qty = saleOrderLineServiceSupplyChain.computeUndeliveredQty(saleOrderLine);

          if (qty.signum() > 0) {
            createStockMoveLine(stockMove, saleOrderLine, qty);
          }
        }
      }
    }

    if (stockMove.getStockMoveLineList() != null && !stockMove.getStockMoveLineList().isEmpty()) {
      if (stockMove
          .getStockMoveLineList()
          .stream()
          .noneMatch(
              stockMoveLine ->
                  stockMoveLine.getSaleOrderLine() != null
                      && stockMoveLine.getSaleOrderLine().getTypeSelect()
                          == SaleOrderLineRepository.TYPE_NORMAL)) {
        stockMove.setFullySpreadOverLogisticalFormsFlag(true);
      }

      stockMove.setEstimatedDate(saleOrder.getDeliveryDate());
      stockMoveService.plan(stockMove);
      return stockMove;
    }

    return null;
  }

  protected boolean isSaleOrderWithProductsToDeliver(SaleOrder saleOrder) throws AxelorException {

    if (saleOrder.getSaleOrderLineList() == null) {
      return false;
    }

    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {

      if (this.isStockMoveProduct(saleOrderLine)) {

        return true;
      }
    }
    return false;
  }

  @Override
  public StockMove createStockMove(SaleOrder saleOrder, Company company) throws AxelorException {
    StockLocation toStockLocation = findSaleOrderToStockLocation(saleOrder);

    StockMove stockMove =
        stockMoveService.createStockMove(
            null,
            saleOrder.getDeliveryAddress(),
            company,
            saleOrder.getClientPartner(),
            saleOrder.getStockLocation(),
            toStockLocation,
            null,
            saleOrder.getShipmentDate(),
            saleOrder.getDescription(),
            saleOrder.getShipmentMode(),
            saleOrder.getFreightCarrierMode(),
            saleOrder.getCarrierPartner(),
            saleOrder.getForwarderPartner(),
            saleOrder.getIncoterm(),
            StockMoveRepository.TYPE_OUTGOING);

    stockMove.setToAddressStr(saleOrder.getDeliveryAddressStr());
    stockMove.setSaleOrder(saleOrder);
    stockMove.setStockMoveLineList(new ArrayList<>());
    stockMove.setTradingName(saleOrder.getTradingName());
    return stockMove;
  }

  /**
   * @param saleOrder
   * @return the first default stock location corresponding to the partner and the company. Choose
   *     first the external stock location, else virtual.
   *     <p>null if there is no default stock location
   */
  protected StockLocation findSaleOrderToStockLocation(SaleOrder saleOrder) throws AxelorException {
    Preconditions.checkNotNull(saleOrder, I18n.get("Sale order cannot be null."));

    Partner partner = saleOrder.getClientPartner();
    Company company = saleOrder.getCompany();

    Preconditions.checkNotNull(partner, I18n.get("Partner cannot be null."));
    Preconditions.checkNotNull(company, I18n.get("Company cannot be null."));

    List<PartnerStockSettings> defaultStockLocations = partner.getPartnerStockSettingsList();

    List<StockLocation> candidateStockLocations =
        defaultStockLocations != null
            ? defaultStockLocations
                .stream()
                .filter(Objects::nonNull)
                .filter(partnerStockSettings -> partnerStockSettings.getCompany().equals(company))
                .map(PartnerStockSettings::getDefaultStockLocation)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
            : new ArrayList<>();

    // check external or internal stock location
    Optional<StockLocation> candidateNonVirtualStockLocation =
        candidateStockLocations
            .stream()
            .filter(
                stockLocation ->
                    stockLocation.getTypeSelect() == StockLocationRepository.TYPE_EXTERNAL
                        || stockLocation.getTypeSelect() == StockLocationRepository.TYPE_INTERNAL)
            .findAny();
    if (candidateNonVirtualStockLocation.isPresent()) {
      return candidateNonVirtualStockLocation.get();
    } else {
      // no external stock location found, search for virtual
      return candidateStockLocations
          .stream()
          .filter(
              stockLocation ->
                  stockLocation.getTypeSelect() == StockLocationRepository.TYPE_VIRTUAL)
          .findAny()
          .orElse(
              stockConfigService.getCustomerVirtualStockLocation(
                  stockConfigService.getStockConfig(company)));
    }
  }

  @Override
  public StockMoveLine createStockMoveLine(StockMove stockMove, SaleOrderLine saleOrderLine)
      throws AxelorException {
    return createStockMoveLine(
        stockMove,
        saleOrderLine,
        saleOrderLineServiceSupplyChain.computeUndeliveredQty(saleOrderLine));
  }

  @Override
  public StockMoveLine createStockMoveLine(
      StockMove stockMove, SaleOrderLine saleOrderLine, BigDecimal qty) throws AxelorException {

    if (this.isStockMoveProduct(saleOrderLine)) {

      Unit unit = saleOrderLine.getProduct().getUnit();
      BigDecimal priceDiscounted = saleOrderLine.getPriceDiscounted();
      BigDecimal companyUnitPriceUntaxed = saleOrderLine.getProduct().getCostPrice();
      if (unit != null && !unit.equals(saleOrderLine.getUnit())) {
        qty =
            unitConversionService.convert(
                saleOrderLine.getUnit(), unit, qty, qty.scale(), saleOrderLine.getProduct());
        priceDiscounted =
            unitConversionService.convert(
                unit,
                saleOrderLine.getUnit(),
                priceDiscounted,
                appBaseService.getNbDecimalDigitForUnitPrice(),
                saleOrderLine.getProduct());
      }

      BigDecimal taxRate = BigDecimal.ZERO;
      TaxLine taxLine = saleOrderLine.getTaxLine();
      if (taxLine != null) {
        taxRate = taxLine.getValue();
      }
      if (saleOrderLine.getQty() != BigDecimal.ZERO) {
        companyUnitPriceUntaxed =
            saleOrderLine
                .getCompanyExTaxTotal()
                .divide(
                    qty,
                    Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice(),
                    RoundingMode.HALF_EVEN);
      }

      StockMoveLine stockMoveLine =
          stockMoveLineService.createStockMoveLine(
              saleOrderLine.getProduct(),
              saleOrderLine.getProductName(),
              saleOrderLine.getDescription(),
              qty,
              priceDiscounted,
              companyUnitPriceUntaxed,
              unit,
              stockMove,
              StockMoveLineService.TYPE_SALES,
              saleOrderLine.getSaleOrder().getInAti(),
              taxRate);

      if (saleOrderLine.getDeliveryState() == 0) {
        saleOrderLine.setDeliveryState(SaleOrderLineRepository.DELIVERY_STATE_NOT_DELIVERED);
      }

      if (stockMoveLine != null) {
        stockMoveLine.setSaleOrderLine(saleOrderLine);
        stockMoveLine.setReservedQty(saleOrderLine.getReservedQty());
      }

      return stockMoveLine;
    } else if (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_PACK
        || saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_TITLE) {
      StockMoveLine stockMoveLine =
          stockMoveLineService.createStockMoveLine(
              null,
              saleOrderLine.getProductName(),
              saleOrderLine.getDescription(),
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              null,
              stockMove,
              StockMoveLineService.TYPE_SALES,
              saleOrderLine.getSaleOrder().getInAti(),
              null);

      saleOrderLine.setDeliveryState(SaleOrderLineRepository.DELIVERY_STATE_NOT_DELIVERED);
      stockMoveLine.setSaleOrderLine(saleOrderLine);
      if (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_TITLE) {
        stockMoveLine.setIsTitleLine(true);
      }
      return stockMoveLine;
    }
    return null;
  }

  @Override
  public boolean isStockMoveProduct(SaleOrderLine saleOrderLine) throws AxelorException {
    return isStockMoveProduct(saleOrderLine, saleOrderLine.getSaleOrder());
  }

  @Override
  public boolean isStockMoveProduct(SaleOrderLine saleOrderLine, SaleOrder saleOrder)
      throws AxelorException {

    Company company = saleOrder.getCompany();

    SupplyChainConfig supplyChainConfig =
        Beans.get(SupplyChainConfigService.class).getSupplyChainConfig(company);

    Product product = saleOrderLine.getProduct();

    return (product != null
        && ((ProductRepository.PRODUCT_TYPE_SERVICE.equals(product.getProductTypeSelect())
                && supplyChainConfig.getHasOutSmForNonStorableProduct()
                && !product.getIsShippingCostsProduct())
            || (ProductRepository.PRODUCT_TYPE_STORABLE.equals(product.getProductTypeSelect())
                && supplyChainConfig.getHasOutSmForStorableProduct())));
  }

  @Override
  public Optional<StockMove> findActiveStockMoveForSaleOrder(SaleOrder saleOrder) {
    return saleOrder.getStockMoveList() != null
        ? saleOrder
            .getStockMoveList()
            .stream()
            .filter(stockMove -> stockMove.getStatusSelect() <= StockMoveRepository.STATUS_PLANNED)
            .findFirst()
        : Optional.empty();
  }

  @Override
  public void updateDeliveryState(SaleOrder saleOrder) throws AxelorException {
    saleOrder.setDeliveryState(computeDeliveryState(saleOrder));
  }

  @Override
  public void fullyUpdateDeliveryState(SaleOrder saleOrder) throws AxelorException {
    saleOrderLineServiceSupplyChain.updateDeliveryStates(saleOrder.getSaleOrderLineList());
    updateDeliveryState(saleOrder);
  }

  private int computeDeliveryState(SaleOrder saleOrder) throws AxelorException {

    if (saleOrder.getSaleOrderLineList() == null || saleOrder.getSaleOrderLineList().isEmpty()) {
      return SaleOrderRepository.DELIVERY_STATE_NOT_DELIVERED;
    }

    int deliveryState = -1;

    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {

      if (this.isStockMoveProduct(saleOrderLine, saleOrder)) {

        if (saleOrderLine.getDeliveryState() == SaleOrderLineRepository.DELIVERY_STATE_DELIVERED) {
          if (deliveryState == SaleOrderRepository.DELIVERY_STATE_NOT_DELIVERED
              || deliveryState == SaleOrderRepository.DELIVERY_STATE_PARTIALLY_DELIVERED) {
            return SaleOrderRepository.DELIVERY_STATE_PARTIALLY_DELIVERED;
          } else {
            deliveryState = SaleOrderRepository.DELIVERY_STATE_DELIVERED;
          }
        } else if (saleOrderLine.getDeliveryState()
            == SaleOrderLineRepository.DELIVERY_STATE_NOT_DELIVERED) {
          if (deliveryState == SaleOrderRepository.DELIVERY_STATE_DELIVERED
              || deliveryState == SaleOrderRepository.DELIVERY_STATE_PARTIALLY_DELIVERED) {
            return SaleOrderRepository.DELIVERY_STATE_PARTIALLY_DELIVERED;
          } else {
            deliveryState = SaleOrderRepository.DELIVERY_STATE_NOT_DELIVERED;
          }
        } else if (saleOrderLine.getDeliveryState()
            == SaleOrderLineRepository.DELIVERY_STATE_PARTIALLY_DELIVERED) {
          return SaleOrderRepository.DELIVERY_STATE_PARTIALLY_DELIVERED;
        }
      }
    }
    return deliveryState;
  }
}
