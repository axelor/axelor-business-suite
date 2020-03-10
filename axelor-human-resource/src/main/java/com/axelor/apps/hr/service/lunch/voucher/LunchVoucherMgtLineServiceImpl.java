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
package com.axelor.apps.hr.service.lunch.voucher;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.LunchVoucherAdvance;
import com.axelor.apps.hr.db.LunchVoucherMgt;
import com.axelor.apps.hr.db.LunchVoucherMgtLine;
import com.axelor.apps.hr.db.repo.LunchVoucherAdvanceRepository;
import com.axelor.apps.hr.db.repo.LunchVoucherMgtLineRepository;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.math.RoundingMode;
import java.util.List;

public class LunchVoucherMgtLineServiceImpl implements LunchVoucherMgtLineService {

  @Inject protected EmployeeService employeeService;

  /*
   * Create a new line from employee and lunchVoucherMgt
   */
  @Override
  public LunchVoucherMgtLine create(Employee employee, LunchVoucherMgt lunchVoucherMgt)
      throws AxelorException {
    LunchVoucherMgtLine lunchVoucherMgtLine = new LunchVoucherMgtLine();
    lunchVoucherMgtLine.setEmployee(employee);
    computeAllAttrs(employee, lunchVoucherMgt, lunchVoucherMgtLine);
    return lunchVoucherMgtLine;
  }

  /*
   * Try to set the line attributes: if an exception occurs, the line status
   * is anomaly.
   */
  @Override
  public void computeAllAttrs(
      Employee employee, LunchVoucherMgt lunchVoucherMgt, LunchVoucherMgtLine lunchVoucherMgtLine) {
    Integer lineStatus = LunchVoucherMgtLineRepository.STATUS_CALCULATED;
    try {
      lunchVoucherMgtLine.setInAdvanceNbr(computeEmployeeLunchVoucherAdvance(employee));
      lunchVoucherMgtLine.setDaysWorkedNbr(
          employeeService
              .getDaysWorkedInPeriod(
                  employee,
                  lunchVoucherMgt.getLeavePeriod().getFromDate(),
                  lunchVoucherMgt.getLeavePeriod().getToDate())
              .setScale(0, RoundingMode.HALF_UP)
              .intValue());
      compute(lunchVoucherMgtLine);
      fillLunchVoucherFormat(employee, lunchVoucherMgt, lunchVoucherMgtLine);
    } catch (Exception e) {
      TraceBackService.trace(e);
      lineStatus = LunchVoucherMgtLineRepository.STATUS_ANOMALY;
    }
    lunchVoucherMgtLine.setStatusSelect(lineStatus);
  }

  protected Integer computeEmployeeLunchVoucherAdvance(Employee employee) {
    int number = 0;
    List<LunchVoucherAdvance> list =
        Beans.get(LunchVoucherAdvanceRepository.class)
            .all()
            .filter(
                "self.employee.id = ?1 AND self.nbrLunchVouchersUsed < self.nbrLunchVouchers",
                employee.getId())
            .fetch();

    for (LunchVoucherAdvance item : list) {
      number += item.getNbrLunchVouchers() - item.getNbrLunchVouchersUsed();
    }

    return number;
  }

  @Override
  public void fillLunchVoucherFormat(
      Employee employee, LunchVoucherMgt lunchVoucherMgt, LunchVoucherMgtLine lunchVoucherMgtLine)
      throws AxelorException {
    int employeeFormat = employee.getLunchVoucherFormatSelect();
    if (employeeFormat != 0) {
      lunchVoucherMgtLine.setLunchVoucherFormatSelect(employeeFormat);
    } else {
      Company company = lunchVoucherMgt.getCompany();
      HRConfig hrConfig = Beans.get(HRConfigService.class).getHRConfig(company);
      lunchVoucherMgtLine.setLunchVoucherFormatSelect(hrConfig.getLunchVoucherFormatSelect());
    }
  }

  @Override
  public void compute(LunchVoucherMgtLine lunchVoucherMgtLine) throws AxelorException {
    Integer lunchVoucherNumber =
        lunchVoucherMgtLine.getDaysWorkedNbr()
            - (lunchVoucherMgtLine.getCanteenEntries()
                + lunchVoucherMgtLine.getDaysOverseas()
                + lunchVoucherMgtLine.getInAdvanceNbr()
                + lunchVoucherMgtLine.getInvitation());
    lunchVoucherMgtLine.setLunchVoucherNumber(Integer.max(lunchVoucherNumber, 0));
  }
}
