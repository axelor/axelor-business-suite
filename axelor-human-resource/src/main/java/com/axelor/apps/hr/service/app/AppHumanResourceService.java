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
package com.axelor.apps.hr.service.app;

import com.axelor.apps.base.db.AppEmployee;
import com.axelor.apps.base.db.AppExpense;
import com.axelor.apps.base.db.AppLeave;
import com.axelor.apps.base.db.AppTimesheet;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public interface AppHumanResourceService extends AppBaseService {

  public AppTimesheet getAppTimesheet();

  public AppLeave getAppLeave();

  public AppExpense getAppExpense();

  public AppEmployee getAppEmployee();

  public void getHrmAppSettings(ActionRequest request, ActionResponse response);

  public void generateHrConfigurations();
}
