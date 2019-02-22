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
package com.axelor.apps.production.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.production.db.MpsWeeklySchedule;
import com.axelor.apps.production.report.IReport;
import com.axelor.apps.production.service.MpsChargeService;
import com.axelor.apps.production.translation.ITranslation;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MpsChargeController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void getMpsWeeklyScheduleCustom(ActionRequest request, ActionResponse response) {

    LocalDate startMonthDate =
        LocalDate.parse(
            request.getData().get("startMonthDate").toString(), DateTimeFormatter.ISO_DATE);
    LocalDate endMonthDate =
        LocalDate.parse(
            request.getData().get("endMonthDate").toString(), DateTimeFormatter.ISO_DATE);

    MpsChargeService mpsChargeService = Beans.get(MpsChargeService.class);

    Map<MpsWeeklySchedule, Map<YearMonth, BigDecimal>> totalHoursCountMap =
        mpsChargeService.countTotalHours(startMonthDate, endMonthDate);
    List<Map<String, Object>> dataMapList =
        mpsChargeService.getTableDataMapList(totalHoursCountMap);

    response.setData(dataMapList);
  }

  public void getMpsWeeklyScheduleChart(ActionRequest request, ActionResponse response) {

    LocalDate startMonthDate = (LocalDate) request.getContext().getParent().get("startMonthDate");
    LocalDate endMonthDate = (LocalDate) request.getContext().getParent().get("endMonthDate");

    MpsChargeService mpsChargeService = Beans.get(MpsChargeService.class);

    Map<MpsWeeklySchedule, Map<YearMonth, BigDecimal>> totalHoursCountMap =
        mpsChargeService.countTotalHours(startMonthDate, endMonthDate);
    List<Map<String, Object>> dataMapList =
        mpsChargeService.getChartDataMapList(totalHoursCountMap);

    response.setData(dataMapList);
  }

  public void print(ActionRequest request, ActionResponse response) throws AxelorException {

    String name = I18n.get(ITranslation.MPS_CHARGE);
    LocalDate startMonthDate = (LocalDate) request.getContext().get("startMonthDate");
    LocalDate endMonthDate = (LocalDate) request.getContext().get("endMonthDate");
    if (startMonthDate == null || endMonthDate == null) {
      return;
    }

    String fileLink =
        ReportFactory.createReport(IReport.MPS_CHARGE, name + "-${date}")
            .addParam("mpsId", request.getContext().get("id"))
            .addParam(
                "startMonthDate", startMonthDate.format(DateTimeFormatter.ofPattern("dd/MM/YYYY")))
            .addParam(
                "endMonthDate", endMonthDate.format(DateTimeFormatter.ofPattern("dd/MM/YYYY")))
            .generate()
            .getFileLink();

    LOG.debug("Printing {}", name);
    response.setView(ActionView.define(name).add("html", fileLink).map());
  }
}
