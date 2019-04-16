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
package com.axelor.apps.businesssupport.web;

import com.axelor.apps.project.db.repo.ProjectCategoryRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.team.db.TeamTask;

public class TeamTaskController {

  public void onChangeCategory(ActionRequest request, ActionResponse response) {

    TeamTask task = request.getContext().asType(TeamTask.class);

    if (task.getProjectCategory() == null
        || task.getProjectCategory().getDefaultInvoicing()
            == ProjectCategoryRepository.DEFAULT_INVOICING_N0) {
      response.setValue("teamTaskInvoicing", false);
      response.setValue("teamTaskInvoicing", null);
    } else {
      response.setValue("teamTaskInvoicing", true);
      if (task.getProjectCategory().getDefaultInvoicing()
          == ProjectCategoryRepository.DEFAULT_INVOICING_TIME_SPENT) {
        response.setValue("invoicingType", ProjectRepository.INVOICING_TYPE_TIME_BASED);
      } else {
        response.setValue("invoicingType", ProjectRepository.INVOICING_TYPE_PACKAGE);
      }
    }
  }
}
