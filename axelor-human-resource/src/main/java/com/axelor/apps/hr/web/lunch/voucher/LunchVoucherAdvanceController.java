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
package com.axelor.apps.hr.web.lunch.voucher;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.LunchVoucherAdvance;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.report.IReport;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.hr.service.lunch.voucher.LunchVoucherAdvanceService;
import com.axelor.apps.hr.service.lunch.voucher.LunchVoucherMgtService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.db.EntityHelper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Singleton
public class LunchVoucherAdvanceController {

  @Inject private Provider<LunchVoucherAdvanceService> lunchVoucherAdvanceServiceProvider;

  @Inject private Provider<LunchVoucherMgtService> lunchVoucherMgtProvider;

  @Inject private Provider<HRConfigService> hrConfigService;

  public void checkOnNewAdvance(ActionRequest request, ActionResponse response)
      throws AxelorException {
    LunchVoucherAdvance lunchVoucherAdvance =
        EntityHelper.getEntity(request.getContext().asType(LunchVoucherAdvance.class));

    if (lunchVoucherAdvance.getEmployee().getMainEmploymentContract() == null) {
      response.setError(
          String.format(
              I18n.get(IExceptionMessage.EMPLOYEE_CONTRACT_OF_EMPLOYMENT),
              lunchVoucherAdvance.getEmployee().getName()));
      return;
    }
    Company company = lunchVoucherAdvance.getEmployee().getMainEmploymentContract().getPayCompany();
    HRConfig hrConfig = hrConfigService.get().getHRConfig(company);
    int stock =
        lunchVoucherMgtProvider
            .get()
            .checkStock(company, lunchVoucherAdvance.getNbrLunchVouchers());

    if (stock <= 0) {
      response.setAlert(
          String.format(
              I18n.get(IExceptionMessage.LUNCH_VOUCHER_MIN_STOCK),
              company.getName(),
              hrConfig.getMinStockLunchVoucher(),
              hrConfig.getAvailableStockLunchVoucher(),
              TraceBackRepository.CATEGORY_INCONSISTENCY));
    }
  }

  public void onNewAdvance(ActionRequest request, ActionResponse response) {
    LunchVoucherAdvance lunchVoucherAdvance =
        EntityHelper.getEntity(request.getContext().asType(LunchVoucherAdvance.class));

    try {
      lunchVoucherAdvanceServiceProvider.get().onNewAdvance(lunchVoucherAdvance);
      response.setCanClose(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void print(ActionRequest request, ActionResponse response) {
    LunchVoucherAdvance lunchVoucherAdvance =
        request.getContext().asType(LunchVoucherAdvance.class);
    String name =
        lunchVoucherAdvance.getEmployee().getName()
            + "-"
            + LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    try {
      String fileLink =
          ReportFactory.createReport(
                  "hrLunchVoucherAdvance",
                  AuthUtils.getUser().getActiveCompany(),
                  IReport.LUNCH_VOUCHER_ADVANCE,
                  name)
              .addParam("lunchVoucherAdvId", lunchVoucherAdvance.getId())
              .addFormat(ReportSettings.FORMAT_PDF)
              .generate()
              .getFileLink();
      response.setView(ActionView.define(name).add("html", fileLink).map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
