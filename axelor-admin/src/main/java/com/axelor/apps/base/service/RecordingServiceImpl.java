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
import com.axelor.apps.base.db.repo.DataBackupRepository;
import com.axelor.apps.base.db.repo.RecordingRepository;
import com.axelor.apps.base.exceptions.IExceptionMessages;
import com.axelor.apps.base.service.app.DataBackupCreateService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import wslite.json.JSONException;
import wslite.json.JSONObject;

public class RecordingServiceImpl implements RecordingService {

  protected RecordingRepository recordingRepo;
  protected DataBackupRepository dataBackupRepo;
  protected DataBackupCreateService dataBackUpService;
  protected MetaModelRepository metaModelRepo;

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
  public void stopRecording(Recording recording)
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

        for (MetaModel metaModel :
            metaModelRepo.all().filter("self.fullName IN ?", metaModelFNSet).fetch()) {
          metaModelIdListMap.put(metaModel, jsonObject.getString(metaModel.getFullName()));
        }

        dataBackup = dataBackUpService.create(dataBackup, false, metaModelIdListMap);
        recording.setRecordingData(dataBackup.getBackupMetaFile());
        recording.setStopDateTime(LocalDateTime.now());
        recording.setModelIds(null);
        recordingRepo.save(recording);
      }
    } else {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessages.RECORDING_DATA_BACKUP_MISSING));
    }
  }
}
