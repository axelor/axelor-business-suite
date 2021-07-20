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
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.excelreport.config.ExcelReportConstants;
import com.axelor.apps.base.service.excelreport.config.ExcelReportHelperService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.rpc.Context;
import com.axelor.script.CompositeScriptHelper;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.script.ScriptException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;

public class ExcelReportGroovyServiceImpl implements ExcelReportGroovyService {

  @Inject ExcelReportHelperService excelReportHelperService;

  // for hide or show condition
  @Override
  public boolean getConditionResult(String statement, Object bean) {

    String conditionalText = statement.substring(statement.lastIndexOf(":") + 1).trim();
    String condition =
        org.apache.commons.lang3.StringUtils.substringBetween(conditionalText, "(", ")");

    Object result = validateCondition(condition, bean);
    boolean flag = true;

    if (!(result instanceof Boolean)
        || (result instanceof Boolean
            && ((boolean) result == true && conditionalText.contains("show")
                || (boolean) result == false && conditionalText.contains("hide")))) {
      flag = false;
    }

    return flag;
  }

  // for if else condition
  public ImmutablePair<String, String> getIfConditionResult(String statement, Object bean)
      throws IOException, AxelorException {

    String condition = "";
    Object flag;

    List<String> lines = IOUtils.readLines(new StringReader(statement));
    String resultValue = null;
    String operation = " ";
    String value = null;

    for (String line : lines) {
      if (line.startsWith("if") || line.startsWith("else if")) {
        condition = org.apache.commons.lang3.StringUtils.substringBetween(line, "(", ")").trim();
        flag = validateCondition(condition, bean);

        if ((boolean) flag) {
          resultValue = line.substring(line.indexOf("->") + 2).trim();
          break;
        }
      } else if (line.startsWith("else")) {
        resultValue = line.substring(line.indexOf("->") + 2).trim();
      }
    }

    if (StringUtils.isEmpty(resultValue)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.INVALID_CONDITION_FORMAT) + statement);
    } else if (resultValue.contains(" ") && resultValue.contains("$")) {
      value = resultValue.substring(0, resultValue.indexOf(" "));
      operation = resultValue.substring(resultValue.indexOf(" "));
    } else {
      value = resultValue;
    }

    return new ImmutablePair<>(value, operation.trim());
  }

  @Override // calculates arithmetic operations using javascript engine
  public String calculateFromString(String expression, int bigDecimalScale) throws ScriptException {

    if (StringUtils.isEmpty(expression)) {
      return "";
    }

    return ((BigDecimal) getScriptResult(null, expression)).setScale(bigDecimalScale).toString();
  }

  @Override // solves $eval:
  public Object evaluate(
      String expression,
      Object bean,
      Map<String, List<Object>> reportQueryBuilderResultMap,
      List<ReportQueryBuilder> reportQueryBuilderList)
      throws ClassNotFoundException {
    Object result = null;
    try {
      if (expression.contains(".")) {
        if (getIsReportQueryBuilderStatement(expression, reportQueryBuilderList)) {
          result =
              getReportQueryBuilderGroovyStatementResult(expression, reportQueryBuilderResultMap);
        } else {
          Context scriptContext = new Context(Mapper.toMap(bean), bean.getClass());
          result = getScriptResult(scriptContext, expression);
        }
      } else {
        result = validateCondition(expression, bean);
      }
    } catch (NullPointerException e) {
      result = "";
    }

    if (result.getClass().equals(BigDecimal.class)) {
      result = ((BigDecimal) result).setScale(excelReportHelperService.getBigDecimalScale());
    }
    return result;
  }

  @Override // solves groovy condition
  public Object validateCondition(String condition, Object bean) {
    Context scriptContext = new Context(Mapper.toMap(bean), bean.getClass());

    if (ObjectUtils.isEmpty(condition)) {
      return "";
    }

    // replace all single quotes to groovy compatible quotes
    if (condition.contains("‘") || condition.contains("’")) {
      condition = condition.replaceAll("‘", "'").replaceAll("’", "'");
    }

    return getScriptResult(scriptContext, condition);
  }

  @Override
  public ImmutableTriple<String, String, Boolean> checkGroovyConditionalText(
      String propertyName,
      Object object,
      Map<String, Object> m,
      Map<String, List<Object>> reportQueryBuilderResultMap,
      List<ReportQueryBuilder> reportQueryBuilderList)
      throws IOException, AxelorException, ClassNotFoundException {

    Boolean hide = false;
    String operationString = "";

    if (propertyName.contains(":")
        && !propertyName.trim().startsWith("$eval:")
        && (propertyName.contains("hide(") || propertyName.contains("show("))) {
      hide = this.getConditionResult(propertyName, object);
      propertyName = propertyName.substring(0, propertyName.lastIndexOf(":")).trim();
    } else if (propertyName.startsWith("if") && propertyName.contains("->")) {
      ImmutablePair<String, String> valueOperationPair =
          this.getIfConditionResult(propertyName, object);

      propertyName = valueOperationPair.getLeft();
      operationString = valueOperationPair.getRight();
    } else if (propertyName.contains(":") && propertyName.startsWith("$eval:")) {
      if (propertyName.contains("hide(") || propertyName.contains("show(")) {
        hide = this.getConditionResult(propertyName, object);
        propertyName = propertyName.substring(0, propertyName.lastIndexOf(":")).trim();
      }
      if (Boolean.TRUE.equals(hide)) {
        m.replace(ExcelReportConstants.KEY_VALUE, "");
      } else {
        String statement = propertyName.substring(propertyName.indexOf(":") + 1);
        m.replace(
            ExcelReportConstants.KEY_VALUE,
            this.evaluate(statement, object, reportQueryBuilderResultMap, reportQueryBuilderList));
      }
      hide = null;
    }

    return new ImmutableTriple<>(propertyName, operationString, hide);
  }

  protected boolean getIsReportQueryBuilderStatement(
      String expression, List<ReportQueryBuilder> reportQueryBuilderList) {
    List<String> reportQueryVariableList =
        reportQueryBuilderList.stream().map(l -> l.getVar()).collect(Collectors.toList());
    boolean isReportQueryBuilderStatement = false;
    for (String var : reportQueryVariableList) {
      if (expression.substring(0, expression.indexOf(".")).equals(var)) {
        isReportQueryBuilderStatement = true;
        break;
      }
    }
    return isReportQueryBuilderStatement;
  }

  protected Object getReportQueryBuilderGroovyStatementResult(
      String expression, Map<String, List<Object>> reportQueryBuilderResultMap) {
    List<Object> collectionList =
        reportQueryBuilderResultMap.get(expression.substring(0, expression.indexOf(".")));
    String modelAlias =
        ((LinkedHashMap<String, Object>) collectionList.get(0)).keySet().iterator().next();
    String variable = expression.substring(0, expression.indexOf("."));
    List<Object> recordList = new ArrayList<>();
    for (Object ob : collectionList) {
      recordList.add(((LinkedHashMap<String, Object>) ob).get(modelAlias));
    }
    Map<String, Object> resultMap = new HashMap<>();
    resultMap.put(variable, recordList);
    Context context =
        new Context(
            resultMap,
            ((LinkedHashMap<String, Object>) ((List<Object>) collectionList).get(0))
                .get(modelAlias)
                .getClass());

    return getScriptResult(context, expression);
  }

  protected Object getScriptResult(Context context, String expression) {
    CompositeScriptHelper csh = new CompositeScriptHelper(context);
    return csh.eval(expression);
  }
}
