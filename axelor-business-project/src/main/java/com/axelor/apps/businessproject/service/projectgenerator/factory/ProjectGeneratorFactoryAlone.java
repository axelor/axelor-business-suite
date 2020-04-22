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
package com.axelor.apps.businessproject.service.projectgenerator.factory;

import com.axelor.apps.businessproject.exception.IExceptionMessage;
import com.axelor.apps.businessproject.service.ProjectBusinessService;
import com.axelor.apps.businessproject.service.projectgenerator.ProjectGeneratorFactory;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;

public class ProjectGeneratorFactoryAlone implements ProjectGeneratorFactory {

  private ProjectBusinessService projectBusinessService;
  private ProjectRepository projectRepository;

  @Inject
  public ProjectGeneratorFactoryAlone(
      ProjectBusinessService projectBusinessService, ProjectRepository projectRepository) {
    this.projectBusinessService = projectBusinessService;
    this.projectRepository = projectRepository;
  }

  @Override
  @Transactional
  public Project create(SaleOrder saleOrder) {
    Project project = projectBusinessService.generateProject(saleOrder);
    project.setIsProject(false);
    project.setIsBusinessProject(true);
    return projectRepository.save(project);
  }

  @Override
  public ActionViewBuilder fill(
      Project project, SaleOrder saleOrder, LocalDateTime localDateTime, User assignedTaskTo)
      throws AxelorException {
    throw new AxelorException(
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        I18n.get(IExceptionMessage.FACTORY_FILL_WITH_PROJECT_ALONE));
  }
}
