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
import com.axelor.apps.businessproject.service.ProductTaskTemplateService;
import com.axelor.apps.businessproject.service.ProjectBusinessService;
import com.axelor.apps.businessproject.service.TeamTaskBusinessProjectService;
import com.axelor.apps.businessproject.service.projectgenerator.ProjectGeneratorFactory;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
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
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class ProjectGeneratorFactoryTaskTemplate implements ProjectGeneratorFactory {

  private ProjectBusinessService projectBusinessService;
  private ProjectRepository projectRepository;
  private TeamTaskBusinessProjectService teamTaskService;
  private TeamTaskRepository teamTaskRepository;
  private ProductTaskTemplateService productTaskTemplateService;

  @Inject
  public ProjectGeneratorFactoryTaskTemplate(
      ProjectBusinessService projectBusinessService,
      ProjectRepository projectRepository,
      TeamTaskBusinessProjectService teamTaskService,
      TeamTaskRepository teamTaskRepository,
      ProductTaskTemplateService productTaskTemplateService) {
    this.projectBusinessService = projectBusinessService;
    this.projectRepository = projectRepository;
    this.teamTaskService = teamTaskService;
    this.teamTaskRepository = teamTaskRepository;
    this.productTaskTemplateService = productTaskTemplateService;
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
    TeamTask root;
    User userToAssign = assignedTaskTo != null ? assignedTaskTo : project.getAssignedTo();
    root =
        teamTaskRepository
            .all()
            .filter(
                "self.project = :project AND self.assignedTo = :userToAssign AND self.name = :fullName")
            .bind("project", project)
            .bind("userToAssign", userToAssign)
            .bind("fullName", saleOrder.getFullName())
            .fetchOne();

    projectRepository.save(project);

    for (SaleOrderLine orderLine : saleOrder.getSaleOrderLineList()) {
      Product product = orderLine.getProduct();
      if (product != null
          && !((ProductRepository.PROCUREMENT_METHOD_PRODUCE.equals(
                      product.getProcurementMethodSelect())
                  || orderLine.getSaleSupplySelect() == SaleOrderLineRepository.SALE_SUPPLY_PRODUCE)
              && ProductRepository.PRODUCT_TYPE_SERVICE.equals(product.getProductTypeSelect()))) {
        continue;
      }
      boolean isTaskGenerated =
          teamTaskRepository
                  .all()
                  .filter("self.saleOrderLine = ? AND self.project = ?", orderLine, project)
                  .fetch()
                  .size()
              > 0;
      if (root == null) {
        root = teamTaskService.create(saleOrder.getFullName(), project, userToAssign);
        root.setTaskDate(startDate.toLocalDate());
        tasks.add(teamTaskRepository.save(root));
      }
      if (product != null && !isTaskGenerated) {
        if (!CollectionUtils.isEmpty(product.getTaskTemplateSet())) {
          List<TeamTask> convertedTasks =
              productTaskTemplateService.convert(
                  product
                      .getTaskTemplateSet()
                      .stream()
                      .filter(template -> Objects.isNull(template.getParentTaskTemplate()))
                      .collect(Collectors.toList()),
                  project,
                  root,
                  startDate,
                  orderLine.getQty(),
                  orderLine);
          convertedTasks.stream().forEach(task -> task.setSaleOrderLine(orderLine));
          tasks.addAll(convertedTasks);
        } else {
          TeamTask childTask =
              teamTaskService.create(orderLine.getFullName(), project, userToAssign);
          this.updateTask(root, childTask, orderLine);

          tasks.add(teamTaskRepository.save(childTask));
        }
      }
    }
    if (root == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.SALE_ORDER_GENERATE_FILL_PROJECT_ERROR_2));
    }
    return ActionView.define("Tasks")
        .model(TeamTask.class.getName())
        .add("grid", "team-task-grid")
        .add("form", "team-task-form")
        .domain("self.parentTask = " + root.getId());
  }

  private void updateTask(TeamTask root, TeamTask childTask, SaleOrderLine orderLine) {
    childTask.setParentTask(root);
    childTask.setQuantity(orderLine.getQty());
    Product product = orderLine.getProduct();
    childTask.setProduct(product);
    childTask.setExTaxTotal(orderLine.getExTaxTotal());
    childTask.setUnitPrice(product != null ? product.getSalePrice() : null);
    childTask.setUnit(product != null ? product.getUnit() : null);
    childTask.setSaleOrderLine(orderLine);
    if (orderLine.getSaleOrder().getToInvoiceViaTask()) {
      childTask.setToInvoice(true);
      childTask.setInvoicingType(TeamTaskRepository.INVOICING_TYPE_PACKAGE);
    }
  }
}
