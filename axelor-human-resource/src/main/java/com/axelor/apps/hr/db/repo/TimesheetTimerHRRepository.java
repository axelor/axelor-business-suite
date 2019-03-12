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
package com.axelor.apps.hr.db.repo;

import com.axelor.apps.hr.db.TSTimer;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.service.timesheet.timer.TimesheetTimerService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

public class TimesheetTimerHRRepository extends TSTimerRepository {

  @Inject private TimesheetTimerService tsTimerService;

  @Override
  public TSTimer save(TSTimer tsTimer) {
    if (tsTimer.getStatusSelect() == TSTimerRepository.STATUS_STOP) {
      if (tsTimer.getTimesheetLine() != null) updateTimesheetLine(tsTimer);
      else {
        if (tsTimer.getDuration() > 59) {
          try {
            tsTimerService.generateTimesheetLine(tsTimer);
          } catch (AxelorException e) {
            e.printStackTrace();
          }
        }
      }
    }

    return super.save(tsTimer);
  }

  public void updateTimesheetLine(TSTimer tsTimer) {
    TimesheetLine timesheetLine = tsTimer.getTimesheetLine();

    timesheetLine.setProject(tsTimer.getProject());
    timesheetLine.setProduct(tsTimer.getProduct());
    timesheetLine.setHoursDuration(
        tsTimerService.convertSecondDurationInHours(tsTimer.getDuration()));
    timesheetLine.setComments(tsTimer.getComments());

    Beans.get(TimesheetLineRepository.class).save(timesheetLine);
  }
}
