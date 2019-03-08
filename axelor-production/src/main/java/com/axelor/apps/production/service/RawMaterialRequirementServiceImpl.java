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
package com.axelor.apps.production.service;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.production.db.RawMaterialRequirement;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.apps.production.report.IReport;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;

public class RawMaterialRequirementServiceImpl implements RawMaterialRequirementService {

  /** The title of the report. */
  public static final String RAW_MATERIAL_REPORT_TITLE = "Raw material requirement";

  @Override
  public String print(RawMaterialRequirement rawMaterialRequirement) throws AxelorException {
    String name = String.format("%s - ${date}", I18n.get(RAW_MATERIAL_REPORT_TITLE));
    ReportSettings reportSetting =
        ReportFactory.createReport(
            "productionRawMaterialRequirement",
            rawMaterialRequirement.getCompany(),
            IReport.RAW_MATERIAL_REQUIREMENT,
            name);

    String locale = ReportSettings.getPrintingLocale(null);
    return reportSetting
        .addParam("RawMaterialRequirementId", rawMaterialRequirement.getId())
        .addParam("Locale", locale)
        .generate()
        .getFileLink();
  }

  @Override
  public String getSequence(RawMaterialRequirement rawMaterialRequirement) throws AxelorException {

    String seq =
        Beans.get(SequenceService.class)
            .getSequenceNumber(
                SequenceRepository.RAW_MATERIAL_REQUIREMENT, rawMaterialRequirement.getCompany());
    if (seq == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.RAW_MATERIAL_REQUIREMENT_NO_SEQUENCE),
          rawMaterialRequirement.getCompany().getName());
    }

    return seq;
  }
}
