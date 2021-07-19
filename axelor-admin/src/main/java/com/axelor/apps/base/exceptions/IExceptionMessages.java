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
package com.axelor.apps.base.exceptions;

public interface IExceptionMessages {

  public static final String NO_CONFIG_REQUIRED = /*$$(*/ "No configuration required" /*)*/;

  public static final String APP_IN_USE = /*$$(*/
      "This app is used by %s. Please deactivate them before continue." /*)*/;

  public static final String BULK_INSTALL_SUCCESS = /*$$(*/ "Apps installed successfully" /*)*/;

  public static final String REFRESH_APP_SUCCESS = /*$$(*/ "Apps refreshed successfully" /*)*/;

  public static final String REFRESH_APP_ERROR = /*$$(*/ "Error in refreshing app" /*)*/;

  public static final String NO_LANGUAGE_SELECTED = /*$$(*/
      "No application language set. Please set 'application.locale' property." /*)*/;

  public static final String DEMO_DATA_SUCCESS = /*$$(*/ "Demo data loaded successfully" /*)*/;

  public static final String ACCESS_CONFIG_IMPORTED = /*$$(*/
      "Access config imported successfully" /*)*/;

  public static final String OBJECT_DATA_REPLACE_MISSING = /*$$(*/ "No record found for: %s" /*)*/;

  public static final String ROLE_IMPORT_SUCCESS = /*$$(*/ "Roles imported successfully" /*)*/;

  public static final String DATA_EXPORT_DIR_ERROR = /*$$(*/ "Export path not configured" /*)*/;

  public static final String FILE_UPLOAD_DIR_ERROR = /*$$(*/
      "File upload path not configured" /*)*/;

  public static final String UNIT_TEST_IMPORT_SUCCESS = /*$$(*/ "Import Successfull" /*)*/;

  public static final String UNIT_TEST_EXPORT_RECORD_NOT_SPECIFIED = /*$$(*/
      "Please select atleast one record to export" /*)*/;

  public static final String UNIT_TEST_IMPORT_NAME_NOT_SPECIFIED = /*$$(*/
      "Please specify unit test name" /*)*/;

  public static final String UNIT_TEST_TARGET_VAR_NOT_SPECIFIED = /*$$(*/
      "Target variable is undefined" /*)*/;

  public static final String UNIT_TEST_INVALID_ACTION_NAME = /*$$(*/
      "Action name is invalid no such action found" /*)*/;

  public static final String UNIT_TEST_INVALID_TARGET_FOR_ADD = /*$$(*/
      "Target must be reference field" /*)*/;

  public static final String UNIT_TEST_INVALID_MODEL_NAME = /*$$(*/
      "%s is not valid model name" /*)*/;

  public static final String UNIT_TEST_INVALID_SELECT_FORMAT = /*$$(*/
      "Format should be ModelName : jpql-filter with valid model name" /*)*/;

  public static final String UNIT_TEST_ACTION_SOMETHING_WRONG = /*$$(*/
      "Something went wrong in action %s" /*)*/;

  public static final String UNIT_TEST_ERROR_FROM_ACTION = /*$$(*/
      "Error in action <b>%s</b><br/><li>%s</li>" /*)*/;

  public static final String UNIT_TEST_ALERT_FROM_ACTION = /*$$(*/
      "Alert from action <b>%s</b><br/><li>%s</li>" /*)*/;

  public static final String UNIT_TEST_FLASH_FROM_ACTION = /*$$(*/
      "Message from action <b>%s</b><br/><li>%s</li>" /*)*/;
}
