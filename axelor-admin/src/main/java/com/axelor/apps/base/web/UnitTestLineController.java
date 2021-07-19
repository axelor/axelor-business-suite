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
package com.axelor.apps.base.web;

import com.axelor.apps.base.db.UnitTest;
import com.axelor.apps.base.db.UnitTestLine;
import com.axelor.apps.base.db.repo.UnitTestLineRepository;
import com.axelor.apps.base.exceptions.IExceptionMessages;
import com.axelor.apps.base.service.unit.testing.UnitTestLineService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.util.HashMap;
import java.util.Map;

public class UnitTestLineController {

  private static final String TEST_LINE_TARGET = "target";
  private static final String TEST_LINE_VALUE = "value";

  public void validateOnChange(ActionRequest request, ActionResponse response) {
    UnitTestLine testLine = request.getContext().asType(UnitTestLine.class);
    UnitTest test = request.getContext().getParent().asType(UnitTest.class);
    UnitTestLineService unitTestLineService = Beans.get(UnitTestLineService.class);

    final String target = testLine.getTarget();
    final String actionTypeSelect = testLine.getActionTypeSelect();
    final String value = testLine.getValue();

    Map<String, String> uniqueErrors = new HashMap<>();
    uniqueErrors.put(TEST_LINE_TARGET, "");
    uniqueErrors.put(TEST_LINE_VALUE, "");

    if (unitTestLineService.isTargetUndefined(test, testLine)) {
      uniqueErrors.put(
          TEST_LINE_TARGET, I18n.get(IExceptionMessages.UNIT_TEST_TARGET_VAR_NOT_SPECIFIED));
    } else if (isTargetValidForAdd(target, actionTypeSelect)) {
      uniqueErrors.put(
          TEST_LINE_TARGET, I18n.get(IExceptionMessages.UNIT_TEST_INVALID_TARGET_FOR_ADD));
    }
    if (unitTestLineService.isValidAction(testLine)) {
      uniqueErrors.put(TEST_LINE_VALUE, I18n.get(IExceptionMessages.UNIT_TEST_INVALID_ACTION_NAME));
    } else if (isValidModelName(actionTypeSelect, value)) {
      uniqueErrors.put(
          TEST_LINE_VALUE,
          String.format(I18n.get(IExceptionMessages.UNIT_TEST_INVALID_MODEL_NAME), value));
    } else if (isValidSelectValueFormat(actionTypeSelect, value)) {
      uniqueErrors.put(
          TEST_LINE_VALUE,
          String.format(I18n.get(IExceptionMessages.UNIT_TEST_INVALID_SELECT_FORMAT), value));
    }

    setErrors(response, uniqueErrors);
  }

  protected boolean isValidModelName(String actionTypeSelect, String modelName) {
    return UnitTestLineRepository.ACTION_TYPE_SELECT_CREATE.equals(actionTypeSelect)
        && StringUtils.notBlank(modelName)
        && ObjectUtils.isEmpty(JPA.model(modelName.trim()));
  }

  protected boolean isTargetValidForAdd(String target, String actionTypeSelect) {
    return UnitTestLineRepository.ACTION_TYPE_SELECT_ADD.equals(actionTypeSelect)
        && StringUtils.notBlank(target)
        && target.indexOf('.') <= 0;
  }

  protected boolean isValidSelectValueFormat(String actionTypeSelect, final String value) {

    if (!UnitTestLineRepository.ACTION_TYPE_SELECT_SELECT.equals(actionTypeSelect)
        || StringUtils.isBlank(value)) {
      return false;
    }

    String[] valueParts = value.trim().split(":");
    // Check if format is correct and validate model name
    return valueParts.length != 2
        || StringUtils.isBlank(valueParts[0])
        || ObjectUtils.isEmpty(JPA.model(valueParts[0].trim()))
        || StringUtils.isBlank(valueParts[1]);
  }

  protected void setErrors(ActionResponse response, Map<String, String> uniqueErrors) {
    for (Map.Entry<String, String> entry : uniqueErrors.entrySet()) {
      response.addError(entry.getKey(), entry.getValue());
    }
  }
}
