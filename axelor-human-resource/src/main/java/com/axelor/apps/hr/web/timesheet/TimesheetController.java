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
package com.axelor.apps.hr.web.timesheet;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.PeriodService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.message.MessageServiceBaseImpl;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.report.IReport;
import com.axelor.apps.hr.service.HRMenuTagService;
import com.axelor.apps.hr.service.HRMenuValidateService;
import com.axelor.apps.hr.service.timesheet.TimesheetLineService;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.hr.service.user.UserHrService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.CallMethod;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TimesheetController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void prefillLines(ActionRequest request, ActionResponse response) {
    try {
      Timesheet timesheet = request.getContext().asType(Timesheet.class);
      Beans.get(TimesheetService.class).prefillLines(timesheet);
      response.setValues(timesheet);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e);
    }
  }

  @SuppressWarnings("unchecked")
  public void generateLines(ActionRequest request, ActionResponse response) throws AxelorException {
    try {
      Timesheet timesheet = request.getContext().asType(Timesheet.class);
      Context context = request.getContext();

      LocalDate fromGenerationDate = null;
      if (context.get("fromGenerationDate") != null)
        fromGenerationDate =
            LocalDate.parse(
                context.get("fromGenerationDate").toString(), DateTimeFormatter.ISO_DATE);
      LocalDate toGenerationDate = null;
      if (context.get("toGenerationDate") != null)
        toGenerationDate =
            LocalDate.parse(context.get("toGenerationDate").toString(), DateTimeFormatter.ISO_DATE);
      BigDecimal logTime = BigDecimal.ZERO;
      if (context.get("logTime") != null)
        logTime = new BigDecimal(context.get("logTime").toString());

      Map<String, Object> projectContext = (Map<String, Object>) context.get("project");
      Project project = null;
      if (projectContext != null) {
        project =
            Beans.get(ProjectRepository.class)
                .find(((Integer) projectContext.get("id")).longValue());
      }

      Map<String, Object> productContext = (Map<String, Object>) context.get("product");
      Product product = null;
      if (productContext != null) {
        product =
            Beans.get(ProductRepository.class)
                .find(((Integer) productContext.get("id")).longValue());
      }
      if (context.get("showActivity") == null || !(Boolean) context.get("showActivity")) {
        product = Beans.get(UserHrService.class).getTimesheetProduct(timesheet.getUser());
      }

      timesheet =
          Beans.get(TimesheetService.class)
              .generateLines(
                  timesheet, fromGenerationDate, toGenerationDate, logTime, project, product);
      response.setValue("timesheetLineList", timesheet.getTimesheetLineList());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void editTimesheet(ActionRequest request, ActionResponse response) {
    List<Timesheet> timesheetList =
        Beans.get(TimesheetRepository.class)
            .all()
            .filter(
                "self.user = ?1 AND self.company = ?2 AND self.statusSelect = 1",
                AuthUtils.getUser(),
                Optional.ofNullable(AuthUtils.getUser()).map(User::getActiveCompany).orElse(null))
            .fetch();
    if (timesheetList.isEmpty()) {
      response.setView(
          ActionView.define(I18n.get("Timesheet"))
              .model(Timesheet.class.getName())
              .add("form", "timesheet-form")
              .map());
    } else if (timesheetList.size() == 1) {
      response.setView(
          ActionView.define(I18n.get("Timesheet"))
              .model(Timesheet.class.getName())
              .add("form", "timesheet-form")
              .param("forceEdit", "true")
              .context("_showRecord", String.valueOf(timesheetList.get(0).getId()))
              .map());
    } else {
      response.setView(
          ActionView.define(I18n.get("Timesheet"))
              .model(Wizard.class.getName())
              .add("form", "popup-timesheet-form")
              .param("forceEdit", "true")
              .param("popup", "true")
              .param("show-toolbar", "false")
              .param("show-confirm", "false")
              .param("forceEdit", "true")
              .param("popup-save", "false")
              .map());
    }
  }

  public void allTimesheet(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Employee employee = user.getEmployee();

    ActionViewBuilder actionView =
        ActionView.define(I18n.get("Timesheets"))
            .model(Timesheet.class.getName())
            .add("grid", "all-timesheet-grid")
            .add("form", "timesheet-form")
            .param("search-filters", "timesheet-filters");

    if (employee == null || !employee.getHrManager()) {
      if (employee == null || employee.getManagerUser() == null) {
        actionView
            .domain("self.user = :_user OR self.user.employee.managerUser = :_user")
            .context("_user", user);
      } else {
        actionView.domain("self.user.employee.managerUser = :_user").context("_user", user);
      }
    }

    response.setView(actionView.map());
  }

  public void allTimesheetLine(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Employee employee = user.getEmployee();

    ActionViewBuilder actionView =
        ActionView.define(I18n.get("See timesheet lines"))
            .model(TimesheetLine.class.getName())
            .add("grid", "timesheet-line-grid")
            .add("form", "timesheet-line-form");

    Beans.get(TimesheetService.class).createDomainAllTimesheetLine(user, employee, actionView);

    response.setView(actionView.map());
  }

  public void validateTimesheet(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Employee employee = user.getEmployee();

    ActionViewBuilder actionView =
        ActionView.define(I18n.get("Timesheets to Validate"))
            .model(Timesheet.class.getName())
            .add("grid", "timesheet-validate-grid")
            .add("form", "timesheet-form")
            .param("search-filters", "timesheet-filters")
            .context(
                "todayDate", Beans.get(AppBaseService.class).getTodayDate(user.getActiveCompany()));

    Beans.get(HRMenuValidateService.class).createValidateDomain(user, employee, actionView);

    response.setView(actionView.map());
  }

  public void validateTimesheetLine(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Employee employee = user.getEmployee();

    ActionViewBuilder actionView =
        ActionView.define(I18n.get("See timesheet lines"))
            .model(TimesheetLine.class.getName())
            .add("grid", "timesheet-line-grid")
            .add("form", "timesheet-line-form")
            .context(
                "todayDate", Beans.get(AppBaseService.class).getTodayDate(user.getActiveCompany()));

    Beans.get(TimesheetService.class).createValidateDomainTimesheetLine(user, employee, actionView);

    response.setView(actionView.map());
  }

  public void editTimesheetSelected(ActionRequest request, ActionResponse response) {
    Map<?, ?> timesheetMap = (Map<?, ?>) request.getContext().get("timesheetSelect");
    Timesheet timesheet =
        Beans.get(TimesheetRepository.class).find(Long.valueOf((Integer) timesheetMap.get("id")));
    response.setView(
        ActionView.define("Timesheet")
            .model(Timesheet.class.getName())
            .add("form", "timesheet-form")
            .param("forceEdit", "true")
            .domain("self.id = " + timesheetMap.get("id"))
            .context("_showRecord", String.valueOf(timesheet.getId()))
            .map());
  }

  public void historicTimesheet(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Employee employee = user.getEmployee();

    ActionViewBuilder actionView =
        ActionView.define(I18n.get("Historic colleague Timesheets"))
            .model(Timesheet.class.getName())
            .add("grid", "timesheet-grid")
            .add("form", "timesheet-form")
            .param("search-filters", "timesheet-filters");

    actionView.domain("(self.statusSelect = 3 OR self.statusSelect = 4)");

    if (employee == null || !employee.getHrManager()) {
      actionView
          .domain(actionView.get().getDomain() + " AND self.user.employee.managerUser = :_user")
          .context("_user", user);
    }

    response.setView(actionView.map());
  }

  public void historicTimesheetLine(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Employee employee = user.getEmployee();

    ActionViewBuilder actionView =
        ActionView.define(I18n.get("See timesheet lines"))
            .model(TimesheetLine.class.getName())
            .add("grid", "timesheet-line-grid")
            .add("form", "timesheet-line-form");

    actionView
        .domain(
            "self.timesheet.company = :_activeCompany AND (self.timesheet.statusSelect = 3 OR self.timesheet.statusSelect = 4)")
        .context("_activeCompany", user.getActiveCompany());

    if (employee == null || !employee.getHrManager()) {
      actionView
          .domain(
              actionView.get().getDomain()
                  + " AND self.timesheet.user.employee.managerUser = :_user")
          .context("_user", user);
    }

    response.setView(actionView.map());
  }

  public void showSubordinateTimesheets(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Company activeCompany = user.getActiveCompany();

    ActionViewBuilder actionView =
        ActionView.define(I18n.get("Timesheets to be Validated by your subordinates"))
            .model(Timesheet.class.getName())
            .add("grid", "timesheet-grid")
            .add("form", "timesheet-form")
            .param("search-filters", "timesheet-filters");

    String domain =
        "self.user.employee.managerUser.employee.managerUser = :_user AND self.company = :_activeCompany AND self.statusSelect = 2";

    long nbTimesheets =
        Query.of(Timesheet.class)
            .filter(domain)
            .bind("_user", user)
            .bind("_activeCompany", activeCompany)
            .count();

    if (nbTimesheets == 0) {
      response.setNotify(I18n.get("No timesheet to be validated by your subordinates"));
    } else {
      response.setView(
          actionView
              .domain(domain)
              .context("_user", user)
              .context("_activeCompany", activeCompany)
              .map());
    }
  }

  public void cancel(ActionRequest request, ActionResponse response) {
    try {
      Timesheet timesheet = request.getContext().asType(Timesheet.class);
      timesheet = Beans.get(TimesheetRepository.class).find(timesheet.getId());

      Message message = Beans.get(TimesheetService.class).cancelAndSendCancellationEmail(timesheet);
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
   * Called from timesheet form view, on clicking "return to draft" button. <br>
   * Call {@link TimesheetService#draft(Timesheet)}
   *
   * @param request
   * @param response
   */
  public void draft(ActionRequest request, ActionResponse response) {
    try {
      Timesheet timesheet = request.getContext().asType(Timesheet.class);
      timesheet = Beans.get(TimesheetRepository.class).find(timesheet.getId());

      Beans.get(TimesheetService.class).draft(timesheet);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Action called when confirming a timesheet. Changing status + Sending mail to Manager
   *
   * @param request
   * @param response
   */
  public void confirm(ActionRequest request, ActionResponse response) {

    try {
      Timesheet timesheet = request.getContext().asType(Timesheet.class);
      timesheet = Beans.get(TimesheetRepository.class).find(timesheet.getId());

      Message message =
          Beans.get(TimesheetService.class).confirmAndSendConfirmationEmail(timesheet);

      if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
        response.setFlash(
            String.format(
                I18n.get("Email sent to %s"),
                Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
      }

      response.setReload(true);

    } catch (Exception e) {
      TraceBackService.trace(e);
      response.setError(e.getMessage());
    }
  }

  // Continue button
  public void continueBtn(ActionRequest request, ActionResponse response) {
    response.setView(
        ActionView.define(I18n.get("Timesheet"))
            .model(Timesheet.class.getName())
            .add("form", "timesheet-form")
            .add("grid", "timesheet-grid")
            .param("search-filters", "timesheet-filters")
            .domain("self.user = :_user")
            .context("_user", AuthUtils.getUser())
            .map());
  }

  // Confirm and continue button
  public void confirmContinue(ActionRequest request, ActionResponse response) {
    this.confirm(request, response);
    this.continueBtn(request, response);
  }

  /**
   * Action called when validating a timesheet. Changing status + Sending mail to Applicant
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void valid(ActionRequest request, ActionResponse response) throws AxelorException {

    try {
      Timesheet timesheet = request.getContext().asType(Timesheet.class);
      timesheet = Beans.get(TimesheetRepository.class).find(timesheet.getId());
      TimesheetService timesheetService = Beans.get(TimesheetService.class);

      timesheetService.checkEmptyPeriod(timesheet);

      computeTimeSpent(request, response);

      Message message = timesheetService.validateAndSendValidationEmail(timesheet);
      if (message != null && message.getStatusSelect() == MessageRepository.STATUS_SENT) {
        response.setFlash(
            String.format(
                I18n.get("Email sent to %s"),
                Beans.get(MessageServiceBaseImpl.class).getToRecipients(message)));
      }
      Beans.get(PeriodService.class)
          .checkPeriod(timesheet.getCompany(), timesheet.getToDate(), timesheet.getFromDate());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  // action called when refusing a timesheet. Changing status + Sending mail to Applicant
  public void refuse(ActionRequest request, ActionResponse response) throws AxelorException {

    try {
      Timesheet timesheet = request.getContext().asType(Timesheet.class);
      timesheet = Beans.get(TimesheetRepository.class).find(timesheet.getId());

      Message message = Beans.get(TimesheetService.class).refuseAndSendRefusalEmail(timesheet);
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

  public void computeTimeSpent(ActionRequest request, ActionResponse response) {
    Timesheet timesheet = request.getContext().asType(Timesheet.class);
    timesheet = Beans.get(TimesheetRepository.class).find(timesheet.getId());
    if (timesheet.getTimesheetLineList() != null && !timesheet.getTimesheetLineList().isEmpty()) {
      Beans.get(TimesheetService.class).computeTimeSpent(timesheet);
    }
  }

  /* Count Tags displayed on the menu items */
  @CallMethod
  public String timesheetValidateMenuTag() {

    return Beans.get(HRMenuTagService.class)
        .countRecordsTag(Timesheet.class, TimesheetRepository.STATUS_CONFIRMED);
  }

  public void printTimesheet(ActionRequest request, ActionResponse response)
      throws AxelorException {

    Timesheet timesheet = request.getContext().asType(Timesheet.class);

    String name = I18n.get("Timesheet") + " " + timesheet.getFullName().replace("/", "-");

    String fileLink =
        ReportFactory.createReport("hrTimesheet", timesheet.getCompany(), IReport.TIMESHEET, name)
            .addParam("TimesheetId", timesheet.getId())
            .addParam(
                "Timezone",
                timesheet.getCompany() != null ? timesheet.getCompany().getTimezone() : null)
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .toAttach(timesheet)
            .generate()
            .getFileLink();

    logger.debug("Printing {}", name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }

  public void setShowActivity(ActionRequest request, ActionResponse response) {

    Timesheet timesheet = request.getContext().asType(Timesheet.class);

    boolean showActivity = true;

    User user = timesheet.getUser();
    if (user != null) {
      Company company = user.getActiveCompany();
      if (company != null && company.getHrConfig() != null) {
        showActivity = !company.getHrConfig().getUseUniqueProductForTimesheet();
      }
    }

    response.setValue("$showActivity", showActivity);
  }

  public void openTimesheetEditor(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();

    String url =
        "hr/timesheet?timesheetId="
            + context.get("id")
            + "&showActivity="
            + context.get("showActivity");

    response.setView(
        ActionView.define(I18n.get("Timesheet lines"))
            .add("html", url)
            .param("popup", "reload")
            .param("popup-save", "false")
            .map());
  }

  public void timesheetPeriodTotalController(ActionRequest request, ActionResponse response) {
    try {
      Timesheet timesheet = request.getContext().asType(Timesheet.class);
      TimesheetService timesheetService = Beans.get(TimesheetService.class);

      BigDecimal periodTotal = timesheetService.computePeriodTotal(timesheet);

      response.setAttr("periodTotal", "value", periodTotal);
      response.setAttr("$periodTotalConvert", "hidden", false);
      response.setAttr(
          "$periodTotalConvert",
          "value",
          Beans.get(TimesheetLineService.class)
              .computeHoursDuration(timesheet, periodTotal, false));
      response.setAttr(
          "$periodTotalConvert", "title", timesheetService.getPeriodTotalConvertTitle(timesheet));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from timesheet form, on user change. Call {@link
   * TimesheetService#updateTimeLoggingPreference(Timesheet)} to update the timesheet, and update
   * the dummy field $periodTotalConvert
   *
   * @param request
   * @param response
   */
  public void updateTimeLoggingPreference(ActionRequest request, ActionResponse response) {
    try {
      Timesheet timesheet = request.getContext().asType(Timesheet.class);
      Beans.get(TimesheetService.class).updateTimeLoggingPreference(timesheet);
      response.setAttr("$periodTotalConvert", "hidden", false);
      response.setAttr(
          "$periodTotalConvert",
          "value",
          Beans.get(TimesheetLineService.class)
              .computeHoursDuration(timesheet, timesheet.getPeriodTotal(), false));
      response.setAttr(
          "$periodTotalConvert",
          "title",
          Beans.get(TimesheetService.class).getPeriodTotalConvertTitle(timesheet));
      response.setValue("timeLoggingPreferenceSelect", timesheet.getTimeLoggingPreferenceSelect());
      response.setValue("timesheetLineList", timesheet.getTimesheetLineList());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void generateLinesFromExpectedPlanning(ActionRequest request, ActionResponse response) {
    try {
      Timesheet timesheet = request.getContext().asType(Timesheet.class);
      timesheet = Beans.get(TimesheetRepository.class).find(timesheet.getId());
      Beans.get(TimesheetService.class).generateLinesFromExpectedProjectPlanning(timesheet);
      response.setReload(true);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e);
    }
  }

  public void removeAfterToDateTimesheetLines(ActionRequest request, ActionResponse response) {

    Timesheet timesheet = request.getContext().asType(Timesheet.class);
    if (timesheet.getTimesheetLineList() != null && !timesheet.getTimesheetLineList().isEmpty()) {
      Beans.get(TimesheetService.class).removeAfterToDateTimesheetLines(timesheet);
    }
    response.setValues(timesheet);
  }
}
