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
package com.axelor.apps.bankpayment.service.invoice.payment;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.PaymentModeRepository;
import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentToolService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentValidateServiceImpl;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderCreateService;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderService;
import com.axelor.apps.base.db.Company;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

public class InvoicePaymentValidateServiceBankPayImpl extends InvoicePaymentValidateServiceImpl {

  protected BankOrderCreateService bankOrderCreateService;
  protected BankOrderService bankOrderService;

  @Inject
  public InvoicePaymentValidateServiceBankPayImpl(
      PaymentModeService paymentModeService,
      MoveService moveService,
      MoveLineService moveLineService,
      AccountConfigService accountConfigService,
      InvoicePaymentRepository invoicePaymentRepository,
      ReconcileService reconcileService,
      BankOrderCreateService bankOrderCreateService,
      BankOrderService bankOrderService,
      InvoicePaymentToolService invoicePaymentToolService,
      InvoiceTermService invoiceTermService) {

    super(
        paymentModeService,
        moveService,
        moveLineService,
        accountConfigService,
        invoicePaymentRepository,
        reconcileService,
        invoicePaymentToolService,
        invoiceTermService);

    this.bankOrderCreateService = bankOrderCreateService;
    this.bankOrderService = bankOrderService;
  }

  /**
   * Method to validate an invoice Payment
   *
   * <p>Create the eventual move (depending general configuration) and reconcile it with the invoice
   * move Compute the amount paid on invoice Change the status to validated
   *
   * @param invoicePayment An invoice payment
   * @throws AxelorException
   * @throws DatatypeConfigurationException
   * @throws IOException
   * @throws JAXBException
   */
  @Transactional(rollbackOn = {Exception.class})
  public void validate(InvoicePayment invoicePayment, boolean force)
      throws AxelorException, JAXBException, IOException, DatatypeConfigurationException {

    if (!force && invoicePayment.getStatusSelect() != InvoicePaymentRepository.STATUS_DRAFT) {
      return;
    }

    Invoice invoice = invoicePayment.getInvoice();
    PaymentMode paymentMode = invoicePayment.getPaymentMode();
    if (paymentMode == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.INVOICE_PAYMENT_MODE_MISSING),
          invoice.getInvoiceId());
    }
    int typeSelect = paymentMode.getTypeSelect();
    int inOutSelect = paymentMode.getInOutSelect();

    if ((typeSelect == PaymentModeRepository.TYPE_DD && inOutSelect == PaymentModeRepository.IN)
        || (typeSelect == PaymentModeRepository.TYPE_TRANSFER
                && inOutSelect == PaymentModeRepository.OUT)
            && paymentMode.getGenerateBankOrder()) {
      invoicePayment.setStatusSelect(InvoicePaymentRepository.STATUS_PENDING);
    } else {
      invoicePayment.setStatusSelect(InvoicePaymentRepository.STATUS_VALIDATED);
    }

    // TODO assign an automatic reference

    Company company = invoice.getCompany();

    if (accountConfigService.getAccountConfig(company).getGenerateMoveForInvoicePayment()
        && !paymentMode.getGenerateBankOrder()) {
      invoicePayment = this.createMoveForInvoicePayment(invoicePayment);
    } else {
      Beans.get(AccountingSituationService.class)
          .updateCustomerCredit(invoicePayment.getInvoice().getPartner());
      invoicePayment = invoicePaymentRepository.save(invoicePayment);
    }
    if (paymentMode.getGenerateBankOrder()) {
      this.createBankOrder(invoicePayment);
    }

    invoiceTermService.updateInvoiceTermsPaidAmount(invoicePayment);
    invoicePaymentToolService.updateAmountPaid(invoice);
    if (invoice != null
        && invoice.getOperationSubTypeSelect() == InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE) {
      invoicePayment.setTypeSelect(InvoicePaymentRepository.TYPE_ADVANCEPAYMENT);
    }
    invoicePaymentRepository.save(invoicePayment);
  }

  @Transactional(rollbackOn = {Exception.class})
  public void validateFromBankOrder(InvoicePayment invoicePayment, boolean force)
      throws AxelorException {

    invoicePayment.setStatusSelect(InvoicePaymentRepository.STATUS_VALIDATED);

    Company company = invoicePayment.getInvoice().getCompany();

    if (accountConfigService.getAccountConfig(company).getGenerateMoveForInvoicePayment()) {
      invoicePayment = this.createMoveForInvoicePayment(invoicePayment);
    } else {
      Beans.get(AccountingSituationService.class)
          .updateCustomerCredit(invoicePayment.getInvoice().getPartner());
      invoicePayment = invoicePaymentRepository.save(invoicePayment);
    }

    invoicePaymentToolService.updateAmountPaid(invoicePayment.getInvoice());
  }

  /**
   * Method to create a bank order for an invoice Payment
   *
   * @param invoicePayment An invoice payment
   * @throws AxelorException
   * @throws DatatypeConfigurationException
   * @throws IOException
   * @throws JAXBException
   */
  @Transactional(rollbackOn = {Exception.class})
  public void createBankOrder(InvoicePayment invoicePayment)
      throws AxelorException, JAXBException, IOException, DatatypeConfigurationException {

    BankOrder bankOrder = bankOrderCreateService.createBankOrder(invoicePayment);

    if (invoicePayment.getPaymentMode().getAutoConfirmBankOrder()) {
      bankOrderService.confirm(bankOrder);
    }
    invoicePayment.setBankOrder(bankOrder);

    invoicePaymentRepository.save(invoicePayment);
  }
}
