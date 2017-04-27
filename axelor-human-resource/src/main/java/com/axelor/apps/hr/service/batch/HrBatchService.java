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
package com.axelor.apps.hr.service.batch;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.hr.db.HrBatch;
import com.axelor.apps.hr.db.repo.HrBatchRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;

public class HrBatchService {
	
	public Batch run(HrBatch hrBatch) throws AxelorException{
			
			Batch batch = null;
			
			switch (hrBatch.getActionSelect()) {
			case HrBatchRepository.ACTION_LEAVE_MANAGEMENT:
				batch = leaveManagement(hrBatch);
				break;
			case HrBatchRepository.ACTION_SENIORITY_LEAVE_MANAGEMENT:
				batch = seniorityLeaveManagement(hrBatch);
				break;
			case HrBatchRepository.ACTION_PAYROLL_PREPARATION_GENERATION:
				batch = payrollPreparationGeneration(hrBatch);
				break;
			case HrBatchRepository.ACTION_PAYROLL_PREPARATION_EXPORT:
				batch = payrollPreparationExport(hrBatch);
				break;
			default:
				throw new AxelorException(String.format(I18n.get(IExceptionMessage.BASE_BATCH_1), hrBatch.getActionSelect(), hrBatch.getCode()), IException.INCONSISTENCY);
			}
			
			return batch;
		}


	
	public Batch leaveManagement(HrBatch hrBatch){
		
		return Beans.get(BatchLeaveManagement.class).run(hrBatch);
	}
	
	public Batch seniorityLeaveManagement(HrBatch hrBatch){
		
		return Beans.get(BatchSeniorityLeaveManagement.class).run(hrBatch);
	}
	
	public Batch payrollPreparationGeneration(HrBatch hrBatch){
		
		return Beans.get(BatchPayrollPreparationGeneration.class).run(hrBatch);
	}
	
	public Batch payrollPreparationExport(HrBatch hrBatch){
		
		return Beans.get(BatchPayrollPreparationExport.class).run(hrBatch);
	}


}

