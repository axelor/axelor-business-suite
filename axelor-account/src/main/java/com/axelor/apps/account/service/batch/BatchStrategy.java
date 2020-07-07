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

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountingBatch;
import com.axelor.apps.account.db.AccountingReport;
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.PaymentScheduleLine;
import com.axelor.apps.account.db.PaymentVoucher;
import com.axelor.apps.account.db.Reimbursement;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountCustomerService;
import com.axelor.apps.account.service.MoveLineExportService;
import com.axelor.apps.account.service.ReimbursementExportService;
import com.axelor.apps.account.service.ReimbursementImportService;
import com.axelor.apps.account.service.ReimbursementService;
import com.axelor.apps.account.service.RejectImportService;
import com.axelor.apps.account.service.bankorder.file.cfonb.CfonbExportService;
import com.axelor.apps.account.service.bankorder.file.cfonb.CfonbImportService;
import com.axelor.apps.account.service.debtrecovery.DebtRecoveryService;
import com.axelor.apps.account.service.debtrecovery.DoubtfulCustomerService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

public abstract class BatchStrategy extends AbstractBatch {

  protected DebtRecoveryService debtRecoveryService;
  protected DoubtfulCustomerService doubtfulCustomerService;
  protected ReimbursementExportService reimbursementExportService;
  protected ReimbursementImportService reimbursementImportService;
  protected RejectImportService rejectImportService;
  protected CfonbExportService cfonbExportService;
  protected CfonbImportService cfonbImportService;
  protected PaymentModeService paymentModeService;
  protected AccountCustomerService accountCustomerService;
  protected MoveLineExportService moveLineExportService;
  protected BatchAccountCustomer batchAccountCustomer;

  @Inject protected BatchRepository batchRepo;

  @Inject protected CompanyRepository companyRepo;

  @Inject protected MoveService moveService;

  @Inject protected MoveRepository moveRepo;

  @Inject protected MoveLineService moveLineService;

  @Inject protected MoveLineRepository moveLineRepo;

  @Inject protected ReimbursementService reimbursementService;

  protected BatchStrategy() {}

  protected BatchStrategy(DebtRecoveryService debtRecoveryService) {
    super();
    this.debtRecoveryService = debtRecoveryService;
  }

  protected BatchStrategy(
      DoubtfulCustomerService doubtfulCustomerService, BatchAccountCustomer batchAccountCustomer) {
    super();
    this.doubtfulCustomerService = doubtfulCustomerService;
    this.batchAccountCustomer = batchAccountCustomer;
  }

  protected BatchStrategy(
      ReimbursementExportService reimbursementExportService,
      CfonbExportService cfonbExportService,
      BatchAccountCustomer batchAccountCustomer) {
    super();
    this.reimbursementExportService = reimbursementExportService;
    this.cfonbExportService = cfonbExportService;
    this.batchAccountCustomer = batchAccountCustomer;
  }

  protected BatchStrategy(
      ReimbursementImportService reimbursementImportService,
      RejectImportService rejectImportService,
      BatchAccountCustomer batchAccountCustomer) {
    super();
    this.reimbursementImportService = reimbursementImportService;
    this.rejectImportService = rejectImportService;
    this.batchAccountCustomer = batchAccountCustomer;
  }

  protected BatchStrategy(AccountCustomerService accountCustomerService) {
    super();
    this.accountCustomerService = accountCustomerService;
  }

  protected BatchStrategy(MoveLineExportService moveLineExportService) {
    super();
    this.moveLineExportService = moveLineExportService;
  }

  protected void updateInvoice(Invoice invoice) {

    invoice.addBatchSetItem(batchRepo.find(batch.getId()));

    incrementDone();
  }

  protected void updateReimbursement(Reimbursement reimbursement) {

    reimbursement.addBatchSetItem(batchRepo.find(batch.getId()));

    incrementDone();
  }

  protected void updatePaymentScheduleLine(PaymentScheduleLine paymentScheduleLine) {

    paymentScheduleLine.addBatchSetItem(batchRepo.find(batch.getId()));

    incrementDone();
  }

  protected void updatePaymentVoucher(PaymentVoucher paymentVoucher) {

    paymentVoucher.addBatchSetItem(batchRepo.find(batch.getId()));

    incrementDone();
  }

  protected void updatePartner(Partner partner) {

    partner.addBatchSetItem(batchRepo.find(batch.getId()));

    incrementDone();
  }

  protected void updateAccountingSituation(AccountingSituation accountingSituation) {

    accountingSituation.addBatchSetItem(batchRepo.find(batch.getId()));

    incrementDone();
  }

  protected void updateAccountingReport(AccountingReport accountingReport) {

    accountingReport.addBatchSetItem(batchRepo.find(batch.getId()));

    incrementDone();
  }

  protected void updateAccount(Account account) {

    account.addBatchSetItem(batchRepo.find(batch.getId()));

    incrementDone();
  }

  protected void updateAccountMove(Move move, boolean incrementDone) {

    move.addBatchSetItem(Beans.get(BatchRepository.class).find(batch.getId()));

    if (incrementDone) {
      incrementDone();
    } else {
      checkPoint();
    }
  }

  public void testAccountingBatchBankDetails(AccountingBatch accountingBatch)
      throws AxelorException {

    if (accountingBatch.getBankDetails() == null) {
      throw new AxelorException(
          accountingBatch,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.BATCH_STRATEGY_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountingBatch.getCode());
    }

    this.cfonbExportService.testBankDetailsField(accountingBatch.getBankDetails());
  }

  @Override
  public int getFetchLimit() {
    return batch.getAccountingBatch().getBatchFetchLimit() > 0
        ? batch.getAccountingBatch().getBatchFetchLimit()
        : super.getFetchLimit();
  }
}
