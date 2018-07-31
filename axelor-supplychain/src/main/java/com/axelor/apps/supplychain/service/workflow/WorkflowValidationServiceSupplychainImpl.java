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
package com.axelor.apps.supplychain.service.workflow;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.service.invoice.workflow.validate.WorkflowValidationServiceImpl;
import com.axelor.apps.supplychain.service.IntercoService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class WorkflowValidationServiceSupplychainImpl extends WorkflowValidationServiceImpl {

  protected IntercoService intercoService;

  @Inject
  public WorkflowValidationServiceSupplychainImpl(IntercoService intercoService) {
    this.intercoService = intercoService;
  }

  @Override
  public void afterValidation(Invoice invoice) throws AxelorException {
    if (invoice.getInterco()) {
      intercoService.generateIntercoInvoice(invoice);
    }
  }
}
