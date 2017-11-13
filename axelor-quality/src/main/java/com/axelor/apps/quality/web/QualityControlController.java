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

package com.axelor.apps.quality.web;

import com.axelor.apps.quality.db.QualityControl;
import com.axelor.apps.quality.db.repo.QualityControlRepository;
import com.axelor.apps.quality.service.QualityControlService;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;

public class QualityControlController {
	
	@Inject
	private QualityControlRepository qualityControlRepo;
	
	@Inject
	private QualityControlService qualityControlService;

	public void openControlPoints(ActionRequest request, ActionResponse response) {
		response.setView(ActionView
				.define(I18n.get("Control points"))
				.model("com.axelor.apps.quality.db.ControlPoint")
				.add("grid","control-point-grid")
				.add("form", "control-point-form")
				.map()
				);
	}
	
	public void preFillOperations(ActionRequest request, ActionResponse response) throws AxelorException {
		QualityControl qualityControl =  request.getContext().asType( QualityControl.class );
		qualityControl = qualityControlRepo.find(qualityControl.getId());
		qualityControlService.preFillOperations(qualityControl);
		response.setReload(true);
	}
}
