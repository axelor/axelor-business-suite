/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service.excelreport;

import com.axelor.apps.base.db.Print;
import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.service.PrintTemplateService;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExcelReportDataMapServiceFactory {

  private static ExcelReportDataMapService service = null;
  private PrintTemplateService printTemplateService;

  private ExcelReportDataMapServiceFactory() {
    service = Beans.get(ExcelReportDataMapService.class);
    printTemplateService = Beans.get(PrintTemplateService.class);
  }

  public static ExcelReportDataMapServiceFactory get() {
    return new ExcelReportDataMapServiceFactory();
  }

  public ExcelReportDataMapServiceFactory setPrintTemplate(PrintTemplate printTemplate) {
    service.setPrintTemplate(printTemplate);
    return this;
  }

  public ExcelReportDataMapServiceFactory addObjectIds(List<Long> objectIds)
      throws ClassNotFoundException, AxelorException, IOException {
    PrintTemplate printTemplate = service.getPrintTemplate();
    if (printTemplate == null || ObjectUtils.isEmpty(objectIds)) {
      return this;
    }
    Print print = printTemplateService.getTemplatePrint(objectIds.get(0), printTemplate);
    if (ObjectUtils.notEmpty(printTemplate.getReportQueryBuilderList())) {
      service.setReportQueryBuilderList(new ArrayList<>(printTemplate.getReportQueryBuilderList()));
    }
    service.setPrint(print);
    return this;
  }

  public ExcelReportDataMapService getService() {
    return service;
  }
}
