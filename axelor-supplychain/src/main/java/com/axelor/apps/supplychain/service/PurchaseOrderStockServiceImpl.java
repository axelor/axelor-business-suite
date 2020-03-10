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
import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.ShippingCoefService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderLineRepository;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.stock.db.StockConfig;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.apps.stock.service.StockMoveLineService;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.supplychain.db.SupplyChainConfig;
import com.axelor.apps.supplychain.db.repo.SupplyChainConfigRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.apps.tool.StringTool;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurchaseOrderStockServiceImpl implements PurchaseOrderStockService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected UnitConversionService unitConversionService;
  protected StockMoveLineRepository stockMoveLineRepository;
  protected PurchaseOrderLineServiceSupplychainImpl purchaseOrderLineServiceSupplychainImpl;
  protected AppBaseService appBaseService;
  protected ShippingCoefService shippingCoefService;
  protected StockMoveLineServiceSupplychain stockMoveLineServiceSupplychain;
  protected StockMoveService stockMoveService;

  @Inject
  public PurchaseOrderStockServiceImpl(
      UnitConversionService unitConversionService,
      StockMoveLineRepository stockMoveLineRepository,
      PurchaseOrderLineServiceSupplychainImpl purchaseOrderLineServiceSupplychainImpl,
      AppBaseService appBaseService,
      ShippingCoefService shippingCoefService,
      StockMoveLineServiceSupplychain stockMoveLineServiceSupplychain,
      StockMoveService stockMoveService) {

    this.unitConversionService = unitConversionService;
    this.stockMoveLineRepository = stockMoveLineRepository;
    this.purchaseOrderLineServiceSupplychainImpl = purchaseOrderLineServiceSupplychainImpl;
    this.appBaseService = appBaseService;
    this.shippingCoefService = shippingCoefService;
    this.stockMoveLineServiceSupplychain = stockMoveLineServiceSupplychain;
    this.stockMoveService = stockMoveService;
  }

  /**
   * Méthode permettant de créer un StockMove à partir d'un PurchaseOrder.
   *
   * @param purchaseOrder une commande
   * @throws AxelorException Aucune séquence de StockMove n'a été configurée
   */
  public List<Long> createStockMoveFromPurchaseOrder(PurchaseOrder purchaseOrder)
      throws AxelorException {

    List<Long> stockMoveIdList = new ArrayList<>();

    if (purchaseOrder.getPurchaseOrderLineList() == null || purchaseOrder.getCompany() == null) {
      return stockMoveIdList;
    }

    if (purchaseOrder.getStockLocation() == null) {
      throw new AxelorException(
          purchaseOrder,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.PO_MISSING_STOCK_LOCATION),
          purchaseOrder.getPurchaseOrderSeq());
    }

    Map<LocalDate, List<PurchaseOrderLine>> purchaseOrderLinePerDateMap =
        getAllPurchaseOrderLinePerDate(purchaseOrder);

    for (LocalDate estimatedDeliveryDate :
        purchaseOrderLinePerDateMap
            .keySet()
            .stream()
            .filter(x -> x != null)
            .sorted((x, y) -> x.compareTo(y))
            .collect(Collectors.toList())) {

      List<PurchaseOrderLine> purchaseOrderLineList =
          purchaseOrderLinePerDateMap.get(estimatedDeliveryDate);

      List<Long> stockMoveId =
          createStockMove(purchaseOrder, estimatedDeliveryDate, purchaseOrderLineList);

      if (stockMoveId != null && !stockMoveId.isEmpty()) {

        stockMoveIdList.addAll(stockMoveId);
      }
    }
    Optional<List<PurchaseOrderLine>> purchaseOrderLineListDeliveryDateNull =
        Optional.ofNullable(purchaseOrderLinePerDateMap.get(null));
    if (purchaseOrderLineListDeliveryDateNull.isPresent()) {

      List<Long> stockMoveId =
          createStockMove(purchaseOrder, null, purchaseOrderLineListDeliveryDateNull.get());

      if (stockMoveId != null && !stockMoveId.isEmpty()) {

        stockMoveIdList.addAll(stockMoveId);
      }
    }
    return stockMoveIdList;
  }

  protected List<Long> createStockMove(
      PurchaseOrder purchaseOrder,
      LocalDate estimatedDeliveryDate,
      List<PurchaseOrderLine> purchaseOrderLineList)
      throws AxelorException {

    List<Long> stockMoveIdList = new ArrayList<>();

    Partner supplierPartner = purchaseOrder.getSupplierPartner();
    Company company = purchaseOrder.getCompany();

    Address address = Beans.get(PartnerService.class).getDeliveryAddress(supplierPartner);

    StockLocation startLocation = getStartStockLocation(purchaseOrder);

    StockMove stockMove =
        stockMoveService.createStockMove(
            address,
            null,
            company,
            supplierPartner,
            startLocation,
            purchaseOrder.getStockLocation(),
            null,
            estimatedDeliveryDate,
            purchaseOrder.getNotes(),
            purchaseOrder.getShipmentMode(),
            purchaseOrder.getFreightCarrierMode(),
            null,
            null,
            null,
            StockMoveRepository.TYPE_INCOMING);

    StockMove qualityStockMove =
        stockMoveService.createStockMove(
            address,
            null,
            company,
            supplierPartner,
            startLocation,
            company.getStockConfig().getQualityControlDefaultStockLocation(),
            null,
            estimatedDeliveryDate,
            purchaseOrder.getNotes(),
            purchaseOrder.getShipmentMode(),
            purchaseOrder.getFreightCarrierMode(),
            null,
            null,
            null,
            StockMoveRepository.TYPE_INCOMING);

    stockMove.setOriginId(purchaseOrder.getId());
    stockMove.setOriginTypeSelect(StockMoveRepository.ORIGIN_PURCHASE_ORDER);
    stockMove.setOrigin(purchaseOrder.getPurchaseOrderSeq());
    stockMove.setTradingName(purchaseOrder.getTradingName());

    qualityStockMove.setOriginId(purchaseOrder.getId());
    qualityStockMove.setOriginTypeSelect(StockMoveRepository.ORIGIN_PURCHASE_ORDER);
    qualityStockMove.setOrigin(purchaseOrder.getPurchaseOrderSeq());
    qualityStockMove.setTradingName(purchaseOrder.getTradingName());

    SupplyChainConfig supplychainConfig =
        Beans.get(SupplyChainConfigService.class).getSupplyChainConfig(purchaseOrder.getCompany());
    if (supplychainConfig.getDefaultEstimatedDateForPurchaseOrder()
            == SupplyChainConfigRepository.CURRENT_DATE
        && stockMove.getEstimatedDate() == null) {
      stockMove.setEstimatedDate(appBaseService.getTodayDate());
    } else if (supplychainConfig.getDefaultEstimatedDateForPurchaseOrder()
            == SupplyChainConfigRepository.CURRENT_DATE_PLUS_DAYS
        && stockMove.getEstimatedDate() == null) {
      stockMove.setEstimatedDate(
          appBaseService
              .getTodayDate()
              .plusDays(supplychainConfig.getNumberOfDaysForPurchaseOrder().longValue()));
    }

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLineList) {
      BigDecimal qty =
          purchaseOrderLineServiceSupplychainImpl.computeUndeliveredQty(purchaseOrderLine);

      if (qty.signum() > 0 && !existActiveStockMoveForPurchaseOrderLine(purchaseOrderLine)) {
        this.createStockMoveLine(stockMove, qualityStockMove, purchaseOrderLine, qty);
      }
    }
    if (stockMove.getStockMoveLineList() != null && !stockMove.getStockMoveLineList().isEmpty()) {
      stockMoveService.plan(stockMove);
      stockMoveIdList.add(stockMove.getId());
    }
    if (qualityStockMove.getStockMoveLineList() != null
        && !qualityStockMove.getStockMoveLineList().isEmpty()) {
      stockMoveService.plan(qualityStockMove);
      stockMoveIdList.add(qualityStockMove.getId());
    }

    return stockMoveIdList;
  }

  protected StockLocation getStartStockLocation(PurchaseOrder purchaseOrder)
      throws AxelorException {

    Company company = purchaseOrder.getCompany();

    StockLocation startLocation =
        Beans.get(StockLocationRepository.class).findByPartner(purchaseOrder.getSupplierPartner());

    if (startLocation == null) {
      StockConfigService stockConfigService = Beans.get(StockConfigService.class);
      StockConfig stockConfig = stockConfigService.getStockConfig(company);
      startLocation = stockConfigService.getSupplierVirtualStockLocation(stockConfig);
    }
    if (startLocation == null) {
      throw new AxelorException(
          purchaseOrder,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.PURCHASE_ORDER_1),
          company.getName());
    }

    return startLocation;
  }

  protected Map<LocalDate, List<PurchaseOrderLine>> getAllPurchaseOrderLinePerDate(
      PurchaseOrder purchaseOrder) {

    Map<LocalDate, List<PurchaseOrderLine>> purchaseOrderLinePerDateMap = new HashMap<>();

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {

      if (purchaseOrderLineServiceSupplychainImpl.computeUndeliveredQty(purchaseOrderLine).signum()
          <= 0) {
        continue;
      }

      LocalDate dateKey = purchaseOrderLine.getEstimatedDelivDate();

      if (dateKey == null) {
        dateKey = purchaseOrderLine.getPurchaseOrder().getDeliveryDate();
      }

      List<PurchaseOrderLine> purchaseOrderLineLists = purchaseOrderLinePerDateMap.get(dateKey);

      if (purchaseOrderLineLists == null) {
        purchaseOrderLineLists = new ArrayList<>();
        purchaseOrderLinePerDateMap.put(dateKey, purchaseOrderLineLists);
      }

      purchaseOrderLineLists.add(purchaseOrderLine);
    }

    return purchaseOrderLinePerDateMap;
  }

  public StockMoveLine createStockMoveLine(
      StockMove stockMove,
      StockMove qualityStockMove,
      PurchaseOrderLine purchaseOrderLine,
      BigDecimal qty)
      throws AxelorException {

    StockMoveLine stockMoveLine = null;

    if (this.isStockMoveProduct(purchaseOrderLine)) {

      stockMoveLine =
          createProductStockMoveLine(
              purchaseOrderLine,
              qty,
              needControlOnReceipt(purchaseOrderLine) ? qualityStockMove : stockMove);

    } else if (purchaseOrderLine.getIsTitleLine()) {
      stockMoveLine = createTitleStockMoveLine(purchaseOrderLine, stockMove);
    }
    return stockMoveLine;
  }

  /**
   * @param product
   * @param purchaseOrder
   * @return true if product needs a control on receipt and if the purchase order is not a direct
   *     order
   */
  protected boolean needControlOnReceipt(PurchaseOrderLine purchaseOrderLine) {

    Product product = purchaseOrderLine.getProduct();
    PurchaseOrder purchaseOrder = purchaseOrderLine.getPurchaseOrder();

    if (product.getControlOnReceipt()
        && !purchaseOrder.getStockLocation().getDirectOrderLocation()) {
      return true;
    }
    return false;
  }

  protected StockMoveLine createProductStockMoveLine(
      PurchaseOrderLine purchaseOrderLine, BigDecimal qty, StockMove stockMove)
      throws AxelorException {

    PurchaseOrder purchaseOrder = purchaseOrderLine.getPurchaseOrder();
    Product product = purchaseOrderLine.getProduct();
    Unit unit = product.getUnit();
    BigDecimal priceDiscounted = purchaseOrderLine.getPriceDiscounted();
    BigDecimal companyUnitPriceUntaxed = purchaseOrderLine.getCompanyExTaxTotal();

    if (purchaseOrderLine.getQty().compareTo(BigDecimal.ZERO) != 0) {
      companyUnitPriceUntaxed =
          purchaseOrderLine
              .getCompanyExTaxTotal()
              .divide(
                  purchaseOrderLine.getQty(),
                  appBaseService.getNbDecimalDigitForUnitPrice(),
                  RoundingMode.HALF_EVEN);
    }

    if (unit != null && !unit.equals(purchaseOrderLine.getUnit())) {
      qty =
          unitConversionService.convert(
              purchaseOrderLine.getUnit(), unit, qty, qty.scale(), product);

      priceDiscounted =
          unitConversionService.convert(
              unit,
              purchaseOrderLine.getUnit(),
              priceDiscounted,
              appBaseService.getNbDecimalDigitForUnitPrice(),
              product);

      companyUnitPriceUntaxed =
          unitConversionService.convert(
              unit,
              purchaseOrderLine.getUnit(),
              companyUnitPriceUntaxed,
              appBaseService.getNbDecimalDigitForUnitPrice(),
              product);
    }

    BigDecimal shippingCoef =
        shippingCoefService.getShippingCoef(
            product, purchaseOrder.getSupplierPartner(), purchaseOrder.getCompany(), qty);
    BigDecimal companyPurchasePrice = priceDiscounted;
    priceDiscounted = priceDiscounted.multiply(shippingCoef);
    companyUnitPriceUntaxed = companyUnitPriceUntaxed.multiply(shippingCoef);

    BigDecimal taxRate = BigDecimal.ZERO;
    TaxLine taxLine = purchaseOrderLine.getTaxLine();
    if (taxLine != null) {
      taxRate = taxLine.getValue();
    }

    return stockMoveLineServiceSupplychain.createStockMoveLine(
        product,
        purchaseOrderLine.getProductName(),
        purchaseOrderLine.getDescription(),
        qty,
        BigDecimal.ZERO,
        priceDiscounted,
        companyUnitPriceUntaxed,
        companyPurchasePrice,
        unit,
        stockMove,
        StockMoveLineService.TYPE_PURCHASES,
        purchaseOrder.getInAti(),
        taxRate,
        null,
        purchaseOrderLine);
  }

  protected StockMoveLine createTitleStockMoveLine(
      PurchaseOrderLine purchaseOrderLine, StockMove stockMove) throws AxelorException {

    return stockMoveLineServiceSupplychain.createStockMoveLine(
        purchaseOrderLine.getProduct(),
        purchaseOrderLine.getProductName(),
        purchaseOrderLine.getDescription(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        stockMove,
        2,
        purchaseOrderLine.getPurchaseOrder().getInAti(),
        null,
        null,
        purchaseOrderLine);
  }

  public void cancelReceipt(PurchaseOrder purchaseOrder) throws AxelorException {

    List<StockMove> stockMoveList =
        Beans.get(StockMoveRepository.class)
            .all()
            .filter(
                "self.originTypeSelect = ? AND self.originId = ? AND self.statusSelect = 2",
                StockMoveRepository.ORIGIN_PURCHASE_ORDER,
                purchaseOrder.getId())
            .fetch();

    for (StockMove stockMove : stockMoveList) {

      stockMoveService.cancel(stockMove);
    }
  }

  public boolean isStockMoveProduct(PurchaseOrderLine purchaseOrderLine) throws AxelorException {
    return isStockMoveProduct(purchaseOrderLine, purchaseOrderLine.getPurchaseOrder());
  }

  public boolean isStockMoveProduct(
      PurchaseOrderLine purchaseOrderLine, PurchaseOrder purchaseOrder) throws AxelorException {

    Company company = purchaseOrder.getCompany();

    SupplyChainConfig supplyChainConfig =
        Beans.get(SupplyChainConfigService.class).getSupplyChainConfig(company);

    Product product = purchaseOrderLine.getProduct();

    return (product != null
        && ((ProductRepository.PRODUCT_TYPE_SERVICE.equals(product.getProductTypeSelect())
                && supplyChainConfig.getHasInSmForNonStorableProduct()
                && !product.getIsShippingCostsProduct())
            || (ProductRepository.PRODUCT_TYPE_STORABLE.equals(product.getProductTypeSelect())
                && supplyChainConfig.getHasInSmForStorableProduct())));
  }

  protected boolean existActiveStockMoveForPurchaseOrderLine(PurchaseOrderLine purchaseOrderLine) {

    long stockMoveLineCount =
        stockMoveLineRepository
            .all()
            .filter(
                "self.purchaseOrderLine.id = ?1 AND self.stockMove.statusSelect in (?2,?3)",
                purchaseOrderLine.getId(),
                StockMoveRepository.STATUS_DRAFT,
                StockMoveRepository.STATUS_PLANNED)
            .count();

    return stockMoveLineCount > 0;
  }

  // Check if existing at least one stockMove not canceled for the purchaseOrder
  public boolean existActiveStockMoveForPurchaseOrder(Long purchaseOrderId) {
    long nbStockMove =
        Beans.get(StockMoveRepository.class)
            .all()
            .filter(
                "self.originTypeSelect LIKE ? AND self.originId = ? AND self.statusSelect <> ?",
                StockMoveRepository.ORIGIN_PURCHASE_ORDER,
                purchaseOrderId,
                StockMoveRepository.STATUS_CANCELED)
            .count();
    return nbStockMove > 0;
  }

  public void updateReceiptState(PurchaseOrder purchaseOrder) throws AxelorException {
    purchaseOrder.setReceiptState(computeReceiptState(purchaseOrder));
  }

  protected int computeReceiptState(PurchaseOrder purchaseOrder) throws AxelorException {

    if (purchaseOrder.getPurchaseOrderLineList() == null
        || purchaseOrder.getPurchaseOrderLineList().isEmpty()) {
      return PurchaseOrderRepository.STATE_NOT_RECEIVED;
    }

    int receiptState = -1;

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {

      if (this.isStockMoveProduct(purchaseOrderLine, purchaseOrder)) {

        if (purchaseOrderLine.getReceiptState() == PurchaseOrderRepository.STATE_RECEIVED) {
          if (receiptState == PurchaseOrderRepository.STATE_NOT_RECEIVED
              || receiptState == PurchaseOrderRepository.STATE_PARTIALLY_RECEIVED) {
            return PurchaseOrderRepository.STATE_PARTIALLY_RECEIVED;
          } else {
            receiptState = PurchaseOrderRepository.STATE_RECEIVED;
          }
        } else if (purchaseOrderLine.getReceiptState()
            == PurchaseOrderRepository.STATE_NOT_RECEIVED) {
          if (receiptState == PurchaseOrderRepository.STATE_RECEIVED
              || receiptState == PurchaseOrderRepository.STATE_PARTIALLY_RECEIVED) {
            return PurchaseOrderRepository.STATE_PARTIALLY_RECEIVED;
          } else {
            receiptState = PurchaseOrderRepository.STATE_NOT_RECEIVED;
          }
        } else if (purchaseOrderLine.getReceiptState()
            == PurchaseOrderRepository.STATE_PARTIALLY_RECEIVED) {
          return PurchaseOrderRepository.STATE_PARTIALLY_RECEIVED;
        }
      }
    }
    return receiptState;
  }

  @Override
  public String getPurchaseOrderLineListForAProduct(
      Long productId, Long companyId, Long stockLocationId) {
    List<Integer> statusList = new ArrayList<>();
    statusList.add(PurchaseOrderRepository.STATUS_VALIDATED);
    String status =
        Beans.get(AppSupplychainService.class)
            .getAppSupplychain()
            .getpOFilterOnStockDetailStatusSelect();
    if (!StringUtils.isBlank(status)) {
      statusList = StringTool.getIntegerList(status);
    }
    String statusListQuery =
        statusList.stream().map(String::valueOf).collect(Collectors.joining(","));
    String query =
        "self.product.id = "
            + productId
            + " AND self.receiptState != "
            + PurchaseOrderLineRepository.RECEIPT_STATE_RECEIVED
            + " AND self.purchaseOrder.statusSelect IN ("
            + statusListQuery
            + ")";
    if (companyId != 0L) {
      query += " AND self.purchaseOrder.company.id = " + companyId;
      if (stockLocationId != 0L) {
        StockLocation stockLocation =
            Beans.get(StockLocationRepository.class).find(stockLocationId);
        List<StockLocation> stockLocationList =
            Beans.get(StockLocationService.class)
                .getAllLocationAndSubLocation(stockLocation, false);
        if (!stockLocationList.isEmpty() && stockLocation.getCompany().getId() == companyId) {
          query +=
              " AND self.purchaseOrder.stockLocation.id IN ("
                  + StringTool.getIdListString(stockLocationList)
                  + ") ";
        }
      }
    }
    return query;
  }
}
