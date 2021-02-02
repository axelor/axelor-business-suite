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
package com.axelor.apps.supplychain.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.callable.ControllerCallableTool;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.supplychain.db.Mrp;
import com.axelor.apps.supplychain.db.repo.MrpRepository;
import com.axelor.apps.supplychain.report.IReport;
import com.axelor.apps.supplychain.service.MrpService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class MrpController {

  public void undoManualChanges(ActionRequest request, ActionResponse response) {
    Mrp mrp = request.getContext().asType(Mrp.class);
    MrpService mrpService = Beans.get(MrpService.class);
    MrpRepository mrpRepository = Beans.get(MrpRepository.class);
    try {
      mrpService.undoManualChanges(mrpRepository.find(mrp.getId()));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
      mrpService.reset(mrpRepository.find(mrp.getId()));
    } finally {
      response.setReload(true);
    }
  }

  public void runCalculation(ActionRequest request, ActionResponse response) {

    Mrp mrp = request.getContext().asType(Mrp.class);
    MrpService mrpService = Beans.get(MrpService.class);
    MrpRepository mrpRepository = Beans.get(MrpRepository.class);
    try {
      mrpService.setMrp(Beans.get(MrpRepository.class).find(mrp.getId()));

      // Tool class that does not need to be injected
      ControllerCallableTool<Mrp> mrpControllerCallableTool = new ControllerCallableTool<>();

      mrpControllerCallableTool.runInSeparateThread(mrpService, response);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
      mrpService.saveErrorInMrp(mrpRepository.find(mrp.getId()), e);
    } finally {
      response.setReload(true);
    }
  }

  public void generateAllProposals(ActionRequest request, ActionResponse response) {

    try {

      Mrp mrp = request.getContext().asType(Mrp.class);
      Boolean isProposalsPerSupplier =
          (Boolean) request.getContext().get("consolidateProposalsPerSupplier");
      Beans.get(MrpService.class)
          .generateProposals(
              Beans.get(MrpRepository.class).find(mrp.getId()),
              isProposalsPerSupplier == null ? false : isProposalsPerSupplier);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  /**
   * Prints the weekly breakdown MRP birt report and shows it to the user.
   *
   * @param request
   * @param response
   */
  public void printWeeks(ActionRequest request, ActionResponse response) {
    Mrp mrp = request.getContext().asType(Mrp.class);
    mrp = Beans.get(MrpRepository.class).find(mrp.getId());
    String name = I18n.get("MRP") + "-" + mrp.getId();

    try {
      String fileLink =
          ReportFactory.createReport(IReport.MRP_WEEKS, name)
              .addParam("mrpId", mrp.getId())
              .addParam("Timezone", getTimezone(mrp))
              .addParam("Locale", ReportSettings.getPrintingLocale(null))
              .addParam(
                  "endDate",
                  Beans.get(MrpService.class).findMrpEndDate(mrp).atStartOfDay().toString())
              .addFormat(ReportSettings.FORMAT_PDF)
              .generate()
              .getFileLink();

      response.setView(ActionView.define(name).add("html", fileLink).map());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  private String getTimezone(Mrp mrp) {
    if (mrp.getStockLocation() == null || mrp.getStockLocation().getCompany() == null) {
      return null;
    }
    return mrp.getStockLocation().getCompany().getTimezone();
  }

  /**
   * Prints the list MRP birt report and shows it to the user.
   *
   * @param request
   * @param response
   */
  public void printList(ActionRequest request, ActionResponse response) {
    Mrp mrp = request.getContext().asType(Mrp.class);
    String name = I18n.get("MRP") + "-" + mrp.getId();

    try {
      String fileLink =
          ReportFactory.createReport(IReport.MRP_LIST, name)
              .addParam("mrpId", mrp.getId())
              .addParam("Timezone", getTimezone(mrp))
              .addParam("Locale", ReportSettings.getPrintingLocale(null))
              .addFormat(ReportSettings.FORMAT_PDF)
              .generate()
              .getFileLink();

      response.setView(ActionView.define(name).add("html", fileLink).map());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
