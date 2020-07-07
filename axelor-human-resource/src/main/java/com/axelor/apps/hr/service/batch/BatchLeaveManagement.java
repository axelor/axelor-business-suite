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
package com.axelor.apps.hr.service.batch;

import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.HrBatch;
import com.axelor.apps.hr.db.LeaveLine;
import com.axelor.apps.hr.db.LeaveManagement;
import com.axelor.apps.hr.db.LeaveReason;
import com.axelor.apps.hr.db.repo.LeaveLineRepository;
import com.axelor.apps.hr.db.repo.LeaveManagementRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.leave.LeaveService;
import com.axelor.apps.hr.service.leave.management.LeaveManagementService;
import com.axelor.auth.AuthUtils;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import javax.validation.constraints.Digits;

public class BatchLeaveManagement extends BatchStrategy {

  int total;
  int noValueAnomaly;
  int confAnomaly;

  protected LeaveLineRepository leaveLineRepository;
  protected LeaveManagementRepository leaveManagementRepository;

  @Inject private Provider<LeaveService> leaveServiceProvider;

  @Inject
  public BatchLeaveManagement(
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
        || batch.getHrBatch().getDayNumber().signum() == 0
        || batch.getHrBatch().getLeaveReason() == null) {
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.BATCH_MISSING_FIELD)),
          ExceptionOriginRepository.LEAVE_MANAGEMENT,
          batch.getId());
    }
    total = 0;
    noValueAnomaly = 0;
    confAnomaly = 0;
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

    List<String> query = Lists.newArrayList();
    Query<Employee> employeeQuery;

    if (hrBatch.getEmployeeSet() != null && !hrBatch.getEmployeeSet().isEmpty()) {
      String employeeIds =
          Joiner.on(',')
              .join(Iterables.transform(hrBatch.getEmployeeSet(), obj -> obj.getId().toString()));
      query.add("self.id IN (" + employeeIds + ")");
    }
    if (hrBatch.getEmployeeSet() != null && !hrBatch.getPlanningSet().isEmpty()) {
      String planningIds =
          Joiner.on(',')
              .join(Iterables.transform(hrBatch.getPlanningSet(), obj -> obj.getId().toString()));

      query.add("self.weeklyPlanning.id IN (" + planningIds + ")");
    }

    String liaison = query.isEmpty() ? "" : " AND";
    if (hrBatch.getCompany() != null) {
      employeeQuery =
          JPA.all(Employee.class)
              .filter(
                  Joiner.on(" AND ").join(query)
                      + liaison
                      + " self.mainEmploymentContract.payCompany = :company")
              .bind("company", hrBatch.getCompany());
    } else {
      employeeQuery = JPA.all(Employee.class).filter(Joiner.on(" AND ").join(query));
    }

    return employeeQuery;
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
    LeaveLine leaveLine = null;
    LeaveReason leaveReason = batch.getHrBatch().getLeaveReason();

    if (employee != null) {
      leaveLine = leaveServiceProvider.get().addLeaveReasonOrCreateIt(employee, leaveReason);

      BigDecimal dayNumber =
          batch.getHrBatch().getUseWeeklyPlanningCoef()
              ? batch
                  .getHrBatch()
                  .getDayNumber()
                  .multiply(employee.getWeeklyPlanning().getLeaveCoef())
              : batch.getHrBatch().getDayNumber();
      dayNumber =
          dayNumber.subtract(
              new BigDecimal(
                  publicHolidayService.getImposedDayNumber(
                      employee,
                      batch.getHrBatch().getStartDate(),
                      batch.getHrBatch().getEndDate())));
      LeaveManagement leaveManagement =
          leaveManagementService.createLeaveManagement(
              leaveLine,
              AuthUtils.getUser(),
              batch.getHrBatch().getComments(),
              null,
              batch.getHrBatch().getStartDate(),
              batch.getHrBatch().getEndDate(),
              dayNumber);
      BigDecimal qty = leaveLine.getQuantity().add(dayNumber);
      BigDecimal totalQty = leaveLine.getTotalQuantity().add(dayNumber);

      try {
        int integer =
            LeaveLine.class.getDeclaredField("quantity").getAnnotation(Digits.class).integer();
        BigDecimal limit = new BigDecimal((long) Math.pow(10, integer));
        if (qty.compareTo(limit) >= 0 || totalQty.compareTo(limit) >= 0) {
          throw new AxelorException(
              employee,
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.BATCH_LEAVE_MANAGEMENT_QTY_OUT_OF_BOUNDS),
              limit.longValue());
        }

      } catch (NoSuchFieldException | SecurityException e) {
        throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
      }

      leaveLine.setQuantity(qty.setScale(4, RoundingMode.HALF_EVEN));
      leaveLine.setTotalQuantity(totalQty.setScale(4, RoundingMode.HALF_EVEN));

      leaveManagementRepository.save(leaveManagement);
      leaveLineRepository.save(leaveLine);
      updateEmployee(employee);
    }
  }

  @Override
  protected void stop() {

    String comment =
        String.format(I18n.get(IExceptionMessage.BATCH_LEAVE_MANAGEMENT_ENDING_0), total) + '\n';

    comment +=
        String.format(I18n.get(IExceptionMessage.BATCH_LEAVE_MANAGEMENT_ENDING_1), batch.getDone())
            + '\n';

    if (confAnomaly > 0) {
      comment +=
          String.format(I18n.get(IExceptionMessage.BATCH_LEAVE_MANAGEMENT_ENDING_2), confAnomaly)
              + '\n';
    }
    if (noValueAnomaly > 0) {
      comment +=
          String.format(I18n.get(IExceptionMessage.BATCH_LEAVE_MANAGEMENT_ENDING_3), noValueAnomaly)
              + '\n';
    }

    addComment(comment);
    super.stop();
  }
}
