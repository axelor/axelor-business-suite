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
package com.axelor.apps.base.service.excelreport.config;

import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.excelreport.components.ExcelReportCellService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportGroovyService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportTranslationService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.script.ScriptException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;

public class ExcelReportHelperServiceImpl implements ExcelReportHelperService {

  @Inject private AppBaseService appBaseService;
  @Inject private ExcelReportCellService excelReportCellService;
  @Inject private ExcelReportTranslationService excelReportTranslationService;
  @Inject private ExcelReportGroovyService excelReportGroovyService;

  @Override
  public ResourceBundle getResourceBundle(PrintTemplate printTemplate) {

    ResourceBundle resourceBundle;
    String language =
        ObjectUtils.notEmpty(printTemplate.getLanguage())
            ? printTemplate.getLanguage().getCode()
            : null;

    if (language == null) {
      resourceBundle = I18n.getBundle();
    } else if (language.equals("fr")) {
      resourceBundle = I18n.getBundle(Locale.FRANCE);
    } else {
      resourceBundle = I18n.getBundle(Locale.ENGLISH);
    }

    return resourceBundle;
  }

  @Override
  public int getBigDecimalScale() {
    int bigDecimalScale = appBaseService.getAppBase().getBigdecimalScale();
    if (bigDecimalScale == 0) {
      bigDecimalScale = ExcelReportConstants.DEFAULT_BIGDECIMAL_SCALE;
    }
    return bigDecimalScale;
  }

  @Override
  public Mapper getMapper(String modelFullName) throws ClassNotFoundException {
    Class<?> klass = Class.forName(modelFullName);
    return Mapper.of(klass);
  }

  @Override
  public ImmutablePair<Property, Object> findField(final Mapper mapper, Object value, String name) {
    final Iterator<String> iter = Splitter.on(".").split(name).iterator();
    Mapper current = mapper;
    Property property = current.getProperty(iter.next());

    if (property == null || (property.isJson() && iter.hasNext())) {
      return null;
    }

    while (property != null && property.getTarget() != null && iter.hasNext()) {
      if (ObjectUtils.notEmpty(value)) {
        value = property.get(value);
      }
      current = Mapper.of(property.getTarget());
      property = current.getProperty(iter.next());
    }

    return ImmutablePair.of(property, value);
  }

  @Override
  public String getDateTimeFormat(Object value) {
    String formattedDateTime = "";
    if (value.getClass() == LocalDate.class) {
      LocalDate date = (LocalDate) value;
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(ExcelReportConstants.DATE_FORMAT);
      formattedDateTime = date.format(formatter);
    } else if (value.getClass() == LocalDateTime.class) {
      LocalDateTime dateTime = (LocalDateTime) value;
      DateTimeFormatter formatter =
          DateTimeFormatter.ofPattern(ExcelReportConstants.DATE_TIME_FORMAT);
      formattedDateTime = dateTime.format(formatter);
    }
    return formattedDateTime;
  }

  @Override
  public Object findNameColumn(Property targetField, Object value) {
    String nameColumn = targetField.getTargetName();
    for (Property property : Mapper.of(targetField.getTarget()).getProperties()) {
      if (nameColumn.equals(property.getName())) {
        return property.get(value);
      }
    }
    return null;
  }

  @Override
  public Property getProperty(Mapper mapper, String propertyName) {
    Property property;

    if (propertyName.contains(".")) {
      property = mapper.getProperty(propertyName.substring(0, propertyName.indexOf(".")));
    } else {
      property = mapper.getProperty(propertyName);
    }

    return property;
  }

  @Override
  public Pair<Boolean, String> checkForTranslationFuction(String propertyName, boolean translate) {
    if (propertyName.contains("_tr(value:")) {
      translate = true;
      propertyName =
          org.apache.commons.lang3.StringUtils.chop(propertyName.trim().replace("_tr(value:", ""));
    }
    return Pair.of(translate, propertyName);
  }

  @Override
  public void setEmptyCell(Workbook wb, Map<String, Object> m) {
    CellStyle newCellStyle = wb.createCellStyle();
    newCellStyle.setFont(((XSSFCellStyle) m.get(ExcelReportConstants.KEY_CELL_STYLE)).getFont());
    m.replace(ExcelReportConstants.KEY_VALUE, "");
    m.replace(ExcelReportConstants.KEY_CELL_STYLE, newCellStyle);
  }

  @Override
  public Map<String, Object> getDataMap(Cell cell) {
    Map<String, Object> map = new HashMap<>();
    Object cellValue = excelReportCellService.getCellValue(cell);
    map.put(ExcelReportConstants.KEY_ROW, cell.getRowIndex());
    map.put(ExcelReportConstants.KEY_COLUMN, cell.getColumnIndex());
    map.put(ExcelReportConstants.KEY_VALUE, cellValue);
    map.put(ExcelReportConstants.KEY_CELL_STYLE, cell.getCellStyle());

    return map;
  }

  @Override
  public String getLabel(PrintTemplate printTemplate, String value, Object bean, boolean translate)
      throws IOException, AxelorException {

    ResourceBundle resourceBundle = this.getResourceBundle(printTemplate);
    if (value.contains(" : ") && (value.contains("hide") || value.contains("show"))) {
      if (excelReportGroovyService.getConditionResult(value, bean)) {
        value = "";
      } else {
        value = value.substring(0, value.lastIndexOf(" : ")).trim();

        if (excelReportTranslationService.isTranslationFunction(value)) {

          value = excelReportTranslationService.getTranslatedValue(value, printTemplate).toString();
        }
      }
    } else if (value.startsWith("if") && value.contains("->")) { // if else condition
      value = excelReportGroovyService.getIfConditionResult(value, bean).getLeft();
      if (excelReportTranslationService.isTranslationFunction(value)) {
        value = excelReportTranslationService.getTranslatedValue(value, printTemplate).toString();
      }
    }
    if (translate) {
      value = resourceBundle.getString(value);
    }

    return value;
  }

  @Override
  public Object getNonCollectionOutputValue(
      Triple<String, String, Boolean> propertyNameOperationStringTranslate,
      PrintTemplate printTemplate,
      Object object,
      Property property)
      throws ScriptException {
    Object outputValue = "";
    if (object == null || ObjectUtils.isEmpty(property.get(object))) {
      outputValue = "";
    } else if (property.isReference()) {
      outputValue = this.findNameColumn(property, property.get(object));
    } else if (!ObjectUtils.isEmpty(property.getSelection())) {

      String title =
          MetaStore.getSelectionItem(property.getSelection(), property.get(object).toString())
              .getTitle();
      outputValue = I18n.get(title);

    } else if (property.get(object).getClass() == LocalDate.class
        || property.get(object).getClass() == LocalDateTime.class) {
      outputValue = this.getDateTimeFormat(property.get(object));
    } else {
      outputValue = property.get(object).toString();
    }

    if (StringUtils.notEmpty(propertyNameOperationStringTranslate.getMiddle())) {
      outputValue =
          excelReportGroovyService.calculateFromString(
              outputValue.toString().concat(propertyNameOperationStringTranslate.getMiddle()),
              this.getBigDecimalScale());
    }

    if (Boolean.TRUE.equals(propertyNameOperationStringTranslate.getRight())) {
      outputValue = excelReportTranslationService.getTranslatedValue(outputValue, printTemplate);
    }
    return outputValue;
  }
}
