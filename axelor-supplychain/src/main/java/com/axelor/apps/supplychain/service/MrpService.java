/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.supplychain.db.Mrp;
import com.axelor.exception.AxelorException;
import java.time.LocalDate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface MrpService {

  public void runCalculation(Mrp mrp, boolean isManualRun)
      throws AxelorException, InterruptedException, ExecutionException, TimeoutException;

  public void generateProposals(Mrp mrp) throws AxelorException;

  public void reset(Mrp mrp);

  /**
   * Search for the end date of the mrp. If the end date field in mrp is blank, search in the lines
   * the last date.
   *
   * @param mrp
   * @return the mrp end date
   */
  public LocalDate findMrpEndDate(Mrp mrp);

  public void setStatusSelect(Mrp mrp, int statusSelect);

  public void setError(Mrp mrp, String errorLog);
}
