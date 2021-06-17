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
package com.axelor.apps.production.service.manuforder;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.production.db.ManufOrder;
import com.axelor.apps.production.report.IReport;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.tool.ModelTool;
import com.axelor.apps.tool.ThrowConsumer;
import com.axelor.apps.tool.file.PdfTool;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ManufOrderPrintServiceImpl implements ManufOrderPrintService {

  @Override
  public String printManufOrders(List<Long> ids) throws IOException {
    List<File> printedManufOrders = new ArrayList<>();
    ModelTool.apply(
        ManufOrder.class,
        ids,
        new ThrowConsumer<ManufOrder>() {

          @Override
          public void accept(ManufOrder manufOrder) throws Exception {
            printedManufOrders.add(print(manufOrder));
          }
        });
    String fileName = getManufOrdersFilename();
    return PdfTool.mergePdfToFileLink(printedManufOrders, fileName);
  }

  @Override
  public String printManufOrder(ManufOrder manufOrder) throws AxelorException {
    String fileName = getFileName(manufOrder);
    return PdfTool.getFileLinkFromPdfFile(print(manufOrder), fileName);
  }

  protected File print(ManufOrder manufOrder) throws AxelorException {
    ReportSettings reportSettings = prepareReportSettings(manufOrder);
    return reportSettings.generate().getFile();
  }

  @Override
  public ReportSettings prepareReportSettings(ManufOrder manufOrder) {
    String title = getFileName(manufOrder);
    ReportSettings reportSetting =
        ReportFactory.createReport(
            "productionManufOrder", manufOrder.getCompany(), IReport.MANUF_ORDER, title);
    return reportSetting
        .addParam("Locale", ReportSettings.getPrintingLocale(null))
        .addParam("ManufOrderId", manufOrder.getId().toString())
        .addParam(
            "Timezone",
            manufOrder.getCompany() != null ? manufOrder.getCompany().getTimezone() : null)
        .addParam(
            "activateBarCodeGeneration",
            Beans.get(AppBaseService.class).getAppBase().getActivateBarCodeGeneration())
        .addFormat(ReportSettings.FORMAT_PDF);
  }

  @Override
  public String getManufOrdersFilename() {
    return I18n.get("Manufacturing orders")
        + " - "
        + Beans.get(AppBaseService.class)
            .getTodayDate(
                Optional.ofNullable(AuthUtils.getUser()).map(User::getActiveCompany).orElse(null))
            .format(DateTimeFormatter.BASIC_ISO_DATE)
        + "."
        + ReportSettings.FORMAT_PDF;
  }

  @Override
  public String getFileName(ManufOrder manufOrder) {
    return I18n.get("Manufacturing order")
        + " "
        + manufOrder.getManufOrderSeq()
        + "."
        + ReportSettings.FORMAT_PDF;
  }
}
