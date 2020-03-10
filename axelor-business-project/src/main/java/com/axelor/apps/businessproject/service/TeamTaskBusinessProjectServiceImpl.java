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
package com.axelor.apps.businessproject.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.AppBusinessProject;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.TaskTemplate;
import com.axelor.apps.project.db.TeamTaskCategory;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.service.TeamTaskProjectServiceImpl;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TeamTaskBusinessProjectServiceImpl extends TeamTaskProjectServiceImpl
    implements TeamTaskBusinessProjectService {

  private PriceListLineRepository priceListLineRepository;

  private PriceListService priceListService;

  @Inject
  public TeamTaskBusinessProjectServiceImpl(
      TeamTaskRepository teamTaskRepo,
      PriceListLineRepository priceListLineRepository,
      PriceListService priceListService) {
    super(teamTaskRepo);
    this.priceListLineRepository = priceListLineRepository;
    this.priceListService = priceListService;
  }

  @Override
  public TeamTask create(SaleOrderLine saleOrderLine, Project project, User assignedTo) {
    TeamTask task = create(saleOrderLine.getFullName() + "_task", project, assignedTo);
    task.setProduct(saleOrderLine.getProduct());
    task.setUnit(saleOrderLine.getUnit());
    task.setCurrency(project.getClientPartner().getCurrency());
    if (project.getPriceList() != null) {
      PriceListLine line =
          priceListLineRepository.findByPriceListAndProduct(
              project.getPriceList(), saleOrderLine.getProduct());
      if (line != null) {
        task.setUnitPrice(line.getAmount());
      }
    }
    if (task.getUnitPrice() == null) {
      task.setUnitPrice(saleOrderLine.getProduct().getSalePrice());
    }
    task.setQuantity(saleOrderLine.getQty());
    task.setSaleOrderLine(saleOrderLine);
    task.setToInvoice(
        saleOrderLine.getSaleOrder() != null
            ? saleOrderLine.getSaleOrder().getToInvoiceViaTask()
            : false);
    return task;
  }

  @Override
  public TeamTask create(
      TaskTemplate template, Project project, LocalDateTime date, BigDecimal qty) {
    TeamTask task = create(template.getName(), project, template.getAssignedTo());

    task.setTaskDate(date.toLocalDate());
    task.setTaskEndDate(date.plusHours(template.getDuration().longValue()).toLocalDate());

    BigDecimal plannedHrs = template.getTotalPlannedHrs();
    if (template.getIsUniqueTaskForMultipleQuantity() && qty.compareTo(BigDecimal.ONE) > 0) {
      plannedHrs = plannedHrs.multiply(qty);
      task.setName(task.getName() + " x" + qty.intValue());
    }
    task.setTotalPlannedHrs(plannedHrs);

    return task;
  }

  @Override
  public TeamTask updateDiscount(TeamTask teamTask) {
    PriceList priceList = teamTask.getProject().getPriceList();
    if (priceList == null) {
      this.emptyDiscounts(teamTask);
      return teamTask;
    }

    PriceListLine priceListLine =
        this.getPriceListLine(teamTask, priceList, teamTask.getUnitPrice());
    Map<String, Object> discounts =
        priceListService.getReplacedPriceAndDiscounts(
            priceList, priceListLine, teamTask.getUnitPrice());

    if (discounts == null) {
      this.emptyDiscounts(teamTask);
    } else {
      teamTask.setDiscountTypeSelect((Integer) discounts.get("discountTypeSelect"));
      teamTask.setDiscountAmount((BigDecimal) discounts.get("discountAmount"));
      if (discounts.get("price") != null) {
        teamTask.setPriceDiscounted((BigDecimal) discounts.get("price"));
      }
    }
    return teamTask;
  }

  protected void emptyDiscounts(TeamTask teamTask) {
    teamTask.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
    teamTask.setDiscountAmount(BigDecimal.ZERO);
    teamTask.setPriceDiscounted(BigDecimal.ZERO);
  }

  protected PriceListLine getPriceListLine(
      TeamTask teamTask, PriceList priceList, BigDecimal price) {

    return priceListService.getPriceListLine(
        teamTask.getProduct(), teamTask.getQuantity(), priceList, price);
  }

  @Override
  public TeamTask compute(TeamTask teamTask) {
    if (teamTask.getProduct() == null && teamTask.getProject() == null
        || teamTask.getUnitPrice() == null
        || teamTask.getQuantity() == null) {
      return teamTask;
    }
    BigDecimal priceDiscounted = this.computeDiscount(teamTask);
    BigDecimal exTaxTotal = this.computeAmount(teamTask.getQuantity(), priceDiscounted);

    teamTask.setPriceDiscounted(priceDiscounted);
    teamTask.setExTaxTotal(exTaxTotal);

    return teamTask;
  }

  protected BigDecimal computeDiscount(TeamTask teamTask) {

    return priceListService.computeDiscount(
        teamTask.getUnitPrice(), teamTask.getDiscountTypeSelect(), teamTask.getDiscountAmount());
  }

  protected BigDecimal computeAmount(BigDecimal quantity, BigDecimal price) {

    BigDecimal amount =
        price
            .multiply(quantity)
            .setScale(AppSaleService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_EVEN);

    return amount;
  }

  @Override
  public List<InvoiceLine> createInvoiceLines(
      Invoice invoice, List<TeamTask> teamTaskList, int priority) throws AxelorException {

    List<InvoiceLine> invoiceLineList = new ArrayList<>();
    int count = 0;
    for (TeamTask teamTask : teamTaskList) {
      invoiceLineList.addAll(this.createInvoiceLine(invoice, teamTask, priority * 100 + count));
      count++;
    }
    return invoiceLineList;
  }

  @Override
  public List<InvoiceLine> createInvoiceLine(Invoice invoice, TeamTask teamTask, int priority)
      throws AxelorException {

    InvoiceLineGenerator invoiceLineGenerator =
        new InvoiceLineGenerator(
            invoice,
            teamTask.getProduct(),
            teamTask.getName(),
            teamTask.getUnitPrice(),
            BigDecimal.ZERO,
            teamTask.getPriceDiscounted(),
            teamTask.getDescription(),
            teamTask.getQuantity(),
            teamTask.getUnit(),
            null,
            priority,
            teamTask.getDiscountAmount(),
            teamTask.getDiscountTypeSelect(),
            teamTask.getExTaxTotal(),
            BigDecimal.ZERO,
            false,
            false,
            0) {

          @Override
          public List<InvoiceLine> creates() throws AxelorException {

            InvoiceLine invoiceLine = this.createInvoiceLine();
            invoiceLine.setProject(teamTask.getProject());
            invoiceLine.setSaleOrderLine(teamTask.getSaleOrderLine());
            teamTask.setInvoiceLine(invoiceLine);

            List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
            invoiceLines.add(invoiceLine);

            return invoiceLines;
          }
        };

    return invoiceLineGenerator.creates();
  }

  @Override
  protected void setModuleFields(TeamTask teamTask, LocalDate date, TeamTask newTeamTask) {
    super.setModuleFields(teamTask, date, newTeamTask);

    // Module 'business project' fields
    // none
  }

  @Override
  protected void updateModuleFields(TeamTask teamTask, TeamTask nextTeamTask) {
    super.updateModuleFields(teamTask, nextTeamTask);

    // Module 'business project' fields
    nextTeamTask.setToInvoice(teamTask.getToInvoice());
    nextTeamTask.setExTaxTotal(teamTask.getExTaxTotal());
    nextTeamTask.setDiscountTypeSelect(teamTask.getDiscountTypeSelect());
    nextTeamTask.setDiscountAmount(teamTask.getDiscountAmount());
    nextTeamTask.setPriceDiscounted(teamTask.getPriceDiscounted());
    nextTeamTask.setInvoicingType(teamTask.getInvoicingType());
    nextTeamTask.setCustomerReferral(teamTask.getCustomerReferral());
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  @Override
  public TeamTask updateTask(TeamTask teamTask, AppBusinessProject appBusinessProject) {

    teamTask = computeDefaultInformation(teamTask);

    if (teamTask.getInvoicingType() == TeamTaskRepository.INVOICING_TYPE_PACKAGE
        && !teamTask.getIsTaskRefused()) {

      switch (teamTask.getProject().getInvoicingSequenceSelect()) {
        case ProjectRepository.INVOICING_SEQ_INVOICE_PRE_TASK:
          teamTask.setToInvoice(
              !Strings.isNullOrEmpty(appBusinessProject.getPreTaskStatusSet())
                  && Arrays.asList(appBusinessProject.getPreTaskStatusSet().split(", "))
                      .contains(teamTask.getStatus()));
          break;

        case ProjectRepository.INVOICING_SEQ_INVOICE_POST_TASK:
          teamTask.setToInvoice(
              !Strings.isNullOrEmpty(appBusinessProject.getPostTaskStatusSet())
                  && Arrays.asList(appBusinessProject.getPostTaskStatusSet().split(", "))
                      .contains(teamTask.getStatus()));
          break;
      }
    } else {
      teamTask.setToInvoice(
          teamTask.getInvoicingType() == TeamTaskRepository.INVOICING_TYPE_TIME_SPENT);
    }

    return teamTaskRepo.save(teamTask);
  }

  @Override
  public TeamTask computeDefaultInformation(TeamTask teamTask) {

    Product product = teamTask.getProduct();
    if (product != null) {
      teamTask.setInvoicingType(TeamTaskRepository.INVOICING_TYPE_PACKAGE);
      if (teamTask.getUnitPrice() == null
          || teamTask.getUnitPrice().compareTo(BigDecimal.ZERO) == 0) {
        teamTask.setUnitPrice(this.computeUnitPrice(teamTask));
      }
    } else {
      TeamTaskCategory teamTaskCategory = teamTask.getTeamTaskCategory();
      if (teamTaskCategory == null) {
        return teamTask;
      }

      teamTask.setInvoicingType(teamTaskCategory.getDefaultInvoicingType());
      teamTask.setProduct(teamTaskCategory.getDefaultProduct());
      product = teamTask.getProduct();
      if (product == null) {
        return teamTask;
      }
      teamTask.setUnitPrice(this.computeUnitPrice(teamTask));
    }
    teamTask.setUnit(product.getSalesUnit() == null ? product.getUnit() : product.getSalesUnit());
    teamTask.setCurrency(product.getSaleCurrency());
    teamTask.setQuantity(teamTask.getBudgetedTime());

    teamTask = this.updateDiscount(teamTask);
    teamTask = this.compute(teamTask);
    return teamTask;
  }

  protected BigDecimal computeUnitPrice(TeamTask teamTask) {
    Product product = teamTask.getProduct();
    BigDecimal unitPrice = product.getSalePrice();

    PriceList priceList =
        Beans.get(PartnerPriceListService.class)
            .getDefaultPriceList(
                teamTask.getProject().getClientPartner(), PriceListRepository.TYPE_SALE);
    if (priceList == null) {
      return unitPrice;
    }

    PriceListLine priceListLine = this.getPriceListLine(teamTask, priceList, unitPrice);
    Map<String, Object> discounts =
        priceListService.getReplacedPriceAndDiscounts(priceList, priceListLine, unitPrice);

    if (discounts == null) {
      return unitPrice;
    } else {
      unitPrice =
          priceListService.computeDiscount(
              unitPrice,
              (Integer) discounts.get("discountTypeSelect"),
              (BigDecimal) discounts.get("discountAmount"));
    }
    return unitPrice;
  }

  @Override
  public TeamTask resetTeamTaskValues(TeamTask teamTask) {
    teamTask.setProduct(null);
    teamTask.setInvoicingType(null);
    teamTask.setToInvoice(null);
    teamTask.setQuantity(null);
    teamTask.setUnit(null);
    teamTask.setUnitPrice(null);
    teamTask.setCurrency(null);
    teamTask.setExTaxTotal(null);
    return teamTask;
  }
}
