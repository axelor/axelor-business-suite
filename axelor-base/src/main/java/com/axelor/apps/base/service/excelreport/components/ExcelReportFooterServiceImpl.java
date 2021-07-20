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
package com.axelor.apps.base.service.excelreport.components;

import com.axelor.apps.base.db.Print;
import com.axelor.apps.base.service.excelreport.config.ExcelReportConstants;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportCellMergingService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportShiftingService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportWriteService;
import com.axelor.common.ObjectUtils;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

public class ExcelReportFooterServiceImpl implements ExcelReportFooterService {

  @Inject private ExcelReportCellService excelReportCellService;
  @Inject private ExcelReportCellMergingService excelReportCellMergingService;
  @Inject private ExcelReportShiftingService excelReportShiftingService;
  @Inject private ExcelReportWriteService excelReportWriteService;

  @Override
  public Map<Integer, Map<String, Object>> getFooterInputMap(Workbook wb, Print print) {
    Map<Integer, Map<String, Object>> map = new HashMap<>();
    if (ObjectUtils.notEmpty(print.getPrintPdfFooter())) {

      Font font = wb.createFont();
      font.setFontName(
          print.getFooterFontType() != null ? print.getFooterFontType() : "Times Roman");
      font.setFontHeightInPoints(
          print.getFooterFontSize().equals(BigDecimal.ZERO)
              ? (short) 10
              : print.getFooterFontSize().shortValue());
      font.setColor(excelReportCellService.getCellFooterFontColor(print.getFooterFontColor()));
      if (print.getIsFooterUnderLine()) {
        font.setUnderline(Font.U_SINGLE);
      }
      CellStyle cellStyle = wb.createCellStyle();
      cellStyle.setFont(font);

      Map<String, Object> dataMap = new HashMap<>();
      dataMap.put(ExcelReportConstants.KEY_ROW, 1);
      dataMap.put(ExcelReportConstants.KEY_COLUMN, 1);
      dataMap.put(ExcelReportConstants.KEY_VALUE, print.getPrintPdfFooter());
      dataMap.put(ExcelReportConstants.KEY_CELL_STYLE, cellStyle);
      map.put(0, dataMap);
    }
    return map;
  }

  @Override
  public void setFooter(
      Sheet originSheet,
      Sheet sheet,
      Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet,
      Map<Integer, Map<String, Object>> footerOutputMap) {

    int footerStartRow =
        excelReportCellMergingService.getSheetLastRowNum(sheet, mergedCellsRangeAddressSetPerSheet)
            + 3;

    if (ObjectUtils.notEmpty(footerOutputMap)) {
      excelReportShiftingService.shiftAll(footerOutputMap, footerStartRow);
    }
    excelReportWriteService.write(footerOutputMap, originSheet, sheet, 0, true);
  }

  @Override
  public String generateFooterHtml(Print print) {

    StringBuilder htmlBuilder = new StringBuilder();

    if (!print.getHidePrintSettings()) {
      String pdfFooter = print.getPrintPdfFooter() != null ? print.getPrintPdfFooter() : "";
      String fontSize =
          print.getFooterHeight() != null
              ? "font-size: " + print.getFooterFontSize().intValue() + "px; "
              : "font-size: 20; ";
      String fontFamily =
          ObjectUtils.notEmpty(print.getFooterFontType())
              ? "font-family: " + print.getFooterFontType() + "; "
              : "font-family: Arial; ";
      String footerAlign =
          print.getFooterTextAlignment() != null
              ? "text-align: " + print.getFooterTextAlignment() + "; "
              : "text-align: left; ";
      String fontColor =
          ObjectUtils.notEmpty(print.getFooterFontColor())
              ? "color: " + print.getFooterFontColor() + "; "
              : "color: black; ";
      String textDecoration =
          Boolean.TRUE.equals(print.getIsFooterUnderLine()) ? "text-decoration: underline;" : "";

      htmlBuilder.append(
          "<table style='width: 100%;'><tr><td valign='top' style='"
              + footerAlign
              + "width: 100%; "
              + fontSize
              + fontFamily
              + fontColor
              + textDecoration
              + "'>"
              + pdfFooter
              + "</td></tr></table>");
    }
    return htmlBuilder.toString();
  }
}
