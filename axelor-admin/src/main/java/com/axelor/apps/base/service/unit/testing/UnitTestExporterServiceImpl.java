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
package com.axelor.apps.base.service.unit.testing;

import com.axelor.apps.base.db.GroupTestLine;
import com.axelor.apps.base.db.UnitTest;
import com.axelor.apps.base.db.UnitTestLine;
import com.axelor.apps.base.db.repo.GroupTestLineRepository;
import com.axelor.apps.base.db.repo.UnitTestLineRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class UnitTestExporterServiceImpl implements UnitTestExporterService {

  private static final String EXTENSION_XLSX = "xlsx";

  protected UnitTestService unitTestService;
  protected GroupTestLineRepository groupTestLineRepo;

  @Inject
  public UnitTestExporterServiceImpl(
      UnitTestService unitTestService, GroupTestLineRepository groupTestLineRepo) {
    this.unitTestService = unitTestService;
    this.groupTestLineRepo = groupTestLineRepo;
  }

  @Override
  public File exportTests(List<UnitTest> unitTestList) throws AxelorException {
    try {
      final Map<String, List<List<String>>> dataListMap = createDataListForUnitTests(unitTestList);
      final String fileName = String.format("%s (%s)", I18n.get("Unit Tests"), LocalDateTime.now());
      return createExportXls(dataListMap, fileName);
    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
  }

  protected Map<String, List<List<String>>> createDataListForUnitTests(
      List<UnitTest> unitTestList) {
    Map<String, List<List<String>>> dataListMap = new LinkedHashMap<>();
    for (UnitTest unitTest : unitTestList) {
      Pair<String, List<List<String>>> unitTestDataPair = getUnitTestData(unitTest);
      dataListMap.put(unitTestDataPair.getLeft(), unitTestDataPair.getRight());
    }
    return dataListMap;
  }

  protected Pair<String, List<List<String>>> getUnitTestData(UnitTest unitTest) {
    List<List<String>> unitTestDataList = new LinkedList<>();

    List<String> unitTestNameRow = getUnitTestNameRow(unitTest); // UnitTest name row
    unitTestDataList.add(unitTestNameRow);

    List<List<String>> groupNameRows = getGroupNameRows(unitTest); // GroupTest rows
    if (ObjectUtils.notEmpty(groupNameRows)) {
      unitTestDataList.add(Collections.nCopies(unitTestNameRow.size(), ""));
    }
    unitTestDataList.addAll(groupNameRows);

    List<String> unitTestHeaderRow = createUnitTestHeaderRow(); // UnitTest header row
    unitTestDataList.add(Collections.nCopies(unitTestHeaderRow.size(), ""));
    unitTestDataList.add(unitTestHeaderRow);

    unitTestDataList.addAll(getUnitTestLineRows(unitTest)); // UnitTestLine rows
    return Pair.of(unitTest.getName(), unitTestDataList);
  }

  protected List<List<String>> getGroupNameRows(UnitTest unitTest) {
    List<List<String>> rows = new LinkedList<>();
    List<GroupTestLine> groupTestLines =
        groupTestLineRepo
            .all()
            .filter("self.unitTest = :unitTest")
            .bind("unitTest", unitTest)
            .fetch();
    for (GroupTestLine groupTestLine : groupTestLines) {
      rows.add(getGroupNameRow(groupTestLine));
    }
    return rows;
  }

  protected List<String> getGroupNameRow(GroupTestLine groupTestLine) {
    return Arrays.asList(
        "group",
        groupTestLine.getGroupTest().getName(),
        String.valueOf(groupTestLine.getSequence()));
  }

  protected List<String> getUnitTestNameRow(UnitTest unitTest) {
    return Arrays.asList("name", unitTest.getName());
  }

  protected List<String> createUnitTestHeaderRow() {
    return Arrays.asList(
        "actionTypeSelect", "target", "value", "input", "isIncludedInContext", "assertTypeSelect");
  }

  protected List<List<String>> getUnitTestLineRows(UnitTest unitTest) {
    List<List<String>> dataRowsList = new LinkedList<>();
    final List<UnitTestLine> sortedTestLines = unitTestService.getSortedTestLines(unitTest);
    for (UnitTestLine unitTestLine : sortedTestLines) {
      String actionTypeSelect = unitTestLine.getActionTypeSelect();
      List<String> values =
          Arrays.asList(
              actionTypeSelect,
              unitTestLine.getTarget(),
              unitTestLine.getValue(),
              unitTestLine.getInput(),
              unitTestLine.getIsIncludedInContext()
                  ? Boolean.toString(unitTestLine.getIsIncludedInContext())
                  : "",
              UnitTestLineRepository.ACTION_TYPE_SELECT_ASSERT.equals(actionTypeSelect)
                  ? unitTestLine.getAssertTypeSelect()
                  : "");
      dataRowsList.add(values);
    }
    return dataRowsList;
  }

  protected File createExportXls(Map<String, List<List<String>>> dataListMap, final String fileName)
      throws IOException {
    Workbook workbook = createExcelWorkbook(dataListMap);
    File exportFile = MetaFiles.createTempFile(fileName, "." + EXTENSION_XLSX).toFile();
    FileOutputStream out = new FileOutputStream(exportFile);
    workbook.write(out);
    out.close();
    return exportFile;
  }

  protected Workbook createExcelWorkbook(Map<String, List<List<String>>> dataListMap) {
    Workbook workbook = new XSSFWorkbook();
    for (Map.Entry<String, List<List<String>>> entry : dataListMap.entrySet()) {
      createExportSheet(workbook, entry.getKey(), entry.getValue());
    }
    return workbook;
  }

  protected Sheet createExportSheet(Workbook workbook, String sheetName, List<List<String>> rows) {
    Sheet sheet = workbook.createSheet(sheetName);
    int rowIndex = 0;
    for (List<String> row : rows) {
      createExportRow(sheet, rowIndex++, row);
    }
    return sheet;
  }

  protected void autoSizeColumns(int headerRow, Sheet sheet) {
    int firstCellNum = sheet.getRow(headerRow).getFirstCellNum();
    int lastCellNum = sheet.getRow(headerRow).getLastCellNum();
    for (int i = firstCellNum; i < lastCellNum; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  protected Row createExportRow(Sheet sheet, int count, List<String> values) {
    Row row = sheet.createRow(count);
    int column = 0;
    for (String value : values) {
      createCellFromValue(row, column++, value);
    }
    autoSizeColumns(count, sheet);
    return row;
  }

  protected Cell createCellFromValue(Row row, int column, String cellValue) {
    Cell cell = row.createCell(column);
    cell.setCellValue(cellValue);
    return cell;
  }
}
