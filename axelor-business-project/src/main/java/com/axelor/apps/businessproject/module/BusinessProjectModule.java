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
package com.axelor.apps.businessproject.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.account.db.repo.InvoiceManagementRepository;
import com.axelor.apps.bankpayment.service.invoice.payment.InvoicePaymentValidateServiceBankPayImpl;
import com.axelor.apps.base.db.repo.AppBusinessProjectRepository;
import com.axelor.apps.businessproject.db.repo.AppBusinessProjectManagementRepository;
import com.axelor.apps.businessproject.db.repo.InvoiceProjectRepository;
import com.axelor.apps.businessproject.db.repo.InvoicingProjectManagementRepository;
import com.axelor.apps.businessproject.db.repo.InvoicingProjectRepository;
import com.axelor.apps.businessproject.db.repo.SaleOrderProjectRepository;
import com.axelor.apps.businessproject.db.repo.TeamTaskBusinessProjectRepository;
import com.axelor.apps.businessproject.service.ContractLineServiceProjectImpl;
import com.axelor.apps.businessproject.service.ExpenseLineProjectService;
import com.axelor.apps.businessproject.service.ExpenseLineProjectServiceImpl;
import com.axelor.apps.businessproject.service.ExpenseServiceProjectImpl;
import com.axelor.apps.businessproject.service.InvoiceLineProjectService;
import com.axelor.apps.businessproject.service.InvoiceLineProjectServiceImpl;
import com.axelor.apps.businessproject.service.InvoicePaymentValidateProjectServiceImpl;
import com.axelor.apps.businessproject.service.InvoiceServiceProjectImpl;
import com.axelor.apps.businessproject.service.ProductTaskTemplateService;
import com.axelor.apps.businessproject.service.ProductTaskTemplateServiceImpl;
import com.axelor.apps.businessproject.service.ProjectBusinessService;
import com.axelor.apps.businessproject.service.ProjectBusinessServiceImpl;
import com.axelor.apps.businessproject.service.ProjectContractServiceImpl;
import com.axelor.apps.businessproject.service.ProjectFolderService;
import com.axelor.apps.businessproject.service.ProjectFolderServiceImpl;
import com.axelor.apps.businessproject.service.ProjectPurchaseServiceImpl;
import com.axelor.apps.businessproject.service.ProjectStockMoveInvoiceServiceImpl;
import com.axelor.apps.businessproject.service.PurchaseOrderInvoiceProjectServiceImpl;
import com.axelor.apps.businessproject.service.PurchaseOrderLineProjectService;
import com.axelor.apps.businessproject.service.PurchaseOrderLineServiceProjectImpl;
import com.axelor.apps.businessproject.service.SaleOrderInvoiceProjectServiceImpl;
import com.axelor.apps.businessproject.service.SaleOrderLineProjectService;
import com.axelor.apps.businessproject.service.SaleOrderLineProjectServiceImpl;
import com.axelor.apps.businessproject.service.TeamTaskBusinessProjectService;
import com.axelor.apps.businessproject.service.TeamTaskBusinessProjectServiceImpl;
import com.axelor.apps.businessproject.service.TimesheetLineBusinessService;
import com.axelor.apps.businessproject.service.TimesheetLineProjectServiceImpl;
import com.axelor.apps.businessproject.service.TimesheetProjectService;
import com.axelor.apps.businessproject.service.TimesheetProjectServiceImpl;
import com.axelor.apps.businessproject.service.WorkflowCancelServiceProjectImpl;
import com.axelor.apps.businessproject.service.WorkflowValidationServiceProjectImpl;
import com.axelor.apps.businessproject.service.WorkflowVentilationProjectServiceImpl;
import com.axelor.apps.businessproject.service.app.AppBusinessProjectService;
import com.axelor.apps.businessproject.service.app.AppBusinessProjectServiceImpl;
import com.axelor.apps.businessproject.service.report.AvailabilitiesReportService;
import com.axelor.apps.businessproject.service.report.AvailabilitiesReportServiceImpl;
import com.axelor.apps.contract.service.ContractLineServiceImpl;
import com.axelor.apps.contract.service.ContractServiceImpl;
import com.axelor.apps.hr.db.repo.TeamTaskHRRepository;
import com.axelor.apps.hr.service.expense.ExpenseServiceImpl;
import com.axelor.apps.hr.service.timesheet.TimesheetLineServiceImpl;
import com.axelor.apps.hr.service.timesheet.TimesheetServiceImpl;
import com.axelor.apps.project.service.ProjectServiceImpl;
import com.axelor.apps.project.service.TeamTaskProjectServiceImpl;
import com.axelor.apps.supplychain.db.repo.SaleOrderSupplychainRepository;
import com.axelor.apps.supplychain.service.InvoiceLineSupplychainService;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.PurchaseOrderLineServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.SaleOrderLineServiceSupplyChainImpl;
import com.axelor.apps.supplychain.service.SaleOrderPurchaseServiceImpl;
import com.axelor.apps.supplychain.service.StockMoveInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.invoice.InvoiceServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.workflow.WorkflowCancelServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.workflow.WorkflowValidationServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.workflow.WorkflowVentilationServiceSupplychainImpl;

public class BusinessProjectModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(SaleOrderInvoiceServiceImpl.class).to(SaleOrderInvoiceProjectServiceImpl.class);
    bind(PurchaseOrderInvoiceServiceImpl.class).to(PurchaseOrderInvoiceProjectServiceImpl.class);
    bind(TimesheetServiceImpl.class).to(TimesheetProjectServiceImpl.class);
    bind(TimesheetProjectService.class).to(TimesheetProjectServiceImpl.class);
    bind(TimesheetLineServiceImpl.class).to(TimesheetLineProjectServiceImpl.class);
    bind(ExpenseServiceImpl.class).to(ExpenseServiceProjectImpl.class);
    bind(ProjectServiceImpl.class).to(ProjectBusinessServiceImpl.class);
    bind(ProjectBusinessService.class).to(ProjectBusinessServiceImpl.class);
    bind(InvoicingProjectRepository.class).to(InvoicingProjectManagementRepository.class);
    bind(AppBusinessProjectService.class).to(AppBusinessProjectServiceImpl.class);
    bind(InvoiceServiceSupplychainImpl.class).to(InvoiceServiceProjectImpl.class);
    bind(TeamTaskProjectServiceImpl.class).to(TeamTaskBusinessProjectServiceImpl.class);
    bind(TeamTaskBusinessProjectService.class).to(TeamTaskBusinessProjectServiceImpl.class);
    bind(SaleOrderSupplychainRepository.class).to(SaleOrderProjectRepository.class);
    bind(ProductTaskTemplateService.class).to(ProductTaskTemplateServiceImpl.class);
    bind(StockMoveInvoiceServiceImpl.class).to(ProjectStockMoveInvoiceServiceImpl.class);
    bind(SaleOrderPurchaseServiceImpl.class).to(ProjectPurchaseServiceImpl.class);
    bind(PurchaseOrderLineServiceSupplychainImpl.class)
        .to(PurchaseOrderLineServiceProjectImpl.class);
    bind(SaleOrderLineProjectService.class).to(SaleOrderLineProjectServiceImpl.class);
    bind(PurchaseOrderLineProjectService.class).to(PurchaseOrderLineServiceProjectImpl.class);
    bind(ExpenseLineProjectService.class).to(ExpenseLineProjectServiceImpl.class);
    bind(InvoiceLineProjectService.class).to(InvoiceLineProjectServiceImpl.class);
    bind(InvoiceManagementRepository.class).to(InvoiceProjectRepository.class);
    bind(WorkflowVentilationServiceSupplychainImpl.class)
        .to(WorkflowVentilationProjectServiceImpl.class);
    bind(TimesheetLineBusinessService.class).to(TimesheetLineProjectServiceImpl.class);
    bind(WorkflowValidationServiceSupplychainImpl.class)
        .to(WorkflowValidationServiceProjectImpl.class);
    bind(WorkflowCancelServiceSupplychainImpl.class).to(WorkflowCancelServiceProjectImpl.class);
    bind(TeamTaskHRRepository.class).to(TeamTaskBusinessProjectRepository.class);
    bind(SaleOrderLineServiceSupplyChainImpl.class).to(SaleOrderLineProjectServiceImpl.class);
    bind(InvoiceLineSupplychainService.class).to(InvoiceLineProjectServiceImpl.class);
    bind(ContractServiceImpl.class).to(ProjectContractServiceImpl.class);
    bind(ContractLineServiceImpl.class).to(ContractLineServiceProjectImpl.class);
    bind(AppBusinessProjectRepository.class).to(AppBusinessProjectManagementRepository.class);
    bind(InvoicePaymentValidateServiceBankPayImpl.class)
        .to(InvoicePaymentValidateProjectServiceImpl.class);
    bind(ProjectFolderService.class).to(ProjectFolderServiceImpl.class);
    bind(AvailabilitiesReportService.class).to(AvailabilitiesReportServiceImpl.class);
  }
}
