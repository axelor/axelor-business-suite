/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.account.web;

import java.math.BigDecimal;
import java.util.Map;
import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.TaxEquiv;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.service.AccountManagementAccountService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.generator.line.InvoiceLineManagement;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.tax.FiscalPositionService;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;

public class InvoiceLineController {

	private InvoiceLineService invoiceLineService;
	private AccountManagementAccountService accountManagementService;
	
	@Inject
	public InvoiceLineController(InvoiceLineService invoiceLineService, AccountManagementAccountService accountManagementService) {
		this.invoiceLineService = invoiceLineService;
		this.accountManagementService = accountManagementService;
	}

	public void createAnalyticDistributionWithTemplate(ActionRequest request, ActionResponse response) throws AxelorException{
		InvoiceLine invoiceLine = request.getContext().asType(InvoiceLine.class);
		Invoice invoice = invoiceLine.getInvoice();
		if(invoice == null){
			invoice = request.getContext().getParentContext().asType(Invoice.class);
			invoiceLine.setInvoice(invoice);
		}
		if(invoiceLine.getAnalyticDistributionTemplate() != null){
			invoiceLine = invoiceLineService.createAnalyticDistributionWithTemplate(invoiceLine);
			response.setValue("analyticMoveLineList", invoiceLine.getAnalyticMoveLineList());
		} else {
			throw new AxelorException(IException.CONFIGURATION_ERROR, I18n.get("No template selected"));
		}
	}
	
	public void computeAnalyticDistribution(ActionRequest request, ActionResponse response) throws AxelorException{
		InvoiceLine invoiceLine = request.getContext().asType(InvoiceLine.class);
		Invoice invoice = invoiceLine.getInvoice();
		Context context = request.getContext();
		
		if(invoice == null){
			if(context.getParent().getContextClass() == InvoiceLine.class) {
				context = request.getContext().getParent();
			}
			invoice = context.getParent().asType(Invoice.class);
			invoiceLine.setInvoice(invoice);
		}
		if(Beans.get(AppAccountService.class).getAppAccount().getManageAnalyticAccounting()){
			invoiceLine = invoiceLineService.computeAnalyticDistribution(invoiceLine);
			response.setValue("analyticMoveLineList", invoiceLine.getAnalyticMoveLineList());
		}
	}
	
	public void compute(ActionRequest request, ActionResponse response) throws AxelorException {

		Context context = request.getContext();
		InvoiceLine invoiceLine = context.asType(InvoiceLine.class);
		
		if(context.getParent().getContextClass() == InvoiceLine.class) {
			context = request.getContext().getParent();
		}
		
		Invoice invoice = this.getInvoice(context);
		
		if(invoice == null || invoiceLine.getProduct() == null || invoiceLine.getPrice() == null || invoiceLine.getQty() == null)  {  return;  }
		
		BigDecimal exTaxTotal = BigDecimal.ZERO;
		BigDecimal companyExTaxTotal = BigDecimal.ZERO;
		BigDecimal inTaxTotal = BigDecimal.ZERO;
		BigDecimal companyInTaxTotal = BigDecimal.ZERO;
		BigDecimal priceDiscounted = invoiceLineService.computeDiscount(invoiceLine,invoice);
		
		response.setValue("priceDiscounted", priceDiscounted);
		response.setAttr("priceDiscounted", "hidden", priceDiscounted.compareTo(invoiceLine.getPrice()) == 0);

		BigDecimal taxRate = BigDecimal.ZERO;
		if(invoiceLine.getTaxLine() != null)  {
			taxRate = invoiceLine.getTaxLine().getValue();
			response.setValue("taxRate", taxRate);
			response.setValue("taxCode", invoiceLine.getTaxLine().getTax().getCode());
		}
		
		if(!invoice.getInAti()) {
			exTaxTotal = InvoiceLineManagement.computeAmount(invoiceLine.getQty(), invoiceLineService.computeDiscount(invoiceLine,invoice));
			inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
		} else {
			inTaxTotal = InvoiceLineManagement.computeAmount(invoiceLine.getQty(), invoiceLineService.computeDiscount(invoiceLine,invoice));
			exTaxTotal = inTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
		}
		
		companyExTaxTotal = invoiceLineService.getCompanyExTaxTotal(exTaxTotal, invoice);
		companyInTaxTotal = invoiceLineService.getCompanyExTaxTotal(inTaxTotal, invoice);
		
		response.setValue("exTaxTotal", exTaxTotal);
		response.setValue("inTaxTotal", inTaxTotal);
		response.setValue("companyInTaxTotal", companyInTaxTotal);
		response.setValue("companyExTaxTotal", companyExTaxTotal);

	}
	
	
	public void getProductInformation(ActionRequest request, ActionResponse response) throws AxelorException {

		Context context = request.getContext();
		InvoiceLine invoiceLine = context.asType(InvoiceLine.class);
		
		if(context.getParent().getContextClass() == InvoiceLine.class) {
			context = request.getContext().getParent();
		}
		
		Invoice invoice = this.getInvoice(context);
		Product product = invoiceLine.getProduct();

		if(invoice == null || product == null) { 
			this.resetProductInformation(response);
			return;
		}

		try  {

			boolean isPurchase = invoiceLineService.isPurchase(invoice);

			TaxLine taxLine = invoiceLineService.getTaxLine(invoice, invoiceLine, isPurchase);
			response.setValue("taxLine", taxLine);
			response.setValue("taxRate", taxLine.getValue());
			response.setValue("taxCode", taxLine.getTax().getCode());

			Tax tax = accountManagementService.getProductTax(accountManagementService.getAccountManagement(product, invoice.getCompany()), isPurchase);
            TaxEquiv taxEquiv = Beans.get(FiscalPositionService.class).getTaxEquiv(invoice.getPartner().getFiscalPosition(), tax);
            response.setValue("taxEquiv", taxEquiv);

			BigDecimal price = invoiceLineService.getUnitPrice(invoice, invoiceLine, taxLine, isPurchase);

			response.setValue("productName", invoiceLine.getProduct().getName());
			response.setValue("unit", invoiceLineService.getUnit(invoiceLine.getProduct(), isPurchase));

			// getting correct account for the product
			AccountManagement accountManagement = accountManagementService.getAccountManagement(product, invoice.getCompany());
			Account account = accountManagementService.getProductAccount(accountManagement, isPurchase);
			response.setValue("account", account);

			Map<String, Object> discounts = invoiceLineService.getDiscount(invoice, invoiceLine, price);
			
			if(discounts != null)  {
				response.setValue("discountAmount", discounts.get("discountAmount"));
				response.setValue("discountTypeSelect", discounts.get("discountTypeSelect"));
				if(discounts.get("price") != null)  {
					price = (BigDecimal) discounts.get("price");
				}
			}
			response.setValue("price", price);
		}
		catch(Exception e) {
			response.setFlash(e.getMessage());
			this.resetProductInformation(response);
		}
	}


	public void resetProductInformation(ActionResponse response)  {

		response.setValue("taxLine", null);
		response.setValue("taxEquiv", null);
		response.setValue("taxCode", null);
		response.setValue("taxRate", null);
		response.setValue("productName", null);
		response.setValue("unit", null);
		response.setValue("discountAmount", null);
		response.setValue("discountTypeSelect", null);
		response.setValue("price", null);
		response.setValue("exTaxTotal", null);
		response.setValue("inTaxTotal", null);
		response.setValue("companyInTaxTotal", null);
		response.setValue("companyExTaxTotal", null);

	}


	public void getDiscount(ActionRequest request, ActionResponse response) throws AxelorException {

		Context context = request.getContext();
		InvoiceLine invoiceLine = context.asType(InvoiceLine.class);
		if(context.getParent().getContextClass() == InvoiceLine.class) {
			context = request.getContext().getParent();
		}

		Invoice invoice = this.getInvoice(context);
		
		if(invoice == null || invoiceLine.getProduct() == null) {  return;  }

		try  {
			BigDecimal price = invoiceLine.getPrice();

			Map<String, Object> discounts = invoiceLineService.getDiscount(invoice, invoiceLine, price);
			
			if(discounts != null)  {
				response.setValue("discountAmount", discounts.get("discountAmount"));
				response.setValue("discountTypeSelect", discounts.get("discountTypeSelect"));
				
				if(discounts.get("price") != null)  {
					response.setValue("price", (BigDecimal) discounts.get("price"));
				}
			}
		}
		catch(Exception e) {
			response.setFlash(e.getMessage());
		}
	}

	public void convertUnitPrice(ActionRequest request, ActionResponse response) {

		Context context = request.getContext();
		InvoiceLine invoiceLine = context.asType(InvoiceLine.class);
		if(context.getParent().getContextClass() == InvoiceLine.class) {
			context = request.getContext().getParent();
		}

		Invoice invoice = this.getInvoice(context);
		
		if(invoice == null || invoiceLine.getProduct() == null || !invoiceLineService.unitPriceShouldBeUpdate(invoice, invoiceLine.getProduct())) {  return;  }

		try  {

			BigDecimal price = invoiceLineService.getUnitPrice(invoice, invoiceLine, invoiceLine.getTaxLine(), invoiceLineService.isPurchase(invoice));
			
			Map<String,Object> discounts = invoiceLineService.getDiscount(invoice, invoiceLine, price);
			
			if(discounts != null)  {  
			
				response.setValue("discountAmount", discounts.get("discountAmount"));
				response.setValue("discountTypeSelect", discounts.get("discountTypeSelect"));
				if(discounts.get("price") != null)  {
					price = (BigDecimal) discounts.get("price");
				}
			}
			response.setValue("price", price);

		}
		catch(Exception e)  {
			response.setFlash(e.getMessage());
		}
	}

	
	public void emptyLine(ActionRequest request, ActionResponse response){
		InvoiceLine invoiceLine = request.getContext().asType(InvoiceLine.class);
		if(invoiceLine.getTypeSelect() != InvoiceLineRepository.TYPE_NORMAL){
			Map<String,Object> newInvoiceLine =  Mapper.toMap(new InvoiceLine());
			newInvoiceLine.put("qty", BigDecimal.ZERO);
			newInvoiceLine.put("id", invoiceLine.getId());
			newInvoiceLine.put("version", invoiceLine.getVersion());
			newInvoiceLine.put("typeSelect", invoiceLine.getTypeSelect());
			response.setValues(newInvoiceLine);
		}
	}
	
	public Invoice getInvoice(Context context)  {
		
		Context parentContext = context.getParentContext();
		
		Invoice invoice = parentContext.asType(Invoice.class);
		
		if(!parentContext.getContextClass().toString().equals(Invoice.class.toString())){
			
			InvoiceLine invoiceLine = context.asType(InvoiceLine.class);
			
			invoice = invoiceLine.getInvoice();
		}
		
		return invoice;
	}
}
