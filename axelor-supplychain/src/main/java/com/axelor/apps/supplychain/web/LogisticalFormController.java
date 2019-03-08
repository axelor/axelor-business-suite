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
package com.axelor.apps.supplychain.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.stock.db.LogisticalForm;
import com.axelor.apps.supplychain.report.IReport;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class LogisticalFormController {

  public void print(ActionRequest request, ActionResponse response) {
    try {
      LogisticalForm logisticalForm = request.getContext().asType(LogisticalForm.class);

      String name =
          String.format("%s %s", I18n.get("Packing list"), logisticalForm.getDeliveryNumberSeq());

      String fileLink =
          ReportFactory.createReport(
                  "supplychainPackingList",
                  logisticalForm.getCompany(),
                  IReport.PACKING_LIST,
                  name + " - ${date}")
              .addParam("LogisticalFormId", logisticalForm.getId())
              .addParam(
                  "Locale",
                  ReportSettings.getPrintingLocale(logisticalForm.getDeliverToCustomerPartner()))
              .generate()
              .getFileLink();

      response.setView(ActionView.define(name).add("html", fileLink).map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
