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
package com.axelor.apps.hr.observer;

import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.service.project.ProjectActivityHRService;
import com.axelor.common.ObjectUtils;
import com.axelor.event.Observes;
import com.axelor.events.PreRequest;
import com.axelor.events.RequestEvent;
import com.axelor.events.qualifiers.EntityType;
import com.axelor.inject.Beans;
import java.util.List;
import java.util.Map;
import javax.inject.Named;

public class ProjectActivityHRObserver {

  @SuppressWarnings("unchecked")
  public void onSaveTimesheet(
      @Observes @Named(RequestEvent.SAVE) @EntityType(Timesheet.class) PreRequest event) {
    Map<String, Object> timesheetDataMap = event.getRequest().getData();
    List<Map<String, Object>> timsheetLineMapList =
        (List<Map<String, Object>>) timesheetDataMap.get("timesheetLineList");
    if (ObjectUtils.notEmpty(timsheetLineMapList)) {
      for (Map<String, Object> timesheetLineDataMap : timsheetLineMapList) {
        if (timesheetLineDataMap != null) {
          Beans.get(ProjectActivityHRService.class)
              .createTimesheetLineProjectActivity(timesheetLineDataMap);
        }
      }
    }
  }

  public void onSaveTimesheetLine(
      @Observes @Named(RequestEvent.SAVE) @EntityType(TimesheetLine.class) PreRequest event) {
    Map<String, Object> dataMap = event.getRequest().getData();
    if (dataMap != null) {
      Beans.get(ProjectActivityHRService.class).createTimesheetLineProjectActivity(dataMap);
    }
  }
}
