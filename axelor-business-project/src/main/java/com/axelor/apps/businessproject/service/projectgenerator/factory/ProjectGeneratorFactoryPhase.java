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
import com.axelor.apps.businessproject.service.ProductTaskTemplateService;
import com.axelor.apps.businessproject.service.ProjectBusinessService;
import com.axelor.apps.businessproject.service.projectgenerator.ProjectGeneratorFactory;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.tool.StringTool;
import com.axelor.auth.db.User;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class ProjectGeneratorFactoryPhase implements ProjectGeneratorFactory {

  private ProjectBusinessService projectBusinessService;
  private ProjectRepository projectRepository;
  private SaleOrderLineRepository saleOrderLineRepository;
  private ProductTaskTemplateService productTaskTemplateService;

  @Inject
  public ProjectGeneratorFactoryPhase(
      ProjectBusinessService projectBusinessService,
      ProjectRepository projectRepository,
      SaleOrderLineRepository saleOrderLineRepository,
      ProductTaskTemplateService productTaskTemplateService) {
    this.projectBusinessService = projectBusinessService;
    this.projectRepository = projectRepository;
    this.saleOrderLineRepository = saleOrderLineRepository;
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
  @Transactional
  public ActionViewBuilder fill(
      Project project, SaleOrder saleOrder, LocalDateTime startDate, User assignedTaskTo) {
    List<Project> projects = new ArrayList<>();
    projectRepository.save(project);
    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
      Product product = saleOrderLine.getProduct();
      if (product != null
          && ProductRepository.PRODUCT_TYPE_SERVICE.equals(product.getProductTypeSelect())
          && saleOrderLine.getSaleSupplySelect() == SaleOrderLineRepository.SALE_SUPPLY_PRODUCE) {
        Project phase = projectBusinessService.generatePhaseProject(saleOrderLine, project);
        phase.setFromDate(startDate);
        saleOrderLineRepository.save(saleOrderLine);
        projects.add(phase);

        if (!CollectionUtils.isEmpty(product.getTaskTemplateSet())) {
          productTaskTemplateService.convert(
              product
                  .getTaskTemplateSet()
                  .stream()
                  .filter(template -> Objects.isNull(template.getParentTaskTemplate()))
                  .collect(Collectors.toList()),
              phase,
              null,
              startDate,
              saleOrderLine.getQty(),
              saleOrderLine);
        }
      }
    }
    return ActionView.define(String.format("Project%s generated", (projects.size() > 1 ? "s" : "")))
        .model(Project.class.getName())
        .add("grid", "project-grid")
        .add("form", "project-form")
        .domain(String.format("self.id in (%s)", StringTool.getIdListString(projects)));
  }
}
