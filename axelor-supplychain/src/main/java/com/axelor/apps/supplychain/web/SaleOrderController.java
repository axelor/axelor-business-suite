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
package com.axelor.apps.supplychain.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.supplychain.db.Subscription;
import com.axelor.apps.supplychain.db.repo.SubscriptionRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.SaleOrderPurchaseService;
import com.axelor.apps.supplychain.service.SaleOrderServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.SaleOrderStockService;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.axelor.team.db.Team;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class SaleOrderController{
	
	@Inject
	private SaleOrderServiceSupplychainImpl saleOrderServiceSupplychain;

	@Inject
	private SaleOrderRepository saleOrderRepo;

	@Inject
	protected AppSupplychainService appSupplychainService;

	@Inject
	private SaleOrderInvoiceServiceImpl saleOrderInvoiceServiceImpl;

	public void createStockMove(ActionRequest request, ActionResponse response) throws AxelorException {

		SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

		if(saleOrder.getId() != null) {

			SaleOrderStockService saleOrderStockService = Beans.get(SaleOrderStockService.class);
			StockMove stockMove = saleOrderStockService.createStocksMovesFromSaleOrder(saleOrderRepo.find(saleOrder.getId()));
			
			if(stockMove != null)  {
				this.generateStockMoveLineParentLine(stockMove.getStockMoveLineList());
				response.setView(ActionView
					.define(I18n.get("Stock move"))
					.model(StockMove.class.getName())
					.add("grid", "stock-move-grid")
					.add("form", "stock-move-form")
					.param("forceEdit", "true")
					.context("_showRecord", String.valueOf(stockMove.getId())).map());
			}
			else  {
				response.setFlash(I18n.get(IExceptionMessage.SO_NO_DELIVERY_STOCK_MOVE_TO_GENERATE));
			}
		}
	}
	
	@Transactional
	public void generateStockMoveLineParentLine(List<StockMoveLine> stockMoveLines) {
		
		for(StockMoveLine line : stockMoveLines) {
			if(line.getSaleOrderLine() != null) {
				line.setPackPriceSelect(line.getSaleOrderLine().getPackPriceSelect());
				StockMoveLine parentStockMoveLine = Beans.get(StockMoveLineRepository.class).all().filter("self.saleOrderLine = ?", line.getSaleOrderLine().getParentLine()).fetchOne();
				line.setParentLine(parentStockMoveLine);
			}
		}
	}

	public void getLocation(ActionRequest request, ActionResponse response) {

		SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

		if(saleOrder != null) {

			StockLocation stockLocation = Beans.get(StockLocationService.class).getLocation(saleOrder.getCompany());

			if(stockLocation != null) {
				response.setValue("stockLocation", stockLocation);
			}
		}
	}


	@SuppressWarnings("rawtypes")
	public void generatePurchaseOrdersFromSelectedSOLines(ActionRequest request, ActionResponse response) {

		SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

		try {
			if (saleOrder.getId() != null) {

				if (request.getContext().get("supplierPartnerSelect") != null) {
					Partner partner = JPA.em().find(Partner.class, new Long((Integer) ((Map) request.getContext().get("supplierPartnerSelect")).get("id")));
					List<Long> saleOrderLineIdSelected = new ArrayList<>();
					String saleOrderLineIdSelectedStr = (String) request.getContext().get("saleOrderLineIdSelected");
					for (String saleOrderId : saleOrderLineIdSelectedStr.split(",")) {
						saleOrderLineIdSelected.add(new Long(saleOrderId));
					}
					List<SaleOrderLine> saleOrderLinesSelected = JPA.all(SaleOrderLine.class).filter("self.id IN (:saleOderLineIdList)").bind("saleOderLineIdList", saleOrderLineIdSelected).fetch();
					PurchaseOrder purchaseOrder = Beans.get(SaleOrderPurchaseService.class).createPurchaseOrder(partner, saleOrderLinesSelected, saleOrderRepo.find(saleOrder.getId()));
					response.setView(ActionView
							.define(I18n.get("Purchase order"))
							.model(PurchaseOrder.class.getName())
							.add("form", "purchase-order-form")
							.param("forceEdit", "true")
							.context("_showRecord", String.valueOf(purchaseOrder.getId()))
							.map());
					response.setCanClose(true);
				} else {
					Partner supplierPartner = null;
					List<Long> saleOrderLineIdSelected = new ArrayList<>();

					//Check if supplier partners of each sale order line are the same. If it is, send the partner id to view to load this partner by default into select
					for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
						if (saleOrderLine.isSelected()) {
							if (supplierPartner == null) {
								supplierPartner = saleOrderLine.getSupplierPartner();
							} else {
								if (!supplierPartner.equals(saleOrderLine.getSupplierPartner())) {
									supplierPartner = null;
									break;
								}
							}
							saleOrderLineIdSelected.add(saleOrderLine.getId());
						}
					}

					if (saleOrderLineIdSelected.isEmpty()) {
						response.setFlash(I18n.get(IExceptionMessage.SO_LINE_PURCHASE_AT_LEAST_ONE));
					} else {
						response.setView(ActionView
								.define("SaleOrder")
								.model(SaleOrder.class.getName())
								.add("form", "sale-order-generate-po-select-supplierpartner-form")
								.param("popup", "true")
								.param("show-toolbar", "false")
								.param("show-confirm", "false")
								.param("popup-save", "false")
								.param("forceEdit", "true")
								.context("_showRecord", String.valueOf(saleOrder.getId()))
								.context("supplierPartnerId", ((supplierPartner != null) ? String.valueOf(supplierPartner.getId()) : "NULL"))
								.context("saleOrderLineIdSelected", Joiner.on(",").join(saleOrderLineIdSelected))
								.map());
					}
				}

			}
		} catch (Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	/**
	 * Called from the sale order invoicing wizard.
	 * Call {@link com.axelor.apps.supplychain.service.SaleOrderInvoiceService#generateInvoice }
     * Return to the view the generated invoice.
	 * @param request
	 * @param response
	 */
	@SuppressWarnings(value="unchecked")
	public void generateInvoice(ActionRequest request, ActionResponse response)  {

		Context context = request.getContext();
		try {
			SaleOrder saleOrder = context.asType(SaleOrder.class);
			int operationSelect = Integer.parseInt(context.get("operationSelect").toString());
			boolean isPercent = (Boolean) context.getOrDefault("isPercent", false);
			BigDecimal amountToInvoice = new BigDecimal(
						context.getOrDefault("amountToInvoice", "0").toString()
				);
			Map<Long, BigDecimal> qtyToInvoiceMap = new HashMap<>();

			List<Map<String, Object>> saleOrderLineListContext;
			saleOrderLineListContext = (List<Map<String,Object>>)
					request.getRawContext().get("saleOrderLineList");
			for (Map<String, Object> map : saleOrderLineListContext ) {
				if (map.get("amountToInvoice") != null) {
					BigDecimal qtyToInvoiceItem = new BigDecimal(
							map.get("amountToInvoice").toString()
					);
					if (qtyToInvoiceItem.compareTo(BigDecimal.ZERO) != 0) {
						Long SOlineId = new Long((Integer) map.get("id"));
						qtyToInvoiceMap.put(SOlineId, qtyToInvoiceItem);
					}
				}
			}

			saleOrder = saleOrderRepo.find(saleOrder.getId());

			Invoice invoice = saleOrderInvoiceServiceImpl.generateInvoice(
							saleOrder, operationSelect, amountToInvoice, isPercent,
							qtyToInvoiceMap
					);

			if(invoice != null)  {
				this.generateInvoiceLineParentLine(invoice.getInvoiceLineList());
				response.setCanClose(true);
				response.setView(ActionView
						.define(I18n.get("Invoice generated"))
						.model(Invoice.class.getName())
						.add("form", "invoice-form")
						.add("grid", "invoice-grid")
						.context("_showRecord",String.valueOf(invoice.getId()))
						.context("_operationTypeSelect", InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)
						.context("todayDate", Beans.get(AppSupplychainService.class).getTodayDate())
						.map());
			}
		}
		catch(Exception e)  { TraceBackService.trace(response, e); }
	}
	
	@Transactional
	public void generateInvoiceLineParentLine(List<InvoiceLine> invoiceLine) {
		
		for(InvoiceLine line : invoiceLine) {
			if(line.getSaleOrderLine() != null) {
				line.setPackPriceSelect(line.getSaleOrderLine().getPackPriceSelect());
				line.setTotalPack(line.getSaleOrderLine().getTotalPack());
				InvoiceLine parentInvoiceLine = Beans.get(InvoiceLineRepository.class).all().filter("self.saleOrderLine = ?", line.getSaleOrderLine().getParentLine()).fetchOne();
				line.setParentLine(parentInvoiceLine);
			}
		}
	}
	
	public void generateInvoiceFromPopup(ActionRequest request, ActionResponse response)  {

		 String saleOrderId = request.getContext().get("_id").toString();

		try {

			SaleOrder saleOrder = saleOrderRepo.find( Long.valueOf(saleOrderId) );
			//Check if at least one row is selected. If yes, then invoiced only the selected rows, else invoiced all rows
			List<Long> saleOrderLineIdSelected = new ArrayList<>();
			for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
				if (saleOrderLine.isSelected()){
					saleOrderLineIdSelected.add(saleOrderLine.getId());
				}
			}
			 	
			Invoice invoice = null;

			if (!saleOrderLineIdSelected.isEmpty()){
				List<SaleOrderLine> saleOrderLinesSelected = JPA.all(SaleOrderLine.class).filter("self.id IN (:saleOderLineIdList)").bind("saleOderLineIdList", saleOrderLineIdSelected).fetch();
				invoice = saleOrderInvoiceServiceImpl.generateInvoice(saleOrder, saleOrderLinesSelected);
			}else{
				invoice = saleOrderInvoiceServiceImpl.generateInvoice(saleOrder);
			}

			if(invoice != null)  {
				
				response.setCanClose(true);

				response.setFlash(I18n.get(IExceptionMessage.PO_INVOICE_2));
				response.setView(ActionView
		            .define(I18n.get("Invoice generated"))
		            .model(Invoice.class.getName())
		            .add("form", "invoice-form")
		            .add("grid", "invoice-grid")
		            .context("_showRecord",String.valueOf(invoice.getId()))
		            .map());
			}
		}
		catch(Exception e)  { TraceBackService.trace(response, e); }
	}

	public void getSubscriptionSaleOrdersToInvoice(ActionRequest request, ActionResponse response) {

		List<Subscription> subscriptionList = Beans.get(SubscriptionRepository.class).all().filter("self.invoiced = false AND self.invoicingDate <= ?1", appSupplychainService.getTodayDate()).fetch();
		List<Long> listId = new ArrayList<>();
		for (Subscription subscription : subscriptionList) {
			listId.add(subscription.getSaleOrderLine().getSaleOrder().getId());
		}
		if (listId.isEmpty()) {
			TraceBackService.trace(response, new AxelorException(IException.CONFIGURATION_ERROR, I18n.get("No Subscription to Invoice")));
		}
		else {
			String domain = "self.id in ("+Joiner.on(",").join(listId)+")";
			if (listId.size() == 1) {
				domain = "self.id = "+listId.get(0);
			}
			response.setView(ActionView
		            .define(I18n.get("Subscription Sale orders"))
		            .model(SaleOrder.class.getName())
		            .add("grid", "sale-order-subscription-grid")
		            .add("form", "sale-order-form")
		            .domain("self.id = "+listId.get(0))
		            .map());
		}
		response.setView(ActionView
	            .define(I18n.get("Subscription Sale orders"))
	            .model(SaleOrder.class.getName())
	            .add("grid", "sale-order-subscription-grid")
	            .add("form", "sale-order-form")
	            .domain("self.id in ("+Joiner.on(",").join(listId)+")")
	            .map());
	}

	@SuppressWarnings("unchecked")
	public void invoiceSubscriptions(ActionRequest request, ActionResponse response) throws AxelorException{
		List<Integer> listSelectedSaleOrder = (List<Integer>) request.getContext().get("_ids");
		if(listSelectedSaleOrder != null){
			SaleOrder saleOrder = null;
			List<Long> listInvoiceId = new ArrayList<>();
			for (Integer integer : listSelectedSaleOrder) {
				saleOrder = saleOrderRepo.find(integer.longValue());
				Invoice invoice = saleOrderInvoiceServiceImpl.generateSubcriptionInvoiceForSaleOrder(saleOrder);
				if(invoice != null){
					listInvoiceId.add(invoice.getId());
				}
			}
			if (listInvoiceId.isEmpty()) {
				throw new AxelorException(IException.CONFIGURATION_ERROR, I18n.get("No sale order selected or no subscription to invoice"));
			}
			response.setReload(true);
			if(listInvoiceId.size() == 1){
				response.setView(ActionView
			            .define(I18n.get("Invoice Generated"))
			            .model(Invoice.class.getName())
			            .add("grid", "invoice-grid")
			            .add("form", "invoice-form")
			            .domain("self.id = "+listInvoiceId.get(0))
			            .map());
			}
			response.setView(ActionView
		            .define(I18n.get("Invoices Generated"))
		            .model(Invoice.class.getName())
		            .add("grid", "invoice-grid")
		            .add("form", "invoice-form")
		            .domain("self.id in ("+Joiner.on(",").join(listInvoiceId)+")")
		            .map());
		}
	}

	public void invoiceSubscriptionsSaleOrder(ActionRequest request, ActionResponse response) throws AxelorException{
		SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
		saleOrder = saleOrderRepo.find(saleOrder.getId());
		Invoice invoice = saleOrderInvoiceServiceImpl.generateSubcriptionInvoiceForSaleOrder(saleOrder);
		if (invoice == null) {
			throw new AxelorException(saleOrder, IException.CONFIGURATION_ERROR, I18n.get("No Subscription to Invoice"));
		}
		response.setReload(true);
		response.setView(ActionView
	            .define(I18n.get("Invoice Generated"))
	            .model(Invoice.class.getName())
	            .add("form", "invoice-form")
	            .add("grid", "invoice-grid")
	            .context("_showRecord",String.valueOf(invoice.getId()))
	            .map());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void mergeSaleOrder(ActionRequest request, ActionResponse response)  {
		List<SaleOrder> saleOrderList = new ArrayList<>();
		List<Long> saleOrderIdList = new ArrayList<>();
		boolean fromPopup = false;
		String lineToMerge;
		if (request.getContext().get("saleQuotationToMerge") != null){
			lineToMerge = "saleQuotationToMerge";
		} else {
			lineToMerge = "saleOrderToMerge";
		}
		
		if (request.getContext().get(lineToMerge) != null){

			if (request.getContext().get(lineToMerge) instanceof List){
				//No confirmation popup, sale orders are content in a parameter list
				List<Map> saleOrderMap = (List<Map>)request.getContext().get(lineToMerge);
				for (Map map : saleOrderMap) {
					saleOrderIdList.add(new Long((Integer)map.get("id")));
				}
			} else {
				//After confirmation popup, sale order's id are in a string separated by ","
				String saleOrderIdListStr = (String)request.getContext().get(lineToMerge);
				for (String saleOrderId : saleOrderIdListStr.split(",")) {
					saleOrderIdList.add(new Long(saleOrderId));
				}
				fromPopup = true;
			}
		}
		//Check if currency, clientPartner and company are the same for all selected sale orders
		Currency commonCurrency = null;
		Partner commonClientPartner = null;
		Company commonCompany = null;
		Partner commonContactPartner = null;
		Team commonTeam = null;
		//Useful to determine if a difference exists between teams of all sale orders
		boolean existTeamDiff = false;
		//Useful to determine if a difference exists between contact partners of all sale orders
		boolean existContactPartnerDiff = false;
		PriceList commonPriceList = null;
		//Useful to determine if a difference exists between price lists of all sale orders
		boolean existPriceListDiff = false;
		StockLocation commonLocation = null;
		//Useful to determine if a difference exists between locations of all sale orders
		boolean existLocationDiff = false;
		
		SaleOrder saleOrderTemp;
		int count = 1;
		for (Long saleOrderId : saleOrderIdList) {
			saleOrderTemp = JPA.em().find(SaleOrder.class, saleOrderId);
			saleOrderList.add(saleOrderTemp);
			if (count == 1) {
				commonCurrency = saleOrderTemp.getCurrency();
				commonClientPartner = saleOrderTemp.getClientPartner();
				commonCompany = saleOrderTemp.getCompany();
				commonContactPartner = saleOrderTemp.getContactPartner();
				commonTeam = saleOrderTemp.getTeam();
				commonPriceList = saleOrderTemp.getPriceList();
				commonLocation = saleOrderTemp.getStockLocation();
			} else {
				if (commonCurrency != null
						&& !commonCurrency.equals(saleOrderTemp.getCurrency())){
					commonCurrency = null;
				}
				if (commonClientPartner != null
						&& !commonClientPartner.equals(saleOrderTemp.getClientPartner())){
					commonClientPartner = null;
				}
				if (commonCompany != null
						&& !commonCompany.equals(saleOrderTemp.getCompany())){
					commonCompany = null;
				}
				if (commonContactPartner != null
						&& !commonContactPartner.equals(saleOrderTemp.getContactPartner())){
					commonContactPartner = null;
					existContactPartnerDiff = true;
				}
				if (commonTeam != null
						&& !commonTeam.equals(saleOrderTemp.getTeam())){
					commonTeam = null;
					existTeamDiff = true;
				}
				if (commonPriceList != null
						&& !commonPriceList.equals(saleOrderTemp.getPriceList())){
					commonPriceList = null;
					existPriceListDiff = true;
				}
				if (commonLocation != null
						&& !commonLocation.equals(saleOrderTemp.getStockLocation())){
					commonLocation = null;
					existLocationDiff = true;
				}
			}
			count++;
		}
		
		StringBuilder fieldErrors = new StringBuilder();
		if (commonCurrency == null) {
			fieldErrors.append(I18n.get(com.axelor.apps.sale.exception.IExceptionMessage.SALE_ORDER_MERGE_ERROR_CURRENCY));
		}
		if (commonClientPartner == null){
			if (fieldErrors.length() > 0){
				fieldErrors.append("<br/>");
			}
			fieldErrors.append(I18n.get(com.axelor.apps.sale.exception.IExceptionMessage.SALE_ORDER_MERGE_ERROR_CLIENT_PARTNER));
		}
		if (commonCompany == null){
			if (fieldErrors.length() > 0){
				fieldErrors.append("<br/>");
			}
			fieldErrors.append(I18n.get(com.axelor.apps.sale.exception.IExceptionMessage.SALE_ORDER_MERGE_ERROR_COMPANY));
		}

		if (fieldErrors.length() > 0){
			response.setFlash(fieldErrors.toString());
			return;
		}

		//Check if priceList or contactPartner are content in parameters
		if (request.getContext().get("priceList") != null){
			commonPriceList = JPA.em().find(PriceList.class, new Long((Integer)((Map)request.getContext().get("priceList")).get("id")));
		}
		if (request.getContext().get("contactPartner") != null){
			commonContactPartner = JPA.em().find(Partner.class, new Long((Integer)((Map)request.getContext().get("contactPartner")).get("id")));
		}
		if (request.getContext().get("team") != null){
			commonTeam = JPA.em().find(Team.class, new Long((Integer)((Map)request.getContext().get("team")).get("id")));
		}
		if (request.getContext().get("stockLocation") != null){
			commonLocation = JPA.em().find(StockLocation.class, new Long((Integer)((Map)request.getContext().get("stockLocation")).get("id")));
		}
		
		if (!fromPopup && (existContactPartnerDiff || existPriceListDiff || existTeamDiff)) {
			//Need to display intermediate screen to select some values
			ActionViewBuilder confirmView = ActionView
										.define("Confirm merge sale order")
										.model(Wizard.class.getName())
										.add("form", "sale-order-merge-confirm-form")
										.param("popup", "true")
										.param("show-toolbar", "false")
										.param("show-confirm", "false")
										.param("popup-save", "false")
										.param("forceEdit", "true");

			if (existPriceListDiff){
				confirmView.context("contextPriceListToCheck", "true");
			}
			if (existContactPartnerDiff){
				confirmView.context("contextContactPartnerToCheck", "true");
				confirmView.context("contextPartnerId", commonClientPartner.getId().toString());
			}
			if (existTeamDiff) {
				confirmView.context("contextTeamToCheck", "true");
			}
			if (existLocationDiff){
				confirmView.context("contextLocationToCheck", "true");
			}

			confirmView.context(lineToMerge, Joiner.on(",").join(saleOrderIdList));

			response.setView(confirmView.map());

			return;
		}

		try{
			SaleOrder saleOrder = saleOrderServiceSupplychain.mergeSaleOrders(saleOrderList, commonCurrency, commonClientPartner, commonCompany, commonLocation, commonContactPartner, commonPriceList, commonTeam);
			if (saleOrder != null){
				//Open the generated sale order in a new tab
				response.setView(ActionView
						.define("Sale order")
						.model(SaleOrder.class.getName())
						.add("grid", "sale-order-grid")
						.add("form", "sale-order-form")
						.param("forceEdit", "true")
						.context("_showRecord", String.valueOf(saleOrder.getId())).map());
				response.setCanClose(true);
			}
		}catch(AxelorException ae){
			response.setFlash(ae.getLocalizedMessage());
		}
	}

	public void updateAmountToBeSpreadOverTheTimetable(ActionRequest request, ActionResponse response) {
		SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
		saleOrderServiceSupplychain.updateAmountToBeSpreadOverTheTimetable(saleOrder);
		response.setValue("amountToBeSpreadOverTheTimetable" , saleOrder.getAmountToBeSpreadOverTheTimetable());
	}

    public void onSave(ActionRequest request, ActionResponse response) {
        try {
            SaleOrder saleOrderView = request.getContext().asType(SaleOrder.class);
            if (saleOrderView.getOrderBeingEdited()) {
                SaleOrder saleOrder = saleOrderRepo.find(saleOrderView.getId());
                saleOrderServiceSupplychain.validateChanges(saleOrder, saleOrderView);
            }
        } catch (Exception e) {
            TraceBackService.trace(response, e);
            response.setReload(true);
        }
    }

}
