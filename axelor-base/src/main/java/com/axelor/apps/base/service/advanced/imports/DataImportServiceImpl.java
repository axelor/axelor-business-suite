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
package com.axelor.apps.base.service.advanced.imports;

import com.axelor.apps.base.db.AdvancedImport;
import com.axelor.apps.base.db.FileField;
import com.axelor.apps.base.db.FileTab;
import com.axelor.apps.base.db.repo.FileFieldRepository;
import com.axelor.apps.base.service.imports.listener.ImporterListener;
import com.axelor.apps.base.service.readers.DataReaderFactory;
import com.axelor.apps.base.service.readers.DataReaderService;
import com.axelor.apps.tool.service.TranslationService;
import com.axelor.common.Inflector;
import com.axelor.data.XStreamUtils;
import com.axelor.data.adapter.DataAdapter;
import com.axelor.data.adapter.JavaTimeAdapter;
import com.axelor.data.csv.CSVBind;
import com.axelor.data.csv.CSVConfig;
import com.axelor.data.csv.CSVImporter;
import com.axelor.data.csv.CSVInput;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.MetaSelect;
import com.axelor.meta.db.MetaSelectItem;
import com.axelor.meta.db.repo.MetaSelectItemRepository;
import com.axelor.meta.db.repo.MetaSelectRepository;
import com.axelor.rpc.Context;
import com.axelor.rpc.JsonContext;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.opencsv.CSVWriter;
import com.thoughtworks.xstream.XStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;

public class DataImportServiceImpl implements DataImportService {

  private static final char CSV_SEPRATOR = ';';
  private static final String INPUT_CALLABLE =
      "com.axelor.csv.script.ImportAdvancedImport:importGeneral";
  private static final String BIND_CALLABLE_DATE =
      "call:com.axelor.csv.script.ImportDateTime:importDate";
  private static final String BIND_CALLABLE_DATE_TIME =
      "call:com.axelor.csv.script.ImportDateTime:importDateTime";
  private static final String BIND_CALLABLE_META_FILE =
      "call:com.axelor.csv.script.ImportAdvancedImport:importPicture";
  private static final String MANY_TO_MANY = "ManyToMany";
  private static final String SPLIT = ".split('\\\\";
  private static final String AS_LIST = "') as List";
  private static final String REPLACE_SYMBOL = "$";

  private CSVInput csvInput;

  private Map<String, CSVBind> parentBindMap;
  private Map<String, CSVBind> subBindMap;
  private Map<String, Object> importContext;
  private Map<String, Object> fieldMap;
  private Map<String, Object> titleMap;
  private Map<String, DataAdapter> adapterMap;

  private List<String> ifList;

  private String fullFieldName;
  private String language;

  private File dataDir;

  private int counter = 1;

  private Inflector inflector = Inflector.getInstance();

  @Inject DataReaderFactory dataReaderFactory;

  @Inject private MetaFiles metaFiles;

  @Inject private MetaSelectItemRepository metaSelectItemRepo;

  @Inject private TranslationService translationService;

  @Inject private ValidatorService validatorService;

  @Inject private AdvancedImportService advancedImportService;

  @Inject private MetaSelectRepository metaSelectRepo;

  @Override
  public MetaFile importData(AdvancedImport advancedImport)
      throws IOException, AxelorException, ClassNotFoundException {

    adapterMap = new HashMap<String, DataAdapter>();
    importContext = new HashMap<String, Object>();
    language = advancedImport.getLanguageSelect();
    dataDir = Files.createTempDir();

    String extension = Files.getFileExtension(advancedImport.getImportFile().getFileName());

    DataReaderService reader = dataReaderFactory.getDataReader(extension);
    reader.initialize(advancedImport.getImportFile(), advancedImport.getFileSeparator());

    List<CSVInput> inputs = this.process(reader, advancedImport);

    if (advancedImport.getAttachment() != null) {
      this.processAttachments(advancedImport.getAttachment());
    }

    MetaFile logFile = this.importData(inputs);
    FileUtils.forceDelete(dataDir);
    return logFile;
  }

  private List<CSVInput> process(DataReaderService reader, AdvancedImport advancedImport)
      throws AxelorException, IOException, ClassNotFoundException {

    String[] sheets = reader.getSheetNames();

    boolean isConfig = advancedImport.getIsConfigInFile();
    int linesToIgnore = advancedImport.getNbOfFirstLineIgnore();
    boolean isTabConfig = advancedImport.getIsFileTabConfigAdded();
    List<CSVInput> inputList = new ArrayList<CSVInput>();

    validatorService.sortFileTabList(advancedImport.getFileTabList());

    for (FileTab fileTab : advancedImport.getFileTabList()) {
      if (!Arrays.stream(sheets).anyMatch(sheet -> sheet.equals(fileTab.getName()))) {
        continue;
      }

      this.initializeVariables();

      String fileName = createDataFileName(fileTab);
      csvInput = this.createCSVInput(fileTab, fileName);
      ifList = new ArrayList<String>();

      try (CSVWriter csvWriter =
          new CSVWriter(new FileWriter(new File(dataDir, fileName)), CSV_SEPRATOR)) {

        int totalLines = reader.getTotalLines(fileTab.getName());
        if (totalLines == 0) {
          continue;
        }

        Mapper mapper = advancedImportService.getMapper(fileTab.getMetaModel().getFullName());
        List<String[]> allLines = new ArrayList<String[]>();
        int startIndex = isConfig ? 1 : linesToIgnore;

        String[] row = reader.read(fileTab.getName(), startIndex, 0);
        String[] headers = this.createHeader(row, fileTab, isConfig, mapper);
        allLines.add(headers);

        int tabConfigRowCount = 0;
        if (isTabConfig) {
          String objectRow[] = reader.read(fileTab.getName(), 0, 0);
          tabConfigRowCount =
              advancedImportService.getTabConfigRowCount(
                  fileTab.getName(), reader, totalLines, objectRow);
        }
        startIndex =
            isConfig
                ? tabConfigRowCount + 3
                : fileTab.getAdvancedImport().getIsHeader() ? linesToIgnore + 1 : linesToIgnore;

        for (int line = startIndex; line < totalLines; line++) {
          String[] dataRow = reader.read(fileTab.getName(), line, row.length);
          if (dataRow == null) {
            continue;
          }
          String[] data = this.createData(dataRow, fileTab, isConfig, mapper);
          allLines.add(data);
        }
        csvWriter.writeAll(allLines);
        csvWriter.flush();
      }

      inputList.add(csvInput);
      importContext.put("ifConditions" + fileTab.getId(), ifList);
      importContext.put("jsonContextValues" + fileTab.getId(), createJsonContext(fileTab));
      importContext.put("actionsToApply" + fileTab.getId(), fileTab.getActions());
    }
    return inputList;
  }

  protected void initializeVariables() {
    parentBindMap = new HashMap<>();
    subBindMap = new HashMap<>();
    fullFieldName = null;
    fieldMap = new HashMap<>();
    titleMap = new HashMap<>();
  }

  protected String[] createHeader(String[] row, FileTab fileTab, boolean isConfig, Mapper mapper)
      throws ClassNotFoundException {

    Map<String, Object> map = isConfig ? fieldMap : titleMap;
    int rowCount = row.length;
    for (int cell = 0; cell < rowCount; cell++) {
      if (Strings.isNullOrEmpty(row[cell])) {
        continue;
      }
      String value = row[cell].trim();
      map.put(isConfig ? value.contains("(") ? value.split("\\(")[0] : value : value, cell);
    }

    validatorService.sortFileFieldList(fileTab.getFileFieldList());
    List<String> headers = new ArrayList<>();
    List<CSVBind> allBindings = new ArrayList<CSVBind>();
    int cnt = 0;
    Map<String, Object> searchMap = new HashMap<String, Object>();

    for (FileField fileField : fileTab.getFileFieldList()) {
      if (fileField.getImportType() == FileFieldRepository.IMPORT_TYPE_IGNORE_EMPTY) {
        continue;
      }

      String key = (isConfig) ? validatorService.getField(fileField) : fileField.getColumnTitle();
      String column = ("cell" + (cnt + 1));

      if (!CollectionUtils.isEmpty(fileTab.getSearchFieldSet())
          && fileTab.getSearchFieldSet().contains(fileField)
          && fileTab.getImportType() != FileFieldRepository.IMPORT_TYPE_NEW) {
        searchMap.put(validatorService.getField(fileField), column);
      }

      if ((map.containsKey(key) || (!isConfig && !fileTab.getAdvancedImport().getIsHeader()))
          && fileField.getIsMatchWithFile()) {
        headers.add(column);
      }

      allBindings = this.createCSVBinding(column, fileField, mapper, allBindings);
      cnt++;
    }

    CSVBind fileTabBind = new CSVBind();
    fileTabBind.setField("fileTabId");
    fileTabBind.setExpression(fileTab.getId().toString());
    allBindings.add(fileTabBind);

    for (Entry<String, Object> entry : searchMap.entrySet()) {
      if (Strings.isNullOrEmpty(csvInput.getSearch())) {
        csvInput.setSearch("self." + entry.getKey() + " = :" + entry.getValue());
      } else {
        csvInput.setSearch(
            csvInput.getSearch() + " AND self." + entry.getKey() + " = :" + entry.getValue());
      }
    }
    csvInput.setBindings(allBindings);

    return headers.stream().toArray(String[]::new);
  }

  protected String[] createData(String[] dataRow, FileTab fileTab, boolean isConfig, Mapper mapper)
      throws ClassNotFoundException {

    Map<String, Object> map = isConfig ? fieldMap : titleMap;
    List<String> dataList = new ArrayList<String>();

    for (int fieldIndex = 0; fieldIndex < fileTab.getFileFieldList().size(); fieldIndex++) {
      FileField fileField = fileTab.getFileFieldList().get(fieldIndex);

      String key = null;
      if (isConfig) {
        key = validatorService.getField(fileField);
      } else {
        key = fileField.getColumnTitle();
      }

      if (!fileField.getIsMatchWithFile()
          || fileField.getImportType() == FileFieldRepository.IMPORT_TYPE_IGNORE_EMPTY) {
        continue;
      }

      int cell = 0;
      if (map.containsKey(key)) {
        cell = (int) map.get(key);
      }

      cell = (!isConfig && !fileTab.getAdvancedImport().getIsHeader()) ? fieldIndex : cell;

      if (Strings.isNullOrEmpty(dataRow[cell])) {
        dataList.add(
            !Strings.isNullOrEmpty(fileField.getDefaultIfNotFound())
                ? fileField.getDefaultIfNotFound().trim()
                : "");
        continue;
      }

      String dataCell = dataRow[cell].trim();
      this.checkAndWriteData(dataCell, fileTab.getMetaModel(), fileField, mapper, dataList);
    }
    return dataList.stream().toArray(String[]::new);
  }

  protected void checkAndWriteData(
      String dataCell, MetaModel model, FileField fileField, Mapper mapper, List<String> dataList)
      throws ClassNotFoundException {

    Property parentProp = mapper.getProperty(fileField.getImportField().getName());

    if (Strings.isNullOrEmpty(fileField.getSubImportField())) {
      if (!Strings.isNullOrEmpty(parentProp.getSelection())) {
        this.writeSelectionData(
            parentProp.getSelection(), dataCell, fileField.getForSelectUse(), dataList);

      } else {
        dataList.add(dataCell);
      }
    } else {
      String[] subFields = fileField.getSubImportField().split("\\.");

      this.checkSubFieldAndWriteData(
          subFields, 0, parentProp, dataCell, fileField.getForSelectUse(), dataList);
    }
  }

  protected void checkSubFieldAndWriteData(
      String[] subFields,
      int index,
      Property parentProp,
      String dataCell,
      int forSelectUse,
      List<String> dataList)
      throws ClassNotFoundException {

    if (index < subFields.length) {
      if (parentProp.getTarget() != null) {
        Mapper mapper = advancedImportService.getMapper(parentProp.getTarget().getName());
        Property childProp = mapper.getProperty(subFields[index]);

        if (childProp != null && childProp.getTarget() != null) {
          this.checkSubFieldAndWriteData(
              subFields, index + 1, childProp, dataCell, forSelectUse, dataList);

        } else {
          if (!Strings.isNullOrEmpty(childProp.getSelection())) {
            this.writeSelectionData(childProp.getSelection(), dataCell, forSelectUse, dataList);
          } else {
            dataList.add(dataCell);
          }
        }
      }
    }
  }

  protected void writeSelectionData(
      String selection, String dataCell, int forSelectUse, List<String> dataList) {

    String value = this.getSelectionValue(selection, dataCell, forSelectUse);
    if (value == null) {
      return;
    }
    dataList.add(value);
  }

  protected String getSelectionValue(String selection, String value, int forSelectUse) {

    if (forSelectUse != FileFieldRepository.SELECT_USE_VALUES) {
      String title = null;
      if (forSelectUse == FileFieldRepository.SELECT_USE_TRANSLATED_TITLES) {
        title = translationService.getTranslationKey(value, language);
      } else {
        title = value;
      }

      MetaSelect metaSelect = metaSelectRepo.findByName(selection);
      if (metaSelect == null) {
        return null;
      }

      MetaSelectItem metaSelectItem =
          metaSelectItemRepo
              .all()
              .filter("self.title = ?1 AND self.select.id = ?2", title, metaSelect.getId())
              .fetchOne();

      if (metaSelectItem != null) {
        return metaSelectItem.getValue();
      } else {
        return value;
      }
    } else {
      return value;
    }
  }

  protected CSVInput createCSVInput(FileTab fileTab, String fileName) {
    boolean update = false;
    int importType = fileTab.getImportType();

    if ((importType == FileFieldRepository.IMPORT_TYPE_FIND)
        && !CollectionUtils.isEmpty(fileTab.getSearchFieldSet())) {
      update = true;
    }

    XStream stream = XStreamUtils.createXStream();
    stream.processAnnotations(CSVInput.class);
    CSVInput input = (CSVInput) stream.fromXML("<input update=\"" + update + "\" />");
    input.setFileName(fileName);
    input.setSeparator(CSV_SEPRATOR);
    input.setTypeName(fileTab.getMetaModel().getFullName());
    input.setCallable(INPUT_CALLABLE);
    input.setSearch(null);
    input.setBindings(new ArrayList<>());

    return input;
  }

  protected List<CSVBind> createCSVBinding(
      String column, FileField fileField, Mapper mapper, List<CSVBind> allBindings)
      throws ClassNotFoundException {

    Property prop = mapper.getProperty(fileField.getImportField().getName());
    if (prop == null) {
      return allBindings;
    }

    CSVBind dummyBind = null;
    if (!fileField.getIsMatchWithFile()) {
      dummyBind = this.createCSVBind(null, column, null, null, null, null);
      allBindings.add(0, dummyBind);
    }

    if (Strings.isNullOrEmpty(fileField.getSubImportField())) {

      String expression = this.setExpression(column, fileField, prop);
      String adapter = null;
      String dateFormat = fileField.getDateFormat();
      if (Strings.isNullOrEmpty(expression) && !Strings.isNullOrEmpty(dateFormat)) {
        adapter = this.getAdapter(prop.getJavaType().getSimpleName(), dateFormat.trim());
      }
      CSVBind bind = null;
      if (!fileField.getIsMatchWithFile()) {
        dummyBind.setExpression(expression);
        dummyBind.setAdapter(adapter);
        bind = this.createCSVBind(column, prop.getName(), null, null, null, null);
        this.setImportIf(prop, bind, column);
      } else {
        bind = this.createCSVBind(column, prop.getName(), null, expression, adapter, null);
        this.setImportIf(prop, bind, column);
      }
      allBindings.add(bind);
      this.setSearch(column, prop.getName(), fileField, null);

    } else {
      CSVBind parentBind = null;
      if (parentBindMap.containsKey(prop.getName())) {
        parentBind = parentBindMap.get(prop.getName());

      } else {
        parentBind = this.createCSVBind(null, prop.getName(), null, null, null, true);
        parentBind.setBindings(new ArrayList<>());
        allBindings.add(parentBind);
        parentBindMap.put(prop.getName(), parentBind);
      }

      fullFieldName = prop.getName();
      String[] subFields = fileField.getSubImportField().split("\\.");
      this.createCSVSubBinding(subFields, 0, column, prop, fileField, parentBind, dummyBind);
    }

    if (!Strings.isNullOrEmpty(fileField.getNoImportIf())) {
      String importIf =
          this.convertExpression(
              fileField.getNoImportIf().trim(), fileField.getTargetType(), column);
      ifList.add(importIf);
    }
    return allBindings;
  }

  protected void createCSVSubBinding(
      String[] subFields,
      int index,
      String column,
      Property parentProp,
      FileField fileField,
      CSVBind parentBind,
      CSVBind dummyBind)
      throws ClassNotFoundException {

    if (index < subFields.length) {
      if (parentProp.getTarget() == null) {
        return;
      }
      int importType = fileField.getImportType();
      String relationship = fileField.getRelationship();
      fullFieldName += "." + subFields[index];
      Mapper mapper = advancedImportService.getMapper(parentProp.getTarget().getName());
      Property childProp = mapper.getProperty(subFields[index]);

      if (childProp != null && childProp.getTarget() != null) {
        CSVBind subBind = null;
        if (subBindMap.containsKey(fullFieldName)) {
          subBind = subBindMap.get(fullFieldName);

        } else {
          subBind = this.createCSVBind(null, childProp.getName(), null, null, null, true);
          subBind.setBindings(new ArrayList<>());
          parentBind.getBindings().add(subBind);
          subBindMap.put(fullFieldName, subBind);
        }
        this.createCSVSubBinding(
            subFields, index + 1, column, childProp, fileField, subBind, dummyBind);

      } else {
        String expression = this.setExpression(column, fileField, childProp);
        String adapter = null;
        String dateFormat = fileField.getDateFormat();
        if (Strings.isNullOrEmpty(expression) && !Strings.isNullOrEmpty(dateFormat)) {
          adapter = this.getAdapter(childProp.getJavaType().getSimpleName(), dateFormat.trim());
        }

        if (!fileField.getIsMatchWithFile()) {
          this.createBindForNotMatchWithFile(
              column, importType, dummyBind, expression, adapter, parentBind, childProp);

        } else {
          this.createBindForMatchWithFile(
              column, importType, expression, adapter, relationship, parentBind, childProp);
        }
        this.setSearch(column, childProp.getName(), fileField, parentBind);

        if (importType != FileFieldRepository.IMPORT_TYPE_FIND) {
          parentBind.setUpdate(false);
        }
      }
    }
  }

  protected void createBindForNotMatchWithFile(
      String column,
      int importType,
      CSVBind dummyBind,
      String expression,
      String adapter,
      CSVBind parentBind,
      Property childProp) {

    dummyBind.setExpression(expression);
    dummyBind.setAdapter(adapter);

    if (importType == FileFieldRepository.IMPORT_TYPE_FIND_NEW
        || importType == FileFieldRepository.IMPORT_TYPE_NEW) {
      CSVBind subBind = this.createCSVBind(column, childProp.getName(), null, null, null, null);
      this.setImportIf(childProp, subBind, column);
      parentBind.getBindings().add(subBind);
    }
  }

  protected void createBindForMatchWithFile(
      String column,
      int importType,
      String expression,
      String adapter,
      String relationship,
      CSVBind parentBind,
      Property childProp) {

    if (importType != FileFieldRepository.IMPORT_TYPE_FIND) {
      if (!Strings.isNullOrEmpty(expression)
          && expression.contains(BIND_CALLABLE_META_FILE)
          && importType == FileFieldRepository.IMPORT_TYPE_NEW) {

        parentBind.setExpression(expression);
        return;
      }
      CSVBind subBind =
          this.createCSVBind(column, childProp.getName(), null, expression, adapter, null);
      this.setImportIf(childProp, subBind, column);
      parentBind.getBindings().add(subBind);

    } else {
      if (!Strings.isNullOrEmpty(relationship) && relationship.equals(MANY_TO_MANY)) {
        parentBind.setColumn(column);
        parentBind.setExpression(expression);
      }
    }
  }

  protected void setSearch(String column, String field, FileField fileField, CSVBind bind) {

    int importType = fileField.getImportType();
    String relationship = fileField.getRelationship();
    String splitBy = fileField.getSplitBy();

    if (importType == FileFieldRepository.IMPORT_TYPE_FIND
        || importType == FileFieldRepository.IMPORT_TYPE_FIND_NEW) {

      if (Strings.isNullOrEmpty(bind.getSearch())) {
        if (!Strings.isNullOrEmpty(relationship)
            && relationship.equals(MANY_TO_MANY)
            && !Strings.isNullOrEmpty(splitBy)) {
          bind.setSearch("self." + field + " in :" + column);
        } else {
          bind.setSearch("self." + field + " = :" + column);
        }
      } else {
        bind.setSearch(bind.getSearch() + " AND " + "self." + field + " = :" + column);
      }
    }
  }

  protected String setExpression(String column, FileField fileField, Property prop) {

    String expr = fileField.getExpression();
    String relationship = fileField.getRelationship();
    String splitBy = fileField.getSplitBy();
    String targetType = fileField.getTargetType();
    String expression = null;

    if (!Strings.isNullOrEmpty(expr)) {
      expr = expr.trim();
      if (expr.contains(REPLACE_SYMBOL) && !Strings.isNullOrEmpty(column)) {
        if (!Strings.isNullOrEmpty(relationship) && relationship.equals(MANY_TO_MANY)) {
          expression =
              !Strings.isNullOrEmpty(splitBy)
                  ? expr.replace(REPLACE_SYMBOL, column) + SPLIT + splitBy + AS_LIST
                  : expr.replace(REPLACE_SYMBOL, column);
          return expression;
        }
        return this.convertExpression(expr, targetType, column);

      } else {
        if (!Strings.isNullOrEmpty(relationship) && relationship.equals(MANY_TO_MANY)) {
          expression =
              !Strings.isNullOrEmpty(splitBy) ? expr + SPLIT + splitBy + AS_LIST : "'" + expr + "'";
          return expression;
        }
        return this.createExpression(expr, targetType, prop, fileField.getForSelectUse());
      }
    } else if (!Strings.isNullOrEmpty(relationship)
        && relationship.equals(MANY_TO_MANY)
        && !targetType.equals("MetaFile")) {

      expression =
          (!Strings.isNullOrEmpty(fileField.getSplitBy()))
              ? column + SPLIT + splitBy + AS_LIST
              : null;
      return expression;

    } else if (!Strings.isNullOrEmpty(targetType) && targetType.equals("MetaFile")) {
      expression = BIND_CALLABLE_META_FILE + "(" + column + ", '" + dataDir.toPath() + "')";
      return expression;
    }
    return expression;
  }

  protected String createExpression(String expr, String type, Property prop, int forSelectUse) {
    String expression = null;

    switch (type) {
      case ValidatorService.LOCAL_DATE:
        expression = BIND_CALLABLE_DATE + "('" + expr + "')";
        break;

      case ValidatorService.LOCAL_TIME:
      case ValidatorService.LOCAL_DATE_TIME:
      case ValidatorService.ZONED_DATE_TIME:
        expression = BIND_CALLABLE_DATE_TIME + "('" + expr + "')";
        break;

      case ValidatorService.INTEGER:
      case ValidatorService.BIG_DECIMAL:
      case ValidatorService.LONG:
      case ValidatorService.STRING:
        if (!Strings.isNullOrEmpty(prop.getSelection())) {
          expression = this.getSelectionValue(prop.getSelection(), expr, forSelectUse);
          if (type.equals(ValidatorService.STRING)) {
            expression = "'" + expression + "'";
          }
        } else {
          expression = type.equals(ValidatorService.STRING) ? "'" + expr + "'" : expr;
        }
        break;

      default:
        expression = "'" + expr + "'";
        break;
    }
    return expression;
  }

  protected String convertExpression(String expr, String type, String column) {
    String expression = null;
    switch (type) {
      case ValidatorService.INTEGER:
        expression = expr.replace(REPLACE_SYMBOL, "Integer.parseInt(" + column + ")");
        break;

      case ValidatorService.LONG:
        expression = expr.replace(REPLACE_SYMBOL, "Long.parseLong(" + column + ")");
        break;

      case ValidatorService.BIG_DECIMAL:
        expression = expr.replace(REPLACE_SYMBOL, "new BigDecimal(" + column + ")");
        break;

      case ValidatorService.LOCAL_DATE:
        expression = BIND_CALLABLE_DATE + "('" + expr.replace(REPLACE_SYMBOL, column) + "')";
        break;

      case ValidatorService.LOCAL_TIME:
      case ValidatorService.LOCAL_DATE_TIME:
      case ValidatorService.ZONED_DATE_TIME:
        expression = BIND_CALLABLE_DATE_TIME + "('" + expr.replace(REPLACE_SYMBOL, column) + "')";
        break;

      default:
        expression = expr.replace(REPLACE_SYMBOL, column);
        break;
    }
    return expression;
  }

  protected String getAdapter(String type, String dateFormat) {
    String adapter = null;

    switch (type) {
      case "LocalDate":
        DataAdapter dateAdapter = this.createAdapter("LocalDate", dateFormat);
        adapter = dateAdapter.getName();
        break;

      case "LocalDateTime":
        DataAdapter dateTimeAdapter = this.createAdapter("LocalDateTime", dateFormat);
        adapter = dateTimeAdapter.getName();
        break;

      case "LocalTime":
        DataAdapter timeAdapter = this.createAdapter("LocalTime", dateFormat);
        adapter = timeAdapter.getName();
        break;

      case "ZoneDateTime":
        DataAdapter zonedDateTimeAdapter = this.createAdapter("ZonedDateTime", dateFormat);
        adapter = zonedDateTimeAdapter.getName();
        break;
    }
    return adapter;
  }

  protected DataAdapter createAdapter(String typeName, String format) {
    DataAdapter adapter = adapterMap.get(format);
    if (adapter == null) {
      adapter =
          new DataAdapter(
              ("Adapter" + counter), JavaTimeAdapter.class, "type", typeName, "format", format);
      adapterMap.put(format, adapter);
      counter++;
    }
    return adapter;
  }

  protected CSVBind createCSVBind(
      String column,
      String field,
      String search,
      String expression,
      String adapter,
      Boolean update) {

    CSVBind bind = new CSVBind();
    bind.setColumn(column);
    bind.setField(field);
    bind.setSearch(search);
    bind.setExpression(expression);
    bind.setAdapter(adapter);
    if (update != null) {
      bind.setUpdate(update);
    }

    return bind;
  }

  protected String createDataFileName(FileTab fileTab) {
    String fileName = null;
    MetaModel model = fileTab.getMetaModel();
    Long fileTabId = fileTab.getId();
    try {
      Mapper mapper = advancedImportService.getMapper(model.getFullName());
      fileName =
          inflector.camelize(mapper.getBeanClass().getSimpleName(), true) + fileTabId + ".csv";

    } catch (ClassNotFoundException e) {
      TraceBackService.trace(e);
    }
    return fileName;
  }

  protected void processAttachments(MetaFile attachments) throws ZipException, IOException {

    if (dataDir.isDirectory() && dataDir.list().length == 0) {
      return;
    }

    File attachmentFile = MetaFiles.getPath(attachments).toFile();
    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(attachmentFile))) {
      ZipEntry ze;
      byte[] buffer = new byte[1024];

      while ((ze = zis.getNextEntry()) != null) {
        String fileName = ze.getName();
        File extractFile = new File(dataDir, fileName);

        if (ze.isDirectory()) {
          extractFile.mkdirs();
          continue;
        } else {
          extractFile.getParentFile().mkdirs();
          extractFile.createNewFile();
        }

        try (FileOutputStream fos = new FileOutputStream(extractFile)) {
          int len;
          while ((len = zis.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
          }
        }
        zis.closeEntry();
      }
    }
  }

  protected MetaFile importData(List<CSVInput> inputs) throws IOException {
    if (CollectionUtils.isEmpty(inputs)) {
      return null;
    }

    CSVConfig config = new CSVConfig();
    config.setInputs(inputs);
    if (!CollectionUtils.isEmpty(adapterMap.values())) {
      config.getAdapters().addAll(adapterMap.values());
    }

    CSVImporter importer = new CSVImporter(config, dataDir.getAbsolutePath());
    ImporterListener listener = new ImporterListener("importData");
    importer.addListener(listener);
    importer.setContext(importContext);
    importer.run();

    if (!listener.isImported()) {
      MetaFile logFile = this.createImportLogFile(listener);
      return logFile;
    }
    return null;
  }

  protected void setImportIf(Property prop, CSVBind bind, String column) {
    if (prop.isRequired()) {
      bind.setCondition(column.toString() + "!= null && !" + column.toString() + ".empty");
    }
  }

  protected MetaFile createImportLogFile(ImporterListener listener) throws IOException {

    MetaFile logMetaFile =
        metaFiles.upload(
            new ByteArrayInputStream(listener.getImportLog().getBytes()),
            "importLog-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".log");

    return logMetaFile;
  }

  @Override
  public Map<String, Object> createJsonContext(FileTab fileTab) {

    Class<? extends Model> klass = (Class<? extends Model>) fileTab.getClass();
    Context context = new Context(klass);

    JsonContext jsonContext =
        new JsonContext(context, Mapper.of(klass).getProperty("attrs"), fileTab.getAttrs());

    Map<String, Object> _map = new HashMap<String, Object>();
    _map.put("context", context);
    _map.put("jsonContext", jsonContext);
    return _map;
  }
}
