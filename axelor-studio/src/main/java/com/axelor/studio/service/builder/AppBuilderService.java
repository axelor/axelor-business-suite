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
package com.axelor.studio.service.builder;

import com.axelor.apps.base.db.App;
import com.axelor.apps.base.db.repo.AppRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.studio.db.AppBuilder;
import com.axelor.studio.exception.IExceptionMessage;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.HashSet;
import java.util.Set;

public class AppBuilderService {

  @Inject private AppRepository appRepo;

  public AppBuilder build(AppBuilder appBuilder) throws AxelorException {

    checkCode(appBuilder);

    App app = appBuilder.getGeneratedApp();

    if (app == null) {
      app = new App(appBuilder.getName(), appBuilder.getCode());
    } else {
      app.setCode(appBuilder.getCode());
      app.setName(appBuilder.getName());
    }

    app.setImage(appBuilder.getImage());
    app.setDescription(appBuilder.getDescription());
    Set<App> depends = new HashSet<App>();
    if (appBuilder.getDependsOnSet() != null) {
      depends.addAll(appBuilder.getDependsOnSet());
      app.setDependsOnSet(depends);
    }
    app.setSequence(appBuilder.getSequence());
    app.setInitDataLoaded(true);
    app.setDemoDataLoaded(true);

    appBuilder.setGeneratedApp(appRepo.save(app));

    return appBuilder;
  }

  protected void checkCode(AppBuilder appBuilder) throws AxelorException {

    App app = appRepo.findByCode(appBuilder.getCode());

    if (app != null && app != appBuilder.getGeneratedApp()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.APP_BUILDER_1),
          appBuilder.getCode());
    }
  }

  @Transactional
  public void clean(AppBuilder appBuilder) {

    if (appBuilder.getGeneratedApp() != null) {
      appRepo.remove(appBuilder.getGeneratedApp());
      appBuilder.setGeneratedApp(null);
    }
  }
}
