package com.axelor.apps.supplychain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.supplychain.db.Timetable;
import com.axelor.apps.supplychain.db.repo.TimetableRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceGeneratorSupplyChain;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineGeneratorSupplyChain;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;

public class TimetableService extends TimetableRepository{

	public void updateTimetable(SaleOrder saleOrder){
		if(saleOrder.getTimetableList() != null && !saleOrder.getTimetableList().isEmpty()){
			List<Timetable> timetableList = saleOrder.getTimetableList();
			BigDecimal amountInvoiced = saleOrder.getAmountInvoiced();
			BigDecimal sum = BigDecimal.ZERO;
			for (Timetable timetable : timetableList) {
				sum = sum.add(timetable.getAmount());
				if(sum.compareTo(amountInvoiced) > 0){
					timetable.setAmountToInvoice(sum.subtract(amountInvoiced));
				}
				else{
					timetable.setAmountToInvoice(BigDecimal.ZERO);
				}
			}
		}
	}
	
	
	public void updateTimetable(PurchaseOrder purchaseOrder){
		if(purchaseOrder.getTimetableList() != null && !purchaseOrder.getTimetableList().isEmpty()){
			List<Timetable> timetableList = purchaseOrder.getTimetableList();
			BigDecimal amountInvoiced = purchaseOrder.getAmountInvoiced();
			BigDecimal sum = BigDecimal.ZERO;
			for (Timetable timetable : timetableList) {
				sum = sum.add(timetable.getAmount());
				if(sum.compareTo(amountInvoiced) > 0){
					timetable.setAmountToInvoice(sum.subtract(amountInvoiced));
				}
				else{
					timetable.setAmountToInvoice(BigDecimal.ZERO);
				}
			}
		}
	}
	
	public Invoice generateInvoice(Timetable timetable) throws AxelorException{
		if(timetable.getProduct() == null){
			throw new AxelorException(I18n.get("Select a product"), IException.CONFIGURATION_ERROR);
		}
		if(timetable.getUnit() == null){
			throw new AxelorException(I18n.get("Select an unit"), IException.CONFIGURATION_ERROR);
		}
		Invoice invoice = this.createInvoice(timetable);
		Beans.get(InvoiceRepository.class).save(invoice);
		return invoice;
	}
	
	public Invoice createInvoice(Timetable timetable) throws AxelorException{
		SaleOrder saleOrder = timetable.getSaleOrder();
		PurchaseOrder purchaseOrder = timetable.getPurchaseOrder();
		if(saleOrder != null){
			if(saleOrder.getCurrency() == null)  {
				throw new AxelorException(String.format(I18n.get(IExceptionMessage.SO_INVOICE_6), saleOrder.getSaleOrderSeq()), IException.CONFIGURATION_ERROR);
			}
			InvoiceGenerator invoiceGenerator = new InvoiceGeneratorSupplyChain(InvoiceRepository.OPERATION_TYPE_CLIENT_SALE, saleOrder.getCompany(),saleOrder.getPaymentCondition(),
					saleOrder.getPaymentMode(), saleOrder.getMainInvoicingAddress(), saleOrder.getClientPartner(), saleOrder.getContactPartner(),
					saleOrder.getCurrency(), saleOrder.getPriceList(), saleOrder.getSaleOrderSeq(), saleOrder.getExternalReference(), saleOrder) {

				@Override
				public Invoice generate() throws AxelorException {

					return super.createInvoiceHeader();
				}
			};
			Invoice invoice = invoiceGenerator.generate();
			invoiceGenerator.populate(invoice, this.createInvoiceLine(invoice, timetable));
			this.fillInLines(invoice);
			return invoice;
		}
		
		if(purchaseOrder != null){
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
			
			Invoice invoice = invoiceGenerator.generate();
			invoiceGenerator.populate(invoice, this.createInvoiceLine(invoice, timetable));
			return invoice;
		}
		
		return null;
	}
	
	public void fillInLines(Invoice invoice){
		List<InvoiceLine> invoiceLineList = invoice.getInvoiceLineList();
		for (InvoiceLine invoiceLine : invoiceLineList) {
			invoiceLine.setSaleOrder(invoice.getSaleOrder());
		}
	}
	
	public List<InvoiceLine> createInvoiceLine(Invoice invoice, Timetable timetable) throws AxelorException  {

		Product product = timetable.getProduct();

		InvoiceLineGenerator invoiceLineGenerator = new InvoiceLineGeneratorSupplyChain(invoice, product, timetable.getProductName(),
				timetable.getComments(), timetable.getQty(), timetable.getUnit(),
				1, false, null, null, null)  {

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
}
