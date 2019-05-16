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
package com.axelor.apps.account.service.app;

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.repo.AccountConfigRepository;
import com.axelor.apps.base.db.AppAccount;
import com.axelor.apps.base.db.AppBudget;
import com.axelor.apps.base.db.AppInvoice;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.repo.AppAccountRepository;
import com.axelor.apps.base.db.repo.AppBudgetRepository;
import com.axelor.apps.base.db.repo.AppInvoiceRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.service.app.AppBaseServiceImpl;
import com.axelor.event.Observes;
import com.axelor.events.PostRequest;
import com.axelor.events.RequestEvent;
import com.axelor.events.qualifiers.EntityType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.util.List;
import javax.inject.Named;

@Singleton
public class AppAccountServiceImpl extends AppBaseServiceImpl implements AppAccountService {

  @Inject private AppAccountRepository appAccountRepo;

  @Inject private AppBudgetRepository appBudgetRepo;

  @Inject private AppInvoiceRepository appInvoiceRepo;

  @Inject private AccountConfigRepository accountConfigRepo;

  @Inject private CompanyRepository companyRepo;

  @Override
  public AppAccount getAppAccount() {
    return appAccountRepo.all().fetchOne();
  }

  @Override
  public AppBudget getAppBudget() {
    return appBudgetRepo.all().fetchOne();
  }

  @Override
  public AppInvoice getAppInvoice() {
    return appInvoiceRepo.all().fetchOne();
  }

  @Transactional
  @Override
  public void generateAccountConfigurations() {

    List<Company> companies = companyRepo.all().filter("self.accountConfig is null").fetch();

    for (Company company : companies) {
      AccountConfig config = new AccountConfig();
      config.setCompany(company);
      accountConfigRepo.save(config);
    }
  }

  void onAppAccountPostSave(
      @Observes @Named(RequestEvent.SAVE) @EntityType(AppAccount.class) PostRequest event) {
    fireFeatureChanged(event, getAppAccount());
  }

  void onAppBudgetPostSave(
      @Observes @Named(RequestEvent.SAVE) @EntityType(AppBudget.class) PostRequest event) {
    fireFeatureChanged(event, getAppBudget());
  }

  void onAppInvoicePostSave(
      @Observes @Named(RequestEvent.SAVE) @EntityType(AppInvoice.class) PostRequest event) {
    fireFeatureChanged(event, getAppInvoice());
  }
}
