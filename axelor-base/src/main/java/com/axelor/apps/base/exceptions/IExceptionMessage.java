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
package com.axelor.apps.base.exceptions;

/**
 * Interface of Exceptions.
 *
 * @author dubaux
 */
public interface IExceptionMessage {

  public static final String EXCEPTION = /*$$(*/ "Warning !" /*)*/;

  static final String NOT_IMPLEMENTED_METHOD = /*$$(*/ "Not implemented yet!" /*)*/;

  static final String BIRT_EXTERNAL_REPORT_NO_URL = /*$$(*/
      "Impossible to generate report, url toward Birt viewer is not correctly configured (%s)" /*)*/;

  static final String RECORD_UNSAVED = /*$$(*/ "Unsaved record" /*)*/;
  static final String RECORD_NONE_SELECTED = /*$$(*/ "Please select at least one record." /*)*/;

  /** Currency service and controller */
  static final String CURRENCY_1 = /*$$(*/
      "No currency conversion found from '%s' to '%s' for date %s" /*)*/;

  static final String CURRENCY_2 = /*$$(*/
      "The currency exchange rate from '%s' to '%s' for date %s must be different from zero." /*)*/;
  static final String CURRENCY_3 = /*$$(*/
      "WARNING : please close the current conversion period to create a new one." /*)*/;
  static final String CURRENCY_4 = /*$$(*/
      "The end date has to be greater than or equal to the start date." /*)*/;
  static final String CURRENCY_5 = /*$$(*/
      "Both currencies must be saved before currency rate apply" /*)*/;
  static final String CURRENCY_6 = /*$$(*/ "Currency conversion webservice not working" /*)*/;
  static final String CURRENCY_7 = /*$$(*/
      "No currency conversion rate found for the date %s to %s. Please change the conversion date" /*)*/;

  static final String COMPANY_CURRENCY = /*$$(*/
      "%s : Please, configure a currency for the company %s" /*)*/;

  /** Unit conversion service */
  static final String UNIT_CONVERSION_1 = /*$$(*/
      "Please configure unit conversion from '%s' to '%s'." /*)*/;

  static final String UNIT_CONVERSION_2 = /*$$(*/ "Please configure units." /*)*/;

  static final String CURRENCY_CONVERSION_1 = /*$$(*/
      "WARNING : Please close the current conversion period before creating new one" /*)*/;
  static final String CURRENCY_CONVERSION_2 = /*$$(*/
      "WARNING : To Date must be after or equals to From Date" /*)*/;

  /** Account management service */
  public static final String ACCOUNT_MANAGEMENT_2 = /*$$(*/ "No tax found for product %s" /*)*/;

  public static final String ACCOUNT_MANAGEMENT_3 = /*$$(*/
      "Tax configuration is missing for Product: %s (company: %s)" /*)*/;

  /** Period service */
  public static final String PERIOD_1 = /*$$(*/
      "No period found or it has been closed for the company %s for the date %s" /*)*/;

  public static final String PERIOD_2 = /*$$(*/ "Period closed" /*)*/;
  public static final String PERIOD_3 = /*$$(*/ "Too much iterations." /*)*/;
  static final String PAY_PERIOD_CLOSED = /*$$(*/ "Warning : the pay period %s is closed." /*)*/;
  /** Abstract batch */
  String ABSTRACT_BATCH_1 = /*$$(*/ "This batch is not runnable!" /*)*/;

  String ABSTRACT_BATCH_2 = /*$$(*/ "Nested batch execution!" /*)*/;
  String ABSTRACT_BATCH_REPORT = /*$$(*/ "Batch report:" /*)*/;
  String ABSTRACT_BATCH_DONE_SINGULAR = /*$$(*/ "%d record processed successfully," /*)*/;
  String ABSTRACT_BATCH_DONE_PLURAL = /*$$(*/ "%d records processed successfully," /*)*/;
  String ABSTRACT_BATCH_ANOMALY_SINGULAR = /*$$(*/ "%d anomaly." /*)*/;
  String ABSTRACT_BATCH_ANOMALY_PLURAL = /*$$(*/ "%d anomalies." /*)*/;
  /** Indicator generator grouping service */
  public static final String INDICATOR_GENERATOR_GROUPING_1 = /*$$(*/
      "Error : no export path has been set" /*)*/;

  public static final String INDICATOR_GENERATOR_GROUPING_2 = /*$$(*/
      "Error : no code has been set" /*)*/;
  public static final String INDICATOR_GENERATOR_GROUPING_3 = /*$$(*/
      "Error while creating the file" /*)*/;
  public static final String INDICATOR_GENERATOR_GROUPING_4 = /*$$(*/ "Result exported" /*)*/;
  /** Indicator generator service */
  public static final String INDICATOR_GENERATOR_1 = /*$$(*/
      "Error : a request has to be set for the indicatior generator %s" /*)*/;

  public static final String INDICATOR_GENERATOR_2 = /*$$(*/
      "Error : incorrect request for the indicatior generator %s" /*)*/;
  public static final String INDICATOR_GENERATOR_3 = /*$$(*/ "Request performed" /*)*/;

  /** Alarm engine batch service */
  public static final String ALARM_ENGINE_BATCH_1 = /*$$(*/ "Alarm Engine %s" /*)*/;

  public static final String ALARM_ENGINE_BATCH_2 = /*$$(*/ "Alarms report :" /*)*/;
  public static final String ALARM_ENGINE_BATCH_3 = /*$$(*/ "* %s object(s) into alarm" /*)*/;
  public static final String ALARM_ENGINE_BATCH_4 = /*$$(*/ "* %s anomaly(ies)" /*)*/;
  public static final String ALARM_ENGINE_BATCH_5 = /*$$(*/ "Alarm batch" /*)*/;

  /** Base batch service */
  public static final String BASE_BATCH_1 = /*$$(*/ "Unknown action %s for the %s treatment" /*)*/;

  public static final String BASE_BATCH_2 = /*$$(*/ "Batch %s unknown" /*)*/;

  /** Product service */
  public static final String PRODUCT_NO_SEQUENCE = /*$$(*/
      "There is no configured sequence for product" /*)*/;

  /** Importer */
  public static final String IMPORTER_1 = /*$$(*/ "Error : Mapping file is unreachable." /*)*/;

  public static final String IMPORTER_2 = /*$$(*/ "Error : Data file is unreachable." /*)*/;
  public static final String IMPORTER_3 = /*$$(*/ "Error : Mapping file is not found." /*)*/;

  /** Importer Listener */
  public static final String IMPORTER_LISTERNER_1 = /*$$(*/ "Total :" /*)*/;

  public static final String IMPORTER_LISTERNER_2 = /*$$(*/ "- Succeeded :" /*)*/;
  public static final String IMPORTER_LISTERNER_3 = /*$$(*/ "Generated anomalies :" /*)*/;
  public static final String IMPORTER_LISTERNER_4 = /*$$(*/
      "The line cannot be imported (import : %s)" /*)*/;
  public static final String IMPORTER_LISTERNER_5 = /*$$(*/ "- Not null :" /*)*/;

  /** Template message service base impl */
  public static final String TEMPLATE_MESSAGE_BASE_1 = /*$$(*/
      "%s : Path to Birt template is incorrect" /*)*/;

  public static final String TEMPLATE_MESSAGE_BASE_2 = /*$$(*/
      "Unable to generate Birt report file" /*)*/;

  /** Querie Service and controller */
  public static final String QUERIE_1 = /*$$(*/
      "Error : There is no query set for the querie %s" /*)*/;

  public static final String QUERIE_2 = /*$$(*/ "Error : Incorrect query for the querie %s" /*)*/;
  public static final String QUERIE_3 = /*$$(*/ "Valid query." /*)*/;

  /** Tax service */
  public static final String TAX_1 = /*$$(*/ "Please enter a tax version for the tax %s" /*)*/;

  public static final String TAX_2 = /*$$(*/ "Tax is missing" /*)*/;

  /** Template rule service */
  public static final String TEMPLATE_RULE_1 = /*$$(*/ "Bean is not an instance of" /*)*/;

  /** Sequence service */
  public static final String SEQUENCE_NOT_SAVED_RECORD = /*$$(*/
      "Can't generate draft sequence number on an unsaved record." /*)*/;

  /** Address controller */
  public static final String ADDRESS_1 = /*$$(*/ "OK" /*)*/;

  public static final String ADDRESS_2 = /*$$(*/
      "Service unavailable, please contact a administrator" /*)*/;
  public static final String ADDRESS_3 = /*$$(*/
      "There is no matching address in the QAS base" /*)*/;
  public static final String ADDRESS_4 = /*$$(*/ "NA" /*)*/;
  public static final String ADDRESS_5 = /*$$(*/ "<B>%s</B> not found" /*)*/;
  public static final String ADDRESS_6 = /*$$(*/
      "Feature currently not available with Open Street Maps." /*)*/;
  public static final String ADDRESS_7 = /*$$(*/
      "Current user's active company address is not set" /*)*/;
  public static final String ADDRESS_8 = /*$$(*/
      "You can select only one default invoicing address." /*)*/;
  public static final String ADDRESS_9 = /*$$(*/
      "You can select only one default delivery address." /*)*/;
  public static final String ADDRESS_CANNOT_BE_NULL = "Address cannot be null.";

  /** Bank details controller */
  public static final String BANK_DETAILS_1 = /*$$(*/
      "The entered IBAN code is not valid . <br> Either the code doesn't respect the norm, or the format you have entered is not correct. It has to be without any blank space, as the following : <br> FR0000000000000000000000000" /*)*/;

  public static final String BANK_DETAILS_2 = /*$$(*/
      "At least one iban code you have entered for this partner is not valid. Here is the list of invalid codes : %s" /*)*/;

  /** General controller */
  public static final String GENERAL_1 = /*$$(*/ "No duplicate records found" /*)*/;

  public static final String GENERAL_2 = /*$$(*/ "Duplicate records" /*)*/;
  public static final String GENERAL_3 = /*$$(*/
      "Please select key fields to check duplicate" /*)*/;
  public static final String GENERAL_4 = /*$$(*/
      "Attachment directory OR Application source does not exist" /*)*/;
  public static final String GENERAL_5 = /*$$(*/ "Export Object" /*)*/;
  public static final String GENERAL_6 = /*$$(*/ "Connection successful" /*)*/;
  public static final String GENERAL_7 = /*$$(*/ "Error in Connection" /*)*/;
  public static final String GENERAL_8 = /*$$(*/
      "Duplicate finder field '%s' is not found inside model '%s'" /*)*/;
  public static final String GENERAL_9 = /*$$(*/
      "Invalid duplicate finder field '%s'. Field type ManyToMany or OneToMany is not supported for duplicate check" /*)*/;
  public static final String GENERAL_10 = /*$$(*/ "No duplicate finder field configured." /*)*/;
  public static final String GENERAL_11 = /*$$(*/ "Please select original object." /*)*/;

  /** Messsage controller */
  public static final String MESSAGE_1 = /*$$(*/
      "Error in print. Please check report configuration and print setting." /*)*/;

  public static final String MESSAGE_2 = /*$$(*/ "Please select the Message(s) to print." /*)*/;

  /** Partner controller */
  public static final String PARTNER_1 = /*$$(*/ "There is no sequence set for the partners" /*)*/;

  public static final String PARTNER_2 = /*$$(*/
      "%s SIRET Number required. Please configure SIRET Number for partner %s" /*)*/;
  public static final String PARTNER_3 = /*$$(*/
      "Can’t convert into an individual partner from scratch." /*)*/;
  public static final String PARTNER_NOT_FOUND = /*$$(*/ "Partner not found" /*)*/;
  public static final String PARTNER_EMAIL_EXIST = /*$$(*/
      "Email address already linked with another partner" /*)*/;

  /** Product controller */
  public static final String PRODUCT_1 = /*$$(*/ "Variants generated" /*)*/;

  public static final String PRODUCT_2 = /*$$(*/ "Prices updated" /*)*/;
  static final String PRODUCT_NO_ACTIVE_COMPANY = /*$$(*/
      "No active company for this user, please define an active company." /*)*/;

  /** Calendar */
  static final String CALENDAR_NOT_VALID = /*$$(*/ "Calendar configuration not valid" /*)*/;

  static final String IMPORT_CALENDAR = /*$$(*/ "Import calendar" /*)*/;

  static final String CALENDAR_NO_EVENTS_FOR_SYNC_ERROR = /*$$(*/
      "Calendars are empty, there is no event to synchronize." /*)*/;

  /** Price list */
  String PRICE_LIST_DATE_WRONG_ORDER = /*$$(*/ "The end date is before the begin date." /*)*/;

  String PARTNER_PRICE_LIST_DATE_INCONSISTENT = /*$$(*/
      "The price list %s will still be active when the price list %s will become active." /*)*/;

  /** Advanced export */
  static final String ADVANCED_EXPORT_1 = /*$$(*/ "Please select fields for export." /*)*/;

  static final String ADVANCED_EXPORT_2 = /*$$(*/ "There is no records to export." /*)*/;
  static final String ADVANCED_EXPORT_3 = /*$$(*/
      "Warning : Exported maximum export limit records." /*)*/;
  static final String ADVANCED_EXPORT_4 = /*$$(*/
      "Please select export object or export format." /*)*/;

  /** Barcode Generator Service */
  public static final String BARCODE_GENERATOR_1 = /*$$(*/
      "Invalid serial number '%s' for '%s' barcode type.It must be of %d digits only." /*)*/;

  public static final String BARCODE_GENERATOR_2 = /*$$(*/
      "Invalid serial number '%s' for '%s' barcode type.It must be digits only with even number length." /*)*/;
  public static final String BARCODE_GENERATOR_3 = /*$$(*/
      "Invalid serial number '%s' for '%s' barcode type.It must be digits only" /*)*/;
  public static final String BARCODE_GENERATOR_4 = /*$$(*/
      "Invalid Serial Number '%s' for '%s' barcode type.Alphabets must be in uppercase only" /*)*/;
  public static final String BARCODE_GENERATOR_5 = /*$$(*/
      "Invalid Serial Number '%s' for '%s' barcode type.Its length limit must be greater than %d and less than %d" /*)*/;
  public static final String BARCODE_GENERATOR_6 = /*$$(*/
      "Invalid Serial Number '%s' for '%s' barcode type.It must be alphanumeric" /*)*/;
  public static final String BARCODE_GENERATOR_7 = /*$$(*/
      "Invalid Serial Number '%s' for '%s' barcode type.Its Length must be %d" /*)*/;
  public static final String BARCODE_GENERATOR_8 = /*$$(*/
      "Invalid Serial Number '%s' for '%s' barcode type.It must be only number or only alphabets" /*)*/;
  public static final String BARCODE_GENERATOR_9 = /*$$(*/ "Barcode format not supported" /*)*/;

  public static final String MAP_RESPONSE_ERROR = /*$$(*/ "Response error from map API: %s" /*)*/;;
  public static final String MAP_GOOGLE_MAPS_API_KEY_MISSING = /*$$(*/
      "Google Maps API key is missing in configuration." /*)*/;

  /** Weekly planning service */
  public static final String WEEKLY_PLANNING_1 = /*$$(*/ "Invalid times %s morning" /*)*/;

  public static final String WEEKLY_PLANNING_2 = /*$$(*/
      "Invalid times on %s between morning and afternoon" /*)*/;
  public static final String WEEKLY_PLANNING_3 = /*$$(*/ "Invalid times %s afternoon" /*)*/;
  public static final String WEEKLY_PLANNING_4 = /*$$(*/
      "Some times are null and should not on %s" /*)*/;

  /*
   * User service
   */
  String USER_CODE_ALREADY_EXISTS = /*$$(*/ "A user with this login already exists." /*)*/;
  String USER_PATTERN_MISMATCH_ACCES_RESTRICTION = /*$$(*/
      "Password must have at least 8 characters with at least three of these four types: lowercase, uppercase, digit, special." /*)*/;
  String USER_PATTERN_MISMATCH_CUSTOM = /*$$(*/
      "Password doesn't match with configured pattern." /*)*/;

  /** Convert demo data file */
  public static final String DUPLICATE_CSV_FILE_NAME_EXISTS = /*$$(*/
      "Please remove duplicate csv file name from excel file." /*)*/;

  public static final String CSV_FILE_NAME_NOT_EXISTS = /*$$(*/
      "Please provide valid csv file name." /*)*/;
  public static final String EXCEL_FILE_FORMAT_ERROR = /*$$(*/
      "Improper format of excel file." /*)*/;
  public static final String VALIDATE_FILE_TYPE = /*$$(*/ "Please import only excel file." /*)*/;

  String TIMER_IS_NOT_STOPPED = /*$$(*/ "You can't start a timer that has already started" /*)*/;
  String TIMER_IS_NOT_STARTED = /*$$(*/ "You can't stop a timer that hasn't been started" /*)*/;

  /** write to CSV from excel sheet */
  public static final String INVALID_HEADER = /*$$(*/ "Header is not valid." /*)*/;

  /** Import demo data from excel */
  public static final String MODULE = /*$$(*/ "Module" /*)*/;

  public static final String MODULE_NOT_EXIST = /*$$(*/ "%s module does not exist." /*)*/;
  public static final String DATA_FILE = /*$$(*/ "Data file" /*)*/;
  public static final String CONFIGURATION_FILE = /*$$(*/ "Configuration file" /*)*/;
  public static final String CONFIGURATION_FILE_NOT_EXIST = /*$$(*/
      "%s configuration file is not exist." /*)*/;
  public static final String ROW_NOT_EMPTY = /*$$(*/ "%s row must not be empty." /*)*/;
  public static final String CELL_NOT_VALID = /*$$(*/ "%s cell is not valid." /*)*/;
  public static final String IMPORT_COMPLETED_MESSAGE = /*$$(*/
      "Import completed successfully. Please check the log for more details." /*)*/;
  public static final String INVALID_DATA_FORMAT_ERROR = /*$$(*/
      "Invalid data format. Please check log for more details." /*)*/;

  /* ABC Analysis */
  public static final String ABC_CLASSES_INVALID_STATE_FOR_REPORTING = /*$$(*/
      "The analysis must be completed before the report can be printed." /*)*/;
  public static final String ABC_CLASSES_INVALID_QTY_OR_WORTH = /*$$(*/
      "The classes total quantity and total worth must equal 100%." /*)*/;
  public static final String ABC_CLASSES_NEGATIVE_OR_NULL_QTY_OR_WORTH = /*$$(*/
      "The worth and quantity value of each class must be greater than 0." /*)*/;

  /* DMS Import */
  public static final String DMS_IMPORT_PROCESS_SUCCESS_MESSAGE = /*$$(*/
      "File loaded successfully" /*)*/;
  public static final String DMS_IMPORT_FILE_PROCESS_ERROR = /*$$(*/
      "Error while processing zip file" /*)*/;
  public static final String DMS_IMPORT_INVALID_ZIP_ERROR = /*$$(*/
      "Uploaded file is not a valid zip file" /*)*/;

  /** Advanced Import */
  public static final String ADVANCED_IMPORT_NO_IMPORT_FILE = /*$$(*/
      "Data file doesn't exist" /*)*/;

  public static final String ADVANCED_IMPORT_FILE_FORMAT_INVALID = /*$$(*/
      "Data file format is invalid" /*)*/;
  public static final String ADVANCED_IMPORT_ATTACHMENT_FORMAT = /*$$(*/
      "Attachments must be in zip format" /*)*/;
  public static final String ADVANCED_IMPORT_1 = /*$$(*/
      "Field(%s) doesn't exist for the object(%s)" /*)*/;
  public static final String ADVANCED_IMPORT_2 = /*$$(*/
      "Sub field(%s) doesn't exist of field(%s) for the object(%s)" /*)*/;
  public static final String ADVANCED_IMPORT_3 = /*$$(*/
      "Config exist in the file. Please check 'Config included in file'" /*)*/;
  public static final String ADVANCED_IMPORT_4 = /*$$(*/
      "Config doesn't exist in the file. Please uncheck 'Config included in file'" /*)*/;
  public static final String ADVANCED_IMPORT_5 = /*$$(*/
      "Sub field doesn't exist of field(%s) for the object(%s)" /*)*/;
  public static final String ADVANCED_IMPORT_6 = /*$$(*/
      "Search fields are missing for the object(%s)" /*)*/;
  public static final String ADVANCED_IMPORT_TAB_ERR = /*$$(*/
      "File tab is not matched inside file" /*)*/;
  public static final String ADVANCED_IMPORT_NO_OBJECT = /*$$(*/
      "Object is missing for tab configuration(%s)" /*)*/;
  public static final String ADVANCED_IMPORT_NO_FIELDS = /*$$(*/
      "There is no field for tab configuration(%s)" /*)*/;
  public static final String ADVANCED_IMPORT_CHECK_LOG = /*$$(*/
      "Check log file in tabs configuration" /*)*/;
  public static final String ADVANCED_IMPORT_IMPORT_DATA = /*$$(*/
      "Data imported successfully" /*)*/;
  public static final String ADVANCED_IMPORT_LOG_1 = /*$$(*/ "Object is not matched" /*)*/;
  public static final String ADVANCED_IMPORT_LOG_2 = /*$$(*/ "Missing import fields" /*)*/;
  public static final String ADVANCED_IMPORT_LOG_3 = /*$$(*/ "Missing required fields" /*)*/;
  public static final String ADVANCED_IMPORT_LOG_4 = /*$$(*/ "Missing sub fields for" /*)*/;
  public static final String ADVANCED_IMPORT_LOG_5 = /*$$(*/ "Fields can't be ignore" /*)*/;
  public static final String ADVANCED_IMPORT_LOG_6 = /*$$(*/
      "Missing date format or expression" /*)*/;
  public static final String ADVANCED_IMPORT_LOG_7 = /*$$(*/ "Invalid fields" /*)*/;
  public static final String ADVANCED_IMPORT_LOG_8 = /*$$(*/
      "Missing data for required fields" /*)*/;
  public static final String ADVANCED_IMPORT_LOG_9 = /*$$(*/ "Invalid type of data" /*)*/;
  public static final String ADVANCED_IMPORT_RESET = /*$$(*/
      "Reset imported data successfully" /*)*/;
  public static final String ADVANCED_IMPORT_NO_RESET = /*$$(*/ "No imported data to reset" /*)*/;
  public static final String SERVER_CONNECTION_ERROR = /*$$(*/
      "Unable to connect to Geonames server" /*)*/;

  /** External Report */
  public static final String TEMPLATE_FIELD_EMPTY = /*$$(*/ "Please select birt template." /*)*/;

  public static final String MISSING_TEMPLATE_FILE = /*$$(*/
      "Missing template file on birt template." /*)*/;
  public static final String META_MODEL_NOT_FOUND = /*$$(*/
      "There is no birt template to print report for %s" /*)*/;
  public static final String MISSING_BIRT_TEMPLATE_ID = /*$$(*/
      "Id field is missing please select birt template." /*)*/;
  public static final String MISSING_RECORD_ID = /*$$(*/ "Please save this record." /*)*/;
}
