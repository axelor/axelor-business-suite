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
package com.axelor.apps.base.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Bank;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.IAdministration;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BankRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.report.IReport;
import com.axelor.apps.base.service.BankDetailsService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.eclipse.birt.core.exception.BirtException;
import org.iban4j.IbanFormatException;
import org.iban4j.InvalidCheckDigitException;
import org.iban4j.UnsupportedCountryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PartnerController {

	@Inject
	private SequenceService sequenceService;

	@Inject
	private UserService userService;

	@Inject
	private PartnerService partnerService;
	
	@Inject
	private PartnerRepository partnerRepo;
	
	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public void setPartnerSequence(ActionRequest request, ActionResponse response) throws AxelorException {
		Partner partner = request.getContext().asType(Partner.class);
		partner = partnerRepo.find(partner.getId());
		if(partner.getPartnerSeq() ==  null) {
			String seq = sequenceService.getSequenceNumber(IAdministration.PARTNER);
			if (seq == null)
				throw new AxelorException(partner, IException.CONFIGURATION_ERROR, I18n.get(IExceptionMessage.PARTNER_1));
			else
				response.setValue("partnerSeq", seq);
		}
	}

	
	/**
	 * Fonction appeler par le bouton imprimer
	 *
	 * @param request
	 * @param response
	 * @return
	 * @throws BirtException 
	 * @throws IOException 
	 */
	public void showPartnerInfo(ActionRequest request, ActionResponse response) throws AxelorException {
		Partner partner = request.getContext().asType(Partner.class);

		String name = I18n.get("Partner")+" "+partner.getPartnerSeq();
		
		String fileLink = ReportFactory.createReport(IReport.PARTNER, name+"-${date}")
					.addParam("Locale", ReportSettings.getPrintingLocale(partner))
					.addParam("PartnerId", partner.getId())
					.generate()
					.getFileLink();

		LOG.debug("Printing "+name);

		response.setView(ActionView
				.define(name)
				.add("html", fileLink).map());

	}

	/**
	 * Fonction appeler par le bouton imprimer
	 *
	 * @param request
	 * @param response
	 * @return
	 * @throws BirtException 
	 * @throws IOException 
	 */
	public void printContactPhonebook(ActionRequest request, ActionResponse response) throws AxelorException {
		User user = AuthUtils.getUser();

		String name = I18n.get("Phone Book");
		
		String fileLink = ReportFactory.createReport(IReport.PHONE_BOOK, name+"-${date}")
					.addParam("Locale", ReportSettings.getPrintingLocale(null))
					.addParam("UserId", user.getId())
					.generate()
					.getFileLink();

		LOG.debug("Printing "+name);

		response.setView(ActionView
				.define(name)
				.add("html", fileLink).map());
	}

	/**
	 * Fonction appeler par le bouton imprimer
	 *
	 * @param request
	 * @param response
	 * @return
	 * @throws BirtException 
	 * @throws IOException 
	 */
	public void printCompanyPhonebook(ActionRequest request, ActionResponse response) throws AxelorException {
		User user = AuthUtils.getUser();

		String name = I18n.get("Company PhoneBook");
		
		String fileLink = ReportFactory.createReport(IReport.COMPANY_PHONE_BOOK, name+"-${date}")
					.addParam("Locale", ReportSettings.getPrintingLocale(null))
					.addParam("UserId", user.getId())
					.generate()
					.getFileLink();

		LOG.debug("Printing "+name);

		response.setView(ActionView
				.define(name)
				.add("html", fileLink).map());
	}


	/* Fonction appeler par le bouton imprimer
	 *
	 * @param request
	 * @param response
	 * @return
	 */
	public void printClientSituation(ActionRequest request, ActionResponse response) throws AxelorException {

		Partner partner = request.getContext().asType(Partner.class);

		User user = AuthUtils.getUser();

		String name = I18n.get("Client Situation");
		
		String fileLink = ReportFactory.createReport(IReport.CLIENT_SITUATION, name+"-${date}")
				.addParam("Locale", ReportSettings.getPrintingLocale(partner))
				.addParam("UserId", user.getId())
				.addParam("PartnerId", partner.getId())
				.addParam("PartnerPic",partner.getPicture() != null ? MetaFiles.getPath(partner.getPicture()).toString() : "")
				.generate()
				.getFileLink();

		LOG.debug("Printing "+name);
	
		response.setView(ActionView
				.define(name)
				.add("html", fileLink).map());
	}

	public Set<Company> getActiveCompany(){
		Set<Company> companySet = new HashSet<Company>();
		Company company = userService.getUser().getActiveCompany();
		if(company == null){
			List<Company> companyList = Beans.get(CompanyRepository.class).all().fetch();
			if(companyList.size() == 1){
				company = companyList.get(0);
			}
		}
		companySet.add(company);
		return companySet;
	}

	public void setSocialNetworkUrl(ActionRequest request, ActionResponse response) {
		Partner partner = request.getContext().asType(Partner.class);
		Map<String,String> urlMap = partnerService.getSocialNetworkUrl(partner.getName(),partner.getFirstName(),partner.getPartnerTypeSelect());
		response.setAttr("google", "title", urlMap.get("google"));
		response.setAttr("facebook", "title", urlMap.get("facebook"));
		response.setAttr("twitter", "title", urlMap.get("twitter"));
		response.setAttr("linkedin", "title", urlMap.get("linkedin"));
		response.setAttr("youtube", "title", urlMap.get("youtube"));

	}

	public void findPartnerMails(ActionRequest request, ActionResponse response) {
		Partner partner = request.getContext().asType(Partner.class);
		List<Long> idList = partnerService.findPartnerMails(partner);

		List<Message> emailsList = new ArrayList<Message>();
		for (Long id : idList) {
			Message message = Beans.get(MessageRepository.class).find(id);
			if(!emailsList.contains(message)){
				emailsList.add(message);
			}
		}

		response.setValue("$emailsList",emailsList);
	}
	
	public void addContactToPartner(ActionRequest request, ActionResponse response) {
		Partner contact = partnerRepo.find(request.getContext().asType(Partner.class).getId());
		partnerService.addContactToPartner(contact);
	}

	public void findContactMails(ActionRequest request, ActionResponse response) {
		Partner partner = request.getContext().asType(Partner.class);
		List<Long> idList = partnerService.findContactMails(partner);

		List<Message> emailsList = new ArrayList<Message>();
		for (Long id : idList) {
			Message message = Beans.get(MessageRepository.class).find(id);
			if(!emailsList.contains(message)){
				emailsList.add(message);
			}
		}

		response.setValue("$emailsList",emailsList);
	}
	
	public void checkIbanValidity(ActionRequest request, ActionResponse response) throws AxelorException{
		
		List<BankDetails> bankDetailsList = request.getContext().asType(Partner.class).getBankDetailsList();
		List<String> ibanInError = Lists.newArrayList();
		
		if (bankDetailsList !=null && !bankDetailsList.isEmpty()){
			for (BankDetails bankDetails : bankDetailsList) {
				Bank bank = bankDetails.getBank();
				if(bankDetails.getIban() != null && bank != null
						&& bank.getBankDetailsTypeSelect()
						== BankRepository.BANK_IDENTIFIER_TYPE_IBAN) {
					LOG.debug("checking iban code : {}", bankDetails.getIban());
					try {
						Beans.get(BankDetailsService.class).validateIban(bankDetails.getIban());
					} catch (IbanFormatException | InvalidCheckDigitException | UnsupportedCountryException e) {
						ibanInError.add(bankDetails.getIban());
					}
				}
			}
		}
		if (!ibanInError.isEmpty()){
			
			Function<String,String> addLi = new Function<String,String>() {
				  @Override public String apply(String s) {
				    return "<li>".concat(s).concat("</li>").toString();
				  }
				};
			
			response.setAlert(String.format(IExceptionMessage.BANK_DETAILS_2, "<ul>" + Joiner.on("").join(Iterables.transform(ibanInError, addLi)) + "<ul>"));
		}
	}
	
	public String normalizePhoneNumber(String phoneNumber){
		return phoneNumber.replaceAll("\\s|\\.", "");
	}
	
	public void convertToIndividualPartner(ActionRequest request, ActionResponse response) throws AxelorException {
		Partner partner = request.getContext().asType(Partner.class);
		if (partner.getId() == null) {
			throw new AxelorException(IException.CONFIGURATION_ERROR, I18n.get(IExceptionMessage.PARTNER_3));
		}
		partner = partnerRepo.find(partner.getId());
		partnerService.convertToIndividualPartner(partner);
	}

	/**
	 * Called from partner view on name change and onLoad.
	 * Call {@link PartnerService#isThereDuplicatePartner(Partner)}
	 * @param request
	 * @param response
	 */
	public void checkPartnerName(ActionRequest request, ActionResponse response) {
		Partner partner = request.getContext().asType(Partner.class);
		response.setAttr("duplicatePartnerText",
				"hidden",
				!partnerService.isThereDuplicatePartner(partner));
	}

    public void setDefaultsIfSingle(ActionRequest request, ActionResponse response) {
        try {
            Partner partner = request.getContext().asType(Partner.class);

            if (partnerService.setDefaultPartnerAdressIfSingle(partner)) {
                response.setValue("partnerAddressList", partner.getPartnerAddressList());
            }

            if (partnerService.setDefaultBankDetailsIfSingle(partner)) {
                response.setValue("bankDetailsList", partner.getBankDetailsList());
            }
        } catch (Exception e) {
            TraceBackService.trace(response, e);

        }
    }

}
