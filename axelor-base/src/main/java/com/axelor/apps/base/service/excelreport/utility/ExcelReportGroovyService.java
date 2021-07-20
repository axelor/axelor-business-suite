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
package com.axelor.apps.base.service.excelreport.utility;

import com.axelor.apps.base.db.ReportQueryBuilder;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.script.ScriptException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;

public interface ExcelReportGroovyService {

  public boolean getConditionResult(String statement, Object bean);

  public ImmutablePair<String, String> getIfConditionResult(String statement, Object bean)
      throws IOException, AxelorException;

  public String calculateFromString(String expression, int bigDecimalScale) throws ScriptException;

  public Object evaluate(
      String expression,
      Object bean,
      Map<String, List<Object>> reportQueryBuilderResultMap,
      List<ReportQueryBuilder> reportQueryBuilderList)
      throws ClassNotFoundException;

  public Object validateCondition(String condition, Object bean);

  public ImmutableTriple<String, String, Boolean> checkGroovyConditionalText(
      String propertyName,
      Object object,
      Map<String, Object> m,
      Map<String, List<Object>> reportQueryBuilderResultMap,
      List<ReportQueryBuilder> reportQueryBuilderList)
      throws IOException, AxelorException, ClassNotFoundException;
}
