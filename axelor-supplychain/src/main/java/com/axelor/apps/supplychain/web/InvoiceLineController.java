/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
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
package com.axelor.apps.supplychain.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.generator.line.InvoiceLineManagement;
import com.axelor.exception.AxelorException;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class InvoiceLineController {
	
	@Inject
	private InvoiceLineRepository invoiceLineRepo;
	
	@Inject
	private InvoiceLineService invoiceLineService;

	
	public void getProductPrice(ActionRequest request, ActionResponse response) {
		
		Context context = request.getContext();
		InvoiceLine invoiceLine = context.asType(InvoiceLine.class);
		Integer parentPackPriceSelect = (Integer) context.getParent().get("packPriceSelect");
		
		if(invoiceLine.getPackPriceSelect() == InvoiceLineRepository.SUBLINE_PRICE_ONLY && invoiceLine.getTypeSelect() == InvoiceLineRepository.TYPE_PACK) {
			response.setValue("price", 0.00);
		} else if (parentPackPriceSelect != null){
			if(invoiceLine.getIsSubLine() != null) {
				if (parentPackPriceSelect == InvoiceLineRepository.PACK_PRICE_ONLY && invoiceLine.getIsSubLine()) {
					response.setValue("price", 0.00);
				}
			}
		}
	}
	
	public List<InvoiceLine> updateQty(List<InvoiceLine> invoiceLines, BigDecimal oldKitQty, BigDecimal newKitQty, Invoice invoice) throws AxelorException {
		
		BigDecimal qty = BigDecimal.ZERO;
		BigDecimal exTaxTotal = BigDecimal.ZERO;
		BigDecimal companyExTaxTotal = BigDecimal.ZERO;
		BigDecimal inTaxTotal = BigDecimal.ZERO;
		BigDecimal companyInTaxTotal = BigDecimal.ZERO;
		BigDecimal priceDiscounted = BigDecimal.ZERO;
		BigDecimal taxRate = BigDecimal.ZERO;
		
		if(invoiceLines != null) {
			if(newKitQty.compareTo(BigDecimal.ZERO) != 0) {
				for(InvoiceLine line : invoiceLines) {
					qty = (line.getQty().divide(oldKitQty, 2, RoundingMode.HALF_EVEN)).multiply(newKitQty);
					priceDiscounted = invoiceLineService.computeDiscount(line,invoice);
					
					if(line.getTaxLine() != null)  {
						taxRate = line.getTaxLine().getValue();
					}
					
					if(!invoice.getInAti()) {
						exTaxTotal = InvoiceLineManagement.computeAmount(qty, invoiceLineService.computeDiscount(line,invoice));
						inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
					} else {
						inTaxTotal = InvoiceLineManagement.computeAmount(qty, invoiceLineService.computeDiscount(line,invoice));
						exTaxTotal = inTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
					}
					
					companyExTaxTotal = invoiceLineService.getCompanyExTaxTotal(exTaxTotal, invoice);
					companyInTaxTotal = invoiceLineService.getCompanyExTaxTotal(inTaxTotal, invoice);
					
					line.setQty(qty.setScale(2, RoundingMode.HALF_EVEN));
					line.setExTaxTotal(exTaxTotal);
					line.setCompanyExTaxTotal(companyExTaxTotal);
					line.setInTaxTotal(inTaxTotal);
					line.setCompanyInTaxTotal(companyInTaxTotal);
					line.setPriceDiscounted(priceDiscounted);
					line.setTaxRate(taxRate);
				}
			} else {
				for(InvoiceLine line : invoiceLines) {
					line.setQty(qty.setScale(2, RoundingMode.HALF_EVEN));
				}
			}
		}
		
		return invoiceLines;
	}
	
	public void updateSubLineQty(ActionRequest request, ActionResponse response) throws AxelorException {
		
		InvoiceLine packLine = request.getContext().asType(InvoiceLine.class);
		BigDecimal oldKitQty = BigDecimal.ONE;
		BigDecimal newKitQty = BigDecimal.ZERO;
		List<InvoiceLine> subLines = null;
		
		Context context = request.getContext();
		
		if(context.getParent().getContextClass() == InvoiceLine.class) {
			context = request.getContext().getParent();
		}
		
		Invoice invoice = this.getInvoice(context);
		
		if(packLine.getOldQty().compareTo(BigDecimal.ZERO) == 0) {
			if(packLine.getId() !=null) {
				InvoiceLine line = invoiceLineRepo.find(packLine.getId());
				if(line.getQty().compareTo(BigDecimal.ZERO) != 0) {
					oldKitQty = line.getQty();
				}
			}
		} else {	
			oldKitQty = packLine.getOldQty();
		}
			
		if(packLine.getQty().compareTo(BigDecimal.ZERO) != 0) {
			newKitQty = packLine.getQty();
		} 
		
		if(packLine.getTypeSelect() == InvoiceLineRepository.TYPE_PACK) {
			subLines = this.updateQty(packLine.getSubLineList(), oldKitQty, newKitQty, invoice);
		}
		
		response.setValue("oldQty", newKitQty);
		response.setValue("subLineList", subLines);
	}
	
	public void resetSubLines(ActionRequest request, ActionResponse response) {
		
		InvoiceLine packLine = request.getContext().asType(InvoiceLine.class);
		List<InvoiceLine> subLines = packLine.getSubLineList();
		
		if(subLines != null) {
			for(InvoiceLine line : subLines) {
				line.setPrice(BigDecimal.ZERO);
				line.setPriceDiscounted(BigDecimal.ZERO);
				line.setExTaxTotal(BigDecimal.ZERO);
				line.setInTaxTotal(BigDecimal.ZERO);
			}
		}
		response.setValue("subLineList", subLines);
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
