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
package com.axelor.apps.production.web;

import com.axelor.app.AppSettings;
import com.axelor.apps.ReportFactory;
import com.axelor.apps.production.db.CostSheet;
import com.axelor.apps.production.report.IReport;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class CostSheetController {

  public void printCostSheetLineDetail(ActionRequest request, ActionResponse response) {

    try {
      CostSheet costSheet = request.getContext().asType(CostSheet.class);
      Long costSheetId = costSheet.getId();
      String name = I18n.get("Cost sheet");
      String fileLink =
          ReportFactory.createReport(
                  "productionCostSheet",
                  costSheet.getManufOrder() == null
                      ? (costSheet.getBillOfMaterial() == null
                          ? null
                          : costSheet.getBillOfMaterial().getCompany())
                      : costSheet.getManufOrder().getCompany(),
                  IReport.COST_SHEET,
                  name + "-${date}")
              .addParam("Locale", ReportSettings.getPrintingLocale(null))
              .addParam("CostSheetId", costSheetId)
              .addParam("BaseUrl", AppSettings.get().getBaseURL())
              .generate()
              .getFileLink();

      response.setCanClose(true);
      response.setView(ActionView.define(name).add("html", fileLink).map());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
