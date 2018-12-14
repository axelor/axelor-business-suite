/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.supplychain.db.Mrp;
import com.axelor.apps.supplychain.db.repo.MrpRepository;
import com.axelor.apps.supplychain.report.IReport;
import com.axelor.apps.supplychain.service.MrpService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.concurrent.TimeoutException;

@Singleton
public class MrpController {

  @Inject protected Provider<MrpService> mrpServiceProvider;

  @Inject protected Provider<MrpRepository> mrpRepositoryProvider;

  public void runCalculation(ActionRequest request, ActionResponse response) {

    Mrp mrp = request.getContext().asType(Mrp.class);
    MrpService mrpService = mrpServiceProvider.get();
    MrpRepository mrpRepository = mrpRepositoryProvider.get();
    try {
      mrpService.runCalculation(mrpRepository.find(mrp.getId()), true);
    } catch (TimeoutException e) {
      TraceBackService.trace(response, e);
      mrpService.reset(mrpRepository.find(mrp.getId()));
      mrpService.setStatusSelect(
          mrpRepository.find(mrp.getId()), MrpRepository.STATUS_TIME_LIMIT_EXCEEDED);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
      mrpService.reset(mrpRepository.find(mrp.getId()));
    } finally {
      response.setReload(true);
    }
  }

  public void generateAllProposals(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Mrp mrp = request.getContext().asType(Mrp.class);
    MrpService mrpService = mrpServiceProvider.get();
    MrpRepository mrpRepository = mrpRepositoryProvider.get();
    mrpService.generateProposals(mrpRepository.find(mrp.getId()));
    response.setReload(true);
  }

  /**
   * Prints the weekly breakdown MRP birt report and shows it to the user.
   *
   * @param request
   * @param response
   */
  public void printWeeks(ActionRequest request, ActionResponse response) {
    Mrp mrp = request.getContext().asType(Mrp.class);
    MrpService mrpService = mrpServiceProvider.get();
    MrpRepository mrpRepository = mrpRepositoryProvider.get();
    mrp = mrpRepository.find(mrp.getId());
    String name = I18n.get("MRP") + "-" + mrp.getId();

    try {
      String fileLink =
          ReportFactory.createReport(IReport.MRP_WEEKS, name)
              .addParam("mrpId", mrp.getId())
              .addParam("Locale", ReportSettings.getPrintingLocale(null))
              .addParam("endDate", mrpService.findMrpEndDate(mrp).atStartOfDay().toString())
              .addFormat(ReportSettings.FORMAT_PDF)
              .generate()
              .getFileLink();

      response.setView(ActionView.define(name).add("html", fileLink).map());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
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
