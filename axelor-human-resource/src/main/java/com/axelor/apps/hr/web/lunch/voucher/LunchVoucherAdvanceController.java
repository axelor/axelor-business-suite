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
package com.axelor.apps.hr.web.lunch.voucher;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.LunchVoucherAdvance;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.hr.service.lunch.voucher.LunchVoucherAdvanceService;
import com.axelor.apps.hr.service.lunch.voucher.LunchVoucherMgtService;
import com.axelor.db.EntityHelper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class LunchVoucherAdvanceController {
	
	@Inject
	private Provider<LunchVoucherAdvanceService> lunchVoucherAdvanceServiceProvider;
	
	@Inject
	private Provider<LunchVoucherMgtService> lunchVoucherMgtProvider;
	
	@Inject
	private Provider<HRConfigService> hrConfigService;
	
	public void checkOnNewAdvance(ActionRequest request, ActionResponse response) throws AxelorException  {
		LunchVoucherAdvance lunchVoucherAdvance = EntityHelper.getEntity(request.getContext().asType(LunchVoucherAdvance.class));
		
		Company company = lunchVoucherAdvance.getEmployee().getMainEmploymentContract().getPayCompany();
		HRConfig hrConfig = hrConfigService.get().getHRConfig(company);
		int stock = lunchVoucherMgtProvider.get().checkStock(company, lunchVoucherAdvance.getNbrLunchVouchers());
		
		if (stock <= 0) {
			response.setAlert(String.format(I18n.get(IExceptionMessage.LUNCH_VOUCHER_MIN_STOCK),company.getName(),
					hrConfig.getMinStockLunchVoucher(), hrConfig.getAvailableStockLunchVoucher(), IException.INCONSISTENCY));
		}
	}
	
	public void onNewAdvance(ActionRequest request, ActionResponse response) {
		LunchVoucherAdvance lunchVoucherAdvance = EntityHelper.getEntity(request.getContext().asType(LunchVoucherAdvance.class));
		
		try {
			lunchVoucherAdvanceServiceProvider.get().onNewAdvance(lunchVoucherAdvance);
			response.setCanClose(true);
		}  catch(Exception e)  {
			TraceBackService.trace(response, e);
		}
	}
}
