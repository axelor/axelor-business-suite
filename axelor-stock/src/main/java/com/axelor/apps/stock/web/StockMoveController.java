/**
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
package com.axelor.apps.stock.web;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.birt.core.exception.BirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.IAdministration;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.MapService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.report.IReport;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class StockMoveController {

	private final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );
	
	@Inject
	private StockMoveService stockMoveService;
	
	@Inject
	private StockMoveRepository stockMoveRepo;

	@Inject
	protected AppBaseService appBaseService;

	public void plan(ActionRequest request, ActionResponse response) {

		StockMove stockMove = request.getContext().asType(StockMove.class);
		try {
			stockMoveService.plan(stockMoveRepo.find(stockMove.getId()));
			response.setReload(true);
		}
		catch(Exception e)  { TraceBackService.trace(response, e); }
	}

	public void realize(ActionRequest request, ActionResponse response)  {

		StockMove stockMoveFromRequest = request.getContext().asType(StockMove.class);

		try {
			StockMove stockMove = stockMoveRepo.find(stockMoveFromRequest.getId());
			String newSeq = stockMoveService.realize(stockMove);
			
			response.setReload(true);

			if(newSeq != null)  {
				if (stockMove.getTypeSelect() == StockMoveRepository.TYPE_INCOMING){
					response.setFlash(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_INCOMING_PARTIAL_GENERATED), newSeq));
				}else if (stockMove.getTypeSelect() == StockMoveRepository.TYPE_OUTGOING){
					response.setFlash(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_OUTGOING_PARTIAL_GENERATED), newSeq));
				}else{
					response.setFlash(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_9), newSeq));
				}
			}
		}
		catch(Exception e)  { TraceBackService.trace(response, e); }
	}

	public void cancel(ActionRequest request, ActionResponse response)  {

		StockMove stockMove = request.getContext().asType(StockMove.class);

		try {
			stockMoveService.cancel(stockMoveRepo.find(stockMove.getId()));
			response.setReload(true);
		}
		catch(Exception e)  { TraceBackService.trace(response, e); }
	}


	/**
	 * Method to generate stock move as a pdf
	 *
	 * @param request
	 * @param response
	 * @return
	 * @throws BirtException 
	 * @throws IOException 
	 */
	public void printStockMove(ActionRequest request, ActionResponse response) throws AxelorException {


		StockMove stockMove = request.getContext().asType(StockMove.class);
		String stockMoveIds = "";

		@SuppressWarnings("unchecked")
		List<Integer> lstSelectedMove = (List<Integer>) request.getContext().get("_ids");
		if(lstSelectedMove != null){
			for(Integer it : lstSelectedMove) {
				stockMoveIds+= it.toString()+",";
			}
		}

		if(!stockMoveIds.equals("")){
			stockMoveIds = stockMoveIds.substring(0, stockMoveIds.length()-1);
			stockMove = stockMoveRepo.find(new Long(lstSelectedMove.get(0)));
		}else if(stockMove.getId() != null){
			stockMoveIds = stockMove.getId().toString();
		}

		if(!stockMoveIds.equals("")){

			String language="";
			try{
				language = stockMove.getPartner().getLanguageSelect() != null? stockMove.getPartner().getLanguageSelect() : stockMove.getCompany().getPrintingSettings().getLanguageSelect() != null ? stockMove.getCompany().getPrintingSettings().getLanguageSelect() : "en" ;
			}catch (NullPointerException e) {
				language = "en";
			}
			language = language.equals("")? "en": language;

			String title = I18n.get("Stock move");
			if(stockMove.getStockMoveSeq() != null)  {
				title = lstSelectedMove == null ? I18n.get("StockMove") + " " + stockMove.getStockMoveSeq() : I18n.get("StockMove(s)");
			}

			String fileLink = ReportFactory.createReport(IReport.STOCK_MOVE, title+"-${date}")
					.addParam("StockMoveId", stockMoveIds)
					.addParam("Locale", language)
					.generate()
					.getFileLink();

			logger.debug("Printing "+title);
		
			response.setView(ActionView
					.define(title)
					.add("html", fileLink).map());
				
		}else{
			response.setFlash(I18n.get(IExceptionMessage.STOCK_MOVE_10));
		}
	}




	public void  viewDirection(ActionRequest request, ActionResponse response) {

		StockMove stockMove = request.getContext().asType(StockMove.class);

		Address fromAddress = stockMove.getFromAddress();
		Address toAddress = stockMove.getToAddress();
		String msg = "";
		if(fromAddress == null)
			fromAddress =  stockMove.getCompany().getAddress();
		if(toAddress == null)
			toAddress =  stockMove.getCompany().getAddress();
		if(fromAddress == null || toAddress == null)
			msg = I18n.get(IExceptionMessage.STOCK_MOVE_11);
		if (appBaseService.getAppBase().getMapApiSelect() == IAdministration.MAP_API_OSM)
			msg = I18n.get(IExceptionMessage.STOCK_MOVE_12);
		if(msg.isEmpty()){
			String dString = fromAddress.getAddressL4()+" ,"+fromAddress.getAddressL6();
			String aString = toAddress.getAddressL4()+" ,"+toAddress.getAddressL6();
			BigDecimal dLat = fromAddress.getLatit();
			BigDecimal dLon = fromAddress.getLongit();
			BigDecimal aLat = toAddress.getLatit();
			BigDecimal aLon =  toAddress.getLongit();
			Map<String, Object> result = Beans.get(MapService.class).getDirectionMapGoogle(dString, dLat, dLon, aString, aLat, aLon);
			if(result != null){
				Map<String,Object> mapView = new HashMap<String,Object>();
				mapView.put("title", I18n.get("Map"));
				mapView.put("resource", result.get("url"));
				mapView.put("viewType", "html");
			    response.setView(mapView);
			}
			else response.setFlash(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_13),dString,aString));
		}else response.setFlash(msg);

	}

	@SuppressWarnings("unchecked")
	public void  splitStockMoveLinesUnit(ActionRequest request, ActionResponse response) {
		List<StockMoveLine> stockMoveLines = (List<StockMoveLine>) request.getContext().get("stockMoveLineList");
		if(stockMoveLines == null){
			response.setFlash(I18n.get(IExceptionMessage.STOCK_MOVE_14));
			return;
		}
		Boolean selected = stockMoveService.splitStockMoveLinesUnit(stockMoveLines, new BigDecimal(1));

		if(!selected)
			response.setFlash(I18n.get(IExceptionMessage.STOCK_MOVE_15));
		response.setReload(true);
		response.setCanClose(true);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void  splitStockMoveLinesSpecial(ActionRequest request, ActionResponse response) {
		List<HashMap> stockMoveLines = (List<HashMap>) request.getContext().get("stockMoveLineList");
		if(stockMoveLines == null){
			response.setFlash(I18n.get(IExceptionMessage.STOCK_MOVE_14));
			return;
		}
		Integer splitQty = (Integer)request.getContext().get("splitQty");
		if(splitQty < 1){
			response.setFlash(I18n.get(IExceptionMessage.STOCK_MOVE_16));
			return ;
		}
		Boolean selected = stockMoveService.splitStockMoveLinesSpecial(stockMoveLines, new BigDecimal(splitQty));
		if(!selected)
			response.setFlash(I18n.get(IExceptionMessage.STOCK_MOVE_15));
		response.setReload(true);
		response.setCanClose(true);
	}

	public void shipReciveAllProducts(ActionRequest request, ActionResponse response) {
		StockMove stockMove = request.getContext().asType(StockMove.class);
		stockMoveService.copyQtyToRealQty(stockMoveRepo.find(stockMove.getId()));
		response.setReload(true);
	}

	public void generateReversion(ActionRequest request, ActionResponse response)  {

		StockMove stockMove = request.getContext().asType(StockMove.class);

		try {
			StockMove reversion = stockMoveService.generateReversion(stockMoveRepo.find(stockMove.getId()));
			response.setView(ActionView
					.define(I18n.get("Stock move"))
					.model(StockMove.class.getName())
					.add("grid", "stock-move-grid")
					.add("form", "stock-move-form")
					.param("forceEdit", "true")
					.context("_showRecord", String.valueOf(reversion.getId())).map());
		}
		catch(Exception e)  { TraceBackService.trace(response, e); }
	}

	public void  splitInto2(ActionRequest request, ActionResponse response) {
		StockMove stockMove = request.getContext().asType(StockMove.class);
		Long newStockMoveId = stockMoveService.splitInto2(stockMove.getId(), stockMove.getStockMoveLineList());

		if (newStockMoveId == null){
			response.setFlash(I18n.get(IExceptionMessage.STOCK_MOVE_SPLIT_NOT_GENERATED));
		}else{
			response.setCanClose(true);

			response.setView(ActionView
					.define("Stock move")
					.model(StockMove.class.getName())
					.add("grid", "stock-move-grid")
					.add("form", "stock-move-form")
					.param("forceEdit", "true")
					.context("_showRecord", String.valueOf(newStockMoveId)).map());

		}

	}
	
	@Transactional
	public void changeConformityStockMove(ActionRequest request, ActionResponse response) {
		StockMove stockMove = request.getContext().asType(StockMove.class);
		
		if(stockMove.getStockMoveLineList() != null && !stockMove.getStockMoveLineList().isEmpty()){
			for(StockMoveLine stockMoveLine : stockMove.getStockMoveLineList()){
				stockMoveLine.setConformitySelect(stockMove.getConformitySelect());
			}
			response.setValue("stockMoveLineList", stockMove.getStockMoveLineList());
		} 
	}
	
	@Transactional
	public void changeConformityStockMoveLine(ActionRequest request, ActionResponse response) {
		StockMove stockMove = request.getContext().asType(StockMove.class);
		
		if(stockMove.getStockMoveLineList() != null && !stockMove.getStockMoveLineList().isEmpty()){
			for(StockMoveLine stockMoveLine : stockMove.getStockMoveLineList()){
				Integer i = 0;
				if(stockMoveLine.getConformitySelect() != null){
					Integer conformitySelectBase = 1;
					while(i < stockMove.getStockMoveLineList().size()){
						Integer conformityLineSelect = stockMoveLine.getConformitySelect();
						if(conformityLineSelect == 3){
							response.setValue("conformitySelect", conformityLineSelect);
							return;
						}
						
						if (conformityLineSelect == conformitySelectBase){
							response.setValue("conformitySelect", conformitySelectBase);
						} else if (conformityLineSelect != conformitySelectBase){
							conformitySelectBase = conformityLineSelect;
						}
						i++;
					}
				}
				
			}
		}
	}
	
	
	public void  compute(ActionRequest request, ActionResponse response) {
		
		StockMove stockMove = request.getContext().asType(StockMove.class);
		response.setValue("exTaxTotal", stockMoveService.compute(stockMove));
		
	}
	
	public void openStockPerDay(ActionRequest request, ActionResponse response) {
		
		Context context = request.getContext();
		
		Long locationId = Long.parseLong(((Map<String,Object>)context.get("stockLocation")).get("id").toString());
		LocalDate fromDate = LocalDate.parse(context.get("stockFromDate").toString());
		LocalDate toDate = LocalDate.parse(context.get("stockToDate").toString());
		
		Collection<Map<String,Object>> products = (Collection<Map<String,Object>>)context.get("productSet");
		
		String domain = null;
		List<Object> productIds = null;
		if (products != null && !products.isEmpty()) {
			productIds = Arrays.asList(products.stream().map(p->p.get("id")).toArray());
			domain = "self.id in (:productIds)";
		}
		
		response.setView(ActionView.define(I18n.get("Stocks"))
			.model(Product.class.getName())
			.add("cards", "stock-product-cards")
			.add("grid", "stock-product-grid")
			.add("form", "stock-product-form")
			.domain(domain)
			.context("fromStockWizard", true)
			.context("productIds", productIds)
			.context("stockFromDate", fromDate)
			.context("stockToDate", toDate)
			.context("locationId", locationId)
			.map());
		
	}
	

}
