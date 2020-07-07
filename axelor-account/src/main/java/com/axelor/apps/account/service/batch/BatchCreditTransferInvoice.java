/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.account.service.batch;

import com.axelor.apps.account.db.AccountingBatch;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCreateService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.repo.BankDetailsRepository;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BatchCreditTransferInvoice extends BatchStrategy {
  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final AppAccountService appAccountService;
  protected final InvoiceRepository invoiceRepo;
  protected final InvoicePaymentCreateService invoicePaymentCreateService;
  protected final InvoicePaymentRepository invoicePaymentRepository;

  @Inject
  public BatchCreditTransferInvoice(
      AppAccountService appAccountService,
      InvoiceRepository invoiceRepo,
      InvoicePaymentCreateService invoicePaymentCreateService,
      InvoicePaymentRepository invoicePaymentRepository) {
    this.appAccountService = appAccountService;
    this.invoiceRepo = invoiceRepo;
    this.invoicePaymentCreateService = invoicePaymentCreateService;
    this.invoicePaymentRepository = invoicePaymentRepository;
  }

  /**
   * Process invoices of the specified document type.
   *
   * @param operationTypeSelect
   * @return
   */
  protected List<InvoicePayment> processInvoices(int operationTypeSelect) {
    List<InvoicePayment> doneList = new ArrayList<>();
    List<Long> anomalyList = Lists.newArrayList(0L); // Can't pass an empty collection to the query
    AccountingBatch accountingBatch = batch.getAccountingBatch();
    boolean manageMultiBanks = appAccountService.getAppBase().getManageMultiBanks();
    StringBuilder filter = new StringBuilder();
    filter.append(
        "self.operationTypeSelect = :operationTypeSelect "
            + "AND self.statusSelect = :statusSelect "
            + "AND self.amountRemaining > 0 "
            + "AND self.hasPendingPayments = FALSE "
            + "AND self.company = :company "
            + "AND self.dueDate <= :dueDate "
            + "AND self.paymentMode = :paymentMode "
            + "AND self.id NOT IN (:anomalyList)"
            + "AND self.pfpValidateStatusSelect != :pfpValidateStatusSelect");

    if (manageMultiBanks) {
      filter.append(" AND self.companyBankDetails IN (:bankDetailsSet)");
    }

    if (accountingBatch.getCurrency() != null) {
      filter.append(" AND self.currency = :currency");
    }

    Query<Invoice> query =
        invoiceRepo
            .all()
            .filter(filter.toString())
            .bind("operationTypeSelect", operationTypeSelect)
            .bind("statusSelect", InvoiceRepository.STATUS_VENTILATED)
            .bind("company", accountingBatch.getCompany())
            .bind("dueDate", accountingBatch.getDueDate())
            .bind("paymentMode", accountingBatch.getPaymentMode())
            .bind("anomalyList", anomalyList)
            .bind("pfpValidateStatusSelect", InvoiceRepository.PFP_STATUS_LITIGATION);

    if (manageMultiBanks) {
      Set<BankDetails> bankDetailsSet = Sets.newHashSet(accountingBatch.getBankDetails());

      if (accountingBatch.getIncludeOtherBankAccounts()) {
        bankDetailsSet.addAll(accountingBatch.getCompany().getBankDetailsList());
      }

      query.bind("bankDetailsSet", bankDetailsSet);
    }

    if (accountingBatch.getCurrency() != null) {
      query.bind("currency", accountingBatch.getCurrency());
    }

    BankDetailsRepository bankDetailsRepo = Beans.get(BankDetailsRepository.class);
    BankDetails companyBankDetails = accountingBatch.getBankDetails();

    int fetchLimit = getFetchLimit();
    int offset = 0;
    for (List<Invoice> invoiceList;
        !(invoiceList = query.fetch(fetchLimit, offset)).isEmpty();
        JPA.clear()) {
      if (!JPA.em().contains(companyBankDetails)) {
        companyBankDetails = bankDetailsRepo.find(companyBankDetails.getId());
      }

      for (Invoice invoice : invoiceList) {
        ++offset;
        try {
          doneList.add(
              invoicePaymentCreateService.createInvoicePayment(invoice, companyBankDetails));
          incrementDone();
        } catch (Exception ex) {
          incrementAnomaly();
          anomalyList.add(invoice.getId());
          query.bind("anomalyList", anomalyList);
          TraceBackService.trace(ex, ExceptionOriginRepository.CREDIT_TRANSFER, batch.getId());
          ex.printStackTrace();
          log.error(
              String.format(
                  "Credit transfer batch for invoices: anomaly for invoice %s",
                  invoice.getInvoiceId()));
          break;
        }
      }
    }

    return doneList;
  }

  @Override
  protected void stop() {
    StringBuilder sb = new StringBuilder();
    sb.append(I18n.get(IExceptionMessage.BATCH_CREDIT_TRANSFER_REPORT_TITLE)).append(" ");
    sb.append(
        String.format(
            I18n.get(
                    IExceptionMessage.BATCH_CREDIT_TRANSFER_INVOICE_DONE_SINGULAR,
                    IExceptionMessage.BATCH_CREDIT_TRANSFER_INVOICE_DONE_PLURAL,
                    batch.getDone())
                + " ",
            batch.getDone()));
    sb.append(
        String.format(
            I18n.get(
                com.axelor.apps.base.exceptions.IExceptionMessage.ABSTRACT_BATCH_ANOMALY_SINGULAR,
                com.axelor.apps.base.exceptions.IExceptionMessage.ABSTRACT_BATCH_ANOMALY_PLURAL,
                batch.getAnomaly()),
            batch.getAnomaly()));
    addComment(sb.toString());
    super.stop();
  }
}
