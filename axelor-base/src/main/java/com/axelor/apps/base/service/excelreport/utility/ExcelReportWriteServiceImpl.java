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
package com.axelor.apps.base.service.excelreport.utility;

import com.axelor.apps.base.service.excelreport.config.ExcelReportConstants;
import com.axelor.common.ObjectUtils;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;

public class ExcelReportWriteServiceImpl implements ExcelReportWriteService {

  @Inject private ExcelReportCellMergingService excelReportCellMergingService;

  @Override
  public void writeTemplateSheet(
      Map<Integer, Map<String, Object>> outputMap,
      Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet,
      Sheet originSheet,
      Sheet newSheet,
      int offset) {
    this.write(outputMap, originSheet, newSheet, offset, false);
    excelReportCellMergingService.fillMergedRegionCells(
        newSheet, mergedCellsRangeAddressSetPerSheet);
    excelReportCellMergingService.setMergedRegionsInSheet(
        newSheet, mergedCellsRangeAddressSetPerSheet);
  }

  @Override
  public void write(
      Map<Integer, Map<String, Object>> outputMap,
      Sheet originSheet,
      Sheet sheet,
      int offset,
      boolean setExtraHeight) {
    for (Map.Entry<Integer, Map<String, Object>> entry : outputMap.entrySet()) {
      Map<String, Object> m = entry.getValue();
      int cellRow = (Integer) m.get(ExcelReportConstants.KEY_ROW) + offset;
      int cellColumn = (Integer) m.get(ExcelReportConstants.KEY_COLUMN);

      Row r = sheet.getRow(cellRow);
      if (r == null) {
        r = sheet.createRow(cellRow);
      }
      Cell c = r.getCell(cellColumn);
      if (c == null) {
        c = r.createCell(cellColumn, CellType.STRING);
      }
      Workbook workbook = sheet.getWorkbook();
      CellStyle newCellStyle = workbook.createCellStyle();
      CellStyle oldCellStyle = (XSSFCellStyle) m.get(ExcelReportConstants.KEY_CELL_STYLE);

      Object cellValue = m.get(ExcelReportConstants.KEY_VALUE);
      if (cellValue.getClass().equals(XSSFRichTextString.class)) {
        c.setCellValue((XSSFRichTextString) cellValue);
      } else {
        c.setCellValue(cellValue.toString());
      }

      if (ObjectUtils.notEmpty(oldCellStyle)) {
        newCellStyle.cloneStyleFrom(oldCellStyle);
        c.setCellStyle(newCellStyle);
      }

      r.setHeightInPoints(setExtraHeight ? 50 : -1);
      sheet.setColumnWidth(cellColumn, originSheet.getColumnWidth(cellColumn));
    }
  }
}
