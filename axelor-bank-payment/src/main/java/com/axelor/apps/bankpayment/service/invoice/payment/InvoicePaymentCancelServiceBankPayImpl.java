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
package com.axelor.apps.bankpayment.service.invoice.payment;

import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveCancelService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCancelServiceImpl;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentToolService;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.repo.BankOrderRepository;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InvoicePaymentCancelServiceBankPayImpl extends InvoicePaymentCancelServiceImpl {

  protected BankOrderService bankOrderService;

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject
  public InvoicePaymentCancelServiceBankPayImpl(
      AccountConfigService accountConfigService,
      InvoicePaymentRepository invoicePaymentRepository,
      MoveCancelService moveCancelService,
      ReconcileService reconcileService,
      BankOrderService bankOrderService,
      InvoicePaymentToolService invoicePaymentToolService) {

    super(
        accountConfigService,
        invoicePaymentRepository,
        moveCancelService,
        reconcileService,
        invoicePaymentToolService);

    this.bankOrderService = bankOrderService;
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
  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void cancel(InvoicePayment invoicePayment) throws AxelorException {

    BankOrder paymentBankOrder = invoicePayment.getBankOrder();

    if (paymentBankOrder != null) {
      if (paymentBankOrder.getStatusSelect() == BankOrderRepository.STATUS_CARRIED_OUT
          || paymentBankOrder.getStatusSelect() == BankOrderRepository.STATUS_REJECTED) {
        throw new AxelorException(
            invoicePayment,
            TraceBackRepository.TYPE_FUNCTIONNAL,
            I18n.get(IExceptionMessage.INVOICE_PAYMENT_CANCEL));
      } else if (paymentBankOrder.getStatusSelect() != BankOrderRepository.STATUS_CANCELED) {
        bankOrderService.cancelBankOrder(paymentBankOrder);
        this.updateCancelStatus(invoicePayment);
      }
    }

    super.cancel(invoicePayment);
  }
}
