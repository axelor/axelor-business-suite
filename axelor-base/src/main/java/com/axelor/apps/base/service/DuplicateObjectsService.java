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
package com.axelor.apps.base.service;

import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.db.JPA;
import com.axelor.db.JpaSecurity;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.axelor.rpc.filter.Filter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DuplicateObjectsService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final Logger log = LoggerFactory.getLogger(DuplicateObjectsService.class);
  @Inject private MetaFieldRepository metaFieldRepo;

  @Transactional
  public void removeDuplicate(List<Long> selectedIds, String modelName) {

    List<Object> duplicateObjects = getDuplicateObject(selectedIds, modelName);
    Object originalObjct = getOriginalObject(selectedIds, modelName);
    List<MetaField> allField =
        metaFieldRepo
            .all()
            .filter(
                "(relationship = 'ManyToOne' AND typeName = ?1) OR (relationship = 'ManyToMany' AND (typeName = ?1 OR metaModel.name =?1))",
                modelName)
            .fetch();
    for (MetaField metaField : allField) {
      if ("ManyToOne".equals(metaField.getRelationship())) {
        Query update =
            JPA.em()
                .createQuery(
                    "UPDATE "
                        + metaField.getMetaModel().getFullName()
                        + " self SET self."
                        + metaField.getName()
                        + " = :value WHERE self."
                        + metaField.getName()
                        + " in (:duplicates)");
        update.setParameter("value", originalObjct);
        update.setParameter("duplicates", duplicateObjects);
        update.executeUpdate();
      } else if ("ManyToMany".equals(metaField.getRelationship())) {

        if (metaField.getTypeName().equals(modelName)) {
          Query select =
              JPA.em()
                  .createQuery(
                      "select self from "
                          + metaField.getMetaModel().getFullName()
                          + " self LEFT JOIN self."
                          + metaField.getName()
                          + " as x WHERE x IN (:ids)");
          select.setParameter("ids", duplicateObjects);
          List<?> list = select.getResultList();
          for (Object obj : list) {
            Set<Object> items =
                (Set<Object>) Mapper.of(obj.getClass()).get(obj, metaField.getName());
            for (Object dupObj : duplicateObjects) {
              if (items.contains(dupObj)) {
                items.remove(dupObj);
              }
            }
            items.add(originalObjct);
          }
        }
        Mapper mapper = Mapper.of(originalObjct.getClass());
        Set<Object> existRelationalObjects =
            (Set<Object>) mapper.get(originalObjct, metaField.getName());

        for (int i = 0; i < duplicateObjects.size(); i++) {
          Set<Object> newRelationalObjects =
              (Set<Object>) mapper.get(duplicateObjects.get(i), metaField.getName());
          if (newRelationalObjects != null) {
            existRelationalObjects.addAll(newRelationalObjects);
            mapper.set(duplicateObjects.get(i), metaField.getName(), new HashSet<>());
          }
        }
      }
    }
    JPA.em().flush();
    JPA.em().clear();
    for (Object obj : getDuplicateObject(selectedIds, modelName)) {
      JPA.em().remove(obj);
    }
  }

  @Transactional
  public Object getOriginalObject(List<Long> selectedIds, String modelName) {
    Query originalObj =
        JPA.em().createQuery("SELECT self FROM " + modelName + " self WHERE self.id = :ids");
    originalObj.setParameter("ids", selectedIds.get(0));
    Object originalObjct = originalObj.getSingleResult();
    return originalObjct;
  }

  @Transactional
  public List<Object> getDuplicateObject(List<Long> selectedIds, String modelName) {
    Query duplicateObj =
        JPA.em().createQuery("SELECT self FROM " + modelName + " self WHERE self.id IN (:ids)");
    duplicateObj.setParameter("ids", selectedIds.subList(1, selectedIds.size()));
    List<Object> duplicateObjects = duplicateObj.getResultList();
    return duplicateObjects;
  }

  @Transactional
  public List<Object> getAllSelectedObject(List<Long> selectedIds, String modelName) {
    Query duplicateObj =
        JPA.em().createQuery("SELECT self FROM " + modelName + " self WHERE self.id IN (:ids)");
    duplicateObj.setParameter("ids", selectedIds);
    List<Object> duplicateObjects = duplicateObj.getResultList();
    return duplicateObjects;
  }

  @Transactional
  public Object getWizardValue(Long id, String modelName, String nameColumn) {
    Query selectedObj;
    if (nameColumn == null) {
      selectedObj =
          JPA.em().createQuery("SELECT self.id FROM " + modelName + " self WHERE self.id = :id");
    } else {
      selectedObj =
          JPA.em()
              .createQuery(
                  "SELECT self.id ,self."
                      + nameColumn
                      + " FROM "
                      + modelName
                      + " self WHERE self.id = :id");
    }
    selectedObj.setParameter("id", id);
    Object selectedObject = selectedObj.getSingleResult();
    return selectedObject;
  }

  @SuppressWarnings("unchecked")
  public Filter getJpaSecurityFilter(Class<? extends Model> beanClass) {

    JpaSecurity jpaSecurity = Beans.get(JpaSecurity.class);

    return jpaSecurity.getFilter(JpaSecurity.CAN_READ, beanClass, (Long) null);
  }

  /*
   * find duplicate records
   */
  public List<?> findDuplicatedRecordIds(
      Set<String> fieldSet, Class<? extends Model> modelClass, String filter)
      throws AxelorException {
    if (fieldSet == null || fieldSet.isEmpty()) {
      return null;
    }

    String concatedFields = concatFields(modelClass, fieldSet);

    String subQuery = createSubQuery(modelClass, filter, concatedFields);
    log.debug("Duplicate check subquery: {}", concatedFields);

    return fetchDuplicatedRecordIds(modelClass, concatedFields, subQuery, filter);
  }

  /*
   * get all records for duplicate records
   */ protected String concatFields(Class<?> modelClass, Set<String> fieldSet)
      throws AxelorException {

    StringBuilder fields = new StringBuilder("LOWER(concat(");
    Mapper mapper = Mapper.of(modelClass);

    int count = 0;

    for (String field : fieldSet) {
      Property property = mapper.getProperty(field);
      if (property == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.GENERAL_8),
            field,
            modelClass.getSimpleName());
      }
      if (property.isCollection()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.GENERAL_9),
            field);
      }
      if (count != 0) {
        fields.append(",");
      }
      count++;
      fields.append("cast(self" + "." + field);

      if (property.getTarget() != null) {
        fields.append(".id");
      }
      fields.append(" as string)");
    }
    fields.append("))");

    return fields.toString();
  }

  protected String createSubQuery(Class<?> modelClass, String filter, String concatedFields) {

    StringBuilder queryBuilder = new StringBuilder("SELECT ");
    queryBuilder.append(concatedFields);
    queryBuilder.append(" FROM ");
    queryBuilder.append(modelClass.getSimpleName() + " self");
    if (filter != null) {
      queryBuilder.append(" WHERE " + filter);
    }
    queryBuilder.append(" GROUP BY ");
    queryBuilder.append(concatedFields);
    queryBuilder.append(" HAVING COUNT(self) > 1");

    return queryBuilder.toString();
  }

  protected List<?> fetchDuplicatedRecordIds(
      Class<? extends Model> modelClass, String concatedFields, String subQuery, String filter) {

    log.debug("Fetch duplicated records for: {}", modelClass);

    StringBuilder queryBuilder = new StringBuilder("SELECT self.id FROM ");
    queryBuilder.append(modelClass.getSimpleName() + " self");
    queryBuilder.append(" WHERE ");
    queryBuilder.append(concatedFields);
    queryBuilder.append(" IN ");
    queryBuilder.append("(" + subQuery + ")");
    if (filter != null) {
      queryBuilder.append(" AND " + filter);
    }

    Filter securityFilter = getJpaSecurityFilter(modelClass);
    Object[] params = new Object[] {};
    if (securityFilter != null) {
      queryBuilder.append(" AND (" + securityFilter.getQuery() + ")");
      log.debug("JPA filter query: {}", securityFilter.getQuery());
      params = securityFilter.getParams().toArray();
      log.debug("JPA filter params: {}", securityFilter.getParams());
    }

    String query = queryBuilder.toString();
    ;

    log.debug("Final query prepared: {}", query);

    Query finalQuery = JPA.em().createQuery(query);

    for (int i = 0; i < params.length; i++) {
      finalQuery.setParameter(i, params[i]);
    }

    return finalQuery.getResultList();
  }
}
