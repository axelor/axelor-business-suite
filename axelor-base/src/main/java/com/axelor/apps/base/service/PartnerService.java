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
package com.axelor.apps.base.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.IPartner;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PartnerAddress;
import com.axelor.apps.base.db.PartnerPriceList;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.repo.PartnerAddressRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.app.AppBaseServiceImpl;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class PartnerService {

	@Inject
	private PartnerRepository partnerRepo;

	public Partner createPartner(String name, String firstName, String fixedPhone, String mobilePhone, EmailAddress emailAddress, Currency currency, Address deliveryAddress, Address mainInvoicingAddress){
		Partner partner = new Partner();

		partner.setName(name);
		partner.setFirstName(firstName);
		partner.setFullName(this.computeFullName(partner));
		partner.setPartnerTypeSelect(IPartner.PARTNER_TYPE_SELECT_ENTERPRISE);
		partner.setIsProspect(true);
		partner.setFixedPhone(fixedPhone);
		partner.setMobilePhone(mobilePhone);
		partner.setEmailAddress(emailAddress);
		partner.setCurrency(currency);
		Partner contact = new Partner();
		contact.setPartnerTypeSelect(IPartner.PARTNER_TYPE_SELECT_INDIVIDUAL);
		contact.setIsContact(true);
		contact.setName(name);
		contact.setFirstName(firstName);
		contact.setMainPartner(partner);
		contact.setFullName(this.computeFullName(partner));
		partner.addContactPartnerSetItem(contact);
		
		if(deliveryAddress == mainInvoicingAddress){
			addPartnerAddress(partner, mainInvoicingAddress, true, true, true);
		}
		else {
			addPartnerAddress(partner, deliveryAddress, true, false, true);
			addPartnerAddress(partner, mainInvoicingAddress, true, true, false);
		}

		return partner;
	}

	public void setPartnerFullName(Partner partner)  {

		partner.setFullName(this.computeFullName(partner));

	}

	public String computeFullName(Partner partner)  {
		if(!Strings.isNullOrEmpty(partner.getName()) && !Strings.isNullOrEmpty(partner.getFirstName()))  {
			return partner.getName() + " " + partner.getFirstName();
		}
		else if(!Strings.isNullOrEmpty(partner.getName()))  {
			return partner.getName();
		}
		else if(!Strings.isNullOrEmpty(partner.getFirstName()))  {
			return partner.getFirstName();
		}
		else  {
			return ""+partner.getId();
		}
	}

	public Map<String,String> getSocialNetworkUrl(String name,String firstName, Integer typeSelect){

		Map<String,String> urlMap = new HashMap<String,String>();
		if(typeSelect == 2){
			name = firstName != null && name != null ? firstName+"+"+name : name == null ? firstName : name;
		}
		name = name == null ? "" : name;
		urlMap.put("google","<a class='fa fa-google-plus' href='https://www.google.com/?gws_rd=cr#q="+name+"' target='_blank' />");
		urlMap.put("facebook","<a class='fa fa-facebook' href='https://www.facebook.com/search/more/?q="+name+"&init=public"+"' target='_blank'/>");
		urlMap.put("twitter", "<a class='fa fa-twitter' href='https://twitter.com/search?q="+name+"' target='_blank' />");
		urlMap.put("linkedin","<a class='fa fa-linkedin' href='https://www.linkedin.com/company/"+name+"' target='_blank' />");
		if(typeSelect == 2){
			urlMap.put("linkedin","<a class='fa fa-linkedin' href='http://www.linkedin.com/pub/dir/"+name.replace("+","/")+"' target='_blank' />");
		}
		urlMap.put("youtube","<a class='fa fa-youtube' href='https://www.youtube.com/results?search_query="+name+"' target='_blank' />");

		return urlMap;
	}

	public List<Long> findPartnerMails(Partner partner){
		List<Long> idList = new ArrayList<Long>();

		idList.addAll(this.findMailsFromPartner(partner));

		Set<Partner> contactSet = partner.getContactPartnerSet();
		if(contactSet != null && !contactSet.isEmpty()){
			for (Partner contact : contactSet) {
				idList.addAll(this.findMailsFromPartner(contact));
			}
		}
		return idList;
	}

	public List<Long> findContactMails(Partner partner){
		List<Long> idList = new ArrayList<Long>();

		idList.addAll(this.findMailsFromPartner(partner));

		return idList;
	}

	@SuppressWarnings("unchecked")
	public List<Long> findMailsFromPartner(Partner partner){
		String query = "SELECT DISTINCT(email.id) FROM Message as email WHERE email.mediaTypeSelect = 2 AND " +
				"(email.relatedTo1Select = 'com.axelor.apps.base.db.Partner' AND email.relatedTo1SelectId = " + partner.getId() + ") " +
				"OR (email.relatedTo2Select = 'com.axelor.apps.base.db.Partner' AND email.relatedTo2SelectId = " + partner.getId() + ")";
		
		if(partner.getEmailAddress() != null) {
			query += "OR (email.fromEmailAddress.id = " + partner.getEmailAddress().getId() + ")";
		}
		
		return JPA.em().createQuery(query).getResultList();
	}
	
	private PartnerAddress createPartnerAddress(Address address, Boolean isDefault){

		PartnerAddress partnerAddress = new PartnerAddress();
		partnerAddress.setAddress(address);
		partnerAddress.setIsDefaultAddr(isDefault);

		return partnerAddress;
	}


	@Transactional
	public void resetDefaultAddress(Partner partner, String addrTypeQuery) {

		if(partner.getId() != null){
			PartnerAddressRepository partnerAddressRepo = Beans.get(PartnerAddressRepository.class);
			PartnerAddress partnerAddress =  partnerAddressRepo.all().filter("self.partner.id = ? AND self.isDefaultAddr = true"+addrTypeQuery,partner.getId()).fetchOne();
			if(partnerAddress != null){
				partnerAddress.setIsDefaultAddr(false);
				partnerAddressRepo.save(partnerAddress);
			}
		}

	}

	public Partner addPartnerAddress(Partner partner,Address address, Boolean isDefault, Boolean isInvoicing, Boolean isDelivery){

		PartnerAddress partnerAddress = createPartnerAddress(address,isDefault);
		
		if(isDefault != null && isDefault){
			String query = " AND self.isDeliveryAddr = false AND self.isInvoicingAddr = false";
			if((isInvoicing != null && isInvoicing)  && (isDelivery != null && isDelivery)){
				query = " AND self.isDeliveryAddr = true AND self.isInvoicingAddr = true";
			}
			else if(isInvoicing != null && isInvoicing){
				query = " AND self.isDeliveryAddr = false AND self.isInvoicingAddr = true";
			}
			else if(isDelivery != null && isDelivery){
				query = " AND self.isDeliveryAddr = true AND self.isInvoicingAddr = false";
			}
			resetDefaultAddress(partner,query);
		}
		
		partnerAddress.setIsInvoicingAddr(isInvoicing);
		partnerAddress.setIsDeliveryAddr(isDelivery);
		partnerAddress.setIsDefaultAddr(isDefault);
		partner.addPartnerAddressListItem(partnerAddress);
		
		return partner;
	}
	
	public void addContactToPartner(Partner contact) {
		if (contact.getMainPartner() != null) {
			Partner partner = contact.getMainPartner();

			partner.addContactPartnerSetItem(contact);
			savePartner(partner);
		}
	}


	private Address getAddress(Partner partner, String querySpecific, String queryComman){

		if(partner != null){
		    if (partner.getPartnerAddressList() != null && partner.getPartnerAddressList().size() == 1) {
		        return partner.getPartnerAddressList().get(0).getAddress();
		    }

			PartnerAddressRepository partnerAddressRepo = Beans.get(PartnerAddressRepository.class);
			List<PartnerAddress> partnerAddressList = partnerAddressRepo.all().filter(querySpecific, partner.getId()).fetch();
			if(partnerAddressList.isEmpty()){
				partnerAddressList = partnerAddressRepo.all().filter(queryComman, partner.getId()).fetch();
			}
			if(partnerAddressList.size() == 1){
				return partnerAddressList.get(0).getAddress();
			}
			for(PartnerAddress partnerAddress : partnerAddressList){
				if(partnerAddress.getIsDefaultAddr()){
					return partnerAddress.getAddress();
				}
			}
		}
		
		return null;
	}

	public Address getInvoicingAddress(Partner partner){

		return getAddress(partner, "self.partner.id = ?1 AND self.isInvoicingAddr = true AND self.isDeliveryAddr = false AND self.isDefaultAddr = true",
				"self.partner.id = ?1 AND self.isInvoicingAddr = true");
	}

	public Address getDeliveryAddress(Partner partner){

		return getAddress(partner, "self.partner.id = ?1 AND self.isDeliveryAddr = true AND self.isInvoicingAddr = false AND self.isDefaultAddr = true",
				"self.partner.id = ?1 AND self.isDeliveryAddr = true");
	}
	
	public Address getDefaultAddress(Partner partner){
		
		return getAddress(partner, "self.partner.id = ?1 AND self.isDeliveryAddr = true AND self.isInvoicingAddr = true AND self.isDefaultAddr = true",
				"self.partner.id = ?1 AND self.isDefaultAddr = true");
	}

	@Transactional
	public Partner savePartner(Partner partner){
		return partnerRepo.save(partner);
	}
	
	public BankDetails getDefaultBankDetails(Partner partner){
		
		for(BankDetails bankDetails : partner.getBankDetailsList()){
			if(bankDetails.getIsDefault()){
				return bankDetails;
			}
		}
		
		return null;
	}
	
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public String getSIRENNumber(Partner partner) throws AxelorException {
		char[] Str = new char[9];
		if (partner.getRegistrationCode() == null || partner.getRegistrationCode().isEmpty()) {
			throw new AxelorException(partner, IException.CONFIGURATION_ERROR, I18n.get(IExceptionMessage.PARTNER_2), AppBaseServiceImpl.EXCEPTION,partner.getName());
		} else {
            String registrationCode = partner.getRegistrationCode();
			//remove whitespace in the registration code before using it
            registrationCode.replaceAll("\\s","").getChars(0, 9, Str, 0);
		}
		
		return new String(Str);
	}
	
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void convertToIndividualPartner(Partner partner) {
		partner.setIsContact(false);
		partner.setPartnerTypeSelect(PartnerRepository.PARTNER_TYPE_INDIVIDUAL);
		addPartnerAddress(partner, partner.getContactAddress(), true, false, false);
		partner.setContactAddress(null);
	}

	/**
	 * Check if the partner in view has a duplicate.
	 * @param partner a context partner object
	 * @return if there is a duplicate partner
	 */
	public boolean isThereDuplicatePartner(Partner partner) {
		String newName = this.computeFullName(partner);
		if (Strings.isNullOrEmpty(newName)) {
		    return false;
		}
		Long partnerId = partner.getId();
		if (partnerId == null) {
			Partner existingPartner = partnerRepo.all()
					.filter("lower(self.fullName) = lower(:newName) " +
							"and self.partnerTypeSelect = :_partnerTypeSelect")
					.bind("newName", newName)
					.bind("_partnerTypeSelect", partner.getPartnerTypeSelect())
					.fetchOne();
			return existingPartner != null;
		} else {
			Partner existingPartner = partnerRepo.all()
					.filter("lower(self.fullName) = lower(:newName) " +
							"and self.id != :partnerId " +
							"and self.partnerTypeSelect = :_partnerTypeSelect")
					.bind("newName", newName)
					.bind("partnerId", partnerId)
					.bind("_partnerTypeSelect", partner.getPartnerTypeSelect())
					.fetchOne();
			return existingPartner != null;
		}
	}

	/**
     * Search for the sale price list for the current date in the partner.
	 * @param partner
	 * @return  the sale price list for the partner
	 *          null if no active price list has been found
	 */
	public PriceList getSalePriceList(Partner partner) {
		PartnerPriceList partnerPriceList = partner.getSalePartnerPriceList();
		if (partnerPriceList == null) {
			return null;
		}
		Set<PriceList> priceListSet =  partnerPriceList.getPriceListSet();
		if (priceListSet == null) {
			return null;
		}
		LocalDate today = Beans.get(AppBaseService.class).getTodayDate();
		List<PriceList> candidatePriceListList = new ArrayList<>();
		for (PriceList priceList : priceListSet) {
			LocalDate beginDate = priceList.getApplicationBeginDate() != null
					? priceList.getApplicationBeginDate()
					: LocalDate.MIN;
			LocalDate endDate = priceList.getApplicationEndDate() != null
					? priceList.getApplicationEndDate()
					: LocalDate.MAX;
			if (beginDate.compareTo(today) <= 0 && today.compareTo(endDate) <= 0) {
			    candidatePriceListList.add(priceList);
			}
		}

		//if we found multiple price list, then the user will have to select one
		if (candidatePriceListList.size() == 1) {
			return candidatePriceListList.get(0);
		} else {
			return null;
		}
	}

    /**
     * If there is only one partner address, set it as default invoicing and delivery address.
     * 
     * @param partner
     * @return whether the record was changed.
     */
    public boolean setDefaultPartnerAdressIfSingle(Partner partner) {
        Preconditions.checkNotNull(partner);

        if (partner.getPartnerAddressList() == null || partner.getPartnerAddressList().isEmpty()
                || partner.getPartnerAddressList().size() > 1) {
            return false;
        }

        PartnerAddress partnerAddress = partner.getPartnerAddressList().get(0);

        partnerAddress.setIsDefaultAddr(true);
        partnerAddress.setIsInvoicingAddr(true);
        partnerAddress.setIsDeliveryAddr(true);

        return true;
    }

    /**
     * If there is only one bank details item, set it as default.
     * 
     * @param partner
     * @return
     */
    public boolean setDefaultBankDetailsIfSingle(Partner partner) {
        Preconditions.checkNotNull(partner);

        if (partner.getBankDetailsList() == null || partner.getBankDetailsList().isEmpty()
                || partner.getBankDetailsList().size() > 1) {
            return false;
        }

        BankDetails bankDetails = partner.getBankDetailsList().get(0);
        bankDetails.setIsDefault(true);

        return true;
    }

}
