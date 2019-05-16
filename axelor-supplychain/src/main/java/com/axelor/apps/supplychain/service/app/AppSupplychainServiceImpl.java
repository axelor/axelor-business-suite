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
package com.axelor.apps.supplychain.service.app;

import com.axelor.apps.base.db.AppSupplychain;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.repo.AppSupplychainRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.service.app.AppBaseServiceImpl;
import com.axelor.apps.supplychain.db.SupplyChainConfig;
import com.axelor.apps.supplychain.db.repo.SupplyChainConfigRepository;
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
public class AppSupplychainServiceImpl extends AppBaseServiceImpl implements AppSupplychainService {

  @Inject private AppSupplychainRepository appSupplychainRepo;

  @Inject private CompanyRepository companyRepo;

  @Inject private SupplyChainConfigRepository supplyChainConfigRepo;

  @Override
  public AppSupplychain getAppSupplychain() {
    return appSupplychainRepo.all().fetchOne();
  }

  @Override
  @Transactional
  public void generateSupplychainConfigurations() {

    List<Company> companies = companyRepo.all().filter("self.supplyChainConfig is null").fetch();

    for (Company company : companies) {
      SupplyChainConfig supplyChainConfig = new SupplyChainConfig();
      supplyChainConfig.setCompany(company);
      supplyChainConfigRepo.save(supplyChainConfig);
    }
  }

  void onAppSupplychainPostSave(
      @Observes @Named(RequestEvent.SAVE) @EntityType(AppSupplychain.class) PostRequest event) {
    fireFeatureChanged(event, getAppSupplychain());
  }
}
