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
package com.axelor.apps.stock.service.stockmove.print;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.AppBase;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.report.IReport;
import com.axelor.apps.tool.ModelTool;
import com.axelor.apps.tool.ThrowConsumer;
import com.axelor.apps.tool.file.PdfTool;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
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
import java.util.Optional;

public class StockMovePrintServiceImpl implements StockMovePrintService {

  protected AppBaseService appBaseService;

  @Inject
  public StockMovePrintServiceImpl(AppBaseService appBaseService) {
    this.appBaseService = appBaseService;
  }

  @Override
  public String printStockMoves(List<Long> ids) throws IOException {
    List<File> printedStockMoves = new ArrayList<>();
    ModelTool.apply(
        StockMove.class,
        ids,
        new ThrowConsumer<StockMove>() {
          @Override
          public void accept(StockMove stockMove) throws Exception {
            printedStockMoves.add(print(stockMove, ReportSettings.FORMAT_PDF));
          }
        });
    String fileName =
        I18n.get("Stock moves")
            + " - "
            + Beans.get(AppBaseService.class)
                .getTodayDate(
                    Optional.ofNullable(AuthUtils.getUser())
                        .map(User::getActiveCompany)
                        .orElse(null))
                .format(DateTimeFormatter.BASIC_ISO_DATE)
            + "."
            + ReportSettings.FORMAT_PDF;
    return PdfTool.mergePdfToFileLink(printedStockMoves, fileName);
  }

  @Override
  public ReportSettings prepareReportSettings(StockMove stockMove, String format)
      throws AxelorException {
    if (stockMove.getPrintingSettings() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          String.format(
              I18n.get(IExceptionMessage.STOCK_MOVES_MISSING_PRINTING_SETTINGS),
              stockMove.getStockMoveSeq()),
          stockMove);
    }

    String locale = ReportSettings.getPrintingLocale(stockMove.getPartner());
    String title = getFileName(stockMove);
    AppBase appBase = appBaseService.getAppBase();

    ReportSettings reportSetting =
        ReportFactory.createReport(
            "stockStockMove", stockMove.getCompany(), IReport.STOCK_MOVE, title + " - ${date}");
    return reportSetting
        .addParam("StockMoveId", stockMove.getId())
        .addParam(
            "Timezone",
            stockMove.getCompany() != null ? stockMove.getCompany().getTimezone() : null)
        .addParam("Locale", locale)
        .addParam(
            "GroupProducts",
            appBase.getIsRegroupProductsOnPrintings() && stockMove.getGroupProductsOnPrintings())
        .addParam("GroupProductTypes", appBase.getRegroupProductsTypeSelect())
        .addParam("GroupProductLevel", appBase.getRegroupProductsLevelSelect())
        .addParam("GroupProductProductTitle", appBase.getRegroupProductsLabelProducts())
        .addParam("GroupProductServiceTitle", appBase.getRegroupProductsLabelServices())
        .addParam("HeaderHeight", stockMove.getPrintingSettings().getPdfHeaderHeight())
        .addParam("FooterHeight", stockMove.getPrintingSettings().getPdfFooterHeight())
        .addFormat(format);
  }

  @Override
  public File print(StockMove stockMove, String format) throws AxelorException {
    ReportSettings reportSettings = prepareReportSettings(stockMove, format);
    return reportSettings.generate().getFile();
  }

  @Override
  public String printStockMove(StockMove stockMove, String format)
      throws AxelorException, IOException {
    String fileName = I18n.get("Stock Move") + "-" + stockMove.getStockMoveSeq() + "." + format;
    return PdfTool.getFileLinkFromPdfFile(print(stockMove, format), fileName);
  }

  @Override
  public String getFileName(StockMove stockMove) {

    return I18n.get("Stock Move") + " " + stockMove.getStockMoveSeq();
  }
}
