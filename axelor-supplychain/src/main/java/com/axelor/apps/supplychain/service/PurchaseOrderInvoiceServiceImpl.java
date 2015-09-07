/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2014 Axelor (<http://axelor.com>).
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
package com.axelor.apps.supplychain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Query;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceGeneratorSupplyChain;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineGeneratorSupplyChain;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class PurchaseOrderInvoiceServiceImpl extends PurchaseOrderRepository implements PurchaseOrderInvoiceService {

	@Inject
	private InvoiceService invoiceService;

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Invoice generateInvoice(PurchaseOrder purchaseOrder) throws AxelorException  {

		Invoice invoice = this.createInvoice(purchaseOrder);
		invoiceService.save(invoice);
		invoiceService.setDraftSequence(invoice);

		if(invoice != null) {
			purchaseOrder.setInvoice(invoice);
			save(purchaseOrder);
		}
		return invoice;
	}

	@Override
	public Invoice createInvoice(PurchaseOrder purchaseOrder) throws AxelorException{

		InvoiceGenerator invoiceGenerator = this.createInvoiceGenerator(purchaseOrder);

		Invoice invoice = invoiceGenerator.generate();

		invoiceGenerator.populate(invoice, this.createInvoiceLines(invoice, purchaseOrder.getPurchaseOrderLineList()));
		return invoice;
	}

	@Override
	public InvoiceGenerator createInvoiceGenerator(PurchaseOrder purchaseOrder) throws AxelorException  {

		if(purchaseOrder.getCurrency() == null)  {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.PO_INVOICE_1), purchaseOrder.getPurchaseOrderSeq()), IException.CONFIGURATION_ERROR);
		}

		InvoiceGenerator invoiceGenerator = new InvoiceGeneratorSupplyChain(InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE, purchaseOrder.getCompany(), purchaseOrder.getSupplierPartner(),
				purchaseOrder.getContactPartner(), purchaseOrder.getPriceList(), purchaseOrder.getPurchaseOrderSeq(), purchaseOrder.getExternalReference(), purchaseOrder) {

			@Override
			public Invoice generate() throws AxelorException {

				return super.createInvoiceHeader();
			}
		};

		return invoiceGenerator;
	}

	@Override
	public List<InvoiceLine> createInvoiceLines(Invoice invoice, List<PurchaseOrderLine> purchaseOrderLineList) throws AxelorException  {

		List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();

		for(PurchaseOrderLine purchaseOrderLine : purchaseOrderLineList) {

			//Lines of subscription type are invoiced directly from purchase order line or from the subscription batch
			if (!ProductRepository.PRODUCT_TYPE_SUBSCRIPTABLE.equals(purchaseOrderLine.getProduct().getProductTypeSelect())){
				invoiceLineList.addAll(this.createInvoiceLine(invoice, purchaseOrderLine));
				purchaseOrderLine.setInvoiced(true);
			}
		}
		return invoiceLineList;
	}

	@Override
	public List<InvoiceLine> createInvoiceLine(Invoice invoice, PurchaseOrderLine purchaseOrderLine) throws AxelorException  {

		Product product = purchaseOrderLine.getProduct();

		InvoiceLineGeneratorSupplyChain invoiceLineGenerator = new InvoiceLineGeneratorSupplyChain(invoice, product, purchaseOrderLine.getProductName(),
				purchaseOrderLine.getDescription(), purchaseOrderLine.getQty(), purchaseOrderLine.getUnit(),
				purchaseOrderLine.getSequence(), false, null, purchaseOrderLine, null)  {
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

	@Override
	public BigDecimal getAmountInvoiced(PurchaseOrder purchaseOrder){
		return this.getAmountInvoiced(purchaseOrder, null, false);
	}

	/**
	 * Return the remaining amount to invoice for the purchaseOrder in parameter
	 *
	 * @param purchaseOrder
	 *
	 * @param currentInvoiceId
	 * In the case of invoice ventilation or cancellation, the invoice status isn't modify in database but it will be integrated in calculation
	 * For ventilation, the invoice should be integrated in calculation
	 * For cancellation,  the invoice shouldn't be integrated in calculation
	 *
	 * @param includeInvoice
	 * To know if the invoice should be or not integrated in calculation
	 */
	@Override
	public BigDecimal getAmountInvoiced(PurchaseOrder purchaseOrder, Long currentInvoiceId, boolean includeInvoice){

		Query q = null;
		String query;
		query = "SELECT SUM(self.companyExTaxTotal)"
				+ " FROM InvoiceLine as self"
				+ " WHERE ( (self.purchaseOrderLine.id IN (SELECT id FROM PurchaseOrderLine WHERE purchaseOrder.id = :purchaseOrderId)"
							+ " AND self.invoice.purchaseOrder IS NULL)"
						+ " OR self.invoice.purchaseOrder.id = :purchaseOrderId )"
					+ " AND self.invoice.statusSelect = :statusVentilated";
		if (currentInvoiceId != null){
			if (includeInvoice){
				query += " OR self.invoice.id = :invoiceId";
			}else{
				query += " AND self.invoice.id <> :invoiceId";
			}
		}
		q = JPA.em().createQuery(query, BigDecimal.class);

		q.setParameter("purchaseOrderId", purchaseOrder.getId());
		q.setParameter("statusVentilated", InvoiceRepository.STATUS_VENTILATED);
		if (currentInvoiceId != null){
			q.setParameter("invoiceId", currentInvoiceId);
		}

		BigDecimal invoicedAmount = (BigDecimal) q.getSingleResult();
		if(invoicedAmount != null){
			if (!purchaseOrder.getCurrency().equals(purchaseOrder.getCompany().getCurrency())){
				BigDecimal rate = invoicedAmount.divide(purchaseOrder.getCompanyExTaxTotal(), 4, RoundingMode.HALF_UP);
				invoicedAmount = purchaseOrder.getExTaxTotal().multiply(rate);
			}
		}

		return invoicedAmount;

	}

}
