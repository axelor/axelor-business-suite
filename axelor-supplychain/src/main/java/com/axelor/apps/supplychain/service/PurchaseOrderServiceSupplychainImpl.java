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

import com.axelor.apps.account.db.Budget;
import com.axelor.apps.account.db.BudgetDistribution;
import com.axelor.apps.account.db.BudgetLine;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.BudgetDistributionRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.purchase.db.IPurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.service.PurchaseOrderServiceImpl;
import com.axelor.apps.stock.db.StockConfig;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockMoveLineService;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.supplychain.db.SupplyChainConfig;
import com.axelor.apps.supplychain.db.Timetable;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.beust.jcommander.internal.Lists;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurchaseOrderServiceSupplychainImpl extends PurchaseOrderServiceImpl {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected UnitConversionService unitConversionService;
  protected StockMoveRepository stockMoveRepo;
  protected AppSupplychainService appSupplychainService;
  protected AccountConfigService accountConfigService;
  protected AppAccountService appAccountService;
  protected AppBaseService appBaseService;

  @Inject private BudgetDistributionRepository budgetDistributionRepo;

  @Inject
  public PurchaseOrderServiceSupplychainImpl(
      UnitConversionService unitConversionService,
      StockMoveRepository stockMoveRepo,
      AppSupplychainService appSupplychainService,
      AccountConfigService accountConfigService,
      AppAccountService appAccountService,
      AppBaseService appBaseService) {
    this.unitConversionService = unitConversionService;
    this.stockMoveRepo = stockMoveRepo;
    this.appSupplychainService = appSupplychainService;
    this.accountConfigService = accountConfigService;
    this.appAccountService = appAccountService;
    this.appBaseService = appBaseService;
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

  /**
   * Méthode permettant de créer un StockMove à partir d'un PurchaseOrder.
   *
   * @param purchaseOrder une commande
   * @throws AxelorException Aucune séquence de StockMove n'a été configurée
   */
  public Long createStocksMove(PurchaseOrder purchaseOrder) throws AxelorException {

    Long stockMoveId = null;
    if (purchaseOrder.getPurchaseOrderLineList() != null && purchaseOrder.getCompany() != null) {
      StockConfigService stockConfigService = Beans.get(StockConfigService.class);
      Company company = purchaseOrder.getCompany();
      SupplyChainConfig supplyChainConfig =
          Beans.get(SupplyChainConfigService.class).getSupplyChainConfig(company);

      StockConfig stockConfig = stockConfigService.getStockConfig(company);

      StockLocation startLocation =
          Beans.get(StockLocationRepository.class)
              .findByPartner(purchaseOrder.getSupplierPartner());

      if (startLocation == null) {
        startLocation = stockConfigService.getSupplierVirtualStockLocation(stockConfig);
      }
      if (startLocation == null) {
        throw new AxelorException(
            purchaseOrder,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.PURCHASE_ORDER_1),
            company.getName());
      }

      Partner supplierPartner = purchaseOrder.getSupplierPartner();

      Address address = Beans.get(PartnerService.class).getDeliveryAddress(supplierPartner);

      StockMove stockMove =
          Beans.get(StockMoveService.class)
              .createStockMove(
                  address,
                  null,
                  company,
                  supplierPartner,
                  startLocation,
                  purchaseOrder.getStockLocation(),
                  null,
                  purchaseOrder.getDeliveryDate(),
                  purchaseOrder.getNotes(),
                  purchaseOrder.getShipmentMode(),
                  purchaseOrder.getFreightCarrierMode(),
                  null,
                  null,
                  null,
                  StockMoveRepository.TYPE_INCOMING);
      stockMove.setPurchaseOrder(purchaseOrder);
      stockMove.setEstimatedDate(purchaseOrder.getDeliveryDate());
      stockMove.setTradingName(purchaseOrder.getTradingName());

      for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {

        Product product = purchaseOrderLine.getProduct();
        // Check if the company field 'hasInSmForStorableProduct' = true and productTypeSelect =
        // 'storable' or 'hasInSmForNonStorableProduct' = true and productTypeSelect = 'service' or
        // productTypeSelect = 'other'
        if (product != null
            && ((supplyChainConfig.getHasInSmForStorableProduct()
                    && ProductRepository.PRODUCT_TYPE_STORABLE.equals(
                        product.getProductTypeSelect()))
                || (supplyChainConfig.getHasInSmForNonStorableProduct()
                    && !ProductRepository.PRODUCT_TYPE_STORABLE.equals(
                        product.getProductTypeSelect())))) {

          Unit unit = purchaseOrderLine.getProduct().getUnit();
          BigDecimal qty = purchaseOrderLine.getQty();
          BigDecimal priceDiscounted = purchaseOrderLine.getPriceDiscounted();
          BigDecimal companyUnitPriceUntaxed = purchaseOrderLine.getProduct().getCostPrice();

          if (purchaseOrderLine.getQty() != BigDecimal.ZERO) {
            companyUnitPriceUntaxed =
                purchaseOrderLine
                    .getCompanyExTaxTotal()
                    .divide(
                        purchaseOrderLine.getQty(),
                        Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice(),
                        RoundingMode.HALF_EVEN);
          }

          if (unit != null && !unit.equals(purchaseOrderLine.getUnit())) {
            qty =
                unitConversionService.convert(
                    purchaseOrderLine.getUnit(),
                    unit,
                    qty,
                    qty.scale(),
                    purchaseOrderLine.getProduct());
            priceDiscounted =
                unitConversionService.convert(
                    unit,
                    purchaseOrderLine.getUnit(),
                    priceDiscounted,
                    appBaseService.getNbDecimalDigitForUnitPrice(),
                    purchaseOrderLine.getProduct());
          }

          BigDecimal taxRate = BigDecimal.ZERO;
          TaxLine taxLine = purchaseOrderLine.getTaxLine();
          if (taxLine != null) {
            taxRate = taxLine.getValue();
          }

          StockMoveLine stockMoveLine =
              Beans.get(StockMoveLineService.class)
                  .createStockMoveLine(
                      product,
                      purchaseOrderLine.getProductName(),
                      purchaseOrderLine.getDescription(),
                      qty,
                      priceDiscounted,
                      companyUnitPriceUntaxed,
                      unit,
                      stockMove,
                      StockMoveLineService.TYPE_PURCHASES,
                      purchaseOrder.getInAti(),
                      taxRate);
          if (stockMoveLine != null) {
            stockMoveLine.setPurchaseOrderLine(purchaseOrderLine);
          }
        } else if (purchaseOrderLine.getIsTitleLine()) {
          StockMoveLine stockMoveLine =
              Beans.get(StockMoveLineService.class)
                  .createStockMoveLine(
                      product,
                      purchaseOrderLine.getProductName(),
                      purchaseOrderLine.getDescription(),
                      BigDecimal.ZERO,
                      BigDecimal.ZERO,
                      BigDecimal.ZERO,
                      null,
                      stockMove,
                      2,
                      purchaseOrder.getInAti(),
                      null);
          if (stockMoveLine != null) {
            stockMoveLine.setPurchaseOrderLine(purchaseOrderLine);
            stockMoveLine.setIsTitleLine(true);
          }
        }
      }
      if (stockMove.getStockMoveLineList() != null && !stockMove.getStockMoveLineList().isEmpty()) {
        Beans.get(StockMoveService.class).plan(stockMove);
      }
      stockMoveId = stockMove.getId();
    }
    return stockMoveId;
  }

  public void cancelReceipt(PurchaseOrder purchaseOrder) throws AxelorException {

    List<StockMove> stockMoveList =
        Beans.get(StockMoveRepository.class)
            .all()
            .filter("self.purchaseOrder = ?1 AND self.statusSelect = 2", purchaseOrder)
            .fetch();

    for (StockMove stockMove : stockMoveList) {

      Beans.get(StockMoveService.class).cancel(stockMove);
    }
  }

  // Check if existing at least one stockMove not canceled for the purchaseOrder
  public boolean existActiveStockMoveForPurchaseOrder(Long purchaseOrderId) {
    long nbStockMove =
        Beans.get(StockMoveRepository.class)
            .all()
            .filter(
                "self.purchaseOrder.id = ? AND self.statusSelect <> ?",
                purchaseOrderId,
                StockMoveRepository.STATUS_CANCELED)
            .count();
    return nbStockMove > 0;
  }

  @Transactional
  public void generateBudgetDistribution(PurchaseOrder purchaseOrder) {
    if (purchaseOrder.getPurchaseOrderLineList() != null) {
      for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
        if (purchaseOrderLine.getBudget() != null
            && purchaseOrderLine.getBudgetDistributionList().isEmpty()) {
          BudgetDistribution budgetDistribution = new BudgetDistribution();
          budgetDistribution.setBudget(purchaseOrderLine.getBudget());
          budgetDistribution.setAmount(purchaseOrderLine.getExTaxTotal());
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

  @Transactional
  public void updatePurchaseOrderOnCancel(StockMove stockMove, PurchaseOrder purchaseOrder) {

    List<StockMove> stockMoveList = Lists.newArrayList();
    stockMoveList = stockMoveRepo.all().filter("self.purchaseOrder = ?1", purchaseOrder).fetch();
    purchaseOrder.setReceiptState(IPurchaseOrder.STATE_NOT_RECEIVED);
    for (StockMove stock : stockMoveList) {
      if (stock.getStatusSelect() != StockMoveRepository.STATUS_CANCELED
          && !stock.getId().equals(stockMove.getId())) {
        purchaseOrder.setReceiptState(IPurchaseOrder.STATE_PARTIALLY_RECEIVED);
        break;
      }
    }

    if (purchaseOrder.getStatusSelect() == IPurchaseOrder.STATUS_FINISHED
        && appSupplychainService.getAppSupplychain().getTerminatePurchaseOrderOnReceipt()) {
      purchaseOrder.setStatusSelect(IPurchaseOrder.STATUS_VALIDATED);
    }
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
  public void applyToallBudgetDistribution(PurchaseOrder purchaseOrder) {

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
      BudgetDistribution newBudgetDistribution = new BudgetDistribution();
      newBudgetDistribution.setAmount(purchaseOrderLine.getExTaxTotal());
      newBudgetDistribution.setBudget(purchaseOrder.getBudget());
      newBudgetDistribution.setPurchaseOrderLine(purchaseOrderLine);
      budgetDistributionRepo.save(newBudgetDistribution);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void requestPurchaseOrder(PurchaseOrder purchaseOrder) throws Exception {
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
    int intercoPurchaseCreatingStatus =
        Beans.get(AppSupplychainService.class)
            .getAppSupplychain()
            .getIntercoPurchaseCreatingStatusSelect();
    if (purchaseOrder.getInterco()
        && intercoPurchaseCreatingStatus == IPurchaseOrder.STATUS_REQUESTED) {
      Beans.get(IntercoService.class).generateIntercoSaleFromPurchase(purchaseOrder);
    }
    super.requestPurchaseOrder(purchaseOrder);
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
        && !existActiveStockMoveForPurchaseOrder(purchaseOrder.getId())) {
      createStocksMove(purchaseOrder);
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
  }
}
