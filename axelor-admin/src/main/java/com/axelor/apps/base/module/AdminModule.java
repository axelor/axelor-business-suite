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
package com.axelor.apps.base.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.base.db.repo.DataBackupBaseRepository;
import com.axelor.apps.base.db.repo.DataBackupRepository;
import com.axelor.apps.base.db.repo.ObjectDataConfigExportManagementRepository;
import com.axelor.apps.base.db.repo.ObjectDataConfigExportRepository;
import com.axelor.apps.base.db.repo.RecordingBaseRepository;
import com.axelor.apps.base.db.repo.RecordingRepository;
import com.axelor.apps.base.recording.RecordingListener;
import com.axelor.apps.base.service.ObjectDataAnonymizeService;
import com.axelor.apps.base.service.ObjectDataAnonymizeServiceImpl;
import com.axelor.apps.base.service.ObjectDataExportService;
import com.axelor.apps.base.service.ObjectDataExportServiceImpl;
import com.axelor.apps.base.service.RecordingService;
import com.axelor.apps.base.service.RecordingServiceImpl;
import com.axelor.apps.base.service.app.AccessConfigImportService;
import com.axelor.apps.base.service.app.AccessConfigImportServiceImpl;
import com.axelor.apps.base.service.app.AccessTemplateService;
import com.axelor.apps.base.service.app.AccessTemplateServiceImpl;
import com.axelor.apps.base.service.app.AppService;
import com.axelor.apps.base.service.app.AppServiceImpl;
import com.axelor.apps.base.service.app.DataBackupService;
import com.axelor.apps.base.service.app.DataBackupServiceImpl;

public class AdminModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(AppService.class).to(AppServiceImpl.class);
    bind(ObjectDataExportService.class).to(ObjectDataExportServiceImpl.class);
    bind(ObjectDataAnonymizeService.class).to(ObjectDataAnonymizeServiceImpl.class);
    bind(AccessTemplateService.class).to(AccessTemplateServiceImpl.class);
    bind(AccessConfigImportService.class).to(AccessConfigImportServiceImpl.class);
    bind(DataBackupService.class).to(DataBackupServiceImpl.class);
    bind(ObjectDataConfigExportRepository.class)
        .to(ObjectDataConfigExportManagementRepository.class);
    bind(RecordingListener.class);
    bind(RecordingService.class).to(RecordingServiceImpl.class);
    bind(RecordingRepository.class).to(RecordingBaseRepository.class);
    bind(DataBackupRepository.class).to(DataBackupBaseRepository.class);
  }
}
