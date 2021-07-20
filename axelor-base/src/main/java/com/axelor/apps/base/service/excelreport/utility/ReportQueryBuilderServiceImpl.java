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

import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.db.ReportQueryBuilder;
import com.axelor.apps.base.db.ReportQueryBuilderParams;
import com.axelor.apps.base.service.excelreport.config.ExcelReportConstants;
import com.axelor.apps.base.service.excelreport.config.ExcelReportHelperService;
import com.axelor.apps.base.service.excelreport.config.ReportParameterVariables;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.QueryBinder;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.google.inject.Inject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.Query;
import javax.script.ScriptException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Triple;
import org.hibernate.transform.BasicTransformerAdapter;

public class ReportQueryBuilderServiceImpl implements ReportQueryBuilderService {

  @Inject private ExcelReportGroovyService excelReportGroovyService;
  @Inject private ExcelReportShiftingService excelReportShiftingService;
  @Inject private ExcelReportHelperService excelReportHelperService;
  @Inject private ExcelReportTranslationService excelReportTranslationService;

  @Override
  public Map<String, List<Object>> getAllReportQueryBuilderResult(
      List<ReportQueryBuilder> reportQueryBuilderList, Object bean) {
    Map<String, List<Object>> reportQueryBuilderResultMap = new HashMap<>();
    if (ObjectUtils.isEmpty(reportQueryBuilderList)) {
      return reportQueryBuilderResultMap;
    }
    String queryString = null;
    Map<String, Map<String, Object>> query = new HashMap<>();
    for (ReportQueryBuilder rqb : reportQueryBuilderList) {
      queryString = rqb.getQueryText();
      Map<String, Object> context = new HashMap<>();
      if (ObjectUtils.notEmpty(rqb.getReportQueryBuilderParamsList())) {

        for (ReportQueryBuilderParams params : rqb.getReportQueryBuilderParamsList()) {
          String expression = params.getValue();
          Object value = null;
          if (expression.trim().startsWith("eval:")) {
            value = excelReportGroovyService.validateCondition(expression, bean);
          } else {
            value = expression;
          }
          context.put(params.getName(), value);
        }
      }
      // add select query according to metaModel
      queryString = getQueryString(queryString, rqb);

      query.put(queryString, context);
      reportQueryBuilderResultMap.put(rqb.getVar(), this.getResult(query));
    }
    return reportQueryBuilderResultMap;
  }

  @Override
  public void getReportQueryBuilderCollectionEntry(ReportParameterVariables reportVariables)
      throws ScriptException, ClassNotFoundException {

    boolean isFirstIteration = true;
    if (reportVariables.isHide()) {
      reportVariables.getRemoveCellKeyList().add(reportVariables.getEntry().getKey());
    }

    Map<String, Object> newEntryValueMap = new HashMap<>(reportVariables.getEntryValueMap());
    excelReportShiftingService.shiftRows(
        newEntryValueMap,
        reportVariables.getTotalRecord(),
        reportVariables.getCollectionEntryRow(),
        reportVariables.getMergedCellsRangeAddressList(),
        reportVariables.getMergedCellsRangeAddressSetPerSheet());

    reportVariables.setRowNumber((int) newEntryValueMap.get(ExcelReportConstants.KEY_ROW));
    String fieldName = reportVariables.getFieldName().trim();
    boolean isModel = reportVariables.isModel();
    String className = null;
    Mapper mapper = null;
    String modelAlias = null;

    if (!reportVariables.getCollection().isEmpty()) {
      List<Object> collectionList = (List<Object>) reportVariables.getCollection();

      if (isModel) {
        modelAlias =
            ((LinkedHashMap<String, Object>) collectionList.get(0)).keySet().iterator().next();
        className =
            ((LinkedHashMap<String, Object>)
                    ((List<Object>) reportVariables.getCollection()).get(0))
                .get(modelAlias)
                .getClass()
                .getName();
        mapper = excelReportHelperService.getMapper(className);
      }
      int rowOffset =
          this.setCollection(
              reportVariables,
              newEntryValueMap,
              mapper,
              fieldName,
              modelAlias,
              isFirstIteration,
              isModel);

      if (reportVariables.getRecord() == 0) reportVariables.setRecord(rowOffset - 1);
    } else {

      newEntryValueMap.replace(ExcelReportConstants.KEY_VALUE, "");
      reportVariables.getOutputMap().put(reportVariables.getEntry().getKey(), newEntryValueMap);
    }
    if (!reportVariables.isNextRowCheckActive()) reportVariables.setNextRowCheckActive(true);
  }

  private int setCollection(
      ReportParameterVariables reportVariables,
      Map<String, Object> newEntryValueMap,
      Mapper mapper,
      String fieldName,
      String modelAlias,
      boolean isFirstIteration,
      boolean isModel)
      throws ScriptException {
    int localMergeOffset = 0;
    int rowOffset = 0;
    for (Object ob : reportVariables.getCollection()) {
      Map<String, Object> newMap = new HashMap<>();

      newMap.putAll(newEntryValueMap);
      newMap.replace(
          ExcelReportConstants.KEY_ROW,
          reportVariables.getRowNumber() + rowOffset + localMergeOffset);

      Object keyValue =
          this.getKeyValue(
              reportVariables.getPrintTemplate(),
              ob,
              mapper,
              Triple.of(reportVariables.getOperationString(), modelAlias, fieldName),
              isModel,
              reportVariables.isHide());
      newMap.replace(ExcelReportConstants.KEY_VALUE, keyValue);

      while (reportVariables.getOutputMap().containsKey(reportVariables.getIndex()))
        reportVariables.setIndex(reportVariables.getIndex() + 1);
      if (isFirstIteration) {
        reportVariables.setIndex(reportVariables.getEntry().getKey());
        isFirstIteration = false;
      }

      reportVariables.getOutputMap().put(reportVariables.getIndex(), newMap);
      reportVariables.setIndex(reportVariables.getIndex() + 1);
      rowOffset = rowOffset + localMergeOffset + 1;

      if (rowOffset == 0 && reportVariables.getMergeOffset() != 0)
        localMergeOffset = reportVariables.getMergeOffset();
    }
    return rowOffset;
  }

  private Object getKeyValue(
      PrintTemplate printTemplate,
      Object ob,
      Mapper mapper,
      Triple<String, String, String> operationStringModelAliasFieldName,
      boolean isModel,
      boolean isHide)
      throws ScriptException {
    Object keyValue = "";
    Object value =
        this.getValue(
            ob,
            mapper,
            operationStringModelAliasFieldName.getMiddle(),
            operationStringModelAliasFieldName.getRight(),
            isModel);

    if (ObjectUtils.isEmpty(value) || isHide) {
      keyValue = "";
    } else if (value.getClass() == LocalDate.class || value.getClass() == LocalDateTime.class) {
      keyValue = excelReportHelperService.getDateTimeFormat(value);
    } else {
      keyValue = value;
    }

    if (StringUtils.notEmpty(operationStringModelAliasFieldName.getLeft())) {
      keyValue =
          excelReportGroovyService.calculateFromString(
              keyValue.toString().concat(operationStringModelAliasFieldName.getLeft()),
              excelReportHelperService.getBigDecimalScale());
    }
    keyValue = excelReportTranslationService.getTranslatedValue(keyValue, printTemplate);

    return keyValue;
  }

  private Object getValue(
      Object ob, Mapper mapper, String modelAlias, String fieldName, boolean isModel) {
    Object value = null;
    if (isModel) {
      ImmutablePair<Property, Object> resultPair =
          excelReportHelperService.findField(
              mapper, ((LinkedHashMap<String, Object>) ob).get(modelAlias), fieldName);
      value =
          ObjectUtils.notEmpty(resultPair)
                  && ObjectUtils.notEmpty(resultPair.getRight())
                  && ObjectUtils.notEmpty(resultPair.getLeft())
              ? resultPair.getLeft().get(resultPair.getRight())
              : "";
    } else {
      Map<String, String> recordMap = (LinkedHashMap<String, String>) ob;
      value = recordMap.get(fieldName);
    }
    return value;
  }

  private String getQueryString(String queryString, ReportQueryBuilder rqb) {
    if (StringUtils.isEmpty(queryString) && ObjectUtils.notEmpty(rqb.getMetaModel())) {
      queryString = String.format("SELECT self as self FROM %s self", rqb.getMetaModel().getName());
    } else if (StringUtils.notEmpty(queryString)
        && ObjectUtils.notEmpty(rqb.getMetaModel())
        && (!queryString.contains("Select") && !queryString.contains("SELECT"))) {
      queryString =
          String.format(
              "SELECT self as self FROM %s self where %s",
              rqb.getMetaModel().getName(), queryString);
    } else if ((StringUtils.isEmpty(queryString) && ObjectUtils.isEmpty(rqb.getMetaModel()))
        || (StringUtils.notEmpty(queryString)
            && (!queryString.contains("Select") && !queryString.contains("SELECT"))
            && ObjectUtils.isEmpty(rqb.getMetaModel()))) {
      queryString = "";
    }
    return queryString;
  }

  private List<Object> getResult(Map<String, Map<String, Object>> reportQuery) {

    String queryString = reportQuery.keySet().stream().findFirst().get();
    Map<String, Object> context = reportQuery.get(queryString);
    if (queryString.isEmpty()) {
      return new ArrayList<>();
    }
    Query query = JPA.em().createQuery(queryString);
    query.unwrap(org.hibernate.query.Query.class).setResultTransformer(new DataSetTransformer());
    QueryBinder.of(query).bind(context);
    return query.getResultList();
  }

  @SuppressWarnings("serial")
  private static final class DataSetTransformer extends BasicTransformerAdapter {

    @Override
    public Object transformTuple(Object[] tuple, String[] aliases) {
      Map<String, Object> result = new LinkedHashMap<>(tuple.length);
      for (int i = 0; i < tuple.length; ++i) {
        String alias = aliases[i];
        if (alias != null) {
          result.put(alias, tuple[i]);
        }
      }
      return result;
    }
  }
}
