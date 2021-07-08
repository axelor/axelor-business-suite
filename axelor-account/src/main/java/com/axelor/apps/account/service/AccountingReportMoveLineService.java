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
import java.math.BigInteger;
import java.util.List;

public interface AccountingReportMoveLineService {

  public void createAccountingReportMoveLines(
      List<BigInteger> taxPaymentMoveLineIds, AccountingReport accountingReport);

  public void createAccountingReportMoveLine(
      TaxPaymentMoveLine taxPaymentMoveLine, AccountingReport accountingReport);

  public void processExportMoveLine(
      AccountingReportMoveLine reportMoveLine, AccountingReport accountingExport);
}
