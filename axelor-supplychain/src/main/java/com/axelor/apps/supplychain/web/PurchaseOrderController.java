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

import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderLineRepository;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.PurchaseOrderServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.PurchaseOrderStockServiceImpl;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.base.Joiner;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
public class PurchaseOrderController {

  public void createStockMove(ActionRequest request, ActionResponse response)
      throws AxelorException {

    PurchaseOrder purchaseOrder = request.getContext().asType(PurchaseOrder.class);

    try {
      if (purchaseOrder.getId() != null) {

        List<Long> stockMoveList =
            Beans.get(PurchaseOrderStockServiceImpl.class)
                .createStockMoveFromPurchaseOrder(
                    Beans.get(PurchaseOrderRepository.class).find(purchaseOrder.getId()));

        if (stockMoveList != null && stockMoveList.size() == 1) {
          response.setView(
              ActionView.define(I18n.get("Stock move"))
                  .model(StockMove.class.getName())
                  .add("grid", "stock-move-grid")
                  .add("form", "stock-move-form")
                  .param("forceEdit", "true")
                  .domain("self.id = " + stockMoveList.get(0))
                  .context("_showRecord", String.valueOf(stockMoveList.get(0)))
                  .map());
        } else if (stockMoveList != null && stockMoveList.size() > 1) {
          response.setView(
              ActionView.define(I18n.get("Stock move"))
                  .model(StockMove.class.getName())
                  .add("grid", "stock-move-grid")
                  .add("form", "stock-move-form")
                  .domain("self.id in (" + Joiner.on(",").join(stockMoveList) + ")")
                  .map());
        } else {
          response.setFlash(I18n.get(IExceptionMessage.PO_NO_DELIVERY_STOCK_MOVE_TO_GENERATE));
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void getStockLocation(ActionRequest request, ActionResponse response) {

    PurchaseOrder purchaseOrder = request.getContext().asType(PurchaseOrder.class);

    if (purchaseOrder.getCompany() != null) {

      response.setValue(
          "stockLocation",
          Beans.get(StockLocationService.class)
              .getDefaultReceiptStockLocation(purchaseOrder.getCompany()));
    }
  }

  public void cancelReceipt(ActionRequest request, ActionResponse response) throws AxelorException {

    PurchaseOrder purchaseOrder = request.getContext().asType(PurchaseOrder.class);
    purchaseOrder = Beans.get(PurchaseOrderRepository.class).find(purchaseOrder.getId());
    Beans.get(PurchaseOrderStockServiceImpl.class).cancelReceipt(purchaseOrder);
  }

  public void generateBudgetDistribution(ActionRequest request, ActionResponse response) {
    PurchaseOrder purchaseOrder = request.getContext().asType(PurchaseOrder.class);

    AppAccountService appAccountService = Beans.get(AppAccountService.class);

    if (appAccountService.isApp("budget")
        && !appAccountService.getAppBudget().getManageMultiBudget()) {
      purchaseOrder = Beans.get(PurchaseOrderRepository.class).find(purchaseOrder.getId());
      Beans.get(PurchaseOrderServiceSupplychainImpl.class)
          .generateBudgetDistribution(purchaseOrder);
      response.setValues(purchaseOrder);
    }
  }

  // Generate single purchase order from several
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void mergePurchaseOrder(ActionRequest request, ActionResponse response) {
    List<PurchaseOrder> purchaseOrderList = new ArrayList<>();
    List<Long> purchaseOrderIdList = new ArrayList<>();
    boolean fromPopup = false;

    if (request.getContext().get("purchaseOrderToMerge") != null) {

      if (request.getContext().get("purchaseOrderToMerge") instanceof List) {
        // No confirmation popup, purchase orders are content in a parameter list
        List<Map> purchaseOrderMap = (List<Map>) request.getContext().get("purchaseOrderToMerge");
        for (Map map : purchaseOrderMap) {
          purchaseOrderIdList.add(new Long((Integer) map.get("id")));
        }
      } else {
        // After confirmation popup, purchase order's id are in a string separated by
        // ","
        String purchaseOrderIdListStr = (String) request.getContext().get("purchaseOrderToMerge");
        for (String purchaseOrderId : purchaseOrderIdListStr.split(",")) {
          purchaseOrderIdList.add(new Long(purchaseOrderId));
        }
        fromPopup = true;
      }
    }

    // Check if currency, supplierPartner and company are the same for all selected
    // purchase orders
    Currency commonCurrency = null;
    Partner commonSupplierPartner = null;
    Company commonCompany = null;
    Partner commonContactPartner = null;
    TradingName commonTradingName = null;
    // Useful to determine if a difference exists between contact partners of all
    // purchase orders
    boolean existContactPartnerDiff = false;
    PriceList commonPriceList = null;
    // Useful to determine if a difference exists between price lists of all
    // purchase orders
    boolean existPriceListDiff = false;
    StockLocation commonLocation = null;
    // Useful to determine if a difference exists between stock locations of all
    // purchase orders
    boolean existLocationDiff = false;
    boolean allTradingNamesAreNull = true;

    PurchaseOrder purchaseOrderTemp;
    int count = 1;
    for (Long purchaseOrderId : purchaseOrderIdList) {
      purchaseOrderTemp = JPA.em().find(PurchaseOrder.class, purchaseOrderId);
      purchaseOrderList.add(purchaseOrderTemp);
      if (count == 1) {
        commonCurrency = purchaseOrderTemp.getCurrency();
        commonSupplierPartner = purchaseOrderTemp.getSupplierPartner();
        commonCompany = purchaseOrderTemp.getCompany();
        commonContactPartner = purchaseOrderTemp.getContactPartner();
        commonPriceList = purchaseOrderTemp.getPriceList();
        commonLocation = purchaseOrderTemp.getStockLocation();
        commonTradingName = purchaseOrderTemp.getTradingName();
        allTradingNamesAreNull = commonTradingName == null;
      } else {
        if (commonCurrency != null && !commonCurrency.equals(purchaseOrderTemp.getCurrency())) {
          commonCurrency = null;
        }
        if (commonSupplierPartner != null
            && !commonSupplierPartner.equals(purchaseOrderTemp.getSupplierPartner())) {
          commonSupplierPartner = null;
        }
        if (commonCompany != null && !commonCompany.equals(purchaseOrderTemp.getCompany())) {
          commonCompany = null;
        }
        if (!Objects.equals(commonTradingName, purchaseOrderTemp.getTradingName())) {
          commonTradingName = null;
          allTradingNamesAreNull = false;
        }
        if (commonContactPartner != null
            && !commonContactPartner.equals(purchaseOrderTemp.getContactPartner())) {
          commonContactPartner = null;
          existContactPartnerDiff = true;
        }
        if (commonPriceList != null && !commonPriceList.equals(purchaseOrderTemp.getPriceList())) {
          commonPriceList = null;
          existPriceListDiff = true;
        }
        if (commonLocation != null
            && !commonLocation.equals(purchaseOrderTemp.getStockLocation())) {
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
              com.axelor.apps.purchase.exception.IExceptionMessage
                  .PURCHASE_ORDER_MERGE_ERROR_CURRENCY));
    }
    if (commonSupplierPartner == null) {
      if (fieldErrors.length() > 0) {
        fieldErrors.append("<br/>");
      }
      fieldErrors.append(
          I18n.get(
              com.axelor.apps.purchase.exception.IExceptionMessage
                  .PURCHASE_ORDER_MERGE_ERROR_SUPPLIER_PARTNER));
    }
    if (commonCompany == null) {
      if (fieldErrors.length() > 0) {
        fieldErrors.append("<br/>");
      }
      fieldErrors.append(
          I18n.get(
              com.axelor.apps.purchase.exception.IExceptionMessage
                  .PURCHASE_ORDER_MERGE_ERROR_COMPANY));
    }
    if (commonTradingName == null && !allTradingNamesAreNull) {
      fieldErrors.append(
          I18n.get(
              com.axelor.apps.purchase.exception.IExceptionMessage
                  .PURCHASE_ORDER_MERGE_ERROR_TRADING_NAME));
    }

    if (fieldErrors.length() > 0) {
      response.setFlash(fieldErrors.toString());
      return;
    }

    // Check if priceList or contactPartner or stock location are content in
    // parameters
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
    if (request.getContext().get("stockLocation") != null) {
      commonLocation =
          JPA.em()
              .find(
                  StockLocation.class,
                  new Long((Integer) ((Map) request.getContext().get("stockLocation")).get("id")));
    }

    if (!fromPopup && (existContactPartnerDiff || existPriceListDiff || existLocationDiff)) {
      // Need to display intermediate screen to select some values
      ActionViewBuilder confirmView =
          ActionView.define("Confirm merge purchase order")
              .model(Wizard.class.getName())
              .add("form", "purchase-order-merge-confirm-form")
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
        confirmView.context("contextPartnerId", commonSupplierPartner.getId().toString());
      }
      if (existLocationDiff) {
        confirmView.context("contextLocationToCheck", "true");
      }

      confirmView.context("purchaseOrderToMerge", Joiner.on(",").join(purchaseOrderIdList));

      response.setView(confirmView.map());

      return;
    }

    try {
      PurchaseOrder purchaseOrder =
          Beans.get(PurchaseOrderServiceSupplychainImpl.class)
              .mergePurchaseOrders(
                  purchaseOrderList,
                  commonCurrency,
                  commonSupplierPartner,
                  commonCompany,
                  commonLocation,
                  commonContactPartner,
                  commonPriceList,
                  commonTradingName);
      if (purchaseOrder != null) {
        // Open the generated purchase order in a new tab
        response.setView(
            ActionView.define("Purchase order")
                .model(PurchaseOrder.class.getName())
                .add("grid", "purchase-order-grid")
                .add("form", "purchase-order-form")
                .param("forceEdit", "true")
                .context("_showRecord", String.valueOf(purchaseOrder.getId()))
                .map());
        response.setCanClose(true);
      }
    } catch (Exception e) {
      response.setFlash(e.getLocalizedMessage());
    }
  }

  public void updateAmountToBeSpreadOverTheTimetable(
      ActionRequest request, ActionResponse response) {
    PurchaseOrder purchaseOrder = request.getContext().asType(PurchaseOrder.class);
    Beans.get(PurchaseOrderServiceSupplychainImpl.class)
        .updateAmountToBeSpreadOverTheTimetable(purchaseOrder);
    response.setValue(
        "amountToBeSpreadOverTheTimetable", purchaseOrder.getAmountToBeSpreadOverTheTimetable());
  }

  public void applyToAllBudgetDistribution(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    PurchaseOrder purchaseOrder = context.asType(PurchaseOrder.class);
    String previousBudgetId = context.get("previousBudgetId").toString();

    purchaseOrder = Beans.get(PurchaseOrderRepository.class).find(purchaseOrder.getId());
    Beans.get(PurchaseOrderServiceSupplychainImpl.class)
        .applyToallBudgetDistribution(purchaseOrder, previousBudgetId);
    response.setAttr("$previousBudgetId", "value", purchaseOrder.getBudget().getId().toString());
    response.setAttr("applyToAllBtn", "readonly", true);
  }

  public void updateEstimatedDelivDate(ActionRequest request, ActionResponse response) {
    PurchaseOrder purchaseOrder = request.getContext().asType(PurchaseOrder.class);

    List<PurchaseOrderLine> purchaseOrderLineList = purchaseOrder.getPurchaseOrderLineList();
    if (purchaseOrderLineList != null) {
      for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLineList) {
        Integer receiptState = purchaseOrderLine.getReceiptState();
        if (receiptState != null
            && !receiptState.equals(PurchaseOrderLineRepository.RECEIPT_STATE_RECEIVED)
            && !receiptState.equals(PurchaseOrderLineRepository.RECEIPT_STATE_PARTIALLY_RECEIVED)) {
          purchaseOrderLine.setEstimatedDelivDate(purchaseOrder.getDeliveryDate());
        }
      }
    }
    response.setValue("purchaseOrderLineList", purchaseOrderLineList);
  }

  /**
   * Called from purchase order form view when validating purchase order and analytic distribution
   * is required from company's purchase config.
   *
   * @param request
   * @param response
   */
  public void checkPurchaseOrderAnalyticDistributionTemplate(
      ActionRequest request, ActionResponse response) {
    try {
      PurchaseOrder purchaseOrder = request.getContext().asType(PurchaseOrder.class);
      List<String> productList = new ArrayList<String>();
      for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
        if (purchaseOrderLine.getAnalyticDistributionTemplate() == null) {
          productList.add(purchaseOrderLine.getProduct().getFullName());
        }
      }
      if (productList != null && !productList.isEmpty()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.PURCHASE_ORDER_ANALYTIC_DISTRIBUTION_ERROR),
            productList);
      }
    } catch (AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
