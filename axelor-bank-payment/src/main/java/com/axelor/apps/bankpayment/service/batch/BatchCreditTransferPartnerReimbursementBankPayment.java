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
package com.axelor.apps.bankpayment.service.batch;

import com.axelor.apps.account.db.AccountingBatch;
import com.axelor.apps.account.db.Reimbursement;
import com.axelor.apps.account.db.repo.AccountingBatchRepository;
import com.axelor.apps.account.db.repo.ReimbursementRepository;
import com.axelor.apps.account.service.ReimbursementExportService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.batch.BatchCreditTransferPartnerReimbursement;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.BankOrderLine;
import com.axelor.apps.bankpayment.db.repo.BankOrderRepository;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderCreateService;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderLineService;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderService;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.List;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchCreditTransferPartnerReimbursementBankPayment
    extends BatchCreditTransferPartnerReimbursement {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AppAccountService appAccountService;
  protected ReimbursementRepository reimbursementRepo;
  protected BankOrderCreateService bankOrderCreateService;
  protected BankOrderService bankOrderService;
  protected BankOrderLineService bankOrderLineService;
  protected BankOrderRepository bankOrderRepo;

  @Inject
  public BatchCreditTransferPartnerReimbursementBankPayment(
      PartnerRepository partnerRepo,
      PartnerService partnerService,
      ReimbursementExportService reimbursementExportService,
      AppAccountService appAccountService,
      ReimbursementRepository reimbursementRepo,
      BankOrderCreateService bankOrderCreateService,
      BankOrderService bankOrderService,
      BankOrderLineService bankOrderLineService,
      BankOrderRepository bankOrderRepo) {
    super(partnerRepo, partnerService, reimbursementExportService);
    this.appAccountService = appAccountService;
    this.reimbursementRepo = reimbursementRepo;
    this.bankOrderCreateService = bankOrderCreateService;
    this.bankOrderService = bankOrderService;
    this.bankOrderLineService = bankOrderLineService;
    this.bankOrderRepo = bankOrderRepo;
  }

  @Override
  protected void process() {
    super.process();
    AccountingBatch accountingBatch = batch.getAccountingBatch();

    if (!accountingBatch.getPaymentMode().getGenerateBankOrder()) {
      return;
    }

    // Fetch all reimbursements that are validated for the specified company.
    Query<Reimbursement> query =
        reimbursementRepo
            .all()
            .filter("self.statusSelect = :statusSelect AND self.company = :company");
    query.bind("statusSelect", ReimbursementRepository.STATUS_VALIDATED);
    query.bind("company", accountingBatch.getCompany());
    List<Reimbursement> reimbursementList = null;
    int fetchLimit = getFetchLimit();

    int offset = 0;
    while (!(reimbursementList = query.fetch(fetchLimit, offset)).isEmpty()) {
      if (reimbursementList.isEmpty()) {
        return;
      }

      accountingBatch = Beans.get(AccountingBatchRepository.class).find(accountingBatch.getId());

      try {
        createBankOrder(accountingBatch, reimbursementList);
      } catch (Exception ex) {
        TraceBackService.trace(ex);
        logger.error(ex.getLocalizedMessage());
      }
      offset += reimbursementList.size();
    }
  }

  /**
   * Create a bank order for the specified list of reimbursements.
   *
   * @param accountingBatch
   * @param reimbursementList
   * @return
   * @throws AxelorException
   * @throws DatatypeConfigurationException
   * @throws IOException
   * @throws JAXBException
   */
  @Transactional(rollbackOn = {Exception.class})
  protected BankOrder createBankOrder(
      AccountingBatch accountingBatch, List<Reimbursement> reimbursementList)
      throws AxelorException, JAXBException, IOException, DatatypeConfigurationException {
    LocalDate bankOrderDate =
        accountingBatch.getDueDate() != null
            ? accountingBatch.getDueDate()
            : appBaseService.getTodayDate();
    BankOrder bankOrder =
        bankOrderCreateService.createBankOrder(
            accountingBatch.getPaymentMode(),
            BankOrderRepository.PARTNER_TYPE_CUSTOMER,
            bankOrderDate,
            accountingBatch.getCompany(),
            accountingBatch.getBankDetails(),
            accountingBatch.getCompany().getCurrency(),
            null,
            null,
            BankOrderRepository.TECHNICAL_ORIGIN_AUTOMATIC);

    for (Reimbursement reimbursement : reimbursementList) {
      BankOrderLine bankOrderLine =
          bankOrderLineService.createBankOrderLine(
              accountingBatch.getPaymentMode().getBankOrderFileFormat(),
              null,
              reimbursement.getPartner(),
              reimbursement.getBankDetails(),
              reimbursement.getAmountToReimburse(),
              accountingBatch.getCompany().getCurrency(),
              bankOrderDate,
              reimbursement.getRef(),
              reimbursement.getDescription(),
              reimbursement);
      bankOrder.addBankOrderLineListItem(bankOrderLine);
      Beans.get(ReimbursementExportService.class)
          .reimburse(reimbursement, accountingBatch.getCompany());
    }

    bankOrder = bankOrderRepo.save(bankOrder);
    bankOrderService.confirm(bankOrder);
    return bankOrder;
  }
}
