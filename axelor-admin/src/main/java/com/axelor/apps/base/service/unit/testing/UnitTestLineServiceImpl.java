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

import com.axelor.apps.base.db.UnitTest;
import com.axelor.apps.base.db.UnitTestLine;
import com.axelor.apps.base.db.repo.UnitTestLineRepository;
import com.axelor.apps.tool.StringTool;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.db.mapper.PropertyType;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaStore;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnitTestLineServiceImpl implements UnitTestLineService {

  private static final String ACTION_HANDLER_METHOD = "actionHandler";

  protected UnitTestService testService;

  @Inject
  public UnitTestLineServiceImpl(UnitTestService testService) {
    this.testService = testService;
  }

  @Override
  public String generateTestScript(
      UnitTestLine unitTestLine, Map<String, Object> context, Set<String> parentContextVarSet) {
    final String target = unitTestLine.getTarget();
    final String value = unitTestLine.getValue();
    String statement = null;

    switch (unitTestLine.getActionTypeSelect()) {
      case UnitTestLineRepository.ACTION_TYPE_SELECT_ASSIGN:
        statement = getAssignStatement(target, value, context);
        break;
      case UnitTestLineRepository.ACTION_TYPE_SELECT_CREATE:
        statement = getCreateStatement(target, value, context);
        break;
      case UnitTestLineRepository.ACTION_TYPE_SELECT_CALL_ACTION:
        statement = getCallActionStatement(unitTestLine, parentContextVarSet);
        break;
      case UnitTestLineRepository.ACTION_TYPE_SELECT_CALL_SERVICE:
        statement = getCallServiceStatement(unitTestLine);
        break;
      case UnitTestLineRepository.ACTION_TYPE_SELECT_SELECT:
        statement = getSelectStatement(unitTestLine, context);
        break;
      case UnitTestLineRepository.ACTION_TYPE_SELECT_ASSERT:
        statement = getAssertStatement(target, value, unitTestLine.getAssertTypeSelect());
        break;
      case UnitTestLineRepository.ACTION_TYPE_SELECT_ADD:
        statement = getAddStatement(target, value);
        break;
      default:
        statement = "";
        break;
    }

    if (unitTestLine.getIsIncludedInContext() && StringUtils.notBlank(unitTestLine.getTarget())) {
      parentContextVarSet.add(unitTestLine.getTarget());
    }

    return statement;
  }

  @Override
  public Boolean isTargetUndefined(UnitTest test, UnitTestLine testLine) {
    String target = testLine.getTarget();
    if (StringUtils.isBlank(target) || !target.contains(".")) {
      return Boolean.FALSE;
    }

    String targetVar = target.split("\\.")[0];
    List<UnitTestLine> testLineList = testService.getSortedTestLines(test);

    Boolean isUndefined = Boolean.TRUE;

    for (UnitTestLine line : testLineList) {
      if (line.equals(testLine)) {
        break;
      }
      if (StringUtils.isBlank(line.getTarget())) {
        continue;
      }
      if (line.getTarget().equals(targetVar)) {
        isUndefined = Boolean.FALSE;
      }
    }

    return isUndefined;
  }

  @Override
  public Boolean isValidAction(UnitTestLine testLine) {
    final String actions = testLine.getValue();

    if (!UnitTestLineRepository.ACTION_TYPE_SELECT_CALL_ACTION.equals(
        testLine.getActionTypeSelect())) {
      return Boolean.FALSE;
    }

    if (StringUtils.isBlank(actions)) {
      return Boolean.FALSE;
    }

    String[] actionsArray = actions.split("\\,");

    if (ObjectUtils.isEmpty(actionsArray)) {
      return Boolean.FALSE;
    }

    return Stream.of(actionsArray)
        .filter(name -> !Arrays.asList("save").contains(name))
        .map(MetaStore::getAction)
        .anyMatch(Objects::isNull);
  }

  protected String getCallActionStatement(
      UnitTestLine unitTestLine, Set<String> parentContextVarSet) {
    final String target = unitTestLine.getTarget();
    final String beanClassName = Beans.class.getName();
    final String testServiceClassName = UnitTestService.class.getName();
    final String value = unitTestLine.getValue().trim();
    final String input = unitTestLine.getInput();
    final String parentContextStr =
        ObjectUtils.isEmpty(parentContextVarSet)
            ? "[:]"
            : parentContextVarSet.stream()
                .map(name -> String.format("'%s':%s", name, name))
                .collect(Collectors.joining(",", "[", "]"));
    String withoutTargetStr =
        String.format(
            "%s.get(%s.class).%s('%s',%s,%s)",
            beanClassName,
            testServiceClassName,
            ACTION_HANDLER_METHOD,
            value,
            input,
            parentContextStr);
    if (StringUtils.isBlank(target)) {
      return withoutTargetStr;
    }
    return String.format("%s = %s", target, withoutTargetStr);
  }

  protected String getCallServiceStatement(UnitTestLine unitTestLine) {
    final String beanClassName = Beans.class.getName();
    String input = Optional.ofNullable(unitTestLine.getInput()).map(String::trim).orElse("");
    String target = unitTestLine.getTarget();
    String[] valueParts = unitTestLine.getValue().split(":");
    String klass = valueParts[0];
    String method = valueParts[1];
    String withoutTargetStr =
        String.format("%s.get(%s.class).%s(%s)", beanClassName, klass, method, input);
    if (StringUtils.isBlank(target)) {
      return withoutTargetStr;
    }
    return String.format("%s = %s", target.trim(), withoutTargetStr);
  }

  protected String getCreateStatement(String target, String value, Map<String, Object> context) {
    String statement = String.format("%s = new %s()", target, value);
    context.put(target, value);
    return statement;
  }

  protected String getAssertStatement(String target, String value, String operationTypeSelect) {

    if (StringUtils.isBlank(target)) {
      return String.format("assert( %s )", value);
    }

    return String.format("assert %s %s %s", target, operationTypeSelect, value);
  }

  protected String getAssignStatement(String target, String value, Map<String, Object> context) {

    if (isEnclosedByQuotes(value)) {
      String[] targetParents = target.split("\\.");
      String targetModel = context.get(targetParents[0]).toString();
      Class<?> targetClass = JPA.model(targetModel);
      Property prop = null;
      int i = 0;
      do {
        prop = findTargetProp(targetClass, targetParents[i + 1]);
        if (!isReferenceField(prop)) {
          break;
        }
        targetClass = prop.getTarget();
        i++;
      } while (i < targetParents.length - 1);

      if (isReferenceField(prop)) {
        return String.format(
            "%s = __repo__(%s).all().filter(\"self.%s = ?1\",%s).fetchOne()",
            target, prop.getTarget().getSimpleName(), prop.getTargetName(), value);
      }
    }
    return String.format("%s = %s", target, value);
  }

  protected String getSelectStatement(UnitTestLine testLine, Map<String, Object> context) {
    String target = testLine.getTarget();
    String value = testLine.getValue();
    String params = testLine.getInput();
    String[] valueParts = value.split(":");
    String model = valueParts[0].trim();
    String filter = valueParts[1].trim();

    context.put(target, model);

    if (StringUtils.isBlank(params)) {
      return String.format(
          "%s = __repo__(%s).all().filter(\"%s\").fetchOne()", target, model, filter);
    }
    return String.format(
        "%s = __repo__(%s).all().filter(\"%s\",%s).fetchOne()",
        target, model, filter, params.trim());
  }

  protected String getAddStatement(String target, String value) {
    String[] targets = target.split("\\.");
    String fieldName = targets[targets.length - 1];
    String prefix = Stream.of(targets).limit(targets.length - 1L).collect(Collectors.joining("."));
    return String.format("%s.add%sItem(%s)", prefix, StringTool.toFirstUpper(fieldName), value);
  }

  protected boolean isReferenceField(Property property) {
    return property.getType() == PropertyType.MANY_TO_ONE
        || property.getType() == PropertyType.ONE_TO_ONE;
  }

  protected boolean isEnclosedByQuotes(String value) {
    return (value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("\'") && value.endsWith("\'"));
  }

  protected Property findTargetProp(Class<?> klass, String fieldName) {
    return Mapper.of(klass).getProperty(fieldName);
  }
}
