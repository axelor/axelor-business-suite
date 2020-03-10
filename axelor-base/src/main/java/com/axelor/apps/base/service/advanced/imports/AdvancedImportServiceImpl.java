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
import com.axelor.apps.base.db.repo.AdvancedImportRepository;
import com.axelor.apps.base.db.repo.FileFieldRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.readers.DataReaderFactory;
import com.axelor.apps.base.service.readers.DataReaderService;
import com.axelor.common.Inflector;
import com.axelor.db.EntityHelper;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.rpc.JsonContext;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvancedImportServiceImpl implements AdvancedImportService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String forSelectUseValues = "values";
  private static final String forSelectUseTitles = "titles";
  private static final String forSelectUseTranslatedTitles = "translated titles";

  private Inflector inflector = Inflector.getInstance();

  private List<String> searchFieldList = new ArrayList<>();

  private boolean isTabWithoutConfig = false;

  @Inject private MetaModelRepository metaModelRepo;

  @Inject private MetaFieldRepository metaFieldRepo;

  @Inject private DataReaderFactory dataReaderFactory;

  @Inject private FileFieldService fileFieldService;

  @Inject private AdvancedImportRepository advancedImportRepository;

  @Inject private FileFieldRepository fileFieldRepository;

  @Inject private DataImportService dataImportService;

  @Override
  public boolean apply(AdvancedImport advancedImport)
      throws AxelorException, ClassNotFoundException {

    if (advancedImport.getImportFile() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ADVANCED_IMPORT_NO_IMPORT_FILE));
    }

    String extension = Files.getFileExtension(advancedImport.getImportFile().getFileName());
    if (extension == null
        || (!extension.equals("xlsx") && !extension.equals("xls") && !extension.equals("csv"))) {
      return false;
    }

    DataReaderService reader = dataReaderFactory.getDataReader(extension);
    reader.initialize(advancedImport.getImportFile(), advancedImport.getFileSeparator());

    return this.process(reader, advancedImport);
  }

  @Transactional
  public boolean process(DataReaderService reader, AdvancedImport advancedImport)
      throws AxelorException, ClassNotFoundException {

    boolean isValid = true;

    if (!CollectionUtils.isEmpty(advancedImport.getFileTabList())) {
      advancedImport.getFileTabList().stream().forEach(tab -> tab.clearFileFieldList());
      advancedImport.clearFileTabList();
    }

    String[] sheets = reader.getSheetNames();
    boolean isConfig = advancedImport.getIsConfigInFile();
    boolean isTabConfig = advancedImport.getIsFileTabConfigAdded();
    boolean isHeader = advancedImport.getIsHeader();
    int linesToIgnore = advancedImport.getNbOfFirstLineIgnore();
    int tabConfigRowCount = 0;
    int startIndex = isConfig ? 0 : linesToIgnore;
    int fileTabSequence = 1;

    for (String sheet : sheets) {
      int totalLines = reader.getTotalLines(sheet);
      if (totalLines == 0) {
        continue;
      }

      FileTab fileTab = new FileTab();
      fileTab.setName(sheet);
      fileTab.setSequence(fileTabSequence);
      fileTabSequence++;

      String[] objectRow = reader.read(sheet, startIndex, 0);
      if (objectRow == null) {
        isValid = false;
        break;
      }

      if (isConfig && isTabConfig) {
        tabConfigRowCount = getTabConfigRowCount(sheet, reader, totalLines, objectRow);
      }

      isValid = this.applyObject(objectRow, fileTab, isConfig, linesToIgnore, isTabConfig);
      if (!isValid) {
        break;
      }

      List<FileField> fileFieldList = new ArrayList<>();
      List<Integer> ignoreFields = new ArrayList<Integer>();

      for (int line = isConfig ? 1 : linesToIgnore; line < totalLines; line++) {
        String[] row = reader.read(sheet, line, isConfig ? 0 : objectRow.length);
        if (row == null) {
          continue;
        }

        if (isConfig) {
          this.applyWithConfig(
              row, line, fileFieldList, ignoreFields, fileTab, isTabConfig, tabConfigRowCount);
        } else {
          this.applyWithoutConfig(row, (line - linesToIgnore), fileFieldList, fileTab, isHeader);
        }
      }

      if (isConfig) {
        fileFieldList.removeIf(field -> field.getImportField() == null);
        if (!fileTab.getImportType().equals(FileFieldRepository.IMPORT_TYPE_NEW)) {
          fileTab = this.setSearchField(fileTab, searchFieldList, fileFieldList);
        }
      }
      advancedImport.addFileTabListItem(fileTab);
      advancedImportRepository.save(advancedImport);
    }
    return isValid;
  }

  protected boolean applyObject(
      String[] row, FileTab fileTab, boolean isConfig, int linesToIgnore, boolean isTabConfig)
      throws AxelorException {
    int rowIndex = isConfig ? (isTabConfig ? 1 : 0) : 0;

    if (isTabConfig && row[0] != null) {
      rowIndex = 0;
      isTabWithoutConfig = true;
    }

    if (StringUtils.contains(row[rowIndex], "Object") && isConfig) {
      this.setFileTabConfig(row, fileTab, rowIndex);

    } else if ((StringUtils.containsIgnoreCase(row[0], "Object")
            || (row.length > 1 && StringUtils.contains(row[1], "Object")))
        && !isConfig) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ADVANCED_IMPORT_3));

    } else if (isConfig
        && (!StringUtils.containsIgnoreCase(row[0], "Object")
            && (row.length > 1 && !StringUtils.contains(row[1], "Object")))) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ADVANCED_IMPORT_4));

    } else if (isConfig) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ADVANCED_IMPORT_NO_OBJECT),
          fileTab.getName());
    }

    if ((StringUtils.isBlank(row[0])
        && (row.length > 1 && StringUtils.isBlank(row[1]))
        && linesToIgnore == 0)) {
      return false;
    }

    return true;
  }

  protected void applyWithConfig(
      String[] row,
      int line,
      List<FileField> fileFieldList,
      List<Integer> ignoreFields,
      FileTab fileTab,
      Boolean isTabConfig,
      int tabConfigRowCount)
      throws AxelorException, ClassNotFoundException {

    Mapper mapper = getMapper(fileTab.getMetaModel().getFullName());
    FileField fileField = null;

    int index = 0;
    for (int i = 0; i < row.length; i++) {
      if (Strings.isNullOrEmpty(row[i])) {
        continue;
      }

      index = line;
      String value = row[i].trim();
      if (line == 1) {
        this.readFields(value, i, fileFieldList, ignoreFields, mapper, fileTab);
        continue;
      }

      if (ignoreFields.contains(i)) {
        continue;
      }

      if (fileFieldList.size() < i) {
        break;
      } else if (!isTabConfig && fileFieldList.size() <= i) {
        break;
      }

      if (row[0] == null) {
        fileField = fileFieldList.get(i - 1);
      } else if (!isTabConfig && row[0] != null) {
        fileField = fileFieldList.get(i);
      }

      if (isTabWithoutConfig) {
        fileField = fileFieldList.get(i);
      }

      if (line == 2) {
        fileField.setColumnTitle(value);
        continue;
      }

      if (isTabConfig && i > 0 && row[0] != null && line >= 3 && line <= 11) {
        fileField = fileFieldList.get(i - 1);
        this.setFileFieldConfig(row, i, fileField);
      }

      if (isTabConfig && i > 0 && row[0] == null) {
        line += -(tabConfigRowCount + 2);
        this.setSampleLines(line, value, fileField);
        line = index;
      } else if (!isTabConfig) {
        line += -2;
        this.setSampleLines(line, value, fileField);
        line = index;
      }
    }
  }

  @Transactional
  public void applyWithoutConfig(
      String[] row, int line, List<FileField> fileFieldList, FileTab fileTab, boolean isHeader)
      throws AxelorException {
    int index = 0;
    for (int i = 0; i < row.length; i++) {
      if (Strings.isNullOrEmpty(row[i])) {
        continue;
      }
      index = line;
      String value = row[i].trim();
      FileField fileField = null;

      if (line == 0) {
        fileField = new FileField();
        fileField.setIsMatchWithFile(true);
        fileFieldList.add(fileField);
        fileField.setFileTab(fileTab);

        if (isHeader) {
          fileField.setColumnTitle(value);
        } else {
          fileField.setFirstLine(value);
        }
        fileField.setSequence(i);
        fileField.setFullName(fileFieldService.computeFullName(fileField));
        fileField = fileFieldRepository.save(fileField);
        continue;
      }

      if (fileFieldList.size() <= i) {
        break;
      }

      if (!isHeader) {
        line += 1;
      }

      if (fileField == null) {
        fileField = fileFieldList.get(i);
      }

      setSampleLines(line, value, fileField);
      line = index;
    }
  }

  protected void setSampleLines(int line, String value, FileField fileField) {
    if (!StringUtils.isBlank(fileField.getTargetType())
        && fileField.getTargetType().equals("String")
        && !StringUtils.isBlank(value)
        && value.length() > 255) {
      value = StringUtils.abbreviate(value, 255);
    }

    switch (line) {
      case 1:
        fileField.setFirstLine(value);
        break;
      case 2:
        fileField.setSecondLine(value);
        break;
      case 3:
        fileField.setThirdLine(value);
        break;
      default:
        break;
    }
  }

  @Transactional
  public void readFields(
      String value,
      int index,
      List<FileField> fileFieldList,
      List<Integer> ignoreFields,
      Mapper mapper,
      FileTab fileTab)
      throws AxelorException, ClassNotFoundException {

    FileField fileField = new FileField();
    fileField.setSequence(index);
    if (Strings.isNullOrEmpty(value)) {
      return;
    }

    String importType = StringUtils.substringBetween(value, "(", ")");

    if (!Strings.isNullOrEmpty(importType)
        && (importType.equalsIgnoreCase(forSelectUseValues)
            || importType.equalsIgnoreCase(forSelectUseTitles)
            || importType.equalsIgnoreCase(forSelectUseTranslatedTitles))) {
      fileField.setForSelectUse(this.getForStatusSelect(importType));
    } else {
      fileField.setImportType(this.getImportType(value, importType));
    }

    value = value.split("\\(")[0];
    String importField = null;
    String subImportField = null;
    if (value.contains(".")) {
      importField = value.substring(0, value.indexOf("."));
      subImportField = value.substring(value.indexOf(".") + 1, value.length());
    } else {
      importField = value;
    }

    boolean isValid = this.checkFields(mapper, importField, subImportField);

    if (isValid) {
      this.setImportFields(mapper, fileField, importField, subImportField);
    } else {
      ignoreFields.add(index);
    }
    fileFieldList.add(fileField);
    fileField = fileFieldRepository.save(fileField);
    fileTab.addFileFieldListItem(fileField);
  }

  protected boolean checkFields(Mapper mapper, String importField, String subImportField)
      throws AxelorException, ClassNotFoundException {

    if (importField != null) {
      Property parentProp = mapper.getProperty(importField);

      if (parentProp == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            String.format(
                I18n.get(IExceptionMessage.ADVANCED_IMPORT_1),
                importField,
                mapper.getBeanClass().getSimpleName()));
      }

      if (parentProp.getType().name().equals("ONE_TO_MANY")) {
        return false;
      }

      if (!Strings.isNullOrEmpty(subImportField)) {
        String[] subFields = subImportField.split("\\.");
        return this.checkSubFields(
            subFields, 0, parentProp, parentProp.getEntity().getSimpleName());

      } else if (parentProp.getTarget() != null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            String.format(
                I18n.get(IExceptionMessage.ADVANCED_IMPORT_5),
                importField,
                mapper.getBeanClass().getSimpleName()));
      }
    }
    return true;
  }

  protected boolean checkSubFields(String[] subFields, int index, Property parentProp, String model)
      throws AxelorException, ClassNotFoundException {
    boolean isValid = true;

    if (index < subFields.length) {
      if (parentProp.getTarget() != null) {
        Mapper mapper = getMapper(parentProp.getTarget().getName());
        Property childProp = mapper.getProperty(subFields[index]);

        if (childProp == null) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              String.format(
                  I18n.get(IExceptionMessage.ADVANCED_IMPORT_2),
                  subFields[index],
                  parentProp.getName(),
                  model));
        }
        if (childProp.getTarget() != null) {
          if (childProp.getType().name().equals("ONE_TO_MANY")) {
            isValid = false;
            return isValid;
          }
          if (index != subFields.length - 1) {
            isValid = this.checkSubFields(subFields, index + 1, childProp, model);
          } else {
            throw new AxelorException(
                TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                String.format(
                    I18n.get(IExceptionMessage.ADVANCED_IMPORT_5), subFields[index], model));
          }
        }
      }
    }
    return isValid;
  }

  protected void setImportFields(
      Mapper mapper, FileField fileField, String importField, String subImportField) {

    Property prop = mapper.getProperty(importField);
    MetaField field =
        metaFieldRepo
            .all()
            .filter(
                "self.name = ?1 AND self.metaModel.name = ?2",
                prop.getName(),
                prop.getEntity().getSimpleName())
            .fetchOne();
    fileField.setImportField(field);
    fileField.setIsMatchWithFile(true);

    if (!Strings.isNullOrEmpty(subImportField)) {
      fileField.setSubImportField(subImportField);
    }

    fileField.setFullName(fileFieldService.computeFullName(fileField));
    fileField = fileFieldService.fillType(fileField);

    if (fileField.getTargetType().equals("MetaFile")) {
      fileField.setImportType(FileFieldRepository.IMPORT_TYPE_NEW);
    }
  }

  protected int getImportType(String value, String importType) {

    if (Strings.isNullOrEmpty(importType)) {
      if (value.contains(".")) {
        return FileFieldRepository.IMPORT_TYPE_NEW;
      }
      return FileFieldRepository.IMPORT_TYPE_DIRECT;
    }

    switch (importType.toLowerCase()) {
      case "find":
        return FileFieldRepository.IMPORT_TYPE_FIND;

      case "findnew":
        return FileFieldRepository.IMPORT_TYPE_FIND_NEW;

      case "new":
        return FileFieldRepository.IMPORT_TYPE_NEW;

      case "ignore/empty":
        return FileFieldRepository.IMPORT_TYPE_IGNORE_EMPTY;

      default:
        if (value.contains(".")) {
          return FileFieldRepository.IMPORT_TYPE_NEW;
        }
        return FileFieldRepository.IMPORT_TYPE_DIRECT;
    }
  }

  protected int getForStatusSelect(String importType) {

    switch (importType.toLowerCase()) {
      case forSelectUseTitles:
        return FileFieldRepository.SELECT_USE_TITLES;
      case forSelectUseValues:
        return FileFieldRepository.SELECT_USE_VALUES;
      case forSelectUseTranslatedTitles:
        return FileFieldRepository.SELECT_USE_TRANSLATED_TITLES;
      default:
        return FileFieldRepository.SELECT_USE_VALUES;
    }
  }

  protected void setFileTabConfig(String row[], FileTab fileTab, int rowIndex)
      throws AxelorException {

    final String KEY_OBJECT = "object";
    final String KEY_IMPORT_TYPE = "importtype";
    final String KEY_SEARCH_FIELD_SET = "searchfieldset";
    final String KEY_ACTIONS = "actions";
    final List<String> KEY_LIST =
        Arrays.asList(KEY_IMPORT_TYPE, KEY_OBJECT, KEY_SEARCH_FIELD_SET, KEY_ACTIONS);
    Map<String, String> tabConfigDataMap = getTabConfigDataMap(row, KEY_LIST);

    MetaModel model = metaModelRepo.findByName(tabConfigDataMap.get(KEY_OBJECT));
    fileTab.setMetaModel(model);

    if (tabConfigDataMap.containsKey(KEY_IMPORT_TYPE)) {

      Integer importType = this.getImportType(null, tabConfigDataMap.get(KEY_IMPORT_TYPE));
      fileTab.setImportType(importType);

      if (importType != FileFieldRepository.IMPORT_TYPE_NEW) {
        if (!tabConfigDataMap.containsKey(KEY_SEARCH_FIELD_SET)) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.ADVANCED_IMPORT_6),
              fileTab.getName());
        }
        searchFieldList = Arrays.asList(tabConfigDataMap.get(KEY_SEARCH_FIELD_SET).split("\\,"));
      }
    }

    fileTab.setActions(tabConfigDataMap.get(KEY_ACTIONS));
  }

  protected void setFileFieldConfig(String[] row, Integer i, FileField fileField) {

    String fieldName = row[0].trim();

    switch (fieldName.toLowerCase()) {
      case "forselectuse":
        fileField.setForSelectUse(this.getForStatusSelect(row[i]));
        break;

      case "noimportif":
        fileField.setNoImportIf(row[i]);
        break;

      case "expression":
        fileField.setExpression(row[i]);
        break;

      case "dateformat":
        fileField.setDateFormat(row[i]);
        break;

      case "importtype":
        fileField.setImportType(Integer.parseInt(row[i]));
        break;

      case "subimportfield":
        fileField.setSubImportField(row[i]);
        break;

      case "splitby":
        fileField.setSplitBy(row[i]);
        break;

      case "defaultifnotfound":
        fileField.setDefaultIfNotFound(row[i]);
        break;
    }
  }

  protected FileTab setSearchField(
      FileTab fileTab, List<String> searchFieldList, List<FileField> fileFieldList) {
    Set<FileField> searchFieldSet = new HashSet<FileField>();

    for (String searchField : searchFieldList) {
      for (FileField fileField : fileFieldList) {
        if (fileField.getFullName().endsWith("- " + searchField)) {
          searchFieldSet.add(fileField);
        }
      }
    }
    fileTab.setSearchFieldSet(searchFieldSet);
    return fileTab;
  }

  @Override
  public int getTabConfigRowCount(
      String sheet, DataReaderService reader, int totalLines, String[] objectRow) {

    int tabConfigRowCount = 0;
    if (objectRow[0] == null
        && (objectRow.length > 1 && StringUtils.containsIgnoreCase(objectRow[1], "Object"))) {
      int linesForTab;
      for (linesForTab = 3; linesForTab < totalLines; linesForTab++) {
        if (reader.read(sheet, linesForTab, 0)[0] != null) {
          tabConfigRowCount++;
        } else {
          break;
        }
      }
    }
    return tabConfigRowCount;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Mapper getMapper(String modelFullName) throws ClassNotFoundException {
    Class<? extends Model> klass = (Class<? extends Model>) Class.forName(modelFullName);
    return Mapper.of(klass);
  }

  @Override
  public boolean resetImport(AdvancedImport advancedImport) throws ClassNotFoundException {
    List<FileTab> fileTabList = advancedImport.getFileTabList();
    Beans.get(ValidatorService.class).sortFileTabList(fileTabList);

    boolean isResetValue = resetRelationalFields(fileTabList);
    if (isResetValue) {
      removeRecords(fileTabList);
    }
    return isResetValue;
  }

  @SuppressWarnings("unchecked")
  public boolean resetRelationalFields(List<FileTab> fileTabList) throws ClassNotFoundException {

    boolean isResetValue = false;

    for (FileTab fileTab : fileTabList) {

      Map<String, Object> jsonContextMap = dataImportService.createJsonContext(fileTab);
      JsonContext jsonContext = (JsonContext) jsonContextMap.get("jsonContext");
      String fieldName = inflector.camelize(fileTab.getMetaModel().getName(), true) + "Set";

      List<Object> recordList = (List<Object>) jsonContext.get(fieldName);
      if (CollectionUtils.isEmpty(recordList)) {
        continue;
      }
      isResetValue = true;

      Class<? extends Model> modelKlass =
          (Class<? extends Model>) Class.forName(fileTab.getMetaModel().getFullName());
      this.resetPropertyValue(modelKlass, recordList);
      this.resetSubPropertyValue(modelKlass, jsonContext);
    }
    return isResetValue;
  }

  @Transactional
  protected void resetPropertyValue(Class<? extends Model> klass, List<Object> recordList)
      throws ClassNotFoundException {

    JpaRepository<? extends Model> modelRepo = JpaRepository.of(klass);

    for (Property prop : Mapper.of(klass).getProperties()) {
      if (prop.getTarget() == null || prop.isRequired()) {
        continue;
      }

      for (Object obj : recordList) {
        Map<String, Object> recordMap = Mapper.toMap(EntityHelper.getEntity(obj));
        Long id = Long.valueOf(recordMap.get("id").toString());
        Object bean = modelRepo.find(id);
        if (bean != null) {
          prop.set(bean, null);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  protected void resetSubPropertyValue(Class<? extends Model> klass, JsonContext jsonContext)
      throws ClassNotFoundException {

    for (Property prop : Mapper.of(klass).getProperties()) {
      if (prop.getTarget() == null || prop.isRequired()) {
        continue;
      }

      String simpleModelName = StringUtils.substringAfterLast(prop.getTarget().getName(), ".");
      String field = inflector.camelize(simpleModelName, true) + "Set";

      if (!jsonContext.containsKey(field)) {
        continue;
      }

      List<Object> recordList = (List<Object>) jsonContext.get(field);

      Class<? extends Model> modelKlass =
          (Class<? extends Model>) Class.forName(prop.getTarget().getName());

      this.resetPropertyValue(modelKlass, recordList);
    }
  }

  @SuppressWarnings("unchecked")
  public void removeRecords(List<FileTab> fileTabList) throws ClassNotFoundException {

    for (FileTab fileTab : fileTabList) {

      String targetModelName = fileTab.getMetaModel().getFullName();

      Map<String, Object> jsonContextMap = dataImportService.createJsonContext(fileTab);
      JsonContext jsonContext = (JsonContext) jsonContextMap.get("jsonContext");
      String fieldName = inflector.camelize(fileTab.getMetaModel().getName(), true) + "Set";

      List<Object> recordList = (List<Object>) jsonContext.get(fieldName);
      if (CollectionUtils.isEmpty(recordList)) {
        continue;
      }

      Class<? extends Model> modelKlass = (Class<? extends Model>) Class.forName(targetModelName);
      removeRecord(fileTab, modelKlass, recordList, fileTabList);
      removeSubRecords(modelKlass, jsonContext);
    }
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public void removeRecord(
      FileTab fileTab,
      Class<? extends Model> modelKlass,
      List<Object> recordList,
      List<FileTab> fileTabList)
      throws ClassNotFoundException {

    JpaRepository<? extends Model> modelRepo = JpaRepository.of(modelKlass);

    for (FileTab tab : fileTabList) {

      Map<String, Object> jsonContextMap = dataImportService.createJsonContext(tab);
      JsonContext jsonContext = (JsonContext) jsonContextMap.get("jsonContext");
      String fieldName = inflector.camelize(tab.getMetaModel().getName(), true) + "Set";

      List<Object> recList = (List<Object>) jsonContext.get(fieldName);
      if (CollectionUtils.isEmpty(recList)) {
        continue;
      }

      Class<? extends Model> klass =
          (Class<? extends Model>) Class.forName(tab.getMetaModel().getFullName());

      Property[] props = Mapper.of(klass).getProperties();

      for (Property prop : props) {
        if (prop.getTarget() != null && prop.getTarget() == modelKlass && prop.isRequired()) {
          removeRecord(tab, klass, recList, fileTabList);
        }
      }
    }

    String ids =
        recordList
            .stream()
            .map(
                obj -> {
                  Map<String, Object> recordMap = Mapper.toMap(EntityHelper.getEntity(obj));
                  return recordMap.get("id").toString();
                })
            .collect(Collectors.joining(","));

    modelRepo.all().filter("self.id IN (" + ids + ")").delete();
    fileTab.setAttrs(null);

    LOG.debug("Reset imported data : {}", modelKlass.getSimpleName());
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public void removeSubRecords(Class<? extends Model> klass, JsonContext jsonContext)
      throws ClassNotFoundException {

    for (Property prop : Mapper.of(klass).getProperties()) {
      if (prop.getTarget() == null || prop.isCollection()) {
        continue;
      }

      String simpleModelName = StringUtils.substringAfterLast(prop.getTarget().getName(), ".");
      String field = inflector.camelize(simpleModelName, true) + "Set";

      if (!jsonContext.containsKey(field)) {
        continue;
      }

      List<Object> recList = (List<Object>) jsonContext.get(field);

      String ids =
          recList
              .stream()
              .map(
                  obj -> {
                    Map<String, Object> recordMap = Mapper.toMap(EntityHelper.getEntity(obj));
                    return recordMap.get("id").toString();
                  })
              .collect(Collectors.joining(","));

      JpaRepository<? extends Model> modelRepo =
          JpaRepository.of((Class<? extends Model>) Class.forName(prop.getTarget().getName()));

      modelRepo.all().filter("self.id IN (" + ids + ")").delete();
    }
  }

  protected Map<String, String> getTabConfigDataMap(String row[], List<String> keys) {
    Map<String, String> tabConfigDataMap = new HashMap<>();
    for (String data : row) {
      if (StringUtils.isBlank(data)) {
        continue;
      }

      String[] keyValue = data.split("\\:");
      if (keyValue.length != 2) {
        continue;
      }
      String key = keyValue[0].replaceAll("(^\\h*)|(\\h*$)", "").trim().toLowerCase();
      if (keys.contains(key)) {
        tabConfigDataMap.put(key, keyValue[1].trim());
      }
    }
    return tabConfigDataMap;
  }
}
