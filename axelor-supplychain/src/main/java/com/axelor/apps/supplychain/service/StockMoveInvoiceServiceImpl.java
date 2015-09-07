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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineGeneratorSupplyChain;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class StockMoveInvoiceServiceImpl extends StockMoveRepository implements StockMoveInvoiceService {

	@Inject
	private SaleOrderInvoiceService saleOrderInvoiceService;

	@Inject
	private PurchaseOrderInvoiceService purchaseOrderInvoiceService;

	@Inject
	private InvoiceRepository invoiceRepository;

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Invoice createInvoiceFromSaleOrder(StockMove stockMove, SaleOrder saleOrder) throws AxelorException  {

		if (stockMove.getInvoice() != null
				&& stockMove.getInvoice().getStatusSelect() != InvoiceRepository.STATUS_CANCELED){
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.OUTGOING_STOCK_MOVE_INVOICE_EXISTS), stockMove.getName()), IException.CONFIGURATION_ERROR);
		}
		InvoiceGenerator invoiceGenerator = saleOrderInvoiceService.createInvoiceGenerator(saleOrder);

		Invoice invoice = invoiceGenerator.generate();

		invoiceGenerator.populate(invoice, this.createInvoiceLines(invoice, stockMove.getStockMoveLineList()));

		if (invoice != null) {
			saleOrderInvoiceService.fillInLines(invoice);
			this.extendInternalReference(stockMove, invoice);

			invoiceRepository.save(invoice);

			stockMove.setInvoice(invoice);
			save(stockMove);
		}
		return invoice;

	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Invoice createInvoiceFromPurchaseOrder(StockMove stockMove, PurchaseOrder purchaseOrder) throws AxelorException  {

		if (stockMove.getInvoice() != null
				&& stockMove.getInvoice().getStatusSelect() != InvoiceRepository.STATUS_CANCELED){
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.INCOMING_STOCK_MOVE_INVOICE_EXISTS), stockMove.getName()), IException.CONFIGURATION_ERROR);
		}
		InvoiceGenerator invoiceGenerator = purchaseOrderInvoiceService.createInvoiceGenerator(purchaseOrder);

		Invoice invoice = invoiceGenerator.generate();

		invoiceGenerator.populate(invoice, this.createInvoiceLines(invoice, stockMove.getStockMoveLineList()));

		if (invoice != null) {

			this.extendInternalReference(stockMove, invoice);

			invoiceRepository.save(invoice);

			stockMove.setInvoice(invoice);
			save(stockMove);
		}
		return invoice;
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Map<String,Object> createInvoiceFromMultiOutgoingStockMove(List<StockMove> stockMoveList, PaymentCondition paymentConditionIn, PaymentMode paymentModeIn, Partner contactPartnerIn) throws AxelorException  {

		Currency invoiceCurrency = null;
		Partner invoiceClientPartner = null;
		Company invoiceCompany = null;
		PaymentCondition invoicePaymentCondition = null;
		PaymentMode invoicePaymentMode = null;
		Address invoiceMainInvoicingAddress = null;
		Partner invoiceContactPartner = null;
		PriceList invoicePriceList = null;
		Boolean invoiceInAti = null;

		Map<String,Object> mapResult = new HashMap<String, Object>();

		int count = 1;
		List<StockMove> stockMoveToInvoiceList = new ArrayList<StockMove>();
		String message = "";
		//Check if field constraints are respected
		for (StockMove stockMove : stockMoveList) {
			if (stockMove.getInvoice() != null){
				if (stockMove.getInvoice().getStatusSelect() != StockMoveRepository.STATUS_CANCELED){
					message = String.format(I18n.get(IExceptionMessage.OUTGOING_STOCK_MOVE_INVOICE_EXISTS), stockMove.getName());
					if (mapResult.get("information") != null){
						message = mapResult.get("information") + "<br/>" + message;
					}
					mapResult.put("information", message);
					continue;
				}
			}
			SaleOrder saleOrder = stockMove.getSaleOrder();
			if (count == 1){
				invoiceCurrency = saleOrder.getCurrency();
				invoiceClientPartner = saleOrder.getClientPartner();
				invoiceCompany = saleOrder.getCompany();
				invoicePaymentCondition = saleOrder.getPaymentCondition();
				invoicePaymentMode = saleOrder.getPaymentMode();
				invoiceMainInvoicingAddress = saleOrder.getMainInvoicingAddress();
				invoiceContactPartner = saleOrder.getContactPartner();
				invoicePriceList = saleOrder.getPriceList();
				invoiceInAti = saleOrder.getInAti();
			}else{

				if (invoiceCurrency != null
						&& !invoiceCurrency.equals(saleOrder.getCurrency())){
					invoiceCurrency = null;
				}

				if (invoiceClientPartner != null
						&& !invoiceClientPartner.equals(saleOrder.getClientPartner())){
					invoiceClientPartner = null;

				}

				if (invoiceCompany != null
						&& !invoiceCompany.equals(saleOrder.getCompany())){
					invoiceCompany = null;
				}

				if (invoicePaymentCondition != null
						&& !invoicePaymentCondition.equals(saleOrder.getPaymentCondition())){
					invoicePaymentCondition = null;
				}

				if (invoicePaymentMode != null
						&& !invoicePaymentMode.equals(saleOrder.getPaymentMode())){
					invoicePaymentMode = null;
				}

				if (invoiceMainInvoicingAddress != null
						&& !invoiceMainInvoicingAddress.equals(saleOrder.getMainInvoicingAddress())){
					invoiceMainInvoicingAddress = null;
				}

				if (invoiceContactPartner != null
						&& !invoiceContactPartner.equals(saleOrder.getContactPartner())){
					invoiceContactPartner = null;
				}

				if (invoicePriceList != null
						&& !invoicePriceList.equals(saleOrder.getPriceList())){
					invoicePriceList = null;
				}

				if (invoiceInAti != null
						&& !invoiceInAti.equals(saleOrder.getInAti())){
					invoiceInAti = null;
				}
			}
			stockMoveToInvoiceList.add(stockMove);
			count++;
		}

		if(stockMoveToInvoiceList.isEmpty()){
			return mapResult;
		}

		StringBuilder fieldErrors = new StringBuilder();

		/***
		 * Step 1, check if required and similar fields are correct
		 * The currency, the clientPartner and the company must be the same for all saleOrders linked to stockMoves
		 */
		if (invoiceCurrency == null){
			fieldErrors.append(I18n.get(IExceptionMessage.STOCK_MOVE_MULTI_INVOICE_CURRENCY));
		}
		if (invoiceClientPartner == null){
			if (fieldErrors.length() > 0){
				fieldErrors.append("<br/>");
			}
			fieldErrors.append(I18n.get(IExceptionMessage.STOCK_MOVE_MULTI_INVOICE_CLIENT_PARTNER));
		}
		if (invoiceCompany == null){
			if (fieldErrors.length() > 0){
				fieldErrors.append("<br/>");
			}
			fieldErrors.append(I18n.get(IExceptionMessage.STOCK_MOVE_MULTI_INVOICE_COMPANY_SO));
		}
		if (invoiceInAti == null){
			if (fieldErrors.length() > 0){
				fieldErrors.append("<br/>");
			}
			fieldErrors.append(I18n.get(IExceptionMessage.STOCK_MOVE_MULTI_INVOICE_IN_ATI));
		}

		if (fieldErrors.length() > 0){
			throw new AxelorException(fieldErrors.toString(), IException.CONFIGURATION_ERROR);
		}

		/***
		 * Step 2, check if some fields require a selection from the user
		 * It can happed for the payment condition, the payment mode and the contact partner
		 */

		if (invoicePaymentCondition == null){
			if (paymentConditionIn != null){
				invoicePaymentCondition = paymentConditionIn;
			}else{
				mapResult.put("paymentConditionToCheck", true);
			}
		}

		if (invoicePaymentMode == null){
			if (paymentModeIn != null){
				invoicePaymentMode = paymentModeIn;
			}else{
				mapResult.put("paymentModeToCheck", true);
			}
		}

		if (invoiceContactPartner == null){
			if (contactPartnerIn != null){
				invoiceContactPartner = contactPartnerIn;
			}else{
				mapResult.put("contactPartnerToCheck", true);
				mapResult.put("partnerId", invoiceClientPartner.getId());
			}
		}

		if (!mapResult.isEmpty()){
			return mapResult;
		}

		/***
		 * Step 3, check if some other fields are different and assign a default value
		 *
		 */

		if (invoiceMainInvoicingAddress == null){
			invoiceMainInvoicingAddress = invoiceClientPartner.getMainInvoicingAddress();
		}

		if (invoicePriceList == null){
			invoicePriceList = invoiceClientPartner.getSalePriceList();
		}

		//Concat sequence, internal ref and external ref from all saleOrder
		String numSeq = "";
		String internalRef = "";
		String externalRef = "";
		List<Long> stockMoveIdList = new ArrayList<Long>();
		for (StockMove stockMoveLocal : stockMoveToInvoiceList) {
			if (!numSeq.isEmpty()){
				numSeq += "-";
			}
			numSeq += stockMoveLocal.getSaleOrder().getSaleOrderSeq();

			if (!internalRef.isEmpty()){
				internalRef += "|";
			}
			internalRef += stockMoveLocal.getStockMoveSeq() + ":" + stockMoveLocal.getSaleOrder().getSaleOrderSeq();

			if (!externalRef.isEmpty()){
				externalRef += "|";
			}
			if (stockMoveLocal.getSaleOrder().getExternalReference() != null){
				externalRef += stockMoveLocal.getSaleOrder().getExternalReference();
			}

			stockMoveIdList.add(stockMoveLocal.getId());
		}

		InvoiceGenerator invoiceGenerator = new InvoiceGenerator(InvoiceRepository.OPERATION_TYPE_CLIENT_SALE, invoiceCompany,invoicePaymentCondition,
				invoicePaymentMode, invoiceMainInvoicingAddress, invoiceClientPartner, invoiceContactPartner,
				invoiceCurrency, invoicePriceList, numSeq, externalRef) {

			@Override
			public Invoice generate() throws AxelorException {

				return super.createInvoiceHeader();
			}
		};

		Invoice invoice = invoiceGenerator.generate();
		invoice.setInternalReference(internalRef);

		List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();

		for (StockMove stockMoveLocal : stockMoveToInvoiceList) {
			invoiceLineList.addAll(this.createInvoiceLines(invoice, stockMoveLocal.getStockMoveLineList()));
		}

		invoiceGenerator.populate(invoice, invoiceLineList);

		if (invoice != null) {

			invoiceRepository.save(invoice);
			//Save the link to the invoice for all stockMove
			JPA.all(StockMove.class).filter("self.id IN (:idStockMoveList)").bind("idStockMoveList", stockMoveIdList).update("invoice", invoice);

			mapResult.put("invoiceId", invoice.getId());
		}

		return mapResult;

	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Map<String,Object> createInvoiceFromMultiIncomingStockMove(List<StockMove> stockMoveList, Partner contactPartnerIn) throws AxelorException  {

		Company invoiceCompany = null;
		Partner invoiceSupplierPartner = null;
		Partner invoiceContactPartner = null;
		PriceList invoicePriceList = null;

		Map<String,Object> mapResult = new HashMap<String, Object>();

		int count = 1;
		List<StockMove> stockMoveToInvoiceList = new ArrayList<StockMove>();
		String message = "";
		for (StockMove stockMove : stockMoveList) {
			if (stockMove.getInvoice() != null){
				if (stockMove.getInvoice().getStatusSelect() != StockMoveRepository.STATUS_CANCELED){
					message = String.format(I18n.get(IExceptionMessage.INCOMING_STOCK_MOVE_INVOICE_EXISTS), stockMove.getName());
					if (mapResult.get("information") != null){
						message = mapResult.get("information") + "<br/>" + message;
					}
					mapResult.put("information", message);
					continue;
				}
			}
			PurchaseOrder purchaseOrder = stockMove.getPurchaseOrder();
			if (count == 1){
				invoiceCompany = purchaseOrder.getCompany();
				invoiceSupplierPartner = purchaseOrder.getSupplierPartner();
				invoiceContactPartner = purchaseOrder.getContactPartner();
				invoicePriceList = purchaseOrder.getPriceList();
			}else{

				if (invoiceCompany != null
						&& !invoiceCompany.equals(purchaseOrder.getCompany())){
					invoiceCompany = null;
				}

				if (invoiceSupplierPartner != null
						&& !invoiceSupplierPartner.equals(purchaseOrder.getSupplierPartner())){
					invoiceSupplierPartner = null;
				}

				if (invoiceContactPartner != null
						&& !invoiceContactPartner.equals(purchaseOrder.getContactPartner())){
					invoiceContactPartner = null;
				}

				if (invoicePriceList != null
						&& !invoicePriceList.equals(purchaseOrder.getPriceList())){
					invoicePriceList = null;
				}
			}
			stockMoveToInvoiceList.add(stockMove);
			count++;
		}

		if(stockMoveToInvoiceList.isEmpty()){
			return mapResult;
		}

		StringBuilder fieldErrors = new StringBuilder();

		/***
		 * Step 1, check if required and similar fields are correct
		 * the supplierPartner and the company must be the same for all saleOrders linked to stockMoves
		 */
		if (invoiceSupplierPartner == null){
			fieldErrors.append(IExceptionMessage.STOCK_MOVE_MULTI_INVOICE_SUPPLIER_PARTNER);
		}
		if (invoiceCompany == null){
			if (fieldErrors.length() > 0){
				fieldErrors.append("<br/>");
			}
			fieldErrors.append(I18n.get(IExceptionMessage.STOCK_MOVE_MULTI_INVOICE_COMPANY_PO));
		}

		if (fieldErrors.length() > 0){
			throw new AxelorException(fieldErrors.toString(), IException.CONFIGURATION_ERROR);
		}

		/***
		 * Step 2, check if some fields require a selection from the user
		 * It can happed for the contact partner
		 */

		if (invoiceContactPartner == null){
			if (contactPartnerIn != null){
				invoiceContactPartner = contactPartnerIn;
			}else{
				mapResult.put("contactPartnerToCheck", true);
				mapResult.put("partnerId", invoiceSupplierPartner.getId());
			}
		}

		if (!mapResult.isEmpty()){
			return mapResult;
		}

		/***
		 * Step 3, check if some other fields are different and assign a default value
		 *
		 */

		if (invoicePriceList == null){
			invoicePriceList = invoiceSupplierPartner.getPurchasePriceList();
		}

		//Concat sequence, internal ref and external ref from all saleOrder
		String numSeq = "";
		String externalRef = "";
		List<Long> stockMoveIdList = new ArrayList<Long>();
		for (StockMove stockMoveLocal : stockMoveToInvoiceList) {
			if (!numSeq.isEmpty()){
				numSeq += "-";
			}
			numSeq += stockMoveLocal.getPurchaseOrder().getPurchaseOrderSeq();

			if (!externalRef.isEmpty()){
				externalRef += "|";
			}
			if (stockMoveLocal.getPurchaseOrder().getExternalReference() != null){
				externalRef += stockMoveLocal.getPurchaseOrder().getExternalReference();
			}

			stockMoveIdList.add(stockMoveLocal.getId());
		}

		InvoiceGenerator invoiceGenerator = new InvoiceGenerator(InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE, invoiceCompany, invoiceSupplierPartner,
				invoiceContactPartner, invoicePriceList, numSeq, externalRef) {

			@Override
			public Invoice generate() throws AxelorException {

				return super.createInvoiceHeader();
			}
		};

		Invoice invoice = invoiceGenerator.generate();

		List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();

		for (StockMove stockMoveLocal : stockMoveToInvoiceList) {
			invoiceLineList.addAll(this.createInvoiceLines(invoice, stockMoveLocal.getStockMoveLineList()));
		}

		invoiceGenerator.populate(invoice, invoiceLineList);

		if (invoice != null) {

			invoiceRepository.save(invoice);
			//Save the link to the invoice for all stockMove
			JPA.all(StockMove.class).filter("self.id IN (:idStockMoveList)").bind("idStockMoveList", stockMoveIdList).update("invoice", invoice);

			mapResult.put("invoiceId", invoice.getId());
		}

		return mapResult;

	}

	@Override
	public Invoice extendInternalReference(StockMove stockMove, Invoice invoice)  {

		invoice.setInternalReference(stockMove.getStockMoveSeq()+":"+invoice.getInternalReference());

		return invoice;
	}


	@Override
	public List<InvoiceLine> createInvoiceLines(Invoice invoice, List<StockMoveLine> stockMoveLineList) throws AxelorException {

		List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();

		for (StockMoveLine stockMoveLine : stockMoveLineList) {
			if (stockMoveLine.getRealQty().compareTo(BigDecimal.ZERO) == 1){
				invoiceLineList.addAll(this.createInvoiceLine(invoice, stockMoveLine));
				//Depending on stockMove type
				if (stockMoveLine.getSaleOrderLine() != null){
					stockMoveLine.getSaleOrderLine().setInvoiced(true);
				}else{
					stockMoveLine.getPurchaseOrderLine().setInvoiced(true);
				}
			}
		}

		return invoiceLineList;
	}

	@Override
	public List<InvoiceLine> createInvoiceLine(Invoice invoice, StockMoveLine stockMoveLine) throws AxelorException {

		Product product = stockMoveLine.getProduct();

		if (product == null) {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_INVOICE_1), stockMoveLine.getStockMove().getStockMoveSeq()), IException.CONFIGURATION_ERROR);
		}

		InvoiceLineGenerator invoiceLineGenerator = new InvoiceLineGeneratorSupplyChain(invoice, product, product.getName(),
				stockMoveLine.getDescription(), stockMoveLine.getRealQty(), stockMoveLine.getUnit(),
				InvoiceLineGenerator.DEFAULT_SEQUENCE, false, stockMoveLine.getSaleOrderLine(), stockMoveLine.getPurchaseOrderLine(), stockMoveLine.getStockMove())  {
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
