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
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.stock.db.Inventory;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.InventoryRepository;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.report.IReport;
import com.axelor.apps.stock.service.InventoryService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.eclipse.birt.core.exception.BirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InventoryController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject InventoryService inventoryService;

  @Inject InventoryRepository inventoryRepo;

  /**
   * Fonction appeler par le bouton imprimer
   *
   * @param request
   * @param response
   * @return
   * @throws BirtException
   * @throws IOException
   */
  public void showInventory(ActionRequest request, ActionResponse response) {
    try {
      Inventory inventory = request.getContext().asType(Inventory.class);

      String name = I18n.get("Inventory") + " " + inventory.getInventorySeq();

      String fileLink =
          ReportFactory.createReport(
                  "stockInventory", inventory.getCompany(), IReport.INVENTORY, name + "-${date}")
              .addParam("InventoryId", inventory.getId())
              .addParam("Locale", ReportSettings.getPrintingLocale(null))
              .addParam(
                  "activateBarCodeGeneration",
                  Beans.get(AppBaseService.class).getAppBase().getActivateBarCodeGeneration())
              .addFormat(inventory.getFormatSelect())
              .generate()
              .getFileLink();

      logger.debug("Printing " + name);

      response.setView(ActionView.define(name).add("html", fileLink).map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void exportInventory(ActionRequest request, ActionResponse response) {
    try {
      Inventory inventory = request.getContext().asType(Inventory.class);
      inventory = inventoryRepo.find(inventory.getId());

      String name = I18n.get("Inventory") + " " + inventory.getInventorySeq();
      MetaFile metaFile = inventoryService.exportInventoryAsCSV(inventory);

      response.setView(
          ActionView.define(name)
              .add(
                  "html",
                  "ws/rest/com.axelor.meta.db.MetaFile/"
                      + metaFile.getId()
                      + "/content/download?v="
                      + metaFile.getVersion())
              .map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void importFile(ActionRequest request, ActionResponse response) {
    try {
      Inventory inventory =
          inventoryRepo.find(request.getContext().asType(Inventory.class).getId());

      Path filePath = inventoryService.importFile(inventory);
      response.setFlash(
          String.format(I18n.get(IExceptionMessage.INVENTORY_8), filePath.toString()));

      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void validateInventory(ActionRequest request, ActionResponse response) {
    try {
      Long id = request.getContext().asType(Inventory.class).getId();
      Inventory inventory = Beans.get(InventoryRepository.class).find(id);
      inventoryService.validateInventory(inventory);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void cancel(ActionRequest request, ActionResponse response) {
    try {
      Inventory inventory = request.getContext().asType(Inventory.class);
      inventory = inventoryRepo.find(inventory.getId());
      inventoryService.cancel(inventory);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void fillInventoryLineList(ActionRequest request, ActionResponse response) {
    try {
      Long inventoryId = (Long) request.getContext().get("id");
      if (inventoryId != null) {
        Inventory inventory = inventoryRepo.find(inventoryId);
        Boolean succeed = inventoryService.fillInventoryLineList(inventory);
        if (succeed == null) {
          response.setFlash(I18n.get(IExceptionMessage.INVENTORY_9));
        } else {
          if (succeed) {
            response.setNotify(I18n.get(IExceptionMessage.INVENTORY_10));
          } else {
            response.setNotify(I18n.get(IExceptionMessage.INVENTORY_11));
          }
        }
      }
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void setInventorySequence(ActionRequest request, ActionResponse response) {
    try {

      Inventory inventory = request.getContext().asType(Inventory.class);
      SequenceService sequenceService = Beans.get(SequenceService.class);

      if (sequenceService.isEmptyOrDraftSequenceNumber(inventory.getInventorySeq())) {

        StockLocation stockLocation = inventory.getStockLocation();

        response.setValue(
            "inventorySeq", inventoryService.getInventorySequence(stockLocation.getCompany()));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void showStockMove(ActionRequest request, ActionResponse response) {
    try {
      Inventory inventory = request.getContext().asType(Inventory.class);
      StockMove stockMove = inventoryService.findStockMove(inventory);
      if (inventory != null) {
        ActionViewBuilder builder =
            ActionView.define(I18n.get("Stock Move"))
                .model(StockMove.class.getName())
                .add("grid", "stock-move-grid")
                .add("form", "stock-move-form")
                .context("_showRecord", stockMove.getId());
        response.setView(builder.map());
      } else {
        response.setFlash(I18n.get("No record found"));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
