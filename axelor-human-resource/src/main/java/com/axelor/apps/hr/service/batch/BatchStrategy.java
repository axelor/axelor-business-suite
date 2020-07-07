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

import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.service.leave.management.LeaveManagementService;
import com.axelor.apps.hr.service.publicHoliday.PublicHolidayHrService;
import com.google.inject.Inject;

public abstract class BatchStrategy extends AbstractBatch {

  protected LeaveManagementService leaveManagementService;

  @Inject protected EmployeeRepository employeeRepository;

  @Inject protected PublicHolidayHrService publicHolidayService;

  public BatchStrategy(LeaveManagementService leaveManagementService) {
    super();
    this.leaveManagementService = leaveManagementService;
  }

  public BatchStrategy() {
    super();
  }

  protected void updateEmployee(Employee employee) {

    employee.addBatchSetItem(batchRepo.find(batch.getId()));

    incrementDone();
  }

  @Override
  public int getFetchLimit() {
    return batch.getHrBatch().getBatchFetchLimit() > 0
        ? batch.getHrBatch().getBatchFetchLimit()
        : super.getFetchLimit();
  }
}
