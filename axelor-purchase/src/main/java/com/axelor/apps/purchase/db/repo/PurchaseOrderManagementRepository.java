package com.axelor.apps.purchase.db.repo;

import javax.persistence.PersistenceException;

import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.service.PurchaseOrderService;
import com.axelor.inject.Beans;

public class PurchaseOrderManagementRepository extends PurchaseOrderRepository {

	@Override
	public PurchaseOrder copy(PurchaseOrder entity, boolean deep) {
		entity.setStatusSelect(1);
		entity.setPurchaseOrderSeq(null);
		return super.copy(entity, deep);
 	}

	@Override
	public PurchaseOrder save(PurchaseOrder purchaseOrder) {

		try{
			Beans.get(PurchaseOrderService.class).setDraftSequence(purchaseOrder);
			return super.save(purchaseOrder);
		} catch(Exception e){
			throw new PersistenceException(e.getLocalizedMessage());
		}
	}

}
