/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service.imports;

import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.imports.importer.ExcelToCSV;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ConvertDemoDataFileServiceImpl implements ConvertDemoDataFileService {

  @Inject private ExcelToCSV excelToCSV;

  @Inject private MetaFiles metaFiles;

  @Override
  public MetaFile convertDemoDataExcelFile(File excelFile)
      throws IOException, AxelorException, ParseException {

    File zipFile = this.createZIPFromExcel(excelFile);
    FileInputStream inStream = new FileInputStream(zipFile);
    MetaFile metaFile =
        metaFiles.upload(
            inStream,
            "demo_data_" + new SimpleDateFormat("ddMMyyyHHmm").format(new Date()) + ".zip");
    inStream.close();
    zipFile.delete();

    return metaFile;
  }

  protected File createZIPFromExcel(File excelFile)
      throws IOException, ParseException, AxelorException {

    Workbook workBook = new XSSFWorkbook(new FileInputStream(excelFile));

    File zipFile = File.createTempFile("demo", ".zip");
    List<String> entries = new ArrayList<>();

    for (int i = 0; i < workBook.getNumberOfSheets(); i++) {
      Sheet sheet = workBook.getSheetAt(i);
      File csvFile =
          new File(excelFile.getParent() + File.separator + this.getFileNameFromSheet(sheet));
      try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile)); ) {

        excelToCSV.writeTOCSV(csvFile, sheet, 3, 1);

        if (entries.contains(csvFile.getName())) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(IExceptionMessage.DUPLICATE_CSV_FILE_NAME_EXISTS));
        }
        entries.add(csvFile.getName());

        this.writeToZip(csvFile, zos);

      } finally {
        csvFile.delete();
      }
    }

    entries.clear();

    return zipFile;
  }

  protected void writeToZip(File csvFile, ZipOutputStream zos) throws IOException {
    try (FileInputStream fis = new FileInputStream(csvFile)) {
      zos.putNextEntry(new ZipEntry(csvFile.getName()));
      byte[] buffer = new byte[1024];

      int length;
      while ((length = fis.read(buffer)) > 0) {
        zos.write(buffer, 0, length);
      }
      zos.closeEntry();
    }
  }

  protected String getFileNameFromSheet(Sheet sheet) throws AxelorException {
    String fileName = "";
    Row fileNameRow = sheet.getRow(1);
    if (fileNameRow != null) {
      Cell fileNameCell = fileNameRow.getCell(0);
      if (fileNameCell != null
          && fileNameCell.getCellType() != Cell.CELL_TYPE_BLANK
          && fileNameCell.getCellType() == Cell.CELL_TYPE_STRING) {

        fileName = fileNameCell.getStringCellValue() + ".csv";
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.CSV_FILE_NAME_NOT_EXISTS));
      }
    } else {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.EXCEL_FILE_FORMAT_ERROR));
    }
    return fileName;
  }
}
