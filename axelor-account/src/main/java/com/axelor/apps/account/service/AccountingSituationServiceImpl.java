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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.AccountingSituationRepository;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.tool.StringTool;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.util.List;
import java.util.Set;

@Singleton
public class AccountingSituationServiceImpl implements AccountingSituationService {

  protected AccountConfigService accountConfigService;
  protected AccountingSituationRepository accountingSituationRepo;

  @Inject
  public AccountingSituationServiceImpl(
      AccountConfigService accountConfigService,
      AccountingSituationRepository accountingSituationRepo) {
    this.accountConfigService = accountConfigService;
    this.accountingSituationRepo = accountingSituationRepo;
  }

  @Override
  public boolean checkAccountingSituationList(
      List<AccountingSituation> accountingSituationList, Company company) {

    if (accountingSituationList != null) {
      for (AccountingSituation accountingSituation : accountingSituationList) {

        if (accountingSituation.getCompany().equals(company)) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public List<AccountingSituation> createAccountingSituation(Partner partner)
      throws AxelorException {
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

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public AccountingSituation createAccountingSituation(Partner partner, Company company)
      throws AxelorException {
    AccountingSituation accountingSituation = new AccountingSituation();
    accountingSituation.setCompany(company);
    partner.addCompanySetItem(company);

    PaymentMode inPaymentMode = partner.getInPaymentMode();
    PaymentMode outPaymentMode = partner.getOutPaymentMode();
    BankDetails defaultBankDetails = company.getDefaultBankDetails();

    if (inPaymentMode != null) {
      List<BankDetails> authorizedInBankDetails =
          Beans.get(PaymentModeService.class).getCompatibleBankDetailsList(inPaymentMode, company);
      if (authorizedInBankDetails.contains(defaultBankDetails)) {
        accountingSituation.setCompanyInBankDetails(defaultBankDetails);
      }
    }

    if (outPaymentMode != null) {
      List<BankDetails> authorizedOutBankDetails =
          Beans.get(PaymentModeService.class).getCompatibleBankDetailsList(outPaymentMode, company);
      if (authorizedOutBankDetails.contains(defaultBankDetails)) {
        accountingSituation.setCompanyOutBankDetails(defaultBankDetails);
      }
    }

    AccountConfig accountConfig = Beans.get(AccountConfigService.class).getAccountConfig(company);
    accountingSituation.setInvoiceAutomaticMail(accountConfig.getInvoiceAutomaticMail());
    accountingSituation.setInvoiceMessageTemplate(accountConfig.getInvoiceMessageTemplate());

    partner.addAccountingSituationListItem(accountingSituation);
    return accountingSituationRepo.save(accountingSituation);
  }

  @Override
  public AccountingSituation getAccountingSituation(Partner partner, Company company) {
    if (partner == null || partner.getAccountingSituationList() == null) {
      return null;
    }

    for (AccountingSituation accountingSituation : partner.getAccountingSituationList()) {
      if (accountingSituation.getCompany().equals(company)) {
        return accountingSituation;
      }
    }

    return null;
  }

  /**
   * Creates the domain for the bank details in Accounting Situation
   *
   * @param accountingSituation
   * @param isInBankDetails true if the field is companyInBankDetails false if the field is
   *     companyOutBankDetails
   * @return the domain of the bank details field
   */
  @Override
  public String createDomainForBankDetails(
      AccountingSituation accountingSituation, boolean isInBankDetails) {
    String domain = "";
    List<BankDetails> authorizedBankDetails;
    if (accountingSituation.getPartner() != null) {

      if (isInBankDetails) {
        authorizedBankDetails =
            Beans.get(PaymentModeService.class)
                .getCompatibleBankDetailsList(
                    accountingSituation.getPartner().getInPaymentMode(),
                    accountingSituation.getCompany());
      } else {
        authorizedBankDetails =
            Beans.get(PaymentModeService.class)
                .getCompatibleBankDetailsList(
                    accountingSituation.getPartner().getOutPaymentMode(),
                    accountingSituation.getCompany());
      }
      String idList = StringTool.getIdListString(authorizedBankDetails);
      if (idList.equals("")) {
        return domain;
      }
      domain = "self.id IN (" + idList + ") AND self.active = true";
    }
    return domain;
  }

  @Override
  public void updateCustomerCredit(Partner partner) throws AxelorException {
    // Nothing to do if the supplychain module is not loaded.
  }

  @Override
  public Account getCustomerAccount(Partner partner, Company company) throws AxelorException {
    Account account = null;
    AccountingSituation accountingSituation = getAccountingSituation(partner, company);

    if (accountingSituation != null) {
      account = accountingSituation.getCustomerAccount();
    }

    if (account == null) {
      AccountConfig accountConfig = accountConfigService.getAccountConfig(company);
      account = accountConfigService.getCustomerAccount(accountConfig);
    }

    return account;
  }

  @Override
  public Account getSupplierAccount(Partner partner, Company company) throws AxelorException {
    Account account = null;
    AccountingSituation accountingSituation = getAccountingSituation(partner, company);

    if (accountingSituation != null) {
      account = accountingSituation.getSupplierAccount();
    }

    if (account == null) {
      AccountConfig accountConfig = accountConfigService.getAccountConfig(company);
      account = accountConfigService.getSupplierAccount(accountConfig);
    }

    return account;
  }

  @Override
  public Account getEmployeeAccount(Partner partner, Company company) throws AxelorException {
    Account account = null;
    AccountingSituation accountingSituation = getAccountingSituation(partner, company);

    if (accountingSituation != null) {
      account = accountingSituation.getEmployeeAccount();
    }

    if (account == null) {
      AccountConfig accountConfig = accountConfigService.getAccountConfig(company);
      account = accountConfigService.getEmployeeAccount(accountConfig);
    }

    return account;
  }

  @Override
  public BankDetails getCompanySalesBankDetails(Company company, Partner partner) {
    AccountingSituation situation = getAccountingSituation(partner, company);
    if (situation != null
        && situation.getCompanyInBankDetails() != null
        && situation.getCompanyInBankDetails().getActive()) {
      return situation.getCompanyInBankDetails();
    }

    return company.getDefaultBankDetails();
  }
}
