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
package com.axelor.apps.businessproject.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.service.user.UserServiceImpl;
import com.axelor.apps.businessproject.exception.IExceptionMessage;
import com.axelor.apps.businessproject.report.IReport;
import com.axelor.apps.businessproject.report.ITranslation;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AvailabilitiesReportController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void printAvailabilitiesReport(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();

    String startDate = context.get("startDate").toString();
    String endDate = context.get("endDate").toString();
    Integer granularitySelect = (Integer) context.get("granularitySelect");
    Long companyId = getCompanyId(context);

    String name = I18n.get(ITranslation.AVAILABILITIES_TITLE);
    String fileLink = null;

    try {
      fileLink =
          ReportFactory.createReport(IReport.AVAILABILITIES, name + " - " + LocalDate.now())
              .addParam("Locale", ReportSettings.getPrintingLocale())
              .addParam("logoPath", getLogoPath())
              .addParam("startDate", startDate)
              .addParam("endDate", endDate)
              .addParam("granularitySelect", granularitySelect)
              .addParam("companyId", companyId)
              .generate()
              .getFileLink();

      LOG.debug("Printing {}", name);
      response.setView(ActionView.define(name).add("html", fileLink).map());

    } catch (AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  @SuppressWarnings("unchecked")
  private Long getCompanyId(Context context) {
    LinkedHashMap<String, Object> companyHashMap =
        (LinkedHashMap<String, Object>) context.get("company");

    return companyHashMap == null
        ? AuthUtils.getUser().getActiveCompany().getId()
        : Long.valueOf(companyHashMap.get("id").toString());
  }

  private String getLogoPath() throws AxelorException {
    MetaFile companyLogo = Beans.get(UserServiceImpl.class).getUserActiveCompanyLogo();

    if (companyLogo == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE, I18n.get(IExceptionMessage.COMPANY_LOGO));
    }

    return companyLogo.getFilePath();
  }
}
