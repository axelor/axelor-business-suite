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
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.production.db.ProductionBatch;
import com.axelor.apps.production.db.repo.ProductionBatchRepository;
import com.axelor.apps.production.report.IReport;
import com.axelor.apps.production.service.batch.ProductionBatchService;
import com.axelor.apps.production.translation.ITranslation;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ProductionBatchController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private ProductionBatchRepository productionBatchRepo;

  @Inject private ProductionBatchService productionBatchService;

  public void computeValuation(ActionRequest request, ActionResponse response) {

    ProductionBatch productionBatch = request.getContext().asType(ProductionBatch.class);
    productionBatch = productionBatchRepo.find(productionBatch.getId());
    Batch batch = productionBatchService.computeValuation(productionBatch);
    if (batch != null) {
      response.setFlash(batch.getComments());
    }
    response.setReload(true);
  }

  public void showValuation(ActionRequest request, ActionResponse response) throws AxelorException {
    ProductionBatch productionBatch = request.getContext().asType(ProductionBatch.class);
    productionBatch = productionBatchRepo.find(productionBatch.getId());

    String name = I18n.get(ITranslation.WORK_IN_PROGRESS_VALUATION);

    String fileLink =
        ReportFactory.createReport(
                "productionWorkInProgressValuation",
                productionBatch.getCompany(),
                IReport.WORK_IN_PROGRESS_VALUATION,
                name + "-${date}")
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .addParam(
                "companyId",
                productionBatch.getCompany() != null ? productionBatch.getCompany().getId() : 0)
            .addParam(
                "locationId",
                productionBatch.getWorkshopStockLocation() != null
                    ? productionBatch.getWorkshopStockLocation().getId()
                    : 0)
            .addParam(
                "editionDate",
                DateTimeFormatter.ofPattern("MMM d, yyyy, hh:mm a")
                    .format(productionBatch.getUpdatedOn()))
            .generate()
            .getFileLink();

    LOG.debug("Printing {}", name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }
}
