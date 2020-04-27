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
package com.axelor.apps.hr.service.leave;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.DayPlanning;
import com.axelor.apps.base.db.EventsPlanning;
import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.WeeklyPlanning;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.ical.ICalendarService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.weeklyplanning.WeeklyPlanningService;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.LeaveLine;
import com.axelor.apps.hr.db.LeaveReason;
import com.axelor.apps.hr.db.LeaveRequest;
import com.axelor.apps.hr.db.repo.LeaveLineRepository;
import com.axelor.apps.hr.db.repo.LeaveReasonRepository;
import com.axelor.apps.hr.db.repo.LeaveRequestRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.hr.service.publicHoliday.PublicHolidayHrService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.List;
import javax.mail.MessagingException;

public class LeaveServiceImpl implements LeaveService {

  protected LeaveLineRepository leaveLineRepo;
  protected WeeklyPlanningService weeklyPlanningService;
  protected PublicHolidayHrService publicHolidayHrService;
  protected LeaveRequestRepository leaveRequestRepo;
  protected AppBaseService appBaseService;
  protected HRConfigService hrConfigService;
  protected TemplateMessageService templateMessageService;
  protected ICalendarEventRepository icalEventRepo;
  protected ICalendarService icalendarService;

  @Inject
  public LeaveServiceImpl(
      LeaveLineRepository leaveLineRepo,
      WeeklyPlanningService weeklyPlanningService,
      PublicHolidayHrService publicHolidayHrService,
      LeaveRequestRepository leaveRequestRepo,
      AppBaseService appBaseService,
      HRConfigService hrConfigService,
      TemplateMessageService templateMessageService,
      ICalendarEventRepository icalEventRepo,
      ICalendarService icalendarService) {

    this.leaveLineRepo = leaveLineRepo;
    this.weeklyPlanningService = weeklyPlanningService;
    this.publicHolidayHrService = publicHolidayHrService;
    this.leaveRequestRepo = leaveRequestRepo;
    this.appBaseService = appBaseService;
    this.hrConfigService = hrConfigService;
    this.templateMessageService = templateMessageService;
    this.icalEventRepo = icalEventRepo;
    this.icalendarService = icalendarService;
  }

  /**
   * Compute the duration of a given leave request but restricted inside a period.
   *
   * @param leave
   * @param fromDate the first date of the period
   * @param toDate the last date of the period
   * @return the computed duration in days
   * @throws AxelorException
   */
  public BigDecimal computeDuration(LeaveRequest leave, LocalDate fromDate, LocalDate toDate)
      throws AxelorException {
    LocalDateTime leaveFromDate = leave.getFromDateT();
    LocalDateTime leaveToDate = leave.getToDateT();

    int startOn = leave.getStartOnSelect();
    int endOn = leave.getEndOnSelect();

    LocalDateTime from = leaveFromDate;
    LocalDateTime to = leaveToDate;
    // if the leave starts before the beginning of the period,
    // we use the beginning date of the period.
    if (leaveFromDate.toLocalDate().isBefore(fromDate)) {
      from = fromDate.atStartOfDay();
      startOn = LeaveRequestRepository.SELECT_MORNING;
    }
    // if the leave ends before the end of the period,
    // we use the last date of the period.
    if (leaveToDate.toLocalDate().isAfter(toDate)) {
      to = toDate.atStartOfDay();
      endOn = LeaveRequestRepository.SELECT_AFTERNOON;
    }

    return computeDuration(leave, from, to, startOn, endOn);
  }

  /**
   * Compute the duration of a given leave request.
   *
   * @param leave
   * @return the computed duration in days
   * @throws AxelorException
   */
  public BigDecimal computeDuration(LeaveRequest leave) throws AxelorException {
    return computeDuration(
        leave,
        leave.getFromDateT(),
        leave.getToDateT(),
        leave.getStartOnSelect(),
        leave.getEndOnSelect());
  }

  /**
   * Compute the duration of a given leave request. The duration can be in hours or in days,
   * depending of the leave reason of the leave.
   *
   * @param leave
   * @param from, the beginning of the leave request inside the period
   * @param to, the end of the leave request inside the period
   * @param startOn If the period starts in the morning or in the afternoon
   * @param endOn If the period ends in the morning or in the afternoon
   * @return the computed duration in days
   * @throws AxelorException
   */
  public BigDecimal computeDuration(
      LeaveRequest leave, LocalDateTime from, LocalDateTime to, int startOn, int endOn)
      throws AxelorException {

    BigDecimal duration = BigDecimal.ZERO;

    if (from != null
        && to != null
        && leave.getLeaveLine() != null
        && leave.getLeaveLine().getLeaveReason() != null) {
      Employee employee = leave.getUser().getEmployee();
      if (employee == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
            leave.getUser().getName());
      }

      switch (leave.getLeaveLine().getLeaveReason().getUnitSelect()) {
        case LeaveReasonRepository.UNIT_SELECT_DAYS:
          LocalDate fromDate = from.toLocalDate();
          LocalDate toDate = to.toLocalDate();
          duration = computeDurationInDays(leave, employee, fromDate, toDate, startOn, endOn);
          break;

        case LeaveReasonRepository.UNIT_SELECT_HOURS:
          duration = computeDurationInHours(leave, employee, from, to);
          break;

        default:
          throw new AxelorException(
              leave.getLeaveLine().getLeaveReason(),
              TraceBackRepository.CATEGORY_NO_VALUE,
              I18n.get(IExceptionMessage.LEAVE_REASON_NO_UNIT),
              leave.getLeaveLine().getLeaveReason().getLeaveReason());
      }
    }

    return duration.signum() != -1 ? duration : BigDecimal.ZERO;
  }

  /**
   * Computes the duration in days of a leave, according to the input planning.
   *
   * @param leave
   * @param employee
   * @param fromDate
   * @param toDate
   * @param startOn
   * @param endOn
   * @return
   * @throws AxelorException
   */
  protected BigDecimal computeDurationInDays(
      LeaveRequest leave,
      Employee employee,
      LocalDate fromDate,
      LocalDate toDate,
      int startOn,
      int endOn)
      throws AxelorException {

    BigDecimal duration = BigDecimal.ZERO;
    WeeklyPlanning weeklyPlanning = getWeeklyPlanning(leave, employee);
    EventsPlanning holidayPlanning = getPublicHolidayEventsPlanning(leave, employee);

    // If the leave request is only for 1 day
    if (fromDate.isEqual(toDate)) {
      if (startOn == endOn) {
        if (startOn == LeaveRequestRepository.SELECT_MORNING) {
          duration =
              duration.add(
                  BigDecimal.valueOf(
                      weeklyPlanningService.getWorkingDayValueInDaysWithSelect(
                          weeklyPlanning, fromDate, true, false)));
        } else {
          duration =
              duration.add(
                  BigDecimal.valueOf(
                      weeklyPlanningService.getWorkingDayValueInDaysWithSelect(
                          weeklyPlanning, fromDate, false, true)));
        }
      } else {
        duration =
            duration.add(
                BigDecimal.valueOf(
                    weeklyPlanningService.getWorkingDayValueInDaysWithSelect(
                        weeklyPlanning, fromDate, true, true)));
      }

      // Else if it's on several days
    } else {
      duration =
          duration.add(
              BigDecimal.valueOf(computeStartDateWithSelect(fromDate, startOn, weeklyPlanning)));

      LocalDate itDate = fromDate.plusDays(1);
      while (!itDate.isEqual(toDate) && !itDate.isAfter(toDate)) {
        duration =
            duration.add(
                BigDecimal.valueOf(
                    weeklyPlanningService.getWorkingDayValueInDays(weeklyPlanning, itDate)));
        itDate = itDate.plusDays(1);
      }

      duration =
          duration.add(BigDecimal.valueOf(computeEndDateWithSelect(toDate, endOn, weeklyPlanning)));
    }

    if (holidayPlanning != null) {
      duration =
          duration.subtract(
              publicHolidayHrService.computePublicHolidayDays(
                  fromDate, toDate, weeklyPlanning, holidayPlanning));
    }

    return duration;
  }

  /**
   * Computes the duration in hours of a leave, according to the weekly and the holiday plannings.
   *
   * @param weeklyPlanning
   * @param holidayPlanning
   * @param fromDateT
   * @param toDateT
   * @return
   * @throws AxelorException
   */
  protected BigDecimal computeDurationInHours(
      LeaveRequest leave, Employee employee, LocalDateTime fromDateT, LocalDateTime toDateT)
      throws AxelorException {

    BigDecimal duration = BigDecimal.ZERO;
    WeeklyPlanning weeklyPlanning = getWeeklyPlanning(leave, employee);
    EventsPlanning holidayPlanning = getPublicHolidayEventsPlanning(leave, employee);
    LocalDate fromDate = fromDateT.toLocalDate();
    LocalDate toDate = toDateT.toLocalDate();

    if (toDate.equals(fromDate)
        && !publicHolidayHrService.checkPublicHolidayDay(fromDate, holidayPlanning)) {
      duration =
          duration.add(
              weeklyPlanningService.getWorkingDayValueInHours(
                  weeklyPlanning, fromDate, fromDateT.toLocalTime(), toDateT.toLocalTime()));

    } else {
      // First day of leave
      if (!publicHolidayHrService.checkPublicHolidayDay(fromDate, holidayPlanning)) {
        duration =
            duration.add(
                weeklyPlanningService.getWorkingDayValueInHours(
                    weeklyPlanning, fromDate, fromDateT.toLocalTime(), null));
      }
      fromDate = fromDate.plusDays(1);

      // Last day of leave
      if (!publicHolidayHrService.checkPublicHolidayDay(toDate, holidayPlanning)) {
        duration =
            duration.add(
                weeklyPlanningService.getWorkingDayValueInHours(
                    weeklyPlanning, toDate, null, toDateT.toLocalTime()));
      }

      // Daily leave duration of the other days between from and to date
      for (LocalDate date = fromDate; date.isBefore(toDate); date = date.plusDays(1)) {
        if (!publicHolidayHrService.checkPublicHolidayDay(date, holidayPlanning)) {
          duration =
              duration.add(
                  weeklyPlanningService.getWorkingDayValueInHours(
                      weeklyPlanning, date, null, null));
        }
      }
    }

    return duration;
  }

  protected WeeklyPlanning getWeeklyPlanning(LeaveRequest leave, Employee employee)
      throws AxelorException {
    WeeklyPlanning weeklyPlanning = employee.getWeeklyPlanning();
    if (weeklyPlanning == null) {
      Company comp = leave.getCompany();
      if (comp != null) {
        HRConfig conf = comp.getHrConfig();
        if (conf != null) {
          weeklyPlanning = conf.getWeeklyPlanning();
        }
      }
    }
    if (weeklyPlanning == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.EMPLOYEE_PLANNING),
          employee.getName());
    }
    return weeklyPlanning;
  }

  protected EventsPlanning getPublicHolidayEventsPlanning(LeaveRequest leave, Employee employee) {
    EventsPlanning publicHolidayPlanning = employee.getPublicHolidayEventsPlanning();
    if (publicHolidayPlanning == null
        && leave.getCompany() != null
        && leave.getCompany().getHrConfig() != null) {
      publicHolidayPlanning = leave.getCompany().getHrConfig().getPublicHolidayEventsPlanning();
    }
    return publicHolidayPlanning;
  }

  @Transactional(rollbackOn = {Exception.class})
  public void manageSentLeaves(LeaveRequest leave) throws AxelorException {
    Employee employee = leave.getUser().getEmployee();
    if (employee == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          leave.getUser().getName());
    }
    LeaveLine leaveLine =
        leaveLineRepo
            .all()
            .filter(
                "self.employee = ?1 AND self.leaveReason = ?2",
                employee,
                leave.getLeaveLine().getLeaveReason())
            .fetchOne();
    if (leaveLine == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_LINE),
          employee.getName(),
          leave.getLeaveLine().getLeaveReason().getLeaveReason());
    }
    if (leave.getInjectConsumeSelect() == LeaveRequestRepository.SELECT_CONSUME) {
      leaveLine.setDaysToValidate(leaveLine.getDaysToValidate().subtract(leave.getDuration()));
    } else {
      leaveLine.setDaysToValidate(leaveLine.getDaysToValidate().add(leave.getDuration()));
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public void manageValidateLeaves(LeaveRequest leave) throws AxelorException {
    Employee employee = leave.getUser().getEmployee();
    if (employee == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          leave.getUser().getName());
    }
    LeaveLine leaveLine =
        leaveLineRepo
            .all()
            .filter(
                "self.employee = ?1 AND self.leaveReason = ?2",
                employee,
                leave.getLeaveLine().getLeaveReason())
            .fetchOne();
    if (leaveLine == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_LINE),
          employee.getName(),
          leave.getLeaveLine().getLeaveReason().getLeaveReason());
    }
    if (leave.getInjectConsumeSelect() == LeaveRequestRepository.SELECT_CONSUME) {
      leaveLine.setQuantity(leaveLine.getQuantity().subtract(leave.getDuration()));
      if (leaveLine.getQuantity().signum() == -1 && !employee.getNegativeValueLeave()) {
        throw new AxelorException(
            leave,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.LEAVE_ALLOW_NEGATIVE_VALUE_EMPLOYEE),
            employee.getName());
      }
      if (leaveLine.getQuantity().signum() == -1
          && !leave.getLeaveLine().getLeaveReason().getAllowNegativeValue()) {
        throw new AxelorException(
            leave,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.LEAVE_ALLOW_NEGATIVE_VALUE_REASON),
            leave.getLeaveLine().getLeaveReason().getLeaveReason());
      }
      leaveLine.setDaysToValidate(leaveLine.getDaysToValidate().add(leave.getDuration()));
      leaveLine.setDaysValidated(leaveLine.getDaysValidated().add(leave.getDuration()));
    } else {
      leaveLine.setQuantity(leaveLine.getQuantity().add(leave.getDuration()));
      leaveLine.setDaysToValidate(leaveLine.getDaysToValidate().subtract(leave.getDuration()));
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public void manageRefuseLeaves(LeaveRequest leave) throws AxelorException {
    Employee employee = leave.getUser().getEmployee();
    if (employee == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          leave.getUser().getName());
    }
    LeaveLine leaveLine =
        leaveLineRepo
            .all()
            .filter(
                "self.employee = ?1 AND self.leaveReason = ?2",
                employee,
                leave.getLeaveLine().getLeaveReason())
            .fetchOne();
    if (leaveLine == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_LINE),
          employee.getName(),
          leave.getLeaveLine().getLeaveReason().getLeaveReason());
    }
    if (leave.getInjectConsumeSelect() == LeaveRequestRepository.SELECT_CONSUME) {
      leaveLine.setDaysToValidate(leaveLine.getDaysToValidate().add(leave.getDuration()));
    } else {
      leaveLine.setDaysToValidate(leaveLine.getDaysToValidate().subtract(leave.getDuration()));
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public void manageCancelLeaves(LeaveRequest leave) throws AxelorException {
    Employee employee = leave.getUser().getEmployee();
    if (employee == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          leave.getUser().getName());
    }
    LeaveLine leaveLine =
        leaveLineRepo
            .all()
            .filter(
                "self.employee = ?1 AND self.leaveReason = ?2",
                employee,
                leave.getLeaveLine().getLeaveReason())
            .fetchOne();
    if (leaveLine == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_LINE),
          employee.getName(),
          leave.getLeaveLine().getLeaveReason().getLeaveReason());
    }
    if (leave.getStatusSelect() == LeaveRequestRepository.STATUS_VALIDATED) {
      if (leave.getInjectConsumeSelect() == LeaveRequestRepository.SELECT_CONSUME) {
        leaveLine.setQuantity(leaveLine.getQuantity().add(leave.getDuration()));
      } else {
        leaveLine.setQuantity(leaveLine.getQuantity().subtract(leave.getDuration()));
      }
      leaveLine.setDaysValidated(leaveLine.getDaysValidated().subtract(leave.getDuration()));
    } else if (leave.getStatusSelect() == LeaveRequestRepository.STATUS_AWAITING_VALIDATION) {
      if (leave.getInjectConsumeSelect() == LeaveRequestRepository.SELECT_CONSUME) {
        leaveLine.setDaysToValidate(leaveLine.getDaysToValidate().add(leave.getDuration()));
      } else {
        leaveLine.setDaysToValidate(leaveLine.getDaysToValidate().subtract(leave.getDuration()));
      }
    }
  }

  public double computeStartDateWithSelect(
      LocalDate date, int select, WeeklyPlanning weeklyPlanning) {
    double value = 0;
    if (select == LeaveRequestRepository.SELECT_MORNING) {
      value = weeklyPlanningService.getWorkingDayValueInDays(weeklyPlanning, date);
    } else {
      DayPlanning dayPlanning = weeklyPlanningService.findDayPlanning(weeklyPlanning, date);
      if (dayPlanning != null
          && dayPlanning.getAfternoonFrom() != null
          && dayPlanning.getAfternoonTo() != null) {
        value = 0.5;
      }
    }
    return value;
  }

  public double computeEndDateWithSelect(
      LocalDate date, int select, WeeklyPlanning weeklyPlanning) {
    double value = 0;
    if (select == LeaveRequestRepository.SELECT_AFTERNOON) {
      value = weeklyPlanningService.getWorkingDayValueInDays(weeklyPlanning, date);
    } else {
      DayPlanning dayPlanning = weeklyPlanningService.findDayPlanning(weeklyPlanning, date);
      if (dayPlanning != null
          && dayPlanning.getMorningFrom() != null
          && dayPlanning.getMorningTo() != null) {
        value = 0.5;
      }
    }
    return value;
  }

  @Transactional(rollbackOn = {Exception.class})
  public LeaveRequest createEvents(LeaveRequest leave) throws AxelorException {
    Employee employee = leave.getUser().getEmployee();
    if (employee == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          leave.getUser().getName());
    }

    WeeklyPlanning weeklyPlanning = employee.getWeeklyPlanning();

    if (weeklyPlanning == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.EMPLOYEE_PLANNING),
          employee.getName());
    }

    LocalDateTime fromDateTime;
    LocalDateTime toDateTime;
    if (leave.getLeaveLine().getLeaveReason().getUnitSelect()
        == LeaveReasonRepository.UNIT_SELECT_DAYS) {
      fromDateTime = getDefaultStart(weeklyPlanning, leave);
      toDateTime = getDefaultEnd(weeklyPlanning, leave);
    } else {
      fromDateTime = leave.getFromDateT();
      toDateTime = leave.getToDateT();
    }

    ICalendarEvent event =
        icalendarService.createEvent(
            fromDateTime,
            toDateTime,
            leave.getUser(),
            leave.getComments(),
            4,
            leave.getLeaveLine().getLeaveReason().getLeaveReason()
                + " "
                + leave.getUser().getFullName());
    icalEventRepo.save(event);
    leave.setIcalendarEvent(event);

    return leave;
  }

  protected LocalDateTime getDefaultStart(WeeklyPlanning weeklyPlanning, LeaveRequest leave) {
    int startTimeHour = 0;
    int startTimeMin = 0;

    DayPlanning startDay =
        weeklyPlanningService.findDayPlanning(weeklyPlanning, leave.getFromDateT().toLocalDate());

    if (leave.getStartOnSelect() == LeaveRequestRepository.SELECT_MORNING) {
      if (startDay != null && startDay.getMorningFrom() != null) {
        startTimeHour = startDay.getMorningFrom().getHour();
        startTimeMin = startDay.getMorningFrom().getMinute();
      } else {
        startTimeHour = 8;
        startTimeMin = 0;
      }
    } else {
      if (startDay != null && startDay.getAfternoonFrom() != null) {
        startTimeHour = startDay.getAfternoonFrom().getHour();
        startTimeMin = startDay.getAfternoonFrom().getMinute();
      } else {
        startTimeHour = 14;
        startTimeMin = 0;
      }
    }
    return LocalDateTime.of(
        leave.getFromDateT().toLocalDate(), LocalTime.of(startTimeHour, startTimeMin));
  }

  protected LocalDateTime getDefaultEnd(WeeklyPlanning weeklyPlanning, LeaveRequest leave) {
    int endTimeHour = 0;
    int endTimeMin = 0;

    DayPlanning endDay =
        weeklyPlanningService.findDayPlanning(weeklyPlanning, leave.getToDateT().toLocalDate());

    if (leave.getEndOnSelect() == LeaveRequestRepository.SELECT_MORNING) {
      if (endDay != null && endDay.getMorningTo() != null) {
        endTimeHour = endDay.getMorningTo().getHour();
        endTimeMin = endDay.getMorningTo().getMinute();
      } else {
        endTimeHour = 12;
        endTimeMin = 0;
      }
    } else {
      if (endDay != null && endDay.getAfternoonTo() != null) {
        endTimeHour = endDay.getAfternoonTo().getHour();
        endTimeMin = endDay.getAfternoonTo().getMinute();
      } else {
        endTimeHour = 18;
        endTimeMin = 0;
      }
    }

    return LocalDateTime.of(
        leave.getToDateT().toLocalDate(), LocalTime.of(endTimeHour, endTimeMin));
  }

  public BigDecimal computeLeaveDaysByLeaveRequest(
      LocalDate fromDate, LocalDate toDate, LeaveRequest leaveRequest, Employee employee)
      throws AxelorException {
    BigDecimal leaveDays = BigDecimal.ZERO;
    WeeklyPlanning weeklyPlanning = employee.getWeeklyPlanning();
    LocalDate leaveFrom = leaveRequest.getFromDateT().toLocalDate();
    LocalDate leaveTo = leaveRequest.getToDateT().toLocalDate();

    LocalDate itDate = fromDate;
    if (fromDate.isBefore(leaveFrom) || fromDate.equals(leaveFrom)) {
      itDate = leaveFrom;
    }

    boolean morningHalf = false;
    boolean eveningHalf = false;
    BigDecimal daysToAdd = BigDecimal.ZERO;
    if (leaveTo.equals(leaveFrom)
        && leaveRequest.getStartOnSelect() == leaveRequest.getEndOnSelect()) {
      eveningHalf = leaveRequest.getStartOnSelect() == LeaveRequestRepository.SELECT_AFTERNOON;
      morningHalf = leaveRequest.getStartOnSelect() == LeaveRequestRepository.SELECT_MORNING;
    }

    while (!itDate.isEqual(leaveTo.plusDays(1)) && !itDate.isEqual(toDate.plusDays(1))) {

      if (itDate.equals(leaveFrom) && !morningHalf) {
        daysToAdd =
            BigDecimal.valueOf(
                computeStartDateWithSelect(
                    itDate, leaveRequest.getStartOnSelect(), weeklyPlanning));
      } else if (itDate.equals(leaveTo) && !eveningHalf) {
        daysToAdd =
            BigDecimal.valueOf(
                computeEndDateWithSelect(itDate, leaveRequest.getEndOnSelect(), weeklyPlanning));
      } else {
        daysToAdd =
            BigDecimal.valueOf(
                weeklyPlanningService.getWorkingDayValueInDays(weeklyPlanning, itDate));
      }

      if (!publicHolidayHrService.checkPublicHolidayDay(itDate, employee)) {
        leaveDays = leaveDays.add(daysToAdd);
      }
      itDate = itDate.plusDays(1);
    }

    return leaveDays;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void cancel(LeaveRequest leaveRequest) throws AxelorException {

    if (leaveRequest.getLeaveLine().getLeaveReason().getManageAccumulation()) {
      manageCancelLeaves(leaveRequest);
    }

    if (leaveRequest.getIcalendarEvent() != null) {
      ICalendarEvent event = leaveRequest.getIcalendarEvent();
      leaveRequest.setIcalendarEvent(null);
      icalEventRepo.remove(icalEventRepo.find(event.getId()));
    }
    leaveRequest.setStatusSelect(LeaveRequestRepository.STATUS_CANCELED);
  }

  public Message sendCancellationEmail(LeaveRequest leaveRequest)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {

    HRConfig hrConfig = hrConfigService.getHRConfig(leaveRequest.getCompany());

    if (hrConfig.getLeaveMailNotification()) {

      return templateMessageService.generateAndSendMessage(
          leaveRequest, hrConfigService.getCanceledLeaveTemplate(hrConfig));
    }

    return null;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void confirm(LeaveRequest leaveRequest) throws AxelorException {

    if (leaveRequest.getLeaveLine().getLeaveReason().getManageAccumulation()) {
      manageSentLeaves(leaveRequest);
    }

    leaveRequest.setStatusSelect(LeaveRequestRepository.STATUS_AWAITING_VALIDATION);
    leaveRequest.setRequestDate(appBaseService.getTodayDate());

    leaveRequestRepo.save(leaveRequest);
  }

  public Message sendConfirmationEmail(LeaveRequest leaveRequest)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {

    HRConfig hrConfig = hrConfigService.getHRConfig(leaveRequest.getCompany());

    if (hrConfig.getLeaveMailNotification()) {

      return templateMessageService.generateAndSendMessage(
          leaveRequest, hrConfigService.getSentLeaveTemplate(hrConfig));
    }

    return null;
  }

  @Transactional(rollbackOn = {Exception.class})
  public void validate(LeaveRequest leaveRequest) throws AxelorException {

    LeaveLine leaveLine = leaveRequest.getLeaveLine();
    if (leaveLine.getLeaveReason().getManageAccumulation()) {
      manageValidateLeaves(leaveRequest);
    }

    leaveRequest.setStatusSelect(LeaveRequestRepository.STATUS_VALIDATED);
    leaveRequest.setValidatedBy(AuthUtils.getUser());
    leaveRequest.setValidationDate(appBaseService.getTodayDate());
    leaveRequest.setQuantityBeforeValidation(leaveLine.getQuantity());

    leaveRequestRepo.save(leaveRequest);

    createEvents(leaveRequest);
  }

  public Message sendValidationEmail(LeaveRequest leaveRequest)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {

    HRConfig hrConfig = hrConfigService.getHRConfig(leaveRequest.getCompany());

    if (hrConfig.getLeaveMailNotification()) {

      return templateMessageService.generateAndSendMessage(
          leaveRequest, hrConfigService.getValidatedLeaveTemplate(hrConfig));
    }

    return null;
  }

  @Transactional(rollbackOn = {Exception.class})
  public void refuse(LeaveRequest leaveRequest) throws AxelorException {

    if (leaveRequest.getLeaveLine().getLeaveReason().getManageAccumulation()) {
      manageRefuseLeaves(leaveRequest);
    }

    leaveRequest.setStatusSelect(LeaveRequestRepository.STATUS_REFUSED);
    leaveRequest.setRefusedBy(AuthUtils.getUser());
    leaveRequest.setRefusalDate(appBaseService.getTodayDate());

    leaveRequestRepo.save(leaveRequest);
  }

  public Message sendRefusalEmail(LeaveRequest leaveRequest)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {

    HRConfig hrConfig = hrConfigService.getHRConfig(leaveRequest.getCompany());

    if (hrConfig.getLeaveMailNotification()) {

      return templateMessageService.generateAndSendMessage(
          leaveRequest, hrConfigService.getRefusedLeaveTemplate(hrConfig));
    }

    return null;
  }

  public boolean willHaveEnoughDays(LeaveRequest leaveRequest) {

    LocalDateTime todayDate = appBaseService.getTodayDateTime().toLocalDateTime();
    LocalDateTime beginDate = leaveRequest.getFromDateT();

    int interval =
        (beginDate.getYear() - todayDate.getYear()) * 12
            + beginDate.getMonthValue()
            - todayDate.getMonthValue();
    BigDecimal num =
        leaveRequest
            .getLeaveLine()
            .getQuantity()
            .add(
                leaveRequest
                    .getUser()
                    .getEmployee()
                    .getWeeklyPlanning()
                    .getLeaveCoef()
                    .multiply(
                        leaveRequest.getLeaveLine().getLeaveReason().getDefaultDayNumberGain())
                    .multiply(BigDecimal.valueOf(interval)));

    return leaveRequest.getDuration().compareTo(num) <= 0;
  }

  @Transactional
  public LeaveLine getLeaveReasonToJustify(Employee employee, LeaveReason leaveReason) {
    LeaveLine leaveLineBase = null;
    if ((employee.getLeaveLineList() != null) || (!employee.getLeaveLineList().isEmpty())) {
      for (LeaveLine leaveLine : employee.getLeaveLineList()) {
        if (leaveReason.equals(leaveLine.getLeaveReason())) {
          leaveLineBase = leaveLine;
        }
      }
    }
    return leaveLineBase;
  }

  @Transactional(rollbackOn = {Exception.class})
  public LeaveLine createLeaveReasonToJustify(Employee employee, LeaveReason leaveReason)
      throws AxelorException {
    LeaveLine leaveLineEmployee = new LeaveLine();
    leaveLineEmployee.setLeaveReason(leaveReason);
    leaveLineEmployee.setEmployee(employee);

    leaveLineRepo.save(leaveLineEmployee);
    return leaveLineEmployee;
  }

  @Transactional(rollbackOn = {Exception.class})
  public LeaveLine addLeaveReasonOrCreateIt(Employee employee, LeaveReason leaveReason)
      throws AxelorException {
    LeaveLine leaveLine = getLeaveReasonToJustify(employee, leaveReason);
    if ((leaveLine == null) || (leaveLine.getLeaveReason() != leaveReason)) {
      leaveLine = createLeaveReasonToJustify(employee, leaveReason);
    }
    return leaveLine;
  }

  public boolean isLeaveDay(User user, LocalDate date) {
    return getLeave(user, date) != null;
  }

  public LeaveRequest getLeave(User user, LocalDate date) {
    List<LeaveRequest> leaves =
        JPA.all(LeaveRequest.class)
            .filter("self.user = :userId AND self.statusSelect IN (:awaitingValidation,:validated)")
            .bind("userId", user)
            .bind("awaitingValidation", LeaveRequestRepository.STATUS_AWAITING_VALIDATION)
            .bind("validated", LeaveRequestRepository.STATUS_VALIDATED)
            .fetch();

    if (ObjectUtils.notEmpty(leaves)) {
      for (LeaveRequest leave : leaves) {
        LocalDate from = leave.getFromDateT().toLocalDate();
        LocalDate to = leave.getToDateT().toLocalDate();
        if ((from.isBefore(date) && to.isAfter(date)) || from.isEqual(date) || to.isEqual(date)) {
          return leave;
        }
      }
    }
    return null;
  }

  public BigDecimal getExpectedDays(LeaveRequest leaveRequest) {
    BigDecimal quantity = null;

    if (leaveRequest.getLeaveLine() != null) {
      quantity = leaveRequest.getLeaveLine().getQuantity();

      if (leaveRequest.getFromDateT() != null) {
        LocalDate fromDate = leaveRequest.getFromDateT().toLocalDate().withDayOfMonth(1);
        Period period = Period.between(LocalDate.now().withDayOfMonth(1), fromDate);

        quantity =
                quantity.add(
                        BigDecimal.valueOf(period.getMonths())
                                .multiply(
                                        leaveRequest.getLeaveLine().getLeaveReason().getDefaultDayNumberGain()));
      }
    }

    return quantity;
  }
}
