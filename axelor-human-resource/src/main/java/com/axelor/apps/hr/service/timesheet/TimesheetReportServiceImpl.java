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
package com.axelor.apps.hr.service.timesheet;

import com.axelor.apps.base.db.DayPlanning;
import com.axelor.apps.base.db.WeeklyPlanning;
import com.axelor.apps.base.service.publicHoliday.PublicHolidayService;
import com.axelor.apps.base.service.weeklyplanning.WeeklyPlanningService;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.LeaveRequest;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.TimesheetReminder;
import com.axelor.apps.hr.db.TimesheetReminderLine;
import com.axelor.apps.hr.db.TimesheetReport;
import com.axelor.apps.hr.db.repo.ExtraHoursLineRepository;
import com.axelor.apps.hr.db.repo.ExtraHoursRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetReminderRepository;
import com.axelor.apps.hr.db.repo.TimesheetReportRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.app.AppHumanResourceService;
import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.apps.hr.service.leave.LeaveService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.apps.tool.QueryBuilder;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;

public class TimesheetReportServiceImpl implements TimesheetReportService {

  protected TimesheetReminderRepository timesheetReminderRepo;
  protected TimesheetReportRepository timesheetReportRepository;
  protected ExtraHoursLineRepository extraHoursLineRepository;
  protected TimesheetLineRepository timesheetLineRepository;

  protected MessageService messageService;
  protected TemplateMessageService templateMessageService;
  protected PublicHolidayService publicHolidayService;
  protected WeeklyPlanningService weeklyPlanningService;
  protected EmployeeService employeeService;
  protected TimesheetLineService timesheetLineService;
  protected LeaveService leaveService;

  @Inject
  public TimesheetReportServiceImpl(
      TimesheetReminderRepository timesheetReminderRepo,
      TimesheetReportRepository timesheetReportRepository,
      ExtraHoursLineRepository extraHoursLineRepository,
      TimesheetLineRepository timesheetLineRepository,
      MessageService messageService,
      TemplateMessageService templateMessageService,
      PublicHolidayService publicHolidayService,
      WeeklyPlanningService weeklyPlanningService,
      EmployeeService employeeService,
      TimesheetLineService timesheetLineService,
      LeaveService leaveService) {
    this.timesheetReminderRepo = timesheetReminderRepo;
    this.timesheetReportRepository = timesheetReportRepository;
    this.extraHoursLineRepository = extraHoursLineRepository;
    this.timesheetLineRepository = timesheetLineRepository;

    this.messageService = messageService;
    this.templateMessageService = templateMessageService;
    this.publicHolidayService = publicHolidayService;
    this.weeklyPlanningService = weeklyPlanningService;
    this.employeeService = employeeService;
    this.timesheetLineService = timesheetLineService;
    this.leaveService = leaveService;
  }

  @Override
  public Set<User> getUserToBeReminded(TimesheetReport timesheetReport) {
    Set<User> userSet = new HashSet<>();
    BigDecimal worksHour = BigDecimal.ZERO, workedHour = BigDecimal.ZERO;

    List<User> users = getUsers(timesheetReport);
    LocalDate fromDate = timesheetReport.getFromDate();
    LocalDate toDate = timesheetReport.getToDate();

    for (User user : users) {
      Employee employee = user.getEmployee();
      try {
        worksHour = workedHour = BigDecimal.ZERO;
        BigDecimal publicHolidays =
            publicHolidayService.computePublicHolidayDays(
                fromDate,
                toDate,
                employee.getWeeklyPlanning(),
                employee.getPublicHolidayEventsPlanning());
        worksHour = getTotalWeekWorksHours(user, fromDate, toDate, publicHolidays);
        workedHour = getTotalWeekWorkedHours(user, fromDate, toDate, publicHolidays);
        if (worksHour.compareTo(workedHour) != 0) {
          userSet.add(user);
        }
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }
    return userSet;
  }

  @Transactional
  public List<Message> sendReminders(TimesheetReport timesheetReport) throws AxelorException {

    Template reminderTemplate =
        Beans.get(AppHumanResourceService.class).getAppTimesheet().getTimesheetReminderTemplate();
    if (reminderTemplate == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.EMPLOYEE_TIMESHEET_REMINDER_TEMPLATE));
    }
    List<TimesheetReminder> timesheetReminders = getTimesheetReminderList(timesheetReport);
    return sendEmailMessage(timesheetReminders, reminderTemplate);
  }

  protected List<TimesheetReminder> getTimesheetReminderList(TimesheetReport timesheetReport) {
    List<TimesheetReminder> timesheetReminders = new ArrayList<>();

    List<User> users = new ArrayList<>(timesheetReport.getReminderUserSet());
    try {
      addTimesheetReminder(timesheetReport, users, timesheetReminders);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
    return timesheetReminders;
  }

  protected void addTimesheetReminder(
      TimesheetReport timesheetReport, List<User> users, List<TimesheetReminder> timesheetReminders)
      throws AxelorException {
    BigDecimal worksHour = BigDecimal.ZERO,
        workedHour = BigDecimal.ZERO,
        missingHour = BigDecimal.ZERO,
        extraHour = BigDecimal.ZERO;
    LocalDate fromDate = timesheetReport.getFromDate();
    LocalDate toDate = null;
    do {
      toDate = fromDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
      if (toDate.until(timesheetReport.getToDate()).getDays() < 0) {
        toDate = timesheetReport.getToDate();
      }

      for (User user : users) {
        Employee employee = user.getEmployee();
        missingHour = BigDecimal.ZERO;
        extraHour = BigDecimal.ZERO;

        BigDecimal publicHolidays =
            publicHolidayService.computePublicHolidayDays(
                fromDate,
                toDate,
                employee.getWeeklyPlanning(),
                employee.getPublicHolidayEventsPlanning());

        worksHour = getTotalWeekWorksHours(user, fromDate, toDate, publicHolidays);

        workedHour = getTotalWeekWorkedHours(user, fromDate, toDate, publicHolidays);
        if (worksHour.compareTo(workedHour) == 1) {
          missingHour = worksHour.subtract(workedHour);
        } else if (worksHour.compareTo(workedHour) == -1) {
          extraHour = workedHour.subtract(worksHour);
        }

        if (missingHour.compareTo(BigDecimal.ZERO) == 0
            && extraHour.compareTo(BigDecimal.ZERO) == 0) {
          continue;
        }
        Optional<TimesheetReminder> optReminder =
            timesheetReminders
                .stream()
                .filter(reminder -> reminder.getEmployee().getId().compareTo(employee.getId()) == 0)
                .findFirst();

        TimesheetReminder timesheetReminder = null;
        if (optReminder.isPresent()) {
          timesheetReminder = optReminder.get();
          timesheetReminder.addTimesheetReminderLineListItem(
              createTimesheetReminderLine(fromDate, toDate, worksHour, missingHour, extraHour));
        } else {
          List<TimesheetReminderLine> timesheetReminderLines = new ArrayList<>();
          timesheetReminder = new TimesheetReminder();
          timesheetReminder.setEmployee(employee);
          timesheetReminder.setTimesheetReminderLineList(timesheetReminderLines);
          timesheetReminder.addTimesheetReminderLineListItem(
              createTimesheetReminderLine(fromDate, toDate, worksHour, missingHour, extraHour));
          timesheetReminders.add(timesheetReminder);
        }
        timesheetReminderRepo.save(timesheetReminder);
      }
      fromDate = fromDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    } while (toDate.until(timesheetReport.getToDate()).getDays() > 0);
  }

  protected List<Message> sendEmailMessage(
      List<TimesheetReminder> timesheetReminders, Template reminderTemplate) {

    List<Message> messages = new ArrayList<>();
    for (TimesheetReminder timesheetReminder : timesheetReminders) {
      try {
        Message message =
            templateMessageService.generateMessage(timesheetReminder, reminderTemplate);
        message = messageService.sendMessage(message);
        timesheetReminder.setEmailSentDateT(LocalDateTime.now());
        messages.add(message);
      } catch (Exception e) {
        TraceBackService.trace(e);
      }
    }

    return messages;
  }

  protected TimesheetReminderLine createTimesheetReminderLine(
      LocalDate fromDate,
      LocalDate toDate,
      BigDecimal worksHour,
      BigDecimal missingHour,
      BigDecimal extraHour) {
    TimesheetReminderLine line = new TimesheetReminderLine();
    line.setFromDate(fromDate);
    line.setToDate(toDate);
    line.setRequiredHours(worksHour);
    line.setExtraHours(extraHour);
    line.setMissingHours(missingHour);
    line.setWorkHour(worksHour.subtract(missingHour).add(extraHour));
    return line;
  }

  public List<Map<String, Object>> getTimesheetReportList(String timesheetReportId) {

    List<Map<String, Object>> list = new ArrayList<>();
    WeekFields weekFields = WeekFields.of(DayOfWeek.MONDAY, 5);

    TimesheetReport timesheetReport =
        timesheetReportRepository.find(Long.parseLong(timesheetReportId.toString()));
    int numOfDays = timesheetReport.getFromDate().until(timesheetReport.getToDate()).getDays();
    List<LocalDate> daysRange =
        Stream.iterate(timesheetReport.getFromDate(), date -> date.plusDays(1))
            .limit(numOfDays + 1)
            .collect(Collectors.toList());

    List<User> users = getUsers(timesheetReport);

    for (User user : users) {
      Employee employee = user.getEmployee();
      BigDecimal dailyWorkingHours = employee.getDailyWorkHours();
      WeeklyPlanning weeklyPlanning = employee.getWeeklyPlanning();

      Integer weekNumber = 1;
      int lastDayIndex = -1;
      int daysInWeek = 0;
      try {
        for (LocalDate date : daysRange) {
          DayPlanning dayPlanning = weeklyPlanningService.findDayPlanning(weeklyPlanning, date);
          if (dayPlanning == null) {
            continue;
          }
          int dayIndex = date.get(weekFields.dayOfWeek()) - 1;
          if (lastDayIndex < dayIndex) {
            lastDayIndex = dayIndex;
            if (weeklyPlanningService.getWorkingDayValueInDays(weeklyPlanning, date) != 0) {
              daysInWeek++;
            }
          } else {
            lastDayIndex = -1;
            daysInWeek = 1;
            weekNumber++;
          }
          BigDecimal weeklyWorkHours =
              daysInWeek <= 5
                  ? employee
                      .getWeeklyWorkHours()
                      .multiply(BigDecimal.valueOf(daysInWeek / 5.00))
                      .setScale(2, RoundingMode.HALF_EVEN)
                  : employee.getWeeklyWorkHours();
          Map<String, Object> map = getTimesheetMap(user, date, dailyWorkingHours);
          map.put("weeklyWorkHours", weeklyWorkHours);
          map.put("weekNumber", weekNumber.toString());
          list.add(map);
        }
      } catch (Exception e) {
        System.out.println(e);
      }
    }
    return list;
  }

  protected Map<String, Object> getTimesheetMap(
      User user, LocalDate date, BigDecimal dailyWorkingHours) throws AxelorException {
    Employee employee = user.getEmployee();
    BigDecimal worksHour = BigDecimal.ZERO, workedHour = BigDecimal.ZERO;

    boolean isPublicHoliday =
        publicHolidayService.checkPublicHolidayDay(date, employee.getPublicHolidayEventsPlanning());
    worksHour = getTotalWorksHours(user, date, isPublicHoliday, dailyWorkingHours);

    try {
      workedHour = getTotalWorkedHours(user, date, isPublicHoliday, dailyWorkingHours);
    } catch (Exception e) {
      System.out.println(e);
    }

    Map<String, Object> map = new HashMap<String, Object>();
    map.put("userName", user.getFullName());
    map.put("date", DateTool.toDate(date));
    map.put("workedHour", workedHour);
    map.put("workingHour", worksHour);
    return map;
  }

  protected BigDecimal getTotalWorksHours(
      User user, LocalDate date, boolean isPublicHoliday, BigDecimal dailyWorkingHours)
      throws AxelorException {
    Employee employee = user.getEmployee();
    BigDecimal worksHour =
        employeeService
            .getDaysWorksInPeriod(employee, date, date)
            .multiply(employee.getDailyWorkHours())
            .setScale(2, RoundingMode.HALF_EVEN);
    if (isPublicHoliday) {
      worksHour = worksHour.add(dailyWorkingHours);
    }
    double extraHours =
        extraHoursLineRepository
            .all()
            .filter(
                "self.user = ? AND self.date = ? AND (self.extraHours.statusSelect = ? OR self.extraHours.statusSelect = ?)",
                user,
                date,
                ExtraHoursRepository.STATUS_VALIDATED,
                ExtraHoursRepository.STATUS_CONFIRMED)
            .fetchStream()
            .mapToDouble(ehl -> Double.parseDouble(ehl.getQty().toString()))
            .sum();
    worksHour = worksHour.add(new BigDecimal(extraHours));
    return worksHour.setScale(2, RoundingMode.HALF_EVEN);
  }

  protected BigDecimal getTotalWeekWorksHours(
      User user, LocalDate fromDate, LocalDate toDate, BigDecimal publicHolidays)
      throws AxelorException {
    Employee employee = user.getEmployee();
    BigDecimal worksHour =
        employeeService
            .getDaysWorksInPeriod(employee, fromDate, toDate)
            .multiply(employee.getDailyWorkHours())
            .setScale(2, RoundingMode.HALF_EVEN);
    worksHour = worksHour.add(publicHolidays.multiply(employee.getDailyWorkHours()));
    double extraHours =
        extraHoursLineRepository
            .all()
            .filter(
                "self.user = ? AND (self.date BETWEEN ? AND ?) AND (self.extraHours.statusSelect = ? OR self.extraHours.statusSelect = ?)",
                user,
                fromDate,
                toDate,
                ExtraHoursRepository.STATUS_VALIDATED,
                ExtraHoursRepository.STATUS_CONFIRMED)
            .fetchStream()
            .mapToDouble(ehl -> Double.parseDouble(ehl.getQty().toString()))
            .sum();
    worksHour = worksHour.add(new BigDecimal(extraHours));
    return worksHour.setScale(2, RoundingMode.HALF_EVEN);
  }

  protected BigDecimal getTotalWorkedHours(
      User user, LocalDate date, boolean isPublicHoliday, BigDecimal dailyWorkingHours)
      throws AxelorException {
    BigDecimal totalHours = BigDecimal.ZERO;

    List<TimesheetLine> timesheetLineList =
        timesheetLineRepository
            .all()
            .filter(
                "self.user = ? AND self.date = ? AND (self.timesheet.statusSelect = ? OR self.timesheet.statusSelect = ?)",
                user,
                date,
                TimesheetRepository.STATUS_CONFIRMED,
                TimesheetRepository.STATUS_VALIDATED)
            .fetch();

    Duration totalDuration = timesheetLineService.computeTotalDuration(timesheetLineList);
    totalHours =
        new BigDecimal(totalDuration.getSeconds())
            .divide(BigDecimal.valueOf(3600))
            .setScale(2, RoundingMode.HALF_EVEN);

    if (isPublicHoliday) {
      totalHours = totalHours.add(dailyWorkingHours);
    } else {
      totalHours = totalHours.add(getLeaveHours(user, date, dailyWorkingHours));
    }

    return totalHours.setScale(2, RoundingMode.HALF_EVEN);
  }

  protected BigDecimal getTotalWeekWorkedHours(
      User user, LocalDate fromDate, LocalDate toDate, BigDecimal publicHolidays)
      throws AxelorException {
    BigDecimal totalHours = BigDecimal.ZERO;
    Employee employee = user.getEmployee();

    List<TimesheetLine> timesheetLineList =
        timesheetLineRepository
            .all()
            .filter(
                "self.user = ? AND (self.date BETWEEN ? AND ?) AND (self.timesheet.statusSelect = ? OR self.timesheet.statusSelect = ?)",
                user,
                fromDate,
                toDate,
                TimesheetRepository.STATUS_VALIDATED,
                TimesheetRepository.STATUS_CONFIRMED)
            .fetch();

    Duration totalDuration = timesheetLineService.computeTotalDuration(timesheetLineList);
    totalHours =
        new BigDecimal(totalDuration.getSeconds())
            .divide(BigDecimal.valueOf(3600))
            .setScale(2, RoundingMode.HALF_EVEN);
    totalHours = totalHours.add(publicHolidays.multiply(employee.getDailyWorkHours()));
    totalHours =
        totalHours.add(getWeekLeaveHours(user, fromDate, toDate, employee.getDailyWorkHours()));

    return totalHours.setScale(2, RoundingMode.HALF_EVEN);
  }

  protected BigDecimal getLeaveHours(User user, LocalDate date, BigDecimal dailyWorkingHours)
      throws AxelorException {
    LeaveRequest leave = leaveService.getLeave(user, date);
    if (leave != null) {
      return leaveService.computeDuration(leave, date, date).multiply(dailyWorkingHours);
    }
    return BigDecimal.ZERO;
  }

  private BigDecimal getWeekLeaveHours(
      User user, LocalDate fromDate, LocalDate toDate, BigDecimal dailyWorkingHours)
      throws AxelorException {
    BigDecimal leaveHours = BigDecimal.ZERO;
    do {
      LeaveRequest leave = leaveService.getLeave(user, fromDate);
      if (leave != null) {

        boolean isPublicHoliday =
            publicHolidayService.checkPublicHolidayDay(
                fromDate, user.getEmployee().getPublicHolidayEventsPlanning());
        if (!isPublicHoliday) {
          leaveHours =
              leaveHours.add(
                  leaveService
                      .computeDuration(leave, fromDate, fromDate)
                      .multiply(dailyWorkingHours));
        }
      }
      fromDate = fromDate.plusDays(1);
    } while (fromDate.until(toDate).getDays() > -1);
    return leaveHours;
  }

  protected List<User> getUsers(TimesheetReport timesheetReport) {
    QueryBuilder<User> userQuery = QueryBuilder.of(User.class);
    userQuery.add(
        "self.employee IS NOT NULL AND self.employee.weeklyPlanning IS NOT NULL AND self.employee.publicHolidayEventsPlanning IS NOT NULL AND self.employee.mainEmploymentContract IS NOT NULL");
    if (!CollectionUtils.isEmpty(timesheetReport.getUserSet())) {
      userQuery.add("self IN :users");
      userQuery.bind("users", timesheetReport.getUserSet());
    }
    return userQuery.build().fetch();
  }
}
