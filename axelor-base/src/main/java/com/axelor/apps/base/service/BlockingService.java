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
package com.axelor.apps.base.service;

import com.axelor.apps.base.db.Blocking;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.inject.Beans;
import com.google.inject.Singleton;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Singleton
public class BlockingService {

  /**
   * Checks if {@code partner} is blocked for the {@code blockingType}
   *
   * @param partner Partner to check blocking
   * @param company Company associated with the blocking
   * @param blockingType Type of blocking
   * @return blocking if partner is blocked for provided company and blocking type, null otherwise
   */
  public Blocking getBlocking(Partner partner, Company company, int blockingType) {
    List<Blocking> blockings = partner.getBlockingList();

    for (Blocking blocking : blockings) {
      if (blocking.getCompanySet().contains(company)
          && blocking.getBlockingSelect().equals(blockingType)
          && blocking.getBlockingToDate().compareTo(Beans.get(AppBaseService.class).getTodayDate())
              >= 0) {
        return blocking;
      }
    }

    return null;
  }

  /**
   * @param company
   * @param blockingType
   * @return the query to get blocked partners ids for the given company and blocking type
   */
  public String listOfBlockedPartner(Company company, int blockingType) {
    return String.format(
        "SELECT DISTINCT partner.id FROM Partner partner "
            + "LEFT JOIN partner.blockingList blocking "
            + "LEFT JOIN blocking.companySet company "
            + "WHERE blocking.blockingSelect = %d "
            + "AND blocking.blockingToDate >= '%s' "
            + "AND company.id = %d",
        blockingType,
        Beans.get(AppBaseService.class)
            .getTodayDate()
            .format(DateTimeFormatter.ofPattern("YYYY-MM-dd")),
        company.getId());
  }
}
