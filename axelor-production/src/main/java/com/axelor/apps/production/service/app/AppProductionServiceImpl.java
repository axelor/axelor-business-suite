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
package com.axelor.apps.production.service.app;

import com.axelor.apps.base.db.AppProduction;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.service.app.AppBaseServiceImpl;
import com.axelor.apps.production.db.ProductionConfig;
import com.axelor.apps.production.db.repo.ProductionConfigRepository;
import com.axelor.db.Query;
import com.axelor.event.Observes;
import com.axelor.events.PostRequest;
import com.axelor.events.RequestEvent;
import com.axelor.events.qualifiers.EntityType;
import com.axelor.inject.Beans;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.util.List;
import javax.inject.Named;

@Singleton
public class AppProductionServiceImpl extends AppBaseServiceImpl implements AppProductionService {

  public static final int DEFAULT_NB_DECIMAL_DIGITS = 2;

  @Override
  public AppProduction getAppProduction() {
    return Query.of(AppProduction.class).cacheable().fetchOne();
  }

  @Override
  @Transactional
  public void generateProductionConfigurations() {

    List<Company> companies =
        Query.of(Company.class).filter("self.productionConfig is null").fetch();

    for (Company company : companies) {
      ProductionConfig productionConfig = new ProductionConfig();
      productionConfig.setCompany(company);
      Beans.get(ProductionConfigRepository.class).save(productionConfig);
    }
  }

  @Override
  public int getNbDecimalDigitForBomQty() {

    AppProduction appProduction = getAppProduction();

    if (appProduction != null) {
      return appProduction.getNbDecimalDigitForBomQty();
    }

    return DEFAULT_NB_DECIMAL_DIGITS;
  }

  void onAppProductionPostSave(
      @Observes @Named(RequestEvent.SAVE) @EntityType(AppProduction.class) PostRequest event) {
    fireFeatureChanged(event, getAppProduction());
  }
}
