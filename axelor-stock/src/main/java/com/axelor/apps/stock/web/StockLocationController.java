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
package com.axelor.apps.stock.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.report.IReport;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.eclipse.birt.core.exception.BirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StockLocationController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private StockLocationRepository stockLocationRepo;

  private StockLocationService stockLocationService;

  @Inject
  public StockLocationController(
      StockLocationRepository stockLocationRepo, StockLocationService stockLocationService) {
    this.stockLocationRepo = stockLocationRepo;
    this.stockLocationService = stockLocationService;
  }

  /**
   * Method that generate inventory as a pdf
   *
   * @param request
   * @param response
   * @return
   * @throws BirtException
   * @throws IOException
   */
  public void print(ActionRequest request, ActionResponse response) throws AxelorException {

    Context context = request.getContext();
    @SuppressWarnings("unchecked")
    LinkedHashMap<String, Object> stockLocationMap =
        (LinkedHashMap<String, Object>) context.get("_stockLocation");
    Integer stockLocationId = (Integer) stockLocationMap.get("id");

    StockLocation stockLocation =
        stockLocationId != null
            ? Beans.get(StockLocationRepository.class).find(new Long(stockLocationId))
            : null;
    String locationIds = "";

    String exportType = (String) context.get("exportTypeSelect");

    @SuppressWarnings("unchecked")
    List<Integer> lstSelectedLocations = (List<Integer>) context.get("_ids");
    if (lstSelectedLocations != null) {
      for (Integer it : lstSelectedLocations) {
        Set<Long> idSet =
            stockLocationService.getContentStockLocationIds(stockLocationRepo.find(new Long(it)));
        if (!idSet.isEmpty()) {
          locationIds += Joiner.on(",").join(idSet) + ",";
        }
      }
    }

    if (!locationIds.equals("")) {
      locationIds = locationIds.substring(0, locationIds.length() - 1);
      stockLocation = stockLocationRepo.find(new Long(lstSelectedLocations.get(0)));
    } else if (stockLocation != null && stockLocation.getId() != null) {
      Set<Long> idSet =
          stockLocationService.getContentStockLocationIds(
              stockLocationRepo.find(stockLocation.getId()));
      if (!idSet.isEmpty()) {
        locationIds = Joiner.on(",").join(idSet);
      }
    }

    if (!locationIds.equals("")) {
      String language = ReportSettings.getPrintingLocale(null);

      String title = I18n.get("Stock location");
      if (stockLocation.getName() != null) {
        title =
            lstSelectedLocations == null
                ? I18n.get("Stock location") + " " + stockLocation.getName()
                : I18n.get("Stock location(s)");
      }

      String fileLink =
          ReportFactory.createReport(
                  "stockStockLocation",
                  stockLocation.getCompany(),
                  IReport.STOCK_LOCATION,
                  title + "-${date}")
              .addParam("StockLocationId", locationIds)
              .addParam("Locale", language)
              .addFormat(exportType)
              .generate()
              .getFileLink();

      logger.debug("Printing " + title);

      response.setView(ActionView.define(title).add("html", fileLink).map());

    } else {
      response.setFlash(I18n.get(IExceptionMessage.LOCATION_2));
    }
    response.setCanClose(true);
  }

  public void setStocklocationValue(ActionRequest request, ActionResponse response) {

    StockLocation stockLocation = request.getContext().asType(StockLocation.class);

    response.setValue(
        "stockLocationValue",
        Beans.get(StockLocationService.class).getStockLocationValue(stockLocation));
  }

  public void openPrintWizard(ActionRequest request, ActionResponse response) {
    StockLocation stockLocation = request.getContext().asType(StockLocation.class);

    @SuppressWarnings("unchecked")
    List<Integer> lstSelectedLocations = (List<Integer>) request.getContext().get("_ids");

    response.setView(
        ActionView.define(I18n.get(IExceptionMessage.STOCK_LOCATION_PRINT_WIZARD_TITLE))
            .model(Wizard.class.getName())
            .add("form", "stock-location-print-wizard-form")
            .param("popup", "true")
            .param("show-toolbar", "false")
            .param("show-confirm", "false")
            .param("popup-save", "false")
            .context("_ids", lstSelectedLocations)
            .context("_stockLocation", stockLocation)
            .map());
  }
}
