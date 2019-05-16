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
package com.axelor.apps.purchase.service.app;

import com.axelor.apps.base.db.AppPurchase;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.repo.AppPurchaseRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.service.app.AppBaseServiceImpl;
import com.axelor.apps.purchase.db.PurchaseConfig;
import com.axelor.apps.purchase.db.repo.PurchaseConfigRepository;
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
public class AppPurchaseServiceImpl extends AppBaseServiceImpl implements AppPurchaseService {

  @Inject private AppPurchaseRepository appPurchaseRepo;

  @Inject private CompanyRepository companyRepo;

  @Inject private PurchaseConfigRepository purchaseConfigRepo;

  @Override
  public AppPurchase getAppPurchase() {
    return appPurchaseRepo.all().fetchOne();
  }

  @Override
  @Transactional
  public void generatePurchaseConfigurations() {

    List<Company> companies = companyRepo.all().filter("self.purchaseConfig is null").fetch();

    for (Company company : companies) {
      PurchaseConfig purchaseConfig = new PurchaseConfig();
      purchaseConfig.setCompany(company);
      purchaseConfigRepo.save(purchaseConfig);
    }
  }

  void onAppPurchasePostSave(
      @Observes @Named(RequestEvent.SAVE) @EntityType(AppPurchase.class) PostRequest event) {
    fireFeatureChanged(event, getAppPurchase());
  }
}
