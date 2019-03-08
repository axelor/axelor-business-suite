/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BankRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.report.IReport;
import com.axelor.apps.base.service.BankDetailsService;
import com.axelor.apps.base.service.MapService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
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
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.birt.core.exception.BirtException;
import org.iban4j.IbanFormatException;
import org.iban4j.InvalidCheckDigitException;
import org.iban4j.UnsupportedCountryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PartnerController {

  @Inject private PartnerService partnerService;

  @Inject private PartnerRepository partnerRepo;

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void setPartnerSequence(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Partner partner = request.getContext().asType(Partner.class);
    partner = partnerRepo.find(partner.getId());
    if (partner.getPartnerSeq() == null) {
      String seq = Beans.get(SequenceService.class).getSequenceNumber(SequenceRepository.PARTNER);
      if (seq == null)
        throw new AxelorException(
            partner,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.PARTNER_1));
      else response.setValue("partnerSeq", seq);
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
  public void showEnvelope(ActionRequest request, ActionResponse response) throws AxelorException {
    Partner partner = request.getContext().asType(Partner.class);

    String name = I18n.get("Partner") + " " + partner.getPartnerSeq();

    String fileLink =
        ReportFactory.createReport(
                "partner", getActiveCompany(), IReport.PARTNER, name + "-${date}")
            .addParam("Locale", ReportSettings.getPrintingLocale(partner))
            .addParam("PartnerId", partner.getId())
            .generate()
            .getFileLink();

    LOG.debug("Printing " + name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
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
  public void printContactPhonebook(ActionRequest request, ActionResponse response)
      throws AxelorException {
    User user = AuthUtils.getUser();

    String name = I18n.get("Phone Book");

    String fileLink =
        ReportFactory.createReport(
                "phoneBook", getActiveCompany(), IReport.PHONE_BOOK, name + "-${date}")
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .addParam("UserId", user.getId())
            .generate()
            .getFileLink();

    LOG.debug("Printing " + name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
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
  public void printCompanyPhonebook(ActionRequest request, ActionResponse response)
      throws AxelorException {
    User user = AuthUtils.getUser();

    String name = I18n.get("Company PhoneBook");

    String fileLink =
        ReportFactory.createReport(
                "companyPhoneBook",
                getActiveCompany(),
                IReport.COMPANY_PHONE_BOOK,
                name + "-${date}")
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .addParam("UserId", user.getId())
            .generate()
            .getFileLink();

    LOG.debug("Printing " + name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }

  /* Fonction appeler par le bouton imprimer
   *
   * @param request
   * @param response
   * @return
   */
  public void printClientSituation(ActionRequest request, ActionResponse response)
      throws AxelorException {

    Partner partner = request.getContext().asType(Partner.class);

    User user = AuthUtils.getUser();

    String name = I18n.get("Customer Situation");
    String fileLink =
        ReportFactory.createReport(
                "clientSituation", getActiveCompany(), IReport.CLIENT_SITUATION, name + "-${date}")
            .addParam("Locale", ReportSettings.getPrintingLocale(partner))
            .addParam("UserId", user.getId())
            .addParam("PartnerId", partner.getId())
            .addParam(
                "PartnerPic",
                partner.getPicture() != null
                    ? MetaFiles.getPath(partner.getPicture()).toString()
                    : "")
            .generate()
            .getFileLink();

    LOG.debug("Printing " + name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }

  public Company getActiveCompany() {
    Company company = Beans.get(UserService.class).getUser().getActiveCompany();
    if (company == null) {
      List<Company> companyList = Beans.get(CompanyRepository.class).all().fetch();
      if (companyList.size() == 1) {
        company = companyList.get(0);
      }
    }
    return company;
  }

  public void setSocialNetworkUrl(ActionRequest request, ActionResponse response) {
    Partner partner = request.getContext().asType(Partner.class);
    Map<String, String> urlMap =
        partnerService.getSocialNetworkUrl(
            partner.getName(), partner.getFirstName(), partner.getPartnerTypeSelect());
    response.setAttr("googleLabel", "title", urlMap.get("google"));
    response.setAttr("facebookLabel", "title", urlMap.get("facebook"));
    response.setAttr("twitterLabel", "title", urlMap.get("twitter"));
    response.setAttr("linkedinLabel", "title", urlMap.get("linkedin"));
    response.setAttr("youtubeLabel", "title", urlMap.get("youtube"));
  }

  public void findPartnerMails(ActionRequest request, ActionResponse response) {
    Partner partner = request.getContext().asType(Partner.class);
    List<Long> idList = partnerService.findPartnerMails(partner);

    List<Message> emailsList = new ArrayList<Message>();
    for (Long id : idList) {
      Message message = Beans.get(MessageRepository.class).find(id);
      if (!emailsList.contains(message)) {
        emailsList.add(message);
      }
    }

    response.setValue("$emailsList", emailsList);
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
      if (!emailsList.contains(message)) {
        emailsList.add(message);
      }
    }

    response.setValue("$emailsList", emailsList);
  }

  public void checkIbanValidity(ActionRequest request, ActionResponse response)
      throws AxelorException {

    List<BankDetails> bankDetailsList =
        request.getContext().asType(Partner.class).getBankDetailsList();
    List<String> ibanInError = Lists.newArrayList();

    if (bankDetailsList != null && !bankDetailsList.isEmpty()) {
      for (BankDetails bankDetails : bankDetailsList) {
        Bank bank = bankDetails.getBank();
        if (bankDetails.getIban() != null
            && bank != null
            && bank.getBankDetailsTypeSelect() == BankRepository.BANK_IDENTIFIER_TYPE_IBAN) {
          LOG.debug("checking iban code : {}", bankDetails.getIban());
          try {
            Beans.get(BankDetailsService.class).validateIban(bankDetails.getIban());
          } catch (IbanFormatException
              | InvalidCheckDigitException
              | UnsupportedCountryException e) {
            ibanInError.add(bankDetails.getIban());
          }
        }
      }
    }
    if (!ibanInError.isEmpty()) {

      Function<String, String> addLi =
          new Function<String, String>() {
            @Override
            public String apply(String s) {
              return "<li>".concat(s).concat("</li>").toString();
            }
          };

      response.setAlert(
          String.format(
              IExceptionMessage.BANK_DETAILS_2,
              "<ul>" + Joiner.on("").join(Iterables.transform(ibanInError, addLi)) + "<ul>"));
    }
  }

  public void normalizePhoneNumber(ActionRequest request, ActionResponse response) {
    try {
      String phoneNumberFieldName = partnerService.getPhoneNumberFieldName(request.getAction());
      String phoneNumber = (String) request.getContext().get(phoneNumberFieldName);

      if (!StringUtils.isBlank(phoneNumber)) {
        String normalizedPhoneNumber = partnerService.normalizePhoneNumber(phoneNumber);

        if (!phoneNumber.equals(normalizedPhoneNumber)) {
          response.setValue(phoneNumberFieldName, normalizedPhoneNumber);
        }

        if (!partnerService.checkPhoneNumber(normalizedPhoneNumber)) {
          response.addError(phoneNumberFieldName, I18n.get("Invalid phone number"));
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void convertToIndividualPartner(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Partner partner = request.getContext().asType(Partner.class);
    if (partner.getId() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, I18n.get(IExceptionMessage.PARTNER_3));
    }
    partner = partnerRepo.find(partner.getId());
    partnerService.convertToIndividualPartner(partner);
  }

  /**
   * Called from partner view on name change and onLoad. Call {@link
   * PartnerService#isThereDuplicatePartner(Partner)}
   *
   * @param request
   * @param response
   */
  public void checkPartnerName(ActionRequest request, ActionResponse response) {
    Partner partner = request.getContext().asType(Partner.class);
    response.setAttr(
        "duplicatePartnerText", "hidden", !partnerService.isThereDuplicatePartner(partner));
  }

  public void showPartnerOnMap(ActionRequest request, ActionResponse response) {
    try {
      Partner partner = request.getContext().asType(Partner.class);
      response.setView(
          ActionView.define(partner.getFullName())
              .add("html", Beans.get(MapService.class).getMapURI("partner", partner.getId()))
              .map());
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }
}
