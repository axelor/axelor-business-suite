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
package com.axelor.apps.businessproject.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentToolService;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderCreateService;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderService;
import com.axelor.apps.bankpayment.service.invoice.payment.InvoicePaymentValidateServiceBankPayImpl;
import com.axelor.apps.businessproject.db.InvoicingProject;
import com.axelor.apps.businessproject.db.repo.InvoicingProjectRepository;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

public class InvoicePaymentValidateProjectServiceImpl
    extends InvoicePaymentValidateServiceBankPayImpl {

  private InvoicingProjectRepository invoicingProjectRepo;

  @Inject
  public InvoicePaymentValidateProjectServiceImpl(
      PaymentModeService paymentModeService,
      MoveService moveService,
      MoveLineService moveLineService,
      AccountConfigService accountConfigService,
      InvoicePaymentRepository invoicePaymentRepository,
      ReconcileService reconcileService,
      BankOrderCreateService bankOrderCreateService,
      BankOrderService bankOrderService,
      InvoicePaymentToolService invoicePaymentToolService,
      InvoiceTermService invoiceTermService,
      InvoicingProjectRepository invoicingProjectRepo) {
    super(
        paymentModeService,
        moveService,
        moveLineService,
        accountConfigService,
        invoicePaymentRepository,
        reconcileService,
        bankOrderCreateService,
        bankOrderService,
        invoicePaymentToolService,
        invoiceTermService);
    this.invoicingProjectRepo = invoicingProjectRepo;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void validate(InvoicePayment invoicePayment, boolean force)
      throws AxelorException, JAXBException, IOException, DatatypeConfigurationException {
    super.validate(invoicePayment, force);
    Invoice invoice = invoicePayment.getInvoice();

    InvoicingProject invoicingProject =
        invoicingProjectRepo
            .all()
            .filter(
                "self.invoice.id = ?1 AND self.project.invoicingSequenceSelect = ?2",
                invoice.getId(),
                ProjectRepository.INVOICING_SEQ_INVOICE_PRE_TASK)
            .fetchOne();

    if (invoicingProject != null) {
      for (ProjectTask projectTask : invoicingProject.getProjectTaskSet()) {
        projectTask.setIsPaid(invoice.getHasPendingPayments());
      }
    }
  }
}
