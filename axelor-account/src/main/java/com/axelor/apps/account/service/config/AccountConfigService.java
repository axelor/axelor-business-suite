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
package com.axelor.apps.account.service.config;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.DebtRecoveryConfigLine;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.account.db.JournalType;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.repo.AccountConfigRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Sequence;
import com.axelor.apps.message.db.Template;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import java.util.List;

public class AccountConfigService {

  public AccountConfig getAccountConfig(Company company) throws AxelorException {

    AccountConfig accountConfig = company.getAccountConfig();

    if (accountConfig == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          company.getName());
    }

    return accountConfig;
  }

  /** ****************************** EXPORT CFONB ******************************************* */
  public void getReimbursementExportFolderPathCFONB(AccountConfig accountConfig)
      throws AxelorException {

    if (accountConfig.getReimbursementExportFolderPathCFONB() == null
        || accountConfig.getReimbursementExportFolderPathCFONB().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_2),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
  }

  public void getPaymentScheduleExportFolderPathCFONB(AccountConfig accountConfig)
      throws AxelorException {

    if (accountConfig.getPaymentScheduleExportFolderPathCFONB() == null
        || accountConfig.getPaymentScheduleExportFolderPathCFONB().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_3),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
  }

  public void getRejectImportPathAndFileName(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getRejectImportPathAndFileName() == null
        || accountConfig.getRejectImportPathAndFileName().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_8),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
  }

  public void getTempImportPathAndFileName(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getTempImportPathAndFileName() == null
        || accountConfig.getTempImportPathAndFileName().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_9),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
  }

  public void getReimbursementImportFolderPathCFONB(AccountConfig accountConfig)
      throws AxelorException {

    if (accountConfig.getReimbursementImportFolderPathCFONB() == null
        || accountConfig.getReimbursementImportFolderPathCFONB().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_10),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
  }

  public void getTempReimbImportFolderPathCFONB(AccountConfig accountConfig)
      throws AxelorException {

    if (accountConfig.getTempReimbImportFolderPathCFONB() == null
        || accountConfig.getTempReimbImportFolderPathCFONB().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_11),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
  }

  /** ****************************** JOURNAL ******************************************* */
  public Journal getRejectJournal(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getRejectJournal() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_12),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getRejectJournal();
  }

  public Journal getIrrecoverableJournal(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getIrrecoverableJournal() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_13),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getIrrecoverableJournal();
  }

  public Journal getSupplierPurchaseJournal(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getSupplierPurchaseJournal() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_14),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getSupplierPurchaseJournal();
  }

  public Journal getSupplierCreditNoteJournal(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getSupplierCreditNoteJournal() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_15),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getSupplierCreditNoteJournal();
  }

  public Journal getCustomerSalesJournal(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getCustomerSalesJournal() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_16),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getCustomerSalesJournal();
  }

  public Journal getCustomerCreditNoteJournal(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getCustomerCreditNoteJournal() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_17),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getCustomerCreditNoteJournal();
  }

  public Journal getAutoMiscOpeJournal(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getAutoMiscOpeJournal() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_18),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getAutoMiscOpeJournal();
  }

  public Journal getReimbursementJournal(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getReimbursementJournal() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_19),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getReimbursementJournal();
  }

  /** ****************************** JOURNAL TYPE ******************************************* */
  public JournalType getSaleJournalType(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getSaleJournalType() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_20),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getSaleJournalType();
  }

  public JournalType getCreditNoteJournalType(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getCreditNoteJournalType() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_21),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getCreditNoteJournalType();
  }

  public JournalType getCashJournalType(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getCashJournalType() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_22),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getCashJournalType();
  }

  public JournalType getPurchaseJournalType(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getPurchaseJournalType() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_23),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getPurchaseJournalType();
  }

  /** ****************************** ACCOUNT ******************************************* */
  public Account getIrrecoverableAccount(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getIrrecoverableAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_24),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getIrrecoverableAccount();
  }

  public Account getCustomerAccount(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getCustomerAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_25),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getCustomerAccount();
  }

  public Account getSupplierAccount(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getSupplierAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_26),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getSupplierAccount();
  }

  public Account getEmployeeAccount(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getEmployeeAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_40),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getEmployeeAccount();
  }

  public Account getAdvancePaymentAccount(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getAdvancePaymentAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_38),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getAdvancePaymentAccount();
  }

  public Account getCashPositionVariationAccount(AccountConfig accountConfig)
      throws AxelorException {

    if (accountConfig.getCashPositionVariationAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_27),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getCashPositionVariationAccount();
  }

  public Account getReimbursementAccount(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getReimbursementAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_28),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getReimbursementAccount();
  }

  public Account getDoubtfulCustomerAccount(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getDoubtfulCustomerAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_29),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getDoubtfulCustomerAccount();
  }

  public Account getEmployeeCbAccount(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getEmployeeCbAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_43));
    }
    return accountConfig.getEmployeeCbAccount();
  }

  /** ****************************** TVA ******************************************* */
  public Tax getIrrecoverableStandardRateTax(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getIrrecoverableStandardRateTax() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CLEARANCE_3),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getIrrecoverableStandardRateTax();
  }

  /** ****************************** PAYMENT MODE ******************************************* */
  public PaymentMode getDirectDebitPaymentMode(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getDirectDebitPaymentMode() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_30),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getDirectDebitPaymentMode();
  }

  public PaymentMode getRejectionPaymentMode(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getRejectionPaymentMode() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_31),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getRejectionPaymentMode();
  }

  /** ****************************** OTHER ******************************************* */
  public String getIrrecoverableReasonPassage(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getIrrecoverableReasonPassage() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_32),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getIrrecoverableReasonPassage();
  }

  public Template getRejectPaymentScheduleTemplate(AccountConfig accountConfig)
      throws AxelorException {

    if (accountConfig.getRejectPaymentScheduleTemplate() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_34),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getRejectPaymentScheduleTemplate();
  }

  public String getReimbursementExportFolderPath(AccountConfig accountConfig)
      throws AxelorException {

    if (accountConfig.getReimbursementExportFolderPath() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          "%s :\n " + I18n.get(IExceptionMessage.REIMBURSEMENT_2),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getReimbursementExportFolderPath();
  }

  public String getSixMonthDebtPassReason(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getSixMonthDebtPassReason() == null
        || accountConfig.getSixMonthDebtPassReason().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_35),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getSixMonthDebtPassReason();
  }

  public String getThreeMonthDebtPassReason(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getThreeMonthDebtPassReason() == null
        || accountConfig.getThreeMonthDebtPassReason().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_36),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getThreeMonthDebtPassReason();
  }

  public List<DebtRecoveryConfigLine> getDebtRecoveryConfigLineList(AccountConfig accountConfig)
      throws AxelorException {

    if (accountConfig.getDebtRecoveryConfigLineList() == null
        || accountConfig.getDebtRecoveryConfigLineList().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_37),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getDebtRecoveryConfigLineList();
  }

  /** ****************************** Sequence ******************************************* */
  public Sequence getCustInvSequence(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getCustInvSequence() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_SEQUENCE_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getCustInvSequence();
  }

  public Sequence getCustRefSequence(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getCustRefSequence() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_SEQUENCE_2),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getCustRefSequence();
  }

  public Sequence getSuppInvSequence(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getSuppInvSequence() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_SEQUENCE_3),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getSuppInvSequence();
  }

  public Sequence getSuppRefSequence(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getSuppRefSequence() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_SEQUENCE_4),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getSuppRefSequence();
  }

  public boolean getInvoiceInAti(AccountConfig accountConfig) throws AxelorException {

    int atiChoice = accountConfig.getInvoiceInAtiSelect();

    if (atiChoice == AccountConfigRepository.INVOICE_ATI_DEFAULT
        || atiChoice == AccountConfigRepository.INVOICE_ATI_ALWAYS) {
      return true;
    }
    return false;
  }

  /** ****************************** FEC ******************************************** */
  public String getExportFileName(AccountConfig accountConfig) throws AxelorException {
    if (accountConfig.getExportFileName() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_39),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }
    return accountConfig.getExportFileName();
  }

  public Account getFactorCreditAccount(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getFactorCreditAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_41),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }

    return accountConfig.getFactorCreditAccount();
  }

  public Account getFactorDebitAccount(AccountConfig accountConfig) throws AxelorException {

    if (accountConfig.getFactorDebitAccount() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ACCOUNT_CONFIG_42),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountConfig.getCompany().getName());
    }

    return accountConfig.getFactorDebitAccount();
  }
}
