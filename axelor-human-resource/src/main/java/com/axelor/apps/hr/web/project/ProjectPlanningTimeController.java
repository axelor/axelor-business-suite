/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.hr.web.project;

import com.axelor.apps.hr.service.project.ProjectPlanningTimeService;
import com.axelor.apps.project.db.ProjectPlanningTime;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Singleton
public class ProjectPlanningTimeController {

  @Inject private ProjectPlanningTimeService projectPlanningTimeService;

  public void showPlanning(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();

    Collection<Map<String, Object>> users =
        (Collection<Map<String, Object>>) context.get("userSet");

    String userIds = "";
    if (users != null) {
      for (Map<String, Object> user : users) {
        if (userIds.isEmpty()) {
          userIds = user.get("id").toString();
        } else {
          userIds += "," + user.get("id").toString();
        }
      }
    }

    ActionViewBuilder builder =
        ActionView.define(I18n.get("Project Planning time"))
            .model(ProjectPlanningTime.class.getName());
    String url = "project/planning";

    builder.add("html", url);
    response.setView(builder.map());
    response.setCanClose(true);
  }

  public void addMultipleProjectPlanningTime(ActionRequest request, ActionResponse response) throws AxelorException {

    Context context = request.getContext();

    projectPlanningTimeService.addMultipleProjectPlanningTime(context);

    response.setCanClose(true);
  }
}
