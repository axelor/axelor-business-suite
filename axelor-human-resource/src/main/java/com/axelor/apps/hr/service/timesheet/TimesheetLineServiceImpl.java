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
package com.axelor.apps.hr.service.timesheet;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.TimesheetHRRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.project.db.Project;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.team.db.TeamTask;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimesheetLineServiceImpl implements TimesheetLineService {

  protected TimesheetService timesheetService;
  protected TimesheetHRRepository timesheetHRRepository;
  protected TimesheetRepository timesheetRepository;
  protected EmployeeRepository employeeRepository;

  @Inject
  public TimesheetLineServiceImpl(
      TimesheetService timesheetService,
      TimesheetHRRepository timesheetHRRepository,
      TimesheetRepository timesheetRepository,
      EmployeeRepository employeeRepository) {
    this.timesheetService = timesheetService;
    this.timesheetHRRepository = timesheetHRRepository;
    this.timesheetRepository = timesheetRepository;
    this.employeeRepository = employeeRepository;
  }

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public BigDecimal computeHoursDuration(Timesheet timesheet, BigDecimal duration, boolean toHours)
      throws AxelorException {
    if (duration == null) {
      return null;
    }
    AppBaseService appBaseService = Beans.get(AppBaseService.class);
    BigDecimal dailyWorkHrs;
    String timePref;

    log.debug(
        "Get user duration for duration: {}, timesheet: {}",
        duration,
        timesheet == null ? "null" : timesheet.getFullName());

    if (timesheet != null) {
      User user = timesheet.getUser();

      timePref = timesheet.getTimeLoggingPreferenceSelect();

      if (user.getEmployee() != null) {
        Employee employee = employeeRepository.find(user.getEmployee().getId());

        log.debug("Employee: {}", employee);

        dailyWorkHrs = employee.getDailyWorkHours();
        if (timePref == null) {
          timePref = employee.getTimeLoggingPreferenceSelect();
        }
      } else {
        dailyWorkHrs = appBaseService.getAppBase().getDailyWorkHours();
      }
    } else {
      timePref = appBaseService.getAppBase().getTimeLoggingPreferenceSelect();
      dailyWorkHrs = appBaseService.getAppBase().getDailyWorkHours();
    }

    return computeHoursDuration(timePref, duration, dailyWorkHrs, toHours);
  }

  @Override
  public BigDecimal computeHoursDuration(
      String timePref, BigDecimal duration, BigDecimal dailyWorkHrs, boolean toHours)
      throws AxelorException {
    log.debug("Timesheet time pref: {}, Daily Working hours: {}", timePref, dailyWorkHrs);
    if (timePref == null) {
      return duration;
    }
    if (toHours) {
      duration = computeDurationToHours(timePref, duration, dailyWorkHrs);
    } else {
      duration = computeDurationFromHours(timePref, duration, dailyWorkHrs);
    }
    log.debug("Calculated duration: {}", duration);
    return duration;
  }

  protected BigDecimal computeDurationToHours(
      String timePref, BigDecimal duration, BigDecimal dailyWorkHrs) throws AxelorException {
    switch (timePref) {
      case EmployeeRepository.TIME_PREFERENCE_DAYS:
        if (dailyWorkHrs.compareTo(BigDecimal.ZERO) == 0) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.TIMESHEET_DAILY_WORK_HOURS));
        }
        return duration.multiply(dailyWorkHrs);
      case EmployeeRepository.TIME_PREFERENCE_MINUTES:
        return duration.divide(new BigDecimal(60), 2, RoundingMode.HALF_UP);
      default:
        return duration;
    }
  }

  protected BigDecimal computeDurationFromHours(
      String timePref, BigDecimal duration, BigDecimal dailyWorkHrs) throws AxelorException {
    switch (timePref) {
      case EmployeeRepository.TIME_PREFERENCE_DAYS:
        if (dailyWorkHrs.compareTo(BigDecimal.ZERO) == 0) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.TIMESHEET_DAILY_WORK_HOURS));
        }
        return duration.divide(dailyWorkHrs, 2, RoundingMode.HALF_UP);
      case EmployeeRepository.TIME_PREFERENCE_MINUTES:
        return duration.multiply(new BigDecimal(60));
      default:
        return duration;
    }
  }

  @Override
  public TimesheetLine createTimesheetLine(
      Project project,
      TeamTask task,
      Product product,
      User user,
      LocalDate date,
      Timesheet timesheet,
      BigDecimal hours,
      String comments) {

    TimesheetLine timesheetLine = new TimesheetLine();

    timesheetLine.setDate(date);
    timesheetLine.setComments(comments);
    timesheetLine.setProduct(product);
    timesheetLine.setProject(project);
    timesheetLine.setTeamTask(task);
    timesheetLine.setUser(user);
    timesheetLine.setHoursDuration(hours);
    try {
      timesheetLine.setDuration(computeHoursDuration(timesheet, hours, false));
    } catch (AxelorException e) {
      log.error(e.getLocalizedMessage());
      TraceBackService.trace(e);
    }
    timesheet.addTimesheetLineListItem(timesheetLine);

    return timesheetLine;
  }

  @Override
  public TimesheetLine createTimesheetLine(
      User user, LocalDate date, Timesheet timesheet, BigDecimal hours, String comments) {
    return createTimesheetLine(null, null, null, user, date, timesheet, hours, comments);
  }

  @Override
  public TimesheetLine updateTimesheetLine(
      TimesheetLine timesheetLine,
      Project project,
      TeamTask task,
      Product product,
      User user,
      LocalDate date,
      Timesheet timesheet,
      BigDecimal hours,
      String comments) {

    timesheetLine.setDate(date);
    timesheetLine.setComments(comments);
    timesheetLine.setProduct(product);
    timesheetLine.setProject(project);
    timesheetLine.setTeamTask(task);
    timesheetLine.setUser(user);
    timesheetLine.setHoursDuration(hours);
    try {
      timesheetLine.setDuration(computeHoursDuration(timesheet, hours, false));
    } catch (AxelorException e) {
      log.error(e.getLocalizedMessage());
      TraceBackService.trace(e);
    }
    timesheet.addTimesheetLineListItem(timesheetLine);

    return timesheetLine;
  }

  @Override
  public Duration computeTotalDuration(List<TimesheetLine> timesheetLineList) {
    if (timesheetLineList == null || timesheetLineList.isEmpty()) {
      return Duration.ZERO;
    }
    long totalSecDuration = 0L;
    for (TimesheetLine timesheetLine : timesheetLineList) {
      // if null, that means the timesheet line is just created so the parent is not canceled.
      if (timesheetLine.getTimesheet() == null
          || timesheetLine.getTimesheet().getStatusSelect()
              != TimesheetRepository.STATUS_CANCELED) {
        totalSecDuration +=
            timesheetLine.getHoursDuration().multiply(new BigDecimal("3600")).longValue();
      }
    }
    return Duration.ofSeconds(totalSecDuration);
  }

  @Override
  public Map<Project, BigDecimal> getProjectTimeSpentMap(List<TimesheetLine> timesheetLineList) {
    Map<Project, BigDecimal> projectTimeSpentMap = new HashMap<>();

    if (timesheetLineList != null) {

      for (TimesheetLine timesheetLine : timesheetLineList) {
        Project project = timesheetLine.getProject();
        BigDecimal hoursDuration = timesheetLine.getHoursDuration();

        if (project != null) {
          if (projectTimeSpentMap.containsKey(project)) {
            hoursDuration = hoursDuration.add(projectTimeSpentMap.get(project));
          }
          projectTimeSpentMap.put(project, hoursDuration);
        }
      }
    }

    return projectTimeSpentMap;
  }

  @Transactional
  public TimesheetLine setTimesheet(TimesheetLine timesheetLine) {
    Timesheet timesheet =
        timesheetRepository
            .all()
            .filter(
                "self.user = ?1 AND self.company = ?2 AND (self.statusSelect = 1 OR self.statusSelect = 2) AND ((?3 BETWEEN self.fromDate AND self.toDate) OR (self.toDate = null)) ORDER BY self.id ASC",
                timesheetLine.getUser(),
                timesheetLine.getProject().getCompany(),
                timesheetLine.getDate())
            .fetchOne();
    if (timesheet == null) {
      Timesheet lastTimesheet =
          timesheetRepository
              .all()
              .filter(
                  "self.user = ?1 AND self.statusSelect != ?2 ORDER BY self.toDate DESC",
                  timesheetLine.getUser(),
                  TimesheetRepository.STATUS_CANCELED)
              .fetchOne();
      timesheet =
          timesheetService.createTimesheet(
              timesheetLine.getUser(),
              lastTimesheet != null && lastTimesheet.getToDate() != null
                  ? lastTimesheet.getToDate().plusDays(1)
                  : timesheetLine.getDate(),
              null);
      timesheet = timesheetHRRepository.save(timesheet);
    }
    timesheetLine.setTimesheet(timesheet);
    return timesheetLine;
  }
}
