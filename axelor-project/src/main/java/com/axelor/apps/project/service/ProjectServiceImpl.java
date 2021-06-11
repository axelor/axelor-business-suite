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
package com.axelor.apps.project.service;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectStatus;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.ProjectTaskCategory;
import com.axelor.apps.project.db.ProjectTemplate;
import com.axelor.apps.project.db.ResourceBooking;
import com.axelor.apps.project.db.TaskTemplate;
import com.axelor.apps.project.db.Wiki;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectStatusRepository;
import com.axelor.apps.project.db.repo.WikiRepository;
import com.axelor.apps.project.service.app.AppProjectService;
import com.axelor.apps.project.translation.ITranslation;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProjectServiceImpl implements ProjectService {

  public static final int MAX_LEVEL_OF_PROJECT = 10;

  protected ProjectRepository projectRepository;
  protected ProjectStatusRepository projectStatusRepository;
  protected AppProjectService appProjectService;

  @Inject
  public ProjectServiceImpl(
      ProjectRepository projectRepository,
      ProjectStatusRepository projectStatusRepository,
      AppProjectService appProjectService) {
    this.projectRepository = projectRepository;
    this.projectStatusRepository = projectStatusRepository;
    this.appProjectService = appProjectService;
  }

  @Inject WikiRepository wikiRepo;
  @Inject ProjectTaskService projectTaskService;
  @Inject ResourceBookingService resourceBookingService;

  @Override
  public Project generateProject(
      Project parentProject,
      String fullName,
      User assignedTo,
      Company company,
      Partner clientPartner) {
    Project project;
    project = projectRepository.findByName(fullName);
    if (project != null) {
      return project;
    }
    project = new Project();
    project.setParentProject(parentProject);
    if (parentProject != null) {
      parentProject.addChildProjectListItem(project);
    }
    if (Strings.isNullOrEmpty(fullName)) {
      fullName = "project";
    }
    project.setName(fullName);
    project.setFullName(project.getName());
    project.setClientPartner(clientPartner);
    project.setAssignedTo(assignedTo);
    project.setProjectStatus(getDefaultProjectStatus());
    project.setProjectTaskStatusSet(
        new HashSet<>(appProjectService.getAppProject().getDefaultTaskStatusSet()));
    project.setProjectTaskPrioritySet(
        new HashSet<>(appProjectService.getAppProject().getDefaultPrioritySet()));
    return project;
  }

  @Override
  @Transactional
  public Project generateProject(Partner partner) {
    Preconditions.checkNotNull(partner);
    User user = AuthUtils.getUser();
    Project project =
        Beans.get(ProjectService.class)
            .generateProject(
                null, getUniqueProjectName(partner), user, user.getActiveCompany(), partner);
    return projectRepository.save(project);
  }

  private String getUniqueProjectName(Partner partner) {
    String baseName = String.format(I18n.get("%s project"), partner.getName());
    long count =
        projectRepository.all().filter(String.format("self.name LIKE '%s%%'", baseName)).count();

    if (count == 0) {
      return baseName;
    }

    String name;

    do {
      name = String.format("%s %d", baseName, ++count);
    } while (projectRepository.findByName(name) != null);

    return name;
  }

  @Override
  @Transactional
  public Project createProjectFromTemplate(
      ProjectTemplate projectTemplate, String projectCode, Partner clientPartner)
      throws AxelorException {
    if (projectRepository.findByCode(projectCode) != null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY, ITranslation.PROJECT_CODE_ERROR);
    }

    Project project = generateProject(projectTemplate, projectCode, clientPartner);
    setWikiItems(project, projectTemplate);
    projectRepository.save(project);

    Set<TaskTemplate> taskTemplateSet = projectTemplate.getTaskTemplateSet();
    if (ObjectUtils.isEmpty(taskTemplateSet)) {
      return project;
    }
    List<TaskTemplate> taskTemplateList = new ArrayList<>(taskTemplateSet);

    Collections.sort(
        taskTemplateList,
        (taskTemplatet1, taskTemplate2) ->
            taskTemplatet1.getParentTaskTemplate() == null || taskTemplate2 == null
                ? 1
                : taskTemplatet1.getParentTaskTemplate().equals(taskTemplate2) ? -1 : 1);

    taskTemplateList.forEach(taskTemplate -> createTask(taskTemplate, project, taskTemplateSet));
    return project;
  }

  @Override
  public Map<String, Object> getTaskView(
      Project project, String title, String domain, Map<String, Object> context) {
    ActionViewBuilder builder =
        ActionView.define(I18n.get(title))
            .model(ProjectTask.class.getName())
            .add("grid", "project-task-grid")
            .add("form", "project-task-form")
            .domain(domain)
            .param("details-view", "true");

    if (project.getIsShowKanbanPerSection() && project.getIsShowCalendarPerSection()) {
      builder.add("kanban", "task-per-section-kanban");
      builder.add("calendar", "project-task-per-section-calendar");
    } else {
      builder.add("kanban", "project-task-kanban");
      builder.add("calendar", "project-task-per-status-calendar");
      builder.param("kanban-hide-columns", getStatusColumnsTobeExcluded(project));
    }

    if (ObjectUtils.notEmpty(context)) {
      context.forEach(builder::context);
    }
    return builder.map();
  }

  @Override
  public Map<String, Object> createProjectFromTemplateView(ProjectTemplate projectTemplate)
      throws AxelorException {
    return ActionView.define(I18n.get("Create project from this template"))
        .model(Wizard.class.getName())
        .add("form", "project-template-wizard-form")
        .param("popup", "reload")
        .param("show-toolbar", "false")
        .param("show-confirm", "false")
        .param("width", "large")
        .param("popup-save", "false")
        .context("_projectTemplate", projectTemplate)
        .context("_businessProject", projectTemplate.getIsBusinessProject())
        .map();
  }

  protected void setWikiItems(Project project, ProjectTemplate projectTemplate) {
    List<Wiki> wikiList = projectTemplate.getWikiList();
    if (ObjectUtils.notEmpty(wikiList)) {
      for (Wiki wiki : wikiList) {
        wiki = wikiRepo.copy(wiki, false);
        wiki.setProjectTemplate(null);
        project.addWikiListItem(wiki);
      }
    }
  }

  @Override
  public Project generateProject(
      ProjectTemplate projectTemplate, String projectCode, Partner clientPartner) {
    Project project = new Project();
    project.setName(projectTemplate.getName());
    project.setCode(projectCode);
    project.setClientPartner(clientPartner);
    project.setDescription(projectTemplate.getDescription());
    project.setTeam(projectTemplate.getTeam());
    project.setAssignedTo(projectTemplate.getAssignedTo());
    project.setProjectTaskCategorySet(new HashSet<>(projectTemplate.getProjectTaskCategorySet()));
    project.setSynchronize(projectTemplate.getSynchronize());
    project.setMembersUserSet(new HashSet<>(projectTemplate.getMembersUserSet()));
    project.setImputable(projectTemplate.getImputable());
    project.setProductSet(new HashSet<>(projectTemplate.getProductSet()));
    project.setExcludePlanning(projectTemplate.getExcludePlanning());
    project.setProjectStatus(getDefaultProjectStatus());
    project.setProjectTaskStatusSet(
        new HashSet<>(appProjectService.getAppProject().getDefaultTaskStatusSet()));
    project.setProjectTaskPrioritySet(
        new HashSet<>(appProjectService.getAppProject().getDefaultPrioritySet()));
    if (clientPartner != null && ObjectUtils.notEmpty(clientPartner.getContactPartnerSet())) {
      project.setContactPartner(clientPartner.getContactPartnerSet().iterator().next());
    }
    return project;
  }

  @Override
  public Map<String, Object> getPerStatusKanban(Project project, Map<String, Object> context) {
    ActionViewBuilder builder =
        ActionView.define(I18n.get("All tasks"))
            .model(ProjectTask.class.getName())
            .add("kanban", "project-task-kanban")
            .add("grid", "project-task-grid")
            .add("form", "project-task-form")
            .domain("self.typeSelect = :_typeSelect AND self.project = :_project")
            .param("kanban-hide-columns", getStatusColumnsTobeExcluded(project));

    if (ObjectUtils.notEmpty(context)) {
      context.forEach(builder::context);
    }
    return builder.map();
  }

  public ProjectTask createTask(
      TaskTemplate taskTemplate, Project project, Set<TaskTemplate> taskTemplateSet) {

    if (!ObjectUtils.isEmpty(project.getProjectTaskList())) {
      for (ProjectTask projectTask : project.getProjectTaskList()) {
        if (projectTask.getName().equals(taskTemplate.getName())) {
          return projectTask;
        }
      }
    }
    ProjectTask task =
        projectTaskService.create(taskTemplate.getName(), project, taskTemplate.getAssignedTo());
    task.setDescription(taskTemplate.getDescription());
    ProjectTaskCategory projectTaskCategory = taskTemplate.getProjectTaskCategory();
    if (projectTaskCategory != null) {
      task.setProjectTaskCategory(projectTaskCategory);
      project.addProjectTaskCategorySetItem(projectTaskCategory);
    }

    TaskTemplate parentTaskTemplate = taskTemplate.getParentTaskTemplate();

    if (parentTaskTemplate != null && taskTemplateSet.contains(parentTaskTemplate)) {
      task.setParentTask(this.createTask(parentTaskTemplate, project, taskTemplateSet));
      return task;
    }
    return task;
  }

  protected String getStatusColumnsTobeExcluded(Project project) {
    return projectStatusRepository
        .all()
        .filter("self not in :allowedProjectTaskStatus")
        .bind("allowedProjectTaskStatus", project.getProjectTaskStatusSet())
        .fetchStream()
        .map(ProjectStatus::getId)
        .map(String::valueOf)
        .collect(Collectors.joining(","));
  }

  @Override
  public String getTimeZone(Project project) {
    return null;
  }

  @Override
  public ProjectStatus getDefaultProjectStatus() {
    return projectStatusRepository
        .all()
        .filter("self.relatedToSelect = ?1", ProjectStatusRepository.PROJECT_STATUS_PROJECT)
        .order("sequence")
        .fetchOne();
  }

  public boolean checkIfResourceBooked(Project project) {

    List<ResourceBooking> resourceBookingList = project.getResourceBookingList();
    if (resourceBookingList != null) {
      for (ResourceBooking resourceBooking : resourceBookingList) {
        if (resourceBooking.getFromDate() != null
            && resourceBooking.getToDate() != null
            && (resourceBookingService.checkIfResourceBooked(resourceBooking)
                || checkIfResourceBookedInList(resourceBookingList, resourceBooking))) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean checkIfResourceBookedInList(
      List<ResourceBooking> resourceBookingList, ResourceBooking resourceBooking) {

    return resourceBookingList.stream()
        .anyMatch(
            x ->
                !x.equals(resourceBooking)
                    && x.getResource().equals(resourceBooking.getResource())
                    && x.getFromDate() != null
                    && x.getToDate() != null
                    && ((resourceBooking.getFromDate().compareTo(x.getFromDate()) >= 0
                            && resourceBooking.getFromDate().compareTo(x.getToDate()) <= 0)
                        || (resourceBooking.getToDate().compareTo(x.getFromDate()) >= 0)
                            && resourceBooking.getToDate().compareTo(x.getToDate()) <= 0));
  }
}
