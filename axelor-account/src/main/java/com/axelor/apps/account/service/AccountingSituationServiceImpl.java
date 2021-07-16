/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
import com.axelor.apps.account.db.AccountType;
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.AccountConfigRepository;
import com.axelor.apps.account.db.repo.AccountingSituationRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Sequence;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.tool.StringTool;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

public class AccountingSituationServiceImpl implements AccountingSituationService {

  private static final char TEMPLATE_DELIMITER = '$';

  protected AccountConfigService accountConfigService;
  protected SequenceService sequenceService;
  protected AccountingSituationRepository accountingSituationRepo;

  @Inject
  public AccountingSituationServiceImpl(
      AccountConfigService accountConfigService,
      SequenceService sequenceService,
      AccountingSituationRepository accountingSituationRepo) {
    this.accountConfigService = accountConfigService;
    this.sequenceService = sequenceService;
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
  @Transactional(rollbackOn = {Exception.class})
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
  @Transactional(rollbackOn = {Exception.class})
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
      if (accountingSituation.getCompany() != null
          && accountingSituation.getCompany().equals(company)) {
        return accountingSituation;
      }
    }

    return null;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void createPartnerAccounts(AccountingSituation situation) throws AxelorException {
    AccountConfig accountConfig = situation.getCompany().getAccountConfig();
    int creationMode;
    if (accountConfig == null
        || (creationMode = accountConfig.getPartnerAccountGenerationModeSelect())
            == AccountConfigRepository.AUTOMATIC_ACCOUNT_CREATION_NONE) {
      // Ignore even if account config is null since this means no automatic creation
      return;
    }
    createCustomerAccount(accountConfig, situation, creationMode);
    createSupplierAccount(accountConfig, situation, creationMode);
    createEmployeeAccount(accountConfig, situation, creationMode);
  }

  protected void createCustomerAccount(
      AccountConfig accountConfig, AccountingSituation situation, int creationMode)
      throws AxelorException {
    Partner partner = situation.getPartner();
    if (partner.getIsCustomer() == Boolean.FALSE || situation.getCustomerAccount() != null) return;

    if (accountConfig.getCustomerAccount() == null) {
      throw new AxelorException(
          partner,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.ACCOUNT_CUSTOMER_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          situation.getCompany().getName());
    }

    final String accountCode;
    if (creationMode == AccountConfigRepository.AUTOMATIC_ACCOUNT_CREATION_PREFIX) {
      final String prefix = accountConfig.getCustomerAccountPrefix();
      if (StringUtils.isBlank(prefix)) {
        throw new AxelorException(
            situation,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.ACCOUNTING_SITUATION_1),
            situation.getCompany().getName());
      }
      CompilerConfiguration conf = new CompilerConfiguration();
      ImportCustomizer customizer = new ImportCustomizer();
      customizer.addStaticStars("java.lang.Math");
      conf.addCompilationCustomizers(customizer);
      Binding binding = new Binding();
      binding.setVariable("partner", partner);
      GroovyShell shell = new GroovyShell(binding, conf);
      accountCode = shell.evaluate(prefix).toString();

    } else if (creationMode == AccountConfigRepository.AUTOMATIC_ACCOUNT_CREATION_SEQUENCE) {
      final Sequence sequence = accountConfig.getCustomerAccountSequence();
      if (sequence == null) {
        throw new AxelorException(
            situation,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.ACCOUNTING_SITUATION_2),
            situation.getCompany().getName());
      }
      accountCode = sequenceService.getSequenceNumber(sequence);
    } else {
      throw new AxelorException(
          situation,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.ACCOUNTING_SITUATION_3),
          situation.getCompany().getName());
    }

    Account account =
        this.createAccount(
            partner.getFullName(),
            accountCode,
            accountConfig.getCustomerAccount(),
            accountConfig.getCustomerAccount().getAccountType(),
            true,
            situation.getCompany(),
            true);
    situation.setCustomerAccount(account);
  }

  protected void createSupplierAccount(
      AccountConfig accountConfig, AccountingSituation situation, int creationMode)
      throws AxelorException {
    Partner partner = situation.getPartner();
    if (partner.getIsSupplier() == Boolean.FALSE || situation.getSupplierAccount() != null) return;

    if (accountConfig.getSupplierAccount() == null) {
      throw new AxelorException(
          partner,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.ACCOUNT_CUSTOMER_2),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          situation.getCompany().getName());
    }

    final String accountCode;
    if (creationMode == AccountConfigRepository.AUTOMATIC_ACCOUNT_CREATION_PREFIX) {
      final String prefix = accountConfig.getSupplierAccountPrefix();
      if (StringUtils.isBlank(prefix)) {
        throw new AxelorException(
            situation,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.ACCOUNTING_SITUATION_4),
            situation.getCompany().getName());
      }
      CompilerConfiguration conf = new CompilerConfiguration();
      ImportCustomizer customizer = new ImportCustomizer();
      customizer.addStaticStars("java.lang.Math");
      conf.addCompilationCustomizers(customizer);
      Binding binding = new Binding();
      binding.setVariable("partner", partner);
      GroovyShell shell = new GroovyShell(binding, conf);
      accountCode = shell.evaluate(prefix).toString();

    } else if (creationMode == AccountConfigRepository.AUTOMATIC_ACCOUNT_CREATION_SEQUENCE) {
      final Sequence sequence = accountConfig.getSupplierAccountSequence();
      if (sequence == null) {
        throw new AxelorException(
            situation,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.ACCOUNTING_SITUATION_5),
            situation.getCompany().getName());
      }
      accountCode = sequenceService.getSequenceNumber(sequence);
    } else {
      throw new AxelorException(
          situation,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.ACCOUNTING_SITUATION_3),
          situation.getCompany().getName());
    }

    Account account =
        this.createAccount(
            partner.getFullName(),
            accountCode,
            accountConfig.getSupplierAccount(),
            accountConfig.getSupplierAccount().getAccountType(),
            true,
            situation.getCompany(),
            true);
    situation.setSupplierAccount(account);
  }

  protected void createEmployeeAccount(
      AccountConfig accountConfig, AccountingSituation situation, int creationMode)
      throws AxelorException {
    Partner partner = situation.getPartner();
    if (partner.getIsEmployee() == Boolean.FALSE || situation.getEmployeeAccount() != null) return;

    if (accountConfig.getEmployeeAccount() == null) {
      throw new AxelorException(
          partner,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_40),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          situation.getCompany().getName());
    }

    final String accountCode;
    if (creationMode == AccountConfigRepository.AUTOMATIC_ACCOUNT_CREATION_PREFIX) {
      final String prefix = accountConfig.getEmployeeAccountPrefix();
      if (StringUtils.isBlank(prefix)) {
        throw new AxelorException(
            situation,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.ACCOUNTING_SITUATION_6),
            situation.getCompany().getName());
      }
      accountCode = getPrefixedAccountCode(prefix, partner);
    } else if (creationMode == AccountConfigRepository.AUTOMATIC_ACCOUNT_CREATION_SEQUENCE) {
      final Sequence sequence = accountConfig.getEmployeeAccountSequence();
      if (sequence == null) {
        throw new AxelorException(
            situation,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.ACCOUNTING_SITUATION_7),
            situation.getCompany().getName());
      }
      accountCode = sequenceService.getSequenceNumber(sequence);
    } else {
      throw new AxelorException(
          situation,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.ACCOUNTING_SITUATION_3),
          situation.getCompany().getName());
    }

    Account account =
        this.createAccount(
            partner.getFullName(),
            accountCode,
            accountConfig.getEmployeeAccount(),
            accountConfig.getEmployeeAccount().getAccountType(),
            true,
            situation.getCompany(),
            true);
    situation.setEmployeeAccount(account);
  }

  /**
   * Normalize partner's fullname to be usable as an account name. Name is appended to prefix, then
   * uppercased and unaccented
   *
   * @param prefix Prefix to prepend to the generated account code
   * @param partner Partner to generate account code for
   * @return The generated account code.
   */
  protected String getPrefixedAccountCode(String prefix, Partner partner) {
    return (prefix + StringUtils.stripAccent(partner.getFullName()))
        .toUpperCase()
        .replaceAll("[^A-Z0-9]", "");
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

  protected Account createAccount(
      String fullName,
      String accountCode,
      Account parentAccount,
      AccountType accountType,
      boolean reconcileOk,
      Company company,
      boolean useForPartnerBalance) {

    Account account = new Account();
    account.setName(fullName);
    account.setCode(accountCode);
    account.setParentAccount(parentAccount);
    account.setAccountType(accountType);
    account.setReconcileOk(reconcileOk);
    account.setCompany(company);
    account.setUseForPartnerBalance(useForPartnerBalance);
    account.setCompatibleAccountSet(new HashSet<>());

    return account;
  }
}
