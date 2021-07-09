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
/** */
package com.axelor.apps.stock.exception;

/** @author axelor */
public interface IExceptionMessage {

  /** Inventory service and controller */
  static final String INVENTORY_1 = /*$$(*/ "You must select a stock location" /*)*/;

  static final String INVENTORY_2 = /*$$(*/
      "There's no configured sequence for inventory for company" /*)*/;
  static final String INVENTORY_3 = /*$$(*/
      "An error occurred while importing the file data. Please contact your application administrator to check Traceback logs." /*)*/;
  static final String INVENTORY_4 = /*$$(*/
      "An error occurred while importing the file data, product not found with code :" /*)*/;
  static final String INVENTORY_5 = /*$$(*/
      "There is currently no such file in the specified folder or the folder may not exists." /*)*/;
  static final String INVENTORY_6 = /*$$(*/ "Company missing for stock location %s" /*)*/;
  static final String INVENTORY_7 = /*$$(*/ "Incorrect product in inventory line" /*)*/;
  static final String INVENTORY_8 = /*$$(*/ "File %s successfully imported." /*)*/;
  static final String INVENTORY_9 = /*$$(*/ "There's no product in stock location." /*)*/;
  static final String INVENTORY_10 = /*$$(*/ "Inventory's lines' list has been filled." /*)*/;
  static final String INVENTORY_11 = /*$$(*/ "No inventory lines has been created." /*)*/;
  static final String INVENTORY_12 = /*$$(*/
      "An error occurred while importing the file data, there are multiple products with code :" /*)*/;
  static final String INVENTORY_3_LINE_LENGHT = /*$$(*/ "Line length too big" /*)*/;
  static final String INVENTORY_3_REAL_QUANTITY = /*$$(*/ "Real quantity problem" /*)*/;
  static final String INVENTORY_3_CURRENT_QUANTITY = /*$$(*/ "Current quantity problem" /*)*/;
  static final String INVENTORY_3_DATA_NULL_OR_EMPTY = /*$$(*/ "Data is null or empty" /*)*/;

  /** Stock Location Line Service Impl */
  static final String LOCATION_LINE_1 = /*$$(*/
      "Product's stocks %s (%s) are not in sufficient quantity to realize the delivery" /*)*/;

  static final String LOCATION_LINE_2 = /*$$(*/
      "Product's stocks %s (%s), tracking number %s are not in sufficient quantity to realize the delivery" /*)*/;
  static final String LOCATION_LINE_3 = /*$$(*/
      "Product's stocks %s (%s) exceeds maximum stock rules." /*)*/;

  static final String LOCATION_LINE_MISSING_UNIT = /*$$(*/
      "Please fill unit for the stock location line in %s for the product %s." /*)*/;

  static final String DETAIL_LOCATION_LINE_MISSING_UNIT = /*$$(*/
      "Please fill unit for the stock location line with tracking number %s in %s for the product %s." /*)*/;

  /** Stock Move Service and Controller */
  static final String STOCK_MOVE_1 = /*$$(*/
      "There's no configured sequence for stock's intern moves for the company %s" /*)*/;

  static final String STOCK_MOVE_2 = /*$$(*/
      "There's no configured sequence for stock's receptions for the company %s" /*)*/;
  static final String STOCK_MOVE_3 = /*$$(*/
      "There's no configured sequence for stock's delivery for the company %s" /*)*/;
  static final String STOCK_MOVE_4 = /*$$(*/ "Stock's movement's type undefined" /*)*/;
  static final String STOCK_MOVE_5 = /*$$(*/
      "There's no source stock location selected for the stock's movement %s" /*)*/;
  static final String STOCK_MOVE_6 = /*$$(*/
      "There's no destination stock location selected for the stock's movement %s" /*)*/;
  static final String STOCK_MOVE_7 = /*$$(*/ "Partial stock move (From" /*)*/;
  static final String STOCK_MOVE_8 = /*$$(*/ "Reverse stock move (From" /*)*/;
  static final String STOCK_MOVE_9 = /*$$(*/ "A partial stock move has been generated (%s)" /*)*/;
  static final String STOCK_MOVE_10 = /*$$(*/ "Please select the StockMove(s) to print." /*)*/;
  static final String STOCK_MOVE_11 = /*$$(*/ "Company address is empty." /*)*/;
  static final String STOCK_MOVE_12 = /*$$(*/
      "Feature currently not available with Open Street Maps." /*)*/;
  static final String STOCK_MOVE_13 = /*$$(*/ "<B>%s or %s</B> not found" /*)*/;
  static final String STOCK_MOVE_14 = /*$$(*/ "No move lines to split" /*)*/;
  static final String STOCK_MOVE_15 = /*$$(*/ "Please select lines to split" /*)*/;
  static final String STOCK_MOVE_16 = /*$$(*/ "Please enter a valid split quantity" /*)*/;
  static final String STOCK_MOVE_17 = /*$$(*/
      "Must set mass unit in stock configuration for customs." /*)*/;
  static final String STOCK_MOVE_18 = /*$$(*/
      "All storable products used in DEB must have net mass and mass unit information for customs." /*)*/;
  static final String STOCK_MOVE_19 = /*$$(*/
      "Can't realize this stock move because of the ongoing inventory %s." /*)*/;
  static final String STOCK_MOVE_PLANNED_NOT_DELETED = /*$$(*/
      "Can't delete a planned stock move" /*)*/;
  static final String STOCK_MOVE_REALIZED_NOT_DELETED = /*$$(*/
      "Can't delete a realized stock move" /*)*/;
  static final String STOCK_MOVE_SPLIT_NOT_GENERATED = /*$$(*/
      "No new stock move was generated" /*)*/;
  static final String STOCK_MOVE_INCOMING_PARTIAL_GENERATED = /*$$(*/
      "An incoming partial stock move has been generated (%s)" /*)*/;
  static final String STOCK_MOVE_OUTGOING_PARTIAL_GENERATED = /*$$(*/
      "An outgoing partial stock move has been generated (%s)" /*)*/;
  static final String STOCK_MOVE_MISSING_TEMPLATE = /*$$(*/
      "The template to send message on realization is missing." /*)*/;
  static final String STOCK_MOVE_QTY_BY_TRACKING = /*$$(*/
      "The tracking number configuration quantity is equal to zero, it must be at least one." /*)*/;
  static final String STOCK_MOVE_TOO_MANY_ITERATION = /*$$(*/
      "Too many iterations while trying to generate stock move line with tracking numbers." /*)*/;
  static final String STOCK_MOVE_CANNOT_GO_BACK_TO_DRAFT = /*$$(*/
      "Cannot go back to draft status." /*)*/;

  /*
   * Stock Move printing
   */
  String STOCK_MOVES_MISSING_PRINTING_SETTINGS = /*$$(*/
      "Please fill printing settings on following stock moves: %s" /*)*/;

  String STOCK_MOVE_PRINT = /*$$(*/ "Please select the stock move(s) to print" /*)*/;

  /** Tracking Number Service */
  static final String TRACKING_NUMBER_1 = /*$$(*/
      "There's no configured sequence for tracking number for the product %s:%s" /*)*/;

  /** Stock Config Service */
  static final String STOCK_CONFIG_1 = /*$$(*/
      "You must configure a Stock module for the company %s" /*)*/;

  static final String STOCK_CONFIG_2 = /*$$(*/
      "You must configure an inventory virtual stock location for the company %s" /*)*/;
  static final String STOCK_CONFIG_3 = /*$$(*/
      "You must configure a supplier virtual stock location for the company %s" /*)*/;
  static final String STOCK_CONFIG_4 = /*$$(*/
      "You must configure a customer virtual stock location for the company %s" /*)*/;
  static final String STOCK_CONFIG_RECEIPT = /*$$(*/
      "You must configure a default receipt stock location for the company %s" /*)*/;
  static final String STOCK_CONFIG_PICKUP = /*$$(*/
      "You must configure a default pickup stock location for the company %s" /*)*/;

  /** Stock Location Controller */
  static final String LOCATION_1 = /*$$(*/
      "There's already an existing storage, you must deactivate it first" /*)*/;

  static final String LOCATION_2 = /*$$(*/ "Please select the Stock Location(s) to print." /*)*/;

  static final String STOCK_LOCATION_PRINT_WIZARD_TITLE = /*$$(*/ "Select format to Export" /*)*/;

  /** Stock Move Line Service */
  static final String STOCK_MOVE_LINE_MUST_FILL_CONFORMITY =
      /*$$(*/ "Please fill the conformity for the product(s) : %s" /*)*/;

  static final String STOCK_MOVE_LINE_MUST_FILL_TRACKING_NUMBER =
      /*$$(*/ "Please fill the tracking number for the product(s) : %s" /*)*/;

  static final String STOCK_MOVE_LINE_EXPIRED_PRODUCTS = /*$$(*/ "Expired product(s): %s" /*)*/;

  static final String MISSING_PRODUCT_MASS_UNIT = /*$$(*/
      "Please configure mass units for this product packing : %s" /*)*/;

  static final String STOCK_CONFIGURATION_MISSING = /*$$(*/
      "Configuration is missing in stock configuration to see financial data" /*)*/;

  /** Partner Product Quality Rating Service */
  String PARTNER_PRODUCT_QUALITY_RATING_MISSING_PARTNER = /*$$(*/ "Partner is missing." /*)*/;

  /*
   * Logistical form
   */
  String LOGISTICAL_FORM_MISSING_SEQUENCE = /*$$(*/
      "Missing logistical form sequence for company %s" /*)*/;
  String LOGISTICAL_FORM_PARTNER_MISMATCH = /*$$(*/ "Partner mismatch: %s" /*)*/;
  String LOGISTICAL_FORM_LINE_INVALID_DIMENSIONS = /*$$(*/
      "Invalid dimensions on packing line No. %d" /*)*/;
  String LOGISTICAL_FORM_LINE_REQUIRED_TYPE = /*$$(*/ "Type is required on line %d." /*)*/;
  String LOGISTICAL_FORM_LINE_REQUIRED_STOCK_MOVE_LINE = /*$$(*/
      "Stock move line is required on line %d." /*)*/;
  String LOGISTICAL_FORM_LINE_REQUIRED_QUANTITY = /*$$(*/ "Quantity is required on line %d." /*)*/;
  String LOGISTICAL_FORM_LINES_INCONSISTENT_QUANTITY = /*$$(*/
      "Total quantity for %s: %s (expected: %s)" /*)*/;
  String LOGISTICAL_FORM_LINES_EMPTY_PARCEL = /*$$(*/ "Parcel %d is empty." /*)*/;
  String LOGISTICAL_FORM_LINES_EMPTY_PALLET = /*$$(*/ "Pallet %d is empty." /*)*/;
  String LOGISTICAL_FORM_LINES_ORPHAN_DETAIL = /*$$(*/
      "Detail line(s) not inside a parcel/pallet" /*)*/;
  String LOGISTICAL_FORM_UNKNOWN_ACCOUNT_SELECTION = /*$$(*/ "Unknown account selection" /*)*/;

  String LOGISTICAL_FORM_MISSING_TEMPLATE = /*$$(*/
      "The template to send message on save is missing." /*)*/;

  String LOGISTICAL_FORM_INVALID_DIMENSIONS = /*$$(*/ "Invalid field dimensions" /*)*/;

  String CANCEL_REASON_MISSING = /*$$(*/ "A cancel reason must be selected" /*)*/;
  String CANCEL_REASON_BAD_TYPE = /*$$(*/
      "The type of cancel reason doesn't match with stock move" /*)*/;

  /*
   * Declaration of exchanges
   */
  String DECLARATION_OF_EXCHANGES_ECONOMIC_AREA_MISSING = /*$$(*/
      "No economic area is configured for %s." /*)*/;
  String DECLARATION_OF_EXCHANGES_ECONOMIC_AREA_UNSUPPORTED = /*$$(*/
      "Declaration of exchanges for %s is not supported." /*)*/;
  String DECLARATION_OF_EXCHANGES_ECONOMIC_AREA_MISSING_IN_APP_STOCK = /*$$(*/
      "Please set an economic are in AppStock." /*)*/;

  String TRACK_NUMBER_WIZARD_TITLE = /*$$(*/ "Enter tracking numbers" /*)*/;
  String TRACK_NUMBER_WIZARD_NO_RECORD_ADDED_ERROR = /*$$(*/ "No Tracking Numbers Added" /*)*/;

  String TRACK_NUMBER_DATE_MISSING = /*$$(*/
      "Please fill estimated delivery date for product %s from %s" /*)*/;

  /** Stock correction service and controller */
  public static final String STOCK_CORRECTION_1 = /*$$(*/
      "Incorrect product for stock correction" /*)*/;

  public static final String STOCK_CORRECTION_2 = /*$$(*/
      "No stock correction needed, current quantity in stock equals real quantity." /*)*/;

  public static final String INVENTORY_PRODUCT_TRACKING_NUMBER_ERROR = /*$$(*/
      "There is more than one line for same product with same tracking number." /*)*/;

  public static final String USER_LOGIN_WITH_STOCK_LOCATION_WRONG_SERIAL_NUMBER = /*$$(*/
      "No stock location with this serial number" /*)*/;
}
