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
package com.axelor.apps.project.observer;

import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.Topic;
import com.axelor.apps.project.db.Wiki;
import com.axelor.apps.project.service.ProjectActivityService;
import com.axelor.event.Observes;
import com.axelor.events.PreRequest;
import com.axelor.events.RequestEvent;
import com.axelor.events.qualifiers.EntityType;
import com.axelor.inject.Beans;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Named;

public class ProjectActivityObserver {

  @SuppressWarnings("unchecked")
  public void onSaveProject(
      @Observes @Named(RequestEvent.SAVE) @EntityType(Project.class) PreRequest event) {
    Map<String, Object> dataMap = event.getRequest().getData();
    if (dataMap != null) {
      Beans.get(ProjectActivityService.class).createProjectProjectActivity(dataMap);
    } else {
      List<Object> records = event.getRequest().getRecords();
      Beans.get(ProjectActivityService.class)
          .createProjectProjectActivity((HashMap<String, Object>) records.get(0));
    }
  }

  @SuppressWarnings("unchecked")
  public void onSaveTask(
      @Observes @Named(RequestEvent.SAVE) @EntityType(ProjectTask.class) PreRequest event) {
    Map<String, Object> dataMap = event.getRequest().getData();
    if (dataMap != null) {
      Beans.get(ProjectActivityService.class).createTaskProjectActivity(dataMap);
    } else {
      List<Object> records = event.getRequest().getRecords();
      Beans.get(ProjectActivityService.class)
          .createTaskProjectActivity((HashMap<String, Object>) records.get(0));
    }
  }

  public void onSaveWiki(
      @Observes @Named(RequestEvent.SAVE) @EntityType(Wiki.class) PreRequest event) {
    Map<String, Object> dataMap = event.getRequest().getData();
    if (dataMap != null) {
      Beans.get(ProjectActivityService.class).createWikiProjectActivity(dataMap);
    }
  }

  public void onSaveTopic(
      @Observes @Named(RequestEvent.SAVE) @EntityType(Topic.class) PreRequest event) {
    Map<String, Object> dataMap = event.getRequest().getData();
    if (dataMap != null) {
      Beans.get(ProjectActivityService.class).createTopicProjectActivity(dataMap);
    }
  }
}
