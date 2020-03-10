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

import com.axelor.apps.base.db.DataConfigLine;
import com.axelor.apps.base.db.ObjectDataConfig;
import com.axelor.apps.base.db.repo.DataConfigLineRepository;
import com.axelor.apps.base.exceptions.IExceptionMessages;
import com.axelor.db.JPA;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.mail.db.repo.MailMessageRepository;
import com.axelor.meta.db.MetaField;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ObjectDataAnonymizeServiceImpl implements ObjectDataAnonymizeService {

  @Inject private MailMessageRepository mailMessageRepo;

  public void anonymize(ObjectDataConfig objectDataConfig, Long recordId) throws AxelorException {

    try {

      String rootModel = objectDataConfig.getModelSelect();

      for (DataConfigLine line : objectDataConfig.getDataConfigLineList()) {
        String path =
            line.getTypeSelect() == DataConfigLineRepository.TYPE_PATH
                ? line.getMetaFieldPath().getName()
                : line.getPath();

        Class<? extends Model> modelClass =
            ObjectDataCommonService.findModelClass(line.getMetaModel());
        Query<? extends Model> query =
            ObjectDataCommonService.createQuery(recordId, line, modelClass);
        List<? extends Model> data = query.fetch();
        Mapper mapper = Mapper.of(modelClass);

        int reset = line.getResetPathSelect();
        if (reset != DataConfigLineRepository.RESET_NONE
            && line.getTypeSelect() == DataConfigLineRepository.TYPE_PATH) {
          if (reset == DataConfigLineRepository.RESET_DELETE) {
            deleteLink(mapper, path, data);
          } else {
            replaceLink(mapper, path, data, rootModel, line.getRecordSelectId());
          }
        }

        deleteFields(line.getToDeleteMetaFieldSet(), mapper, data);
      }

    } catch (Exception e) {
      TraceBackService.trace(e);
      throw new AxelorException(TraceBackRepository.TYPE_TECHNICAL, e.getMessage());
    }
  }

  @Transactional
  public void deleteLink(Mapper mapper, String path, List<? extends Model> data) {

    path = path.split("\\.")[0];
    Property property = mapper.getProperty(path);
    if (property == null || property.isRequired()) {
      return;
    }
    for (Model record : data) {
      mapper.set(record, path, null);
      JPA.save(record);
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public void replaceLink(
      Mapper mapper, String path, List<? extends Model> data, String rootModel, Long recordValue)
      throws ClassNotFoundException, AxelorException {

    path = path.split("\\.")[0];
    Property property = mapper.getProperty(path);
    if (property == null) {
      return;
    }
    Class<? extends Model> klass = (Class<? extends Model>) Class.forName(rootModel);

    JpaRepository<? extends Model> repo = JpaRepository.of(klass);
    Model object = repo.all().filter("self.id = ?1", recordValue).fetchOne();
    if (object == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessages.OBJECT_DATA_REPLACE_MISSING),
          recordValue);
    }
    for (Model record : data) {
      mapper.set(record, path, object);
      JPA.save(record);
    }
  }

  @Transactional
  public void deleteFields(Set<MetaField> fields, Mapper mapper, List<? extends Model> data) {

    if (fields.isEmpty()) {
      return;
    }

    Map<String, Object> defaultValues = getDefaultValues(mapper, fields);

    for (Model record : data) {
      for (String field : defaultValues.keySet()) {
        Object object = defaultValues.get(field);
        mapper.set(record, field, object);
      }
      JPA.save(record);

      mailMessageRepo
          .all()
          .filter(
              "self.relatedId = ?1 AND self.relatedModel = ?2",
              record.getId(),
              mapper.getBeanClass().getName())
          .delete();
    }
  }

  protected Map<String, Object> getDefaultValues(Mapper mapper, Set<MetaField> fields) {

    Map<String, Object> defaultValues = new HashMap<>();

    LocalDate defaultDate = LocalDate.of(1900, 01, 01);

    for (MetaField field : fields) {

      String name = field.getName();
      Property property = mapper.getProperty(name);

      if (!property.isRequired()) {
        defaultValues.put(name, null);
        continue;
      }
      if (property.getTarget() != null) {
        continue;
      }

      switch (field.getTypeName()) {
        case "String":
          {
            defaultValues.put(name, "anonymous");
            break;
          }
        case "BigDecimal":
          {
            defaultValues.put(name, BigDecimal.ZERO);
            break;
          }
        case "Integer":
          {
            defaultValues.put(name, new Integer(0));
            break;
          }
        case "Boolean":
          {
            defaultValues.put(name, Boolean.FALSE);
            break;
          }
        case "Long":
          {
            defaultValues.put(name, new Long(0));
            break;
          }
        case "LocalTime":
          {
            defaultValues.put(name, LocalTime.MIDNIGHT);
            break;
          }
        case "LocalDateTime":
          {
            defaultValues.put(name, defaultDate.atStartOfDay());
            break;
          }
        case "ZonedDateTime":
          {
            defaultValues.put(name, defaultDate.atStartOfDay(ZoneId.systemDefault()));
            break;
          }
        default:
          {
            break;
          }
      }
    }

    return defaultValues;
  }
}
