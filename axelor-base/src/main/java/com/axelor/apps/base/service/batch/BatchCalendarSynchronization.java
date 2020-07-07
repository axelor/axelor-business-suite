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
package com.axelor.apps.base.service.batch;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.ICalendar;
import com.axelor.apps.base.db.repo.ICalendarRepository;
import com.axelor.apps.base.ical.ICalendarService;
import com.axelor.db.Query;
import com.google.inject.Inject;
import java.util.List;

public class BatchCalendarSynchronization extends BatchStrategy {

  @Inject ICalendarService iCalendarService;

  @Inject ICalendarRepository repo;

  @Override
  protected void process() {
    final Company company = batch.getBaseBatch().getCompany();
    int fetchLimit = getFetchLimit();

    List<ICalendar> calendars = null;
    Query<ICalendar> query =
        repo.all()
            .filter("self.user.activeCompany = :company AND self.isValid = TRUE")
            .bind("company", company);

    int offset = 0;
    while (!(calendars = query.fetch(fetchLimit, offset)).isEmpty()) {
      offset += calendars.size();
      for (ICalendar calendar : calendars) {
        try {
          iCalendarService.sync(
              calendar,
              batch.getBaseBatch().getAllEvents(),
              batch.getBaseBatch().getSynchronizationDuration());
          incrementDone();
        } catch (Exception e) {
          e.printStackTrace();
          incrementAnomaly();
        }
      }
    }
  }
}
