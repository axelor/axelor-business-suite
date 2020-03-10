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
package com.axelor.apps.project.service;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectTemplate;
import com.axelor.apps.project.db.TaskTemplate;
import com.axelor.apps.project.db.Wiki;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.WikiRepository;
import com.axelor.apps.project.exception.IExceptionMessage;
import com.axelor.apps.project.translation.ITranslation;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.team.db.TeamTask;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.persistence.TypedQuery;

public class ProjectServiceImpl implements ProjectService {

  public static final int MAX_LEVEL_OF_PROJECT = 10;

  private ProjectRepository projectRepository;

  @Inject
  public ProjectServiceImpl(ProjectRepository projectRepository) {
    this.projectRepository = projectRepository;
  }

  @Inject WikiRepository wikiRepo;
  @Inject TeamTaskProjectService teamTaskProjectService;

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
    project.setStatusSelect(ProjectRepository.STATE_NEW);
    project.setParentProject(parentProject);
    if (parentProject != null) {
      parentProject.addChildProjectListItem(project);
      project.setProjectTypeSelect(ProjectRepository.TYPE_PHASE);
    } else {
      project.setProjectTypeSelect(ProjectRepository.TYPE_PROJECT);
    }
    if (Strings.isNullOrEmpty(fullName)) {
      fullName = "project";
    }
    project.setName(fullName);
    project.setFullName(project.getName());
    project.setCompany(company);
    project.setClientPartner(clientPartner);
    project.setAssignedTo(assignedTo);
    project.setProgress(BigDecimal.ZERO);
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

  protected String getUniqueProjectName(Partner partner) {
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
  public Partner getClientPartnerFromProject(Project project) throws AxelorException {
    return this.getClientPartnerFromProject(project, 0);
  }

  protected Partner getClientPartnerFromProject(Project project, int counter)
      throws AxelorException {
    if (project.getParentProject() == null) {
      // it is a root project, can get the client partner
      if (project.getClientPartner() == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.PROJECT_CUSTOMER_PARTNER));
      } else {
        return project.getClientPartner();
      }
    } else {
      if (counter > MAX_LEVEL_OF_PROJECT) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.PROJECT_DEEP_LIMIT_REACH));
      } else {
        return this.getClientPartnerFromProject(project.getParentProject(), counter + 1);
      }
    }
  }

  @Override
  public BigDecimal computeDurationFromChildren(Long projectId) {
    String query =
        "SELECT SUM(pt.duration)" + " FROM Project as pt" + " WHERE pt.project.id = :projectId";

    TypedQuery<BigDecimal> q = JPA.em().createQuery(query, BigDecimal.class);
    q.setParameter("projectId", projectId);
    return q.getSingleResult();
  }

  @Override
  @Transactional
  public Project createProjectFromTemplate(
      ProjectTemplate projectTemplate, String projectCode, Partner clientPartner)
      throws AxelorException {

    Project project = new Project();
    project.setName(projectTemplate.getName());

    if (projectRepository.all().filter("self.code = ?", projectCode).count() > 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY, ITranslation.PROJECT_CODE_ERROR);
    } else {

      project.setCode(projectCode);
      project.setClientPartner(clientPartner);
      if (clientPartner != null
          && clientPartner.getContactPartnerSet() != null
          && !clientPartner.getContactPartnerSet().isEmpty()) {
        project.setContactPartner(clientPartner.getContactPartnerSet().iterator().next());
      }
      project.setDescription(projectTemplate.getDescription());
      project.setTeam(projectTemplate.getTeam());
      project.setProjectFolderSet(new HashSet<>(projectTemplate.getProjectFolderSet()));
      project.setAssignedTo(projectTemplate.getAssignedTo());
      project.setTeamTaskCategorySet(new HashSet<>(projectTemplate.getTeamTaskCategorySet()));
      project.setSynchronize(projectTemplate.getSynchronize());
      project.setMembersUserSet(new HashSet<>(projectTemplate.getMembersUserSet()));
      project.setImputable(projectTemplate.getImputable());
      project.setCompany(projectTemplate.getCompany());
      project.setProductSet(new HashSet<>(projectTemplate.getProductSet()));
      project.setExcludePlanning(projectTemplate.getExcludePlanning());
      project.setProjectTypeSelect(ProjectRepository.TYPE_PROJECT);

      List<Wiki> wikiList = projectTemplate.getWikiList();

      if (wikiList != null && !wikiList.isEmpty()) {

        for (Wiki wiki : wikiList) {
          wiki = wikiRepo.copy(wiki, false);
          wiki.setProjectTemplate(null);
          project.addWikiListItem(wiki);
        }
      }

      projectRepository.save(project);

      Set<TaskTemplate> taskTemplateSet = projectTemplate.getTaskTemplateSet();

      if (taskTemplateSet != null) {
        Iterator<TaskTemplate> taskTemplateItr = taskTemplateSet.iterator();

        while (taskTemplateItr.hasNext()) {
          createTask(taskTemplateItr.next(), project);
        }
      }

      return project;
    }
  }

  public TeamTask createTask(TaskTemplate taskTemplate, Project project) {

    TeamTask task =
        teamTaskProjectService.create(
            taskTemplate.getName(), project, taskTemplate.getAssignedTo());
    task.setDescription(taskTemplate.getDescription());

    return task;
  }
}
