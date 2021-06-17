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
package com.axelor.apps.hr.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.service.PeriodService;
import com.axelor.apps.hr.db.EmployeeBonusMgt;
import com.axelor.apps.hr.db.repo.EmployeeBonusMgtRepository;
import com.axelor.apps.hr.report.IReport;
import com.axelor.apps.hr.service.EmployeeBonusService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class EmployeeBonusController {

  public void compute(ActionRequest request, ActionResponse response) {
    EmployeeBonusMgt employeeBonusMgt = request.getContext().asType(EmployeeBonusMgt.class);
    PeriodService periodService = Beans.get(PeriodService.class);
    try {
      employeeBonusMgt = Beans.get(EmployeeBonusMgtRepository.class).find(employeeBonusMgt.getId());
      Beans.get(EmployeeBonusService.class).compute(employeeBonusMgt);
      response.setReload(true);
      periodService.checkPeriod(employeeBonusMgt.getPayPeriod());
      periodService.checkPeriod(employeeBonusMgt.getLeavePeriod());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void print(ActionRequest request, ActionResponse response) throws AxelorException {

    EmployeeBonusMgt bonus =
        Beans.get(EmployeeBonusMgtRepository.class)
            .find(request.getContext().asType(EmployeeBonusMgt.class).getId());

    String name =
        I18n.get("Employee bonus management") + " :  " + bonus.getEmployeeBonusType().getLabel();

    String fileLink =
        ReportFactory.createReport(
                "hrEmployeeBonusManagement",
                bonus.getCompany(),
                IReport.EMPLOYEE_BONUS_MANAGEMENT,
                name)
            .addParam("EmployeeBonusMgtId", bonus.getId())
            .addParam(
                "Timezone", bonus.getCompany() != null ? bonus.getCompany().getTimezone() : null)
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .toAttach(bonus)
            .generate()
            .getFileLink();

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }
}
