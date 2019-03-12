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
package com.axelor.apps.hr.service;

import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.EmployeeAdvance;
import com.axelor.apps.hr.db.EmployeeAdvanceUsage;
import com.axelor.apps.hr.db.Expense;
import com.axelor.apps.hr.db.repo.EmployeeAdvanceRepository;
import com.axelor.apps.hr.db.repo.EmployeeAdvanceUsageRepository;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.List;

public class EmployeeAdvanceService {

  @Inject private EmployeeAdvanceRepository employeeAdvanceRepository;

  @Inject private EmployeeAdvanceUsageRepository employeeAdvanceUsageRepository;

  @Transactional
  public void fillExpenseWithAdvances(Expense expense) {

    Employee employee = Beans.get(EmployeeRepository.class).find(expense.getEmployee().getId());

    List<EmployeeAdvance> advanceList =
        employeeAdvanceRepository
            .all()
            .filter(
                "self.employee.id = ?1 AND self.remainingAmount > 0 AND self.date < ?2 AND self.statusSelect = ?3 AND self.typeSelect = ?4",
                employee.getId(),
                expense.getPeriod().getToDate(),
                EmployeeAdvanceRepository.STATUS_VALIDATED,
                EmployeeAdvanceRepository.TYPE_OCCASIONAL)
            .fetch();

    if (advanceList != null && !advanceList.isEmpty()) {

      BigDecimal currentAmountToRefund =
          expense
              .getInTaxTotal()
              .subtract(expense.getPersonalExpenseAmount())
              .subtract(expense.getWithdrawnCash());

      for (EmployeeAdvance advance : advanceList) {

        if (currentAmountToRefund.signum() == 0) {
          break;
        }

        currentAmountToRefund = withdrawFromAdvance(advance, expense, currentAmountToRefund);
        employeeAdvanceRepository.save(advance);
      }
      expense.setAdvanceAmount(
          expense
              .getInTaxTotal()
              .subtract(currentAmountToRefund)
              .subtract(expense.getPersonalExpenseAmount())
              .subtract(expense.getWithdrawnCash()));
    }
  }

  public BigDecimal withdrawFromAdvance(
      EmployeeAdvance employeeAdvance, Expense expense, BigDecimal maxAmount) {

    if (maxAmount.compareTo(employeeAdvance.getRemainingAmount()) > 0) {
      maxAmount = maxAmount.subtract(employeeAdvance.getRemainingAmount());
      createEmployeeAdvanceUsage(employeeAdvance, expense, employeeAdvance.getRemainingAmount());
      employeeAdvance.setRemainingAmount(BigDecimal.ZERO);

    } else {
      createEmployeeAdvanceUsage(employeeAdvance, expense, maxAmount);
      employeeAdvance.setRemainingAmount(employeeAdvance.getRemainingAmount().subtract(maxAmount));
      maxAmount = BigDecimal.ZERO;
    }

    return maxAmount;
  }

  @Transactional
  public void createEmployeeAdvanceUsage(
      EmployeeAdvance employeeAdvance, Expense expense, BigDecimal amount) {

    EmployeeAdvanceUsage usage = new EmployeeAdvanceUsage();

    usage.setEmployeeAdvance(employeeAdvance);
    usage.setExpense(expense);
    usage.setUsedAmount(amount);
    employeeAdvanceUsageRepository.save(usage);
  }
}
