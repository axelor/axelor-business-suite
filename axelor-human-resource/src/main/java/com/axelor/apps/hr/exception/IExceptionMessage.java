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
package com.axelor.apps.hr.exception;

/** Interface of Exceptions. Enum all exception of axelor-human-resource. */
public interface IExceptionMessage {

  static final String HR_CONFIG = /*$$(*/
      "Please configure informations for human resources for the company %s" /*)*/;
  static final String HR_CONFIG_EXPENSE_TYPE = /*$$(*/
      "Please configure the expense type for kilometric allowance in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_SENT_EXPENSE_TEMPLATE = /*$$(*/
      "Please configure the sent expense template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_VALIDATED_EXPENSE_TEMPLATE = /*$$(*/
      "Please configure the validated expense template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_REFUSED_EXPENSE_TEMPLATE = /*$$(*/
      "Please configure the refused expense template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_CANCELED_EXPENSE_TEMPLATE = /*$$(*/
      "Please configure the canceled expense template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_SENT_TIMESHEET_TEMPLATE = /*$$(*/
      "Please configure the sent timehsheet template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_VALIDATED_TIMESHEET_TEMPLATE = /*$$(*/
      "Please configure the validated timehsheet template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_REFUSED_TIMESHEET_TEMPLATE = /*$$(*/
      "Please configure the refused timehsheet template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_CANCELED_TIMESHEET_TEMPLATE = /*$$(*/
      "Please configure the canceled timehsheet template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_SENT_LEAVE_TEMPLATE = /*$$(*/
      "Please configure the sent leave template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_VALIDATED_LEAVE_TEMPLATE = /*$$(*/
      "Please configure the validated leave template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_REFUSED_LEAVE_TEMPLATE = /*$$(*/
      "Please configure the refused leave template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_CANCELED_LEAVE_TEMPLATE = /*$$(*/
      "Please configure the canceled leave template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_SENT_EXTRA_HOURS_TEMPLATE = /*$$(*/
      "Please configure the sent extra hours template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_VALIDATED_EXTRA_HOURS_TEMPLATE = /*$$(*/
      "Please configure the validated extra hours template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_REFUSED_EXTRA_HOURS_TEMPLATE = /*$$(*/
      "Please configure the refused extra hours template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_CANCELED_EXTRA_HOURS_TEMPLATE = /*$$(*/
      "Please configure the canceled extra hours template in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_LEAVE_REASON = /*$$(*/
      "Please configure the unjustified absence reason in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_LUNCH_VOUCHER_EXPORT_PATH = /*$$(*/
      "Please configure the lunch voucher export path in HR config for the company %s" /*)*/;
  static final String HR_CONFIG_NO_EXPENSE_SEQUENCE = /*$$(*/
      "Company %s does not have any expense's sequence" /*)*/;
  static final String HR_CONFIG_FORMULA_VARIABLE_MISSING = /*$$(*/
      "Please configure Formula Variables for human resource for the company %s" /*)*/;

  static final String TIMESHEET_FROM_DATE = /*$$(*/ "Please add a start date for generation" /*)*/;
  static final String TIMESHEET_TO_DATE = /*$$(*/ "Please add an end date for generation" /*)*/;
  static final String TIMESHEET_PRODUCT = /*$$(*/ "Please add a product" /*)*/;
  static final String TIMESHEET_EMPLOYEE_DAY_PLANNING = /*$$(*/
      "Please add an employee's planning related to user %s" /*)*/;
  static final String TIMESHEET_EMPLOYEE_DAILY_WORK_HOURS = /*$$(*/
      "Please, enter the number of daily work hours per employee %s" /*)*/;
  static final String TIMESHEET_DAILY_WORK_HOURS = /*$$(*/
      "Please, configure the number of daily work hours." /*)*/;
  static final String TIMESHEET_DATE_CONFLICT = /*$$(*/
      "There is a conflict between the timesheet input dates and the dates of the following lines: %s" /*)*/;
  static final String TIMESHEET_NULL_FROM_DATE = /*$$(*/ "From date can't be empty" /*)*/;
  static final String TIMESHEET_NULL_TO_DATE = /*$$(*/ "To date can't be empty" /*)*/;
  static final String TIMESHEET_LINE_NULL_DATE = /*$$(*/
      "The date of timesheet line %d can't be empty" /*)*/;
  static final String GENERAL_EMPLOYEE_ACTIVITY = /*$$(*/
      "Please, enter an activity for the employee %s" /*)*/;
  static final String TIMESHEET_EMPLOYEE_PUBLIC_HOLIDAY_EVENTS_PLANNING = /*$$(*/
      "Please add an employee's public holiday events planning related to user %s" /*)*/;
  static final String TIMESHEET_TIMESHEET_LINE_LIST_IS_EMPTY = /*$$(*/
      "Timesheet line list is empty, please add a timesheet line list" /*)*/;
  static final String TIMESHEET_HOLIDAY = /*$$(*/ "Holiday" /*)*/;
  static final String TIMESHEET_DAY_LEAVE = /*$$(*/ "Day leave" /*)*/;

  static final String LEAVE_USER_EMPLOYEE = /*$$(*/
      "Please create an employee for the user %s" /*)*/;
  static final String LEAVE_LINE = /*$$(*/
      "There is no leave line for the employee %s and the reason %s." /*)*/;
  static final String LEAVE_ALLOW_NEGATIVE_VALUE_EMPLOYEE = /*$$(*/
      "Employee %s is not allowed to take leave in advance." /*)*/;
  static final String LEAVE_ALLOW_NEGATIVE_VALUE_REASON = /*$$(*/
      "You are not able to take leave in advance for the reason '%s'." /*)*/;
  static final String LEAVE_ALLOW_NEGATIVE_ALERT = /*$$(*/
      "You now have a negative amount of leave available for the reason %s" /*)*/;
  static final String LEAVE_REASON_NO_UNIT = /*$$(*/
      "Please, choose unit in leave reason %s." /*)*/;

  static final String EMPLOYEE_PLANNING = /*$$(*/ "Please, add a planning for employee : %s" /*)*/;
  static final String EMPLOYEE_PUBLIC_HOLIDAY = /*$$(*/
      "Please, add a public holiday planning for employee : %s" /*)*/;
  static final String EMPLOYEE_CONTRACT_OF_EMPLOYMENT = /*$$(*/
      "Please, add a contract of employment for employee : %s" /*)*/;

  static final String BATCH_MISSING_FIELD = /*$$(*/
      "Leave reason and day number have to be defined" /*)*/;
  static final String EMPLOYEE_DOUBLE_LEAVE_MANAGEMENT = /*$$(*/
      "The employee %s has multiple %s leave lines" /*)*/;
  static final String EMPLOYEE_NO_LEAVE_MANAGEMENT = /*$$(*/
      "The employee %s has no %s leave line" /*)*/;
  static final String EMPLOYEE_NO_SENIORITY_DATE = /*$$(*/
      "The employee %s has no seniority date" /*)*/;
  static final String EMPLOYEE_NO_BIRTH_DATE = /*$$(*/ "The employee %s has no birth date" /*)*/;

  static final String BATCH_LEAVE_MANAGEMENT_ENDING_0 = /*$$(*/
      "Employees' leaves attempted to be computed : %s" /*)*/;
  static final String BATCH_LEAVE_MANAGEMENT_ENDING_1 = /*$$(*/
      "Employees' leaves successfully computed : %s" /*)*/;
  static final String BATCH_LEAVE_MANAGEMENT_ENDING_2 = /*$$(*/
      "Employees' leaves failed to be computed due to configuration anomaly : %s" /*)*/;
  static final String BATCH_LEAVE_MANAGEMENT_ENDING_3 = /*$$(*/
      "Employees' leaves failed to be computed due to missing data : %s" /*)*/;
  static final String BATCH_LEAVE_MANAGEMENT_QTY_OUT_OF_BOUNDS = /*$$(*/
      "Qty must be lower than %d." /*)*/;
  static final String BATCH_SENIORITY_LEAVE_MANAGEMENT_FORMULA = /*$$(*/
      "There is an error in a formula" /*)*/;
  static final String BATCH_PAYROLL_PREPARATION_GENERATION_RECAP = /*$$(*/
      "Payroll preparations attempted to be generated : %s" /*)*/;
  static final String BATCH_PAYROLL_PREPARATION_SUCCESS_RECAP = /*$$(*/
      "Payroll preparations successfully generated : %s" /*)*/;
  static final String BATCH_PAYROLL_PREPARATION_DUPLICATE_RECAP = /*$$(*/
      "Payroll preparations failed to be generated due to a duplicate one : %s" /*)*/;
  static final String BATCH_PAYROLL_PREPARATION_CONFIGURATION_RECAP = /*$$(*/
      "Payroll preparations failed to be generated due to missing data : %s" /*)*/;
  static final String BATCH_PAYROLL_PREPARATION_EXPORT_RECAP = /*$$(*/
      "Payroll preparations exported : %s" /*)*/;

  static final String BATCH_TIMESHEET_MISSING_TEMPLATE = /*$$(*/
      "You must choose a template." /*)*/;
  static final String BATCH_TIMESHEET_REMINDER_DONE = /*$$(*/ "Employees computed: %d" /*)*/;
  static final String BATCH_TIMESHEET_REMINDER_ANOMALY = /*$$(*/
      "Employees failed to be computed due to anomaly: %d" /*)*/;

  static final String BATCH_CREDIT_TRANSFER_EXPENSE_DONE_SINGULAR = /*$$(*/
      "%d expense treated successfully," /*)*/;
  static final String BATCH_CREDIT_TRANSFER_EXPENSE_DONE_PLURAL = /*$$(*/
      "%d expenses treated successfully," /*)*/;

  static final String LUNCH_VOUCHER_MIN_STOCK = /*$$(*/
      "Minimum stock of lunch vouchers will be reached for the company %s. Minimum Stock allowed : %s. Available Stock : %s" /*)*/;

  static final String KILOMETRIC_LOG_NO_YEAR = /*$$(*/
      "There is no year for society %s which includes date %s" /*)*/;

  static final String KILOMETRIC_ALLOWANCE_NO_RULE = /*$$(*/
      "There is no matching condition for the allowance %s" /*)*/;
  static final String KILOMETRIC_ALLOWANCE_NO_DATE_SELECTED = /*$$(*/
      "There is no year selected for the allowance." /*)*/;

  static final String PAYROLL_PREPARATION_DUPLICATE = /*$$(*/
      "There is already a payroll preparation for the employee %s, the company %s and the period %s" /*)*/;

  /** Expense service */
  static final String EXPENSE_JOURNAL = /*$$(*/
      "You must configure an expenses journal(company : %s)" /*)*/;

  static final String EXPENSE_ACCOUNT = /*$$(*/
      "You must configure an expenses account (company : %s)" /*)*/;
  static final String EXPENSE_ACCOUNT_TAX = /*$$(*/
      "You must configure an account for expenses taxes (company : %s)" /*)*/;
  static final String EXPENSE_CANCEL_MOVE = /*$$(*/
      "Move already used, you must unreconcile it first" /*)*/;

  static final String EXPENSE_TAX_PRODUCT = /*$$(*/ "No Tax for the product %s" /*)*/;
  static final String EXPENSE_MISSING_PERIOD = /*$$(*/ "Please fill the period" /*)*/;
  static final String EXPENSE_MISSING_PAYMENT_MODE = /*$$(*/ "Please fill the payment mode." /*)*/;

  /** Timesheet Editor */
  static final String NEW_PROJECT_LINE = /*$$(*/ "New project line" /*)*/;

  /** Kilometric allowance */
  String KILOMETRIC_ALLOWANCE_GOOGLE_MAPS_ERROR = /*$$(*/ "Google Maps error: %s" /*)*/;

  static final String EXPENSE_PAYMENT_CANCEL = /*$$(*/
      "The bank order linked to this expense has already been carried out/rejected, and thus can't be canceled" /*)*/;

  /** Kilometric service */
  static final String KILOMETRIC_ALLOWANCE_RATE_MISSING = /*$$(*/
      "The kilometric allowance rate corresponding to the kilometric allow param %s and the company %s is missing" /*)*/;

  /** TsTimer Service */
  String NO_TIMESHEET_CREATED = /*$$(*/
      "No timesheet line has been created because the duration is less than 1 minute" /*)*/;

  static final String EXPENSE_NOT_SELECTED = /*$$(*/ "Please, select an expense" /*)*/;

  static final String BATCH_EMPLOYMENT_CONTRACT_EXPORT_RECAP = /*$$(*/
      "Employment contracts exported : %s" /*)*/;

  static final String UNIT_SELECT_FOR_LEAVE_REASON = /*$$(*/
      "Please configure the unit for this type of absence" /*)*/;

  /** DPAE Service */
  static final String DPAE_FIELD_INVALID = /*$$(*/ "Field %s missing or invalid in DPAE." /*)*/;

  static final String DPAE_PRINT = /*$$(*/ "Please select the employee(s) to generate DPAE." /*)*/;

  static final String DPAE_PRINTS_SUCCESSFUL = /*$$(*/ "%s DPAE generated." /*)*/;

  static final String DPAE_PRINTS_ERROR = /*$$(*/ "%s DPAE generated.\n%s with errors." /*)*/;

  static final String DPAE_SEND_SELECT = /*$$(*/ "Please select one or more DPAE to send." /*)*/;

  static final String DPAE_ALREADY_SEND = /*$$(*/
      "DPAE already sent. Please uncheck the boolean in DPAE." /*)*/;

  static final String DPAE_SEND = /*$$(*/ "DPAE correctly sent." /*)*/;

  static final String DPAE_MISSING_TEMPLATE = /*$$(*/
      "DPAE template is missing. Please select one." /*)*/;
}
