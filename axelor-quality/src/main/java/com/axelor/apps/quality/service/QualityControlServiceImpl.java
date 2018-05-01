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
package com.axelor.apps.quality.service;

import com.axelor.apps.quality.db.ControlPoint;
import com.axelor.apps.quality.db.ControlPointModel;
import com.axelor.apps.quality.db.QualityControl;
import com.axelor.apps.quality.db.QualityProcess;
import com.axelor.apps.quality.db.repo.ControlPointRepository;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class QualityControlServiceImpl  implements QualityControlService {

	@Inject
	ControlPointRepository controlPointRepo;

	/**
	 * Copy control point model to control point and set it to the quality control.
	 * @param qualityControl Set control point model to control point of this object.
	 * @throws AxelorException
	 *
	 */
	@Override
	@Transactional
	public void preFillOperations(QualityControl qualityControl) {

		if (qualityControl.getQualityProcess() != null) {
			QualityProcess process = qualityControl.getQualityProcess();

			if (process != null && process.getControlPointModelList() != null) {
				qualityControl.clearControlPointList();
				for(ControlPointModel model : process.getControlPointModelList() ) {
					ControlPoint point = new ControlPoint();
					point.setStatusSelect(1);
					point.setName(model.getName());
					point.setPriority(model.getPriority());
					point.setProduct(model.getProduct());
					point.setTeam(model.getTeam());
					point.setResponsible(model.getResponsible());
					point.setControlTypeSelect(model.getControlTypeSelect());
					point.setTestTypeSelect(model.getTestTypeSelect());
					point.setInstructions(model.getInstructions());
					point.setNotes(model.getNotes());
					point.setMessageIfFailure(model.getMessageIfFailure());
					point.setControlFrequency(model.getControlFrequency());
					point.setControlPointDate(qualityControl.getStartDate());
					point.setQualityControl(qualityControl);
					controlPointRepo.save(point);
					qualityControl.addControlPointListItem(point);
				}
			}
		}
	}

}
