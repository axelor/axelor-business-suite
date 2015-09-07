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
package com.axelor.apps.crm.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.service.MapService;
import com.axelor.apps.crm.db.Opportunity;
import com.axelor.apps.crm.db.repo.OpportunityRepository;
import com.axelor.apps.crm.exception.IExceptionMessage;
import com.axelor.apps.crm.service.OpportunityServiceImpl;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Strings;

public class OpportunityController {
	
	public void saveOpportunitySalesStage(ActionRequest request, ActionResponse response) throws AxelorException {
		
		Opportunity opportunity = request.getContext().asType(Opportunity.class);
		OpportunityServiceImpl opportunityService = Beans.get(OpportunityServiceImpl.class);
		Opportunity persistOpportunity = opportunityService.find(opportunity.getId());
		persistOpportunity.setSalesStageSelect(opportunity.getSalesStageSelect());
		opportunityService.saveOpportunity(persistOpportunity);
		
	}
	
	public void assignToMe(ActionRequest request, ActionResponse response)  {
		OpportunityServiceImpl opportunityService = Beans.get(OpportunityServiceImpl.class);
		if(request.getContext().get("id") != null){
			Opportunity opportunity = opportunityService.find((Long)request.getContext().get("id"));
			opportunity.setUser(AuthUtils.getUser());
			opportunityService.saveOpportunity(opportunity);
		}
		else if(!((List)request.getContext().get("_ids")).isEmpty()){
			for(Opportunity opportunity : opportunityService.all().filter("id in ?1",request.getContext().get("_ids")).fetch()){
				opportunity.setUser(AuthUtils.getUser());
				opportunityService.saveOpportunity(opportunity);
			}
		}
		response.setReload(true);
	}
	
	public void showOpportunitiesOnMap(ActionRequest request, ActionResponse response) throws IOException {
		
		String appHome = AppSettings.get().get("application.home");
		if (Strings.isNullOrEmpty(appHome)) {
			response.setFlash(I18n.get(IExceptionMessage.LEAD_2));
			return;
		}
		if (!Beans.get(MapService.class).isInternetAvailable()) {
			response.setFlash(I18n.get(IExceptionMessage.LEAD_3));
			return;			
		}		
		String mapUrl = new String(appHome + "/map/gmap-objs.html?apphome=" + appHome + "&object=opportunity");
		Map<String, Object> mapView = new HashMap<String, Object>();
		mapView.put("title", I18n.get("Opportunities"));
		mapView.put("resource", mapUrl);
		mapView.put("viewType", "html");		
		response.setView(mapView);
	}	
	
	public void createClient(ActionRequest request, ActionResponse response) throws AxelorException{
		Opportunity opportunity = request.getContext().asType(Opportunity.class);
		opportunity = Beans.get(OpportunityRepository.class).find(opportunity.getId());
		Beans.get(OpportunityServiceImpl.class).createClientFromLead(opportunity);
		response.setReload(true);
	}
}
