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
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.exception.AxelorException;
import java.util.List;

public interface AccountingSituationService {

  boolean checkAccountingSituationList(
      List<AccountingSituation> accountingSituationList, Company company);

  /**
   * Creates unexisting accounting situations for a given partner. Created situations will be
   * appended to the partner's AccountingSituationList
   *
   * @param partner Partner to create accounting situation for.
   * @return The created accounting situations (which is the same as calling
   *     partner.getAccountingSituationList())
   * @throws AxelorException In case of configuration issue
   */
  List<AccountingSituation> createAccountingSituation(Partner partner) throws AxelorException;

  AccountingSituation getAccountingSituation(Partner partner, Company company);

  AccountingSituation createAccountingSituation(Partner partner, Company company)
      throws AxelorException;

  /**
   * Automatically creates supplier/customer/employee accounts based on situation's company
   * configuration.
   *
   * @param situation Situation on which accounts should be created.
   */
  void createPartnerAccounts(AccountingSituation situation) throws AxelorException;

  String createDomainForBankDetails(
      AccountingSituation accountingSituation, boolean isInBankDetails);

  void updateCustomerCredit(Partner partner) throws AxelorException;

  /**
   * Get customer account from accounting situation or account config.
   *
   * @param partner
   * @param company
   * @return
   */
  Account getCustomerAccount(Partner partner, Company company) throws AxelorException;

  /**
   * Get supplier account from accounting situation or account config.
   *
   * @param partner
   * @param company
   * @return
   */
  Account getSupplierAccount(Partner partner, Company company) throws AxelorException;

  /**
   * Get employee account from accounting situation or account config.
   *
   * @param partner
   * @param company
   * @return
   */
  Account getEmployeeAccount(Partner partner, Company company) throws AxelorException;

  /**
   * Return bank details for sales to <code>partner</code> (took from SaleOrder.xml).
   *
   * @param company
   * @param partner
   * @return
   */
  BankDetails getCompanySalesBankDetails(Company company, Partner partner);

  Account getEmployeeCbAccount(Partner partner, Company company) throws AxelorException;
}
