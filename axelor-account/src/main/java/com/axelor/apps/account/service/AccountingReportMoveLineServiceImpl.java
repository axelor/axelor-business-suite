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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.AccountingReport;
import com.axelor.apps.account.db.AccountingReportMoveLine;
import com.axelor.apps.account.db.TaxPaymentMoveLine;
import com.axelor.apps.account.db.repo.AccountingReportMoveLineRepository;
import com.axelor.apps.account.db.repo.AccountingReportRepository;
import com.axelor.apps.account.db.repo.TaxPaymentMoveLineRepository;
import com.axelor.db.JPA;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigInteger;
import java.util.List;

public class AccountingReportMoveLineServiceImpl implements AccountingReportMoveLineService {

  protected AccountingReportMoveLineRepository accountingReportMoveLineRepo;

  protected AccountingReportRepository accountingReportRepo;

  protected TaxPaymentMoveLineRepository taxPaymentmoveLineRepo;

  @Inject
  public AccountingReportMoveLineServiceImpl(
      AccountingReportMoveLineRepository accountingReportMoveLineRepo,
      AccountingReportRepository accountingReportRepo,
      TaxPaymentMoveLineRepository taxPaymentmoveLineRepo) {
    this.accountingReportMoveLineRepo = accountingReportMoveLineRepo;
    this.taxPaymentmoveLineRepo = taxPaymentmoveLineRepo;
    this.accountingReportRepo = accountingReportRepo;
  }

  @Override
  public void createAccountingReportMoveLines(
      List<BigInteger> taxPaymentMoveLineIds, AccountingReport accountingReport) {

    int i = 0;
    for (BigInteger id : taxPaymentMoveLineIds) {
      TaxPaymentMoveLine taxPaymentMoveLine = taxPaymentmoveLineRepo.find(id.longValue());
      if (taxPaymentMoveLine != null) {
        createAccountingReportMoveLine(
            taxPaymentMoveLine, accountingReportRepo.find(accountingReport.getId()));
        i++;
        if (i % 10 == 0) {
          JPA.clear();
        }
      }
    }
  }

  @Transactional
  @Override
  public void createAccountingReportMoveLine(
      TaxPaymentMoveLine taxPaymentMoveLine, AccountingReport accountingReport) {

    AccountingReportMoveLine accountingReportMoveLine =
        new AccountingReportMoveLine(taxPaymentMoveLine, accountingReport);
    accountingReportMoveLine.setExcludeFromDas2Report(
        taxPaymentMoveLine.getReconcile().getCreditMoveLine().getExcludeFromDas2Report());
    accountingReportMoveLineRepo.save(accountingReportMoveLine);
  }

  @Override
  @Transactional
  public void processExportMoveLine(
      AccountingReportMoveLine reportMoveLine, AccountingReport accountingExport) {

    reportMoveLine.setAccountingExport(accountingExport);
    accountingReportMoveLineRepo.save(reportMoveLine);
  }
}
