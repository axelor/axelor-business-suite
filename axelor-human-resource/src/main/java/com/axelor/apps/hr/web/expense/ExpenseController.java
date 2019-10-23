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
/*
< * Axelor Business Solutions
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
package com.axelor.apps.hr.web.expense;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.message.MessageServiceBaseImpl;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.Expense;
import com.axelor.apps.hr.db.ExpenseLine;
import com.axelor.apps.hr.db.ExtraHours;
import com.axelor.apps.hr.db.KilometricAllowParam;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.ExpenseRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.report.IReport;
import com.axelor.apps.hr.service.HRMenuTagService;
import com.axelor.apps.hr.service.HRMenuValidateService;
import com.axelor.apps.hr.service.KilometricService;
import com.axelor.apps.hr.service.app.AppHumanResourceService;
import com.axelor.apps.hr.service.expense.ExpenseService;
import com.axelor.apps.hr.service.user.UserHrService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.tool.StringTool;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author axelor */
@Singleton
public class ExpenseController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private Provider<HRMenuTagService> hrMenuTagServiceProvider;
  @Inject private Provider<ExpenseService> expenseServiceProvider;
  @Inject private Provider<AppBaseService> appBaseServiceProvider;
  @Inject private Provider<ExpenseRepository> expenseRepositoryProvider;

  @Inject UserHrService userHrService;
  @Inject ExpenseService expenseService;

  public void createAnalyticDistributionWithTemplate(ActionRequest request, ActionResponse response)
      throws AxelorException {
    ExpenseLine expenseLine = request.getContext().asType(ExpenseLine.class);

    expenseLine = expenseServiceProvider.get().createAnalyticDistributionWithTemplate(expenseLine);
    response.setValue("analyticMoveLineList", expenseLine.getAnalyticMoveLineList());
  }

  public void computeAnalyticDistribution(ActionRequest request, ActionResponse response)
      throws AxelorException {
    ExpenseLine expenseLine = request.getContext().asType(ExpenseLine.class);
    Expense expense = expenseLine.getExpense();
    if (expense == null) {
      setExpense(request, expenseLine);
    }
    if (Beans.get(AppAccountService.class).getAppAccount().getManageAnalyticAccounting()) {
      expenseLine = expenseServiceProvider.get().computeAnalyticDistribution(expenseLine);
      response.setValue("analyticMoveLineList", expenseLine.getAnalyticMoveLineList());
    }
  }

  public void editExpense(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Company activeCompany = user.getActiveCompany();

    List<Expense> expenseList =
        Beans.get(ExpenseRepository.class)
            .all()
            .filter(
                "self.user = ?1 AND self.company = ?2 AND self.statusSelect = 1 AND (self.multipleUsers is false OR self.multipleUsers is null)",
                user,
                activeCompany)
            .fetch();
    if (expenseList.isEmpty()) {
      response.setView(
          ActionView.define(I18n.get("Expense"))
              .model(Expense.class.getName())
              .add("form", "expense-form")
              .context("_payCompany", userHrService.getPayCompany(user))
              .map());
    } else if (expenseList.size() == 1) {
      response.setView(
          ActionView.define(I18n.get("Expense"))
              .model(Expense.class.getName())
              .add("form", "expense-form")
              .param("forceEdit", "true")
              .context("_showRecord", String.valueOf(expenseList.get(0).getId()))
              .map());
    } else {
      response.setView(
          ActionView.define(I18n.get("Expense"))
              .model(Wizard.class.getName())
              .add("form", "popup-expense-form")
              .param("forceEdit", "true")
              .param("popup", "true")
              .param("show-toolbar", "false")
              .param("show-confirm", "false")
              .param("forceEdit", "true")
              .param("popup-save", "false")
              .map());
    }
  }

  @SuppressWarnings("unchecked")
  public void editExpenseSelected(ActionRequest request, ActionResponse response) {
    Map<String, Object> expenseMap =
        (Map<String, Object>) request.getContext().get("expenseSelect");

    if (expenseMap == null) {
      response.setError(I18n.get(IExceptionMessage.EXPENSE_NOT_SELECTED));
      return;
    }
    Long expenseId = Long.valueOf((Integer) expenseMap.get("id"));
    response.setCanClose(true);
    response.setView(
        ActionView.define(I18n.get("Expense"))
            .model(Expense.class.getName())
            .add("form", "expense-form")
            .param("forceEdit", "true")
            .domain("self.id = " + expenseId)
            .context("_showRecord", expenseId)
            .map());
  }

  public void validateExpense(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Employee employee = user.getEmployee();

    ActionViewBuilder actionView =
        ActionView.define(I18n.get("Expenses to Validate"))
            .model(Expense.class.getName())
            .add("grid", "expense-validate-grid")
            .add("form", "expense-form");

    Beans.get(HRMenuValidateService.class).createValidateDomain(user, employee, actionView);

    response.setView(actionView.map());
  }

  public void historicExpense(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Employee employee = user.getEmployee();

    ActionViewBuilder actionView =
        ActionView.define(I18n.get("Historic colleague Expenses"))
            .model(Expense.class.getName())
            .add("grid", "expense-grid")
            .add("form", "expense-form");

    actionView
        .domain(
            "self.company = :_activeCompany AND (self.statusSelect = 3 OR self.statusSelect = 4)")
        .context("_activeCompany", user.getActiveCompany());

    if (employee == null || !employee.getHrManager()) {
      actionView
          .domain(actionView.get().getDomain() + " AND self.user.employee.managerUser = :_user")
          .context("_user", user);
    }

    response.setView(actionView.map());
  }

  public void showSubordinateExpenses(ActionRequest request, ActionResponse response) {

    User user = AuthUtils.getUser();
    Company activeCompany = user.getActiveCompany();

    ActionViewBuilder actionView =
        ActionView.define(I18n.get("Expenses to be Validated by your subordinates"))
            .model(Expense.class.getName())
            .add("grid", "expense-grid")
            .add("form", "expense-form");

    String domain =
        "self.user.employee.managerUser.employee.managerUser = :_user AND self.company = :_activeCompany AND self.statusSelect = 2";

    long nbExpenses =
        Query.of(ExtraHours.class)
            .filter(domain)
            .bind("_user", user)
            .bind("_activeCompany", activeCompany)
            .count();

    if (nbExpenses == 0) {
      response.setNotify(I18n.get("No expense to be validated by your subordinates"));
    } else {
      response.setView(
          actionView
              .domain(domain)
              .context("_user", user)
              .context("_activeCompany", activeCompany)
              .map());
    }
  }

  /**
   * Called from expense form, on expense lines change. Call {@link ExpenseService#compute(Expense)}
   *
   * @param request
   * @param response
   */
  public void compute(ActionRequest request, ActionResponse response) {
    Expense expense = request.getContext().asType(Expense.class);
    expense = expenseServiceProvider.get().compute(expense);
    response.setValues(expense);
  }

  public void updateMoveDateAndPeriod(ActionRequest request, ActionResponse response) {
    Expense expense = request.getContext().asType(Expense.class);
    expense = expenseServiceProvider.get().updateMoveDateAndPeriod(expense);
    response.setValue("moveDate", expense.getMoveDate());
    response.setValue("period", expense.getPeriod());
  }

  public void ventilate(ActionRequest request, ActionResponse response) throws AxelorException {
    try {
      Expense expense = request.getContext().asType(Expense.class);
      expense = Beans.get(ExpenseRepository.class).find(expense.getId());
      Move move = expenseServiceProvider.get().ventilate(expense);
      response.setReload(true);
      if (move != null) {
        response.setView(
            ActionView.define(I18n.get("Move"))
                .model(Move.class.getName())
                .add("grid", "move-grid")
                .add("form", "move-form")
                .context("_showRecord", String.valueOf(move.getId()))
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void printExpense(ActionRequest request, ActionResponse response) throws AxelorException {

    Expense expense = request.getContext().asType(Expense.class);

    String name = I18n.get("Expense") + " " + expense.getFullName().replace("/", "-");

    String fileLink =
        ReportFactory.createReport(IReport.EXPENSE, name)
            .addParam("ExpenseId", expense.getId())
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .toAttach(expense)
            .generate()
            .getFileLink();

    logger.debug("Printing {}", name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }

  /* Count Tags displayed on the menu items */

  public String expenseValidateMenuTag() {

    return hrMenuTagServiceProvider
        .get()
        .countRecordsTag(Expense.class, ExpenseRepository.STATUS_CONFIRMED);
  }

  public String expenseVentilateMenuTag() {
    Long total =
        JPA.all(Expense.class).filter("self.statusSelect = 3 AND self.ventilated = false").count();

    return String.format("%s", total);
  }

  public void cancel(ActionRequest request, ActionResponse response) throws AxelorException {
    try {
      Expense expense = request.getContext().asType(Expense.class);
      expense = expenseRepositoryProvider.get().find(expense.getId());
      ExpenseService expenseService = expenseServiceProvider.get();

      expenseService.cancel(expense);

      Message message = expenseService.sendCancellationEmail(expense);
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

  public void addPayment(ActionRequest request, ActionResponse response) {
    Expense expense = request.getContext().asType(Expense.class);
    expense = Beans.get(ExpenseRepository.class).find(expense.getId());
    try {
      expenseServiceProvider.get().addPayment(expense);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called on clicking cancelPaymentButton, call {@link ExpenseService#cancelPayment(Expense)}.
   *
   * @param request
   * @param response
   */
  public void cancelPayment(ActionRequest request, ActionResponse response) {
    Expense expense = request.getContext().asType(Expense.class);
    expense = Beans.get(ExpenseRepository.class).find(expense.getId());
    try {
      expenseServiceProvider.get().cancelPayment(expense);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  // sending expense and sending mail to manager
  public void send(ActionRequest request, ActionResponse response) throws AxelorException {

    try {
      Expense expense = request.getContext().asType(Expense.class);
      expense = expenseRepositoryProvider.get().find(expense.getId());
      ExpenseService expenseService = expenseServiceProvider.get();

      expenseService.confirm(expense);

      Message message = expenseService.sendConfirmationEmail(expense);
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

  public void newExpense(ActionResponse response) {

    response.setView(
        ActionView.define(I18n.get("Expense"))
            .model(Expense.class.getName())
            .add("form", "expense-form")
            .context("_payCompany", userHrService.getPayCompany(AuthUtils.getUser()))
            .map());
  }

  // validating expense and sending mail to applicant
  public void valid(ActionRequest request, ActionResponse response) throws AxelorException {

    try {
      Expense expense = request.getContext().asType(Expense.class);
      expense = expenseRepositoryProvider.get().find(expense.getId());
      ExpenseService expenseService = expenseServiceProvider.get();

      expenseService.validate(expense);

      Message message = expenseService.sendValidationEmail(expense);
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

  // refusing expense and sending mail to applicant
  public void refuse(ActionRequest request, ActionResponse response) throws AxelorException {

    try {
      Expense expense = request.getContext().asType(Expense.class);
      expense = expenseRepositoryProvider.get().find(expense.getId());
      ExpenseService expenseService = expenseServiceProvider.get();

      expenseService.refuse(expense);

      Message message = expenseService.sendRefusalEmail(expense);
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

  public void fillKilometricExpenseProduct(ActionRequest request, ActionResponse response)
      throws AxelorException {

    try {
      Expense expense = request.getContext().getParent().asType(Expense.class);
      Product expenseProduct = expenseServiceProvider.get().getKilometricExpenseProduct(expense);
      logger.debug("Get Kilometric expense product : {}", expenseProduct);
      response.setValue("expenseProduct", expenseProduct);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void validateAndCompute(ActionRequest request, ActionResponse response) {

    Expense expense = request.getContext().asType(Expense.class);

    List<Integer> expenseLineListId = new ArrayList<>();
    int compt = 0;
    for (ExpenseLine expenseLine : expenseService.getExpenseLineList(expense)) {
      compt++;
      if (expenseLine.getExpenseDate().isAfter(appBaseServiceProvider.get().getTodayDate())) {
        expenseLineListId.add(compt);
      }
    }
    try {
      if (!expenseLineListId.isEmpty()) {

        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get("Date can't be in the future for line(s) : %s"),
            expenseLineListId.stream().map(id -> id.toString()).collect(Collectors.joining(",")));
      }

    } catch (AxelorException e) {

      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }

    expenseService = expenseServiceProvider.get();

    response.setValue(
        "personalExpenseAmount", expenseService.computePersonalExpenseAmount(expense));
    response.setValue("advanceAmount", expenseService.computeAdvanceAmount(expense));

    if (expense.getKilometricExpenseLineList() != null
        && !expense.getKilometricExpenseLineList().isEmpty()) {
      response.setValue("kilometricExpenseLineList", expense.getKilometricExpenseLineList());
    }

    compute(request, response);
  }

  public void computeKilometricExpense(ActionRequest request, ActionResponse response)
      throws AxelorException {

    ExpenseLine expenseLine = request.getContext().asType(ExpenseLine.class);
    if (expenseLine.getKilometricAllowParam() == null
        || expenseLine.getDistance().compareTo(BigDecimal.ZERO) == 0
        || expenseLine.getExpenseDate() == null) {
      return;
    }

    String userId;
    String userName;
    if (expenseLine.getExpense() != null) {
      setExpense(request, expenseLine);
    }
    Expense expense = expenseLine.getExpense();

    if (expense != null && expenseLine.getUser() != null) {
      userId = expense.getUser().getId().toString();
      userName = expense.getUser().getFullName();
    } else {
      userId = request.getContext().getParent().asType(Expense.class).getUser().getId().toString();
      userName = request.getContext().getParent().asType(Expense.class).getUser().getFullName();
    }
    Employee employee =
        Beans.get(EmployeeRepository.class).all().filter("self.user.id = ?1", userId).fetchOne();

    if (employee == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          userName);
    }

    BigDecimal amount = BigDecimal.ZERO;
    try {
      amount = Beans.get(KilometricService.class).computeKilometricExpense(expenseLine, employee);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }

    response.setValue("totalAmount", amount);
    response.setValue("untaxedAmount", amount);
  }

  public void updateKAPOfKilometricAllowance(ActionRequest request, ActionResponse response)
      throws AxelorException {
    ExpenseLine expenseLine = request.getContext().asType(ExpenseLine.class);

    if (expenseLine.getExpense() == null) {
      setExpense(request, expenseLine);
    }

    try {
      List<KilometricAllowParam> kilometricAllowParamList =
          expenseServiceProvider.get().getListOfKilometricAllowParamVehicleFilter(expenseLine);
      if (kilometricAllowParamList == null || kilometricAllowParamList.isEmpty()) {
        response.setAttr("kilometricAllowParam", "domain", "self.id IN (0)");
      } else {
        response.setAttr(
            "kilometricAllowParam",
            "domain",
            "self.id IN (" + StringTool.getIdListString(kilometricAllowParamList) + ")");
      }

      KilometricAllowParam currentKilometricAllowParam = expenseLine.getKilometricAllowParam();
      boolean vehicleOk = false;

      if (kilometricAllowParamList != null && kilometricAllowParamList.size() == 1) {
        response.setValue("kilometricAllowParam", kilometricAllowParamList.get(0));
      } else if (kilometricAllowParamList != null) {
        for (KilometricAllowParam kilometricAllowParam : kilometricAllowParamList) {
          if (currentKilometricAllowParam != null
              && currentKilometricAllowParam.equals(kilometricAllowParam)) {
            expenseLine.setKilometricAllowParam(kilometricAllowParam);
            vehicleOk = true;
            break;
          }
        }
        if (!vehicleOk) {
          response.setValue("kilometricAllowParam", null);
        } else {
          response.setValue("kilometricAllowParam", expenseLine.getKilometricAllowParam());
        }
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  private void setExpense(ActionRequest request, ExpenseLine expenseLine) {

    Context parent = request.getContext().getParent();

    if (parent != null && parent.get("_model").equals(Expense.class.getName())) {
      expenseLine.setExpense(parent.asType(Expense.class));
    }
  }

  public void domainOnSelectOnKAP(ActionRequest request, ActionResponse response)
      throws AxelorException {

    ExpenseLine expenseLine = request.getContext().asType(ExpenseLine.class);

    if (expenseLine.getExpense() == null) {
      setExpense(request, expenseLine);
    }

    try {
      List<KilometricAllowParam> kilometricAllowParamList =
          expenseServiceProvider.get().getListOfKilometricAllowParamVehicleFilter(expenseLine);
      response.setAttr(
          "kilometricAllowParam",
          "domain",
          "self.id IN (" + StringTool.getIdListString(kilometricAllowParamList) + ")");
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void computeDistanceAndKilometricExpense(ActionRequest request, ActionResponse response)
      throws AxelorException {

    // Compute distance.

    if (!Beans.get(AppHumanResourceService.class)
        .getAppExpense()
        .getComputeDistanceWithWebService()) {
      return;
    }

    Context context = request.getContext();
    ExpenseLine expenseLine = context.asType(ExpenseLine.class);

    if (Strings.isNullOrEmpty(expenseLine.getFromCity())
        || Strings.isNullOrEmpty(expenseLine.getToCity())) {
      return;
    }

    KilometricService kilometricService = Beans.get(KilometricService.class);
    BigDecimal distance = kilometricService.computeDistance(expenseLine);
    expenseLine.setDistance(distance);
    response.setValue("distance", distance);

    // Compute kilometric expense.

    if (expenseLine.getKilometricAllowParam() == null
        || expenseLine.getExpenseDate() == null
        || expenseLine.getKilometricTypeSelect() == 0) {
      return;
    }

    Expense expense = expenseLine.getExpense();

    if (expense == null) {
      expense = context.getParent().asType(Expense.class);
    }

    Employee employee = expense.getUser().getEmployee();

    if (employee == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          expense.getUser().getName());
    }

    BigDecimal amount = kilometricService.computeKilometricExpense(expenseLine, employee);
    response.setValue("totalAmount", amount);
    response.setValue("untaxedAmount", amount);
  }
}
