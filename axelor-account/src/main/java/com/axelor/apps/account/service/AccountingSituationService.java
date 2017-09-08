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
package com.axelor.apps.account.service;

import java.util.List;
import java.util.Set;

import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.AccountingSituationRepository;
import com.axelor.apps.account.db.repo.PaymentModeRepository;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.tool.StringTool;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;


public class AccountingSituationService	{

	protected AccountingSituationRepository accountingSituationRepo;

	@Inject
	public AccountingSituationService(AccountingSituationRepository situationRepository)  {
		this.accountingSituationRepo = situationRepository;
	}

	public boolean checkAccountingSituationList(List<AccountingSituation> accountingSituationList, Company company) {

		if(accountingSituationList != null)  { 
			for(AccountingSituation accountingSituation : accountingSituationList) {
	
				if(accountingSituation.getCompany().equals(company))  { 
					return true;
				}
			}
		}
	
		return false;
	}

	@Transactional(rollbackOn = { AxelorException.class, Exception.class })
	public List<AccountingSituation> createAccountingSituation(Partner partner) throws AxelorException {
		Set<Company> companySet = partner.getCompanySet();

		if (companySet != null) {
			for (Company company : companySet) {
				if (!checkAccountingSituationList(partner.getAccountingSituationList(), company)) {
					createAccountingSituation(partner, company);
				}
			}
		}

		return partner.getAccountingSituationList();
	}

	@Transactional(rollbackOn = { AxelorException.class, Exception.class })
	public AccountingSituation createAccountingSituation(Partner partner, Company company) throws AxelorException {
		AccountingSituation accountingSituation = new AccountingSituation();
		accountingSituation.setCompany(company);
		partner.addCompanySetItem(company);

		PaymentMode inPaymentMode = partner.getInPaymentMode();
		PaymentMode outPaymentMode = partner.getOutPaymentMode();
		BankDetails defaultBankDetails = company.getDefaultBankDetails();

		if (inPaymentMode != null) {
			List<BankDetails> authorizedInBankDetails = Beans.get(PaymentModeService.class)
					.getCompatibleBankDetailsList(inPaymentMode, company);
			if (authorizedInBankDetails.contains(defaultBankDetails)) {
				accountingSituation.setCompanyInBankDetails(defaultBankDetails);
			}
		}

		if (outPaymentMode != null) {
			List<BankDetails> authorizedOutBankDetails = Beans.get(PaymentModeService.class)
					.getCompatibleBankDetailsList(outPaymentMode, company);
			if (authorizedOutBankDetails.contains(defaultBankDetails)) {
				accountingSituation.setCompanyOutBankDetails(defaultBankDetails);
			}
		}

		partner.addAccountingSituationListItem(accountingSituation);
		return accountingSituationRepo.save(accountingSituation);
	}

	public AccountingSituation getAccountingSituation(Partner partner, Company company)  {
		
		if(partner.getAccountingSituationList() == null)  {  return null;  }
		
		for(AccountingSituation accountingSituation : partner.getAccountingSituationList())  {
			
			if(accountingSituation.getCompany().equals(company))  {
				
				return accountingSituation;
				
			}
		}
		
		return null;
		
	}

	/**
	 * Creates the domain for the bank details in Accounting Situation
	 * @param accountingSituation
	 * @param isInBankDetails  true if the field is companyInBankDetails
	 *                         false if the field is companyOutBankDetails
	 * @return the domain of the bank details field
	 */
	public String createDomainForBankDetails(AccountingSituation accountingSituation, boolean isInBankDetails) {
	    String domain = "";
	    List<BankDetails> authorizedBankDetails;
		if (isInBankDetails) {
			authorizedBankDetails = Beans.get(PaymentModeService.class).
					getCompatibleBankDetailsList(
							accountingSituation.getPartner().getInPaymentMode(),
							accountingSituation.getCompany()
					);
		}
		else {
			authorizedBankDetails = Beans.get(PaymentModeService.class).
					getCompatibleBankDetailsList(
							accountingSituation.getPartner().getOutPaymentMode(),
							accountingSituation.getCompany()
					);
		}
		String idList = StringTool.getIdFromCollection(authorizedBankDetails);
		if(idList.equals("")) {
			return domain;
		}
		domain = "self.id IN (" + idList + ") AND self.active = true";
		return domain;
	}

	/**
	 * Find a default bank details.
	 * @param company
	 * @param paymentMode
	 * @param partner
	 * @return  the default bank details in accounting situation if it is active
	 *          and allowed by the payment mode.
	 */
	public BankDetails findDefaultBankDetails(Company company, PaymentMode paymentMode, Partner partner) {
		AccountingSituation accountingSituation = this.getAccountingSituation(partner, company);
		if (accountingSituation == null) { return null;}
		BankDetails candidateBankDetails = null;
		if (paymentMode.getInOutSelect() == PaymentModeRepository.IN) {
			candidateBankDetails = accountingSituation.getCompanyInBankDetails();
		}
		else if (paymentMode.getInOutSelect() == PaymentModeRepository.OUT) {
			candidateBankDetails = accountingSituation.getCompanyOutBankDetails();
		}
		List<BankDetails>authorizedBankDetails = Beans.get(PaymentModeService.class).
				getCompatibleBankDetailsList(paymentMode, company);
		if (authorizedBankDetails.contains(candidateBankDetails) &&
				candidateBankDetails.getActive()) {
			return candidateBankDetails;
		}
		else {
			return null;
		}
	}
	
}
