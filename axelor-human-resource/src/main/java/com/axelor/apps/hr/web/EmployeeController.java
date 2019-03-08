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
package com.axelor.apps.hr.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.report.IReport;
import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import wslite.json.JSONException;
import wslite.json.JSONObject;

@Singleton
public class EmployeeController {

  @Inject private EmployeeService employeeService;

  public void showAnnualReport(ActionRequest request, ActionResponse response)
      throws JSONException, NumberFormatException, AxelorException {

    String employeeId = request.getContext().get("_id").toString();
    String year = request.getContext().get("year").toString();
    int yearId = new JSONObject(year).getInt("id");
    String yearName = new JSONObject(year).getString("name");
    User user = AuthUtils.getUser();

    String name =
        I18n.get("Annual expenses report") + " :  " + user.getFullName() + " (" + yearName + ")";

    String fileLink =
        ReportFactory.createReport(
                "hrEmployeeAnnualReport",
                user.getActiveCompany(),
                IReport.EMPLOYEE_ANNUAL_REPORT,
                name)
            .addParam("EmployeeId", Long.valueOf(employeeId))
            .addParam("YearId", Long.valueOf(yearId))
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .toAttach(Beans.get(EmployeeRepository.class).find(Long.valueOf(employeeId)))
            .generate()
            .getFileLink();

    response.setView(ActionView.define(name).add("html", fileLink).map());

    response.setCanClose(true);
  }

  public void setEmployeeSocialNetworkUrl(ActionRequest request, ActionResponse response) {

    Employee employee = request.getContext().asType(Employee.class);
    Map<String, String> urlMap =
        employeeService.getSocialNetworkUrl(
            employee.getContactPartner().getName(), employee.getContactPartner().getFirstName());
    response.setAttr("contactPartner.googleLabel", "title", urlMap.get("google"));
    response.setAttr("contactPartner.facebookLabel", "title", urlMap.get("facebook"));
    response.setAttr("contactPartner.twitterLabel", "title", urlMap.get("twitter"));
    response.setAttr("contactPartner.linkedinLabel", "title", urlMap.get("linkedin"));
    response.setAttr("contactPartner.youtubeLabel", "title", urlMap.get("youtube"));
  }

  public void setContactSocialNetworkUrl(ActionRequest request, ActionResponse response) {

    Partner partnerContact = request.getContext().asType(Partner.class);
    Map<String, String> urlMap =
        employeeService.getSocialNetworkUrl(
            partnerContact.getName(), partnerContact.getFirstName());
    response.setAttr("googleLabel", "title", urlMap.get("google"));
    response.setAttr("facebookLabel", "title", urlMap.get("facebook"));
    response.setAttr("twitterLabel", "title", urlMap.get("twitter"));
    response.setAttr("linkedinLabel", "title", urlMap.get("linkedin"));
    response.setAttr("youtubeLabel", "title", urlMap.get("youtube"));
  }
}
