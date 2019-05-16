/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.project.service.app;

import com.axelor.apps.base.db.AppProject;
import com.axelor.apps.base.db.repo.AppProjectRepository;
import com.axelor.apps.base.service.app.AppBaseServiceImpl;
import com.axelor.event.Observes;
import com.axelor.events.PostRequest;
import com.axelor.events.RequestEvent;
import com.axelor.events.qualifiers.EntityType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.inject.Named;

@Singleton
public class AppProjectServiceImpl extends AppBaseServiceImpl implements AppProjectService {

  @Inject private AppProjectRepository appProjectRepo;

  @Override
  public AppProject getAppProject() {
    return appProjectRepo.all().fetchOne();
  }

  void onAppProjectPostSave(
      @Observes @Named(RequestEvent.SAVE) @EntityType(AppProject.class) PostRequest event) {
    fireFeatureChanged(event, getAppProject());
  }
}
