package com.axelor.apps.businessproduction.service;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.production.service.SaleOrderWorkflowServiceProductionImpl;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.production.service.productionorder.ProductionOrderSaleOrderService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.supplychain.service.AccountingSituationSupplychainService;
import com.axelor.apps.supplychain.service.SaleOrderPurchaseService;
import com.axelor.apps.supplychain.service.SaleOrderStockService;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class SaleOrderWorkflowServiceBusinessProductionImpl
    extends SaleOrderWorkflowServiceProductionImpl {

  @Inject
  public SaleOrderWorkflowServiceBusinessProductionImpl(
      SequenceService sequenceService,
      PartnerRepository partnerRepo,
      SaleOrderRepository saleOrderRepo,
      AppSaleService appSaleService,
      UserService userService,
      SaleOrderStockService saleOrderStockService,
      SaleOrderPurchaseService saleOrderPurchaseService,
      AppSupplychainService appSupplychainService,
      AccountingSituationSupplychainService accountingSituationSupplychainService,
      ProductionOrderSaleOrderService productionOrderSaleOrderService,
      AppProductionService appProductionService) {
    super(
        sequenceService,
        partnerRepo,
        saleOrderRepo,
        appSaleService,
        userService,
        saleOrderStockService,
        saleOrderPurchaseService,
        appSupplychainService,
        accountingSituationSupplychainService,
        productionOrderSaleOrderService,
        appProductionService);
  }

  @Override
  @Transactional(rollbackOn = Exception.class)
  public void cancelSaleOrder(
      SaleOrder saleOrder, CancelReason cancelReason, String cancelReasonStr) {
    super.cancelSaleOrder(saleOrder, cancelReason, cancelReasonStr);
    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
      for (AnalyticMoveLine analyticMoveLine : saleOrderLine.getAnalyticMoveLineList()) {
        analyticMoveLine.setProject(null);
        Beans.get(AnalyticMoveLineRepository.class).save(analyticMoveLine);
      }
    }
  }
}
