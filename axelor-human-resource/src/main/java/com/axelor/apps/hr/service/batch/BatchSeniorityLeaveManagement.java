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
/**
 * Axelor Business Solutions
 *
 * <p>Copyright (C) 2016 Axelor (<http://axelor.com>).
 *
 * <p>This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License, version 3, as published by the Free Software Foundation.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Affero General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.hr.service.batch;

import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.EmploymentContract;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.HrBatch;
import com.axelor.apps.hr.db.LeaveLine;
import com.axelor.apps.hr.db.LeaveManagement;
import com.axelor.apps.hr.db.LeaveManagementBatchRule;
import com.axelor.apps.hr.db.repo.HRConfigRepository;
import com.axelor.apps.hr.db.repo.LeaveLineRepository;
import com.axelor.apps.hr.db.repo.LeaveManagementRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.apps.hr.service.leave.management.LeaveManagementService;
import com.axelor.auth.AuthUtils;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.tool.template.TemplateMaker;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

public class BatchSeniorityLeaveManagement extends BatchStrategy {

  int total;
  int noValueAnomaly;
  int confAnomaly;

  private static final char TEMPLATE_DELIMITER = '$';
  protected TemplateMaker maker;
  protected HRConfig hrConfig;

  protected LeaveLineRepository leaveLineRepository;
  protected LeaveManagementRepository leaveManagementRepository;

  @Inject
  public BatchSeniorityLeaveManagement(
      LeaveManagementService leaveManagementService,
      LeaveLineRepository leaveLineRepository,
      LeaveManagementRepository leaveManagementRepository) {

    super(leaveManagementService);
    this.leaveLineRepository = leaveLineRepository;
    this.leaveManagementRepository = leaveManagementRepository;
  }

  @Override
  protected void start() throws IllegalAccessException {

    super.start();

    if (batch.getHrBatch().getDayNumber() == null
        || batch.getHrBatch().getDayNumber() == BigDecimal.ZERO
        || batch.getHrBatch().getLeaveReason() == null)
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.BATCH_MISSING_FIELD)),
          ExceptionOriginRepository.LEAVE_MANAGEMENT,
          batch.getId());
    total = 0;
    noValueAnomaly = 0;
    confAnomaly = 0;
    this.maker = new TemplateMaker(Locale.FRENCH, TEMPLATE_DELIMITER, TEMPLATE_DELIMITER);
    hrConfig =
        Beans.get(HRConfigRepository.class)
            .all()
            .filter("self.company.id = ?1 ", batch.getHrBatch().getCompany().getId())
            .fetchOne();
    checkPoint();
  }

  @Override
  protected void process() {

    List<Employee> employeeList = null;
    int fetchLimit = getFetchLimit();
    Query<Employee> query = this.getEmployees(batch.getHrBatch());
    int offset = 0;
    while (!(employeeList = query.fetch(fetchLimit, offset)).isEmpty()) {
      generateLeaveManagementLines(employeeList);
      offset += employeeList.size();
    }
  }

  public Query<Employee> getEmployees(HrBatch hrBatch) {

    Query<Employee> query;
    if (hrBatch.getCompany() != null) {
      query =
          JPA.all(Employee.class)
              .filter("self.mainEmploymentContract.payCompany = :company")
              .bind("company", hrBatch.getCompany());
    } else {
      query = JPA.all(Employee.class);
    }
    return query;
  }

  public void generateLeaveManagementLines(List<Employee> employeeList) {

    for (Employee employee : employeeList) {
      try {
        createLeaveManagement(employeeRepository.find(employee.getId()));
      } catch (AxelorException e) {
        TraceBackService.trace(e, ExceptionOriginRepository.LEAVE_MANAGEMENT, batch.getId());
        incrementAnomaly();
        if (e.getCategory() == TraceBackRepository.CATEGORY_NO_VALUE) {
          noValueAnomaly++;
        }
        if (e.getCategory() == TraceBackRepository.CATEGORY_CONFIGURATION_ERROR) {
          confAnomaly++;
        }
      } finally {
        total++;
        JPA.clear();
      }
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public void createLeaveManagement(Employee employee) throws AxelorException {

    batch = batchRepo.find(batch.getId());
    int count = 0;
    String eval = null;
    LeaveLine leaveLine = null;
    BigDecimal quantity = BigDecimal.ZERO;

    if (!employee.getLeaveLineList().isEmpty()) {
      for (LeaveLine line : employee.getLeaveLineList()) {

        if (line.getLeaveReason().equals(batch.getHrBatch().getLeaveReason())) {
          count++;
          leaveLine = line;
        }
      }
    }

    if (count == 0) {
      throw new AxelorException(
          employee,
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.EMPLOYEE_NO_LEAVE_MANAGEMENT),
          employee.getName(),
          batch.getHrBatch().getLeaveReason().getLeaveReason());
    }
    if (count > 1) {
      throw new AxelorException(
          employee,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.EMPLOYEE_DOUBLE_LEAVE_MANAGEMENT),
          employee.getName(),
          batch.getHrBatch().getLeaveReason().getLeaveReason());
    }
    if (count == 1) {

      EmploymentContract contract = employee.getMainEmploymentContract();
      if (contract == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE,
            IExceptionMessage.EMPLOYEE_CONTRACT_OF_EMPLOYMENT);
      }
      Integer executiveStatusSelect = contract.getExecutiveStatusSelect();

      for (LeaveManagementBatchRule rule :
          Beans.get(HRConfigRepository.class)
              .all()
              .filter("self.company.id = ?1", batch.getHrBatch().getCompany().getId())
              .fetchOne()
              .getLeaveManagementBatchRuleList()) {

        if (rule.getExecutiveStatusSelect().equals(executiveStatusSelect)) {
          maker.setContext(employee, "Employee");
          String formula = rule.getFormula();
          formula =
              formula.replace(
                  hrConfig.getSeniorityVariableName(),
                  String.valueOf(
                      Beans.get(EmployeeService.class)
                          .getLengthOfService(employee, batch.getHrBatch().getReferentialDate())));
          formula =
              formula.replace(
                  hrConfig.getAgeVariableName(),
                  String.valueOf(
                      Beans.get(EmployeeService.class)
                          .getAge(employee, batch.getHrBatch().getReferentialDate())));
          maker.setTemplate(formula);
          eval = maker.make();
          CompilerConfiguration conf = new CompilerConfiguration();
          ImportCustomizer customizer = new ImportCustomizer();
          customizer.addStaticStars("java.lang.Math");
          conf.addCompilationCustomizers(customizer);
          Binding binding = new Binding();
          GroovyShell shell = new GroovyShell(binding, conf);
          if (shell.evaluate(eval).toString().equals("true")) {
            quantity = rule.getLeaveDayNumber();
            break;
          }
        }
      }
      if (quantity.signum() == 0) {
        // If the quantity equals 0, no need to create a leaveManagement and to update the employee,
        // since we won't give them any leaves
        incrementDone();
        return;
      }
      LeaveManagement leaveManagement =
          leaveManagementService.createLeaveManagement(
              leaveLine,
              AuthUtils.getUser(),
              batch.getHrBatch().getComments(),
              null,
              batch.getHrBatch().getStartDate(),
              batch.getHrBatch().getEndDate(),
              quantity);
      leaveLine.setQuantity(leaveLine.getQuantity().add(quantity));
      leaveLine.setTotalQuantity(leaveLine.getTotalQuantity().add(quantity));
      leaveManagementRepository.save(leaveManagement);
      leaveLineRepository.save(leaveLine);
      updateEmployee(employee);
    }
  }

  @Override
  protected void stop() {

    String comment =
        String.format(I18n.get(IExceptionMessage.BATCH_LEAVE_MANAGEMENT_ENDING_0) + '\n', total);

    comment +=
        String.format(
            I18n.get(IExceptionMessage.BATCH_LEAVE_MANAGEMENT_ENDING_1) + '\n', batch.getDone());

    if (confAnomaly > 0) {
      comment +=
          String.format(
              I18n.get(IExceptionMessage.BATCH_LEAVE_MANAGEMENT_ENDING_2) + '\n', confAnomaly);
    }
    if (noValueAnomaly > 0) {
      comment +=
          String.format(
              I18n.get(IExceptionMessage.BATCH_LEAVE_MANAGEMENT_ENDING_3) + '\n', noValueAnomaly);
    }

    addComment(comment);
    super.stop();
  }
}
