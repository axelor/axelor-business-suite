package com.axelor.apps.supplychain.service;

import com.axelor.apps.account.service.AccountManagementServiceAccountImpl;
import com.axelor.apps.account.service.AnalyticMoveLineServiceImpl;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import java.math.BigDecimal;

public class AnalyticMoveLineServiceSupplyChainImpl extends AnalyticMoveLineServiceImpl {

  @Inject
  public AnalyticMoveLineServiceSupplyChainImpl(
      AppAccountService appAccountService,
      AccountManagementServiceAccountImpl accountManagementServiceAccountImpl) {
    super(appAccountService, accountManagementServiceAccountImpl);
    // TODO Auto-generated constructor stub
  }

  @Override
  public BigDecimal getOriginalPieceAmount(Context parentContext) {
    if (parentContext.getContextClass().equals(SaleOrderLine.class)) {
      SaleOrderLine saleOrderLine = parentContext.asType(SaleOrderLine.class);
      return saleOrderLine.getCompanyExTaxTotal();
    }
    return super.getOriginalPieceAmount(parentContext);
  }
}
