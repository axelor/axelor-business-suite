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
package com.axelor.apps.account.service.move;

import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.service.AccountCustomerService;
import com.axelor.apps.account.service.AccountingService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MoveCustAccountService {

  protected AccountCustomerService accountCustomerService;

  @Inject
  public MoveCustAccountService(AccountCustomerService accountCustomerService) {

    this.accountCustomerService = accountCustomerService;
  }

  /**
   * Update the partner balances linked to the move
   *
   * @param move
   * @throws AxelorException
   */
  public void updateCustomerAccount(Move move) throws AxelorException {

    this.updateCustomerAccount(this.getPartnerOfMove(move), move.getCompany());
  }

  /**
   * Update the partner balances for the company and partner list
   *
   * @param partnerList
   * @param company
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void updateCustomerAccount(List<Partner> partnerList, Company company)
      throws AxelorException {

    if (AccountingService.getUpdateCustomerAccount()) {
      accountCustomerService.updatePartnerAccountingSituation(
          partnerList, company, true, true, false);
    } else {
      this.flagPartners(partnerList, company);
    }
  }

  /**
   * Get the distinct partners of an account move that impact the partner balances
   *
   * @param move
   * @return A list of partner
   */
  public List<Partner> getPartnerOfMove(Move move) {
    List<Partner> partnerList = new ArrayList<Partner>();
    for (MoveLine moveLine : move.getMoveLineList()) {
      if (moveLine.getAccount() != null
          && moveLine.getAccount().getUseForPartnerBalance()
          && moveLine.getPartner() != null
          && !partnerList.contains(moveLine.getPartner())) {
        partnerList.add(moveLine.getPartner());
      }
    }
    return partnerList;
  }

  public void flagPartners(List<Partner> partnerList, Company company) throws AxelorException {

    accountCustomerService.flagPartners(partnerList, company);
  }
}
