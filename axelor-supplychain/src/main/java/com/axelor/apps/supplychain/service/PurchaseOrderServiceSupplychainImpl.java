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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.account.db.Budget;
import com.axelor.apps.account.db.BudgetDistribution;
import com.axelor.apps.account.db.BudgetLine;
import com.axelor.apps.account.db.repo.BudgetDistributionRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderLineRepository;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.purchase.service.PurchaseOrderLineService;
import com.axelor.apps.purchase.service.PurchaseOrderServiceImpl;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.ShipmentMode;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.supplychain.db.Timetable;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurchaseOrderServiceSupplychainImpl extends PurchaseOrderServiceImpl
    implements PurchaseOrderSupplychainService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AppSupplychainService appSupplychainService;
  protected AccountConfigService accountConfigService;
  protected AppAccountService appAccountService;
  protected AppBaseService appBaseService;
  protected PurchaseOrderStockService purchaseOrderStockService;
  protected BudgetSupplychainService budgetSupplychainService;
  protected PurchaseOrderLineRepository purchaseOrderLineRepository;
  protected PurchaseOrderLineService purchaseOrderLineService;

  @Inject
  public PurchaseOrderServiceSupplychainImpl(
      AppSupplychainService appSupplychainService,
      AccountConfigService accountConfigService,
      AppAccountService appAccountService,
      AppBaseService appBaseService,
      PurchaseOrderStockService purchaseOrderStockService,
      BudgetSupplychainService budgetSupplychainService,
      PurchaseOrderLineRepository purchaseOrderLineRepository,
      PurchaseOrderLineService purchaseOrderLineService) {

    this.appSupplychainService = appSupplychainService;
    this.accountConfigService = accountConfigService;
    this.appAccountService = appAccountService;
    this.appBaseService = appBaseService;
    this.purchaseOrderStockService = purchaseOrderStockService;
    this.budgetSupplychainService = budgetSupplychainService;
    this.purchaseOrderLineRepository = purchaseOrderLineRepository;
    this.purchaseOrderLineService = purchaseOrderLineService;
  }

  @Override
  public PurchaseOrder createPurchaseOrder(
      User buyerUser,
      Company company,
      Partner contactPartner,
      Currency currency,
      LocalDate deliveryDate,
      String internalReference,
      String externalReference,
      StockLocation stockLocation,
      LocalDate orderDate,
      PriceList priceList,
      Partner supplierPartner,
      TradingName tradingName)
      throws AxelorException {

    LOG.debug(
        "Création d'une commande fournisseur : Société = {},  Reference externe = {}, Fournisseur = {}",
        company.getName(),
        externalReference,
        supplierPartner.getFullName());

    PurchaseOrder purchaseOrder =
        super.createPurchaseOrder(
            buyerUser,
            company,
            contactPartner,
            currency,
            deliveryDate,
            internalReference,
            externalReference,
            orderDate,
            priceList,
            supplierPartner,
            tradingName);

    purchaseOrder.setStockLocation(stockLocation);

    purchaseOrder.setPaymentMode(supplierPartner.getOutPaymentMode());
    purchaseOrder.setPaymentCondition(supplierPartner.getPaymentCondition());

    if (purchaseOrder.getPaymentMode() == null) {
      purchaseOrder.setPaymentMode(
          this.accountConfigService.getAccountConfig(company).getOutPaymentMode());
    }

    if (purchaseOrder.getPaymentCondition() == null) {
      purchaseOrder.setPaymentCondition(
          this.accountConfigService.getAccountConfig(company).getDefPaymentCondition());
    }

    purchaseOrder.setTradingName(tradingName);

    return purchaseOrder;
  }

  @Transactional
  @Override
  public void generateBudgetDistribution(PurchaseOrder purchaseOrder) {
    List<PurchaseOrderLine> purchaseOrderLines = purchaseOrder.getPurchaseOrderLineList();
    List<Budget> updateBudgetList = new ArrayList<>();

    if (!CollectionUtils.isEmpty(purchaseOrderLines)) {

      for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLines) {
        Budget purchaseOrderLineBudget = purchaseOrderLine.getBudget();
        List<BudgetDistribution> budgetDistributions =
            purchaseOrderLine.getBudgetDistributionList();

        if (!CollectionUtils.isEmpty(budgetDistributions)) {
          BudgetDistribution tempBudgetDistribution = null;

          for (BudgetDistribution budgetDistribution : budgetDistributions) {
            Budget budget = budgetDistribution.getBudget();

            if (purchaseOrderLineBudget != null) {

              if (!purchaseOrderLineBudget.equals(budget)) {
                updateBudgetList.add(budget);
                updateBudgetList.add(purchaseOrderLineBudget);
                budgetDistribution.setBudget(purchaseOrderLineBudget);
                budgetDistribution.setAmount(purchaseOrderLine.getCompanyExTaxTotal());
              }

              tempBudgetDistribution = budgetDistribution;
            } else {
              updateBudgetList.add(budget);
            }
          }
          purchaseOrderLine.clearBudgetDistributionList();

          if (tempBudgetDistribution != null) {
            purchaseOrderLine.addBudgetDistributionListItem(tempBudgetDistribution);
          }
        } else if (purchaseOrderLineBudget != null) {
          BudgetDistribution budgetDistribution = new BudgetDistribution();
          budgetDistribution.setBudget(purchaseOrderLineBudget);
          budgetDistribution.setAmount(purchaseOrderLine.getCompanyExTaxTotal());
          purchaseOrderLine.addBudgetDistributionListItem(budgetDistribution);
          updateBudgetList.add(purchaseOrderLineBudget);
        }
      }
    }

    if (!CollectionUtils.isEmpty(updateBudgetList)
        && !purchaseOrder.getStatusSelect().equals(PurchaseOrderRepository.STATUS_DRAFT)
        && !purchaseOrder.getStatusSelect().equals(PurchaseOrderRepository.STATUS_REQUESTED)) {
      for (Budget budget : updateBudgetList) {
        budgetSupplychainService.updateLines(budget);
        budgetSupplychainService.computeTotalAmountCommitted(budget);
      }
    }
    // purchaseOrderRepo.save(purchaseOrder);
  }

  @Transactional(rollbackOn = {Exception.class})
  @Override
  public PurchaseOrder mergePurchaseOrders(
      List<PurchaseOrder> purchaseOrderList,
      Currency currency,
      Partner supplierPartner,
      Company company,
      StockLocation stockLocation,
      Partner contactPartner,
      PriceList priceList,
      TradingName tradingName)
      throws AxelorException {
    String numSeq = "";
    String externalRef = "";
    for (PurchaseOrder purchaseOrderLocal : purchaseOrderList) {
      if (!numSeq.isEmpty()) {
        numSeq += "-";
      }
      numSeq += purchaseOrderLocal.getPurchaseOrderSeq();

      if (!externalRef.isEmpty()) {
        externalRef += "|";
      }
      if (purchaseOrderLocal.getExternalReference() != null) {
        externalRef += purchaseOrderLocal.getExternalReference();
      }
    }

    PurchaseOrder purchaseOrderMerged =
        this.createPurchaseOrder(
            AuthUtils.getUser(),
            company,
            contactPartner,
            currency,
            null,
            numSeq,
            externalRef,
            stockLocation,
            appBaseService.getTodayDate(company),
            priceList,
            supplierPartner,
            tradingName);

    super.attachToNewPurchaseOrder(purchaseOrderList, purchaseOrderMerged);

    this.computePurchaseOrder(purchaseOrderMerged);

    purchaseOrderRepo.save(purchaseOrderMerged);

    super.removeOldPurchaseOrders(purchaseOrderList);

    return purchaseOrderMerged;
  }

  @Override
  public void updateAmountToBeSpreadOverTheTimetable(PurchaseOrder purchaseOrder) {
    List<Timetable> timetableList = purchaseOrder.getTimetableList();
    BigDecimal totalHT = purchaseOrder.getExTaxTotal();
    BigDecimal sumTimetableAmount = BigDecimal.ZERO;
    if (timetableList != null) {
      for (Timetable timetable : timetableList) {
        sumTimetableAmount = sumTimetableAmount.add(timetable.getAmount());
      }
    }
    purchaseOrder.setAmountToBeSpreadOverTheTimetable(totalHT.subtract(sumTimetableAmount));
  }

  @Transactional
  @Override
  public void applyToallBudgetDistribution(PurchaseOrder purchaseOrder) {

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
      BudgetDistribution newBudgetDistribution = new BudgetDistribution();
      newBudgetDistribution.setAmount(purchaseOrderLine.getCompanyExTaxTotal());
      newBudgetDistribution.setBudget(purchaseOrder.getBudget());
      newBudgetDistribution.setPurchaseOrderLine(purchaseOrderLine);
      Beans.get(BudgetDistributionRepository.class).save(newBudgetDistribution);
      Beans.get(PurchaseOrderLineServiceSupplychainImpl.class)
          .computeBudgetDistributionSumAmount(purchaseOrderLine, purchaseOrder);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void requestPurchaseOrder(PurchaseOrder purchaseOrder) throws AxelorException {
    if (!Beans.get(AppSupplychainService.class).isApp("supplychain")) {
      super.requestPurchaseOrder(purchaseOrder);
      return;
    }

    // budget control
    if (appAccountService.isApp("budget")
        && appAccountService.getAppBudget().getCheckAvailableBudget()) {
      List<PurchaseOrderLine> purchaseOrderLines = purchaseOrder.getPurchaseOrderLineList();

      Map<Budget, BigDecimal> amountPerBudget = new HashMap<>();
      if (appAccountService.getAppBudget().getManageMultiBudget()) {
        for (PurchaseOrderLine pol : purchaseOrderLines) {
          if (pol.getBudgetDistributionList() != null) {
            for (BudgetDistribution bd : pol.getBudgetDistributionList()) {
              Budget budget = bd.getBudget();

              if (!amountPerBudget.containsKey(budget)) {
                amountPerBudget.put(budget, bd.getAmount());
              } else {
                BigDecimal oldAmount = amountPerBudget.get(budget);
                amountPerBudget.put(budget, oldAmount.add(bd.getAmount()));
              }

              isBudgetExceeded(budget, amountPerBudget.get(budget));
            }
          }
        }
      } else {
        for (PurchaseOrderLine pol : purchaseOrderLines) {
          // getting Budget associated to POL
          Budget budget = pol.getBudget();

          if (!amountPerBudget.containsKey(budget)) {
            amountPerBudget.put(budget, pol.getExTaxTotal());
          } else {
            BigDecimal oldAmount = amountPerBudget.get(budget);
            amountPerBudget.put(budget, oldAmount.add(pol.getExTaxTotal()));
          }

          isBudgetExceeded(budget, amountPerBudget.get(budget));
        }
      }
    }
    super.requestPurchaseOrder(purchaseOrder);
    int intercoPurchaseCreatingStatus =
        Beans.get(AppSupplychainService.class)
            .getAppSupplychain()
            .getIntercoPurchaseCreatingStatusSelect();
    if (purchaseOrder.getInterco()
        && intercoPurchaseCreatingStatus == PurchaseOrderRepository.STATUS_REQUESTED) {
      Beans.get(IntercoService.class).generateIntercoSaleFromPurchase(purchaseOrder);
    }
    if (purchaseOrder.getCreatedByInterco()) {
      fillIntercompanySaleOrderCounterpart(purchaseOrder);
    }
  }

  /**
   * Fill interco sale order counterpart is the sale order exist.
   *
   * @param purchaseOrder
   */
  protected void fillIntercompanySaleOrderCounterpart(PurchaseOrder purchaseOrder) {
    SaleOrder saleOrder =
        Beans.get(SaleOrderRepository.class)
            .all()
            .filter("self.saleOrderSeq = :saleOrderSeq")
            .bind("saleOrderSeq", purchaseOrder.getExternalReference())
            .fetchOne();
    if (saleOrder != null) {
      saleOrder.setExternalReference(purchaseOrder.getPurchaseOrderSeq());
    }
  }

  @Override
  public void isBudgetExceeded(Budget budget, BigDecimal amount) throws AxelorException {
    if (budget == null) {
      return;
    }

    // getting BudgetLine of the period
    BudgetLine bl = null;
    for (BudgetLine budgetLine : budget.getBudgetLineList()) {
      if (DateTool.isBetween(
          budgetLine.getFromDate(),
          budgetLine.getToDate(),
          appAccountService.getTodayDate(budget.getCompany()))) {
        bl = budgetLine;
        break;
      }
    }

    // checking budget excess
    if (bl != null) {
      if (amount.add(bl.getAmountCommitted()).compareTo(bl.getAmountExpected()) > 0) {
        throw new AxelorException(
            budget,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.PURCHASE_ORDER_2),
            budget.getCode());
      }
    }
  }

  @Override
  public void setPurchaseOrderLineBudget(PurchaseOrder purchaseOrder) {

    Budget budget = purchaseOrder.getBudget();
    for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
      purchaseOrderLine.setBudget(budget);
    }
  }

  @Override
  @Transactional
  public void updateToValidatedStatus(PurchaseOrder purchaseOrder) {
    purchaseOrder.setStatusSelect(PurchaseOrderRepository.STATUS_VALIDATED);
    purchaseOrderRepo.save(purchaseOrder);
  }

  @Override
  public String createShipmentCostLine(PurchaseOrder purchaseOrder) throws AxelorException {
    List<PurchaseOrderLine> purchaseOrderLines = purchaseOrder.getPurchaseOrderLineList();
    ShipmentMode shipmentMode = purchaseOrder.getShipmentMode();
    if (shipmentMode == null) {
      return null;
    }
    Product shippingCostProduct = shipmentMode.getShippingCostsProduct();
    if (shipmentMode.getHasCarriagePaidPossibility()) {
      BigDecimal carriagePaidThreshold = shipmentMode.getCarriagePaidThreshold();
      if (computeExTaxTotalWithoutShippingLines(purchaseOrder).compareTo(carriagePaidThreshold)
          >= 0) {
        String message = removeShipmentCostLine(purchaseOrder);
        this.computePurchaseOrder(purchaseOrder);
        return message;
      }
    }
    if (alreadyHasShippingCostLine(purchaseOrder, shippingCostProduct)) {
      return null;
    }
    PurchaseOrderLine shippingCostLine = createShippingCostLine(purchaseOrder, shippingCostProduct);
    purchaseOrderLines.add(shippingCostLine);
    this.computePurchaseOrder(purchaseOrder);
    return null;
  }

  @Override
  public PurchaseOrderLine createShippingCostLine(
      PurchaseOrder purchaseOrder, Product shippingCostProduct) throws AxelorException {
    PurchaseOrderLine shippingCostLine = new PurchaseOrderLine();
    shippingCostLine.setPurchaseOrder(purchaseOrder);
    shippingCostLine.setProduct(shippingCostProduct);
    purchaseOrderLineService.fill(shippingCostLine, purchaseOrder);
    purchaseOrderLineService.compute(shippingCostLine, purchaseOrder);
    return shippingCostLine;
  }

  @Override
  public boolean alreadyHasShippingCostLine(
      PurchaseOrder purchaseOrder, Product shippingCostProduct) {
    List<PurchaseOrderLine> purchaseOrderLines = purchaseOrder.getPurchaseOrderLineList();
    if (purchaseOrderLines == null) {
      return false;
    }
    for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLines) {
      if (purchaseOrderLine.getProduct().getId() == shippingCostProduct.getId()) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Transactional(rollbackOn = Exception.class)
  public String removeShipmentCostLine(PurchaseOrder purchaseOrder) {
    List<PurchaseOrderLine> purchaseOrderLines = purchaseOrder.getPurchaseOrderLineList();
    if (purchaseOrderLines == null) {
      return null;
    }
    List<PurchaseOrderLine> linesToRemove = new ArrayList<PurchaseOrderLine>();
    for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLines) {
      if (purchaseOrderLine.getProduct().getIsShippingCostsProduct()) {
        linesToRemove.add(purchaseOrderLine);
      }
    }
    if (linesToRemove.size() == 0) {
      return null;
    }
    for (PurchaseOrderLine lineToRemove : linesToRemove) {
      purchaseOrderLines.remove(lineToRemove);
      if (lineToRemove.getId() != null) {
        purchaseOrderLineRepository.remove(lineToRemove);
      }
    }
    purchaseOrder.setPurchaseOrderLineList(purchaseOrderLines);
    return I18n.get("Carriage paid threshold is exceeded, all shipment cost lines are removed");
  }

  @Override
  public BigDecimal computeExTaxTotalWithoutShippingLines(PurchaseOrder purchaseOrder) {
    List<PurchaseOrderLine> purchaseOrderLines = purchaseOrder.getPurchaseOrderLineList();
    if (purchaseOrderLines == null) {
      return BigDecimal.ZERO;
    }
    BigDecimal exTaxTotal = BigDecimal.ZERO;
    for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLines) {
      if (!purchaseOrderLine.getProduct().getIsShippingCostsProduct()) {
        exTaxTotal = exTaxTotal.add(purchaseOrderLine.getExTaxTotal());
      }
    }
    return exTaxTotal;
  }
}
