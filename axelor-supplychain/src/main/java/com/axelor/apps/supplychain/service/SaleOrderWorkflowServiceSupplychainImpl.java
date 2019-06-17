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

import com.axelor.apps.base.db.AppSupplychain;
import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.exception.BlockedSaleOrderException;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.saleorder.SaleOrderWorkflowServiceImpl;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleOrderWorkflowServiceSupplychainImpl extends SaleOrderWorkflowServiceImpl {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected SaleOrderStockService saleOrderStockService;
  protected SaleOrderPurchaseService saleOrderPurchaseService;
  protected AppSupplychain appSupplychain;
  protected AccountingSituationSupplychainService accountingSituationSupplychainService;

  @Inject
  public SaleOrderWorkflowServiceSupplychainImpl(
      SequenceService sequenceService,
      PartnerRepository partnerRepo,
      SaleOrderRepository saleOrderRepo,
      AppSaleService appSaleService,
      UserService userService,
      SaleOrderStockService saleOrderStockService,
      SaleOrderPurchaseService saleOrderPurchaseService,
      AppSupplychainService appSupplychainService,
      AccountingSituationSupplychainService accountingSituationSupplychainService) {

    super(sequenceService, partnerRepo, saleOrderRepo, appSaleService, userService);

    this.saleOrderStockService = saleOrderStockService;
    this.saleOrderPurchaseService = saleOrderPurchaseService;
    this.appSupplychain = appSupplychainService.getAppSupplychain();
    this.accountingSituationSupplychainService = accountingSituationSupplychainService;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void confirmSaleOrder(SaleOrder saleOrder) throws AxelorException {

    super.confirmSaleOrder(saleOrder);

    if (appSupplychain.getPurchaseOrderGenerationAuto()) {
      saleOrderPurchaseService.createPurchaseOrders(saleOrder);
    }
    if (appSupplychain.getCustomerStockMoveGenerationAuto()) {
      saleOrderStockService.createStocksMovesFromSaleOrder(saleOrder);
    }
    int intercoSaleCreatingStatus =
        Beans.get(AppSupplychainService.class)
            .getAppSupplychain()
            .getIntercoSaleCreatingStatusSelect();
    if (saleOrder.getInterco()
        && intercoSaleCreatingStatus == SaleOrderRepository.STATUS_ORDER_CONFIRMED) {
      Beans.get(IntercoService.class).generateIntercoPurchaseFromSale(saleOrder);
    }
  }

  @Override
  @Transactional
  public void cancelSaleOrder(
      SaleOrder saleOrder, CancelReason cancelReason, String cancelReasonStr) {
    super.cancelSaleOrder(saleOrder, cancelReason, cancelReasonStr);
    try {
      accountingSituationSupplychainService.updateUsedCredit(saleOrder.getClientPartner());

      for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
        saleOrderLine.setAnalyticDistributionTemplate(null);
        saleOrderLine.clearAnalyticMoveLineList();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  @Transactional(
    rollbackOn = {AxelorException.class, RuntimeException.class},
    ignore = {BlockedSaleOrderException.class}
  )
  public void finalizeQuotation(SaleOrder saleOrder) throws AxelorException {
    accountingSituationSupplychainService.updateCustomerCreditFromSaleOrder(saleOrder);
    super.finalizeQuotation(saleOrder);
    int intercoSaleCreatingStatus =
        Beans.get(AppSupplychainService.class)
            .getAppSupplychain()
            .getIntercoSaleCreatingStatusSelect();
    if (saleOrder.getInterco()
        && intercoSaleCreatingStatus == SaleOrderRepository.STATUS_FINALIZED_QUOTATION) {
      Beans.get(IntercoService.class).generateIntercoPurchaseFromSale(saleOrder);
    }
    if (saleOrder.getCreatedByInterco()) {
      fillIntercompanyPurchaseOrderCounterpart(saleOrder);
    }
  }

  /**
   * Fill interco purchase order counterpart is the sale order exist.
   *
   * @param saleOrder
   */
  protected void fillIntercompanyPurchaseOrderCounterpart(SaleOrder saleOrder) {
    PurchaseOrder purchaseOrder =
        Beans.get(PurchaseOrderRepository.class)
            .all()
            .filter("self.purchaseOrderSeq = :purchaseOrderSeq")
            .bind("purchaseOrderSeq", saleOrder.getExternalReference())
            .fetchOne();
    if (purchaseOrder != null) {
      purchaseOrder.setExternalReference(saleOrder.getSaleOrderSeq());
    }
  }
}
