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
package com.axelor.apps.hr.service.timesheet.timer;

import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.hr.db.TSTimer;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.TSTimerRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.timesheet.TimesheetLineService;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.tool.date.DurationTool;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimesheetTimerServiceImpl implements TimesheetTimerService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Transactional
  public void pause(TSTimer timer) {
    timer.setStatusSelect(TSTimerRepository.STATUS_PAUSE);
    calculateDuration(timer);
  }

  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void stop(TSTimer timer) throws AxelorException {
    timer.setStatusSelect(TSTimerRepository.STATUS_STOP);
    calculateDuration(timer);
    if (timer.getDuration() > 59) {
      generateTimesheetLine(timer);
    } else {
      throw new AxelorException(
          TraceBackRepository.TYPE_FUNCTIONNAL,
          I18n.get(IExceptionMessage.NO_TIMESHEET_CREATED),
          timer);
    }
  }

  @Transactional
  public void calculateDuration(TSTimer timer) {
    long currentDuration = timer.getDuration();
    Duration duration =
        DurationTool.computeDuration(
            timer.getTimerStartDateT(),
            Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime());
    long secondes = DurationTool.getSecondsDuration(duration) + currentDuration;
    timer.setDuration(secondes);
  }

  @Transactional
  public TimesheetLine generateTimesheetLine(TSTimer timer) throws AxelorException {

    BigDecimal durationHours = this.convertSecondDurationInHours(timer.getDuration());
    Timesheet timesheet = Beans.get(TimesheetService.class).getCurrentOrCreateTimesheet();
    TimesheetLine timesheetLine =
        Beans.get(TimesheetLineService.class)
            .createTimesheetLine(
                timer.getProject(),
                timer.getProduct(),
                timer.getEmployee(),
                timer.getStartDateTime().toLocalDate(),
                timesheet,
                durationHours,
                timer.getComments());

    Beans.get(TimesheetRepository.class).save(timesheet);
    Beans.get(TimesheetLineRepository.class).save(timesheetLine);
    timer.setTimesheetLine(timesheetLine);

    return timesheetLine;
  }

  public BigDecimal convertSecondDurationInHours(long durationInSeconds) {
    logger.debug("Duration in seconds : {}", durationInSeconds);

    BigDecimal durationHours =
        new BigDecimal(durationInSeconds).divide(new BigDecimal(3600), 4, RoundingMode.HALF_EVEN);
    logger.debug("Duration in hours : {}", durationHours);

    return durationHours;
  }

  public TSTimer getCurrentTSTimer() {
    return Beans.get(TSTimerRepository.class)
        .all()
        .filter("self.employee.user.id = ?1", AuthUtils.getUser().getId())
        .fetchOne();
  }
}
