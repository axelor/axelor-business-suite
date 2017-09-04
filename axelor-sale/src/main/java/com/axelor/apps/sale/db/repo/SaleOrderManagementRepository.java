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
package com.axelor.apps.sale.db.repo;

import javax.persistence.PersistenceException;

import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.ISaleOrder;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.service.SaleOrderLineService;
import com.axelor.apps.sale.service.SaleOrderService;
import com.axelor.exception.AxelorException;
import com.google.common.base.Strings;
import com.google.inject.Inject;

public class SaleOrderManagementRepository extends SaleOrderRepository {

	@Inject
	protected AppBaseService appBaseService;
	
	@Inject
	private SaleOrderService saleOrderService;
	
	@Inject
	private SaleOrderLineService saleOrderLineService;

	@Override
	public SaleOrder copy(SaleOrder entity, boolean deep) {

		SaleOrder copy = super.copy(entity, deep);

		copy.setStatusSelect(ISaleOrder.STATUS_DRAFT);
		copy.setSaleOrderSeq(null);
		copy.clearBatchSet();
		copy.setImportId(null);
		copy.setCreationDate(appBaseService.getTodayDate());
		copy.setConfirmationDate(null);
		copy.setConfirmedByUser(null);
		copy.setOrderDate(null);
		copy.setOrderNumber(null);
		copy.setVersionNumber(1);

		return copy;
	}

	@Override
	public SaleOrder save(SaleOrder saleOrder) {
		try {
			computeSeq(saleOrder);
			computeFullName(saleOrder);
			computeSubMargin(saleOrder);
			saleOrderService.computeMarginSaleOrder(saleOrder);
			return super.save(saleOrder);
		} catch (Exception e) {
			throw new PersistenceException(e.getLocalizedMessage());
		}
	}
	
	public void computeSeq(SaleOrder saleOrder){
		try{
			
			if((saleOrder.getSaleOrderSeq() == null || Strings.isNullOrEmpty(saleOrder.getSaleOrderSeq())) && !saleOrder.getTemplate()){
				if ( saleOrder.getStatusSelect() == ISaleOrder.STATUS_DRAFT ){
					saleOrder.setSaleOrderSeq("*" + saleOrder.getId().toString());
				}
			}
				
		}
		catch (Exception e) {
			throw new PersistenceException(e.getLocalizedMessage());
		}
	}
	
	public void computeFullName(SaleOrder saleOrder){
		try{
			if(!Strings.isNullOrEmpty(saleOrder.getSaleOrderSeq()))
				saleOrder.setFullName(saleOrder.getSaleOrderSeq()+"-"+saleOrder.getClientPartner().getName());
			else
				saleOrder.setFullName(saleOrder.getClientPartner().getName());
		}
		catch (Exception e) {
			throw new PersistenceException(e.getLocalizedMessage());
		}
	}
	
	public void computeSubMargin(SaleOrder saleOrder) throws AxelorException {
		
		if (saleOrder.getSaleOrderLineList() != null) {
			for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
				saleOrderLineService.computeSubMargin(saleOrderLine);
			}
		}
	}
	
}
