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
import com.axelor.apps.base.service.PeriodService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.hr.db.LunchVoucherMgt;
import com.axelor.apps.hr.db.LunchVoucherMgtLine;
import com.axelor.apps.hr.db.repo.LunchVoucherMgtLineRepository;
import com.axelor.apps.hr.db.repo.LunchVoucherMgtRepository;
import com.axelor.apps.hr.report.IReport;
import com.axelor.apps.hr.service.lunch.voucher.LunchVoucherMgtService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Singleton
public class LunchVoucherMgtController {

  @Inject private Provider<LunchVoucherMgtService> lunchVoucherMgtProvider;

  public void calculate(ActionRequest request, ActionResponse response) {

    try {
      LunchVoucherMgt lunchVoucherMgt =
          Beans.get(LunchVoucherMgtRepository.class)
              .find(request.getContext().asType(LunchVoucherMgt.class).getId());
      lunchVoucherMgtProvider.get().calculate(lunchVoucherMgt);

      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void validate(ActionRequest request, ActionResponse response) {
    LunchVoucherMgt lunchVoucherMgt =
        Beans.get(LunchVoucherMgtRepository.class)
            .find(request.getContext().asType(LunchVoucherMgt.class).getId());
    try {
      lunchVoucherMgtProvider.get().validate(lunchVoucherMgt);

      response.setReload(true);

    } catch (Exception e) {
      TraceBackService.trace(response, e);
      return;
    }

    try {
      Beans.get(PeriodService.class).checkPeriod(lunchVoucherMgt.getPayPeriod());
      Beans.get(PeriodService.class).checkPeriod(lunchVoucherMgt.getLeavePeriod());
    } catch (Exception e) {
      response.setFlash(e.getMessage());
    }
  }

  public void updateTotal(ActionRequest request, ActionResponse response) {
    LunchVoucherMgtService lunchVoucherMgtService = lunchVoucherMgtProvider.get();
    LunchVoucherMgt lunchVoucherMgt = request.getContext().asType(LunchVoucherMgt.class);
    lunchVoucherMgtService.calculateTotal(lunchVoucherMgt);

    response.setValue("totalLunchVouchers", lunchVoucherMgt.getTotalLunchVouchers());
    response.setValue("requestedLunchVouchers", lunchVoucherMgt.getRequestedLunchVouchers());
    response.setValue("givenLunchVouchers", lunchVoucherMgt.getGivenLunchVouchers());
  }

  public void export(ActionRequest request, ActionResponse response) throws IOException {
    LunchVoucherMgt lunchVoucherMgt =
        Beans.get(LunchVoucherMgtRepository.class)
            .find(request.getContext().asType(LunchVoucherMgt.class).getId());

    try {
      lunchVoucherMgtProvider.get().export(lunchVoucherMgt);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void print(ActionRequest request, ActionResponse response) throws IOException {
    LunchVoucherMgt lunchVoucherMgt = request.getContext().asType(LunchVoucherMgt.class);

    String name =
        lunchVoucherMgt.getCompany().getName()
            + " - "
            + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

    try {
      String fileLink =
          ReportFactory.createReport(
                  "hrLunchVoucherManagementMonthly",
                  lunchVoucherMgt.getCompany(),
                  IReport.LUNCH_VOUCHER_MGT_MONTHLY,
                  name)
              .addParam("lunchVoucherMgtId", lunchVoucherMgt.getId())
              .addParam("Locale", Beans.get(UserService.class).getLanguage())
              .addFormat(ReportSettings.FORMAT_PDF)
              .generate()
              .getFileLink();

      response.setView(ActionView.define(name).add("html", fileLink).map());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void updateStock(ActionRequest request, ActionResponse response) {
    try {
      LunchVoucherMgtService lunchVoucherMgtService = lunchVoucherMgtProvider.get();
      LunchVoucherMgt lunchVoucherMgt = request.getContext().asType(LunchVoucherMgt.class);
      if (lunchVoucherMgt.getId() == null) {
        return;
      }
      List<LunchVoucherMgtLine> oldLunchVoucherLines =
          Beans.get(LunchVoucherMgtLineRepository.class)
              .all()
              .filter("self.lunchVoucherMgt.id = ?", lunchVoucherMgt.getId())
              .fetch();
      int stockQuantityStatus =
          lunchVoucherMgtService.updateStock(
              lunchVoucherMgt.getLunchVoucherMgtLineList(),
              oldLunchVoucherLines,
              lunchVoucherMgt.getCompany());
      response.setValue("stockQuantityStatus", stockQuantityStatus);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void checkPayPeriod(ActionRequest request, ActionResponse response) {
    LunchVoucherMgt lunchVoucherMgt = request.getContext().asType(LunchVoucherMgt.class);
    try {
      Beans.get(PeriodService.class).checkPeriod(lunchVoucherMgt.getPayPeriod());
    } catch (Exception e) {
      response.setFlash(e.getMessage());
    }
  }

  public void checkLeavePeriod(ActionRequest request, ActionResponse response) {
    LunchVoucherMgt lunchVoucherMgt = request.getContext().asType(LunchVoucherMgt.class);
    try {
      Beans.get(PeriodService.class).checkPeriod(lunchVoucherMgt.getLeavePeriod());
    } catch (Exception e) {
      response.setFlash(e.getMessage());
    }
  }
}
