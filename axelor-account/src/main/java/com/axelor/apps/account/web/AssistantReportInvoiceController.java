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
package com.axelor.apps.account.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.AssistantReportInvoice;
import com.axelor.apps.account.report.IReport;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Joiner;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AssistantReportInvoiceController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final DateTimeFormatter dtFormater = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  public void printSales(ActionRequest request, ActionResponse response) throws AxelorException {

    AssistantReportInvoice assistant = request.getContext().asType(AssistantReportInvoice.class);

    String name = I18n.get("SaleInvoicesDetails-") + getDateString(assistant);

    String fileLink =
        ReportFactory.createReport(
                "accountSaleInvoicesDetails",
                assistant.getCompany(),
                IReport.SALE_INVOICES_DETAILS,
                name + "-${date}")
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .addParam("assistantId", assistant.getId())
            .addParam("companyId", assistant.getCompany().getId())
            .addParam("graphType", assistant.getGraphTypeSelect().toString())
            .addParam("turnoverTypeSelect", assistant.getTurnoverTypeSelect())
            .addFormat(assistant.getFormatSelect())
            .generate()
            .getFileLink();

    logger.debug("Printing " + name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }

  private String getDateString(AssistantReportInvoice assistant) {
    return assistant.getFromDate().format(dtFormater) + assistant.getToDate().format(dtFormater);
  }

  public void printPurchases(ActionRequest request, ActionResponse response)
      throws AxelorException {

    AssistantReportInvoice assistant = request.getContext().asType(AssistantReportInvoice.class);

    String name = I18n.get("PurchaseInvoicesDetails-") + getDateString(assistant);

    String fileLink =
        ReportFactory.createReport(
                "accountPurchaseInvoicesDetails",
                assistant.getCompany(),
                IReport.PURCHASE_INVOICES_DETAILS,
                name + "-${date}")
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .addParam("assistantId", assistant.getId())
            .addParam("companyId", assistant.getCompany().getId())
            .addParam("partnersIds", Joiner.on(",").join(assistant.getPartnerSet()))
            .addParam("productsIds", Joiner.on(",").join(assistant.getProductSet()))
            .addParam(
                "productCategoriesIds", Joiner.on(",").join(assistant.getProductCategorySet()))
            .addParam("graphType", assistant.getGraphTypeSelect().toString())
            .addFormat(assistant.getFormatSelect())
            .generate()
            .getFileLink();

    logger.debug("Printing " + name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }
}
