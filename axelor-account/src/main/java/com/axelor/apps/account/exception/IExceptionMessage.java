/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.account.exception;

/**
 * Interface of Exceptions. Enum all exception of axelor-account.
 *
 * @author dubaux
 */
public interface IExceptionMessage {

  static final String INVOICE_LINE_TAX_LINE = /*$$(*/ "A tax line is missing" /*)*/;
  /** Bank statement service */
  static final String BANK_STATEMENT_1 = /*$$(*/
      "%s : Computed balance and Ending Balance must be equal" /*)*/;

  static final String BANK_STATEMENT_2 = /*$$(*/
      "%s : MoveLine amount is not equals with bank statement line %s" /*)*/;
  static final String BANK_STATEMENT_3 = /*$$(*/
      "%s : Bank statement line %s amount can't be null" /*)*/;

  /** Move service */
  static final String NO_MOVES_SELECTED = /*$$(*/
      "Please select 'Draft' or 'Simulated' moves" /*)*/;

  static final String MOVE_VALIDATION_NOT_OK = /*$$(*/
      "Error in move validation, please check the log" /*)*/;
  static final String MOVE_VALIDATION_OK = /*$$(*/ "Moves validated successfully" /*)*/;
  static final String MOVE_ARCHIVE_NOT_OK = /*$$(*/ "You can't archive this move %s" /*)*/;
  static final String MOVE_REMOVE_NOT_OK = /*$$(*/ "You can't remove this move %s" /*)*/;
  static final String MOVE_REMOVED_OK = /*$$(*/ "Move(s) has been removed successfully" /*)*/;
  static final String MOVE_ARCHIVE_OK = /*$$(*/ "Move(s) has been archived successfully" /*)*/;
  static final String NO_MOVE_TO_REMOVE_OR_ARCHIVE = /*$$(*/
      "Please select 'Draft' or 'Daybook' or 'Canceled' moves" /*)*/;
  static final String MOVE_ARCHIVE_OR_REMOVE_OK = /*$$(*/
      "Move(s) has been removed or archived successfully" /*)*/;
  static final String MOVE_ARCHIVE_OR_REMOVE_NOT_OK = /*$$(*/
      "Error in move deleting or archiving, please check the log" /*)*/;
  static final String MOVE_ARCHIVE_NOT_OK_BECAUSE_OF_LINK_WITH = /*$$(*/
      "This move %s can not be archived because it is linked to another piece named %s." /*)*/;
  static final String MOVE_LINE_ARCHIVE_NOT_OK_BECAUSE_OF_LINK_WITH = /*$$(*/
      "This move line %s can not be archived because it is linked to another piece named %s." /*)*/;
  static final String MOVE_LINE_RECONCILE_LINE_CANNOT_BE_REMOVED = /*$$(*/
      "The move lines %s are reconciled and should not have been removed." /*)*/;;
  static final String MOVE_ARCHIVE_OR_REMOVE_NOT_OK_NB = /*$$(*/
      "%d moves couldn't be deleted or archived, please check the logs." /*)*/;

  /** Account management service */
  static final String ACCOUNT_MANAGEMENT_1_ACCOUNT = /*$$(*/
      "Accounting configuration is missing for Product: %s (company: %s)" /*)*/;

  /** AccountingSituationService * */
  static final String ACCOUNTING_SITUATION_1 = /*$$(*/
      "You have to enter a prefix for automatic customer account creation in accounting application parameters of company %s" /*)*/;

  static final String ACCOUNTING_SITUATION_2 = /*$$(*/
      "You have to select a sequence for automatic customer account creation in accounting application parameters of company %s" /*)*/;
  static final String ACCOUNTING_SITUATION_3 = /*$$(*/
      "Invalid automatic partner account creation mode, this is a bug that should be reported." /*)*/;
  static final String ACCOUNTING_SITUATION_4 = /*$$(*/
      "You have to enter a prefix for automatic supplier account creation in accounting application parameters of company %s" /*)*/;
  static final String ACCOUNTING_SITUATION_5 = /*$$(*/
      "You have to select a sequence for automatic supplier account creation in accounting application parameters of company %s" /*)*/;
  static final String ACCOUNTING_SITUATION_6 = /*$$(*/
      "You have to enter a prefix for automatic employee account creation in accounting application parameters of company %s" /*)*/;
  static final String ACCOUNTING_SITUATION_7 = /*$$(*/
      "You have to select a sequence for automatic employee account creation in accounting application parameters of company %s" /*)*/;

  /** Mail service */
  static final String MAIL_1 = /*$$(*/
      "%s : Please define an email template for cash register (company: %s)" /*)*/;

  /** Account clearance service and controller */
  static final String ACCOUNT_CLEARANCE_1 = /*$$(*/
      "%s : You must configure account information for the company %s" /*)*/;

  static final String ACCOUNT_CLEARANCE_2 = /*$$(*/
      "%s : You must configure a financial account for then company %s" /*)*/;
  static final String ACCOUNT_CLEARANCE_3 = /*$$(*/
      "%s : You must configure a tax standard rate for the company %s" /*)*/;
  static final String ACCOUNT_CLEARANCE_4 = /*$$(*/
      "%s : You must configure account clearance for the company %s" /*)*/;
  static final String ACCOUNT_CLEARANCE_5 = /*$$(*/
      "%s : You must configure a clearance sequence of the overpayments for the company %s" /*)*/;
  static final String ACCOUNT_CLEARANCE_6 = /*$$(*/
      "%s : You must configure an overpayment account clearance journal for the company %s" /*)*/;
  static final String ACCOUNT_CLEARANCE_7 = /*$$(*/ "Move lines generated" /*)*/;

  /** Account customer service */
  static final String ACCOUNT_CUSTOMER_1 = /*$$(*/
      "%s : A customer account is missing for the company %s" /*)*/;

  static final String ACCOUNT_CUSTOMER_2 = /*$$(*/
      "%s : A supplier account is missing for the company %s" /*)*/;

  /** Check rejection service */
  static final String CHECK_REJECTION_1 = /*$$(*/
      "%s : You must configure a cheque rejection sequence for the company %s" /*)*/;

  /** Irrecoverable service and controller */
  static final String IRRECOVERABLE_1 = /*$$(*/ "Timetable line %s" /*)*/;

  static final String IRRECOVERABLE_2 = /*$$(*/
      "%s : Error generated on invoice creation shift to irrecoverable %s" /*)*/;
  static final String IRRECOVERABLE_3 = /*$$(*/
      "%s : The invoice %s has no accounting document whose the remainder to be paid is positive" /*)*/;
  static final String IRRECOVERABLE_4 = /*$$(*/
      "%s : You must configure shit to irrecoverable sequence for the company %s" /*)*/;
  static final String IRRECOVERABLE_5 = /*$$(*/ "Treatment finished" /*)*/;
  static final String IRRECOVERABLE_6 = /*$$(*/ "Anomalies generated" /*)*/;
  static final String IRRECOVERABLE_7 = /*$$(*/ "You must select a printing type" /*)*/;

  /** Journal service */
  static final String JOURNAL_1 = /*$$(*/ "Invoice type missing on invoice %s" /*)*/;

  /** Move line export service */
  static final String MOVE_LINE_EXPORT_1 = /*$$(*/
      "%s : Error : You must configure a sale interface sequence for the company %s" /*)*/;

  static final String MOVE_LINE_EXPORT_2 = /*$$(*/
      "%s : Error : You must configure a credit note interface sequence for the company %s" /*)*/;
  static final String MOVE_LINE_EXPORT_3 = /*$$(*/
      "%s : Error : You must configure a treasury interface sequence for the company %s" /*)*/;
  static final String MOVE_LINE_EXPORT_4 = /*$$(*/
      "%s : Error : You must configure a purchase interface sequence for the company %s" /*)*/;
  static final String MOVE_LINE_EXPORT_YEAR_OR_PERIOD_OR_DATE_IS_NULL = /*$$(*/
      "Year or period or date is null, you must set a period." /*)*/;

  /** Accounting report service and controller */
  static final String ACCOUNTING_REPORT_1 = /*$$(*/
      "%s : Error : You must configure an account reporting sequence for the company %s" /*)*/;

  static final String ACCOUNTING_REPORT_2 = /*$$(*/
      "%s : Error : You must configure an account export sequence for the company %s" /*)*/;
  static final String ACCOUNTING_REPORT_3 = /*$$(*/ "Move lines recovered" /*)*/;
  static final String ACCOUNTING_REPORT_4 = /*$$(*/ "You must select an export type" /*)*/;
  static final String ACCOUNTING_REPORT_6 = /*$$(*/ "Moves exported" /*)*/;
  static final String ACCOUNTING_REPORT_UNKNOWN_ACCOUNTING_REPORT_TYPE = /*$$(*/
      "Unknown accounting report type: %d" /*)*/;
  static final String ACCOUNTING_REPORT_ANALYTIC_REPORT = /*$$(*/
      "%s : Error : You must configure an analytic report sequence for the company %s" /*)*/;

  /** Move line service */
  static final String MOVE_LINE_1 = /*$$(*/ "Partner is missing on the invoice %s" /*)*/;

  static final String MOVE_LINE_2 = /*$$(*/ "Partner account missing on the invoice %s" /*)*/;
  static final String MOVE_LINE_4 = /*$$(*/
      "Account missing on configuration for line : %s (company : %s)" /*)*/;
  static final String MOVE_LINE_5 = /*$$(*/
      "Analytic account %s associated to sales account for the product %s is not configured : (company : %s)" /*)*/;
  static final String MOVE_LINE_6 = /*$$(*/
      "Account missing on the tax line : %s (company : %s)" /*)*/;
  static final String ANALYTIC_DISTRIBUTION_MISSING = /*$$(*/
      "Analytic distribution is missing on configuration for line : %s (company : %s)" /*)*/;
  static final String MOVE_LINE_7 = /*$$(*/
      "The accounting move line on the account %s can't have an amount equals to zero" /*)*/;

  /** Move service */
  static final String MOVE_1 = /*$$(*/ "Invoice type missing on invoice %s" /*)*/;

  static final String MOVE_2 = /*$$(*/ "You must select a journal for the move %s" /*)*/;
  static final String MOVE_3 = /*$$(*/ "You must select a company for the move %s" /*)*/;
  static final String MOVE_4 = /*$$(*/ "You must select a period for the move %s" /*)*/;
  static final String MOVE_5 = /*$$(*/
      "Journal %s does not have any account move sequence configured" /*)*/;
  static final String MOVE_6 = /*$$(*/ "Move account sens %s can't be determined" /*)*/;
  static final String MOVE_7 = /*$$(*/
      "Account move %s has a total debit different than total credit : %s <> %s" /*)*/;
  static final String MOVE_8 = /*$$(*/ "The move %s cannot be empty" /*)*/;
  static final String MOVE_9 = /*$$(*/
      "Tax is mandatory for the account %s on the move line %s" /*)*/;
  static final String MOVE_10 = /*$$(*/
      "Analytic distribution template is mandatory for the account %s on the move line %s." /*)*/;
  static final String MOVE_11 = /*$$(*/
      "An analytic distribution is set in move line %s but the account used do not allow analytic distribution" /*)*/;
  static final String MOVE_VALIDATION_FISCAL_PERIOD_CLOSED = /*$$(*/
      "Accounting move can not be validated because its fiscal period is closed." /*)*/;

  /** Payment schedule export service */
  static final String PAYMENT_SCHEDULE_1 = /*$$(*/
      "%s : You must configure a RIB for payment timetable %s" /*)*/;

  static final String PAYMENT_SCHEDULE_2 = /*$$(*/
      "%s : You must configure a RIB for the partner %s" /*)*/;
  static final String PAYMENT_SCHEDULE_3 = /*$$(*/
      "%s : Error : You must configure a direct debit date for the %s batch configuration" /*)*/;
  static final String PAYMENT_SCHEDULE_4 = /*$$(*/
      "%s : You must configure a direct debit reject sequence\n for the company %s for the journal %s" /*)*/;
  static final String PAYMENT_SCHEDULE_5 = /*$$(*/
      "You must configure a timetable sequence for the company %s" /*)*/;
  static final String PAYMENT_SCHEDULE_6 = /*$$(*/
      "%s : Error : You must, at first, create timetable lines for the timetable %s" /*)*/;
  static final String PAYMENT_SCHEDULE_LINE_AMOUNT_MISMATCH = /*$$(*/
      "The sum of line amounts (%s) must match the amount of the payment schedule (%s)." /*)*/;

  /** Reconcile service */
  static final String RECONCILE_1 = /*$$(*/
      "%s : Reconciliation : You must fill concerned moves lines." /*)*/;

  static final String RECONCILE_2 = /*$$(*/
      "%s : Reconciliation : Move line accounts are not compatible." /*)*/;
  static final String RECONCILE_3 = /*$$(*/
      "(Debit %s account %s amount %s - Credit %s account %s amount %s)" /*)*/;
  static final String RECONCILE_4 = /*$$(*/
      "%s : Reconciliation %s: Reconciliated amount must be different than zero. (Debit %s account %s - Credit %s account %s)" /*)*/;
  static final String RECONCILE_5 = /*$$(*/
      "%s : Reconciliation %s: Reconciliated amount (%s) must be lower or equal to remaining amount to reconciliate from moves lines." /*)*/;
  static final String RECONCILE_6 = /*$$(*/
      "%s : Error : You must configure a reconciliation sequence for the company %s" /*)*/;
  static final String RECONCILE_7 = /*$$(*/
      "Reconciliation : Selected moves lines must concern the same company. Reconcile : %s company \n Debit move line : %s company \n Credit move line : %s company" /*)*/;
  static final String RECONCILE_CAN_NOT_BE_REMOVE = /*$$(*/
      "The reconcile %s cannot be removed, please select draft reconcile(s)" /*)*/;

  /** Reimbursement service and controller */
  static final String REIMBURSEMENT_1 = /*$$(*/
      "%s : You must configure a reimbursement sequence for the company %s" /*)*/;

  static final String REIMBURSEMENT_2 = /*$$(*/
      "Export reimbursement folder (SEPA format) has not been configured for the company %s." /*)*/;
  static final String REIMBURSEMENT_3 = /*$$(*/
      "No reimbursement found for the ref %s and the company %s." /*)*/;
  static final String REIMBURSEMENT_4 = /*$$(*/ "You must configure a RIB." /*)*/;

  /** Year service */
  static final String YEAR_1 = /*$$(*/
      "%s : You must configure a company for the fiscal year %s" /*)*/;

  /** Batch Account customer */
  static final String BATCH_ACCOUNT_1 = /*$$(*/ "Accounting situation %s" /*)*/;

  static final String BATCH_ACCOUNT_2 = /*$$(*/
      "Contact's account balances determination's reporting :" /*)*/;
  static final String BATCH_ACCOUNT_3 = /*$$(*/ "* %s Account(s) situation(s) treated" /*)*/;
  static final String BATCH_ACCOUNT_4 = /*$$(*/
      "Account balances of %s accounting situation has not been updated, you must run the contact account batch update." /*)*/;
  static final String BATCH_ACCOUNT_5 = /*$$(*/
      "Account balances from all accounts situations (%s) has been updated." /*)*/;

  /** Batch doubtful customer */
  static final String BATCH_DOUBTFUL_1 = /*$$(*/
      "Doubtful account's determination's reporting" /*)*/;

  static final String BATCH_DOUBTFUL_2 = /*$$(*/ "* %s Invoice(s) treated" /*)*/;

  /** Batch move line export */
  static final String BATCH_MOVELINE_EXPORT_1 = /*$$(*/
      "%s : Error : You must configure a company for the batch configurator %s" /*)*/;

  static final String BATCH_MOVELINE_EXPORT_2 = /*$$(*/
      "%s : Error : You must configure a due date for the batch configurator %s" /*)*/;
  static final String BATCH_MOVELINE_EXPORT_3 = /*$$(*/
      "%s : Error : You must configure an export type for the batch configurator %s" /*)*/;
  static final String BATCH_MOVELINE_EXPORT_4 = /*$$(*/ "Moves export batch's reporting :" /*)*/;
  static final String BATCH_MOVELINE_EXPORT_5 = /*$$(*/ "Moves Lines (Moves) exported" /*)*/;

  /** Batch payment schedule import/export */
  static final String BATCH_PAYMENT_SCHEDULE_1 = /*$$(*/
      "Unknowned data type for the treatment %s" /*)*/;

  static final String BATCH_PAYMENT_SCHEDULE_2 = /*$$(*/ "Direct debit's export batch %s" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_3 = /*$$(*/ "Due date's direct debit %s" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_4 = /*$$(*/
      "Export reporting to invoices direct debits :" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_5 = /*$$(*/ "Invoice(s) direct debit(s) treated" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_6 = /*$$(*/
      "Export reporting to monthly direct debits :" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_7 = /*$$(*/ "Monthly direct debit(s) treated" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_8 = /*$$(*/
      "%s : No timetable nor invoice found for the direct debit number : %s" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_9 = /*$$(*/ "Reject %s" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_10 = /*$$(*/
      "Timetable's reject move's creation %s" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_11 = /*$$(*/
      "Invoice's reject move's creation %s" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_12 = /*$$(*/
      "Reporting to direct debit reject's import :" /*)*/;
  static final String BATCH_PAYMENT_SCHEDULE_13 = /*$$(*/ "Direct debit(s) rejected" /*)*/;

  /** Batch reimbursement export/import */
  static final String BATCH_REIMBURSEMENT_1 = /*$$(*/
      "Bug(Anomaly) generated during SEPA export - Batch %s" /*)*/;

  static final String BATCH_REIMBURSEMENT_2 = /*$$(*/ "Reporting to reimbursement creation :" /*)*/;
  static final String BATCH_REIMBURSEMENT_3 = /*$$(*/ "Reimbursement(s) created" /*)*/;
  static final String BATCH_REIMBURSEMENT_4 = /*$$(*/ "Reporting to reimbursement's export :" /*)*/;
  static final String BATCH_REIMBURSEMENT_5 = /*$$(*/ "Reimbursement(s) treated" /*)*/;
  static final String BATCH_REIMBURSEMENT_6 = /*$$(*/ "Reimbursement's import's batch %s" /*)*/;
  static final String BATCH_REIMBURSEMENT_7 = /*$$(*/ "Reimbursement reject %s" /*)*/;
  static final String BATCH_REIMBURSEMENT_8 = /*$$(*/
      "Reporting to reimbursement reject's import :" /*)*/;
  static final String BATCH_REIMBURSEMENT_9 = /*$$(*/ "Reimbursement(s) rejected" /*)*/;
  static final String BATCH_REIMBURSEMENT_10 = /*$$(*/ "Total Amount" /*)*/;

  /** Batch debt recovery */
  static final String BATCH_DEBT_RECOVERY_1 = /*$$(*/ "Debt recovery's reporting :" /*)*/;

  static final String BATCH_DEBT_RECOVERY_2 = /*$$(*/ "Partner(s) treated" /*)*/;

  /** Batch credit transfer */
  static final String BATCH_CREDIT_TRANSFER_REPORT_TITLE = /*$$(*/
      "Report for credit transfer batch:" /*)*/;

  static final String BATCH_CREDIT_TRANSFER_INVOICE_DONE_SINGULAR = /*$$(*/
      "%d invoice treated successfully," /*)*/;
  static final String BATCH_CREDIT_TRANSFER_INVOICE_DONE_PLURAL = /*$$(*/
      "%d invoices treated successfully," /*)*/;
  static final String BATCH_CREDIT_TRANSFER_REIMBURSEMENT_DONE_SINGULAR = /*$$(*/
      "%d reimbursement created successfully," /*)*/;
  static final String BATCH_CREDIT_TRANSFER_REIMBURSEMENT_DONE_PLURAL = /*$$(*/
      "%d reimbursements created successfully," /*)*/;
  static final String BATCH_CREDIT_TRANSFER_ANOMALY_SINGULAR = /*$$(*/ "%d anomaly." /*)*/;
  static final String BATCH_CREDIT_TRANSFER_ANOMALY_PLURAL = /*$$(*/ "%d anomalies." /*)*/;

  /** Batch strategy */
  static final String BATCH_STRATEGY_1 = /*$$(*/
      "%s : You must configure a RIB for batch's configurator %s" /*)*/;

  /** Batch realize fixed asset lines */
  static final String BATCH_REALIZED_FIXED_ASSET_LINE = /*$$(*/ "Realized fixed asset lines" /*)*/;

  static final String BATCH_FIXED_ASSET_LINE_DATE_ERROR = /*$$(*/
      "Please enter valid start date and end date" /*)*/;

  /** Batch close / open the year account */
  static final String BATCH_CLOSE_OPEN_ANNUAL_ACCOUNT_REPORT_TITLE = /*$$(*/
      "Report for close/open annual accounts batch:" /*)*/;

  static final String BATCH_CLOSE_OPEN_ANNUAL_ACCOUNT_DONE_SINGULAR = /*$$(*/
      "%d account treated successfully," /*)*/;
  static final String BATCH_CLOSE_OPEN_ANNUAL_ACCOUNT_DONE_PLURAL = /*$$(*/
      "%d accounts treated successfully," /*)*/;

  /** Cfonb export service */
  static final String CFONB_EXPORT_1 = /*$$(*/
      "You must configure a RIB for the reimbursement" /*)*/;

  static final String CFONB_EXPORT_2 = /*$$(*/
      "%s : Error detected during CFONB file's writing : %s" /*)*/;
  static final String CFONB_EXPORT_3 = /*$$(*/
      "%s : You must configure a Sort Code for the RIB %s of third-payer %s" /*)*/;
  static final String CFONB_EXPORT_4 = /*$$(*/
      "%s : You must configure a number's account for the RIB %s of third-payer %s" /*)*/;
  static final String CFONB_EXPORT_5 = /*$$(*/
      "%s : You must configure a Bank Code for the RIB %s of third-payer %s" /*)*/;
  static final String CFONB_EXPORT_6 = /*$$(*/
      "%s : You must configure a Bank Address for the RIB %s of third-payer %s" /*)*/;

  /** Cfonb import service */
  static final String CFONB_IMPORT_1 = /*$$(*/
      "%s : You must configure a reject/return reason's code's list relating to Card cashing, Direct debit and TIP in general configuration" /*)*/;

  static final String CFONB_IMPORT_2 = /*$$(*/
      "%s : A header record is missing in the file %s" /*)*/;
  static final String CFONB_IMPORT_3 = /*$$(*/
      "%s : One or several detail records are missing in the file %s" /*)*/;
  static final String CFONB_IMPORT_4 = /*$$(*/ "%s : A record is missing in the file %s" /*)*/;
  static final String CFONB_IMPORT_5 = /*$$(*/
      "%s : The total amount for the following record isn't correct (file %s) :\n %s" /*)*/;
  static final String CFONB_IMPORT_6 = /*$$(*/
      "%s : No payment mode found for the code %s and the company %s" /*)*/;

  /** Cfonb tool service */
  static final String CFONB_TOOL_NB_OF_CHAR_PER_LINE = /*$$(*/
      "The record is not %s characters" /*)*/;

  static final String CFONB_TOOL_EMPTY_ZONE = /*$$(*/ "Zone %s is empty" /*)*/;
  static final String CFONB_TOOL_DIGITAL_ZONE_NOT_CORRECT = /*$$(*/
      "Zone %s (%s) must be of the numeric type" /*)*/;
  static final String CFONB_TOOL_1 = /*$$(*/
      "%s : Anomaly detected (value isn't numeric : %s) for sender" /*)*/;
  static final String CFONB_TOOL_2 = /*$$(*/
      "%s : Anomaly detected (value isn't numeric : %s) for the receiver" /*)*/;
  static final String CFONB_TOOL_3 = /*$$(*/
      "%s : Anomaly detected (value isn't numeric : %s) for the total" /*)*/;
  static final String CFONB_TOOL_4 = /*$$(*/
      "%s : Anomaly detected (the record doesn't have %s characters : %s) for the record %s, company %s" /*)*/;

  static final String COMPANY_CURRENCY = /*$$(*/
      "%s : Please, configure a currency for the company %s" /*)*/;

  static final String ACCOUNT_CONFIG_1 = /*$$(*/
      "%s : You must configure account's information for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_2 = /*$$(*/
      "%s : You must configure a CFONB format reimbursement's export's folder for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_3 = /*$$(*/
      "%s : You must configure a CFONB format direct debit's export's folder for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_4 = /*$$(*/
      "%s : You must configure a TIP and cheque TIP payment's import path for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_5 = /*$$(*/
      "%s : You must configure a TIP and cheque TIP temporary import path for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_6 = /*$$(*/
      "%s : You must configure a TIP and cheque TIP payment rejects path for the import file for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_7 = /*$$(*/
      "%s : You must configure a TIP and cheque TIP temporary path for the payment reject's file for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_8 = /*$$(*/
      "%s : You must configure a path for the reject's file for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_9 = /*$$(*/
      "%s : You must configure a path for the temporary reject's file for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_10 = /*$$(*/
      "%s : You must configure a path for the reimbursements rejects import's file for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_11 = /*$$(*/
      "%s : You must configure a path for the reimbursement rejects import's temporary file for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_12 = /*$$(*/
      "%s : You must configure a rejects journal for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_13 = /*$$(*/
      "%s : You must configure an irrevocable journal for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_14 = /*$$(*/
      "%s : You must configure a Supplier purchase journal for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_15 = /*$$(*/
      "%s : You must configure a Supplier credit note journal for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_16 = /*$$(*/
      "%s : You must configure a Sales journal for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_17 = /*$$(*/
      "%s : You must configure a Customer credit note journal for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_18 = /*$$(*/
      "%s : You must configure a Misc. Operation journal for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_19 = /*$$(*/
      "%s : You must configure a Reimbursement journal for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_20 = /*$$(*/
      "%s : You must configure a Sales journal type for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_21 = /*$$(*/
      "%s : You must configure a Credit note journal type for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_22 = /*$$(*/
      "%s : You must configure a Cash journal type for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_23 = /*$$(*/
      "%s : You must configure a Purchase journal type for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_24 = /*$$(*/
      "%s : You must configure an irrevocable doubtful account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_25 = /*$$(*/
      "%s : You must configure a customer account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_26 = /*$$(*/
      "%s : You must configure a supplier account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_27 = /*$$(*/
      "%s : You must configure a cash difference account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_28 = /*$$(*/
      "%s : You must configure a reimbursement account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_29 = /*$$(*/
      "%s : You must configure a doubtful customer account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_30 = /*$$(*/
      "%s : You must configure a direct debit payment mode for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_31 = /*$$(*/
      "%s : You must configure a payment mode after reject for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_32 = /*$$(*/
      "%s : You must configure a shift to irrecoverable's reason for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_34 = /*$$(*/
      "%s : You must configure a reject import letter template for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_35 = /*$$(*/
      "%s : You must configure a shifting reason (debt more than six months) for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_36 = /*$$(*/
      "%s : You must configure a shifting reason (debt more than three months) for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_37 = /*$$(*/
      "%s : You must configure a debt recovery tab for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_38 = /*$$(*/
      "%s : You must configure an advance payment account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_39 = /*$$(*/
      "%s : You must configure a file name for the export of move file for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_40 = /*$$(*/
      "%s : You must configure an employee account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_41 = /*$$(*/
      "%s : You must configure a factor credit account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_42 = /*$$(*/
      "%s : You must configure a factor debit account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_43 = /*$$(*/
      "%s : You must configure a year opening account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_44 = /*$$(*/
      "%s : You must configure a year closure account for the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_45 = /*$$(*/
      "%s : You must configure a reported balance journal for the company %s" /*)*/;

  static final String ACCOUNT_CONFIG_SEQUENCE_1 = /*$$(*/
      "%s : Please, configure a sequence for the customer invoices and the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_SEQUENCE_2 = /*$$(*/
      "%s : Please, configure a sequence for the customer refunds and the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_SEQUENCE_3 = /*$$(*/
      "%s : Please, configure a sequence for the supplier invoices and the company %s" /*)*/;
  static final String ACCOUNT_CONFIG_SEQUENCE_4 = /*$$(*/
      "%s : Please, configure a sequence for the supplier refunds and the company %s" /*)*/;

  /** Cfonb config service */
  static final String CFONB_CONFIG_1 = /*$$(*/
      "%s : You must configure CFONB for the company %s" /*)*/;

  static final String CFONB_CONFIG_2 = /*$$(*/
      "%s : You must configure a CFONB sender code register for the company %s" /*)*/;
  static final String CFONB_CONFIG_3 = /*$$(*/
      "%s : You must configure a CFONB sender's number for the company %s" /*)*/;
  static final String CFONB_CONFIG_4 = /*$$(*/
      "%s : You must configure a CFONB sender's name/corporate name for the company %s" /*)*/;
  static final String CFONB_CONFIG_5 = /*$$(*/
      "%s : You must configure a CFONB receiver code register for the company %s" /*)*/;
  static final String CFONB_CONFIG_6 = /*$$(*/
      "%s : You must configure a CFONB total code register for the company %s" /*)*/;
  static final String CFONB_CONFIG_7 = /*$$(*/
      "%s : You must configure a CFONB internet payment code for the company %s" /*)*/;
  static final String CFONB_CONFIG_8 = /*$$(*/
      "%s : You must configure a CFONB direct debit code for the company %s" /*)*/;
  static final String CFONB_CONFIG_9 = /*$$(*/
      "%s : You must configure a CFONB header code register for the company %s" /*)*/;
  static final String CFONB_CONFIG_10 = /*$$(*/
      "%s : You must configure a CFONB detail code register for the company %s" /*)*/;
  static final String CFONB_CONFIG_11 = /*$$(*/
      "%s : You must configure a CFONB code register end for the company %s" /*)*/;
  static final String CFONB_CONFIG_12 = /*$$(*/
      "%s : You must configure a CFONB rejected direct debit code for the company %s" /*)*/;
  static final String CFONB_CONFIG_13 = /*$$(*/
      "%s : You must configure a CFONB unpaid direct debit code fir the company %s" /*)*/;
  static final String CFONB_CONFIG_14 = /*$$(*/
      "%s : You must configure a CFONB unpaid TIP code for the company %s" /*)*/;
  static final String CFONB_CONFIG_15 = /*$$(*/
      "%s : You must configure a CFONB TIP and cheque TIP code for the company %s" /*)*/;
  static final String CFONB_CONFIG_16 = /*$$(*/
      "%s : You must configure a CFONB TIP code for the company %s" /*)*/;

  /** Payer quality service */
  static final String PAYER_QUALITY_1 = /*$$(*/
      "%s : Error : You must configure a weight table in general configuration" /*)*/;

  /** Debt recovery action service */
  static final String DEBT_RECOVERY_ACTION_1 = /*$$(*/ "Debt recovery method missing." /*)*/;

  static final String DEBT_RECOVERY_ACTION_2 = /*$$(*/ "Debt recovery line missing." /*)*/;
  static final String DEBT_RECOVERY_ACTION_3 = /*$$(*/
      "%s : Letter template missing for debt recovery matrix %s (Partner %s, Level %s)." /*)*/;
  static final String DEBT_RECOVERY_ACTION_4 = /*$$(*/
      "Email is not sent. Please check email account configuration." /*)*/;
  static final String DEBT_RECOVERY_ACTION_5 = /*$$(*/
      "No email address in debt recovery %s. Please set one." /*)*/;

  /** Debt recovery service */
  static final String DEBT_RECOVERY_1 = /*$$(*/ "There's no accounting situation." /*)*/;

  static final String DEBT_RECOVERY_2 = /*$$(*/ "Reference date undefined." /*)*/;
  static final String DEBT_RECOVERY_3 = /*$$(*/
      "Debt recovery method missing for the configuration." /*)*/;
  static final String DEBT_RECOVERY_4 = /*$$(*/ "Debt recovery level waiting for approval." /*)*/;
  static final String DEBT_RECOVERY_DEBT_RECOVERY_LEVEL_NOT_FOUND = /*$$(*/
      "Debt recovery method line not found" /*)*/;

  /** Debt recovery session service */
  static final String DEBT_RECOVERY_SESSION_1 = /*$$(*/ "Debt recovery method line missing." /*)*/;

  /** Invoice batch service */
  static final String INVOICE_BATCH_1 = /*$$(*/ "State %s unknown for treatment %s" /*)*/;

  /** Invoice generator */
  static final String INVOICE_GENERATOR_1 = /*$$(*/ "%s : Invoice's type is not filled %s" /*)*/;

  static final String INVOICE_GENERATOR_2 = /*$$(*/ "%s : There's no partner selected" /*)*/;
  static final String INVOICE_GENERATOR_3 = /*$$(*/ "%s : Payment condition missing" /*)*/;
  static final String INVOICE_GENERATOR_4 = /*$$(*/ "%s : Payment mode missing" /*)*/;
  static final String INVOICE_GENERATOR_5 = /*$$(*/ "%s : Invoicing address missing" /*)*/;
  static final String INVOICE_GENERATOR_6 = /*$$(*/ "%s : Currency missing" /*)*/;

  /** Merge Invoice */
  public static final String INVOICE_MERGE_ERROR_CURRENCY = /*$$(*/
      "The currency is required and must be the same for all invoices" /*)*/;

  public static final String INVOICE_MERGE_ERROR_PARTNER = /*$$(*/
      "The partner is required and must be the same for all invoices" /*)*/;
  public static final String INVOICE_MERGE_ERROR_COMPANY = /*$$(*/
      "The company is required and must be the same for all invoices" /*)*/;
  public static final String INVOICE_MERGE_ERROR_SALEORDER = /*$$(*/
      "The sale order must be the same for all invoices" /*)*/;
  public static final String INVOICE_MERGE_ERROR_PROJECT = /*$$(*/
      "The project must be the same for all invoices" /*)*/;
  public static final String INVOICE_MASS_PAYMENT_ERROR_PFP_LITIGATION = /*$$(*/
      "Their is at least one invoice selected that it is not validated to pay" /*)*/;

  /** Invoice line generator */
  static final String INVOICE_LINE_GENERATOR_1 = /*$$(*/
      "You must select a currency for partner %s (%s)" /*)*/;

  static final String INVOICE_LINE_GENERATOR_2 = /*$$(*/
      "You must select a currency for company %s" /*)*/;

  public static final String INVOICE_LINE_ERROR_FIXED_ASSET_CATEGORY = /*$$(*/
      "Fixed asset category is missing on invoice line for product %s" /*)*/;

  /** Batch validation */
  static final String BATCH_VALIDATION_1 = /*$$(*/ "Invoice validation's reporting :" /*)*/;

  static final String BATCH_VALIDATION_2 = /*$$(*/ "Invoice(s) validated" /*)*/;

  /** Batch ventilation */
  static final String BATCH_VENTILATION_1 = /*$$(*/ "Invoice ventilation's reporting :" /*)*/;

  static final String BATCH_VENTILATION_2 = /*$$(*/ "Invoice(s) ventilated" /*)*/;

  /** Refund invoice */
  static final String REFUND_INVOICE_1 = /*$$(*/
      "%s : Payment mode must be filled either in the partner or in the company configuration." /*)*/;

  /** Validate state */
  static final String INVOICE_VALIDATE_1 = /*$$(*/
      "The payment mode is not in adequacy with the invoice type" /*)*/;

  static final String INVOICE_VALIDATE_BLOCKING = /*$$(*/
      "The partner is blocked for invoicing." /*)*/;

  /** Cancel state */
  static final String MOVE_CANCEL_1 = /*$$(*/
      "Move should be unreconcile before to cancel the invoice" /*)*/;

  static final String MOVE_CANCEL_2 = /*$$(*/
      "Move is ventilated on a closed period, and can't be canceled" /*)*/;
  static final String MOVE_CANCEL_3 = /*$$(*/
      "So many accounting operations are used on this move, so move can't be canceled" /*)*/;

  static final String INVOICE_CANCEL_1 = /*$$(*/
      "Invoice is passed in doubfult debit, and can't be canceled" /*)*/;

  static final String INVOICE_PAYMENT_CANCEL = /*$$(*/
      "The bank order linked to this invoice payment has already been carried out/rejected, and thus can't be canceled" /*)*/;

  public static final String INVOICE_PAYMENT_NO_AMOUNT_REMAINING = /*$$(*/
      "The payment cannot be done because the amount remaining on the invoice %s is inferior or equal to 0." /*)*/;

  /** Ventilate state */
  static final String VENTILATE_STATE_1 = /*$$(*/
      "Invoice's or credit note's date can't be previous last invoice ventilated's date" /*)*/;

  static final String VENTILATE_STATE_2 = /*$$(*/
      "Invoice's or credit note's date can't be previous last invoice ventilated on month's date" /*)*/;
  static final String VENTILATE_STATE_3 = /*$$(*/
      "Invoice's or credit note's date can't be previous last invoice ventilated on year's date" /*)*/;
  static final String VENTILATE_STATE_4 = /*$$(*/
      "Company %s does not have any invoice's nor credit note's sequence" /*)*/;
  static final String VENTILATE_STATE_5 = /*$$(*/
      "The partner account can not be determined. Please set up the partner account on the invoice or configure the partner's accounting situation." /*)*/;
  static final String VENTILATE_STATE_6 = /*$$(*/
      "The account of a product could not be determined or is not filled. Please fill the missing account on invoice line %s" /*)*/;
  static final String VENTILATE_STATE_7 = /*$$(*/
      "An analytic distribution is set in product but the account used do not allow analytic distribution" /*)*/;

  static final String VENTILATE_STATE_FUTURE_DATE = /*$$(*/
      "Invoice date can't be in the future." /*)*/;

  static final String VENTILATE_STATE_FUTURE_ORIGIN_DATE = /*$$(*/
      "Invoice date of origin can't be in the future." /*)*/;

  static final String VENTILATE_STATE_MISSING_ORIGIN_DATE = /*$$(*/
      "Origin date is missing on the invoice" /*)*/;

  /** Workflow ventilation */
  String AMOUNT_ADVANCE_PAYMENTS_TOO_HIGH = /*$$(*/
      "Sum of advance payments amounts is higher than the total of this invoice." /*)*/;

  static final String PAYMENT_AMOUNT_EXCEEDING = /*$$(*/
      "%s : Caution - You can't pay for an amount higher than selected invoices" /*)*/;

  /** Payment mode service */
  static final String PAYMENT_MODE_1 = /*$$(*/ "Associated account not configured" /*)*/;

  static final String PAYMENT_MODE_2 = /*$$(*/
      "%s : Error : You must configure a sequence for the company %s and a payment mode %s" /*)*/;
  static final String PAYMENT_MODE_3 = /*$$(*/
      "%s : Error : You must configure a journal for the company %s and a payment mode %s" /*)*/;

  /** Payment voucher control service */
  static final String PAYMENT_VOUCHER_CONTROL_PAID_AMOUNT = /*$$(*/
      "%s : Payment voucher n° %s, the paid amount should be positive" /*)*/;

  static final String PAYMENT_VOUCHER_CONTROL_1 = /*$$(*/
      "%s : Caution, payment entry nb %s, total line's amount imputed is higher than customer's amount paid." /*)*/;
  static final String PAYMENT_VOUCHER_CONTROL_2 = /*$$(*/ "%s : There's no line to pay." /*)*/;
  static final String PAYMENT_VOUCHER_CONTROL_3 = /*$$(*/
      "%s : You must add a journal and a treasury account into payment mode." /*)*/;

  /** Payment voucher load service */
  static final String PAYMENT_VOUCHER_LOAD_1 = /*$$(*/ "%s : You must add an amount paid." /*)*/;

  /** Payment voucher sequence service */
  static final String PAYMENT_VOUCHER_SEQUENCE_1 = /*$$(*/
      "%s : You must configure a receipt number (Payment entry) for the company %s" /*)*/;

  /** Payment voucher tool service */
  static final String PAYMENT_VOUCHER_TOOL_1 = /*$$(*/
      "Payment entry's type missing from payment entry %s" /*)*/;

  /** Payment schedule line service */
  String PAYMENT_SCHEDULE_LINE_NO_DIRECT_DEBIT_PAYMENT_MODE = /*$$(*/
      "Missing direct debit payment mode in the company's account configuration" /*)*/;

  /** Account chart controller */
  static final String ACCOUNT_CHART_1 = /*$$(*/
      "The chart of account has been loaded successfully" /*)*/;

  static final String ACCOUNT_CHART_2 = /*$$(*/
      "Error in account chart import please check the log" /*)*/;
  static final String ACCOUNT_CHART_3 = /*$$(*/
      "A chart or chart structure of accounts already exists, please delete the hierarchy between accounts in order to import a new chart." /*)*/;

  /** Address controller */
  static final String ADDRESS_1 = /*$$(*/ "Sales map" /*)*/;

  static final String ADDRESS_2 = /*$$(*/ "Not implemented for OSM" /*)*/;

  /** Invoice controller */
  static final String INVOICE_1 = /*$$(*/ "Invoice canceled" /*)*/;

  static final String INVOICE_2 = /*$$(*/ "Credit note created" /*)*/;
  static final String INVOICE_3 = /*$$(*/ "Please select the invoice(s) to print." /*)*/;
  static final String INVOICE_4 = /*$$(*/ "Refunds from invoice %s" /*)*/;

  static final String INVOICE_NO_INVOICE_TO_PAY = /*$$(*/ "No invoice to pay" /*)*/;

  /** Move template controller */
  static final String MOVE_TEMPLATE_1 = /*$$(*/ "Template move is not balanced" /*)*/;

  static final String MOVE_TEMPLATE_2 = /*$$(*/ "Error in move generation" /*)*/;
  static final String MOVE_TEMPLATE_3 = /*$$(*/ "Generated moves" /*)*/;
  static final String MOVE_TEMPLATE_4 = /*$$(*/ "Please fill input lines" /*)*/;

  /** Budget service */
  static final String BUDGET_1 = /*$$(*/ "Too much iterations." /*)*/;

  static final String USER_PARTNER = /*$$(*/ "You must create a contact for user %s" /*)*/;

  /*
   * Deposit slip
   */
  static final String DEPOSIT_SLIP_MISSING_SEQUENCE = /*$$(*/
      "Missing deposit slip sequence for company %s" /*)*/;
  static final String DEPOSIT_SLIP_CANNOT_DELETE = /*$$(*/
      "You cannot delete this deposit slip." /*)*/;
  static final String DEPOSIT_SLIP_ALREADY_PUBLISHED = /*$$(*/
      "The deposit slip has already been published." /*)*/;
  static final String DEPOSIT_SLIP_UNSUPPORTED_PAYMENT_MODE_TYPE = /*$$(*/
      "Unsupported payment mode type" /*)*/;

  /*
   * Partner
   */
  String PARTNER_BANK_DETAILS_MISSING = /*$$(*/ "Bank details are missing for partner %s." /*)*/;

  /*
   * Invoice printing
   */
  String INVOICE_MISSING_PRINTING_SETTINGS = /*$$(*/
      "Please fill printing settings on invoice %s." /*)*/;
  String INVOICES_MISSING_PRINTING_SETTINGS = /*$$(*/
      "Please fill printing settings on following invoices: %s" /*)*/;
  String INVOICE_PRINTING_IO_ERROR = /*$$(*/ "Error on uploading printed invoice:" /*)*/;

  /*
   * Reconcile Group
   */
  String RECONCILE_GROUP_VALIDATION_NO_LINES = /*$$(*/
      "The reconcile group cannot be validated since there is no lines." /*)*/;
  String RECONCILE_GROUP_NO_TEMP_SEQUENCE = /*$$(*/
      "There is no configured sequence for temporary reconcile group" /*)*/;
  String RECONCILE_GROUP_NO_FINAL_SEQUENCE = /*$$(*/
      "There is no configured sequence for final reconcile group" /*)*/;

  /*
   * Subrogation Release
   */
  static final String SUBROGATION_RELEASE_MISSING_SEQUENCE = /*$$(*/
      "Missing subrogation release sequence for company %s" /*)*/;
  static final String SUBROGATION_RELEASE_SUBROGATION_ALREADY_EXIST_FOR_INVOICES = /*$$(*/
      "A transmitted or a accounted subrogation release already exist for the invoices %s." /*)*/;

  /** MoveLine */
  static final String NO_MOVE_LINE_SELECTED = /*$$(*/ "No Lines selected" /*)*/;

  /** User */
  static final String USER_PFP_VALIDATOR_COMPANY_SET_NOT_EQUAL = /*$$(*/
      "%s has not exaclty the same internal companies as %s." /*)*/;

  static final String USER_PFP_VALIDATOR_UPDATED = /*$$(*/
      "Pfp validator changed successfully" /*)*/;
  static final String USER_PFP_VALIDATOR_NO_RELATED_ACCOUNTING_SITUATION = /*$$(*/
      "No Accounting Situation related to %s." /*)*/;

  /* Check refunds */
  String INVOICE_NOT_IMPUTED_CLIENT_REFUNDS = /*$$(*/
      "Note: there are existing not imputed client refunds."; /*)*/
  String INVOICE_NOT_IMPUTED_SUPPLIER_REFUNDS = /*$$(*/
      "Note: there are existing not imputed supplier refunds."; /*)*/

  public static final String FIXED_ASSET_DISPOSAL_DATE_ERROR_1 = /*$$(*/
      "Disposal date must be after the date of the last depreciation." /*)*/;
  public static final String FIXED_ASSET_DISPOSAL_DATE_ERROR_2 = /*$$(*/
      "Disposal date shouldn't be after the next planned depreciation date. Please realize all depreciations that happened before the disposal." /*)*/;

  /* MOVE REVERSE*/
  static final String REVERSE_DATE_SELECT_UNKNOW_TYPE = /*$$(*/
      "There is no reverse date select value of value %d" /*)*/;

  /*Check not lettered advance move lines*/
  public static final String INVOICE_NOT_LETTERED_SUPPLIER_ADVANCE_MOVE_LINES = /*$$(*/
      "There is at least one advance payment or payment that can be imputed to this invoice." /*)*/;

  static final String CLOSE_NO_REPORTED_BALANCE_DATE = /*$$(*/
      "Please set a reported balance date on fiscal year" /*)*/;

  static final String ACCOUNT_CODE_ALREADY_IN_USE_FOR_COMPANY = /*$$(*/
      "The account code %s is already used for the company %s, there cannot be two accounts with the same code for the same company." /*)*/;;

  static final String INVALID_ANALYTIC_MOVE_LINE = /*$$(*/
      "Invalid Analytic moveLines, some axes percentage values are higher than 100%." /*)*/;
}
