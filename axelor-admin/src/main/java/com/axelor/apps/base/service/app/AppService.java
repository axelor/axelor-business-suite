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
package com.axelor.apps.base.service.app;

import com.axelor.app.AppConfig;
import com.axelor.apps.base.db.App;
import com.axelor.exception.AxelorException;
import java.io.IOException;
import java.util.Collection;

public interface AppService extends AppConfig {

  public App importDataDemo(App app) throws AxelorException;

  public App getApp(String type);

  public boolean isApp(String type);

  public App installApp(App app, String language) throws AxelorException;

  public App unInstallApp(App app) throws AxelorException;

  public void refreshApp() throws IOException, ClassNotFoundException;

  public void bulkInstall(Collection<App> apps, Boolean importDeomo, String language)
      throws AxelorException;

  public App importRoles(App app) throws AxelorException;

  public void importRoles() throws AxelorException;
}
