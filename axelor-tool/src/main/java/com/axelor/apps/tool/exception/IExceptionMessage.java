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
package com.axelor.apps.tool.exception;

/** @author axelor */
public interface IExceptionMessage {

  /** Period service */
  static final String PERIOD_1 = /*$$(*/ "Years in 360 days" /*)*/;

  /** URL service */
  static final String URL_SERVICE_1 = /*$$(*/
      "Can not opening the connection to a empty URL." /*)*/;

  static final String URL_SERVICE_2 = /*$$(*/ "Url %s is malformed." /*)*/;
  static final String URL_SERVICE_3 = /*$$(*/
      "An error occurs while opening the connection. Please verify the following URL : %s." /*)*/;

  /** Template maker */
  static final String TEMPLATE_MAKER_1 = /*$$(*/ "No such template" /*)*/;

  static final String TEMPLATE_MAKER_2 = /*$$(*/ "Templating can not be empty" /*)*/;

  static final String RECORD_UNIQUE_FIELD = /*$$(*/ "This field needs to be unique." /*)*/;

  /** Pdf Tool */
  static final String BAD_COPY_NUMBER_ARGUMENT = /*$$(*/
      "The parameter copyNumber should be superior to 0." /*)*/;

  // Callable Tool
  String PROCESS_BEING_COMPUTED = /*$$(*/ "Computation in progress..." /*)*/;
}
