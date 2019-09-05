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
package com.axelor.apps.account.web;

import com.axelor.apps.account.db.DebtRecovery;
import com.axelor.apps.account.db.repo.DebtRecoveryRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.debtrecovery.DebtRecoveryActionService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DebtRecoveryController {
  private DebtRecoveryRepository debtRecoveryRepository;
  private DebtRecoveryActionService debtRecoveryActionService;

  @Inject
  public DebtRecoveryController(
      DebtRecoveryRepository debtRecoveryRepository,
      DebtRecoveryActionService debtRecoveryActionService) {
    this.debtRecoveryRepository = debtRecoveryRepository;
    this.debtRecoveryActionService = debtRecoveryActionService;
  }

  public void runDebtRecovery(ActionRequest request, ActionResponse response) {
    DebtRecovery debtRecovery = request.getContext().asType(DebtRecovery.class);

    debtRecovery = debtRecoveryRepository.find(debtRecovery.getId());
    try {
      if (debtRecovery.getAccountingSituation() == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.DEBT_RECOVERY_1));
      } else if (!debtRecovery.getDebtRecoveryMethodLine().getManualValidationOk()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.DEBT_RECOVERY_5));
      }
      debtRecovery.setDebtRecoveryMethodLine(debtRecovery.getWaitDebtRecoveryMethodLine());
      debtRecoveryActionService.runManualAction(debtRecovery);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
