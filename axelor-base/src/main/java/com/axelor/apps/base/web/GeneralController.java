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
package com.axelor.apps.base.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.administration.ExportDbObjectService;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.JPA;
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

public class GeneralController {

	private static final Logger LOG = LoggerFactory.getLogger(CurrencyService.class);

	@Inject
	private ExportDbObjectService eos;

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
		List<String> fieldList = new ArrayList<String>();
		String searchFields;
		if(model==null){
			model=request.getModel();
			searchFields=(String) request.getContext().get("searchFields");
			fieldList.addAll(Arrays.asList(searchFields.split(";")));

		}else{
			for(HashMap<String,Object> field:(List<HashMap<String,Object>>) request.getContext().get("fieldsSet")){
				if((Boolean)field.get("selected"))
					fieldList.add(field.get("name").toString());
			}
		}
		LOG.debug("Duplicate record model: {}",model);
		if(fieldList.size() > 0){
			LOG.debug("Duplicate record joinList: {}",fieldList);
			String ids =findDuplicateRecords(fieldList,model);
			if(ids.isEmpty())
				response.setFlash(I18n.get(IExceptionMessage.GENERAL_1));
			else
				response.setView(ActionView
						  .define(I18n.get(IExceptionMessage.GENERAL_2))
						  .model(model)
						  .domain("self.id in ("+ids+")")
						  .map());
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
	private String findDuplicateRecords(List<String> fieldList,String object){
		List<String> joinList=new ArrayList<>();

		for(String field:fieldList){
			joinList.add("m."+field);
		}
		Query query = JPA.em().createQuery("SELECT new List( CAST ( m.id AS string ), "+Joiner.on(",").join(joinList)+") FROM "+object+" m");

		List<List<String>> stringLists = query.getResultList();
		List<String> idList = new ArrayList<String>();
		List<List<String>> newStringLists = new ArrayList<List<String>>();
		for (List<String> list : stringLists){
			List<String> newList = new ArrayList<String>();
			for (String string : list) {
				newList.add(StringTool.deleteAccent(string.toLowerCase()));
			}
			newStringLists.add(newList);
		}

		for (List<String> list : newStringLists){
			List<String> testList = new ArrayList<String>();
			for(int i = 1; i<fieldList.size()+1; i++){
				testList.add(list.get(i));
			}
			for (List<String> listIt : newStringLists){
				if(!listIt.get(0).equals(list.get(0)) && listIt.containsAll(testList)){
					idList.add(listIt.get(0));
				}
			}
		}
		return Joiner.on(",").join(idList);
	}
}
