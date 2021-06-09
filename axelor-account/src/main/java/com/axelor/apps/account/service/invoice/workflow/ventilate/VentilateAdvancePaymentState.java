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
package com.axelor.apps.account.service.invoice.workflow.ventilate;

import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.fixedasset.FixedAssetService;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.exception.AxelorException;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;

@RequestScoped
public class VentilateAdvancePaymentState extends VentilateState {

  @Inject
  public VentilateAdvancePaymentState(
      SequenceService sequenceService,
      MoveService moveService,
      AccountConfigService accountConfigService,
      AppAccountService appAccountService,
      InvoiceRepository invoiceRepo,
      WorkflowVentilationService workflowService,
      UserService userService,
      FixedAssetService fixedAssetService,
      InvoiceTermService invoiceTermService) {
    super(
        sequenceService,
        moveService,
        accountConfigService,
        appAccountService,
        invoiceRepo,
        workflowService,
        userService,
        fixedAssetService,
        invoiceTermService);
  }

  @Override
  public void process() throws AxelorException {

    Preconditions.checkNotNull(invoice.getPartner());

    setDate();
    setJournal();
    setPartnerAccount();
    setInvoiceId();
    updatePaymentSchedule();
    // we don't create the move
    // and the invoice stays validated

    workflowService.afterVentilation(invoice);
  }
}
