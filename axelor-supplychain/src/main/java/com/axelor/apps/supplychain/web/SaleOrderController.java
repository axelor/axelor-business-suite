/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.supplychain.web;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.SaleOrderCreateServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.apps.supplychain.service.SaleOrderPurchaseService;
import com.axelor.apps.supplychain.service.SaleOrderReservedQtyService;
import com.axelor.apps.supplychain.service.SaleOrderServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.SaleOrderStockService;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.db.JPA;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.axelor.team.db.Team;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SaleOrderController {

  private final String SO_LINES_WIZARD_QTY_TO_INVOICE_FIELD = "qtyToInvoice";

  @Inject private SaleOrderServiceSupplychainImpl saleOrderServiceSupplychain;

  @Inject private SaleOrderRepository saleOrderRepo;

  @Inject private SaleOrderLineRepository saleOrderLineRepo;

  public void createStockMove(ActionRequest request, ActionResponse response) {

    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

    try {
      if (saleOrder.getId() != null) {

        SaleOrderStockService saleOrderStockService = Beans.get(SaleOrderStockService.class);
        List<Long> stockMoveList =
            saleOrderStockService.createStocksMovesFromSaleOrder(
                saleOrderRepo.find(saleOrder.getId()));

        if (stockMoveList != null && stockMoveList.size() == 1) {
          response.setView(
              ActionView.define(I18n.get("Stock move"))
                  .model(StockMove.class.getName())
                  .add("form", "stock-move-form")
                  .add("grid", "stock-move-grid")
                  .param("forceEdit", "true")
                  .domain("self.id = " + stockMoveList.get(0))
                  .context("_showRecord", String.valueOf(stockMoveList.get(0)))
                  .context("_userType", StockMoveRepository.USER_TYPE_SALESPERSON)
                  .map());
        } else if (stockMoveList != null && stockMoveList.size() > 1) {
          response.setView(
              ActionView.define(I18n.get("Stock move"))
                  .model(StockMove.class.getName())
                  .add("grid", "stock-move-grid")
                  .add("form", "stock-move-form")
                  .domain("self.id in (" + Joiner.on(",").join(stockMoveList) + ")")
                  .context("_userType", StockMoveRepository.USER_TYPE_SALESPERSON)
                  .map());
        } else {
          response.setFlash(I18n.get(IExceptionMessage.SO_NO_DELIVERY_STOCK_MOVE_TO_GENERATE));
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void getStockLocation(ActionRequest request, ActionResponse response) {

    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

    if (saleOrder != null && saleOrder.getCompany() != null) {

      StockLocation stockLocation =
          Beans.get(StockLocationService.class)
              .getPickupDefaultStockLocation(saleOrder.getCompany());

      if (stockLocation != null) {
        response.setValue("stockLocation", stockLocation);
      }
    }
  }

  @SuppressWarnings("rawtypes")
  public void generatePurchaseOrdersFromSelectedSOLines(
      ActionRequest request, ActionResponse response) {

    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

    try {
      if (saleOrder.getId() != null) {

        if (request.getContext().get("supplierPartnerSelect") != null) {
          Partner partner =
              JPA.em()
                  .find(
                      Partner.class,
                      new Long(
                          (Integer)
                              ((Map) request.getContext().get("supplierPartnerSelect")).get("id")));
          List<Long> saleOrderLineIdSelected = new ArrayList<>();
          String saleOrderLineIdSelectedStr =
              (String) request.getContext().get("saleOrderLineIdSelected");
          for (String saleOrderId : saleOrderLineIdSelectedStr.split(",")) {
            saleOrderLineIdSelected.add(new Long(saleOrderId));
          }
          List<SaleOrderLine> saleOrderLinesSelected =
              JPA.all(SaleOrderLine.class)
                  .filter("self.id IN (:saleOderLineIdList)")
                  .bind("saleOderLineIdList", saleOrderLineIdSelected)
                  .fetch();
          PurchaseOrder purchaseOrder =
              Beans.get(SaleOrderPurchaseService.class)
                  .createPurchaseOrder(
                      partner, saleOrderLinesSelected, saleOrderRepo.find(saleOrder.getId()));
          response.setView(
              ActionView.define(I18n.get("Purchase order"))
                  .model(PurchaseOrder.class.getName())
                  .add("form", "purchase-order-form")
                  .param("forceEdit", "true")
                  .context("_showRecord", String.valueOf(purchaseOrder.getId()))
                  .map());
          response.setCanClose(true);
        } else {
          Partner supplierPartner = null;
          List<Long> saleOrderLineIdSelected = new ArrayList<>();

          // Check if supplier partners of each sale order line are the same. If it is,
          // send the
          // partner id to view to load this partner by default into select
          for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
            if (saleOrderLine.isSelected()) {
              if (supplierPartner == null) {
                supplierPartner = saleOrderLine.getSupplierPartner();
              } else {
                if (!supplierPartner.equals(saleOrderLine.getSupplierPartner())) {
                  supplierPartner = null;
                  break;
                }
              }
              saleOrderLineIdSelected.add(saleOrderLine.getId());
            }
          }

          if (saleOrderLineIdSelected.isEmpty()) {
            response.setFlash(I18n.get(IExceptionMessage.SO_LINE_PURCHASE_AT_LEAST_ONE));
          } else {
            response.setView(
                ActionView.define("SaleOrder")
                    .model(SaleOrder.class.getName())
                    .add("form", "sale-order-generate-po-select-supplierpartner-form")
                    .param("popup", "true")
                    .param("show-toolbar", "false")
                    .param("show-confirm", "false")
                    .param("popup-save", "false")
                    .param("forceEdit", "true")
                    .context("_showRecord", String.valueOf(saleOrder.getId()))
                    .context(
                        "supplierPartnerId",
                        ((supplierPartner != null) ? supplierPartner.getId() : 0L))
                    .context(
                        "saleOrderLineIdSelected", Joiner.on(",").join(saleOrderLineIdSelected))
                    .map());
          }
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from the sale order invoicing wizard. Call {@link
   * com.axelor.apps.supplychain.service.SaleOrderInvoiceService#generateInvoice(SaleOrder, int,
   * BigDecimal, boolean, Map)} } Return to the view the generated invoice.
   *
   * @param request
   * @param response
   */
  @SuppressWarnings(value = "unchecked")
  public void generateInvoice(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    try {
      SaleOrder saleOrder = context.asType(SaleOrder.class);
      int operationSelect = Integer.parseInt(context.get("operationSelect").toString());
      boolean isPercent = (Boolean) context.getOrDefault("isPercent", false);
      BigDecimal amountToInvoice =
          new BigDecimal(context.getOrDefault("amountToInvoice", "0").toString());

      Beans.get(SaleOrderInvoiceService.class)
          .displayErrorMessageIfSaleOrderIsInvoiceable(saleOrder, amountToInvoice, isPercent);
      Map<Long, BigDecimal> qtyToInvoiceMap = new HashMap<>();

      List<Map<String, Object>> saleOrderLineListContext;
      saleOrderLineListContext =
          (List<Map<String, Object>>) request.getRawContext().get("saleOrderLineList");
      for (Map<String, Object> map : saleOrderLineListContext) {
        if (map.get(SO_LINES_WIZARD_QTY_TO_INVOICE_FIELD) != null) {
          BigDecimal qtyToInvoiceItem =
              new BigDecimal(map.get(SO_LINES_WIZARD_QTY_TO_INVOICE_FIELD).toString());
          if (qtyToInvoiceItem.compareTo(BigDecimal.ZERO) != 0) {
            Long soLineId = Long.valueOf((Integer) map.get("id"));
            addSubLineQty(qtyToInvoiceMap, qtyToInvoiceItem, soLineId);
            qtyToInvoiceMap.put(soLineId, qtyToInvoiceItem);
          }
        }
      }

      saleOrder = saleOrderRepo.find(saleOrder.getId());

      SaleOrderInvoiceService saleOrderInvoiceService = Beans.get(SaleOrderInvoiceService.class);

      Invoice invoice =
          saleOrderInvoiceService.generateInvoice(
              saleOrder, operationSelect, amountToInvoice, isPercent, qtyToInvoiceMap);

      if (invoice != null) {
        response.setCanClose(true);
        response.setView(
            ActionView.define(I18n.get("Invoice generated"))
                .model(Invoice.class.getName())
                .add("form", "invoice-form")
                .add("grid", "invoice-grid")
                .context("_showRecord", String.valueOf(invoice.getId()))
                .context("_operationTypeSelect", InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)
                .context("todayDate", Beans.get(AppSupplychainService.class).getTodayDate())
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  private void addSubLineQty(
      Map<Long, BigDecimal> qtyToInvoiceMap, BigDecimal qtyToInvoiceItem, Long soLineId) {

    SaleOrderLine saleOrderLine = saleOrderLineRepo.find(soLineId);

    if (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_PACK) {
      for (SaleOrderLine subLine : saleOrderLine.getSubLineList()) {
        BigDecimal qty = BigDecimal.ZERO;
        if (saleOrderLine.getQty().compareTo(BigDecimal.ZERO) != 0) {
          qty =
              qtyToInvoiceItem
                  .multiply(subLine.getQty())
                  .divide(saleOrderLine.getQty(), 2, RoundingMode.HALF_EVEN);
        }
        qty = qty.setScale(2, RoundingMode.HALF_EVEN);
        qtyToInvoiceMap.put(subLine.getId(), qty);
      }
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public void mergeSaleOrder(ActionRequest request, ActionResponse response) {
    List<SaleOrder> saleOrderList = new ArrayList<>();
    List<Long> saleOrderIdList = new ArrayList<>();
    boolean fromPopup = false;
    String lineToMerge;
    if (request.getContext().get("saleQuotationToMerge") != null) {
      lineToMerge = "saleQuotationToMerge";
    } else {
      lineToMerge = "saleOrderToMerge";
    }

    if (request.getContext().get(lineToMerge) != null) {

      if (request.getContext().get(lineToMerge) instanceof List) {
        // No confirmation popup, sale orders are content in a parameter list
        List<Map> saleOrderMap = (List<Map>) request.getContext().get(lineToMerge);
        for (Map map : saleOrderMap) {
          saleOrderIdList.add(new Long((Integer) map.get("id")));
        }
      } else {
        // After confirmation popup, sale order's id are in a string separated by ","
        String saleOrderIdListStr = (String) request.getContext().get(lineToMerge);
        for (String saleOrderId : saleOrderIdListStr.split(",")) {
          saleOrderIdList.add(new Long(saleOrderId));
        }
        fromPopup = true;
      }
    }
    // Check if currency, clientPartner and company are the same for all selected
    // sale orders
    Currency commonCurrency = null;
    Partner commonClientPartner = null;
    Company commonCompany = null;
    Partner commonContactPartner = null;
    Team commonTeam = null;
    // Useful to determine if a difference exists between teams of all sale orders
    boolean existTeamDiff = false;
    // Useful to determine if a difference exists between contact partners of all
    // sale orders
    boolean existContactPartnerDiff = false;
    PriceList commonPriceList = null;
    // Useful to determine if a difference exists between price lists of all sale
    // orders
    boolean existPriceListDiff = false;
    StockLocation commonLocation = null;
    // Useful to determine if a difference exists between stock locations of all
    // sale orders
    boolean existLocationDiff = false;

    SaleOrder saleOrderTemp;
    int count = 1;
    for (Long saleOrderId : saleOrderIdList) {
      saleOrderTemp = JPA.em().find(SaleOrder.class, saleOrderId);
      saleOrderList.add(saleOrderTemp);
      if (count == 1) {
        commonCurrency = saleOrderTemp.getCurrency();
        commonClientPartner = saleOrderTemp.getClientPartner();
        commonCompany = saleOrderTemp.getCompany();
        commonContactPartner = saleOrderTemp.getContactPartner();
        commonTeam = saleOrderTemp.getTeam();
        commonPriceList = saleOrderTemp.getPriceList();
        commonLocation = saleOrderTemp.getStockLocation();
      } else {
        if (commonCurrency != null && !commonCurrency.equals(saleOrderTemp.getCurrency())) {
          commonCurrency = null;
        }
        if (commonClientPartner != null
            && !commonClientPartner.equals(saleOrderTemp.getClientPartner())) {
          commonClientPartner = null;
        }
        if (commonCompany != null && !commonCompany.equals(saleOrderTemp.getCompany())) {
          commonCompany = null;
        }
        if (commonContactPartner != null
            && !commonContactPartner.equals(saleOrderTemp.getContactPartner())) {
          commonContactPartner = null;
          existContactPartnerDiff = true;
        }
        if (commonTeam != null && !commonTeam.equals(saleOrderTemp.getTeam())) {
          commonTeam = null;
          existTeamDiff = true;
        }
        if (commonPriceList != null && !commonPriceList.equals(saleOrderTemp.getPriceList())) {
          commonPriceList = null;
          existPriceListDiff = true;
        }
        if (commonLocation != null && !commonLocation.equals(saleOrderTemp.getStockLocation())) {
          commonLocation = null;
          existLocationDiff = true;
        }
      }
      count++;
    }

    StringBuilder fieldErrors = new StringBuilder();
    if (commonCurrency == null) {
      fieldErrors.append(
          I18n.get(
              com.axelor.apps.sale.exception.IExceptionMessage.SALE_ORDER_MERGE_ERROR_CURRENCY));
    }
    if (commonClientPartner == null) {
      if (fieldErrors.length() > 0) {
        fieldErrors.append("<br/>");
      }
      fieldErrors.append(
          I18n.get(
              com.axelor.apps.sale.exception.IExceptionMessage
                  .SALE_ORDER_MERGE_ERROR_CLIENT_PARTNER));
    }
    if (commonCompany == null) {
      if (fieldErrors.length() > 0) {
        fieldErrors.append("<br/>");
      }
      fieldErrors.append(
          I18n.get(
              com.axelor.apps.sale.exception.IExceptionMessage.SALE_ORDER_MERGE_ERROR_COMPANY));
    }

    if (fieldErrors.length() > 0) {
      response.setFlash(fieldErrors.toString());
      return;
    }

    // Check if priceList or contactPartner are content in parameters
    if (request.getContext().get("priceList") != null) {
      commonPriceList =
          JPA.em()
              .find(
                  PriceList.class,
                  new Long((Integer) ((Map) request.getContext().get("priceList")).get("id")));
    }
    if (request.getContext().get("contactPartner") != null) {
      commonContactPartner =
          JPA.em()
              .find(
                  Partner.class,
                  new Long((Integer) ((Map) request.getContext().get("contactPartner")).get("id")));
    }
    if (request.getContext().get("team") != null) {
      commonTeam =
          JPA.em()
              .find(
                  Team.class,
                  new Long((Integer) ((Map) request.getContext().get("team")).get("id")));
    }
    if (request.getContext().get("stockLocation") != null) {
      commonLocation =
          JPA.em()
              .find(
                  StockLocation.class,
                  new Long((Integer) ((Map) request.getContext().get("stockLocation")).get("id")));
    }

    if (!fromPopup && (existContactPartnerDiff || existPriceListDiff || existTeamDiff)) {
      // Need to display intermediate screen to select some values
      ActionViewBuilder confirmView =
          ActionView.define("Confirm merge sale order")
              .model(Wizard.class.getName())
              .add("form", "sale-order-merge-confirm-form")
              .param("popup", "true")
              .param("show-toolbar", "false")
              .param("show-confirm", "false")
              .param("popup-save", "false")
              .param("forceEdit", "true");

      if (existPriceListDiff) {
        confirmView.context("contextPriceListToCheck", "true");
      }
      if (existContactPartnerDiff) {
        confirmView.context("contextContactPartnerToCheck", "true");
        confirmView.context("contextPartnerId", commonClientPartner.getId().toString());
      }
      if (existTeamDiff) {
        confirmView.context("contextTeamToCheck", "true");
      }
      if (existLocationDiff) {
        confirmView.context("contextLocationToCheck", "true");
      }

      confirmView.context(lineToMerge, Joiner.on(",").join(saleOrderIdList));

      response.setView(confirmView.map());

      return;
    }

    try {
      SaleOrder saleOrder =
          Beans.get(SaleOrderCreateServiceSupplychainImpl.class)
              .mergeSaleOrders(
                  saleOrderList,
                  commonCurrency,
                  commonClientPartner,
                  commonCompany,
                  commonLocation,
                  commonContactPartner,
                  commonPriceList,
                  commonTeam);
      if (saleOrder != null) {
        // Open the generated sale order in a new tab
        response.setView(
            ActionView.define("Sale order")
                .model(SaleOrder.class.getName())
                .add("grid", "sale-order-grid")
                .add("form", "sale-order-form")
                .param("forceEdit", "true")
                .context("_showRecord", String.valueOf(saleOrder.getId()))
                .map());
        response.setCanClose(true);
      }
    } catch (Exception e) {
      response.setFlash(e.getLocalizedMessage());
    }
  }

  public void updateAmountToBeSpreadOverTheTimetable(
      ActionRequest request, ActionResponse response) {
    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
    saleOrderServiceSupplychain.updateAmountToBeSpreadOverTheTimetable(saleOrder);
    response.setValue(
        "amountToBeSpreadOverTheTimetable", saleOrder.getAmountToBeSpreadOverTheTimetable());
  }

  /**
   * Called from sale order on save. Call {@link
   * SaleOrderServiceSupplychainImpl#checkModifiedConfirmedOrder(SaleOrder, SaleOrder)}.
   *
   * @param request
   * @param response
   */
  public void onSave(ActionRequest request, ActionResponse response) {
    try {
      SaleOrder saleOrderView = request.getContext().asType(SaleOrder.class);
      if (saleOrderView.getOrderBeingEdited()) {
        SaleOrder saleOrder = saleOrderRepo.find(saleOrderView.getId());
        saleOrderServiceSupplychain.checkModifiedConfirmedOrder(saleOrder, saleOrderView);
        response.setValues(saleOrderView);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  /**
   * Called on sale order invoicing wizard form. Call {@link
   * SaleOrderInvoiceService#getInvoicingWizardOperationDomain(SaleOrder)}
   *
   * @param request
   * @param response
   */
  public void changeWizardOperationDomain(ActionRequest request, ActionResponse response) {
    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
    Map<String, Integer> contextValues =
        Beans.get(SaleOrderInvoiceService.class).getInvoicingWizardOperationDomain(saleOrder);
    response.setValues(contextValues);
  }

  /**
   * Called from sale order generate purchase order form. Set domain for supplier partner.
   *
   * @param request
   * @param response
   */
  public void supplierPartnerSelectDomain(ActionRequest request, ActionResponse response) {
    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
    String domain = "self.isContact = false AND self.isSupplier = true";

    String blockedPartnerQuery =
        Beans.get(BlockingService.class)
            .listOfBlockedPartner(saleOrder.getCompany(), BlockingRepository.PURCHASE_BLOCKING);

    if (!Strings.isNullOrEmpty(blockedPartnerQuery)) {
      domain += String.format(" AND self.id NOT in (%s)", blockedPartnerQuery);
    }

    if (saleOrder.getCompany() != null) {
      domain += " AND " + saleOrder.getCompany().getId() + " in (SELECT id FROM self.companySet)";
    }
    response.setAttr("supplierPartnerSelect", "domain", domain);
  }

  public void setNextInvoicingStartPeriodDate(ActionRequest request, ActionResponse response) {

    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

    TemporalUnit temporalUnit = ChronoUnit.MONTHS;

    if (saleOrder.getPeriodicityTypeSelect() != null
        && saleOrder.getNextInvoicingStartPeriodDate() != null) {
      LocalDate invoicingPeriodStartDate = saleOrder.getNextInvoicingStartPeriodDate();
      if (saleOrder.getPeriodicityTypeSelect() == 1) {
        temporalUnit = ChronoUnit.DAYS;
      }
      LocalDate subscriptionToDate =
          invoicingPeriodStartDate.plus(saleOrder.getNumberOfPeriods(), temporalUnit);
      subscriptionToDate = subscriptionToDate.minusDays(1);
      response.setValue("nextInvoicingEndPeriodDate", subscriptionToDate);
    }
  }

  /**
   * Called on load of sale order invoicing wizard view. Fill dummy field with default value to
   * avoid issues with null values.
   *
   * @param request
   * @param response
   */
  public void fillDefaultValueWizard(ActionRequest request, ActionResponse response) {
    try {
      SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
      List<Map<String, Object>> saleOrderLineList = new ArrayList<>();
      for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
        if (saleOrderLine.getIsSubLine()) {
          continue;
        }
        Map<String, Object> saleOrderLineMap = Mapper.toMap(saleOrderLine);
        saleOrderLineMap.put(SO_LINES_WIZARD_QTY_TO_INVOICE_FIELD, BigDecimal.ZERO);
        saleOrderLineList.add(saleOrderLineMap);
      }
      response.setValue("amountToInvoice", BigDecimal.ZERO);
      response.setValue("saleOrderLineList", saleOrderLineList);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void fillSaleOrderLinesEstimatedDate(ActionRequest request, ActionResponse response) {
    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

    List<SaleOrderLine> saleOrderLineList = saleOrder.getSaleOrderLineList();
    if (saleOrderLineList != null) {
      for (SaleOrderLine saleOrderLine : saleOrderLineList) {
        Integer deliveryState = saleOrderLine.getDeliveryState();
        if (!deliveryState.equals(SaleOrderLineRepository.DELIVERY_STATE_DELIVERED)
            && !deliveryState.equals(SaleOrderLineRepository.DELIVERY_STATE_PARTIALLY_DELIVERED)) {
          saleOrderLine.setEstimatedDelivDate(saleOrder.getDeliveryDate());
        }
      }
    }

    response.setValue("saleOrderLineList", saleOrderLineList);
  }

  public void notifyStockMoveCreated(ActionRequest request, ActionResponse response) {
    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
    StockMoveRepository stockMoveRepo = Beans.get(StockMoveRepository.class);
    StockMove stockMove =
        stockMoveRepo
            .all()
            .filter(
                "self.originTypeSelect = ?1 AND self.originId = ?2 AND self.statusSelect = ?3",
                "com.axelor.apps.sale.db.SaleOrder",
                saleOrder.getId(),
                StockMoveRepository.STATUS_PLANNED)
            .fetchOne();
    if (stockMove != null) {
      response.setNotify(
          String.format(
              I18n.get(IExceptionMessage.SALE_ORDER_STOCK_MOVE_CREATED),
              stockMove.getStockMoveSeq()));
    }
  }

  /**
   * Called from the toolbar in sale order form view. Call {@link
   * com.axelor.apps.supplychain.service.SaleOrderReservedQtyService#allocateAll(SaleOrder)}.
   *
   * @param request
   * @param response
   */
  public void allocateAll(ActionRequest request, ActionResponse response) {
    try {
      SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
      saleOrder = saleOrderRepo.find(saleOrder.getId());
      Beans.get(SaleOrderReservedQtyService.class).allocateAll(saleOrder);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from the toolbar in sale order form view. Call {@link
   * com.axelor.apps.supplychain.service.SaleOrderReservedQtyService#deallocateAll(SaleOrder)}.
   *
   * @param request
   * @param response
   */
  public void deallocateAll(ActionRequest request, ActionResponse response) {
    try {
      SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
      saleOrder = saleOrderRepo.find(saleOrder.getId());
      Beans.get(SaleOrderReservedQtyService.class).deallocateAll(saleOrder);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from the toolbar in sale order form view. Call {@link
   * com.axelor.apps.supplychain.service.SaleOrderReservedQtyService#reserveAll(SaleOrder)}.
   *
   * @param request
   * @param response
   */
  public void reserveAll(ActionRequest request, ActionResponse response) {
    try {
      SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
      saleOrder = saleOrderRepo.find(saleOrder.getId());
      Beans.get(SaleOrderReservedQtyService.class).reserveAll(saleOrder);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from the toolbar in sale order form view. Call {@link
   * com.axelor.apps.supplychain.service.SaleOrderReservedQtyService#cancelReservation(SaleOrder)}.
   *
   * @param request
   * @param response
   */
  public void cancelReservation(ActionRequest request, ActionResponse response) {
    try {
      SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
      saleOrder = saleOrderRepo.find(saleOrder.getId());
      Beans.get(SaleOrderReservedQtyService.class).cancelReservation(saleOrder);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void showPopUpInvoicingWizard(ActionRequest request, ActionResponse response) {
    try {
      SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
      saleOrder = saleOrderRepo.find(saleOrder.getId());
      Beans.get(SaleOrderInvoiceService.class).displayErrorMessageBtnGenerateInvoice(saleOrder);
      response.setView(
          ActionView.define("Invoicing")
              .model(SaleOrder.class.getName())
              .add("form", "sale-order-invoicing-wizard-form")
              .param("popup", "reload")
              .param("show-toolbar", "false")
              .param("show-confirm", "false")
              .param("popup-save", "false")
              .param("forceEdit", "true")
              .context("_showRecord", String.valueOf(saleOrder.getId()))
              .map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void checkSaleOrderAnalyticDistributionTemplate(
      ActionRequest request, ActionResponse response) {
    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
    List<String> ProductList = new ArrayList<String>();
    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
      if (saleOrderLine.getAnalyticDistributionTemplate() == null) {
        ProductList.add(saleOrderLine.getProduct().getFullName());
      }
    }
    if (ProductList != null && !ProductList.isEmpty()) {
      response.setFlash(
          String.format(
              I18n.get(IExceptionMessage.SALE_ORDER_ANALYTIC_DISTRIBUTION_ERROR), ProductList));
    }
  }
}
