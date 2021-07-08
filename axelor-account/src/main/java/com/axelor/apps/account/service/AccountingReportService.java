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

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountingReport;
import com.axelor.apps.account.db.JournalType;
import com.axelor.exception.AxelorException;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

public interface AccountingReportService {

  public String getMoveLineList(AccountingReport accountingReport) throws AxelorException;

  public String buildQuery(AccountingReport accountingReport) throws AxelorException;

  public String addParams(String paramQuery, Object param);

  public String addParams(String paramQuery);

  @Transactional
  public void setSequence(AccountingReport accountingReport, String sequence);

  public String getSequence(AccountingReport accountingReport) throws AxelorException;

  public JournalType getJournalType(AccountingReport accountingReport) throws AxelorException;

  public Account getAccount(AccountingReport accountingReport);

  @Transactional
  public void setStatus(AccountingReport accountingReport);

  /** @param accountingReport */
  @Transactional
  public void setPublicationDateTime(AccountingReport accountingReport);

  /**
   * @param queryFilter
   * @return
   */
  public BigDecimal getDebitBalance();

  /**
   * @param queryFilter
   * @return
   */
  public BigDecimal getCreditBalance();

  public BigDecimal getDebitBalanceType4();

  public BigDecimal getCreditBalance(AccountingReport accountingReport, String queryFilter);

  public BigDecimal getCreditBalanceType4();

  public String getReportFileLink(AccountingReport accountingReport, String name)
      throws AxelorException;

  public boolean isThereTooManyLines(AccountingReport accountingReport) throws AxelorException;

  public void testReportedDateField(LocalDate reportedDate) throws AxelorException;

  public List<BigInteger> getAccountingReportDas2Pieces(
      AccountingReport accountingReport, boolean selectMoveLines);

  public void processAccountingReportMoveLines(AccountingReport accountingReport);

  public boolean isThereAlreadyDraftReportInPeriod(AccountingReport accountingReport)
      throws AxelorException;

  public boolean isThereAlreadyOngoingDas2ExportInPeriod(AccountingReport accountingReport)
      throws AxelorException;

  public AccountingReport createAccountingExportFromReport(
      AccountingReport accountingReport, int exportTypeSelect) throws AxelorException;
}
