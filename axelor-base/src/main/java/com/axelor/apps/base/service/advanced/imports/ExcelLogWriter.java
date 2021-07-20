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
package com.axelor.apps.base.service.advanced.imports;

import com.axelor.i18n.I18n;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelLogWriter {

  private Workbook workbook;

  private Sheet sheet;

  private File excelFile;

  public void initialize(String fileName) throws IOException {
    excelFile = File.createTempFile(fileName, ".xlsx");

    workbook = new XSSFWorkbook();
    sheet = workbook.createSheet(fileName);
  }

  public void writeHeader(String[] header) {
    Row headerRow = sheet.createRow(sheet.getFirstRowNum());
    for (int col = 0; col < header.length; col++) {
      Cell headerCell = headerRow.createCell(col);
      headerCell.setCellValue(I18n.get(header[col]));
    }
  }

  public void writeBody(Map<String, Map<String, List<Integer>>> dataMap) {
    CellStyle cellStyle = this.setStyle();

    for (Entry<String, Map<String, List<Integer>>> keyEntry : dataMap.entrySet()) {
      Row titleRow = sheet.createRow(sheet.getLastRowNum() + 1);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellStyle(cellStyle);
      titleCell.setCellValue(keyEntry.getKey());

      for (Entry<String, List<Integer>> dataEntry : keyEntry.getValue().entrySet()) {
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        Row dataRow = sheet.createRow(sheet.getLastRowNum() + 1);
        Cell dataCell1 = dataRow.createCell(0);
        dataCell1.setCellValue(dataEntry.getKey());
        Cell dataCell2 = dataRow.createCell(1);
        if (!CollectionUtils.isEmpty(dataEntry.getValue())) {
          dataCell2.setCellValue(
              dataEntry.getValue().stream()
                  .map(num -> String.valueOf(num))
                  .collect(Collectors.joining(",")));
        }
      }
    }
  }

  private CellStyle setStyle() {
    CellStyle cellStyle = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    cellStyle.setFont(font);
    return cellStyle;
  }

  public void close() throws IOException {
    FileOutputStream fout = new FileOutputStream(excelFile);
    workbook.write(fout);
    fout.close();
  }

  public File getExcelFile() {
    return excelFile;
  }
}
