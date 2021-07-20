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
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import java.util.Map;
import java.util.ResourceBundle;
import javax.script.ScriptException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Workbook;

public interface ExcelReportHelperService {

  public ResourceBundle getResourceBundle(PrintTemplate printTemplate);

  public int getBigDecimalScale();

  public Mapper getMapper(String modelFullName) throws ClassNotFoundException;

  public ImmutablePair<Property, Object> findField(final Mapper mapper, Object value, String name);

  public String getDateTimeFormat(Object value);

  public Object findNameColumn(Property targetField, Object value);

  public Property getProperty(Mapper mapper, String propertyName);

  public Pair<Boolean, String> checkForTranslationFuction(String propertyName, boolean translate);

  public void setEmptyCell(Workbook wb, Map<String, Object> m);

  public Map<String, Object> getDataMap(Cell cell);

  public String getLabel(PrintTemplate printTemplate, String value, Object bean, boolean translate)
      throws IOException, AxelorException;

  public Object getNonCollectionOutputValue(
      Triple<String, String, Boolean> propertyNameOperationStringTranslate,
      PrintTemplate printTemplate,
      Object object,
      Property property)
      throws ScriptException;
}
