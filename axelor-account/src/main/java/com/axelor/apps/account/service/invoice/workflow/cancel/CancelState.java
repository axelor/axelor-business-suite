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
package com.axelor.apps.account.service.invoice.workflow.cancel;

import java.math.BigDecimal;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.invoice.workflow.WorkflowInvoice;
import com.axelor.apps.base.db.Period;
import com.axelor.apps.base.db.repo.PeriodRepository;
import com.axelor.apps.base.service.PeriodService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;

public class CancelState extends WorkflowInvoice {

	@Override
	public void init(Invoice invoice){
		this.invoice = invoice;
	}

	@Override
	public void process() throws AxelorException {

		if(invoice.getStatusSelect() == STATUS_VENTILATED && invoice.getCompany().getAccountConfig().getAllowCancelVentilatedInvoice())  {
			cancelMove();
		}

		setStatus();

	}

	protected void setStatus(){

		invoice.setStatusSelect(STATUS_CANCELED);

	}

	protected void cancelMove() throws AxelorException{

		Move move = invoice.getMove();

		if(move == null)   {  return;  }

		if(invoice.getCompanyInTaxTotalRemaining().compareTo(invoice.getCompanyInTaxTotal()) != 0)  {

			throw new AxelorException(I18n.get(IExceptionMessage.CANCEL_STATE_1), IException.CONFIGURATION_ERROR);
		}

		if(invoice.getOldMove() != null)  {

			throw new AxelorException(I18n.get(IExceptionMessage.CANCEL_STATE_2), IException.CONFIGURATION_ERROR);
		}

		Period period = Beans.get(PeriodService.class).getPeriod(move.getDate(), move.getCompany());
		if(period == null || period.getStatusSelect() == PeriodRepository.STATUS_CLOSED)  {
			throw new AxelorException(I18n.get(IExceptionMessage.CANCEL_STATE_3), IException.CONFIGURATION_ERROR);
		}

		try{

			invoice.setMove(null);
			invoice.setCompanyInTaxTotalRemaining(BigDecimal.ZERO);

			if(invoice.getCompany().getAccountConfig().getAllowRemovalValidatedMove())  {
				Beans.get(MoveRepository.class).remove(move);
			}
			else  {
				move.setStatusSelect(MoveRepository.STATUS_CANCELED);
			}

		}
		catch(Exception e)  {

			throw new AxelorException(I18n.get(IExceptionMessage.CANCEL_STATE_4), IException.CONFIGURATION_ERROR);

		}


	}

}