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
package com.axelor.apps.project.web;

import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectTaskRepository;
import com.axelor.apps.project.exception.IExceptionMessage;
import com.axelor.apps.project.service.ProjectService;
import com.axelor.apps.project.service.app.AppProjectService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class ProjectController {

  public void importMembers(ActionRequest request, ActionResponse response) {
    Project project = request.getContext().asType(Project.class);
    if (project.getTeam() != null) {
      project.getTeam().getMembers().forEach(project::addMembersUserSetItem);
      response.setValue("membersUserSet", project.getMembersUserSet());
    }
  }

  public void getMyOpenTasks(ActionRequest request, ActionResponse response) {
    Project project =
        Beans.get(ProjectRepository.class).find(request.getContext().asType(Project.class).getId());
    Map<String, Object> context = getTaskContext(project);
    Map<String, Object> view =
        Beans.get(ProjectService.class)
            .getTaskView(
                project,
                "My open tasks",
                "self.assignedTo = :__user__ AND self.status.isCompleted = false AND self.typeSelect = :_typeSelect AND self.project = :_project",
                context);
    response.setView(view);
  }

  public void getMyTasks(ActionRequest request, ActionResponse response) {
    Project project =
        Beans.get(ProjectRepository.class).find(request.getContext().asType(Project.class).getId());
    Map<String, Object> context = getTaskContext(project);
    Map<String, Object> view =
        Beans.get(ProjectService.class)
            .getTaskView(
                project,
                "My tasks",
                "self.createdBy = :__user__ AND self.typeSelect = :_typeSelect AND self.project = :_project",
                context);
    response.setView(view);
  }

  public void getAllOpenTasks(ActionRequest request, ActionResponse response) {
    Project project =
        Beans.get(ProjectRepository.class).find(request.getContext().asType(Project.class).getId());
    Map<String, Object> context = getTaskContext(project);
    Map<String, Object> view =
        Beans.get(ProjectService.class)
            .getTaskView(
                project,
                "All open tasks",
                "self.status.isCompleted = false AND self.typeSelect = :_typeSelect AND self.project = :_project",
                context);
    response.setView(view);
  }

  public void getAllTasks(ActionRequest request, ActionResponse response) {
    Project project =
        Beans.get(ProjectRepository.class).find(request.getContext().asType(Project.class).getId());
    Map<String, Object> context = getTaskContext(project);
    Map<String, Object> view =
        Beans.get(ProjectService.class)
            .getTaskView(
                project,
                "All tasks",
                "self.typeSelect = :_typeSelect AND self.project = :_project",
                context);
    response.setView(view);
  }

  public void perStatusKanban(ActionRequest request, ActionResponse response) {
    Project project =
        Beans.get(ProjectRepository.class).find(request.getContext().asType(Project.class).getId());
    Map<String, Object> context = getTaskContext(project);
    Map<String, Object> view = Beans.get(ProjectService.class).getPerStatusKanban(project, context);
    response.setView(view);
  }

  protected Map<String, Object> getTaskContext(Project project) {
    Map<String, Object> context = new HashMap<>();
    context.put("_project", project);
    context.put("_typeSelect", ProjectTaskRepository.TYPE_TASK);
    return context;
  }

  public void checkIfResourceBooked(ActionRequest request, ActionResponse response) {
    if (Beans.get(AppProjectService.class).getAppProject().getCheckResourceAvailibility()) {
      Project project = request.getContext().asType(Project.class);
      if (Beans.get(ProjectService.class).checkIfResourceBooked(project)) {
        response.setError(I18n.get(IExceptionMessage.RESOURCE_ALREADY_BOOKED_ERROR_MSG));
      }
    }
  }
}
