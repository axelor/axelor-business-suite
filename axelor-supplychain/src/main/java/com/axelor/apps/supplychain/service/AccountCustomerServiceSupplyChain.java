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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.repo.AccountingSituationRepository;
import com.axelor.apps.account.service.AccountCustomerService;
import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;

@Singleton
public class AccountCustomerServiceSupplyChain extends AccountCustomerService {

  @Inject
  public AccountCustomerServiceSupplyChain(
      AccountingSituationService accountingSituationService,
      AccountingSituationRepository accSituationRepo,
      AppBaseService appBaseService) {
    super(accountingSituationService, accSituationRepo, appBaseService);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public AccountingSituation updateAccountingSituationCustomerAccount(
      AccountingSituation accountingSituation,
      boolean updateCustAccount,
      boolean updateDueCustAccount,
      boolean updateDueDebtRecoveryCustAccount)
      throws AxelorException {

    accountingSituation =
        super.updateAccountingSituationCustomerAccount(
            accountingSituation,
            updateCustAccount,
            updateDueCustAccount,
            updateDueDebtRecoveryCustAccount);

    if (updateCustAccount) {
      accountingSituationService.updateCustomerCredit(accountingSituation.getPartner());
    }

    return accountingSituation;
  }
}
