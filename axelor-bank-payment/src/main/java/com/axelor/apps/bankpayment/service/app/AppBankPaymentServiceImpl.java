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
package com.axelor.apps.bankpayment.service.app;

import com.axelor.apps.bankpayment.db.BankPaymentConfig;
import com.axelor.apps.bankpayment.db.repo.BankPaymentConfigRepository;
import com.axelor.apps.base.db.AppBankPayment;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.repo.AppBankPaymentRepository;
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
public class AppBankPaymentServiceImpl extends AppBaseServiceImpl implements AppBankPaymentService {

  protected AppBankPaymentRepository appBankPaymentRepo;
  protected BankPaymentConfigRepository bankPaymentConfigRepo;
  protected CompanyRepository companyRepo;

  @Inject
  AppBankPaymentServiceImpl(
      AppBankPaymentRepository appBankPaymentRepo,
      BankPaymentConfigRepository bankPaymentConfigRepo,
      CompanyRepository companyRepo) {
    this.appBankPaymentRepo = appBankPaymentRepo;
    this.bankPaymentConfigRepo = bankPaymentConfigRepo;
    this.companyRepo = companyRepo;
  }

  @Override
  public AppBankPayment getAppBankPayment() {
    return appBankPaymentRepo.all().fetchOne();
  }

  @Override
  @Transactional
  public void generateBankPaymentConfigurations() {
    List<Company> companies = companyRepo.all().filter("self.bankPaymentConfig IS NULL").fetch();

    for (Company company : companies) {
      BankPaymentConfig config = new BankPaymentConfig();
      config.setCompany(company);
      bankPaymentConfigRepo.save(config);
    }
  }

  void onAppBankPaymentPostSave(
      @Observes @Named(RequestEvent.SAVE) @EntityType(AppBankPayment.class) PostRequest event) {
    fireFeatureChanged(event, getAppBankPayment());
  }
}
