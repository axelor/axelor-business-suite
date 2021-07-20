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
package com.axelor.apps.base.service.imports.importer;

import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExcelToCSV {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public List<Map> generateExcelSheets(File file) throws IOException {
    List<Map> newSheets = new ArrayList<>();
    Object sheet = new Object();

    FileInputStream inputStream;
    Workbook workBook = null;

    try {

      inputStream = new FileInputStream(file);
      workBook = new XSSFWorkbook(inputStream);

      for (int i = 0; i < workBook.getNumberOfSheets(); i++) {
        sheet = workBook.getSheetAt(i).getSheetName();
        Map<String, Object> newSheet = new HashMap<>();
        newSheet.put("name", sheet);
        newSheets.add(newSheet);
      }

    } catch (Exception e) {
      LOG.error(e.getMessage());
    }

    return newSheets;
  }

  public void writeTOCSV(File sheetFile, Sheet sheet, int startRow, int startColumn)
      throws IOException, AxelorException {
    try (FileWriter writer = new FileWriter(sheetFile); ) {
      String separator = ";";
      int cnt = 0;

      for (int row = startRow; row <= sheet.getLastRowNum(); row++) {

        if (row == startRow) {
          Row headerRow = sheet.getRow(row);
          for (int cell = startColumn; cell < headerRow.getLastCellNum(); cell++) {
            Cell headerCell = headerRow.getCell(cell);
            if (headerCell == null
                || headerCell.getCellType() != CellType.STRING
                || headerCell.getStringCellValue().isEmpty()) {
              throw new AxelorException(
                  TraceBackRepository.CATEGORY_INCONSISTENCY,
                  I18n.get(IExceptionMessage.INVALID_HEADER));
            }

            String value = headerCell.getStringCellValue();

            if (cnt != headerRow.getLastCellNum()) {
              value += separator;
            }
            writer.append(value);
            cnt++;
          }
          writer.append("\n");

        } else {

          Row dataRow = sheet.getRow(row);
          for (int cell = startColumn; cell < cnt; cell++) {

            try {
              Cell dataCell = dataRow.getCell(cell);
              String value = "";
              if (dataCell != null) {

                switch (dataCell.getCellType()) {
                  case STRING:
                    String strData = dataCell.getStringCellValue();
                    value = "\"" + strData + "\"";
                    break;

                  case NUMERIC:
                    if (DateUtil.isCellDateFormatted(dataCell)) {
                      String dateInString = getDateValue(dataCell);
                      value = "\"" + dateInString + "\"";

                    } else {
                      Integer val = (int) dataCell.getNumericCellValue();
                      value = val.toString();
                    }
                    break;

                  case BOOLEAN:
                    value = new Boolean(dataCell.getBooleanCellValue()).toString();
                    break;
                  case BLANK:
                  default:
                    break;
                }
              }
              if (cell != cnt) {
                value += separator;
              }
              writer.append(value);
            } catch (Exception e) {
              LOG.error(e.getMessage());
            }
          }
          writer.append("\n");
        }
      }

      writer.flush();
    }
  }

  public static String getDateValue(Cell cell) {

    Calendar cal = Calendar.getInstance();
    Date date = cell.getDateCellValue();
    cal.setTime(date);
    int hours = cal.get(Calendar.HOUR_OF_DAY);
    int minutes = cal.get(Calendar.MINUTE);
    int seconds = cal.get(Calendar.SECOND);

    SimpleDateFormat format;
    if (hours == 0 && minutes == 0 && seconds == 0) format = new SimpleDateFormat("yyyy-MM-dd");
    else if (seconds == 0) format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
    else format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:SS");

    return format.format(date);
  }
}
