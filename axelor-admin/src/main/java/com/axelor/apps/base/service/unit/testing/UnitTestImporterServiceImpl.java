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
import com.axelor.apps.base.db.UnitTestLine;
import com.axelor.apps.base.exceptions.IExceptionMessages;
import com.axelor.apps.tool.reader.DataReaderFactory;
import com.axelor.apps.tool.reader.DataReaderService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.data.ImportTask;
import com.axelor.data.XStreamUtils;
import com.axelor.data.csv.CSVBind;
import com.axelor.data.csv.CSVConfig;
import com.axelor.data.csv.CSVImporter;
import com.axelor.data.csv.CSVInput;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.opencsv.CSVWriter;
import com.thoughtworks.xstream.XStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

public class UnitTestImporterServiceImpl implements UnitTestImporterService {

  private static final String CSV_TEST_LINE_IMPORT_FILE_NAME = "[test-line.import]";
  private static final String CSV_GROUP_TEST_FILE_NAME = "GroupTestLine";
  private static final String CSV_UNIT_TEST_LINE_SEQUENCE = "sequence";
  private static final String CSV_UNIT_TEST_COLUMN_NAME = "unitTestName";
  private static final String CSV_GROUP_TEST_COLUMN_NAME = "groupTestName";
  private static final String EXTENSION_CSV = ".csv";
  private static final String EXTENSION_XLSX = "xlsx";
  private static final String GROUP_CELL_KEY = "group";
  private static final String UNIT_NAME_CELL_KEY = "name";
  private static final char CSV_SEPERATOR = ';';

  protected DataReaderFactory dataReaderFactory;

  @Inject
  public UnitTestImporterServiceImpl(DataReaderFactory dataReaderFactory) {
    this.dataReaderFactory = dataReaderFactory;
  }

  @Override
  public void importTests(MetaFile importFile) throws AxelorException {
    final File dataDir = getTempDirectory(importFile);
    final List<File> dataFileList = createUnitTestInputCsvsFromXLS(importFile, dataDir);
    final File groupTestFile = createGroupTestInputCsvsFromXLS(importFile, dataDir);

    CSVConfig csvConfig = createGroupTestCSVConfig();
    XStream stream = XStreamUtils.createXStream();
    stream.processAnnotations(CSVConfig.class);
    CSVImporter importer = new CSVImporter(csvConfig, dataDir.getAbsolutePath());
    importer.run(
        new ImportTask() {

          @Override
          public void configure() throws IOException {
            for (File file : dataFileList) {
              input(CSV_TEST_LINE_IMPORT_FILE_NAME, file);
            }
            input(CSV_GROUP_TEST_FILE_NAME, groupTestFile);
          }
        });
  }

  protected List<File> createUnitTestInputCsvsFromXLS(MetaFile importFile, File dataDir)
      throws AxelorException {
    DataReaderService reader = dataReaderFactory.getDataReader(EXTENSION_XLSX);
    reader.initialize(importFile, Character.toString(CSV_SEPERATOR));
    List<File> dataFileList = new ArrayList<>();

    for (String sheetName : reader.getSheetNames()) {
      dataFileList.add(createInputCsvFromSheet(dataDir, reader, sheetName));
    }

    return dataFileList;
  }

  protected File createGroupTestInputCsvsFromXLS(MetaFile importFile, File dataDir)
      throws AxelorException {
    DataReaderService reader = dataReaderFactory.getDataReader(EXTENSION_XLSX);
    reader.initialize(importFile, Character.toString(CSV_SEPERATOR));

    File dataCSVFile = new File(dataDir, CSV_GROUP_TEST_FILE_NAME + EXTENSION_CSV);

    try (CSVWriter csvWriter = new CSVWriter(new FileWriter(dataCSVFile), CSV_SEPERATOR)) {
      String[] headers =
          new String[] {
            CSV_GROUP_TEST_COLUMN_NAME, CSV_UNIT_TEST_COLUMN_NAME, CSV_UNIT_TEST_LINE_SEQUENCE
          };
      csvWriter.writeNext(headers);
      for (String sheetName : reader.getSheetNames()) {
        Pair<Integer, String> resultPair = getUnitTestRowIndexAndName(reader, sheetName);
        String unitTestName = resultPair.getRight();
        int rowIndex = resultPair.getLeft();
        csvWriter.writeAll(
            getGroupNameRows(reader, sheetName, unitTestName, ++rowIndex, headers.length));
      }
    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_INCONSISTENCY);
    }

    return dataCSVFile;
  }

  protected List<String[]> getGroupNameRows(
      DataReaderService reader,
      String sheetName,
      String testName,
      int rowIndex,
      final int headerLength) {
    List<String[]> allLines = new ArrayList<>();
    int totalLines = reader.getTotalLines(sheetName);
    while (rowIndex < totalLines) {
      String[] values = new String[headerLength];
      String[] row = reader.read(sheetName, rowIndex++, 3);
      if (isEmptyRow(row)) {
        continue;
      }
      if (!GROUP_CELL_KEY.equals(row[0])) {
        break;
      }
      values[0] = row[1]; // groupName
      values[1] = testName; // unitTestName
      values[2] = row[2]; // sequence
      allLines.add(values);
    }
    return allLines;
  }

  protected Pair<Integer, String> getUnitTestRowIndexAndName(
      DataReaderService reader, String sheetName) throws AxelorException {
    int rowIndex = 0;
    String unitTestName = null;
    while (rowIndex < reader.getTotalLines(sheetName)) {
      String[] row = reader.read(sheetName, rowIndex++, 2);
      if (!isEmptyRow(row) && UNIT_NAME_CELL_KEY.equals(row[0])) {
        unitTestName = row[1];
        break;
      }
    }

    if (StringUtils.isBlank(unitTestName)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessages.UNIT_TEST_IMPORT_NAME_NOT_SPECIFIED));
    }

    return Pair.of(--rowIndex, unitTestName);
  }

  protected File createInputCsvFromSheet(File dataDir, DataReaderService reader, String sheetName)
      throws AxelorException {

    File dataCSVFile = new File(dataDir, sheetName + EXTENSION_CSV);

    try (CSVWriter csvWriter = new CSVWriter(new FileWriter(dataCSVFile), CSV_SEPERATOR)) {
      int totalLines = reader.getTotalLines(sheetName);
      int startRow = getStartRow(reader, sheetName);
      String[] headers = reader.read(sheetName, startRow++, 0);
      csvWriter.writeNext(
          ArrayUtils.addAll(headers, CSV_UNIT_TEST_COLUMN_NAME, CSV_UNIT_TEST_LINE_SEQUENCE));
      String unitTestName = getUnitTestRowIndexAndName(reader, sheetName).getRight();
      for (int i = startRow; i < totalLines; i++) {
        String[] values = reader.read(sheetName, i, headers.length);
        if (isEmptyRow(values)) {
          continue;
        }
        csvWriter.writeNext(ArrayUtils.addAll(values, unitTestName, String.valueOf(i)));
      }

    } catch (IOException e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_INCONSISTENCY);
    }
    return dataCSVFile;
  }

  protected CSVConfig createUnitTestCSVConfig() {
    CSVConfig csvConfig = new CSVConfig();
    csvConfig.setInputs(Arrays.asList(createUnitTestInput()));
    return csvConfig;
  }

  protected CSVConfig createGroupTestCSVConfig() {
    CSVConfig csvConfig = createUnitTestCSVConfig();
    csvConfig.setInputs(Arrays.asList(createGroupTestInput(), createUnitTestInput()));
    return csvConfig;
  }

  protected CSVInput createUnitTestInput() {
    CSVInput input = createCsvInput(CSV_TEST_LINE_IMPORT_FILE_NAME, UnitTestLine.class);
    input.setBindings(Arrays.asList(createUnitTestCsvBind()));
    return input;
  }

  protected CSVInput createGroupTestInput() {
    CSVInput input = createCsvInput(CSV_GROUP_TEST_FILE_NAME, GroupTestLine.class);
    input.setSearch("self.groupTest.name = :groupTestName and self.unitTest.name = :unitTestName");

    List<CSVBind> bindings = new ArrayList<>();
    bindings.add(createGroupTestCsvBind());
    bindings.add(createUnitTestCsvBind());

    input.setBindings(bindings);
    return input;
  }

  protected CSVBind createGroupTestCsvBind() {
    CSVBind groupTestBinding =
        createCsvBinding(
            "groupTest", null, String.format("self.name = :%s", CSV_GROUP_TEST_COLUMN_NAME));
    groupTestBinding.setBindings(
        Arrays.asList(createCsvBinding("name", CSV_GROUP_TEST_COLUMN_NAME, null)));
    return groupTestBinding;
  }

  protected CSVBind createUnitTestCsvBind() {
    CSVBind unitTestBinding =
        createCsvBinding(
            "unitTest", null, String.format("self.name = :%s", CSV_UNIT_TEST_COLUMN_NAME));
    unitTestBinding.setBindings(
        Arrays.asList(createCsvBinding("name", CSV_UNIT_TEST_COLUMN_NAME, null)));
    return unitTestBinding;
  }

  protected CSVInput createCsvInput(final String inputSourceName, Class<?> klass) {
    final XStream stream = XStreamUtils.createXStream();
    stream.processAnnotations(CSVInput.class);
    CSVInput input = (CSVInput) stream.fromXML("<input />");
    input.setFileName(inputSourceName);
    input.setSeparator(CSV_SEPERATOR);
    input.setTypeName(klass.getName());
    return input;
  }

  protected CSVBind createCsvBinding(String field, String column, String search) {
    CSVBind csvBind = new CSVBind();
    csvBind.setField(field);
    csvBind.setColumn(column);
    csvBind.setSearch(search);
    return csvBind;
  }

  protected int getStartRow(DataReaderService reader, String sheetName) {
    int count = 0;
    int totalLines = reader.getTotalLines(sheetName);
    for (int i = 0; i < totalLines; i++) {
      String[] values = reader.read(sheetName, i, 1);
      if (isEmptyRow(values)
          || UNIT_NAME_CELL_KEY.equals(values[0])
          || GROUP_CELL_KEY.equals(values[0])) {
        count++;
      } else {
        break;
      }
    }
    return count;
  }

  protected Boolean isEmptyRow(String[] values) {
    if (ObjectUtils.isEmpty(values)) {
      return true;
    }
    for (String value : values) {
      if (StringUtils.notBlank(value)) {
        return false;
      }
    }
    return true;
  }

  protected File getTempDirectory(MetaFile importFile) {
    final File tempPath = MetaFiles.getPath("tmp").toFile();
    File dataDir =
        new File(tempPath, importFile.getFileName() + " " + LocalDateTime.now() + "data-dir");
    dataDir.mkdirs();
    return dataDir;
  }
}
