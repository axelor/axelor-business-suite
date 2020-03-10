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
package com.axelor.studio.service.filter;

import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaJsonFieldRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.studio.db.Filter;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilterGroovyService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private FilterCommonService filterCommonService;

  @Inject private MetaModelRepository metaModelRepo;

  @Inject private MetaJsonFieldRepository metaJsonFieldRepo;

  @Inject private FilterSqlService filterSqlService;

  /**
   * Method to convert chart filter list to groovy expression string. Each filter of list will be
   * joined by logical operator(logicalOp) selected.
   *
   * @param conditions List for chart filters.
   * @param parentField Field that represent parent.
   * @return Groovy expression string.
   * @throws AxelorException
   */
  public String getGroovyFilters(
      List<Filter> conditions, String parentField, boolean isButton, boolean isField)
      throws AxelorException {

    String condition = null;

    if (conditions == null) {
      return null;
    }

    for (Filter filter : conditions) {
      String activeFilter = createGroovyFilter(filter, parentField, isButton, isField);
      log.debug("Active filter: {}", filter);

      if (condition == null) {
        condition = "(" + activeFilter;
      } else if (filter.getLogicOp() > 0) {
        condition += ") || (" + activeFilter;
      } else {
        condition += " && " + activeFilter;
      }
    }

    if (condition == null) {
      return null;
    }

    return condition + ")";
  }

  /**
   * Method to generate groovy expression for a single chart filter.
   *
   * @param chartFilter Chart filter to use .
   * @param parentField Parent field.
   * @return Groovy expression string.
   * @throws AxelorException
   */
  protected String createGroovyFilter(
      Filter filter, String parentField, boolean isButton, boolean isField) throws AxelorException {

    String fieldType = null;
    boolean isJson =
        (!filter.getIsJson() && filter.getMetaField() != null)
            ? false
            : (filter.getIsJson() && filter.getMetaJsonField() != null) ? true : false;

    if (!isJson) {
      fieldType = this.getMetaFieldType(filter.getMetaField(), filter.getTargetField(), true);
    } else {
      fieldType = this.getJsonFieldType(filter.getMetaJsonField(), filter.getTargetField());
    }

    String targetField = filter.getTargetField();
    targetField = !isButton ? targetField.replace(".", "?.") : targetField;

    String value = processValue(filter);
    String operator = filter.getOperator();

    if (isButton || isField) {
      if (isJson && !Strings.isNullOrEmpty(parentField)) {
        boolean isModelFieldSame =
            ("$" + filter.getMetaJsonField().getModelField()).equals(parentField);
        if (!isModelFieldSame && isButton) {
          targetField =
              "$record." + "$" + filter.getMetaJsonField().getModelField() + "." + targetField;
        } else if ((!isModelFieldSame || isModelFieldSame) && isField) {
          targetField = "$" + filter.getMetaJsonField().getModelField() + "." + targetField;
        }
      } else if (!isJson && isButton) {
        targetField = "$record." + targetField;
      }
    }

    return getConditionExpr(operator, targetField, fieldType, value, isButton);
  }

  protected String getJsonFieldType(MetaJsonField jsonField, String targetField)
      throws AxelorException {

    if (targetField == null || !targetField.contains(".")) {
      return jsonField.getType();
    }

    targetField = targetField.substring(targetField.indexOf(".") + 1);
    String targetName =
        targetField.contains(".")
            ? targetField.substring(0, targetField.indexOf("."))
            : targetField;

    if (jsonField.getTargetJsonModel() != null) {
      MetaJsonField subJson =
          metaJsonFieldRepo
              .all()
              .filter(
                  "self.name = ?1 and self.jsonModel = ?2",
                  targetName,
                  jsonField.getTargetJsonModel())
              .fetchOne();
      if (subJson == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            "No sub field found model: %s field %s ",
            jsonField.getTargetJsonModel().getName(),
            targetName);
      }
      return getJsonFieldType(subJson, targetField);

    } else {
      MetaField subMeta = filterSqlService.findMetaField(targetName, jsonField.getTargetModel());
      if (subMeta == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            "No sub field found model: %s field %s ",
            jsonField.getTargetModel(),
            targetName);
      }
      return getMetaFieldType(subMeta, targetField, true);
    }
  }

  protected String getMetaFieldType(MetaField field, String targetField, boolean isJson)
      throws AxelorException {

    if (targetField == null || !targetField.contains(".")) {
      return field.getTypeName();
    }

    targetField = targetField.substring(targetField.indexOf(".") + 1);
    String targetName =
        targetField.contains(".")
            ? targetField.substring(0, targetField.indexOf("."))
            : targetField;

    MetaModel model = metaModelRepo.findByName(field.getTypeName());
    if (model == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD, "No model found: %s ", field.getName());
    }

    MetaField subMeta = filterSqlService.findMetaField(targetName, model.getFullName());
    if (subMeta != null) {
      return getMetaFieldType(subMeta, targetField, isJson);
    } else if (isJson) {
      MetaJsonField subJson = filterSqlService.findJsonField(targetName, model.getName());
      if (subJson != null) {
        return getJsonFieldType(subJson, targetField);
      }
    }
    throw new AxelorException(
        TraceBackRepository.CATEGORY_MISSING_FIELD,
        "No sub field found field: %s model: %s ",
        targetName,
        model.getFullName());
  }

  protected String processValue(Filter filter) {

    String value = filter.getValue();
    if (value == null) {
      return value;
    }

    value = value.replace("$$", "_parent.");

    return filterCommonService.getTagValue(value, false);
  }

  protected String getConditionExpr(
      String operator, String field, String fieldType, String value, boolean isButton) {

    switch (operator) {
      case "isNull":
        return field + " == null";
      case "notNull":
        return field + " != null";
    }

    if (isButton) {
      switch (fieldType) {
        case "date":
        case "datetime":
        case "LocalDate":
        case "LocalDateTime":
          value = "$moment(" + value + ")";
          field = "$moment(" + field + ")";

          switch (operator) {
            case "=":
              return field + ".isSame(" + value + ", 'days')";
            case "!=":
              return "!" + field + ".isSame(" + value + ", 'days')";
          }
      }
    }

    switch (operator) {
      case "=":
        return field + " == " + value;
      case "empty":
        return field + ".empty";
      case "notEmpty":
        return "!" + field + ".empty";
      case "isTrue":
        return field;
      case "isFalse":
        return "!" + field;
      default:
        return field + " " + operator + " " + value;
    }
  }
}
