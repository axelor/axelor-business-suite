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
package com.axelor.apps.account.service.payment.invoice.payment;

import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.db.repo.ReconcileRepository;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveCancelService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InvoicePaymentCancelServiceImpl implements InvoicePaymentCancelService {

  protected AccountConfigService accountConfigService;
  protected InvoicePaymentRepository invoicePaymentRepository;
  protected MoveCancelService moveCancelService;
  protected ReconcileService reconcileService;
  protected InvoicePaymentToolService invoicePaymentToolService;

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject
  public InvoicePaymentCancelServiceImpl(
      AccountConfigService accountConfigService,
      InvoicePaymentRepository invoicePaymentRepository,
      MoveCancelService moveCancelService,
      ReconcileService reconcileService,
      InvoicePaymentToolService invoicePaymentToolService) {

    this.accountConfigService = accountConfigService;
    this.invoicePaymentRepository = invoicePaymentRepository;
    this.moveCancelService = moveCancelService;
    this.reconcileService = reconcileService;
    this.invoicePaymentToolService = invoicePaymentToolService;
  }

  /**
   * Method to cancel an invoice Payment
   *
   * <p>Cancel the eventual Move and Reconcile Compute the total amount paid on the linked invoice
   * Change the status to cancel
   *
   * @param invoicePayment An invoice payment
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void cancel(InvoicePayment invoicePayment) throws AxelorException {

    Move paymentMove = invoicePayment.getMove();
    Reconcile reconcile = invoicePayment.getReconcile();

    log.debug("cancel : reconcile : {}", reconcile);

    if (reconcile != null && reconcile.getStatusSelect() == ReconcileRepository.STATUS_CONFIRMED) {
      reconcileService.unreconcile(reconcile);
      if (paymentMove.getStatusSelect() == MoveRepository.STATUS_DAYBOOK
          || (paymentMove.getStatusSelect() == MoveRepository.STATUS_VALIDATED
              && accountConfigService
                  .getAccountConfig(invoicePayment.getInvoice().getCompany())
                  .getAllowRemovalValidatedMove())) {
        invoicePayment.setReconcile(null);
        Beans.get(ReconcileRepository.class).remove(reconcile);
      }
    }

    if (paymentMove != null
        && invoicePayment.getTypeSelect() == InvoicePaymentRepository.TYPE_PAYMENT) {
      invoicePayment.setMove(null);
      moveCancelService.cancel(paymentMove);
    } else {
      this.updateCancelStatus(invoicePayment);
    }
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void updateCancelStatus(InvoicePayment invoicePayment) throws AxelorException {

    invoicePayment.setStatusSelect(InvoicePaymentRepository.STATUS_CANCELED);

    invoicePaymentRepository.save(invoicePayment);

    invoicePaymentToolService.updateAmountPaid(invoicePayment.getInvoice());
  }
}
