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
package com.axelor.apps.account.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.AccountAccountRepository;
import com.axelor.apps.account.db.repo.AccountRepository;
import com.axelor.apps.account.db.repo.AccountingBatchAccountRepository;
import com.axelor.apps.account.db.repo.AccountingBatchRepository;
import com.axelor.apps.account.db.repo.AccountingReportManagementRepository;
import com.axelor.apps.account.db.repo.AccountingReportRepository;
import com.axelor.apps.account.db.repo.AnalyticMoveLineMngtRepository;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.account.db.repo.DepositSlipAccountRepository;
import com.axelor.apps.account.db.repo.DepositSlipRepository;
import com.axelor.apps.account.db.repo.FixedAssetManagementRepository;
import com.axelor.apps.account.db.repo.FixedAssetRepository;
import com.axelor.apps.account.db.repo.InvoiceBatchAccountRepository;
import com.axelor.apps.account.db.repo.InvoiceBatchRepository;
import com.axelor.apps.account.db.repo.InvoiceManagementRepository;
import com.axelor.apps.account.db.repo.InvoicePaymentManagementRepository;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.JournalManagementRepository;
import com.axelor.apps.account.db.repo.JournalRepository;
import com.axelor.apps.account.db.repo.MoveLineManagementRepository;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveManagementRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.db.repo.PartnerAccountRepository;
import com.axelor.apps.account.db.repo.PaymentVoucherManagementRepository;
import com.axelor.apps.account.db.repo.PaymentVoucherRepository;
import com.axelor.apps.account.db.repo.ReconcileGroupAccountRepository;
import com.axelor.apps.account.db.repo.ReconcileGroupRepository;
import com.axelor.apps.account.db.repo.ReconcileManagementRepository;
import com.axelor.apps.account.db.repo.ReconcileRepository;
import com.axelor.apps.account.db.repo.SubrogationReleaseManagementRepository;
import com.axelor.apps.account.db.repo.SubrogationReleaseRepository;
import com.axelor.apps.account.service.AccountManagementAccountService;
import com.axelor.apps.account.service.AccountManagementServiceAccountImpl;
import com.axelor.apps.account.service.AccountingReportService;
import com.axelor.apps.account.service.AccountingReportServiceImpl;
import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.account.service.AccountingSituationServiceImpl;
import com.axelor.apps.account.service.AddressServiceAccountImpl;
import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.apps.account.service.AnalyticMoveLineServiceImpl;
import com.axelor.apps.account.service.BankDetailsServiceAccountImpl;
import com.axelor.apps.account.service.DepositSlipService;
import com.axelor.apps.account.service.DepositSlipServiceImpl;
import com.axelor.apps.account.service.FiscalPositionAccountService;
import com.axelor.apps.account.service.FiscalPositionAccountServiceImpl;
import com.axelor.apps.account.service.FixedAssetLineService;
import com.axelor.apps.account.service.FixedAssetLineServiceImpl;
import com.axelor.apps.account.service.FixedAssetService;
import com.axelor.apps.account.service.FixedAssetServiceImpl;
import com.axelor.apps.account.service.MoveLineExportService;
import com.axelor.apps.account.service.MoveLineExportServiceImpl;
import com.axelor.apps.account.service.NotificationService;
import com.axelor.apps.account.service.NotificationServiceImpl;
import com.axelor.apps.account.service.PaymentScheduleLineService;
import com.axelor.apps.account.service.PaymentScheduleLineServiceImpl;
import com.axelor.apps.account.service.PaymentScheduleService;
import com.axelor.apps.account.service.PaymentScheduleServiceImpl;
import com.axelor.apps.account.service.PeriodServiceAccountImpl;
import com.axelor.apps.account.service.ReconcileGroupSequenceService;
import com.axelor.apps.account.service.ReconcileGroupSequenceServiceImpl;
import com.axelor.apps.account.service.ReconcileGroupService;
import com.axelor.apps.account.service.ReconcileGroupServiceImpl;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.ReconcileServiceImpl;
import com.axelor.apps.account.service.SubrogationReleaseService;
import com.axelor.apps.account.service.SubrogationReleaseServiceImpl;
import com.axelor.apps.account.service.TemplateMessageAccountService;
import com.axelor.apps.account.service.TemplateMessageAccountServiceImpl;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.app.AppAccountServiceImpl;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceLineServiceImpl;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.InvoiceServiceImpl;
import com.axelor.apps.account.service.invoice.print.InvoicePrintService;
import com.axelor.apps.account.service.invoice.print.InvoicePrintServiceImpl;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.account.service.payment.PaymentModeServiceImpl;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCancelService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCancelServiceImpl;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCreateService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCreateServiceImpl;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentToolService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentToolServiceImpl;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentValidateService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentValidateServiceImpl;
import com.axelor.apps.account.service.umr.UmrNumberService;
import com.axelor.apps.account.service.umr.UmrNumberServiceImpl;
import com.axelor.apps.base.db.repo.PartnerAddressRepository;
import com.axelor.apps.base.db.repo.PartnerBaseRepository;
import com.axelor.apps.base.service.BankDetailsServiceImpl;
import com.axelor.apps.base.service.PeriodServiceImpl;
import com.axelor.apps.base.service.tax.AccountManagementServiceImpl;
import com.axelor.apps.base.service.tax.FiscalPositionServiceImpl;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.apps.message.service.TemplateMessageServiceImpl;

public class AccountModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(AddressServiceAccountImpl.class);

    bind(AccountManagementServiceImpl.class).to(AccountManagementServiceAccountImpl.class);

    bind(AccountManagementAccountService.class).to(AccountManagementServiceAccountImpl.class);

    bind(FiscalPositionServiceImpl.class).to(FiscalPositionAccountServiceImpl.class);

    bind(FiscalPositionAccountService.class).to(FiscalPositionAccountServiceImpl.class);

    bind(TemplateMessageService.class).to(TemplateMessageServiceImpl.class);

    bind(InvoiceRepository.class).to(InvoiceManagementRepository.class);

    bind(MoveRepository.class).to(MoveManagementRepository.class);

    bind(MoveLineRepository.class).to(MoveLineManagementRepository.class);

    bind(AccountingReportRepository.class).to(AccountingReportManagementRepository.class);

    bind(AccountingReportService.class).to(AccountingReportServiceImpl.class);

    bind(JournalRepository.class).to(JournalManagementRepository.class);

    bind(PaymentVoucherRepository.class).to(PaymentVoucherManagementRepository.class);

    bind(InvoiceService.class).to(InvoiceServiceImpl.class);

    bind(InvoicePrintService.class).to(InvoicePrintServiceImpl.class);

    bind(PartnerBaseRepository.class).to(PartnerAccountRepository.class);

    bind(AnalyticMoveLineService.class).to(AnalyticMoveLineServiceImpl.class);

    bind(InvoicePaymentRepository.class).to(InvoicePaymentManagementRepository.class);

    bind(InvoicePaymentValidateService.class).to(InvoicePaymentValidateServiceImpl.class);

    bind(InvoicePaymentCreateService.class).to(InvoicePaymentCreateServiceImpl.class);

    bind(InvoicePaymentCancelService.class).to(InvoicePaymentCancelServiceImpl.class);

    bind(InvoicePaymentToolService.class).to(InvoicePaymentToolServiceImpl.class);

    bind(AnalyticMoveLineRepository.class).to(AnalyticMoveLineMngtRepository.class);

    bind(ReconcileService.class).to(ReconcileServiceImpl.class);

    bind(ReconcileRepository.class).to(ReconcileManagementRepository.class);

    bind(AppAccountService.class).to(AppAccountServiceImpl.class);

    bind(AccountingSituationService.class).to(AccountingSituationServiceImpl.class);

    bind(PaymentModeService.class).to(PaymentModeServiceImpl.class);

    bind(BankDetailsServiceImpl.class).to(BankDetailsServiceAccountImpl.class);

    bind(MoveLineExportService.class).to(MoveLineExportServiceImpl.class);

    bind(AccountingBatchRepository.class).to(AccountingBatchAccountRepository.class);

    bind(InvoiceBatchRepository.class).to(InvoiceBatchAccountRepository.class);

    bind(AccountRepository.class).to(AccountAccountRepository.class);

    bind(SubrogationReleaseService.class).to(SubrogationReleaseServiceImpl.class);

    bind(NotificationService.class).to(NotificationServiceImpl.class);

    bind(PaymentScheduleService.class).to(PaymentScheduleServiceImpl.class);

    bind(PaymentScheduleLineService.class).to(PaymentScheduleLineServiceImpl.class);

    bind(DepositSlipRepository.class).to(DepositSlipAccountRepository.class);

    bind(DepositSlipService.class).to(DepositSlipServiceImpl.class);

    bind(InvoiceLineService.class).to(InvoiceLineServiceImpl.class);

    bind(TemplateMessageAccountService.class).to(TemplateMessageAccountServiceImpl.class);

    PartnerAddressRepository.modelPartnerFieldMap.put(Invoice.class.getName(), "partner");

    bind(UmrNumberService.class).to(UmrNumberServiceImpl.class);

    bind(ReconcileGroupSequenceService.class).to(ReconcileGroupSequenceServiceImpl.class);

    bind(ReconcileGroupRepository.class).to(ReconcileGroupAccountRepository.class);

    bind(ReconcileGroupService.class).to(ReconcileGroupServiceImpl.class);

    bind(SubrogationReleaseRepository.class).to(SubrogationReleaseManagementRepository.class);

    bind(PeriodServiceImpl.class).to(PeriodServiceAccountImpl.class);

    bind(FixedAssetRepository.class).to(FixedAssetManagementRepository.class);

    bind(FixedAssetService.class).to(FixedAssetServiceImpl.class);

    bind(FixedAssetLineService.class).to(FixedAssetLineServiceImpl.class);
  }
}
