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

import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.EntityHelper;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.meta.ActionExecutor;
import com.axelor.meta.ActionHandler;
import com.axelor.meta.MetaStore;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.ContextEntity;
import com.axelor.rpc.ContextHandlerFactory;
import com.axelor.rpc.Resource;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ActionServiceImpl implements ActionService {

  @Inject protected ActionExecutor executor;

  private String modelName;
  private Map<String, Object> context;

  @Override
  public Boolean validate(String actions) {
    if (StringUtils.isBlank(actions)) {
      return false;
    }

    List<String> actionNameList = Arrays.asList(actions.split("\\,"));

    if (ObjectUtils.isEmpty(actionNameList)) {
      return false;
    }

    return !actionNameList.stream().anyMatch(name -> MetaStore.getAction(name) == null);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object apply(String actions, Object bean) {
    if (StringUtils.isBlank(actions) || bean == null) {
      return bean;
    }
    final Class<? extends Model> klass = (Class<? extends Model>) bean.getClass();
    this.modelName = klass.getName();
    this.context = Mapper.toMap(bean);

    apply(actions);
    return updateBean(bean);
  }

  @SuppressWarnings("unchecked")
  protected void apply(String actions) {
    ActionHandler handler = createHandler(actions);
    Object value = handler.execute();
    ActionResponse response = (ActionResponse) value;
    List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.getData();
    for (Map<String, Object> map : dataList) {
      updateContext(map);
    }
  }

  protected ActionHandler createHandler(String action) {

    ActionRequest request = new ActionRequest();

    Map<String, Object> data = new HashMap<>();

    request.setData(data);
    request.setModel(modelName);
    request.setAction(action);
    data.put("context", context);

    return executor.newActionHandler(request);
  }

  @SuppressWarnings("unchecked")
  protected void updateContext(Map<String, Object> value) {

    if (value == null) {
      return;
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
  }

  @SuppressWarnings("unchecked")
  protected <T extends Model> Object updateBean(Object bean) {
    final Class<T> klass = (Class<T>) JPA.model(this.modelName);
    T updatedBean = Mapper.toBean(klass, this.context);

    for (Property property : JPA.fields(klass)) {
      Object oldValue = property.get(bean);
      Object newValue = property.get(updatedBean);
      if (!ObjectUtils.isEmpty(newValue) && property.valueChanged(updatedBean, oldValue)) {
        property.set(bean, this.validate(property, newValue));
      }
    }
    return bean;
  }

  protected Object validate(Property property, Object value) {
    if (property == null) {
      return value;
    }
    if (property.isCollection() && value instanceof Collection) {
      value =
          ((Collection<?>) value)
              .stream()
              .map(item -> createOrFind(property, item))
              .collect(Collectors.toList());
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

    final Object bean = JPA.em().find(property.getTarget(), id);

    return bean;
  }
}
