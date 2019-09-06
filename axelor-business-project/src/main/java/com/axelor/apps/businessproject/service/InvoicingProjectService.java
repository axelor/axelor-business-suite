/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.account.service.invoice.print.InvoicePrintServiceImpl;
import com.axelor.apps.account.util.InvoiceLineComparator;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businessproject.db.InvoicingElementOrder;
import com.axelor.apps.businessproject.db.InvoicingProject;
import com.axelor.apps.businessproject.db.repo.InvoicingProjectRepository;
import com.axelor.apps.businessproject.exception.IExceptionMessage;
import com.axelor.apps.businessproject.report.IReport;
import com.axelor.apps.hr.db.ExpenseLine;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.ExpenseLineRepository;
import com.axelor.apps.hr.db.repo.ExpenseRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.service.expense.ExpenseService;
import com.axelor.apps.hr.service.timesheet.TimesheetService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.service.ProjectServiceImpl;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderLineRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineGeneratorSupplyChain;
import com.axelor.apps.tool.file.PdfTool;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class InvoicingProjectService {

  @Inject protected TimesheetService timesheetService;

  @Inject protected ExpenseService expenseService;

  @Inject protected PartnerService partnerService;

  @Inject protected TeamTaskBusinessProjectService teamTaskBusinessProjectService;

  @Inject protected InvoicingProjectRepository invoicingProjectRepo;

  protected int MAX_LEVEL_OF_PROJECT = 10;

  protected int sequence = 0;

  protected static final String DATE_FORMAT_YYYYMMDDHHMM = "YYYYMMddHHmm";

  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateInvoice(InvoicingProject invoicingProject) throws AxelorException {
    Project project = invoicingProject.getProject();
    Partner customer = project.getClientPartner();
    Partner customerContact = project.getContactPartner();
    if (customerContact == null && customer.getContactPartnerSet().size() == 1) {
      customerContact = customer.getContactPartnerSet().iterator().next();
    }
    Company company = this.getRootCompany(project);
    if (company == null) {
      throw new AxelorException(
          invoicingProject,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.INVOICING_PROJECT_PROJECT_COMPANY));
    }
    project.getAssignedTo();
    InvoiceGenerator invoiceGenerator =
        new InvoiceGenerator(
            InvoiceRepository.OPERATION_TYPE_CLIENT_SALE,
            company,
            customer.getPaymentCondition(),
            customer.getInPaymentMode(),
            partnerService.getInvoicingAddress(customer),
            customer,
            customerContact,
            customer.getCurrency(),
            Beans.get(PartnerPriceListService.class)
                .getDefaultPriceList(customer, PriceListRepository.TYPE_SALE),
            null,
            null,
            null,
            null,
            null) {

          @Override
          public Invoice generate() throws AxelorException {

            Invoice invoice = super.createInvoiceHeader();
            invoice.setProject(project);
            invoice.setPriceList(project.getPriceList());
            return invoice;
          }
        };
    Invoice invoice = invoiceGenerator.generate();
    AccountConfigService accountConfigService = Beans.get(AccountConfigService.class);
    AccountConfig accountConfig = accountConfigService.getAccountConfig(company);
    invoice.setDisplayTimesheetOnPrinting(accountConfig.getDisplayTimesheetOnPrinting());
    invoice.setDisplayExpenseOnPrinting(accountConfig.getDisplayExpenseOnPrinting());

    invoiceGenerator.populate(invoice, this.populate(invoice, invoicingProject));
    Beans.get(InvoiceRepository.class).save(invoice);

    invoicingProject.setInvoice(invoice);
    invoicingProject.setStatusSelect(InvoicingProjectRepository.STATUS_GENERATED);
    invoicingProjectRepo.save(invoicingProject);
    return invoice;
  }

  public List<InvoiceLine> populate(Invoice invoice, InvoicingProject folder)
      throws AxelorException {
    List<SaleOrderLine> saleOrderLineList =
        new ArrayList<SaleOrderLine>(folder.getSaleOrderLineSet());
    List<PurchaseOrderLine> purchaseOrderLineList =
        new ArrayList<PurchaseOrderLine>(folder.getPurchaseOrderLineSet());
    List<TimesheetLine> timesheetLineList = new ArrayList<TimesheetLine>(folder.getLogTimesSet());
    List<ExpenseLine> expenseLineList = new ArrayList<ExpenseLine>(folder.getExpenseLineSet());
    List<TeamTask> teamTaskList = new ArrayList<TeamTask>(folder.getTeamTaskSet());

    List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();

    List<InvoicingElementOrder> invoicingElementOrderList = folder.getInvoicingElementOrderList();
    Map<String, Integer> priorityMap = new HashMap<>();

    for (InvoicingElementOrder elementOrder : invoicingElementOrderList) {
      priorityMap.put(elementOrder.getElementToInvoiceSelect(), elementOrder.getSequence());
    }

    invoiceLineList.addAll(
        this.createSaleOrderInvoiceLines(
            invoice,
            saleOrderLineList,
            priorityMap.get(InvoicingProjectRepository.SALE_ORDER_LINE) != null
                ? priorityMap.get(InvoicingProjectRepository.SALE_ORDER_LINE)
                : 0));
    invoiceLineList.addAll(
        this.createPurchaseOrderInvoiceLines(
            invoice,
            purchaseOrderLineList,
            priorityMap.get(InvoicingProjectRepository.PURCHASE_ORDER_LINE) != null
                ? priorityMap.get(InvoicingProjectRepository.PURCHASE_ORDER_LINE)
                : 0));
    invoiceLineList.addAll(
        timesheetService.createInvoiceLines(
            invoice,
            timesheetLineList,
            priorityMap.get(InvoicingProjectRepository.TIME_SHEET_LINE) != null
                ? priorityMap.get(InvoicingProjectRepository.TIME_SHEET_LINE)
                : 0));
    invoiceLineList.addAll(
        expenseService.createInvoiceLines(
            invoice,
            expenseLineList,
            priorityMap.get(InvoicingProjectRepository.EXPENSE_LINE) != null
                ? priorityMap.get(InvoicingProjectRepository.EXPENSE_LINE)
                : 0));
    invoiceLineList.addAll(
        teamTaskBusinessProjectService.createInvoiceLines(
            invoice,
            teamTaskList,
            priorityMap.get(InvoicingProjectRepository.TEAM_TASK) != null
                ? priorityMap.get(InvoicingProjectRepository.TEAM_TASK)
                : 0));

    Collections.sort(invoiceLineList, new InvoiceLineComparator());

    for (InvoiceLine invoiceLine : invoiceLineList) {
      invoiceLine.setSequence(sequence);
      sequence++;
    }

    return invoiceLineList;
  }

  public List<InvoiceLine> createSaleOrderInvoiceLines(
      Invoice invoice, List<SaleOrderLine> saleOrderLineList, int priority) throws AxelorException {

    List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();
    int count = 1;
    for (SaleOrderLine saleOrderLine : saleOrderLineList) {

      invoiceLineList.addAll(
          this.createInvoiceLine(invoice, saleOrderLine, priority * 100 + count));
      count++;
    }

    return invoiceLineList;
  }

  public List<InvoiceLine> createInvoiceLine(
      Invoice invoice, SaleOrderLine saleOrderLine, int priority) throws AxelorException {

    Product product = saleOrderLine.getProduct();

    InvoiceLineGenerator invoiceLineGenerator =
        new InvoiceLineGeneratorSupplyChain(
            invoice,
            product,
            saleOrderLine.getProductName(),
            saleOrderLine.getDescription(),
            saleOrderLine.getQty(),
            saleOrderLine.getUnit(),
            priority,
            false,
            saleOrderLine,
            null,
            null,
            false,
            0) {

          @Override
          public List<InvoiceLine> creates() throws AxelorException {

            InvoiceLine invoiceLine = this.createInvoiceLine();
            invoiceLine.setProject(saleOrderLine.getProject());
            List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
            invoiceLines.add(invoiceLine);

            return invoiceLines;
          }
        };

    return invoiceLineGenerator.creates();
  }

  public List<InvoiceLine> createPurchaseOrderInvoiceLines(
      Invoice invoice, List<PurchaseOrderLine> purchaseOrderLineList, int priority)
      throws AxelorException {

    List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();
    for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLineList) {

      invoiceLineList.addAll(
          Beans.get(PurchaseOrderInvoiceProjectServiceImpl.class)
              .createInvoiceLine(invoice, purchaseOrderLine));
    }
    return invoiceLineList;
  }

  public List<InvoiceLine> createInvoiceLine(
      Invoice invoice, PurchaseOrderLine purchaseOrderLine, int priority) throws AxelorException {

    Product product = purchaseOrderLine.getProduct();

    InvoiceLineGeneratorSupplyChain invoiceLineGenerator =
        new InvoiceLineGeneratorSupplyChain(
            invoice,
            product,
            purchaseOrderLine.getProductName(),
            purchaseOrderLine.getDescription(),
            purchaseOrderLine.getQty(),
            purchaseOrderLine.getUnit(),
            priority,
            false,
            null,
            purchaseOrderLine,
            null,
            false,
            0) {
          @Override
          public List<InvoiceLine> creates() throws AxelorException {

            InvoiceLine invoiceLine = this.createInvoiceLine();

            List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
            invoiceLines.add(invoiceLine);

            return invoiceLines;
          }
        };

    return invoiceLineGenerator.creates();
  }

  public void setLines(InvoicingProject invoicingProject, Project project, int counter) {

    if (counter > ProjectServiceImpl.MAX_LEVEL_OF_PROJECT) {
      return;
    }
    counter++;

    this.fillLines(invoicingProject, project);

    List<Project> projectChildrenList =
        Beans.get(ProjectRepository.class).all().filter("self.parentProject = ?1", project).fetch();

    for (Project projectChild : projectChildrenList) {
      this.setLines(invoicingProject, projectChild, counter);
    }

    return;
  }

  public void fillLines(InvoicingProject invoicingProject, Project project) {
    String commonQuery =
        "self.project = :project AND self.toInvoice = true AND self.invoiced = false";

    StringBuilder solQueryBuilder = new StringBuilder(commonQuery);
    solQueryBuilder.append(
        " AND (self.saleOrder.statusSelect = :statusConfirmed OR self.saleOrder.statusSelect = :statusCompleted)");

    Map<String, Object> solQueryMap = new HashMap<>();
    solQueryMap.put("project", project);
    solQueryMap.put("statusConfirmed", SaleOrderRepository.STATUS_ORDER_CONFIRMED);
    solQueryMap.put("statusCompleted", SaleOrderRepository.STATUS_ORDER_COMPLETED);

    StringBuilder polQueryBuilder = new StringBuilder(commonQuery);
    polQueryBuilder.append(
        " AND (self.purchaseOrder.statusSelect = 3 OR self.purchaseOrder.statusSelect = 4)");

    Map<String, Object> polQueryMap = new HashMap<>();
    polQueryMap.put("project", project);

    StringBuilder logTimesQueryBuilder = new StringBuilder(commonQuery);
    logTimesQueryBuilder.append(" AND self.timesheet.statusSelect = :timesheetStatus");

    Map<String, Object> logTimesQueryMap = new HashMap<>();
    logTimesQueryMap.put("project", project);
    logTimesQueryMap.put("timesheetStatus", TimesheetRepository.STATUS_VALIDATED);

    StringBuilder expenseLineQueryBuilder = new StringBuilder(commonQuery);
    expenseLineQueryBuilder.append(
        " AND (self.expense.statusSelect = :statusValidated OR self.expense.statusSelect = :statusReimbursed)");

    Map<String, Object> expenseLineQueryMap = new HashMap<>();
    expenseLineQueryMap.put("project", project);
    expenseLineQueryMap.put("statusValidated", ExpenseRepository.STATUS_VALIDATED);
    expenseLineQueryMap.put("statusReimbursed", ExpenseRepository.STATUS_REIMBURSED);

    StringBuilder taskQueryBuilder = new StringBuilder(commonQuery);
    taskQueryBuilder.append(" AND self.status = 'closed'");

    Map<String, Object> taskQueryMap = new HashMap<>();
    taskQueryMap.put("project", project);

    if (invoicingProject.getDeadlineDate() != null) {
      solQueryBuilder.append(" AND self.saleOrder.creationDate < :deadlineDate");
      solQueryMap.put("deadlineDate", invoicingProject.getDeadlineDate());

      polQueryBuilder.append(" AND self.purchaseOrder.orderDate < :deadlineDate");
      polQueryMap.put("deadlineDate", invoicingProject.getDeadlineDate());

      logTimesQueryBuilder.append(" AND self.date < :deadlineDate");
      logTimesQueryMap.put("deadlineDate", invoicingProject.getDeadlineDate());

      expenseLineQueryBuilder.append(" AND self.expenseDate < :deadlineDate");
      expenseLineQueryMap.put("deadlineDate", invoicingProject.getDeadlineDate());

      taskQueryBuilder.append(" AND self.taskDeadline < :deadlineDate");
      taskQueryMap.put("deadlineDate", invoicingProject.getDeadlineDate());
    }

    invoicingProject
        .getSaleOrderLineSet()
        .addAll(
            Beans.get(SaleOrderLineRepository.class)
                .all()
                .filter(solQueryBuilder.toString())
                .bind(solQueryMap)
                .fetch());

    invoicingProject
        .getPurchaseOrderLineSet()
        .addAll(
            Beans.get(PurchaseOrderLineRepository.class)
                .all()
                .filter(polQueryBuilder.toString())
                .bind(polQueryMap)
                .fetch());

    invoicingProject
        .getLogTimesSet()
        .addAll(
            Beans.get(TimesheetLineRepository.class)
                .all()
                .filter(logTimesQueryBuilder.toString())
                .bind(logTimesQueryMap)
                .fetch());

    invoicingProject
        .getExpenseLineSet()
        .addAll(
            Beans.get(ExpenseLineRepository.class)
                .all()
                .filter(expenseLineQueryBuilder.toString())
                .bind(expenseLineQueryMap)
                .fetch());

    invoicingProject
        .getTeamTaskSet()
        .addAll(
            Beans.get(TeamTaskRepository.class)
                .all()
                .filter(taskQueryBuilder.toString())
                .bind(taskQueryMap)
                .fetch());
  }

  public void clearLines(InvoicingProject invoicingProject) {

    invoicingProject.setSaleOrderLineSet(new HashSet<SaleOrderLine>());
    invoicingProject.setPurchaseOrderLineSet(new HashSet<PurchaseOrderLine>());
    invoicingProject.setLogTimesSet(new HashSet<TimesheetLine>());
    invoicingProject.setExpenseLineSet(new HashSet<ExpenseLine>());
    invoicingProject.setProjectSet(new HashSet<Project>());
    invoicingProject.setTeamTaskSet(new HashSet<TeamTask>());
  }

  public Company getRootCompany(Project project) {
    if (project.getParentProject() == null) {
      return project.getCompany();
    } else {
      return getRootCompany(project.getParentProject());
    }
  }

  public int countToInvoice(Project project) {

    int toInvoiceCount = 0;

    String query = "self.project = ?1";

    if (project.getIsShowPhasesElements()) {
      query = "(self.project = ?1 OR self.project.parentProject = ?1)";
    }

    query += " AND self.toInvoice = true AND self.invoiced = false";

    toInvoiceCount += Beans.get(SaleOrderLineRepository.class).all().filter(query, project).count();

    toInvoiceCount +=
        Beans.get(PurchaseOrderLineRepository.class).all().filter(query, project).count();

    toInvoiceCount += Beans.get(ExpenseLineRepository.class).all().filter(query, project).count();

    toInvoiceCount += Beans.get(TimesheetLineRepository.class).all().filter(query, project).count();

    toInvoiceCount += Beans.get(TeamTaskRepository.class).all().filter(query, project).count();

    return toInvoiceCount;
  }

  public void generateAnnex(InvoicingProject invoicingProject) throws AxelorException, IOException {
    String title =
        I18n.get("InvoicingProjectAnnex")
            + "-"
            + Beans.get(AppBaseService.class)
                .getTodayDateTime()
                .format(DateTimeFormatter.ofPattern(DATE_FORMAT_YYYYMMDDHHMM));

    ReportSettings reportSettings =
        ReportFactory.createReport(IReport.INVOICING_PROJECT_ANNEX, title)
            .addParam("InvProjectId", invoicingProject.getId())
            .addParam("Locale", ReportSettings.getPrintingLocale(null));

    if (invoicingProject.getAttachAnnexToInvoice()) {
      List<File> fileList = new ArrayList<>();
      MetaFiles metaFiles = Beans.get(MetaFiles.class);

      fileList.add(
          Beans.get(InvoicePrintServiceImpl.class)
              .print(invoicingProject.getInvoice(), null, ReportSettings.FORMAT_PDF));
      fileList.add(reportSettings.generate().getFile());

      MetaFile metaFile = metaFiles.upload(PdfTool.mergePdf(fileList));
      metaFile.setFileName(title + ".pdf");
      metaFiles.attach(metaFile, null, invoicingProject);
      return;
    }
    reportSettings.toAttach(invoicingProject).generate();
  }
}
