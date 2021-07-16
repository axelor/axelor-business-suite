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
package com.axelor.apps.base.service.app;

import com.axelor.apps.base.db.DataBackup;
import com.axelor.apps.base.db.repo.DataBackupRepository;
import com.axelor.auth.AuditableRunner;
import com.axelor.db.JPA;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.google.inject.servlet.RequestScoper;
import com.google.inject.servlet.ServletScopes;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataBackupServiceImpl implements DataBackupService {

  static final String CONFIG_FILE_NAME = "config.xml";

  @Inject private DataBackupCreateService createService;

  @Inject private DataBackupRestoreService restoreService;

  @Inject private MetaFiles metaFiles;

  @Inject private DataBackupRepository dataBackupRepository;

  @Inject private MetaModelRepository metaModelRepo;

  private ExecutorService executor = Executors.newCachedThreadPool();

  @Override
  public void createBackUp(DataBackup dataBackup) {
    DataBackup obj = setStatus(dataBackup);
    if (dataBackup.getUpdateImportId()) {
      updateImportId();
    }
    try {
      executor.submit(
          new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
              RequestScoper scope = ServletScopes.scopeRequest(Collections.emptyMap());
              try (RequestScoper.CloseableScope ignored = scope.open()) {
                startBackup(obj);
              }
              return true;
            }
          });
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void startBackup(DataBackup dataBackup) throws Exception {
    final AuditableRunner runner = Beans.get(AuditableRunner.class);
    final Callable<Boolean> job =
        new Callable<Boolean>() {
          @Override
          public Boolean call() throws Exception {
            Logger LOG = LoggerFactory.getLogger(getClass());
            DataBackup obj = Beans.get(DataBackupRepository.class).find(dataBackup.getId());
            obj = createService.create(obj, true, null);
            MetaFile logFile = obj.getLogMetaFile();
            MetaFile zipFile = obj.getBackupMetaFile();
            int status = obj.getStatusSelect();
            obj = dataBackupRepository.find(obj.getId());
            if (status != DataBackupRepository.DATA_BACKUP_STATUS_ERROR) {
              obj.setBackupMetaFile(zipFile);
              obj.setStatusSelect(DataBackupRepository.DATA_BACKUP_STATUS_CREATED);
            } else {
              obj.setStatusSelect(DataBackupRepository.DATA_BACKUP_STATUS_ERROR);
            }
            obj.setLogMetaFile(logFile);
            dataBackupRepository.save(obj);
            LOG.info("Data BackUp Saved");
            return true;
          }
        };
    runner.run(job);
  }

  @Override
  public void restoreBackUp(DataBackup dataBackup) {
    DataBackup obj = setStatus(dataBackup);

    try {
      executor.submit(
          new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
              startRestore(dataBackup);
              return true;
            }
          });
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  protected void startRestore(DataBackup dataBackup) throws Exception {
    final AuditableRunner runner = Beans.get(AuditableRunner.class);
    final Callable<Boolean> job =
        new Callable<Boolean>() {
          @Override
          public Boolean call() throws Exception {
            Logger LOG = LoggerFactory.getLogger(getClass());
            DataBackup obj = Beans.get(DataBackupRepository.class).find(dataBackup.getId());
            File logFile = restoreService.restore(obj.getBackupMetaFile());
            save(logFile, obj);
            LOG.info("Data Restore Saved");
            return true;
          }

          public void save(File logFile, DataBackup obj) {
            try {
              JPA.em().getTransaction().begin();
              obj = Beans.get(DataBackupRepository.class).find(dataBackup.getId());
              if (logFile != null) {
                obj.setStatusSelect(DataBackupRepository.DATA_BACKUP_STATUS_RESTORE);
                obj.setLogMetaFile(metaFiles.upload(logFile));
              } else {
                obj.setStatusSelect(DataBackupRepository.DATA_BACKUP_STATUS_RESTORE_ERROR);
              }
              Beans.get(DataBackupRepository.class).save(obj);
              JPA.em().getTransaction().commit();
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        };
    runner.run(job);
  }

  public boolean sequencesOrMrpLineTypesExist() {
    return restoreService.sequencesOrMrpLineTypesExist();
  }

  @Transactional
  protected DataBackup setStatus(DataBackup dataBackup) {
    DataBackup obj = dataBackupRepository.find(dataBackup.getId());
    obj.setStatusSelect(DataBackupRepository.DATA_BACKUP_STATUS_IN_PROGRESS);
    return dataBackupRepository.save(obj);
  }

  @Transactional
  public void updateImportId() {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyHHmm");
    String filterStr =
        "self.packageName NOT LIKE '%meta%' AND self.packageName !='com.axelor.studio.db' AND self.name!='DataBackup' AND self.tableName IS NOT NULL";

    List<MetaModel> metaModelList = metaModelRepo.all().filter(filterStr).fetch();
    metaModelList.add(metaModelRepo.findByName(MetaFile.class.getSimpleName()));
    metaModelList.add(metaModelRepo.findByName(MetaJsonField.class.getSimpleName()));

    for (MetaModel metaModel : metaModelList) {
      try {
        Class.forName(metaModel.getFullName());
        String currentDateTimeStr = "'" + LocalDateTime.now().format(formatter).toString() + "'";
        String query =
            "Update "
                + metaModel.getName()
                + " self SET self.importId = CONCAT(CAST(self.id as text),"
                + currentDateTimeStr
                + ") WHERE self.importId=null";
        JPA.execute(query);
      } catch (ClassNotFoundException e) {
        TraceBackService.trace(e);
      }
    }
  }
}
