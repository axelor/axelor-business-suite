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
package com.axelor.apps.base.service;

import com.axelor.apps.base.db.DataBackup;
import com.axelor.apps.base.db.Recording;
import com.axelor.apps.base.db.SearchConfiguration;
import com.axelor.apps.base.db.repo.DataBackupRepository;
import com.axelor.apps.base.db.repo.RecordingRepository;
import com.axelor.apps.base.exceptions.IExceptionMessages;
import com.axelor.apps.base.service.app.DataBackupCreateService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.annotations.NameColumn;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.persistence.Column;
import javax.validation.constraints.NotNull;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class RecordingServiceImpl implements RecordingService {

  protected RecordingRepository recordingRepo;
  protected DataBackupRepository dataBackupRepo;
  protected DataBackupCreateService dataBackUpService;
  protected MetaModelRepository metaModelRepo;

  protected static Set<String> excludeFieldNameList =
      ImmutableSet.of(
          "importOrigin",
          "id",
          "updatedBy",
          "createdBy",
          "updatedOn",
          "createdOn",
          "archived",
          "version",
          "attrs");

  @Inject
  public RecordingServiceImpl(
      RecordingRepository recordingRepo,
      DataBackupRepository dataBackupRepo,
      DataBackupCreateService dataBackUpService,
      MetaModelRepository metaModelRepo) {
    this.recordingRepo = recordingRepo;
    this.dataBackupRepo = dataBackupRepo;
    this.dataBackUpService = dataBackUpService;
    this.metaModelRepo = metaModelRepo;
  }

  @Override
  @Transactional
  public void addModelIdLog(Recording recording, MetaModel metaModel, Long recordId) {

    try {
      Set<MetaModel> includeModelSet = recording.getIncludeModelSet();
      Set<MetaModel> excludeModelSet = recording.getExcludeModelSet();

      if (excludeModelSet != null
          && !excludeModelSet.isEmpty()
          && excludeModelSet.contains(metaModel)) return;

      if (includeModelSet != null
          && !includeModelSet.isEmpty()
          && !includeModelSet.contains(metaModel)) return;

      String modelIds = recording.getModelIds();

      JSONObject jsonObject =
          Strings.isNullOrEmpty(modelIds) ? new JSONObject() : new JSONObject(modelIds);
      String metaModelFullName = metaModel.getFullName();

      List<String> idsList =
          new ArrayList<>(
              Arrays.asList(
                  jsonObject.containsKey(metaModelFullName)
                      ? jsonObject.getString(metaModelFullName).split(",")
                      : new String[] {recordId.toString()}));

      if (!idsList.contains(recordId.toString())) idsList.add(recordId.toString());

      recording.setModelIds(
          jsonObject
              .put(
                  metaModelFullName,
                  idsList.toString().replaceAll(" ", "").replace("[", "").replace("]", ""))
              .toString());

      recordingRepo.save(recording);
    } catch (JSONException e) {
      TraceBackService.trace(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  @Transactional(rollbackOn = {Exception.class, InterruptedException.class, IOException.class})
  public String stopRecording(Recording recording)
      throws InterruptedException, IOException, JSONException, AxelorException {

    DataBackup dataBackup =
        dataBackupRepo
            .all()
            .filter("self.importId = ?", DataBackupRepository.DATA_BACKUP_RECORDING_IMPORT_ID)
            .fetchOne();

    if (dataBackup != null) {
      recording.setStatusSelect(RecordingRepository.STATUS_STOP);

      JSONObject jsonObject = new JSONObject(recording.getModelIds());

      Set<String> metaModelFNSet = jsonObject.keySet();
      if (metaModelFNSet != null && !metaModelFNSet.isEmpty()) {
        Map<MetaModel, String> metaModelIdListMap = new HashMap<>();
        List<String> searchWarningModelList = new ArrayList<>();
        Map<String, String> metaModelSearchConfigMap = new HashMap<>();
        Set<SearchConfiguration> searchConfigSet = recording.getSearchConfigurationSet();

        for (MetaModel metaModel :
            metaModelRepo.all().filter("self.fullName IN ?", metaModelFNSet).fetch()) {
          metaModelIdListMap.put(metaModel, jsonObject.getString(metaModel.getFullName()));
          manageSearchConfig(
              recording,
              metaModel,
              searchConfigSet,
              metaModelSearchConfigMap,
              searchWarningModelList);
        }

        dataBackup =
            dataBackUpService.create(
                dataBackup, false, metaModelIdListMap, metaModelSearchConfigMap);
        recording.setRecordingData(dataBackup.getBackupMetaFile());
        recording.setStopDateTime(LocalDateTime.now());
        recording.setModelIds(null);
        recordingRepo.save(recording);
        if (!recording.getIsIgnoreRecordsHavingIssues()
            && ObjectUtils.notEmpty(searchWarningModelList)) {
          return String.format(
              I18n.get(IExceptionMessages.RECORDING_SEARCH_CONFIGURATION_WARNING),
              String.join("\n", searchWarningModelList));
        }
      }
    } else {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessages.RECORDING_DATA_BACKUP_MISSING));
    }

    return null;
  }

  protected void manageSearchConfig(
      Recording recording,
      MetaModel metaModel,
      Set<SearchConfiguration> searchConfigSet,
      Map<String, String> metaModelSearchConfigMap,
      List<String> searchWarningModelList) {

    if (ObjectUtils.isEmpty(searchConfigSet)) {
      return;
    }

    String search = null;
    Optional<SearchConfiguration> searchConfigurationOpt =
        searchConfigSet.stream()
            .filter(searchConfig -> searchConfig.getMetaModel().equals(metaModel))
            .findFirst();
    if (searchConfigurationOpt.isPresent()) {
      search = getSearchFromSearchConfiguration(searchConfigurationOpt.get());
    } else {
      search = getSearchFromFieldConfiguration(metaModel);
    }

    if (StringUtils.notBlank(search)) {
      metaModelSearchConfigMap.put(metaModel.getFullName(), search);
    } else if (recording.getIsIgnoreRecordsHavingIssues()) {
      recording.addExcludeModelSetItem(metaModel);
    } else {
      searchWarningModelList.add(metaModel.getFullName());
    }
  }

  protected String getSearchFromSearchConfiguration(SearchConfiguration searchConfiguration) {

    Set<MetaField> searchFields = searchConfiguration.getMetaFieldSet();
    List<String> searchArr = new ArrayList<>();

    for (MetaField metaField : searchFields) {
      String fieldName = metaField.getName();
      if (excludeFieldNameList.contains(fieldName)) {
        continue;
      }

      String fieldSearch = null;
      String relationship = metaField.getRelationship();
      if (StringUtils.isBlank(relationship)) {
        fieldSearch = String.format("self.%s = :%s", fieldName, fieldName);
      } else if (relationship.equalsIgnoreCase("OneToOne")
          || relationship.equalsIgnoreCase("ManyToOne")) {
        fieldSearch = String.format("self.%s = :%s_importId", fieldName, fieldName);
      }
      if (StringUtils.notBlank(fieldSearch)) {
        searchArr.add(fieldSearch);
      }
    }

    if (ObjectUtils.notEmpty(searchArr)) {
      return String.join(" AND ", searchArr);
    }

    return null;
  }

  protected String getSearchFromFieldConfiguration(MetaModel metaModel) {

    try {
      List<Field> fields =
          Arrays.asList(Class.forName(metaModel.getFullName()).getDeclaredFields());

      Field uniqueRequiredField = null;
      for (Field field : fields) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null && columnAnnotation.unique()) {
          NotNull notNullAnnotation = field.getAnnotation(NotNull.class);
          if (notNullAnnotation != null) {
            uniqueRequiredField = field;
            break;
          }
        }
      }

      if (uniqueRequiredField != null) {
        return String.format(
            "self.%s = %s", uniqueRequiredField.getName(), uniqueRequiredField.getName());
      } else {
        Field nameColumnField = null;
        for (Field field : fields) {
          NameColumn nameColumnAnnotation = field.getAnnotation(NameColumn.class);
          if (nameColumnAnnotation != null) {
            nameColumnField = field;
            break;
          }
        }
        if (nameColumnField != null) {
          return String.format(
              "self.%s = %s", nameColumnField.getName(), nameColumnField.getName());
        } else if (fields.stream().anyMatch(field -> field.getName().equals("name"))) {
          return "self.name = name";
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return null;
  }
}
