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

import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.AccountingBatch;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.PaymentSchedule;
import com.axelor.apps.account.db.PaymentScheduleLine;
import com.axelor.apps.account.db.repo.PaymentModeRepository;
import com.axelor.apps.account.db.repo.PaymentScheduleLineRepository;
import com.axelor.apps.account.db.repo.PaymentScheduleRepository;
import com.axelor.apps.account.service.PaymentScheduleLineService;
import com.axelor.apps.account.service.PaymentScheduleService;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.bankpayment.db.BankPaymentConfig;
import com.axelor.apps.bankpayment.service.config.BankPaymentConfigService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BankDetailsRepository;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.tool.QueryBuilder;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchDirectDebitPaymentSchedule extends BatchDirectDebit {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  protected void process() {
    processPaymentScheduleLines(PaymentScheduleRepository.TYPE_TERMS);

    if (batchBankPaymentService.paymentScheduleLineDoneListExists(batch) && generateBankOrderFlag) {
      try {
        findBatch();
        batchBankPaymentService.createBankOrderFromPaymentScheduleLines(batch);
      } catch (Exception e) {
        TraceBackService.trace(e, ExceptionOriginRepository.DIRECT_DEBIT, batch.getId());
        logger.error(e.getLocalizedMessage());
      }
    }
  }

  protected void processPaymentScheduleLines(int paymentScheduleType) {
    AccountingBatch accountingBatch = batch.getAccountingBatch();

    if (generateBankOrderFlag) {
      Preconditions.checkNotNull(accountingBatch.getCompany(), I18n.get("Company is missing."));
      Preconditions.checkNotNull(
          accountingBatch.getPaymentMode(), I18n.get("Payment method is missing."));
      BankDetails companyBankDetails = getCompanyBankDetails(accountingBatch);
      AccountManagement accountManagement =
          Beans.get(PaymentModeService.class)
              .getAccountManagement(
                  accountingBatch.getPaymentMode(),
                  accountingBatch.getCompany(),
                  companyBankDetails);
      Preconditions.checkNotNull(accountManagement, I18n.get("Account management is missing."));
      Preconditions.checkNotNull(
          accountManagement.getBankDetails(),
          I18n.get("Bank details in account management is missing."));
      Preconditions.checkNotNull(
          companyBankDetails.getCurrency(),
          I18n.get("Currency in company bank details is missing."));
      Preconditions.checkArgument(
          new File(accountingBatch.getPaymentMode().getBankOrderExportFolderPath()).exists(),
          String.format(
              I18n.get("Bank order export folder does not exist: %s"),
              accountingBatch.getPaymentMode().getBankOrderExportFolderPath()));

      BankPaymentConfigService bankPaymentConfigService = Beans.get(BankPaymentConfigService.class);

      try {
        BankPaymentConfig bankPaymentConfig =
            bankPaymentConfigService.getBankPaymentConfig(accountingBatch.getCompany());
        bankPaymentConfigService.getIcsNumber(bankPaymentConfig);
      } catch (AxelorException e) {
        throw new RuntimeException(e);
      }
    }

    QueryBuilder<PaymentScheduleLine> queryBuilder = QueryBuilder.of(PaymentScheduleLine.class);

    queryBuilder.add("self.paymentSchedule.statusSelect = :paymentScheduleStatusSelect");
    queryBuilder.bind("paymentScheduleStatusSelect", PaymentScheduleRepository.STATUS_CONFIRMED);

    queryBuilder.add("self.paymentSchedule.typeSelect = :paymentScheduleTypeSelect");
    queryBuilder.bind("paymentScheduleTypeSelect", paymentScheduleType);

    queryBuilder.add("self.paymentSchedule.invoiceTypeSelect = :paymentScheduleInvoiceTypeSelect");
    queryBuilder.bind(
        "paymentScheduleInvoiceTypeSelect", PaymentScheduleRepository.INVOICE_TYPE_SALE);

    queryBuilder.add("self.statusSelect = :statusSelect");
    queryBuilder.bind("statusSelect", PaymentScheduleLineRepository.STATUS_IN_PROGRESS);

    LocalDate dueDate =
        accountingBatch.getDueDate() != null
            ? accountingBatch.getDueDate()
            : Beans.get(AppBaseService.class).getTodayDate();
    queryBuilder.add("self.scheduleDate <= :dueDate");
    queryBuilder.bind("dueDate", dueDate);

    if (accountingBatch.getCompany() != null) {
      queryBuilder.add(
          "self.paymentSchedule.company IS NULL OR self.paymentSchedule.company = :company");
      queryBuilder.bind("company", accountingBatch.getCompany());
    }

    queryBuilder.add(
        "self.paymentSchedule.partner.id NOT IN (SELECT DISTINCT partner.id FROM Partner partner LEFT JOIN partner.blockingList blocking WHERE blocking.blockingSelect = :blockingSelect AND blocking.blockingToDate >= :blockingToDate)");
    queryBuilder.bind("blockingSelect", BlockingRepository.DEBIT_BLOCKING);
    queryBuilder.bind("blockingToDate", Beans.get(AppBaseService.class).getTodayDate());

    if (accountingBatch.getBankDetails() != null) {
      Set<BankDetails> bankDetailsSet = Sets.newHashSet(accountingBatch.getBankDetails());

      if (accountingBatch.getIncludeOtherBankAccounts()
          && appBaseService.getAppBase().getManageMultiBanks()) {
        bankDetailsSet.addAll(accountingBatch.getCompany().getBankDetailsList());
      }

      queryBuilder.add(
          "self.paymentSchedule.companyBankDetails IS NULL OR self.paymentSchedule.companyBankDetails IN (:bankDetailsSet)");
      queryBuilder.bind("bankDetailsSet", bankDetailsSet);
    }

    if (accountingBatch.getPaymentMode() != null) {
      queryBuilder.add(
          "self.paymentSchedule.paymentMode IS NULL OR self.paymentSchedule.paymentMode = :paymentMode");
      queryBuilder.bind("paymentMode", accountingBatch.getPaymentMode());
    }

    queryBuilder.add(":batch NOT MEMBER OF self.batchSet");
    queryBuilder.bind("batch", batch);

    processQuery(queryBuilder);
  }

  private void processQuery(QueryBuilder<PaymentScheduleLine> queryBuilder) {
    Query<PaymentScheduleLine> query = queryBuilder.build();

    List<PaymentScheduleLine> paymentScheduleLineList;
    PaymentScheduleService paymentScheduleService = Beans.get(PaymentScheduleService.class);
    PaymentScheduleLineService paymentScheduleLineService =
        Beans.get(PaymentScheduleLineService.class);
    BankDetailsRepository bankDetailsRepo = Beans.get(BankDetailsRepository.class);

    BankDetails companyBankDetails = getCompanyBankDetails(batch.getAccountingBatch());

    while (!(paymentScheduleLineList = query.fetch(FETCH_LIMIT)).isEmpty()) {
      findBatch();
      companyBankDetails = bankDetailsRepo.find(companyBankDetails.getId());
      PaymentMode directDebitPaymentMode = batch.getAccountingBatch().getPaymentMode();

      for (PaymentScheduleLine paymentScheduleLine : paymentScheduleLineList) {
        try {
          if (generateBankOrderFlag) {
            PaymentSchedule paymentSchedule = paymentScheduleLine.getPaymentSchedule();
            BankDetails bankDetails = paymentScheduleService.getBankDetails(paymentSchedule);
            Preconditions.checkArgument(
                bankDetails.getActive(), I18n.get("Bank details are inactive."));

            if (directDebitPaymentMode.getOrderTypeSelect()
                == PaymentModeRepository.ORDER_TYPE_SEPA_DIRECT_DEBIT) {
              Partner partner = paymentSchedule.getPartner();
              Preconditions.checkNotNull(partner, I18n.get("Partner is missing."));
              Preconditions.checkNotNull(
                  partner.getActiveUmr(), I18n.get("Partner active UMR is missing."));
            }
          }

          paymentScheduleLineService.createPaymentMove(
              paymentScheduleLine, companyBankDetails, directDebitPaymentMode);
          incrementDone(paymentScheduleLine);
        } catch (Exception e) {
          TraceBackService.trace(e, ExceptionOriginRepository.DIRECT_DEBIT, batch.getId());
          incrementAnomaly(paymentScheduleLine);
          break;
        }
      }

      JPA.clear();
    }
  }

  protected void incrementDone(PaymentScheduleLine paymentScheduleLine) {
    paymentScheduleLine.addBatchSetItem(batch);
    _incrementDone();
  }

  protected void incrementAnomaly(PaymentScheduleLine paymentScheduleLine) {
    findBatch();
    paymentScheduleLine = paymentScheduleLineRepo.find(paymentScheduleLine.getId());
    paymentScheduleLine.addBatchSetItem(batch);
    _incrementAnomaly();
  }
}
