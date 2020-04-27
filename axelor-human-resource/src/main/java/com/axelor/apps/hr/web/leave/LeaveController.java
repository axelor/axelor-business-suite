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
package com.axelor.apps.hr.web.leave;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.base.service.PeriodService;
import com.axelor.apps.base.service.message.MessageServiceBaseImpl;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.ExtraHours;
import com.axelor.apps.hr.db.LeaveLine;
import com.axelor.apps.hr.db.LeaveReason;
import com.axelor.apps.hr.db.LeaveRequest;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.LeaveReasonRepository;
import com.axelor.apps.hr.db.repo.LeaveRequestRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.HRMenuTagService;
import com.axelor.apps.hr.service.HRMenuValidateService;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.hr.service.leave.LeaveService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Singleton
public class LeaveController {

  public void editLeave(ActionRequest request, ActionResponse response) {
    try {

      User user = AuthUtils.getUser();

      List<LeaveRequest> leaveList =
          Beans.get(LeaveRequestRepository.class)
              .all()
              .filter(
                  "self.user = ?1 AND self.company = ?2 AND self.statusSelect = 1",
                  user,
                  user.getActiveCompany())
              .fetch();
      if (leaveList.isEmpty()) {
        response.setView(
            ActionView.define(I18n.get("LeaveRequest"))
                .model(LeaveRequest.class.getName())
                .add("form", "leave-request-form")
                .map());
      } else if (leaveList.size() == 1) {
        response.setView(
            ActionView.define(I18n.get("LeaveRequest"))
                .model(LeaveRequest.class.getName())
                .add("form", "leave-request-form")
                .param("forceEdit", "true")
                .context("_showRecord", String.valueOf(leaveList.get(0).getId()))
                .map());
      } else {
        response.setView(
            ActionView.define(I18n.get("LeaveRequest"))
                .model(Wizard.class.getName())
                .add("form", "popup-leave-request-form")
                .param("forceEdit", "true")
                .param("popup", "true")
                .param("show-toolbar", "false")
                .param("show-confirm", "false")
                .param("forceEdit", "true")
                .param("popup-save", "false")
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  @SuppressWarnings("unchecked")
  public void editLeaveSelected(ActionRequest request, ActionResponse response) {
    try {
      Map<String, Object> leaveMap = (Map<String, Object>) request.getContext().get("leaveSelect");
      if (leaveMap == null) {
        response.setError(I18n.get("Select the leave request you want to edit"));
      } else {
        Long leaveId = Long.valueOf(leaveMap.get("id").toString());

        response.setView(
            ActionView.define(I18n.get("LeaveRequest"))
                .model(LeaveRequest.class.getName())
                .add("form", "leave-request-form")
                .param("forceEdit", "true")
                .domain("self.id = " + leaveId)
                .context("_showRecord", leaveId)
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void validateLeave(ActionRequest request, ActionResponse response) {
    try {
      User user = AuthUtils.getUser();
      Employee employee = user.getEmployee();

      ActionViewBuilder actionView =
          ActionView.define(I18n.get("Leave Requests to Validate"))
              .model(LeaveRequest.class.getName())
              .add("grid", "leave-request-validate-grid")
              .add("form", "leave-request-form");

      Beans.get(HRMenuValidateService.class).createValidateDomain(user, employee, actionView);

      response.setView(actionView.map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void historicLeave(ActionRequest request, ActionResponse response) {
    try {

      User user = AuthUtils.getUser();
      Employee employee = user.getEmployee();

      ActionViewBuilder actionView =
          ActionView.define(I18n.get("Colleague Leave Requests"))
              .model(LeaveRequest.class.getName())
              .add("grid", "leave-request-grid")
              .add("form", "leave-request-form");

      actionView.domain("(self.statusSelect = 3 OR self.statusSelect = 4)");

      if (employee == null || !employee.getHrManager()) {
        actionView
            .domain(actionView.get().getDomain() + " AND self.user.employee.managerUser = :_user")
            .context("_user", user);
      }

      response.setView(actionView.map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void leaveCalendar(ActionRequest request, ActionResponse response) {
    try {

      User user = AuthUtils.getUser();
      Employee employee = user.getEmployee();

      ActionViewBuilder actionView =
          ActionView.define(I18n.get("Leaves calendar"))
              .model(ICalendarEvent.class.getName())
              .add("calendar", "calendar-event-leave-request")
              .add("grid", "calendar-event-grid")
              .add("form", "calendar-event-form");

      actionView.domain(
          "self.typeSelect = 4 AND self.id IN (SELECT leaveRequest.icalendarEvent FROM LeaveRequest leaveRequest WHERE leaveRequest.statusSelect = 3");

      if (employee == null || !employee.getHrManager()) {
        actionView
            .domain(
                actionView.get().getDomain()
                    + " AND leaveRequest.user.employee.managerUser = :_user")
            .context("_user", user);
      }
      actionView.domain(actionView.get().getDomain() + ")");
      response.setView(actionView.map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void showSubordinateLeaves(ActionRequest request, ActionResponse response) {
    try {

      User user = AuthUtils.getUser();

      String domain =
          "self.user.employee.managerUser.employee.managerUser = :_user AND self.statusSelect = 2";
      long nbLeaveRequests = Query.of(ExtraHours.class).filter(domain).bind("_user", user).count();

      if (nbLeaveRequests == 0) {
        response.setNotify(I18n.get("No Leave Request to be validated by your subordinates"));
      } else {
        ActionViewBuilder actionView =
            ActionView.define(I18n.get("Leaves to be Validated by your subordinates"))
                .model(LeaveRequest.class.getName())
                .add("grid", "leave-request-grid")
                .add("form", "leave-request-form");
        response.setView(actionView.domain(domain).context("_user", user).map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void testDuration(ActionRequest request, ActionResponse response) {
    try {
      LeaveRequest leave = request.getContext().asType(LeaveRequest.class);
      double duration = leave.getDuration().doubleValue();
      if (duration % 0.5 != 0) {
        response.setError(I18n.get("Invalid duration (must be a 0.5's multiple)"));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void computeDuration(ActionRequest request, ActionResponse response) {
    try {
      LeaveRequest leave = request.getContext().asType(LeaveRequest.class);
      response.setValue("duration", Beans.get(LeaveService.class).computeDuration(leave));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  // sending leave request and an email to the manager
  public void send(ActionRequest request, ActionResponse response) {

    try {
      LeaveService leaveService = Beans.get(LeaveService.class);
      LeaveRequest leaveRequest = request.getContext().asType(LeaveRequest.class);
      leaveRequest = Beans.get(LeaveRequestRepository.class).find(leaveRequest.getId());

      if (leaveRequest.getUser().getEmployee().getWeeklyPlanning() == null) {
        response.setAlert(
            String.format(
                I18n.get(IExceptionMessage.EMPLOYEE_PLANNING),
                leaveRequest.getUser().getEmployee().getName()));
        return;
      }
      BigDecimal quantity = new BigDecimal((String) request.getContext().get("expectedDays"));
      if (quantity.subtract(leaveRequest.getDuration()).signum()
          < 0) {
        if (!leaveRequest.getLeaveLine().getLeaveReason().getAllowNegativeValue()
            && !leaveService.willHaveEnoughDays(leaveRequest)) {
          String instruction = leaveRequest.getLeaveLine().getLeaveReason().getInstruction();
          if (instruction == null) {
            instruction = "";
          }
          response.setAlert(
              String.format(
                      I18n.get(IExceptionMessage.LEAVE_ALLOW_NEGATIVE_VALUE_REASON),
                      leaveRequest.getLeaveLine().getLeaveReason().getLeaveReason())
                  + " "
                  + instruction);
          return;
        } else {
          response.setNotify(
              String.format(
                  I18n.get(IExceptionMessage.LEAVE_ALLOW_NEGATIVE_ALERT),
                  leaveRequest.getLeaveLine().getLeaveReason().getLeaveReason()));
        }
      }

      leaveService.confirm(leaveRequest);

      Message message = leaveService.sendConfirmationEmail(leaveRequest);
      if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
        response.setFlash(
            String.format(
                I18n.get("Email sent to %s"),
                Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  /**
   * Validates leave request and sends an email to the applicant.
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void validate(ActionRequest request, ActionResponse response) {

    try {
      LeaveService leaveService = Beans.get(LeaveService.class);
      LeaveRequest leaveRequest = request.getContext().asType(LeaveRequest.class);
      leaveRequest = Beans.get(LeaveRequestRepository.class).find(leaveRequest.getId());

      leaveService.validate(leaveRequest);

      Message message = leaveService.sendValidationEmail(leaveRequest);
      if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
        response.setFlash(
            String.format(
                I18n.get("Email sent to %s"),
                Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
      }
      Beans.get(PeriodService.class)
          .checkPeriod(
              leaveRequest.getCompany(),
              leaveRequest.getToDateT().toLocalDate(),
              leaveRequest.getFromDateT().toLocalDate());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  /**
   * Refuses leave request and sends an email to the applicant.
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void refuse(ActionRequest request, ActionResponse response) throws AxelorException {

    try {
      LeaveService leaveService = Beans.get(LeaveService.class);
      LeaveRequest leaveRequest = request.getContext().asType(LeaveRequest.class);
      leaveRequest = Beans.get(LeaveRequestRepository.class).find(leaveRequest.getId());

      leaveService.refuse(leaveRequest);

      Message message = leaveService.sendRefusalEmail(leaveRequest);
      if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
        response.setFlash(
            String.format(
                I18n.get("Email sent to %s"),
                Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  public void cancel(ActionRequest request, ActionResponse response) {
    try {
      LeaveRequest leave = request.getContext().asType(LeaveRequest.class);
      leave = Beans.get(LeaveRequestRepository.class).find(leave.getId());
      LeaveService leaveService = Beans.get(LeaveService.class);

      leaveService.cancel(leave);

      Message message = leaveService.sendCancellationEmail(leave);
      if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
        response.setFlash(
            String.format(
                I18n.get("Email sent to %s"),
                Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  /* Count Tags displayed on the menu items */

  @Transactional
  public void leaveReasonToJustify(ActionRequest request, ActionResponse response) {
    try {
      LeaveRequest leave = request.getContext().asType(LeaveRequest.class);
      Boolean leaveToJustify = leave.getToJustifyLeaveReason();
      LeaveLine leaveLine = null;

      if (!leaveToJustify) {
        return;
      }
      Company company = leave.getCompany();
      if (leave.getUser() == null) {
        return;
      }
      if (company == null) {
        company = leave.getUser().getActiveCompany();
      }
      if (company == null) {
        return;
      }

      Beans.get(HRConfigService.class).getLeaveReason(company.getHrConfig());

      Employee employee = leave.getUser().getEmployee();

      LeaveReason leaveReason =
          Beans.get(LeaveReasonRepository.class)
              .find(company.getHrConfig().getToJustifyLeaveReason().getId());

      if (employee != null) {
        employee = Beans.get(EmployeeRepository.class).find(leave.getUser().getEmployee().getId());
        leaveLine = Beans.get(LeaveService.class).addLeaveReasonOrCreateIt(employee, leaveReason);
        response.setValue("leaveLine", leaveLine);
      }
    } catch (AxelorException e) {
      TraceBackService.trace(response, e);
    }
  }

  public String leaveValidateMenuTag() {

    return Beans.get(HRMenuTagService.class)
        .countRecordsTag(LeaveRequest.class, LeaveRequestRepository.STATUS_AWAITING_VALIDATION);
  }

  public void computeExpectedDays(ActionRequest request, ActionResponse response) {
    try {
      LeaveRequest leaveRequest = request.getContext().asType(LeaveRequest.class);
      BigDecimal expectedDays = Beans.get(LeaveService.class).getExpectedDays(leaveRequest);
      response.setValue("$expectedDays", expectedDays);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
