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
package com.axelor.apps.businessproject.web;

import com.axelor.apps.businessproject.service.TimesheetLineBusinessService;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;

public class TimesheetLineBusinessController {

  @Inject private TimesheetLineBusinessService timesheetLineBusinessService;

  public void setDefaultToInvoice(ActionRequest request, ActionResponse response) {
    TimesheetLine timesheetLine = request.getContext().asType(TimesheetLine.class);
    timesheetLine = timesheetLineBusinessService.getDefaultToInvoice(timesheetLine);
    response.setValue("toInvoice", timesheetLine.getToInvoice());
  }
}
