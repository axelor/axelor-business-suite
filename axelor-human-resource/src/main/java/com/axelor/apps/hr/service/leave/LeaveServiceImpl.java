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
import com.axelor.apps.hr.db.repo.LeaveRequestRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.hr.service.publicHoliday.PublicHolidayHrService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.mail.MessagingException;

public class LeaveServiceImpl implements LeaveService {

  private LeaveLineRepository leaveLineRepo;
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

    this.setLeaveLineRepo(leaveLineRepo);
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
    LocalDate leaveFromDate = leave.getFromDate();
    LocalDate leaveToDate = leave.getToDate();

    int startOn = leave.getStartOnSelect();
    int endOn = leave.getEndOnSelect();

    LocalDate from = leaveFromDate;
    LocalDate to = leaveToDate;
    // if the leave starts before the beginning of the period,
    // we use the beginning date of the period.
    if (leaveFromDate.isBefore(fromDate)) {
      from = fromDate;
      startOn = LeaveRequestRepository.SELECT_MORNING;
    }
    // if the leave ends before the end of the period,
    // we use the last date of the period.
    if (leaveToDate.isAfter(toDate)) {
      to = toDate;
      endOn = LeaveRequestRepository.SELECT_AFTERNOON;
    }

    BigDecimal duration = this.computeDuration(leave, from, to, startOn, endOn);

    return duration;
  }

  /**
   * Compute the duration of a given leave request.
   *
   * @param leave
   * @return the computed duration in days
   * @throws AxelorException
   */
  public BigDecimal computeDuration(LeaveRequest leave) throws AxelorException {
    return this.computeDuration(
        leave,
        leave.getFromDate(),
        leave.getToDate(),
        leave.getStartOnSelect(),
        leave.getEndOnSelect());
  }

  /**
   * Compute the duration of a given leave request.
   *
   * @param leave
   * @param from the beginning of the period
   * @param to the ending of the period
   * @param startOn If the period starts in the morning or in the afternoon
   * @param endOn If the period ends in the morning or in the afternoon
   * @return the computed duration in days
   * @throws AxelorException
   */
  public BigDecimal computeDuration(
      LeaveRequest leave, LocalDate from, LocalDate to, int startOn, int endOn)
      throws AxelorException {
    if (from != null && to != null) {
      Employee employee = leave.getEmployee();
      if (employee == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
            leave.getEmployee().getName());
      }

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
      EventsPlanning publicHolidayPlanning = employee.getPublicHolidayEventsPlanning();
      if (publicHolidayPlanning == null) {
        if (leave.getCompany() != null && leave.getCompany().getHrConfig() != null) {
          publicHolidayPlanning = leave.getCompany().getHrConfig().getPublicHolidayEventsPlanning();
        }
      }

      BigDecimal duration = BigDecimal.ZERO;

      // If the leave request is only for 1 day
      if (from.isEqual(to)) {
        if (startOn == endOn) {
          if (startOn == LeaveRequestRepository.SELECT_MORNING) {
            duration =
                duration.add(
                    new BigDecimal(
                        weeklyPlanningService.workingDayValueWithSelect(
                            weeklyPlanning, from, true, false)));
          } else {
            duration =
                duration.add(
                    new BigDecimal(
                        weeklyPlanningService.workingDayValueWithSelect(
                            weeklyPlanning, from, false, true)));
          }
        } else {
          duration =
              duration.add(
                  new BigDecimal(
                      weeklyPlanningService.workingDayValueWithSelect(
                          weeklyPlanning, from, true, true)));
        }
      }

      // Else if it's on several days
      else {
        duration =
            duration.add(
                new BigDecimal(this.computeStartDateWithSelect(from, startOn, weeklyPlanning)));
        LocalDate itDate = leave.getFromDate().plusDays(1);

        while (!itDate.isEqual(to) && !itDate.isAfter(to)) {
          duration =
              duration.add(
                  new BigDecimal(weeklyPlanningService.workingDayValue(weeklyPlanning, itDate)));
          itDate = itDate.plusDays(1);
        }

        duration =
            duration.add(new BigDecimal(this.computeEndDateWithSelect(to, endOn, weeklyPlanning)));
      }

      if (publicHolidayPlanning != null) {
        duration =
            duration.subtract(
                Beans.get(PublicHolidayHrService.class)
                    .computePublicHolidayDays(from, to, weeklyPlanning, publicHolidayPlanning));
      }

      if (duration.compareTo(BigDecimal.ZERO) < 0) {
        duration = BigDecimal.ZERO;
      }
      return duration;
    } else {
      return BigDecimal.ZERO;
    }
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void manageSentLeaves(LeaveRequest leave) throws AxelorException {
    Employee employee = leave.getEmployee();
    if (employee == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          leave.getEmployee().getName());
    }
    LeaveLine leaveLine =
        getLeaveLineRepo()
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

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void manageValidateLeaves(LeaveRequest leave) throws AxelorException {
    Employee employee = leave.getEmployee();
    if (employee == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          leave.getEmployee().getName());
    }
    LeaveLine leaveLine =
        getLeaveLineRepo()
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
      if (leaveLine.getQuantity().compareTo(BigDecimal.ZERO) < 0
          && !employee.getNegativeValueLeave()) {
        throw new AxelorException(
            leave,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.LEAVE_ALLOW_NEGATIVE_VALUE_EMPLOYEE),
            employee.getName());
      }
      if (leaveLine.getQuantity().compareTo(BigDecimal.ZERO) < 0
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

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void manageRefuseLeaves(LeaveRequest leave) throws AxelorException {
    Employee employee = leave.getEmployee();
    if (employee == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          leave.getEmployee().getName());
    }
    LeaveLine leaveLine =
        getLeaveLineRepo()
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

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void manageCancelLeaves(LeaveRequest leave) throws AxelorException {
    Employee employee = leave.getEmployee();
    if (employee == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          leave.getEmployee().getName());
    }
    LeaveLine leaveLine =
        getLeaveLineRepo()
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
      value = weeklyPlanningService.workingDayValue(weeklyPlanning, date);
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
      value = weeklyPlanningService.workingDayValue(weeklyPlanning, date);
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

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public LeaveRequest createEvents(LeaveRequest leave) throws AxelorException {
    Employee employee = leave.getEmployee();
    if (employee == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          leave.getEmployee().getName());
    }

    WeeklyPlanning weeklyPlanning = employee.getWeeklyPlanning();

    if (weeklyPlanning == null) {
      throw new AxelorException(
          leave,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.EMPLOYEE_PLANNING),
          employee.getName());
    }

    int startTimeHour = 0;
    int startTimeMin = 0;
    DayPlanning startDay =
        weeklyPlanningService.findDayPlanning(weeklyPlanning, leave.getFromDate());
    DayPlanning endDay = weeklyPlanningService.findDayPlanning(weeklyPlanning, leave.getToDate());
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
    LocalDateTime fromDateTime =
        LocalDateTime.of(
            leave.getFromDate().getYear(),
            leave.getFromDate().getMonthValue(),
            leave.getFromDate().getDayOfMonth(),
            startTimeHour,
            startTimeMin);

    int endTimeHour = 0;
    int endTimeMin = 0;
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
    LocalDateTime toDateTime =
        LocalDateTime.of(
            leave.getToDate().getYear(),
            leave.getToDate().getMonthValue(),
            leave.getToDate().getDayOfMonth(),
            endTimeHour,
            endTimeMin);

    ICalendarEvent event =
        icalendarService.createEvent(
            fromDateTime,
            toDateTime,
            leave.getEmployee().getUser(),
            leave.getComments(),
            4,
            leave.getLeaveLine().getLeaveReason().getLeaveReason()
                + " "
                + leave.getEmployee().getName());
    icalEventRepo.save(event);
    leave.setIcalendarEvent(event);

    return leave;
  }

  public BigDecimal computeLeaveDaysByLeaveRequest(
      LocalDate fromDate, LocalDate toDate, LeaveRequest leaveRequest, Employee employee)
      throws AxelorException {
    BigDecimal leaveDays = BigDecimal.ZERO;
    WeeklyPlanning weeklyPlanning = employee.getWeeklyPlanning();
    if (leaveRequest.getFromDate().equals(fromDate)) {
      leaveDays =
          leaveDays.add(
              BigDecimal.valueOf(
                  this.computeStartDateWithSelect(
                      fromDate, leaveRequest.getStartOnSelect(), weeklyPlanning)));
    }
    if (leaveRequest.getToDate().equals(toDate)) {
      leaveDays =
          leaveDays.add(
              BigDecimal.valueOf(
                  this.computeEndDateWithSelect(
                      toDate, leaveRequest.getEndOnSelect(), weeklyPlanning)));
    }

    LocalDate itDate = LocalDate.parse(fromDate.toString(), DateTimeFormatter.ISO_DATE);
    if (fromDate.isBefore(leaveRequest.getFromDate())
        || fromDate.equals(leaveRequest.getFromDate())) {
      itDate = leaveRequest.getFromDate();
    }

    while (!itDate.isEqual(leaveRequest.getToDate().plusDays(1)) && !itDate.isAfter(toDate)) {
      leaveDays =
          leaveDays.add(
              BigDecimal.valueOf(weeklyPlanningService.workingDayValue(weeklyPlanning, itDate)));
      if (publicHolidayHrService.checkPublicHolidayDay(itDate, employee)) {
        leaveDays = leaveDays.subtract(BigDecimal.ONE);
      }
      itDate = itDate.plusDays(1);
    }

    return leaveDays;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
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
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
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

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
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

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
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

    LocalDate todayDate = appBaseService.getTodayDate();
    LocalDate beginDate = leaveRequest.getFromDate();

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
                    .getEmployee()
                    .getWeeklyPlanning()
                    .getLeaveCoef()
                    .multiply(
                        leaveRequest.getLeaveLine().getLeaveReason().getDefaultDayNumberGain())
                    .multiply(new BigDecimal(interval)));

    if (leaveRequest.getDuration().compareTo(num) > 0) {
      return false;
    } else {
      return true;
    }
  }

  @Transactional
  public LeaveLine getLeaveReasonToJustify(Employee employee, LeaveReason leaveReason)
      throws AxelorException {
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

  @Transactional
  public LeaveLine createLeaveReasonToJustify(Employee employee, LeaveReason leaveReason)
      throws AxelorException {
    LeaveLine leaveLineEmployee = new LeaveLine();
    leaveLineEmployee.setLeaveReason(leaveReason);
    leaveLineEmployee.setEmployee(employee);

    getLeaveLineRepo().save(leaveLineEmployee);
    return leaveLineEmployee;
  }

  @Transactional
  public LeaveLine addLeaveReasonOrCreateIt(Employee employee, LeaveReason leaveReason)
      throws AxelorException {
    LeaveLine leaveLine = this.getLeaveReasonToJustify(employee, leaveReason);
    if ((leaveLine == null) || (leaveLine.getLeaveReason() != leaveReason)) {
      leaveLine = this.createLeaveReasonToJustify(employee, leaveReason);
    }
    return leaveLine;
  }

  @Override
  public LeaveLine leaveReasonToJustify(Employee employee, LeaveReason leaveReason)
      throws AxelorException {
    // TODO Auto-generated method stub
    return null;
  }

  /** @return the leaveLineRepo */
  public LeaveLineRepository getLeaveLineRepo() {
    return leaveLineRepo;
  }

  /** @param leaveLineRepo the leaveLineRepo to set */
  public void setLeaveLineRepo(LeaveLineRepository leaveLineRepo) {
    this.leaveLineRepo = leaveLineRepo;
  }
}
