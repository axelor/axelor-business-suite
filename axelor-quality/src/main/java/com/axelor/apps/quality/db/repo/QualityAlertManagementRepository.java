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
package com.axelor.apps.quality.db.repo;

import com.axelor.apps.base.db.IAdministration;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.quality.db.QualityAlert;
import com.google.common.base.Strings;
import com.google.inject.Inject;

public class QualityAlertManagementRepository extends QualityAlertRepository {
	
	@Inject
	private SequenceService sequenceService;
	
	@Override
	public QualityAlert save(QualityAlert qualityAlert) {
			if (Strings.isNullOrEmpty(qualityAlert.getReference())) {
				if (Strings.isNullOrEmpty(qualityAlert.getQualityAlertSeq())) {
					qualityAlert.setQualityAlertSeq(sequenceService.getSequenceNumber(IAdministration.QUALITY_ALERT, null));
				}
			} 
			qualityAlert.setReference(qualityAlert.getQualityAlertSeq());
		return super.save(qualityAlert);
	}
		
}
