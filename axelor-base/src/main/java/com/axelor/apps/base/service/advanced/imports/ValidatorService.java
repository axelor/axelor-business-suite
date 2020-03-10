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
import com.axelor.apps.base.db.repo.FileTabRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.readers.DataReaderFactory;
import com.axelor.apps.base.service.readers.DataReaderService;
import com.axelor.common.Inflector;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.repo.MetaJsonFieldRepository;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class ValidatorService {

  public static final String STRING = "String";
  public static final String INTEGER = "Integer";
  public static final String LONG = "Long";
  public static final String BIG_DECIMAL = "BigDecimal";
  public static final String BOOLEAN = "Boolean";
  public static final String ZONED_DATE_TIME = "ZonedDateTime";
  public static final String LOCAL_DATE_TIME = "LocalDateTime";
  public static final String LOCAL_TIME = "LocalTime";
  public static final String LOCAL_DATE = "LocalDate";

  private Map<String, Object> titleMap;

  private Map<String, Object> fieldMap;

  @Inject private MetaFiles metaFiles;

  @Inject private AdvancedImportService advancedImportService;

  @Inject private FileTabRepository fileTabRepo;

  @Inject private ActionService actionService;

  @Inject private DataReaderFactory dataReaderFactory;

  @Inject private LogService logService;

  @Inject private MetaJsonFieldRepository metaJsonFieldRepo;

  public boolean validate(AdvancedImport advancedImport)
      throws AxelorException, IOException, ClassNotFoundException {

    if (advancedImport.getImportFile() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ADVANCED_IMPORT_NO_IMPORT_FILE));
    }

    if (advancedImport.getAttachment() != null
        && !Files.getFileExtension(advancedImport.getAttachment().getFileName()).equals("zip")) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ADVANCED_IMPORT_ATTACHMENT_FORMAT));
    }

    String extension = Files.getFileExtension(advancedImport.getImportFile().getFileName());

    if (extension == null
        || (!extension.equals("xlsx") && !extension.equals("xls") && !extension.equals("csv"))) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ADVANCED_IMPORT_FILE_FORMAT_INVALID));
    }

    DataReaderService reader = dataReaderFactory.getDataReader(extension);
    reader.initialize(advancedImport.getImportFile(), advancedImport.getFileSeparator());

    return validate(reader, advancedImport);
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public boolean validate(DataReaderService reader, AdvancedImport advancedImport)
      throws IOException, ClassNotFoundException, AxelorException {

    boolean isLog = false;
    String[] sheets = reader.getSheetNames();

    this.validateTab(sheets, advancedImport);

    boolean isConfig = advancedImport.getIsConfigInFile();
    boolean isTabConfig = advancedImport.getIsFileTabConfigAdded();

    sortFileTabList(advancedImport.getFileTabList());

    for (FileTab fileTab : advancedImport.getFileTabList()) {
      if (!Arrays.stream(sheets).anyMatch(sheet -> sheet.equals(fileTab.getName()))) {
        continue;
      }

      fieldMap = new HashMap<>();
      titleMap = new HashMap<>();
      String sheet = fileTab.getName();
      logService.initialize(sheet);

      this.validateModel(fileTab);

      int tabConfigRowCount = 0;
      int totalLines = reader.getTotalLines(fileTab.getName());

      if (isConfig) {
        String[] objectRow = reader.read(sheet, 0, 0);
        if (isTabConfig) {
          tabConfigRowCount =
              advancedImportService.getTabConfigRowCount(sheet, reader, totalLines, objectRow);
        }
        this.validateObject(objectRow, fileTab, isTabConfig);
      }
      this.validateSearchFields(fileTab);
      this.validateObjectRequiredFields(fileTab);
      this.validateFieldAndData(reader, sheet, fileTab, isConfig, isTabConfig, tabConfigRowCount);
      this.validateActions(fileTab);

      if (fileTab.getValidationLog() != null) {
        fileTab.setValidationLog(null);
      }

      if (logService.isLogGenerated()) {
        logService.write();
        logService.close();

        File logFile = logService.getLogFile();
        fileTab.setValidationLog(
            metaFiles.upload(new FileInputStream(logFile), sheet + "_err.xlsx"));

        logFile.delete();
        isLog = true;
      } else {
        createCustomObjectSet(
            fileTab.getClass().getName(), fileTab.getMetaModel().getFullName(), 0);
        createCustomButton(fileTab.getClass().getName(), fileTab.getMetaModel().getFullName(), 1);
      }
      fileTabRepo.save(fileTab);
    }
    return isLog;
  }

  protected void validateTab(String[] sheets, AdvancedImport advancedImport)
      throws AxelorException {
    if (sheets == null) {
      return;
    }
    List<String> sheetList = Arrays.asList(sheets);
    List<String> tabList =
        advancedImport
            .getFileTabList()
            .stream()
            .map(tab -> tab.getName())
            .collect(Collectors.toList());

    if (!CollectionUtils.containsAny(tabList, sheetList)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ADVANCED_IMPORT_TAB_ERR));
    }
  }

  protected void validateModel(FileTab fileTab) throws IOException, AxelorException {
    if (fileTab.getMetaModel() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          String.format(I18n.get(IExceptionMessage.ADVANCED_IMPORT_NO_OBJECT), fileTab.getName()));
    }
  }

  protected void validateObject(String[] row, FileTab fileTab, Boolean isTabConfig)
      throws IOException, AxelorException {

    int rowIndex = isTabConfig ? 1 : 0;

    if (isTabConfig && row[0] != null) {
      rowIndex = 0;
    }
    if (row == null || (StringUtils.isBlank(row[rowIndex]))) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ADVANCED_IMPORT_FILE_FORMAT_INVALID));
    }

    String object = row[rowIndex].trim();
    if (StringUtils.containsIgnoreCase(object, "Object")) {
      String model = object.split("\\:")[1].trim();
      if (fileTab.getMetaModel() != null && !fileTab.getMetaModel().getName().equals(model)) {
        logService.addLog(LogService.COMMON_KEY, IExceptionMessage.ADVANCED_IMPORT_LOG_1, rowIndex);
      }
    }
  }

  protected void validateSearchFields(FileTab fileTab) throws AxelorException {
    if ((fileTab.getImportType() == FileFieldRepository.IMPORT_TYPE_FIND
            || fileTab.getImportType() == FileFieldRepository.IMPORT_TYPE_FIND_NEW)
        && CollectionUtils.isEmpty(fileTab.getSearchFieldSet())) {

      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          String.format(
              I18n.get(IExceptionMessage.ADVANCED_IMPORT_6), fileTab.getMetaModel().getName()));
    }
  }

  protected void validateObjectRequiredFields(FileTab fileTab)
      throws ClassNotFoundException, IOException, AxelorException {

    if (CollectionUtils.isEmpty(fileTab.getFileFieldList())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          String.format(I18n.get(IExceptionMessage.ADVANCED_IMPORT_NO_FIELDS), fileTab.getName()));
    }

    List<String> fieldList = new ArrayList<String>();

    for (FileField fileField : fileTab.getFileFieldList()) {
      if (fileField.getImportField() != null) {
        fieldList.add(fileField.getImportField().getName());

      } else {
        logService.addLog(
            IExceptionMessage.ADVANCED_IMPORT_LOG_2, fileField.getColumnTitle(), null);
      }
    }

    if (fileTab.getImportType() == FileFieldRepository.IMPORT_TYPE_FIND) {
      return;
    }

    if (fileTab.getMetaModel() != null) {
      Mapper mapper = advancedImportService.getMapper(fileTab.getMetaModel().getFullName());
      Model obj = null;
      try {
        obj = (Model) Class.forName(fileTab.getMetaModel().getFullName()).newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
        TraceBackService.trace(e);
      }

      for (Property prop : mapper.getProperties()) {
        if (prop.isRequired() && !fieldList.contains(prop.getName())) {
          if (obj != null && mapper.get(obj, prop.getName()) != null) {
            continue;
          }
          logService.addLog(IExceptionMessage.ADVANCED_IMPORT_LOG_3, prop.getName(), null);
        }
      }
    }
  }

  protected void validateFieldAndData(
      DataReaderService reader,
      String sheet,
      FileTab fileTab,
      boolean isConfig,
      boolean isTabConfig,
      int tabConfigRowCount)
      throws ClassNotFoundException, IOException {

    AdvancedImport advancedImport = fileTab.getAdvancedImport();
    Map<String, Object> map = isConfig ? fieldMap : titleMap;
    int linesToIgnore = advancedImport.getNbOfFirstLineIgnore();
    int startIndex = isConfig ? 1 : linesToIgnore;

    String[] row = reader.read(sheet, startIndex, 0);
    if (row == null) {
      return;
    }

    sortFileFieldList(fileTab.getFileFieldList());

    int rowCount = row.length;
    for (int cell = 0; cell < rowCount; cell++) {
      String value = row[cell];
      if (Strings.isNullOrEmpty(value)) {
        continue;
      }
      value = value.trim();
      map.put(isConfig ? value.contains("(") ? value.split("\\(")[0] : value : value, cell);
      if (cell == row.length - 1) {
        this.validateFields(startIndex, isConfig, fileTab);
      }
    }

    if (!advancedImport.getIsValidateValue()) {
      return;
    }

    int totalLines = reader.getTotalLines(sheet);

    startIndex =
        isConfig
            ? tabConfigRowCount + 3
            : fileTab.getAdvancedImport().getIsHeader() ? linesToIgnore + 1 : linesToIgnore;

    for (int line = startIndex; line < totalLines; line++) {
      String[] dataRow = reader.read(sheet, line, row.length);
      if (dataRow == null) {
        continue;
      }
      this.validateData(dataRow, line, isConfig, fileTab);
    }
  }

  protected void validateFields(int line, boolean isConfig, FileTab fileTab)
      throws IOException, ClassNotFoundException {

    List<String> relationalFieldList =
        fileTab
            .getFileFieldList()
            .stream()
            .filter(field -> !Strings.isNullOrEmpty(field.getSubImportField()))
            .map(field -> field.getImportField().getName() + "." + field.getSubImportField())
            .collect(Collectors.toList());

    for (FileField fileField : fileTab.getFileFieldList()) {
      MetaField importField = fileField.getImportField();

      if (importField != null && Strings.isNullOrEmpty(fileField.getSubImportField())) {
        if (importField.getRelationship() != null) {
          logService.addLog(IExceptionMessage.ADVANCED_IMPORT_LOG_4, importField.getName(), line);
        }

        this.validateImportRequiredField(
            line,
            Class.forName(fileTab.getMetaModel().getFullName()),
            importField.getName(),
            fileField,
            null);

        this.validateDateField(line, fileField);

      } else if (!Strings.isNullOrEmpty(fileField.getSubImportField())) {

        Mapper mapper = advancedImportService.getMapper(importField.getMetaModel().getFullName());
        Property parentProp = mapper.getProperty(importField.getName());
        if (parentProp == null) {
          return;
        }

        Property subProperty = this.getAndValidateSubField(line, parentProp, fileField, false);

        if (subProperty != null) {
          this.validateImportRequiredField(
              line, subProperty.getEntity(), subProperty.getName(), fileField, relationalFieldList);

          this.validateDateField(line, fileField);
        }
      }
    }
  }

  protected void validateDateField(int line, FileField fileField) throws IOException {

    String type = fileField.getTargetType();
    Integer rowNum = fileField.getIsMatchWithFile() ? line : null;
    if (!Strings.isNullOrEmpty(type)
        && (type.equals(LOCAL_DATE)
            || type.equals(LOCAL_DATE_TIME)
            || type.equals(LOCAL_TIME)
            || type.equals(ZONED_DATE_TIME))) {

      String field = getField(fileField);

      if (Strings.isNullOrEmpty(fileField.getDateFormat())
          && Strings.isNullOrEmpty(fileField.getExpression())) {

        logService.addLog(IExceptionMessage.ADVANCED_IMPORT_LOG_6, field, rowNum);
      }
    }
  }

  protected void validateImportRequiredField(
      int line,
      Class<?> model,
      String fieldName,
      FileField fileField,
      List<String> relationalFieldList)
      throws IOException, ClassNotFoundException {

    Mapper mapper = advancedImportService.getMapper(model.getName());
    String field = getField(fileField);

    Integer rowNum = fileField.getIsMatchWithFile() ? line : null;
    int importType = fileField.getImportType();

    for (Property prop : mapper.getProperties()) {
      if (prop.isRequired()) {
        if (prop.getName().equals(fieldName)
            && importType == FileFieldRepository.IMPORT_TYPE_IGNORE_EMPTY) {

          logService.addLog(IExceptionMessage.ADVANCED_IMPORT_LOG_5, field, rowNum);

        } else if ((importType == FileFieldRepository.IMPORT_TYPE_FIND_NEW
                || importType == FileFieldRepository.IMPORT_TYPE_NEW)
            && field.contains(".")
            && !fileField.getTargetType().equals("MetaFile")) {

          String newField = StringUtils.substringBeforeLast(field, ".");
          newField = newField + "." + prop.getName();

          if (!relationalFieldList.contains(newField)) {
            logService.addLog(IExceptionMessage.ADVANCED_IMPORT_LOG_3, newField, null);
          }
        }
      }
    }
  }

  protected void validateData(String[] dataRow, int line, boolean isConfig, FileTab fileTab)
      throws IOException, ClassNotFoundException {

    Map<String, Object> map = isConfig ? fieldMap : titleMap;

    for (int fieldIndex = 0; fieldIndex < fileTab.getFileFieldList().size(); fieldIndex++) {
      FileField fileField = fileTab.getFileFieldList().get(fieldIndex);

      if (!fileField.getIsMatchWithFile() || !Strings.isNullOrEmpty(fileField.getExpression())) {
        continue;
      }

      Mapper mapper = null;
      Property property = null;

      String key = null;
      if (isConfig) {
        key = this.getField(fileField);
      } else {
        key = fileField.getColumnTitle();
      }

      int cellIndex = 0;
      if (map.containsKey(key)) {
        cellIndex = (int) map.get(key);
      }

      cellIndex =
          (!isConfig && !fileTab.getAdvancedImport().getIsHeader()) ? fieldIndex : cellIndex;

      if (fileField.getImportField() != null) {
        mapper =
            advancedImportService.getMapper(
                fileField.getImportField().getMetaModel().getFullName());
        property = mapper.getProperty(fileField.getImportField().getName());
      }

      if (property != null && Strings.isNullOrEmpty(fileField.getSubImportField())) {
        if (this.validateDataRequiredField(
            dataRow,
            cellIndex,
            line,
            Class.forName(fileTab.getMetaModel().getFullName()),
            property.getName(),
            fileField)) {

          continue;
        }

        if (!Strings.isNullOrEmpty(property.getSelection())
            && fileField.getForSelectUse() != FileFieldRepository.SELECT_USE_VALUES) {
          continue;
        }

        this.validateDataType(
            dataRow, cellIndex, line, property.getJavaType().getSimpleName(), fileField);

      } else if (!Strings.isNullOrEmpty(fileField.getSubImportField())) {

        Property subProperty = this.getAndValidateSubField(line, property, fileField, true);

        if (subProperty != null) {
          if (this.validateDataRequiredField(
              dataRow,
              cellIndex,
              line,
              subProperty.getEntity(),
              subProperty.getName(),
              fileField)) {

            continue;
          }

          if (!Strings.isNullOrEmpty(subProperty.getSelection())
              && fileField.getForSelectUse() != FileFieldRepository.SELECT_USE_VALUES) {
            continue;
          }

          this.validateDataType(
              dataRow, cellIndex, line, subProperty.getJavaType().getSimpleName(), fileField);
        }
      }
    }
  }

  protected boolean validateDataRequiredField(
      String row[], int cell, int line, Class<?> model, String fieldName, FileField fileField)
      throws IOException, ClassNotFoundException {

    boolean flag = false;
    String field = getField(fileField);
    int importType = fileField.getImportType();

    Mapper mapper = advancedImportService.getMapper(model.getName());
    Property prop = mapper.getProperty(fieldName);
    if (prop != null) {
      if (prop.isRequired()
          && Strings.isNullOrEmpty(row[cell])
          && importType != FileFieldRepository.IMPORT_TYPE_FIND) {

        logService.addLog(IExceptionMessage.ADVANCED_IMPORT_LOG_8, field, line);

      } else if (importType == FileFieldRepository.IMPORT_TYPE_IGNORE_EMPTY) {
        flag = true;
        return flag;
      }
    }
    return flag;
  }

  protected Property getAndValidateSubField(
      int line, Property parentProp, FileField fileField, boolean isLog)
      throws IOException, ClassNotFoundException {

    String field = getField(fileField);
    Integer rowNum = fileField.getIsMatchWithFile() ? line : null;
    String[] subFields = fileField.getSubImportField().split("\\.");
    return this.getAndValidateSubField(subFields, 0, rowNum, parentProp, field, isLog);
  }

  public Property getAndValidateSubField(
      String[] subFields,
      int index,
      Integer rowNum,
      Property parentProp,
      String field,
      boolean isLog)
      throws IOException, ClassNotFoundException {

    Property subProperty = null;

    if (parentProp.getTarget() != null) {
      Mapper mapper = advancedImportService.getMapper(parentProp.getTarget().getName());
      Property childProp = mapper.getProperty(subFields[index]);

      if (childProp == null) {
        if (!isLog) {
          logService.addLog(IExceptionMessage.ADVANCED_IMPORT_LOG_7, field, rowNum);
        }
      }

      if (childProp != null && childProp.getTarget() != null) {
        if (index != subFields.length - 1) {
          subProperty =
              this.getAndValidateSubField(subFields, index + 1, rowNum, childProp, field, isLog);
        } else {
          subProperty = childProp;
          if (!isLog) {
            logService.addLog(IExceptionMessage.ADVANCED_IMPORT_LOG_4, field, rowNum);
          }
        }
      } else {
        subProperty = childProp;
      }
    }
    return subProperty;
  }

  protected void validateDataType(
      String[] row, int cell, int line, String type, FileField fileField) throws IOException {

    if (Strings.isNullOrEmpty(row[cell])) {
      return;
    }

    String field = getField(fileField);
    String value = row[cell].trim();

    switch (type) {
      case INTEGER:
      case LONG:
      case BIG_DECIMAL:
        this.checkNumeric(value, line, field, type);
        break;

      case LOCAL_DATE:
      case ZONED_DATE_TIME:
      case LOCAL_DATE_TIME:
      case LOCAL_TIME:
        this.checkDateTime(value, line, type, fileField);
        break;

      case BOOLEAN:
        String boolPat = "(true|false|1|0|no|yes|n|y)";

        if (!value.matches(boolPat)) {
          logService.addLog(
              IExceptionMessage.ADVANCED_IMPORT_LOG_9, field + "(" + type + ")", line);
        }
        break;

      default:
        if (!type.equals(STRING)) {
          try {
            new BigInteger(value);
          } catch (Exception e) {
            logService.addLog(
                IExceptionMessage.ADVANCED_IMPORT_LOG_9, field + "(" + type + ")", line);
          }
        }
        break;
    }
  }

  protected void checkNumeric(String value, int line, String field, String type)
      throws IOException {

    switch (type) {
      case INTEGER:
        try {
          Integer.parseInt(value);
        } catch (NumberFormatException e) {
          logService.addLog(
              IExceptionMessage.ADVANCED_IMPORT_LOG_9, field + "(" + type + ")", line);
        }
        break;

      case LONG:
        try {
          Long.parseLong(value);
        } catch (NumberFormatException e) {
          logService.addLog(
              IExceptionMessage.ADVANCED_IMPORT_LOG_9, field + "(" + type + ")", line);
        }
        break;

      case BIG_DECIMAL:
        try {
          new BigDecimal(value);
        } catch (NumberFormatException e) {
          logService.addLog(
              IExceptionMessage.ADVANCED_IMPORT_LOG_9, field + "(" + type + ")", line);
        }
        break;
    }
  }

  protected void checkDateTime(String value, int line, String type, FileField fileField)
      throws IOException {

    if (!Strings.isNullOrEmpty(fileField.getDateFormat())
        && Strings.isNullOrEmpty(fileField.getExpression())) {

      String pattern = fileField.getDateFormat().trim();
      try {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);

        switch (type) {
          case LOCAL_DATE:
            LocalDate.parse(value, formatter);
            break;

          case LOCAL_TIME:
            LocalTime.parse(value, formatter);
            break;

          case LOCAL_DATE_TIME:
            LocalDateTime.parse(value, formatter);
            break;

          case ZONED_DATE_TIME:
            ZonedDateTime.parse(value, formatter);
            break;
        }
      } catch (DateTimeParseException e) {
        logService.addLog(
            IExceptionMessage.ADVANCED_IMPORT_LOG_9, getField(fileField) + "(" + type + ")", line);
      }
    }
  }

  protected void validateActions(FileTab fileTab) {
    String actions = fileTab.getActions();
    if (StringUtils.isBlank(actions)) {
      return;
    }
    if (!actionService.validate(actions)) {
      logService.addLog(
          LogService.COMMON_KEY,
          String.format(IExceptionMessage.ADVANCED_IMPORT_LOG_10, fileTab.getMetaModel().getName()),
          1);
    }
  }

  public String getField(FileField fileField) {
    String field =
        !Strings.isNullOrEmpty(fileField.getSubImportField())
            ? fileField.getImportField().getName() + "." + fileField.getSubImportField().trim()
            : fileField.getImportField().getName();

    return field;
  }

  public void sortFileTabList(List<FileTab> fileTabList) {
    fileTabList.sort((tab1, tab2) -> tab1.getSequence().compareTo(tab2.getSequence()));
  }

  public void sortFileFieldList(List<FileField> fileFieldList) {
    fileFieldList.sort((field1, field2) -> field1.getSequence().compareTo(field2.getSequence()));
  }

  public void addLog(BufferedWriter writer, String errorType, String log) throws IOException {
    writer.write(I18n.get(errorType) + ":   " + log);
    writer.newLine();
  }

  @Transactional(rollbackOn = {Exception.class})
  public void createCustomObjectSet(String modelName, String targetModelName, int sequence) {

    String simpleModelName = StringUtils.substringAfterLast(targetModelName, ".");
    String fieldName = Inflector.getInstance().camelize(simpleModelName, true) + "Set";
    String viewName = Inflector.getInstance().dasherize(simpleModelName);

    if (metaJsonFieldRepo
            .all()
            .filter(
                "self.type = ?1 AND self.model = ?2 AND self.targetModel = ?3",
                "many-to-many",
                modelName,
                targetModelName)
            .count()
        > 0) {
      return;
    }

    MetaJsonField jsonField = new MetaJsonField();
    jsonField.setName(fieldName);
    jsonField.setType("many-to-many");
    jsonField.setTitle(Inflector.getInstance().titleize(simpleModelName));
    jsonField.setSequence(sequence);
    jsonField.setModel(modelName);
    jsonField.setModelField("attrs");
    jsonField.setTargetModel(targetModelName);
    jsonField.setGridView(viewName + "-grid");
    jsonField.setFormView(viewName + "-form");
    jsonField.setWidgetAttrs("{\"colSpan\": \"12\"}");
    jsonField.setShowIf(fieldName + " != null && $record.advancedImport.statusSelect > 0");

    metaJsonFieldRepo.save(jsonField);
  }

  @Transactional(rollbackOn = {Exception.class})
  public void createCustomButton(String modelName, String targetModelName, int sequence) {

    String simpleModelName = StringUtils.substringAfterLast(targetModelName, ".");
    String fieldName = Inflector.getInstance().camelize(simpleModelName, true) + "Set";
    String buttonName = "show" + fieldName + "Btn";

    if (metaJsonFieldRepo
            .all()
            .filter(
                "self.name = ?1 AND self.type = ?2 AND self.model = ?3",
                buttonName,
                "button",
                modelName)
            .count()
        > 0) {
      return;
    }

    MetaJsonField jsonField = new MetaJsonField();
    jsonField.setName(buttonName);
    jsonField.setType("button");
    jsonField.setTitle("Show " + Inflector.getInstance().titleize(simpleModelName));
    jsonField.setSequence(sequence);
    jsonField.setModel(modelName);
    jsonField.setModelField("attrs");
    jsonField.setOnClick("action-file-tab-method-show-record,close");
    jsonField.setWidgetAttrs("{\"colSpan\": \"4\"}");
    jsonField.setShowIf(fieldName + " != null && $record.advancedImport.statusSelect > 0");

    metaJsonFieldRepo.save(jsonField);
  }
}
