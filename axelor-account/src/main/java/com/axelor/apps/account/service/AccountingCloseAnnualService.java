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

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Year;
import com.axelor.exception.AxelorException;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public interface AccountingCloseAnnualService {

  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public List<Move> generateCloseAnnualAccount(
      Year year,
      Account account,
      Partner partner,
      LocalDate endOfYearDate,
      LocalDate reportedBalanceDate,
      String origin,
      String moveDescription,
      boolean closeYear,
      boolean openYear,
      boolean allocatePerPartner)
      throws AxelorException;

  public List<Long> getAllAccountOfYear(
      Set<Account> accountSet, Year year, int fetchLimit, int position);

  public List<Pair<Long, Long>> assignPartner(
      List<Long> accountIdList, Year year, boolean allocatePerPartner);
}
