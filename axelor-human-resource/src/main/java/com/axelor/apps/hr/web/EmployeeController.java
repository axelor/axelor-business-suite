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

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.hr.db.DPAE;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.report.IReport;
import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
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
import com.google.inject.Singleton;

import wslite.json.JSONException;
import wslite.json.JSONObject;

@Singleton
public class EmployeeController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
        ReportFactory.createReport(IReport.EMPLOYEE_ANNUAL_REPORT, name)
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
        Beans.get(EmployeeService.class)
            .getSocialNetworkUrl(
                employee.getContactPartner().getName(),
                employee.getContactPartner().getFirstName());
    response.setAttr("contactPartner.facebookLabel", "title", urlMap.get("facebook"));
    response.setAttr("contactPartner.twitterLabel", "title", urlMap.get("twitter"));
    response.setAttr("contactPartner.linkedinLabel", "title", urlMap.get("linkedin"));
    response.setAttr("contactPartner.youtubeLabel", "title", urlMap.get("youtube"));
  }

  public void setContactSocialNetworkUrl(ActionRequest request, ActionResponse response) {

    Partner partnerContact = request.getContext().asType(Partner.class);
    Map<String, String> urlMap =
        Beans.get(EmployeeService.class)
            .getSocialNetworkUrl(partnerContact.getName(), partnerContact.getFirstName());
    response.setAttr("facebookLabel", "title", urlMap.get("facebook"));
    response.setAttr("twitterLabel", "title", urlMap.get("twitter"));
    response.setAttr("linkedinLabel", "title", urlMap.get("linkedin"));
    response.setAttr("youtubeLabel", "title", urlMap.get("youtube"));
  }

  public void printEmployeePhonebook(ActionRequest request, ActionResponse response)
      throws AxelorException {

    User user = AuthUtils.getUser();

    String name = I18n.get("Employee PhoneBook");

    String fileLink =
        ReportFactory.createReport(IReport.EMPLOYEE_PHONEBOOK, name + "-${date}")
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .addParam("UserId", user.getId())
            .generate()
            .getFileLink();

    LOG.debug("Printing " + name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }

  public void generateNewDPAE(ActionRequest request, ActionResponse response) {
    Employee employee = request.getContext().asType(Employee.class);
    employee = Beans.get(EmployeeRepository.class).find(employee.getId());

    try {
      Long dpaeId = Beans.get(EmployeeService.class).generateNewDPAE(employee);

      ActionViewBuilder builder =
          ActionView.define(I18n.get("DPAE"))
              .model(DPAE.class.getName())
              .add("grid", "dpae-grid")
              .add("form", "dpae-form")
              .context("_showRecord", dpaeId);
      response.setView(builder.map());
    } catch (AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }

    response.setReload(true);
  }

  @SuppressWarnings("unchecked")
  public void generateDPAE(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();

    try {
      EmployeeService employeeService = Beans.get(EmployeeService.class);

      if (!ObjectUtils.isEmpty(request.getContext().get("_ids"))) {

        List<Long> ids =
            (List)
                (((List) context.get("_ids"))
                    .stream()
                    .filter(ObjectUtils::notEmpty)
                    .map(input -> Long.parseLong(input.toString()))
                    .collect(Collectors.toList()));
        List<Long> idsError = employeeService.generateDPAEs(ids);
        if (idsError.isEmpty()) {
          response.setFlash(
              String.format(I18n.get(IExceptionMessage.DPAE_PRINTS_SUCCESSFUL), ids.size()));
        } else {
          response.setError(
              String.format(
                  I18n.get(IExceptionMessage.DPAE_PRINTS_ERROR),
                  ids.size() - idsError.size(),
                  idsError.toString()));
        }
      } else if (context.get("id") != null) {
        Employee employee = request.getContext().asType(Employee.class);
        employee = Beans.get(EmployeeRepository.class).find(employee.getId());
        employeeService.generateNewDPAE(employee);
        LOG.debug("Printing " + employee.getName() + "'s DPAE");
        response.setFlash(String.format(I18n.get(IExceptionMessage.DPAE_PRINTS_SUCCESSFUL), 1));
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.DPAE_PRINT));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
