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
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.alarm.AlarmEngineService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.businessproject.db.InvoicingProject;
import com.axelor.apps.businessproject.db.repo.InvoicingProjectRepository;
import com.axelor.apps.businessproject.report.IReport;
import com.axelor.apps.hr.db.ExpenseLine;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.supplychain.service.AccountingSituationSupplychainService;
import com.axelor.apps.supplychain.service.IntercoService;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.apps.supplychain.service.invoice.InvoiceServiceSupplychainImpl;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.team.db.TeamTask;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;

@Singleton
public class InvoiceServiceProjectImpl extends InvoiceServiceSupplychainImpl {
  private InvoicingProjectRepository invoicingProjectRepo;

  @Inject
  public InvoiceServiceProjectImpl(
      PartnerService partnerService,
      AlarmEngineService<Invoice> alarmEngineService,
      InvoiceRepository invoiceRepo,
      AppAccountService appAccountService,
      InvoiceLineService invoiceLineService,
      BlockingService blockingService,
      UserService userService,
      SequenceService sequenceService,
      AccountConfigService accountConfigService,
      MoveService moveService,
      PurchaseOrderInvoiceService purchaseOrderInvoiceService,
      PurchaseOrderRepository purchaseOrderRepository,
      SaleOrderInvoiceService saleOrderInvoiceService,
      SaleOrderRepository saleOrderRepository,
      AccountingSituationSupplychainService accountingSituationSupplychainService,
      IntercoService intercoService,
      InvoicingProjectRepository invoicingProjectRepo) {
    super(
        partnerService,
        alarmEngineService,
        invoiceRepo,
        appAccountService,
        invoiceLineService,
        blockingService,
        userService,
        sequenceService,
        accountConfigService,
        moveService,
        purchaseOrderInvoiceService,
        purchaseOrderRepository,
        saleOrderInvoiceService,
        saleOrderRepository,
        accountingSituationSupplychainService,
        intercoService);

    this.invoicingProjectRepo = invoicingProjectRepo;
  }

  public List<String> editInvoiceAnnex(Invoice invoice, String invoiceIds, boolean toAttach)
      throws AxelorException {
    User user = userService.getUser();

    if (!user.getActiveCompany().getAccountConfig().getDisplayTimesheetOnPrinting()
        && !user.getActiveCompany().getAccountConfig().getDisplayExpenseOnPrinting()) {
      return null;
    }

    String language = ReportSettings.getPrintingLocale(invoice.getPartner());

    String title = I18n.get("Invoice");
    if (invoice.getInvoiceId() != null) {
      title += invoice.getInvoiceId();
    }

    Integer invoicesCopy = invoice.getInvoicesCopySelect();
    ReportSettings rS =
        ReportFactory.createReport(
            IReport.INVOICE_ANNEX, title + "-" + I18n.get("Annex") + "-${date}");

    if (toAttach) {
      rS.toAttach(invoice);
    }

    String fileLink =
        rS.addParam("InvoiceId", invoiceIds)
            .addParam("Locale", language)
            .addParam("InvoicesCopy", invoicesCopy)
            .generate()
            .getFileLink();

    List<String> res = Arrays.asList(title, fileLink);

    return res;
  }

  @Override
  protected void afterVentilation(Invoice invoice) throws AxelorException {
    super.afterVentilation(invoice);
    InvoicingProject invoicingProject =
        invoicingProjectRepo.all().filter("self.invoice.id = ?", invoice.getId()).fetchOne();

    if (invoicingProject != null) {
      for (SaleOrderLine saleOrderLine : invoicingProject.getSaleOrderLineSet()) {
        saleOrderLine.setInvoiced(true);
      }
      for (PurchaseOrderLine purchaseOrderLine : invoicingProject.getPurchaseOrderLineSet()) {
        purchaseOrderLine.setInvoiced(true);
      }
      for (TimesheetLine timesheetLine : invoicingProject.getLogTimesSet()) {
        timesheetLine.setInvoiced(true);
      }
      for (ExpenseLine expenseLine : invoicingProject.getExpenseLineSet()) {
        expenseLine.setInvoiced(true);
      }
      for (TeamTask teamTask : invoicingProject.getTeamTaskSet()) {
        teamTask.setInvoiced(true);
      }
      for (Project project : invoicingProject.getProjectSet()) {
        project.setInvoiced(true);
      }
    }
  }
}
