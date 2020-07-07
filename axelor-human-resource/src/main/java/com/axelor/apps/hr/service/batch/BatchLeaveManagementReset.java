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
import com.axelor.apps.hr.db.LeaveLine;
import com.axelor.apps.hr.db.LeaveReason;
import com.axelor.apps.hr.db.repo.LeaveLineRepository;
import com.axelor.apps.hr.db.repo.LeaveManagementRepository;
import com.axelor.apps.hr.service.leave.management.LeaveManagementService;
import com.axelor.auth.AuthUtils;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.List;

public class BatchLeaveManagementReset extends BatchLeaveManagement {

  @Inject
  public BatchLeaveManagementReset(
      LeaveManagementService leaveManagementService,
      LeaveLineRepository leaveLineRepository,
      LeaveManagementRepository leaveManagementRepository) {
    super(leaveManagementService, leaveLineRepository, leaveManagementRepository);
  }

  @Override
  protected void process() {
    List<Employee> employeeList = null;
    int fetchLimit = getFetchLimit();
    Query<Employee> query = getEmployees(batch.getHrBatch());
    int offset = 0;
    while (!(employeeList = query.fetch(fetchLimit, offset)).isEmpty()) {
      resetLeaveManagementLines(employeeList);
      offset += employeeList.size();
    }
  }

  public void resetLeaveManagementLines(List<Employee> employeeList) {
    for (Employee employee : employeeList) {
      try {
        resetLeaveManagement(employeeRepository.find(employee.getId()));
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
  public void resetLeaveManagement(Employee employee) throws AxelorException {
    LeaveReason leaveReason = batch.getHrBatch().getLeaveReason();
    for (LeaveLine leaveLine : employee.getLeaveLineList()) {
      if (leaveReason.equals(leaveLine.getLeaveReason())) {
        leaveManagementService.reset(
            leaveLine,
            AuthUtils.getUser(),
            batch.getHrBatch().getComments(),
            null,
            batch.getHrBatch().getStartDate(),
            batch.getHrBatch().getEndDate());
      }
    }
  }
}
