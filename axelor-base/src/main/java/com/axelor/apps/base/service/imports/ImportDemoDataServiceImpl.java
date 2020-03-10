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

import com.axelor.apps.base.db.ImportConfiguration;
import com.axelor.apps.base.db.ImportHistory;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.imports.importer.ExcelToCSV;
import com.axelor.apps.base.service.imports.importer.FactoryImporter;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.MetaScanner;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaModuleRepository;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ImportDemoDataServiceImpl implements ImportDemoDataService {

  @Inject private MetaFiles metaFiles;

  @Inject FactoryImporter factoryImporter;

  @Inject private ExcelToCSV excelToCSV;

  @Inject private MetaModuleRepository metaModuleRepo;

  @Override
  public boolean importDemoDataExcel(File excelFile, File logFile)
      throws FileNotFoundException, IOException, AxelorException, ParseException,
          ClassNotFoundException {
    Workbook workBook = new XSSFWorkbook(new FileInputStream(excelFile));
    FileOutputStream out = new FileOutputStream(logFile);

    try {

      if (this.validateExcel(excelFile, out)) {
        out = new FileOutputStream(logFile);
        for (int i = 0; i < workBook.getNumberOfSheets(); i++) {
          Sheet sheet = workBook.getSheetAt(i);

          String[] importDetails = this.getImportDetailsFromSheet(sheet);

          File dataFile = File.createTempFile(importDetails[1], ".csv");

          excelToCSV.writeTOCSV(dataFile, sheet, 3, 1);

          File configFile = File.createTempFile(importDetails[2], ".xml");
          configFile = this.getConfigFile(importDetails[0], configFile, importDetails[2]);

          MetaFile logMetaFile =
              this.importFileData(dataFile, configFile, importDetails[1], importDetails[2]);
          File file = MetaFiles.getPath(logMetaFile).toFile();
          out.write(("Import: " + importDetails[1]).getBytes());
          ByteStreams.copy(new FileInputStream(file), out);
          out.write("\n\n".getBytes());
        }
        return true;
      }

    } finally {
      out.close();
    }
    return false;
  }

  protected boolean validateExcel(File excelFile, FileOutputStream out)
      throws FileNotFoundException, IOException, AxelorException {
    Workbook workBook = new XSSFWorkbook(new FileInputStream(excelFile));
    boolean flag = true;
    for (int i = 0; i < workBook.getNumberOfSheets(); i++) {

      Sheet sheet = workBook.getSheetAt(i);
      StringBuilder errorList = new StringBuilder();
      errorList.append("\n" + "Sheet : " + sheet.getSheetName());

      if (!this.validateSheet(sheet, errorList)) {
        out.write(errorList.toString().getBytes());

        flag = false;
        out.write("\n".getBytes());
      }
    }

    return flag;
  }

  protected boolean validateSheet(Sheet sheet, StringBuilder errorList) throws IOException {

    boolean flag = true;

    if (this.validateModule(sheet.getRow(0), errorList)) {
      if (!this.validateConfigFile(sheet.getRow(0), sheet.getRow(2), errorList)) {
        flag = false;
      }
    } else {
      flag = false;
    }

    if (!this.validateDataFile(sheet.getRow(1), errorList)) {
      flag = false;
    }

    if (!this.validateHeader(sheet, errorList)) {
      flag = false;
    }
    return flag;
  }

  protected boolean validateModule(Row moduleRow, StringBuilder errorList) throws IOException {

    if (this.validateRow(moduleRow, errorList, I18n.get(IExceptionMessage.MODULE))
        && this.validateCell(moduleRow.getCell(0), errorList, I18n.get(IExceptionMessage.MODULE))) {
      String moduleName = moduleRow.getCell(0).getStringCellValue();

      if (metaModuleRepo.findByName(moduleName) != null) {
        return true;
      } else {
        errorList.append(
            String.format("\n" + I18n.get(IExceptionMessage.MODULE_NOT_EXIST), moduleName));
      }
    }
    return false;
  }

  protected boolean validateDataFile(Row dataFileRow, StringBuilder errorList) throws IOException {

    if (this.validateRow(dataFileRow, errorList, I18n.get(IExceptionMessage.DATA_FILE))
        && this.validateCell(
            dataFileRow.getCell(0), errorList, I18n.get(IExceptionMessage.DATA_FILE))) {
      return true;
    }

    return false;
  }

  protected boolean validateConfigFile(Row moduleRow, Row configFileRow, StringBuilder errorList)
      throws IOException {

    if (this.validateRow(configFileRow, errorList, I18n.get(IExceptionMessage.CONFIGURATION_FILE))
        && this.validateCell(
            configFileRow.getCell(0), errorList, I18n.get(IExceptionMessage.CONFIGURATION_FILE))) {

      String moduleName = moduleRow.getCell(0).getStringCellValue();
      String configFileName = configFileRow.getCell(0).getStringCellValue();

      if (this.checkConfigFile(moduleName, configFileName)) {
        return true;
      } else {
        errorList.append(
            String.format(
                I18n.get("\n" + IExceptionMessage.CONFIGURATION_FILE_NOT_EXIST), configFileName));
      }
    }
    return false;
  }

  protected boolean validateHeader(Sheet sheet, StringBuilder errorList) throws IOException {

    boolean flag = true;
    Row headerRow = sheet.getRow(3);

    if (headerRow != null) {

      for (int cell = 1; cell < headerRow.getLastCellNum(); cell++) {
        Cell headerCell = headerRow.getCell(cell);

        if (headerCell == null || headerCell.getCellType() != Cell.CELL_TYPE_STRING) {
          errorList.append("\n" + I18n.get(IExceptionMessage.INVALID_HEADER));
          flag = false;
        }
      }
    } else {
      errorList.append("\n" + I18n.get(IExceptionMessage.INVALID_HEADER));
      flag = false;
    }
    return flag;
  }

  protected boolean validateRow(Row row, StringBuilder errorList, String rowName)
      throws IOException {

    if (row == null) {
      errorList.append(String.format("\n" + I18n.get(IExceptionMessage.ROW_NOT_EMPTY), rowName));
      return false;
    }
    return true;
  }

  protected boolean validateCell(Cell cell, StringBuilder errorList, String cellName)
      throws IOException {

    if (cell == null || cell.getCellType() != Cell.CELL_TYPE_STRING) {
      errorList.append(String.format("\n" + I18n.get(IExceptionMessage.CELL_NOT_VALID), cellName));
      return false;
    }
    return true;
  }

  protected boolean checkConfigFile(String moduleName, String configFileName) {
    String dirNamePattern = "demo".replaceAll("/|\\\\", "(/|\\\\\\\\)");
    List<URL> files = new ArrayList<URL>();

    files.addAll(MetaScanner.findAll(moduleName, dirNamePattern, configFileName + ".xml"));

    if (files.isEmpty()) {
      return false;
    }
    return true;
  }

  protected String[] getImportDetailsFromSheet(Sheet sheet) throws AxelorException {
    String[] importDetails = new String[3];
    Row moduleRow = sheet.getRow(0);
    Row dataFileRow = sheet.getRow(1);
    Row configFileRow = sheet.getRow(2);

    importDetails[0] = moduleRow.getCell(0).getStringCellValue();
    importDetails[1] = dataFileRow.getCell(0).getStringCellValue();
    importDetails[2] = configFileRow.getCell(0).getStringCellValue();

    return importDetails;
  }

  protected File getConfigFile(String moduleName, File configFile, String configFileName)
      throws IOException, AxelorException {
    String dirNamePattern = "demo".replaceAll("/|\\\\", "(/|\\\\\\\\)");
    List<URL> files = new ArrayList<URL>();

    files.addAll(MetaScanner.findAll(moduleName, dirNamePattern, configFileName + ".xml"));

    for (URL file : files) {
      FileOutputStream out = new FileOutputStream(configFile);
      try {
        ByteStreams.copy(file.openStream(), out);
      } finally {
        out.close();
      }
    }

    return configFile;
  }

  protected MetaFile importFileData(
      File dataFile, File configFile, String dataFileName, String configFileName)
      throws IOException, AxelorException {
    ImportConfiguration config = new ImportConfiguration();

    MetaFile configMetaFile =
        metaFiles.upload(new FileInputStream(configFile), configFileName + ".xml");
    MetaFile dataMetaFile = metaFiles.upload(new FileInputStream(dataFile), dataFileName + ".csv");

    config.setBindMetaFile(configMetaFile);
    config.setDataMetaFile(dataMetaFile);
    config.setTypeSelect("csv");

    ImportHistory importHistory = factoryImporter.createImporter(config).run();

    MetaFiles.getPath(configMetaFile).toFile().delete();
    MetaFiles.getPath(dataMetaFile).toFile().delete();

    return importHistory.getLogMetaFile();
  }
}
