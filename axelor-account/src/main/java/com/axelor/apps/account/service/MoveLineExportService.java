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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.AccountingReport;
import com.axelor.apps.base.db.Company;
import com.axelor.exception.AxelorException;
import com.axelor.meta.db.MetaFile;
import java.io.IOException;
import java.time.LocalDate;

public interface MoveLineExportService {

  public MetaFile exportMoveLine(AccountingReport accountingReport, int fetchLimit)
      throws AxelorException, IOException;

  public void replayExportMoveLine(AccountingReport accountingReport, int fetchLimit)
      throws AxelorException, IOException;

  public AccountingReport createAccountingReport(
      Company company, int exportTypeSelect, LocalDate startDate, LocalDate endDate)
      throws AxelorException;

  public void exportMoveLineTypeSelect1006(AccountingReport mlr, boolean replay, int fetchLimit)
      throws AxelorException, IOException;

  public void exportMoveLineTypeSelect1007(
      AccountingReport accountingReport, boolean replay, int fetchLimit)
      throws AxelorException, IOException;

  public void exportMoveLineTypeSelect1008(
      AccountingReport accountingReport, boolean replay, int fetchLimit)
      throws AxelorException, IOException;

  public void exportMoveLineTypeSelect1009(
      AccountingReport accountingReport, boolean replay, int fetchLimit)
      throws AxelorException, IOException;

  /**
   * Export general balance to CSV file.
   *
   * @param accountingReport
   * @return
   * @throws AxelorException
   * @throws IOException
   */
  void exportMoveLineTypeSelect1010(AccountingReport accountingReport, int fetchLimit)
      throws AxelorException, IOException;
}
