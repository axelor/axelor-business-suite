package com.axelor.apps.supplychain.db.repo;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.StockMoveManagementRepository;

public class StockMoveSupplyChainRepository extends StockMoveManagementRepository {

  @Override
  public StockMove save(StockMove entity) {
    Invoice invoice = entity.getInvoice();
    if (invoice != null) {
      invoice.setIsPassedForPayment(entity.getIsPassedForPayment());
    }
    return super.save(entity);
  }
}
