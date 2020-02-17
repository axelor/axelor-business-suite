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
package com.axelor.apps.businessproject.service.report;

import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.Query;

public class AvailabilitiesReportServiceImpl implements AvailabilitiesReportService {

  static final int GRANULARITY_SELECT_FORTNIGHT = 1;
  static final int GRANULARITY_SELECT_MONTH = 2;
  static final int GRANULARITY_SELECT_YEAR = 3;

  static final int PLANNING_LINE_TYPE_SELECT_STAFFING = 1;
  static final int PLANNING_LINE_TYPE_SELECT_PRE_STAFFING = 2;
  static final int PLANNING_LINE_TYPE_SELECT_BOTH = 3;

  @Inject protected EmployeeService employeeService;
  @Inject protected EmployeeRepository employeeRepository;

  public static AvailabilitiesReportService getInstance() {
    return Beans.get(AvailabilitiesReportService.class);
  }

  @Override
  public List<Map<String, Object>> getAvailabilitiesData(
      String startDateStr, String endDateStr, Integer granularitySelect, Long companyId)
      throws AxelorException {

    LocalDate startDate = LocalDate.parse(startDateStr, DateTimeFormatter.ISO_DATE);
    LocalDate endDate = LocalDate.parse(endDateStr, DateTimeFormatter.ISO_DATE);

    Map<LocalDate, LocalDate> periodMap = getPeriods(granularitySelect, startDate, endDate);
    List<Map<String, Object>> dataMapList = getEmployeeResidualTime(periodMap, companyId);

    return dataMapList;
  }

  protected Map<LocalDate, LocalDate> getPeriods(
      int granularitySelect, LocalDate startDate, LocalDate endDate) {

    Map<LocalDate, LocalDate> periodMap = new HashMap<>();
    Period period = null;

    switch (granularitySelect) {
      case GRANULARITY_SELECT_FORTNIGHT:
      case GRANULARITY_SELECT_MONTH:
        period = Period.ofMonths(1);
        break;
      case GRANULARITY_SELECT_YEAR:
        period = Period.ofYears(1);
        break;
      default:
        period = Period.ofMonths(1);
    }

    while (startDate.isBefore(endDate)) {
      LocalDate startDateOfMonth = startDate.withDayOfMonth(1);
      LocalDate endDateOfMonth = startDate.with(TemporalAdjusters.lastDayOfMonth());

      switch (granularitySelect) {
        case GRANULARITY_SELECT_FORTNIGHT:
          LocalDate middleDateOfMonth = startDate.withDayOfMonth(15);
          if (startDate.getDayOfMonth() <= 15) {
            periodMap.put(startDateOfMonth, middleDateOfMonth);
          }
          periodMap.put(middleDateOfMonth.plusDays(1), endDateOfMonth);
          break;
        case GRANULARITY_SELECT_MONTH:
          periodMap.put(startDateOfMonth, endDateOfMonth);
          break;
        case GRANULARITY_SELECT_YEAR:
          periodMap.put(startDateOfMonth, endDateOfMonth.with(TemporalAdjusters.lastDayOfYear()));
          break;
        default:
      }

      startDate = startDateOfMonth.plus(period);
    }
    return periodMap;
  }

  protected List<Map<String, Object>> getEmployeeResidualTime(
      Map<LocalDate, LocalDate> periodMap, Long companyId) throws AxelorException {

    List<Map<String, Object>> dataMapList = new ArrayList<>();
    List<Employee> employeeList = getEmployeeList(companyId);

    for (Employee employee : employeeList) {
      List<Map<String, Object>> perEmployeeResidualTimeMap =
          getPerEmployeeResidualTime(employee, periodMap);
      dataMapList.addAll(perEmployeeResidualTimeMap);
    }

    return dataMapList;
  }

  protected List<Map<String, Object>> getPerEmployeeResidualTime(
      Employee employee, Map<LocalDate, LocalDate> periodMap) throws AxelorException {
    List<Map<String, Object>> dataMapList = new ArrayList<>();

    if (employee.getUser() == null) {
      return dataMapList;
    }

    Query planningLinesQuery =
        JPA.em()
            .createQuery(
                "SELECT SUM(self.plannedHours) FROM ProjectPlanningTime self WHERE self.user = :user AND self.date BETWEEN :fromDate AND :toDate");

    planningLinesQuery.setParameter("user", employee.getUser());

    for (Map.Entry<LocalDate, LocalDate> period : periodMap.entrySet()) {
      Map<String, Object> dataMap = new HashMap<>();

      planningLinesQuery.setParameter("fromDate", period.getKey());
      planningLinesQuery.setParameter("toDate", period.getValue());

      BigDecimal totalRealHrs = (BigDecimal) planningLinesQuery.getSingleResult();
      BigDecimal daysWorkedInPeriod =
          employeeService.getDaysWorkedInPeriod(employee, period.getKey(), period.getValue());
      BigDecimal totalPlannedTime =
          totalRealHrs == null
              ? BigDecimal.ZERO
              : totalRealHrs.divide(employee.getDailyWorkHours(), RoundingMode.HALF_EVEN);
      BigDecimal value =
          daysWorkedInPeriod
              .multiply(employee.getWeeklyPlanning().getLeaveCoef())
              .subtract(totalPlannedTime)
              .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_EVEN);

      dataMap.put(
          "startDate",
          Date.from(
              ZonedDateTime.of(period.getKey(), LocalTime.MIDNIGHT, ZoneId.systemDefault())
                  .toInstant()));
      dataMap.put(
          "endDate",
          Date.from(
              ZonedDateTime.of(period.getValue(), LocalTime.MIDNIGHT, ZoneId.systemDefault())
                  .toInstant()));
      dataMap.put("value", value);
      dataMap.put("employeeName", employee.getContactPartner().getSimpleFullName());
      dataMap.put(
          "companyDepartment",
          employee.getMainEmploymentContract().getCompanyDepartment().getName());
      dataMapList.add(dataMap);
    }

    return dataMapList;
  }

  public List<Employee> getEmployeeList(Long companyId) {
    return employeeRepository
        .all()
        .filter(
            "self.mainEmploymentContract != null AND self.mainEmploymentContract.payCompany.id = :companyId AND self.mainEmploymentContract.companyDepartment.name != null AND self.weeklyPlanning != null AND self.publicHolidayEventsPlanning != null")
        .bind("companyId", companyId)
        .fetch();
  }
}
