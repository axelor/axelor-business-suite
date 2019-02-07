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
package com.axelor.apps.hr.service.employee;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.EventsPlanning;
import com.axelor.apps.base.db.WeeklyPlanning;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.user.UserServiceImpl;
import com.axelor.apps.base.service.weeklyplanning.WeeklyPlanningService;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.LeaveRequest;
import com.axelor.apps.hr.db.repo.LeaveRequestRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.leave.LeaveService;
import com.axelor.apps.hr.service.publicHoliday.PublicHolidayHrService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class EmployeeServiceImpl extends UserServiceImpl implements EmployeeService {

  @Inject protected WeeklyPlanningService weeklyPlanningService;

  public int getLengthOfService(Employee employee, LocalDate refDate) throws AxelorException {

    try {
      Period period =
          Period.between(
              employee.getSeniorityDate(),
              refDate == null ? Beans.get(AppBaseService.class).getTodayDate() : refDate);
      return period.getYears();
    } catch (IllegalArgumentException e) {
      throw new AxelorException(
          e.getCause(),
          employee,
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.EMPLOYEE_NO_SENIORITY_DATE),
          employee.getName());
    }
  }

  public int getAge(Employee employee, LocalDate refDate) throws AxelorException {

    try {
      Period period =
          Period.between(
              employee.getBirthDate(),
              refDate == null ? Beans.get(AppBaseService.class).getTodayDate() : refDate);
      return period.getYears();
    } catch (IllegalArgumentException e) {
      throw new AxelorException(
          e.getCause(),
          employee,
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.EMPLOYEE_NO_BIRTH_DATE),
          employee.getName());
    }
  }

  @Override
  public BigDecimal getDaysWorksInPeriod(Employee employee, LocalDate fromDate, LocalDate toDate)
      throws AxelorException {
    Company company = employee.getMainEmploymentContract().getPayCompany();
    BigDecimal duration = BigDecimal.ZERO;

    WeeklyPlanning weeklyPlanning = employee.getWeeklyPlanning();
    if (weeklyPlanning == null) {
      HRConfig conf = company.getHrConfig();
      if (conf != null) {
        weeklyPlanning = conf.getWeeklyPlanning();
      }
    }

    if (weeklyPlanning == null) {
      throw new AxelorException(
          employee,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.EMPLOYEE_PLANNING),
          employee.getName());
    }

    EventsPlanning publicHolidayPlanning = employee.getPublicHolidayEventsPlanning();
    if (publicHolidayPlanning == null) {
      HRConfig conf = company.getHrConfig();
      if (conf != null) {
        publicHolidayPlanning = conf.getPublicHolidayEventsPlanning();
      }
    }

    if (publicHolidayPlanning == null) {
      throw new AxelorException(
          employee,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.EMPLOYEE_PUBLIC_HOLIDAY),
          employee.getName());
    }

    LocalDate itDate = fromDate;

    while (!itDate.isAfter(toDate)) {
      duration =
          duration.add(
              new BigDecimal(weeklyPlanningService.workingDayValue(weeklyPlanning, itDate)));
      itDate = itDate.plusDays(1);
    }

    if (publicHolidayPlanning != null) {
      duration =
          duration.subtract(
              Beans.get(PublicHolidayHrService.class)
                  .computePublicHolidayDays(
                      fromDate, toDate, weeklyPlanning, publicHolidayPlanning));
    }

    return duration;
  }

  @Override
  public BigDecimal getDaysWorkedInPeriod(Employee employee, LocalDate fromDate, LocalDate toDate)
      throws AxelorException {
    BigDecimal daysWorks = getDaysWorksInPeriod(employee, fromDate, toDate);

    BigDecimal daysLeave = BigDecimal.ZERO;
    List<LeaveRequest> leaveRequestList =
        Beans.get(LeaveRequestRepository.class)
            .all()
            .filter(
                "self.user = ?1 AND self.duration >= 1 AND self.statusSelect = ?2 AND (self.fromDate BETWEEN ?3 AND ?4 OR self.toDate BETWEEN ?3 AND ?4)",
                employee.getUser(),
                LeaveRequestRepository.STATUS_VALIDATED,
                fromDate,
                toDate)
            .fetch();

    for (LeaveRequest leaveRequest : leaveRequestList) {
      daysLeave =
          daysLeave.add(
              Beans.get(LeaveService.class).computeDuration(leaveRequest, fromDate, toDate));
    }

    return daysWorks.subtract(daysLeave);
  }

  public Map<String, String> getSocialNetworkUrl(String name, String firstName) {

    Map<String, String> urlMap = new HashMap<String, String>();
    name =
        firstName != null && name != null
            ? firstName + "+" + name
            : name == null ? firstName : name;
    name = name == null ? "" : name;
    urlMap.put(
        "google",
        "<a class='fa fa-google-plus' href='https://www.google.com/?gws_rd=cr#q="
            + name
            + "' target='_blank' />");
    urlMap.put(
        "facebook",
        "<a class='fa fa-facebook' href='https://www.facebook.com/search/more/?q="
            + name
            + "&init=public"
            + "' target='_blank'/>");
    urlMap.put(
        "twitter",
        "<a class='fa fa-twitter' href='https://twitter.com/search?q="
            + name
            + "' target='_blank' />");
    urlMap.put(
        "linkedin",
        "<a class='fa fa-linkedin' href='http://www.linkedin.com/pub/dir/"
            + name.replace("+", "/")
            + "' target='_blank' />");
    urlMap.put(
        "youtube",
        "<a class='fa fa-youtube' href='https://www.youtube.com/results?search_query="
            + name
            + "' target='_blank' />");

    return urlMap;
  }
}
