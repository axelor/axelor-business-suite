package com.axelor.apps.hr.web;

import java.util.List;

import com.axelor.apps.hr.db.EmploymentContract;
import com.axelor.apps.hr.db.PayrollLeave;
import com.axelor.apps.hr.db.PayrollPreparation;
import com.axelor.apps.hr.db.repo.EmploymentContractRepository;
import com.axelor.apps.hr.db.repo.PayrollLeaveRepository;
import com.axelor.apps.hr.service.PayrollPreparationService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;

public class PayrollPreparationController {

	@Inject
	protected PayrollPreparationService payrollPreparationService;
	
	@Inject
	protected PayrollLeaveRepository payrollLeaveRepo;

	public void generateFromEmploymentContract(ActionRequest request, ActionResponse response){

		PayrollPreparation payrollPreparation = request.getContext().asType(PayrollPreparation.class);
		EmploymentContract employmentContract = Beans.get(EmploymentContractRepository.class).find(new Long(request.getContext().get("_idEmploymentContract").toString()));

		response.setValues(payrollPreparationService.generateFromEmploymentContract(payrollPreparation, employmentContract));
	}
	
	public void fillInPayrollPreparation(ActionRequest request, ActionResponse response) throws AxelorException{
		PayrollPreparation payrollPreparation = request.getContext().asType(PayrollPreparation.class);
		
		 List<PayrollLeave> payrollLeaveList = payrollPreparationService.fillInPayrollPreparation(payrollPreparation);
		
		response.setValue("extraHoursLineList",payrollPreparation.getExtraHoursLineList());
		response.setValue("$payrollLeavesList", payrollLeaveList);
		response.setValue("duration",payrollPreparation.getDuration());
		response.setValue("duration",payrollPreparation.getDuration());
		response.setValue("expenseAmount",payrollPreparation.getExpenseAmount());
		response.setValue("expenseList",payrollPreparation.getExpenseList());
		response.setValue("otherCostsEmployeeSet",payrollPreparation.getEmploymentContract().getOtherCostsEmployeeSet());
		response.setValue("annualGrossSalary",payrollPreparation.getEmploymentContract().getAnnualGrossSalary());
	}
	
	public void fillInPayrollPreparationLeaves(ActionRequest request, ActionResponse response) throws AxelorException{
		PayrollPreparation payrollPreparation = request.getContext().asType(PayrollPreparation.class);
		
		 List<PayrollLeave> payrollLeaveList = payrollPreparationService.fillInLeaves(payrollPreparation);
		
		response.setValue("$payrollLeavesList", payrollLeaveList);
	}
}
