package com.axelor.apps.cash.management.service.move;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveCreateService;
import com.axelor.apps.account.service.move.MoveDueService;
import com.axelor.apps.account.service.move.MoveExcessPaymentService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveRemoveService;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.account.service.move.MoveValidateService;
import com.axelor.apps.account.service.payment.PaymentService;
import com.axelor.apps.bankpayment.service.move.MoveServiceBankPaymentImpl;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class MoveServiceCashManagementImpl extends MoveServiceBankPaymentImpl {

  @Inject
  public MoveServiceCashManagementImpl(
      AppAccountService appAccountService,
      MoveLineService moveLineService,
      MoveCreateService moveCreateService,
      MoveValidateService moveValidateService,
      MoveToolService moveToolService,
      MoveRemoveService moveRemoveService,
      ReconcileService reconcileService,
      MoveDueService moveDueService,
      PaymentService paymentService,
      MoveExcessPaymentService moveExcessPaymentService,
      MoveRepository moveRepository,
      AccountConfigService accountConfigService) {
    super(
        appAccountService,
        moveLineService,
        moveCreateService,
        moveValidateService,
        moveToolService,
        moveRemoveService,
        reconcileService,
        moveDueService,
        paymentService,
        moveExcessPaymentService,
        moveRepository,
        accountConfigService);
  }

  @Override
  @Transactional(rollbackOn = Exception.class)
  public Move createMove(Invoice invoice) throws AxelorException {
    Move move = super.createMove(invoice);
    if (invoice.getSaleOrder() != null) {
      move.setFunctionalOriginSelect(MoveRepository.FUNCTIONAL_ORIGIN_SALE);
    } else if (invoice.getPurchaseOrder() != null) {
      move.setFunctionalOriginSelect(MoveRepository.FUNCTIONAL_ORIGIN_PURCHASE);
    }
    return move;
  }
}
