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
import com.axelor.apps.base.exceptions.IExceptionMessages;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.EntityHelper;
import com.axelor.db.JPA;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.inject.Beans;
import com.axelor.meta.ActionExecutor;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.ContextEntity;
import com.axelor.rpc.ContextHandlerFactory;
import com.axelor.rpc.Resource;
import com.axelor.rpc.Response;
import com.axelor.script.GroovyScriptHelper;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.EntityTransaction;

public class UnitTestServiceImpl implements UnitTestService {

  private static final String SAVE = "save";
  private static final String PENDING = "pending";
  private static final String ALERT = "alert";
  private static final String FLASH = "flash";
  private static final String ERROR = "error";

  protected UnitTestLineService testLineService;

  @Inject
  public UnitTestServiceImpl(UnitTestLineService testLineService) {
    this.testLineService = testLineService;
  }

  @Override
  public String generateTestScript(UnitTest test) {
    List<String> statements = new ArrayList<>();
    Map<String, Object> context = new HashMap<>();
    Set<String> parentContextVarSet = new LinkedHashSet<>();
    for (UnitTestLine unitTestLine : getSortedTestLines(test)) {
      statements.add(
          testLineService.generateTestScript(unitTestLine, context, parentContextVarSet));
    }
    return statements.stream().collect(Collectors.joining("\n"));
  }

  @Override
  public String executeTestScript(UnitTest test) {
    return execute(test);
  }

  @Override
  public String executeTestScriptWithRollback(UnitTest test) {
    final EntityTransaction transaction = JPA.em().getTransaction();
    if (!transaction.isActive()) {
      transaction.begin();
    }
    String result = execute(test);
    transaction.rollback();
    return result;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Model> T actionHandler(String action, T bean, Map<String, Object> extaContext) {
    ActionExecutor executor = Beans.get(ActionExecutor.class);
    ActionRequest req = new ActionRequest();
    ActionResponse res = null;
    Map<String, Object> reqData = new HashMap<>();

    Map<String, Object> context = Mapper.toMap(bean);
    if (ObjectUtils.notEmpty(extaContext)) {
      extaContext.forEach(context::putIfAbsent);
    }
    reqData.put("context", context);

    Class<T> klass = (Class<T>) bean.getClass();
    req.setModel(klass.getName());
    req.setData(reqData);
    req.setAction(action);

    res = executor.execute(req);

    Preconditions.checkArgument(
        Response.STATUS_SUCCESS == res.getStatus(),
        IExceptionMessages.UNIT_TEST_ACTION_SOMETHING_WRONG,
        action);

    Object data = res.getData();
    List<Map<String, Object>> dataList = new ArrayList<>();

    if (data instanceof List) {
      dataList = (List<Map<String, Object>>) data;
    } else if (data instanceof Map) {
      dataList.add((Map<String, Object>) data);
    } else {
      return bean;
    }

    for (Map<String, Object> map : dataList) {
      context = updateContext(klass, context, map, action);
    }

    T updatedBean = Mapper.toBean(klass, context);

    for (Property property : JPA.fields(klass)) {
      Object oldValue = property.get(bean);
      Object newValue = property.get(updatedBean);
      if (!ObjectUtils.isEmpty(newValue) && property.valueChanged(updatedBean, oldValue)) {
        property.set(bean, this.validate(property, newValue));
      }
    }

    bean = handlePendingActions(bean, klass, dataList, extaContext);
    return bean;
  }

  @Override
  public List<UnitTestLine> getSortedTestLines(UnitTest test) {
    List<UnitTestLine> objectList = test.getTestLineList();
    objectList.sort(Comparator.comparingInt(UnitTestLine::getSequence));
    return objectList;
  }

  protected String execute(UnitTest test) {
    try {
      GroovyScriptHelper helper = new GroovyScriptHelper(null);
      Object result = helper.eval(test.getTestScript());
      return processResult(test, result);
    } catch (AssertionError e) {
      return processResult(test, e.getMessage());
    } catch (IllegalArgumentException e) {
      String message =
          org.apache.commons.lang3.StringUtils.substringAfterLast(
                  e.getMessage(), IllegalArgumentException.class.getName())
              .trim();
      if (message.startsWith(":")) {
        message = message.substring(1).trim();
      }
      return message;
    }
  }

  protected String processResult(UnitTest test, Object result) {
    String script = test.getTestScript();
    String resultStr = result == null ? "" : result.toString();
    String[] lines = script.split("\n");
    String failedAssert = null;
    if (StringUtils.notBlank(resultStr) && resultStr.startsWith("assert")) {
      String[] parts = resultStr.split("\n");
      failedAssert = parts[0];
    }

    StringBuilder assertLinesBuilder = new StringBuilder();

    if (ObjectUtils.notEmpty(lines)) {
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        line = line.trim();
        if (!line.startsWith("assert")) {
          continue;
        }
        if (StringUtils.notEmpty(failedAssert) && failedAssert.equals(line)) {
          assertLinesBuilder.append("<pre>" + resultStr + "</pre>");
          break;
        }
        assertLinesBuilder.append(
            String.format("%s : <span style='color:green'>success</span><br/>", line));
      }
    }
    if (assertLinesBuilder.length() > 0) {
      resultStr = assertLinesBuilder.toString();
    }

    return resultStr;
  }

  protected Object validate(Property property, Object value) {
    if (property == null) {
      return value;
    }
    if (property.isCollection() && value instanceof Collection) {
      value =
          ((Collection<?>) value)
              .stream().map(item -> createOrFind(property, item)).collect(Collectors.toList());
    } else if (property.isReference()) {
      value = createOrFind(property, value);
    }
    return value;
  }

  protected Object createOrFind(Property property, Object item) {
    if (item == null) {
      return item;
    }
    final Long id = ((Model) item).getId();

    if (id == null || id <= 0) {
      return EntityHelper.getEntity(
          ContextHandlerFactory.newHandler(property.getTarget(), Resource.toMapCompact(item))
              .getProxy());
    }

    return JPA.em().find(property.getTarget(), id);
  }

  @SuppressWarnings("unchecked")
  protected <T extends Model> Map<String, Object> updateContext(
      Class<T> klass, Map<String, Object> context, Map<String, Object> value, String action) {
    if (value == null) {
      return context;
    }

    checkResponseForErrors(value, action);

    if (Boolean.valueOf(String.valueOf(value.get("reload")))
        && ObjectUtils.notEmpty(context.get("id"))) {
      Object bean = JPA.find(klass, Long.valueOf(context.get("id").toString()));
      return Mapper.toMap(bean);
    }

    Object values = value.get("values");
    Map<String, Object> map = new HashMap<>();

    if (values instanceof ContextEntity) {
      map = ((ContextEntity) values).getContextMap();
    } else if (values instanceof Model) {
      map = Mapper.toMap(value);
    } else if (values instanceof Map) {
      map = (Map<String, Object>) values;
    }

    values = value.get("attrs");

    if (values instanceof Map) {
      for (Object key : ((Map<String, Object>) values).keySet()) {
        String name = key.toString();
        Map<String, Object> attrs = (Map<String, Object>) ((Map<String, Object>) values).get(key);
        if (attrs.containsKey("value")) {
          map.put(name, attrs.get("value"));
        }
        if (attrs.containsKey("value:set")) {
          map.put(name, attrs.get("value:set"));
        }
      }
    }

    context.putAll(map);
    return context;
  }

  protected void checkResponseForErrors(Map<String, Object> value, String action) {
    Object error = value.get(ERROR);
    Preconditions.checkArgument(
        ObjectUtils.isEmpty(error), IExceptionMessages.UNIT_TEST_ERROR_FROM_ACTION, action, error);
    Object alert = value.get(ALERT);
    Preconditions.checkArgument(
        ObjectUtils.isEmpty(alert), IExceptionMessages.UNIT_TEST_ALERT_FROM_ACTION, action, alert);
    Object flash = value.get(FLASH);
    Preconditions.checkArgument(
        ObjectUtils.isEmpty(flash), IExceptionMessages.UNIT_TEST_FLASH_FROM_ACTION, action, flash);
  }

  protected <T extends Model> T handlePendingActions(
      T bean, Class<T> klass, List<Map<String, Object>> dataList, Map<String, Object> extaContext) {

    if (!dataList.toString().contains(PENDING)) {
      return bean;
    }

    for (Map<String, Object> map : dataList) {

      if (!map.containsKey(PENDING)) {
        continue;
      }

      bean = handleSaveAction(bean, klass, map);

      if (ObjectUtils.notEmpty(map.get(PENDING))) {
        String pedingAction = map.get(PENDING).toString();
        bean = actionHandler(pedingAction, bean, extaContext);
      }
    }

    return bean;
  }

  @Transactional
  protected <T extends Model> T handleSaveAction(T bean, Class<T> klass, Map<String, Object> map) {
    if (!Boolean.valueOf(String.valueOf(map.get(SAVE)))) {
      return bean;
    }
    JpaRepository<T> repo = JpaRepository.of(klass);
    return repo.save(bean);
  }
}
