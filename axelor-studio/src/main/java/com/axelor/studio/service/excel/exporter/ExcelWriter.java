/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.studio.service.excel.exporter;

import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.studio.service.CommonService;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExcelWriter implements DataWriter {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private XSSFWorkbook workBook;

  private XSSFCellStyle style;

  private XSSFCellStyle green;

  private XSSFCellStyle lavender;

  private XSSFCellStyle violet;

  private XSSFCellStyle header;

  private MetaFiles metaFiles;

  @Override
  public void initialize() {
    workBook = new XSSFWorkbook();
    this.metaFiles = Beans.get(MetaFiles.class);
    addStyle();
  }

  @Override
  public void write(String key, Integer index, String[] values) {

    if (key == null || values == null) {
      return;
    }

    XSSFSheet sheet = workBook.getSheet(key);

    if (sheet == null) {
      sheet = workBook.createSheet(key);
    }

    if (index == null) {
      index = sheet.getPhysicalNumberOfRows();
    } else if (sheet.getPhysicalNumberOfRows() - 1 > index) {
      sheet.shiftRows(index, sheet.getPhysicalNumberOfRows(), 1);
    }

    XSSFRow row = sheet.createRow(index);

    for (int count = 0; count < values.length; count++) {
      XSSFCell cell = row.createCell(count);
      cell.setCellValue(values[count]);
    }
  }

  @Override
  public void write(String key, Integer index, Map<String, String> valMap, String[] headers) {

    if (key == null || valMap == null) {
      return;
    }

    XSSFSheet sheet = workBook.getSheet(key);

    if (sheet == null) {
      sheet = workBook.createSheet(key);
    }

    if (index == null) {
      index = sheet.getPhysicalNumberOfRows();
    } else if (sheet.getPhysicalNumberOfRows() - 1 > index) {
      sheet.shiftRows(index, sheet.getPhysicalNumberOfRows(), 1);
    }

    XSSFRow row = sheet.createRow(index);

    for (String header : headers) {
      XSSFCell cell = row.createCell(row.getPhysicalNumberOfCells());
      cell.setCellValue(valMap.get(header));
    }

    String type = valMap.get(CommonService.TYPE);

    if (headers.equals(CommonService.HEADERS)) {
      setStyle(type, row, index);
    }
  }

  @Override
  public MetaFile export(MetaFile input) {

    if (workBook == null) {
      return input;
    }

    setColumnWidth();

    String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyyy HH:mm:ss"));
    String fileName = "Export " + date + ".xlsx";

    try {
      File file = File.createTempFile("Export", ".xlsx");
      removeBlankSheets();
      FileOutputStream outStream = new FileOutputStream(file);
      workBook.write(outStream);
      outStream.close();

      log.debug("File created: {}, Size: {}", file.getName(), file.getTotalSpace());
      log.debug("Meta files: {}", metaFiles);
      FileInputStream inStream = new FileInputStream(file);
      if (input != null) {
        input.setFileName(fileName);
        input = metaFiles.upload(inStream, input);
      } else {
        input = metaFiles.upload(inStream, fileName);
      }

      inStream.close();

      file.delete();

    } catch (IOException e) {
      e.printStackTrace();
    }
    return input;
  }

  private void addStyle() {

    style = workBook.createCellStyle();
    style.setBorderBottom(CellStyle.BORDER_THIN);
    style.setBorderTop(CellStyle.BORDER_THIN);
    style.setBorderLeft(CellStyle.BORDER_THIN);
    style.setBorderRight(CellStyle.BORDER_THIN);

    green = workBook.createCellStyle();
    green.cloneStyleFrom(style);
    green.setFillForegroundColor(IndexedColors.BRIGHT_GREEN.index);
    green.setFillPattern(FillPatternType.SOLID_FOREGROUND);

    lavender = workBook.createCellStyle();
    lavender.cloneStyleFrom(green);
    lavender.setFillForegroundColor(IndexedColors.LAVENDER.index);

    violet = workBook.createCellStyle();
    violet.cloneStyleFrom(green);
    violet.setFillForegroundColor(IndexedColors.VIOLET.index);

    header = workBook.createCellStyle();
    header.cloneStyleFrom(green);
    header.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.index);
    XSSFFont font = workBook.createFont();
    font.setBold(true);
    header.setFont(font);
  }

  private void setStyle(String type, XSSFRow row, int index) {

    XSSFCellStyle applyStyle = null;
    if (index == 0) {
      applyStyle = header;
    } else if (type == null) {
      applyStyle = style;
    } else {
      switch (type) {
        case "panel":
          applyStyle = violet;
          break;
        case "paneltab":
          applyStyle = violet;
          break;
        case "panelside":
          applyStyle = violet;
          break;
        case "panelbook":
          applyStyle = lavender;
          break;
        default:
          applyStyle = style;
      }
    }

    Iterator<Cell> cellIter = row.cellIterator();
    while (cellIter.hasNext()) {
      Cell cell = cellIter.next();
      cell.setCellStyle(applyStyle);
    }
  }

  private void setColumnWidth() {

    Iterator<XSSFSheet> sheets = workBook.iterator();

    while (sheets.hasNext()) {
      XSSFSheet sheet = sheets.next();
      sheet.createFreezePane(0, 1, 0, 1);
      int count = 0;
      while (count < CommonService.HEADERS.length) {
        sheet.autoSizeColumn(count);
        count++;
      }
    }
  }

  private void removeBlankSheets() {

    Iterator<XSSFSheet> sheetIter = workBook.iterator();
    sheetIter.next();

    List<String> removeSheets = new ArrayList<String>();
    while (sheetIter.hasNext()) {
      XSSFSheet sheet = sheetIter.next();
      if (sheet.getPhysicalNumberOfRows() < 2) {
        removeSheets.add(sheet.getSheetName());
      }
    }

    for (String name : removeSheets) {
      workBook.removeSheetAt(workBook.getSheetIndex(name));
    }
  }
}
