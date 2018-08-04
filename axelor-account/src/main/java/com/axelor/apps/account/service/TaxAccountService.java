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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.base.db.Company;
import com.google.inject.Singleton;

@Singleton
public class TaxAccountService {

  public Account getAccount(Tax tax, Company company, boolean isPurchase, boolean isFixedAssets) {

    AccountManagement accountManagement = this.getTaxAccount(tax, company);

    if (accountManagement != null) {
      if (isPurchase) {
        if (isFixedAssets) {
          return accountManagement.getPurchFixedAssetsAccount();
        }
        return accountManagement.getPurchaseAccount();
      }
      return accountManagement.getSaleAccount();
    }

    return null;
  }

  protected AccountManagement getTaxAccount(Tax tax, Company company) {

    if (tax != null && tax.getAccountManagementList() != null) {

      for (AccountManagement accountManagement : tax.getAccountManagementList()) {

        if (accountManagement.getCompany().equals(company)) {
          return accountManagement;
        }
      }
    }

    return null;
  }
}
