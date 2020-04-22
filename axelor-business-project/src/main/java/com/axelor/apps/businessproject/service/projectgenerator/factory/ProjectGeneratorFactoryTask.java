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

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.businessproject.exception.IExceptionMessage;
import com.axelor.apps.businessproject.service.ProjectBusinessService;
import com.axelor.apps.businessproject.service.TeamTaskBusinessProjectService;
import com.axelor.apps.businessproject.service.projectgenerator.ProjectGeneratorFactory;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.tool.StringTool;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProjectGeneratorFactoryTask implements ProjectGeneratorFactory {

  private ProjectBusinessService projectBusinessService;
  private ProjectRepository projectRepository;
  private TeamTaskBusinessProjectService teamTaskBusinessProjectService;
  private TeamTaskRepository teamTaskRepository;

  @Inject
  public ProjectGeneratorFactoryTask(
      ProjectBusinessService projectBusinessService,
      ProjectRepository projectRepository,
      TeamTaskBusinessProjectService teamTaskBusinessProjectService,
      TeamTaskRepository teamTaskRepository) {
    this.projectBusinessService = projectBusinessService;
    this.projectRepository = projectRepository;
    this.teamTaskBusinessProjectService = teamTaskBusinessProjectService;
    this.teamTaskRepository = teamTaskRepository;
  }

  @Override
  public Project create(SaleOrder saleOrder) {
    Project project = projectBusinessService.generateProject(saleOrder);
    project.setIsProject(true);
    project.setIsBusinessProject(true);
    return project;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class, AxelorException.class})
  public ActionViewBuilder fill(
      Project project, SaleOrder saleOrder, LocalDateTime startDate, User assignedTaskTo)
      throws AxelorException {
    List<TeamTask> tasks = new ArrayList<>();
    projectRepository.save(project);
    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
      Product product = saleOrderLine.getProduct();
      boolean isTaskGenerated =
          teamTaskRepository
                  .all()
                  .filter("self.saleOrderLine = ? AND self.project = ?", saleOrderLine, project)
                  .fetch()
                  .size()
              > 0;
      if (product != null
          && ProductRepository.PRODUCT_TYPE_SERVICE.equals(product.getProductTypeSelect())
          && saleOrderLine.getSaleSupplySelect() == SaleOrderLineRepository.SALE_SUPPLY_PRODUCE
          && !(isTaskGenerated)) {

        TeamTask task =
            teamTaskBusinessProjectService.create(
                saleOrderLine,
                project,
                assignedTaskTo != null ? assignedTaskTo : project.getAssignedTo());

        if (saleOrder.getToInvoiceViaTask()) {
          task.setInvoicingType(TeamTaskRepository.INVOICING_TYPE_PACKAGE);
        }

        task.setTaskDate(startDate.toLocalDate());
        task.setUnitPrice(product.getSalePrice());
        task.setExTaxTotal(saleOrderLine.getExTaxTotal());
        if (project.getIsInvoicingTimesheet()) {
          task.setToInvoice(true);
        } else {
          task.setToInvoice(false);
        }
        teamTaskRepository.save(task);
        tasks.add(task);
      }
    }
    if (tasks == null || tasks.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.SALE_ORDER_GENERATE_FILL_PROJECT_ERROR_1));
    }

    return ActionView.define(String.format("Task%s generated", (tasks.size() > 1 ? "s" : "")))
        .model(TeamTask.class.getName())
        .add("grid", "team-task-grid")
        .add("form", "team-task-form")
        .domain(String.format("self.id in (%s)", StringTool.getIdListString(tasks)));
  }
}
