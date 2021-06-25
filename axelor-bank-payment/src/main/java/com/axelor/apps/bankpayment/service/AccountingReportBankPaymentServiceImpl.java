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
package com.axelor.apps.bankpayment.service;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.AccountingReport;
import com.axelor.apps.account.db.repo.AccountRepository;
import com.axelor.apps.account.db.repo.AccountingReportRepository;
import com.axelor.apps.account.service.AccountingReportServiceImpl;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.bankpayment.report.IReport;
import com.axelor.apps.base.service.BankDetailsService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

public class AccountingReportBankPaymentServiceImpl extends AccountingReportServiceImpl
    implements AccountingReportBankPaymentService {

  @Inject
  public AccountingReportBankPaymentServiceImpl(
      AppAccountService appBaseService,
      AccountingReportRepository accountingReportRepo,
      AccountRepository accountRepo,
      AccountConfigService accountConfigService) {
    super(appBaseService, accountingReportRepo, accountRepo, accountConfigService);
  }

  @Override
  public String createDomainForBankDetails(AccountingReport accountingReport) {
    return Beans.get(BankDetailsService.class)
        .getActiveCompanyBankDetails(accountingReport.getCompany(), accountingReport.getCurrency());
  }

  @Override
  public String getReportFileLink(AccountingReport accountingReport, String name)
      throws AxelorException {
    if (accountingReport.getReportType().getTypeSelect()
        == AccountingReportRepository.REPORT_BANK_RECONCILIATION_STATEMENT) {
      return ReportFactory.createReport(IReport.BANK_PAYMENT_REPORT_TYPE, name + "-${date}")
          .addParam("AccountingReportId", accountingReport.getId())
          .addParam("Locale", ReportSettings.getPrintingLocale(null))
          .addFormat(accountingReport.getExportTypeSelect())
          .toAttach(accountingReport)
          .generate()
          .getFileLink();
    } else {
      return super.getReportFileLink(accountingReport, name);
    }
  }
}
