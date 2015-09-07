package com.axelor.apps.business.project.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.business.project.service.ExpenseProjectService;
import com.axelor.apps.business.project.service.ProjectTaskBusinessService;
import com.axelor.apps.business.project.service.PurchaseOrderInvoiceProjectServiceImpl;
import com.axelor.apps.business.project.service.SaleOrderInvoiceProjectServiceImpl;
import com.axelor.apps.business.project.service.TimesheetProjectServiceImp;
import com.axelor.apps.hr.service.expense.ExpenseService;
import com.axelor.apps.hr.service.timesheet.TimesheetServiceImp;
import com.axelor.apps.project.service.ProjectTaskService;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceServiceImpl;

public class BusinessProjectModule extends AxelorModule{

	    @Override
	    protected void configure() {
	    	 bind(SaleOrderInvoiceServiceImpl.class).to(SaleOrderInvoiceProjectServiceImpl.class);
	    	 bind(PurchaseOrderInvoiceServiceImpl.class).to(PurchaseOrderInvoiceProjectServiceImpl.class);
	    	 bind(TimesheetServiceImp.class).to(TimesheetProjectServiceImp.class);
	    	 bind(ExpenseService.class).to(ExpenseProjectService.class);
	    	 bind(ProjectTaskService.class).to(ProjectTaskBusinessService.class);
	    }
}
