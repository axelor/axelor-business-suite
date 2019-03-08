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
package com.axelor.apps.sale.service.saleorder.print;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.exception.IExceptionMessage;
import com.axelor.apps.sale.report.IReport;
import com.axelor.apps.sale.service.saleorder.SaleOrderService;
import com.axelor.apps.tool.ModelTool;
import com.axelor.apps.tool.ThrowConsumer;
import com.axelor.apps.tool.file.PdfTool;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SaleOrderPrintServiceImpl implements SaleOrderPrintService {

  @Inject SaleOrderService saleOrderService;

  @Override
  public String printSaleOrder(SaleOrder saleOrder, boolean proforma, String format)
      throws AxelorException, IOException {
    String fileName = getSaleOrderFilesName(false, format);
    return PdfTool.getFileLinkFromPdfFile(print(saleOrder, proforma, format), fileName);
  }

  @Override
  public String printSaleOrders(List<Long> ids) throws IOException {
    List<File> printedSaleOrders = new ArrayList<>();
    ModelTool.apply(
        SaleOrder.class,
        ids,
        new ThrowConsumer<SaleOrder>() {
          @Override
          public void accept(SaleOrder saleOrder) throws Exception {
            printedSaleOrders.add(print(saleOrder, false, ReportSettings.FORMAT_PDF));
          }
        });
    String fileName = getSaleOrderFilesName(true, ReportSettings.FORMAT_PDF);
    return PdfTool.mergePdfToFileLink(printedSaleOrders, fileName);
  }

  public File print(SaleOrder saleOrder, boolean proforma, String format) throws AxelorException {
    ReportSettings reportSettings = prepareReportSettings(saleOrder, proforma, format);
    return reportSettings.generate().getFile();
  }

  @Override
  public ReportSettings prepareReportSettings(SaleOrder saleOrder, boolean proforma, String format)
      throws AxelorException {

    if (saleOrder.getPrintingSettings() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          String.format(
              I18n.get(IExceptionMessage.SALE_ORDER_MISSING_PRINTING_SETTINGS),
              saleOrder.getSaleOrderSeq()),
          saleOrder);
    }
    String locale = ReportSettings.getPrintingLocale(saleOrder.getClientPartner());

    String title = saleOrderService.getFileName(saleOrder);

    ReportSettings reportSetting =
        ReportFactory.createReport(
            "saleSaleOrder", saleOrder.getCompany(), IReport.SALES_ORDER, title + " - ${date}");

    return reportSetting
        .addParam("SaleOrderId", saleOrder.getId())
        .addParam("Locale", locale)
        .addParam("ProformaInvoice", proforma)
        .addFormat(format);
  }

  /**
   * Return the name for the printed sale order.
   *
   * @param plural if there is one or multiple sale orders.
   */
  protected String getSaleOrderFilesName(boolean plural, String format) {

    return I18n.get(plural ? "Sale orders" : "Sale order")
        + " - "
        + Beans.get(AppBaseService.class).getTodayDate().format(DateTimeFormatter.BASIC_ISO_DATE)
        + "."
        + format;
  }
}
