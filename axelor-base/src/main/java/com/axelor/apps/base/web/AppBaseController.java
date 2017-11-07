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
package com.axelor.apps.base.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.AppBase;
import com.axelor.apps.base.db.CurrencyConversionLine;
import com.axelor.apps.base.db.repo.CurrencyConversionLineRepository;
import com.axelor.app.AppSettings;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.CurrencyConversionService;
import com.axelor.apps.base.service.MapService;
import com.axelor.apps.base.service.administration.ExportDbObjectService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Joiner;
import com.google.inject.Inject;

public class AppBaseController {

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Inject
	private ExportDbObjectService eos;
	
	@Inject
	private MapService mapService;
	
	@Inject
	private MetaFieldRepository metaFieldRepo;
	
	@Inject
	private CurrencyConversionService ccs;

	@Inject
	private CurrencyConversionLineRepository cclRepo;
	
	@Inject
	private AppBaseService appBaseService;

	public Set<MetaField> setFields(String model) throws IOException {
		LOG.debug("Model: {}",model);
		Set<MetaField> fieldSet = new HashSet<MetaField>();
		List<String> fields = new ArrayList<String>();

		MetaFieldRepository metaFieldRepository = Beans.get(MetaFieldRepository.class);

		for(MetaField field : metaFieldRepository.all().filter("metaModel.fullName = ?1 AND (relationship = null OR relationship = 'ManyToOne')",model).fetch()){
			fieldSet.add(field);
			fields.add(field.getName());
		}

		LOG.debug("Fields set: {}",fields);
		return fieldSet;
	}

	@SuppressWarnings("unchecked")
	public void showDuplicate(ActionRequest request, ActionResponse response){
		
		String model = (String)request.getContext().get("object");
		List<String> fields = new ArrayList<String>();
		Map<String, String> collectionFields = new HashMap<>();

		if(model == null){
			model = request.getModel();
			String searchFields = (String)request.getContext().get("searchFields");
			if(searchFields != null){
				fields.addAll(Arrays.asList(searchFields.split("\\s*;\\s*")));
			}
			String searchCollectionFields = (String) request.getContext().get("searchCollectionFields");
			if (searchCollectionFields != null) {
				for (String pair : searchCollectionFields.split("\\s*;\\s*")) {
					String[] items = pair.split("\\s*:\\s*");
					collectionFields.put(items[0], items[1]);
				}
			}
		}else{
			List<HashMap<String,Object>> fieldsSet = (List<HashMap<String,Object>>)request.getContext().get("fieldsSet");
			for(HashMap<String,Object> field : fieldsSet){
				if(field.get("selected") != null && (Boolean)field.get("selected")){
					MetaField metaField = metaFieldRepo.find(Long.parseLong(field.get("id").toString()));
					fields.add(metaField.getName());
				}
			}
		}
		
		LOG.debug("Duplicate record model: {}",model);
		
		if(fields.size() > 0){
			LOG.debug("Duplicate record joinList: {}, {}", fields, collectionFields);
			String ids = findDuplicateRecords(fields, collectionFields, model);
			if(ids.isEmpty())
				response.setFlash(I18n.get(IExceptionMessage.GENERAL_1));
			else{
				response.setView(ActionView
						  .define(I18n.get(IExceptionMessage.GENERAL_2))
						  .model(model)
						  .domain("self.id in ("+ids+")")
						  .map());
				response.setCanClose(true);
			}
		}
		else
			response.setFlash(I18n.get(IExceptionMessage.GENERAL_3));
	}

	public void exportObjects(ActionRequest request, ActionResponse response){
		MetaFile metaFile = eos.exportObject();
		if(metaFile == null){
			response.setFlash(I18n.get(IExceptionMessage.GENERAL_4));
		}
		else {
			response.setView(ActionView
					  .define(I18n.get(IExceptionMessage.GENERAL_5))
					  .model("com.axelor.meta.db.MetaFile")
					  .add("form", "meta-files-form")
					  .add("grid", "meta-files-grid")
					  .param("forceEdit", "true")
					  .context("_showRecord", metaFile.getId().toString())
					  .map());
		}
	}
	
	private String findDuplicateRecords(List<String> fieldList, Map<String, String> collectionFieldMap, String object) {
		
		
		List<List<String>> allRecords = getAllRecords(fieldList, collectionFieldMap, object);
		
		Map<String, List<String>> recordMap = new HashMap<String, List<String>>();
		
		for(List<String> rec : allRecords){
			
			List<String> record = new ArrayList<String>();
			for(String field : rec) {
				if(field != null){
					record.add(StringTool.deleteAccent(field.toLowerCase()));
				}
			}
			
			String recId = record.get(0);
			record.remove(0);
			if(!record.isEmpty()){
				recordMap.put(recId, record);
			}
		}
		
		Iterator<String> keys = recordMap.keySet().iterator();
		
		List<String> ids = getDuplicateIds(keys, recordMap, new ArrayList<String>());

		return Joiner.on(",").join(ids);
	}
	
	@SuppressWarnings("unchecked")
	private List<List<String>> getAllRecords(List<String> fieldList, Map<String, String> collectionFieldMap,
			String object) {

		StringBuilder queryBuilder = new StringBuilder();
		queryBuilder.append("SELECT new List(CAST(m.id AS string)");
		
		for(String field : fieldList) {
			if (!collectionFieldMap.containsKey(field)) {
				queryBuilder.append(String.format(", m.%s", field));
			} else {
				queryBuilder.append(String.format(", %s", field));
			}
		}

		queryBuilder.append(String.format(") FROM %s m", object));

		for (Map.Entry<String, String> entry : collectionFieldMap.entrySet()) {
			queryBuilder.append(String.format(" LEFT JOIN m.%s %s", entry.getValue(), entry.getKey()));
		}

		List<List<Object>> resultList = JPA.em().createQuery(queryBuilder.toString()).getResultList();
		
		List<List<String>> records = new ArrayList<List<String>>();
		
		for(List<Object> result : resultList){
			
			List<String> record = new ArrayList<String>();
			for(Object field : result){
				if(field == null){
					continue;
				}
				if(field instanceof Model){
					record.add(((Model)field).getId().toString());
				}
				else{
					record.add(field.toString());
				}
			}
			
			records.add(record);
		}
		
		return records;
	}
	
	private List<String> getDuplicateIds(Iterator<String> keys, Map<String, List<String>> recordMap, List<String> ids){
		
		if(!keys.hasNext()){
			return ids;
		}
		
		String recId = keys.next();
		List<String> record = recordMap.get(recId);
		keys.remove();
		recordMap.remove(recId);
		
		Iterator<String> compareKeys = recordMap.keySet().iterator();
		
		while(compareKeys.hasNext()){
			String compareId = compareKeys.next();
			List<String> value = recordMap.get(compareId);
			if(value.containsAll(record)){
				ids.add(recId);
				ids.add(compareId);
				compareKeys.remove();
				recordMap.remove(compareId);
				keys = recordMap.keySet().iterator();
			}
		}
		
		return getDuplicateIds(keys, recordMap, ids);
	}
	
	public void checkMapApi(ActionRequest request, ActionResponse response)  {
		
		AppBase appBase = request.getContext().asType(AppBase.class);;
		
		boolean connected = false;
		
		Integer apiType = appBase.getMapApiSelect();
		
		if(apiType == 1){
			connected = mapService.testGMapService();
		}
		
		if(connected){
			response.setFlash(IExceptionMessage.GENERAL_6);
		}
		else{
			response.setFlash(IExceptionMessage.GENERAL_7);
		}
	}
	
	public void updateCurrencyConversion(ActionRequest request, ActionResponse response){
		 AppBase appBase = request.getContext().asType(AppBase.class);
		 LocalDate today = appBaseService.getTodayDate();
		 
		 Map<Long, Long> currencyMap = new HashMap<Long, Long>();
		 
		 for(CurrencyConversionLine ccl : appBase.getCurrencyConversionLineList()){
			 currencyMap.put(ccl.getEndCurrency().getId(), ccl.getStartCurrency().getId());
		 }
		 
		 for(Long key  : currencyMap.keySet()){
			
			CurrencyConversionLine ccl = cclRepo.all().filter("startCurrency.id = ?1 AND endCurrency.id = ?2 AND fromDate <= ?3 AND toDate is null", currencyMap.get(key), key, today).fetchOne();
			
			LOG.info("Currency Conversion Line without toDate : {}", ccl);

			if(ccl == null){
				ccl = cclRepo.all().filter("startCurrency.id = ?1 AND endCurrency.id = ?2 AND fromDate <= ?3 AND toDate > ?3", currencyMap.get(key), key, today).fetchOne();
				if(ccl != null){
					LOG.info("Already convered Currency Conversion Line  found : {}", ccl);
					continue;
				}
				ccl = cclRepo.all().filter("startCurrency.id = ?1 AND endCurrency.id = ?2 AND fromDate <= ?3 AND (toDate not null AND toDate <= ?3)", currencyMap.get(key), key, today).order("-toDate").fetchOne();
				LOG.info("Currency Conversion Line found with toDate : {}", ccl);
			}
			
			
			if(ccl != null){
				BigDecimal currentRate = ccs.convert(ccl.getStartCurrency(), ccl.getEndCurrency());
				if(currentRate.compareTo(new BigDecimal(-1)) == 0){
					response.setFlash(I18n.get(IExceptionMessage.CURRENCY_6));
					break;
				}
				ccl = cclRepo.find(ccl.getId());
				ccl.setToDate(today.minusDays(1));
				ccs.saveCurrencyConversionLine(ccl);
				BigDecimal previousRate = ccl.getExchangeRate();
				String variations = ccs.getVariations(currentRate, previousRate);
				ccs.createCurrencyConversionLine(ccl.getStartCurrency(), ccl.getEndCurrency(), today, currentRate, appBaseService.getAppBase(), variations);
			}
			
		 }
		 
		 response.setReload(true);
	}
	
	public void applyApplicationMode(ActionRequest request, ActionResponse response)  {
		 String applicationMode = AppSettings.get().get("application.mode", "prod");
		 if ("dev".equals(applicationMode)) {
			 response.setAttr("main", "hidden", false);
		 }
	}

	
	public void showCustomersOnMap(ActionRequest request, ActionResponse response) throws AxelorException {

		mapService.showMap("customer", I18n.get("Customers"), response);
	
	}
		 	
	public void showProspectsOnMap(ActionRequest request, ActionResponse response) throws AxelorException {
		
		mapService.showMap("prospect", I18n.get("Prospect"), response);
 	
	}
		 	
 	public void showSuppliersOnMap(ActionRequest request, ActionResponse response) throws AxelorException {
 
 		mapService.showMap("supplier", I18n.get("Supplier"), response);
 	
 	}
 	
		 	
}
