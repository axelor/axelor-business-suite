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
package com.axelor.apps.supplychain.service.invoice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceServiceImpl;
import com.axelor.apps.account.service.invoice.factory.CancelFactory;
import com.axelor.apps.account.service.invoice.factory.ValidateFactory;
import com.axelor.apps.account.service.invoice.factory.VentilateFactory;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.service.alarm.AlarmEngineService;
import com.axelor.apps.sale.db.AdvancePayment;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

public class InvoiceServiceSupplychainImpl extends InvoiceServiceImpl implements InvoiceServiceSupplychain {

	@Inject
	public InvoiceServiceSupplychainImpl(ValidateFactory validateFactory, VentilateFactory ventilateFactory,
			CancelFactory cancelFactory, AlarmEngineService<Invoice> alarmEngineService, InvoiceRepository invoiceRepo,
			AppAccountService appAccountService) {
		super(validateFactory, ventilateFactory, cancelFactory, alarmEngineService, invoiceRepo, appAccountService);
	}

	@Override
	public Set<Invoice> getDefaultAdvancePaymentInvoice(Invoice invoice) throws AxelorException {
		SaleOrder saleOrder = invoice.getSaleOrder();
		Company company = invoice.getCompany();
		Currency currency = invoice.getCurrency();
	    if (company == null || saleOrder == null) {
	    	return super.getDefaultAdvancePaymentInvoice(invoice);
		}
		boolean generateMoveForInvoicePayment = Beans
				.get(AccountConfigService.class)
				.getAccountConfig(company)
				.getGenerateMoveForInvoicePayment();

		String filter = writeGeneralFilterForAdvancePayment();
		filter += " AND self.saleOrder = :_saleOrder";

		if (!generateMoveForInvoicePayment) {
			filter += " AND self.currency = :_currency";
		}
		Query<Invoice> query = Beans.get(InvoiceRepository.class).all()
				.filter(filter)
				.bind("_status", InvoiceRepository.STATUS_VALIDATED)
				.bind("_operationSubType",
						InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE)
                .bind("_saleOrder", saleOrder);

		if (!generateMoveForInvoicePayment) {
		    if (currency == null) {
		    	return new HashSet<>();
			}
			query.bind("_currency", currency);
		}

		Set<Invoice> advancePaymentInvoices = new HashSet<>(
		        query.fetch()
		);
		filterAdvancePaymentInvoice(invoice, advancePaymentInvoices);
		return advancePaymentInvoices;
    }

	@Override
	public List<MoveLine> getMoveLinesFromSOAdvancePayments(Invoice invoice) {
		//search sale order in the invoice
        SaleOrder saleOrder = invoice.getSaleOrder();
        //search sale order in invoice lines
		List<SaleOrder> saleOrderList = invoice.getInvoiceLineList()
				.stream()
				.map(InvoiceLine::getSaleOrder)
				.collect(Collectors.toList());
		saleOrderList.add(saleOrder);

		//remove null value and duplicates
		saleOrderList = saleOrderList.stream()
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());

		if (saleOrderList.isEmpty()) {
			return new ArrayList<>();
		} else {
			//get move lines from sale order
			return saleOrderList
					.stream()
					.flatMap(saleOrder1 -> saleOrder1.getAdvancePaymentList().stream())
					.filter(Objects::nonNull)
					.distinct()
					.map(AdvancePayment::getMove)
					.filter(Objects::nonNull)
					.distinct()
					.flatMap(move -> move.getMoveLineList().stream())
					.collect(Collectors.toList());
		}
	}
	
	@Override
	public List<InvoiceLine> addSubLines(List<InvoiceLine> invoiceLine) {
		
		if (invoiceLine == null) {
            return invoiceLine;
        }
        
        List<InvoiceLine> lines = new ArrayList<InvoiceLine>();
        lines.addAll(invoiceLine);
        for (InvoiceLine line : lines) {
            if (line.getSubLineList() == null) {
                continue;
            }
            for (InvoiceLine subLine : line.getSubLineList()) {
                if (subLine.getInvoice() == null) {
                	invoiceLine.add(subLine);
                }
            }
        }
		return invoiceLine;
	}
	
	@Override
	public List<InvoiceLine> removeSubLines(List<InvoiceLine> invoiceLines) {
		
		if (invoiceLines == null) {
	        return invoiceLines;
	    }
	    
	    
	    List<InvoiceLine> subLines = new ArrayList<InvoiceLine>();
	    for (InvoiceLine packLine : invoiceLines) {
	        if (packLine.getTypeSelect() == 2 && packLine.getSubLineList() != null) {
	            packLine.getSubLineList().removeIf(it->it.getId() != null && !invoiceLines.contains(it));
	            packLine.setTotalPack(packLine.getSubLineList()
	                    .stream().map(it->it.getExTaxTotal()).reduce(BigDecimal.ZERO, BigDecimal::add));
	            subLines.addAll(packLine.getSubLineList());
	        }
	    }
	    Iterator<InvoiceLine> lines = invoiceLines.iterator();

	    while (lines.hasNext()) {
	    	InvoiceLine subLine = lines.next();
	        if (subLine.getId() != null 
	                && subLine.getParentLine() != null
	                && !subLines.contains(subLine)) {
	                lines.remove();
	        }
	    }

	    return invoiceLines;
	}
}
