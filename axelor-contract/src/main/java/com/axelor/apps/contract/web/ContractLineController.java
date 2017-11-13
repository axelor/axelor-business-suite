package com.axelor.apps.contract.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.contract.db.Contract;
import com.axelor.apps.contract.db.ContractLine;
import com.axelor.apps.contract.db.ContractVersion;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class ContractLineController {
	
	public void changeProduct(ActionRequest request, ActionResponse response) {
		ContractLine contractLine = request.getContext().asType(ContractLine.class);
		
		if(contractLine == null || contractLine.getProduct() == null) {
			this.resetProductInformation(response);
			return;
		}
		
		Contract contract = null;
		if(request.getContext().getParentContext().getContextClass() == Contract.class){
			contract = request.getContext().getParentContext().asType(Contract.class);
		}else if (request.getContext().getParentContext().getContextClass() == ContractVersion.class){
			ContractVersion contractVersion = request.getContext().getParentContext().asType(ContractVersion.class);
			contract = contractVersion.getContractNext() == null ? contractVersion.getContract() : contractVersion.getContractNext() ;
		}
		Product product = contractLine.getProduct();
		
		
		try  {
			BigDecimal price = product.getSalePrice();
			if (contract != null){
				TaxLine taxLine = Beans.get(AccountManagementService.class).getTaxLine(
						LocalDate.now(), product, contract.getCompany(), contract.getPartner().getFiscalPosition(), false);
				response.setValue("taxLine", taxLine);

				
				if(taxLine != null && product.getInAti()) {
					price = price.divide(taxLine.getValue().add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
				}
			}
			

			response.setValue("productName", product.getName());
			response.setValue("unit", product.getSalesUnit() == null ? product.getUnit() : product.getSalesUnit());
			response.setValue("price", price);

		}
		catch(Exception e)  {
			response.setFlash(e.getMessage());
			this.resetProductInformation(response);
		}
	}
	
	private void resetProductInformation(ActionResponse response) {
		response.setValue("taxLine", null);
		response.setValue("productName", null);
		response.setValue("unit", null);
		response.setValue("price", null);
		response.setValue("exTaxTotal", null);
		response.setValue("inTaxTotal", null);
	}

	public void compute(ActionRequest request, ActionResponse response) {
		ContractLine contractLine = request.getContext().asType(ContractLine.class);
		Product product = contractLine.getProduct();

		if(contractLine == null || product == null) {
			this.resetProductInformation(response);
			return;
		}

		BigDecimal taxRate = BigDecimal.ZERO;
		if(contractLine.getTaxLine() != null)  {  taxRate = contractLine.getTaxLine().getValue();  }
		BigDecimal exTaxTotal = contractLine.getQty().multiply(contractLine.getPrice()).setScale(2, RoundingMode.HALF_EVEN);
		BigDecimal inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));

		response.setValue("exTaxTotal", exTaxTotal);
		response.setValue("inTaxTotal", inTaxTotal);
	}
}
