package com.axelor.apps.businessproject.service.projectgenerator.factory;

import com.axelor.apps.businessproject.service.ProductTaskTemplateService;
import com.axelor.apps.businessproject.service.ProjectBusinessService;
import com.axelor.apps.businessproject.service.projectgenerator.ProjectGeneratorFactory;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.service.TeamTaskService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.tool.StringTool;
import com.axelor.exception.AxelorException;
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
  private TeamTaskService teamTaskService;
  private TeamTaskRepository teamTaskRepository;
  private ProductTaskTemplateService productTaskTemplateService;

  @Inject
  public ProjectGeneratorFactoryTaskTemplate(
      ProjectBusinessService projectBusinessService,
      ProjectRepository projectRepository,
      TeamTaskService teamTaskService,
      TeamTaskRepository teamTaskRepository,
      ProductTaskTemplateService productTaskTemplateService) {
    this.projectBusinessService = projectBusinessService;
    this.projectRepository = projectRepository;
    this.teamTaskService = teamTaskService;
    this.teamTaskRepository = teamTaskRepository;
    this.productTaskTemplateService = productTaskTemplateService;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public Project create(SaleOrder saleOrder) {
    Project project = projectBusinessService.generateProject(saleOrder);
    project.setIsProject(true);
    project.setIsBusinessProject(true);
    return projectRepository.save(project);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public ActionViewBuilder fill(Project project, SaleOrder saleOrder, LocalDateTime startDate) {
    List<TeamTask> tasks = new ArrayList<>();

    TeamTask root =
        teamTaskService.create(saleOrder.getFullName(), project, project.getAssignedTo());
    root.setTaskDate(startDate.toLocalDate());
    tasks.add(teamTaskRepository.save(root));

    for (SaleOrderLine orderLine : saleOrder.getSaleOrderLineList()) {
      if (!CollectionUtils.isEmpty(orderLine.getProduct().getTaskTemplateList())) {
        List<TeamTask> convertedTasks =
            productTaskTemplateService.convert(
                orderLine
                    .getProduct()
                    .getTaskTemplateList()
                    .stream()
                    .filter(template -> Objects.isNull(template.getParentTaskTemplate()))
                    .collect(Collectors.toList()),
                project,
                root,
                startDate,
                orderLine.getQty().intValue());
        tasks.addAll(convertedTasks);
      }
    }
    return ActionView.define(String.format("Task%s generated", (tasks.size() > 1 ? "s" : "")))
        .model(TeamTask.class.getName())
        .add("grid", "team-task-grid")
        .add("form", "team-task-form")
        .domain(String.format("self.id in (%s)", StringTool.getIdListString(tasks)));
  }
}
