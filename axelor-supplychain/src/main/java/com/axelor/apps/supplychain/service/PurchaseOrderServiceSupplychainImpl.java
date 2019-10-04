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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.account.db.Budget;
import com.axelor.apps.account.db.BudgetDistribution;
import com.axelor.apps.account.db.BudgetLine;
import com.axelor.apps.account.db.repo.BudgetDistributionRepository;
import com.axelor.apps.account.db.repo.BudgetRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.purchase.db.IPurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.service.PurchaseOrderServiceImpl;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurchaseOrderServiceSupplychainImpl extends PurchaseOrderServiceImpl {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AppSupplychainService appSupplychainService;
  protected AccountConfigService accountConfigService;
  protected AppAccountService appAccountService;
  protected AppBaseService appBaseService;
  protected PurchaseOrderStockService purchaseOrderStockService;
  protected BudgetSupplychainService budgetSupplychainService;

  @Inject
  public PurchaseOrderServiceSupplychainImpl(
      AppSupplychainService appSupplychainService,
      AccountConfigService accountConfigService,
      AppAccountService appAccountService,
      AppBaseService appBaseService,
      PurchaseOrderStockService purchaseOrderStockService,
      BudgetSupplychainService budgetSupplychainService) {

    this.appSupplychainService = appSupplychainService;
    this.accountConfigService = accountConfigService;
    this.appAccountService = appAccountService;
    this.appBaseService = appBaseService;
    this.purchaseOrderStockService = purchaseOrderStockService;
    this.budgetSupplychainService = budgetSupplychainService;
  }

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

    purchaseOrder.setPaymentMode(supplierPartner.getInPaymentMode());
    purchaseOrder.setPaymentCondition(supplierPartner.getPaymentCondition());

    if (purchaseOrder.getPaymentMode() == null) {
      purchaseOrder.setPaymentMode(
          this.accountConfigService.getAccountConfig(company).getInPaymentMode());
    }

    if (purchaseOrder.getPaymentCondition() == null) {
      purchaseOrder.setPaymentCondition(
          this.accountConfigService.getAccountConfig(company).getDefPaymentCondition());
    }

    purchaseOrder.setTradingName(tradingName);

    return purchaseOrder;
  }

  @Transactional
  public void generateBudgetDistribution(PurchaseOrder purchaseOrder) {
    if (purchaseOrder.getPurchaseOrderLineList() != null) {
      for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
        if (purchaseOrderLine.getBudget() != null
            && (purchaseOrderLine.getBudgetDistributionList() == null
                || purchaseOrderLine.getBudgetDistributionList().isEmpty())) {
          BudgetDistribution budgetDistribution = new BudgetDistribution();
          budgetDistribution.setBudget(purchaseOrderLine.getBudget());
          budgetDistribution.setAmount(purchaseOrderLine.getCompanyExTaxTotal());
          purchaseOrderLine.addBudgetDistributionListItem(budgetDistribution);
        }
      }
      // purchaseOrderRepo.save(purchaseOrder);
    }
  }

  @Transactional
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
            LocalDate.now(),
            priceList,
            supplierPartner,
            tradingName);

    super.attachToNewPurchaseOrder(purchaseOrderList, purchaseOrderMerged);

    this.computePurchaseOrder(purchaseOrderMerged);

    purchaseOrderRepo.save(purchaseOrderMerged);

    super.removeOldPurchaseOrders(purchaseOrderList);

    return purchaseOrderMerged;
  }

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
  public void applyToallBudgetDistribution(PurchaseOrder purchaseOrder, String previousBudgetId) {

    if (!previousBudgetId.equals("null")) {

      Budget previousBudget =
          Beans.get(BudgetRepository.class).find(Long.parseLong(previousBudgetId));
      this.removePreviousBudgetDistributionLines(
          purchaseOrder.getPurchaseOrderLineList(), previousBudget);
    }

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
      BudgetDistribution newBudgetDistribution = new BudgetDistribution();
      newBudgetDistribution.setAmount(purchaseOrderLine.getCompanyExTaxTotal());
      newBudgetDistribution.setBudget(purchaseOrder.getBudget());
      newBudgetDistribution.setPurchaseOrderLine(purchaseOrderLine);
      Beans.get(BudgetDistributionRepository.class).save(newBudgetDistribution);
    }
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void requestPurchaseOrder(PurchaseOrder purchaseOrder) throws AxelorException {
    // budget control
    if (appAccountService.isApp("budget")
        && appAccountService.getAppBudget().getCheckAvailableBudget()) {
      List<PurchaseOrderLine> purchaseOrderLines = purchaseOrder.getPurchaseOrderLineList();

      Map<Budget, BigDecimal> amountPerBudget = new HashMap<>();
      if (appAccountService.getAppBudget().getManageMultiBudget()) {
        for (PurchaseOrderLine pol : purchaseOrderLines) {
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
        && intercoPurchaseCreatingStatus == IPurchaseOrder.STATUS_REQUESTED) {
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

  public void isBudgetExceeded(Budget budget, BigDecimal amount) throws AxelorException {
    if (budget == null) {
      return;
    }

    // getting BudgetLine of the period
    BudgetLine bl = null;
    for (BudgetLine budgetLine : budget.getBudgetLineList()) {
      if (DateTool.isBetween(
          budgetLine.getFromDate(), budgetLine.getToDate(), appAccountService.getTodayDate())) {
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
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void validatePurchaseOrder(PurchaseOrder purchaseOrder) throws AxelorException {
    super.validatePurchaseOrder(purchaseOrder);

    if (appSupplychainService.getAppSupplychain().getSupplierStockMoveGenerationAuto()
        && !purchaseOrderStockService.existActiveStockMoveForPurchaseOrder(purchaseOrder.getId())) {
      purchaseOrderStockService.createStockMoveFromPurchaseOrder(purchaseOrder);
    }

    if (appAccountService.getAppBudget().getActive()
        && !appAccountService.getAppBudget().getManageMultiBudget()) {
      generateBudgetDistribution(purchaseOrder);
    }
    int intercoPurchaseCreatingStatus =
        Beans.get(AppSupplychainService.class)
            .getAppSupplychain()
            .getIntercoPurchaseCreatingStatusSelect();
    if (purchaseOrder.getInterco()
        && intercoPurchaseCreatingStatus == IPurchaseOrder.STATUS_VALIDATED) {
      Beans.get(IntercoService.class).generateIntercoSaleFromPurchase(purchaseOrder);
    }

    budgetSupplychainService.updateBudgetLinesFromPurchaseOrder(purchaseOrder);
  }

  @Override
  @Transactional
  public void cancelPurchaseOrder(PurchaseOrder purchaseOrder) {
    super.cancelPurchaseOrder(purchaseOrder);
    budgetSupplychainService.updateBudgetLinesFromPurchaseOrder(purchaseOrder);
  }

  @Transactional
  private void removePreviousBudgetDistributionLines(
      List<PurchaseOrderLine> purchaseOrderLineList, Budget previousBudget) {

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLineList) {

      List<BudgetDistribution> budgetDistributionList =
          new ArrayList<>(purchaseOrderLine.getBudgetDistributionList());

      if (budgetDistributionList != null && !budgetDistributionList.isEmpty()) {

        for (BudgetDistribution budgetDistribution : budgetDistributionList) {
          if (budgetDistribution.getBudget() == previousBudget) {
            purchaseOrderLine.getBudgetDistributionList().remove(budgetDistribution);
            Beans.get(BudgetDistributionRepository.class).remove(budgetDistribution);
          }
        }
      }
    }
  }
}
