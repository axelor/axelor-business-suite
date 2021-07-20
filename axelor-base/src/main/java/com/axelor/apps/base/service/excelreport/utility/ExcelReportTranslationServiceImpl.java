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
package com.axelor.apps.base.service.excelreport.utility;

import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.service.excelreport.config.ExcelReportHelperService;
import com.google.inject.Inject;
import java.util.ResourceBundle;

public class ExcelReportTranslationServiceImpl implements ExcelReportTranslationService {

  @Inject private ExcelReportHelperService excelReportHelperService;

  private static final String TRANSLATION_FUNCTION_NORMAL_QUOTE = "_tr('";
  private static final String TRANSLATION_FUNCTION_OPEN_OFFICE_QUOTE = "_tr(‘";
  private static final String TRANSLATION_FUNCTION_VALUE = "_tr(value:";
  private static final String VALUE_PREFIX = "value:";

  @Override
  public boolean isTranslationFunction(String value) {
    boolean isTranslation = false;
    if (value.trim().startsWith(TRANSLATION_FUNCTION_NORMAL_QUOTE)
        || value.trim().startsWith(TRANSLATION_FUNCTION_OPEN_OFFICE_QUOTE)
        || value.trim().startsWith(TRANSLATION_FUNCTION_VALUE)) {
      isTranslation = true;
    }
    return isTranslation;
  }

  @Override
  public String checkTranslationFunction(String value) {

    if (value.trim().startsWith(TRANSLATION_FUNCTION_VALUE)
        && (value.trim().contains("hide") || value.trim().contains("show"))) {
      value =
          org.apache.commons.lang3.StringUtils.replaceOnce(
              value.trim().replace(TRANSLATION_FUNCTION_VALUE, "").trim(), ")", "");

    } else if (value.trim().startsWith(TRANSLATION_FUNCTION_VALUE)) {
      value =
          org.apache.commons.lang3.StringUtils.chop(
              value.trim().replace(TRANSLATION_FUNCTION_VALUE, ""));
    } else if (value.trim().startsWith(TRANSLATION_FUNCTION_NORMAL_QUOTE)
        || value.trim().startsWith(TRANSLATION_FUNCTION_OPEN_OFFICE_QUOTE)) {
      value =
          value
              .trim()
              .replace(TRANSLATION_FUNCTION_NORMAL_QUOTE, "")
              .replace(TRANSLATION_FUNCTION_OPEN_OFFICE_QUOTE, "")
              .replace("')", "")
              .replace("’)", "");
    }
    return value;
  }

  @Override
  public Object getTranslatedValue(Object value, PrintTemplate printTemplate) {

    ResourceBundle resourceBundle = excelReportHelperService.getResourceBundle(printTemplate);

    if (value.toString().trim().startsWith(TRANSLATION_FUNCTION_VALUE)) {
      value = this.getValue(value, resourceBundle);
    } else if (value.toString().trim().startsWith(TRANSLATION_FUNCTION_NORMAL_QUOTE)
        || value.toString().trim().startsWith(TRANSLATION_FUNCTION_OPEN_OFFICE_QUOTE)) {
      value =
          value
              .toString()
              .trim()
              .replace(TRANSLATION_FUNCTION_NORMAL_QUOTE, "")
              .replace(TRANSLATION_FUNCTION_OPEN_OFFICE_QUOTE, "")
              .replace("')", "")
              .replace("’)", "");
      value = resourceBundle.getString(value.toString());
    } else {
      value = this.getValue(value, resourceBundle);
    }
    return value;
  }

  private Object getValue(Object value, ResourceBundle resourceBundle) {
    value = resourceBundle.getString(VALUE_PREFIX + value.toString());
    value =
        value.toString().startsWith(VALUE_PREFIX)
            ? value.toString().replace(VALUE_PREFIX, "")
            : value;
    return value;
  }
}
