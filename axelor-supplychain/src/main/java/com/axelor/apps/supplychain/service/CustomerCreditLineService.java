package com.axelor.apps.supplychain.service;

import java.util.Map;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.supplychain.db.CustomerCreditLine;
import com.google.inject.persist.Transactional;


public interface CustomerCreditLineService {
	
	public CustomerCreditLine computeUsedCredit(CustomerCreditLine customerCreditLine);
	public Partner generateLines(Partner partner);
	public Map<String,Object> updateLines(Partner partner);
	public Map<String,Object> updateLinesFromOrder(Partner partner,SaleOrder saleOrder);
	public boolean testUsedCredit(CustomerCreditLine customerCreditLine);
	@Transactional
	public boolean checkBlockedPartner(Partner partner, Company company);
}
