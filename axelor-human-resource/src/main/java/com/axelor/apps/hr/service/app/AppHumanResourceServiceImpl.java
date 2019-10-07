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
package com.axelor.apps.hr.service.app;

import com.axelor.apps.base.db.AppEmployee;
import com.axelor.apps.base.db.AppExpense;
import com.axelor.apps.base.db.AppLeave;
import com.axelor.apps.base.db.AppTimesheet;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.repo.AppEmployeeRepository;
import com.axelor.apps.base.db.repo.AppExpenseRepository;
import com.axelor.apps.base.db.repo.AppLeaveRepository;
import com.axelor.apps.base.db.repo.AppTimesheetRepository;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.service.app.AppBaseServiceImpl;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.repo.HRConfigRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class AppHumanResourceServiceImpl extends AppBaseServiceImpl
    implements AppHumanResourceService {

  private AppTimesheetRepository appTimesheetRepo;
  private AppLeaveRepository appLeaveRepo;
  private AppExpenseRepository appExpenseRepo;
  private AppEmployeeRepository appEmployeeRepo;

  @Inject private CompanyRepository companyRepo;

  @Inject private HRConfigRepository hrConfigRepo;

  @Inject
  public AppHumanResourceServiceImpl(
      AppTimesheetRepository appTimesheetRepo,
      AppLeaveRepository appLeaveRepo,
      AppExpenseRepository appExpense,
      AppEmployeeRepository appEmployee) {
    this.appTimesheetRepo = appTimesheetRepo;
    this.appLeaveRepo = appLeaveRepo;
    this.appExpenseRepo = appExpense;
    this.appEmployeeRepo = appEmployee;
  }

  @Override
  public AppTimesheet getAppTimesheet() {
    return appTimesheetRepo.all().fetchOne();
  }

  @Override
  public AppLeave getAppLeave() {
    return appLeaveRepo.all().fetchOne();
  }

  @Override
  public AppExpense getAppExpense() {
    return appExpenseRepo.all().fetchOne();
  }

  @Override
  public AppEmployee getAppEmployee() {
    return appEmployeeRepo.all().fetchOne();
  }

  @Override
  public void getHrmAppSettings(ActionRequest request, ActionResponse response) {

    try {

      Map<String, Object> map = new HashMap<>();

      map.put("hasInvoicingAppEnable", isApp("invoice"));
      map.put("hasLeaveAppEnable", isApp("leave"));
      map.put("hasExpenseAppEnable", isApp("expense"));
      map.put("hasTimesheetAppEnable", isApp("timesheet"));
      map.put("hasProjectAppEnable", isApp("project"));

      response.setData(map);
      response.setTotal(map.size());

    } catch (Exception e) {
      e.printStackTrace();
      response.setException(e);
    }
  }

  @Override
  @Transactional
  public void generateHrConfigurations() {

    List<Company> companies = companyRepo.all().filter("self.hrConfig is null").fetch();

    for (Company company : companies) {
      HRConfig hrConfig = new HRConfig();
      hrConfig.setCompany(company);
      hrConfigRepo.save(hrConfig);
    }
  }
}
