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
package com.axelor.apps.crm.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.ImportConfiguration;
import com.axelor.apps.base.db.repo.ImportConfigurationRepository;
import com.axelor.apps.base.service.MapService;
import com.axelor.apps.crm.db.Lead;
import com.axelor.apps.crm.db.repo.LeadRepository;
import com.axelor.apps.crm.db.report.IReport;
import com.axelor.apps.crm.exception.IExceptionMessage;
import com.axelor.apps.crm.service.LeadService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.csv.script.ImportLeadConfiguration;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import org.eclipse.birt.core.exception.BirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LeadController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject LeadRepository leadRepo;

  /**
   * Method to generate Lead as a Pdf
   *
   * @param request
   * @param response
   * @return
   * @throws BirtException
   * @throws IOException
   */
  public void print(ActionRequest request, ActionResponse response) throws AxelorException {

    Lead lead = request.getContext().asType(Lead.class);
    String leadIds = "";

    @SuppressWarnings("unchecked")
    List<Integer> lstSelectedleads = (List<Integer>) request.getContext().get("_ids");
    if (lstSelectedleads != null) {
      for (Integer it : lstSelectedleads) {
        leadIds += it.toString() + ",";
      }
    }

    if (!leadIds.equals("")) {
      leadIds = leadIds.substring(0, leadIds.length() - 1);
      lead = Beans.get(LeadRepository.class).find(new Long(lstSelectedleads.get(0)));
    } else if (lead.getId() != null) {
      leadIds = lead.getId().toString();
    }

    if (!leadIds.equals("")) {
      String title = " ";
      if (lead.getFirstName() != null) {
        title += lstSelectedleads == null ? "Lead " + lead.getFirstName() : "Leads";
      }

      String fileLink =
          ReportFactory.createReport(
                  "crmLead",
                  AuthUtils.getUser().getActiveCompany(),
                  IReport.LEAD,
                  title + "-${date}")
              .addParam("LeadId", leadIds)
              .addParam("Locale", ReportSettings.getPrintingLocale(lead.getPartner()))
              .generate()
              .getFileLink();

      logger.debug("Printing " + title);

      response.setView(ActionView.define(title).add("html", fileLink).map());

    } else {
      response.setFlash(I18n.get(IExceptionMessage.LEAD_1));
    }
  }

  public void showLeadsOnMap(ActionRequest request, ActionResponse response) {
    try {
      response.setView(
          ActionView.define(I18n.get("Leads"))
              .add("html", Beans.get(MapService.class).getMapURI("lead"))
              .map());
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  public void setSocialNetworkUrl(ActionRequest request, ActionResponse response)
      throws IOException {

    Lead lead = request.getContext().asType(Lead.class);
    Map<String, String> urlMap =
        Beans.get(LeadService.class)
            .getSocialNetworkUrl(lead.getName(), lead.getFirstName(), lead.getEnterpriseName());
    response.setAttr("googleLabel", "title", urlMap.get("google"));
    response.setAttr("facebookLabel", "title", urlMap.get("facebook"));
    response.setAttr("twitterLabel", "title", urlMap.get("twitter"));
    response.setAttr("linkedinLabel", "title", urlMap.get("linkedin"));
    response.setAttr("youtubeLabel", "title", urlMap.get("youtube"));
  }

  public void getLeadImportConfig(ActionRequest request, ActionResponse response) {

    ImportConfiguration leadImportConfig =
        Beans.get(ImportConfigurationRepository.class)
            .all()
            .filter("self.bindMetaFile.fileName = ?1", ImportLeadConfiguration.IMPORT_LEAD_CONFIG)
            .fetchOne();

    logger.debug("ImportConfig for lead: {}", leadImportConfig);

    if (leadImportConfig == null) {
      response.setFlash(I18n.get(IExceptionMessage.LEAD_4));
    } else {
      response.setView(
          ActionView.define(I18n.get(IExceptionMessage.LEAD_5))
              .model("com.axelor.apps.base.db.ImportConfiguration")
              .add("form", "import-configuration-form")
              .param("popup", "reload")
              .param("forceEdit", "true")
              .param("popup-save", "false")
              .param("show-toolbar", "false")
              .context("_showRecord", leadImportConfig.getId().toString())
              .map());
    }
  }

  /**
   * Called from lead view on name change and onLoad. Call {@link
   * LeadService#isThereDuplicateLead(Lead)}
   *
   * @param request
   * @param response
   */
  public void checkLeadName(ActionRequest request, ActionResponse response) {
    Lead lead = request.getContext().asType(Lead.class);
    response.setAttr(
        "duplicateLeadText", "hidden", !Beans.get(LeadService.class).isThereDuplicateLead(lead));
  }

  public void loseLead(ActionRequest request, ActionResponse response) {
    try {
      Lead lead = request.getContext().asType(Lead.class);
      Beans.get(LeadService.class).loseLead(leadRepo.find(lead.getId()), lead.getLostReason());
      response.setCanClose(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
