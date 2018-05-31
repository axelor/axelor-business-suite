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
package com.axelor.apps.crm.web;

import com.axelor.apps.crm.db.TargetConfiguration;
import com.axelor.apps.crm.exception.IExceptionMessage;
import com.axelor.apps.crm.service.TargetService;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class TargetConfigurationController {
	
	@Inject
	private TargetService targetService;
	
	public void generateTarget(ActionRequest request, ActionResponse response) throws AxelorException {
		
		TargetConfiguration targetConfiguration = request.getContext().asType(TargetConfiguration.class);
		targetService.createsTargets(targetConfiguration);
		response.setFlash(I18n.get(IExceptionMessage.TARGET_GENERATE));
	}
}
