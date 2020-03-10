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
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.stock.db.PartnerStockSettings;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.PartnerStockSettingsService;
import com.axelor.apps.stock.service.StockMoveLineService;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.supplychain.db.SupplyChainConfig;
import com.axelor.apps.supplychain.db.repo.SupplyChainConfigRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SaleOrderStockServiceImpl implements SaleOrderStockService {

  protected StockMoveService stockMoveService;
  protected StockMoveLineService stockMoveLineService;
  protected StockConfigService stockConfigService;
  protected UnitConversionService unitConversionService;
  protected SaleOrderLineServiceSupplyChain saleOrderLineServiceSupplyChain;
  protected StockMoveLineServiceSupplychain stockMoveLineSupplychainService;
  protected StockMoveLineRepository stockMoveLineRepository;
  protected AppBaseService appBaseService;
  protected SaleOrderRepository saleOrderRepository;

  @Inject
  public SaleOrderStockServiceImpl(
      StockMoveService stockMoveService,
      StockMoveLineService stockMoveLineService,
      StockConfigService stockConfigService,
      UnitConversionService unitConversionService,
      SaleOrderLineServiceSupplyChain saleOrderLineServiceSupplyChain,
      StockMoveLineServiceSupplychain stockMoveLineSupplychainService,
      StockMoveLineRepository stockMoveLineRepository,
      AppBaseService appBaseService,
      SaleOrderRepository saleOrderRepository) {

    this.stockMoveService = stockMoveService;
    this.stockMoveLineService = stockMoveLineService;
    this.stockConfigService = stockConfigService;
    this.unitConversionService = unitConversionService;
    this.saleOrderLineServiceSupplyChain = saleOrderLineServiceSupplyChain;
    this.stockMoveLineSupplychainService = stockMoveLineSupplychainService;
    this.stockMoveLineRepository = stockMoveLineRepository;
    this.appBaseService = appBaseService;
    this.saleOrderRepository = saleOrderRepository;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public List<Long> createStocksMovesFromSaleOrder(SaleOrder saleOrder) throws AxelorException {

    if (!this.isSaleOrderWithProductsToDeliver(saleOrder)) {
      return null;
    }

    if (saleOrder.getStockLocation() == null) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.SO_MISSING_STOCK_LOCATION),
          saleOrder.getSaleOrderSeq());
    }

    List<Long> stockMoveList = new ArrayList<>();

    Map<LocalDate, List<SaleOrderLine>> saleOrderLinePerDateMap =
        getAllSaleOrderLinePerDate(saleOrder);

    for (LocalDate estimatedDeliveryDate :
        saleOrderLinePerDateMap
            .keySet()
            .stream()
            .filter(x -> x != null)
            .sorted((x, y) -> x.compareTo(y))
            .collect(Collectors.toList())) {

      List<SaleOrderLine> saleOrderLineList = saleOrderLinePerDateMap.get(estimatedDeliveryDate);

      Optional<StockMove> stockMove =
          createStockMove(saleOrder, estimatedDeliveryDate, saleOrderLineList);

      stockMove.map(StockMove::getId).ifPresent(stockMoveList::add);
    }
    Optional<List<SaleOrderLine>> saleOrderLineList =
        Optional.ofNullable(saleOrderLinePerDateMap.get(null));
    if (saleOrderLineList.isPresent()) {

      Optional<StockMove> stockMove = createStockMove(saleOrder, null, saleOrderLineList.get());

      stockMove.map(StockMove::getId).ifPresent(stockMoveList::add);
    }
    return stockMoveList;
  }

  protected Optional<StockMove> createStockMove(
      SaleOrder saleOrder, LocalDate estimatedDeliveryDate, List<SaleOrderLine> saleOrderLineList)
      throws AxelorException {

    StockMove stockMove =
        this.createStockMove(saleOrder, saleOrder.getCompany(), estimatedDeliveryDate);
    stockMove.setDeliveryCondition(saleOrder.getDeliveryCondition());

    for (SaleOrderLine saleOrderLine : saleOrderLineList) {
      if (saleOrderLine.getProduct() != null
          || saleOrderLine.getTypeSelect().equals(SaleOrderLineRepository.TYPE_PACK)) {
        BigDecimal qty = saleOrderLineServiceSupplyChain.computeUndeliveredQty(saleOrderLine);

        if (qty.signum() > 0 && !existActiveStockMoveForSaleOrderLine(saleOrderLine)) {
          createStockMoveLine(stockMove, saleOrderLine, qty);
        }
      }
    }

    if (stockMove.getStockMoveLineList() == null || stockMove.getStockMoveLineList().isEmpty()) {
      return Optional.empty();
    }

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

    boolean isNeedingConformityCertificate = saleOrder.getIsNeedingConformityCertificate();
    stockMove.setIsNeedingConformityCertificate(isNeedingConformityCertificate);

    if (isNeedingConformityCertificate) {
      stockMove.setSignatoryUser(
          stockConfigService.getStockConfig(stockMove.getCompany()).getSignatoryUser());
    }

    SupplyChainConfig supplychainConfig =
        Beans.get(SupplyChainConfigService.class).getSupplyChainConfig(saleOrder.getCompany());

    if (supplychainConfig.getDefaultEstimatedDate() != null
        && supplychainConfig.getDefaultEstimatedDate() == SupplyChainConfigRepository.CURRENT_DATE
        && stockMove.getEstimatedDate() == null) {
      stockMove.setEstimatedDate(appBaseService.getTodayDate());
    } else if (supplychainConfig.getDefaultEstimatedDate()
            == SupplyChainConfigRepository.CURRENT_DATE_PLUS_DAYS
        && stockMove.getEstimatedDate() == null) {
      stockMove.setEstimatedDate(
          appBaseService.getTodayDate().plusDays(supplychainConfig.getNumberOfDays().longValue()));
    }

    setReservationDateTime(stockMove, saleOrder);
    stockMoveService.plan(stockMove);

    if (Beans.get(AppSaleService.class).getAppSale().getProductPackMgt()) {
      setParentStockMoveLine(stockMove);
    }

    return Optional.of(stockMove);
  }

  protected Map<LocalDate, List<SaleOrderLine>> getAllSaleOrderLinePerDate(SaleOrder saleOrder) {

    Map<LocalDate, List<SaleOrderLine>> saleOrderLinePerDateMap = new HashMap<>();

    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {

      if (saleOrderLineServiceSupplyChain.computeUndeliveredQty(saleOrderLine).signum() <= 0) {
        continue;
      }

      LocalDate dateKey = saleOrderLine.getEstimatedDelivDate();

      if (dateKey == null) {
        dateKey = saleOrderLine.getSaleOrder().getDeliveryDate();
      }
      if (dateKey == null) {
        dateKey = saleOrderLine.getDesiredDelivDate();
      }

      List<SaleOrderLine> saleOrderLineLists = saleOrderLinePerDateMap.get(dateKey);

      if (saleOrderLineLists == null) {
        saleOrderLineLists = new ArrayList<>();
        saleOrderLinePerDateMap.put(dateKey, saleOrderLineLists);
      }

      saleOrderLineLists.add(saleOrderLine);
    }

    return saleOrderLinePerDateMap;
  }

  @Transactional
  public void setParentStockMoveLine(StockMove stockMove) {

    for (StockMoveLine line : stockMove.getStockMoveLineList()) {
      if (line.getSaleOrderLine() != null) {
        line.setPackPriceSelect(line.getSaleOrderLine().getPackPriceSelect());
        StockMoveLine parentStockMoveLine =
            Beans.get(StockMoveLineRepository.class)
                .all()
                .filter(
                    "self.saleOrderLine = ?1 and self.stockMove = ?2",
                    line.getSaleOrderLine().getParentLine(),
                    stockMove)
                .fetchOne();
        line.setParentLine(parentStockMoveLine);
      }
    }
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
  public StockMove createStockMove(
      SaleOrder saleOrder, Company company, LocalDate estimatedDeliveryDate)
      throws AxelorException {
    StockLocation toStockLocation =
        stockConfigService.getCustomerVirtualStockLocation(
            stockConfigService.getStockConfig(company));

    StockMove stockMove =
        stockMoveService.createStockMove(
            null,
            saleOrder.getDeliveryAddress(),
            company,
            saleOrder.getClientPartner(),
            saleOrder.getStockLocation(),
            toStockLocation,
            null,
            estimatedDeliveryDate,
            saleOrder.getDescription(),
            saleOrder.getShipmentMode(),
            saleOrder.getFreightCarrierMode(),
            saleOrder.getCarrierPartner(),
            saleOrder.getForwarderPartner(),
            saleOrder.getIncoterm(),
            StockMoveRepository.TYPE_OUTGOING);

    stockMove.setToAddressStr(saleOrder.getDeliveryAddressStr());
    stockMove.setOriginId(saleOrder.getId());
    stockMove.setOriginTypeSelect(StockMoveRepository.ORIGIN_SALE_ORDER);
    stockMove.setOrigin(saleOrder.getSaleOrderSeq());
    stockMove.setStockMoveLineList(new ArrayList<>());
    stockMove.setTradingName(saleOrder.getTradingName());
    stockMove.setSpecificPackage(saleOrder.getSpecificPackage());
    stockMove.setNote(saleOrder.getDeliveryComments());
    stockMove.setPickingOrderComments(saleOrder.getPickingOrderComments());
    if (stockMove.getPartner() != null) {
      setDefaultAutoMailSettings(stockMove);
    }
    return stockMove;
  }

  /**
   * Fill reservation date time in stock move lines with sale order confirmation date time.
   *
   * @param stockMove
   * @param saleOrder
   */
  protected void setReservationDateTime(StockMove stockMove, SaleOrder saleOrder) {
    LocalDateTime reservationDateTime = saleOrder.getConfirmationDateTime();
    if (reservationDateTime == null) {
      reservationDateTime = Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime();
    }
    List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();
    if (stockMoveLineList != null) {
      for (StockMoveLine stockMoveLine : stockMoveLineList) {
        stockMoveLine.setReservationDateTime(reservationDateTime);
      }
    }
  }

  /**
   * Set automatic mail configuration from the partner.
   *
   * @param stockMove
   */
  protected void setDefaultAutoMailSettings(StockMove stockMove) throws AxelorException {
    Partner partner = stockMove.getPartner();
    Company company = stockMove.getCompany();

    PartnerStockSettings mailSettings =
        Beans.get(PartnerStockSettingsService.class).getOrCreateMailSettings(partner, company);

    stockMove.setRealStockMoveAutomaticMail(mailSettings.getRealStockMoveAutomaticMail());
    stockMove.setRealStockMoveMessageTemplate(mailSettings.getRealStockMoveMessageTemplate());
    stockMove.setPlannedStockMoveAutomaticMail(mailSettings.getPlannedStockMoveAutomaticMail());
    stockMove.setPlannedStockMoveMessageTemplate(mailSettings.getPlannedStockMoveMessageTemplate());
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
      BigDecimal requestedReservedQty =
          saleOrderLine.getRequestedReservedQty().subtract(saleOrderLine.getDeliveredQty());

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
        requestedReservedQty =
            unitConversionService.convert(
                saleOrderLine.getUnit(),
                unit,
                requestedReservedQty,
                requestedReservedQty.scale(),
                saleOrderLine.getProduct());
      }

      BigDecimal taxRate = BigDecimal.ZERO;
      TaxLine taxLine = saleOrderLine.getTaxLine();
      if (taxLine != null) {
        taxRate = taxLine.getValue();
      }
      if (saleOrderLine.getQty().signum() != 0) {
        companyUnitPriceUntaxed =
            saleOrderLine
                .getCompanyExTaxTotal()
                .divide(
                    saleOrderLine.getQty(),
                    Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice(),
                    RoundingMode.HALF_EVEN);
      }

      StockMoveLine stockMoveLine =
          stockMoveLineSupplychainService.createStockMoveLine(
              saleOrderLine.getProduct(),
              saleOrderLine.getProductName(),
              saleOrderLine.getDescription(),
              qty,
              requestedReservedQty,
              priceDiscounted,
              companyUnitPriceUntaxed,
              null,
              unit,
              stockMove,
              StockMoveLineService.TYPE_SALES,
              saleOrderLine.getSaleOrder().getInAti(),
              taxRate,
              saleOrderLine,
              null);

      if (saleOrderLine.getDeliveryState() == 0) {
        saleOrderLine.setDeliveryState(SaleOrderLineRepository.DELIVERY_STATE_NOT_DELIVERED);
      }

      if (stockMoveLine != null) {
        updatePackInfo(saleOrderLine, stockMoveLine);
      }

      return stockMoveLine;
    } else if (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_PACK) {
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
      updatePackInfo(saleOrderLine, stockMoveLine);
      stockMoveLine.setSaleOrderLine(saleOrderLine);

      return stockMoveLine;
    }
    return null;
  }

  protected void updatePackInfo(SaleOrderLine saleOrderLine, StockMoveLine stockMoveLine) {
    stockMoveLine.setLineTypeSelect(saleOrderLine.getTypeSelect());
    stockMoveLine.setPackPriceSelect(saleOrderLine.getPackPriceSelect());
    stockMoveLine.setIsSubLine(saleOrderLine.getIsSubLine());
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

  protected boolean existActiveStockMoveForSaleOrderLine(SaleOrderLine saleOrderLine) {

    long stockMoveLineCount =
        stockMoveLineRepository
            .all()
            .filter(
                "self.saleOrderLine.id = ?1 AND self.stockMove.statusSelect in (?2,?3)",
                saleOrderLine.getId(),
                StockMoveRepository.STATUS_DRAFT,
                StockMoveRepository.STATUS_PLANNED)
            .count();

    return stockMoveLineCount > 0;
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

  protected int computeDeliveryState(SaleOrder saleOrder) throws AxelorException {

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

  public Optional<SaleOrder> findSaleOrder(StockMove stockMove) {
    if (StockMoveRepository.ORIGIN_SALE_ORDER.equals(stockMove.getOriginTypeSelect())
        && stockMove.getOriginId() != null) {
      return Optional.ofNullable(saleOrderRepository.find(stockMove.getOriginId()));
    } else {
      return Optional.empty();
    }
  }
}
