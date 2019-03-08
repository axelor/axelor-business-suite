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
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.ProdProcess;
import com.axelor.apps.production.db.repo.BillOfMaterialRepository;
import com.axelor.apps.production.db.repo.ProdProcessRepository;
import com.axelor.apps.production.report.IReport;
import com.axelor.apps.production.service.ProdProcessService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class ProdProcessController {

  @Inject protected ProdProcessService prodProcessService;

  @Inject ProdProcessRepository prodProcessRepo;

  public void validateProdProcess(ActionRequest request, ActionResponse response) {
    ProdProcess prodProcess = request.getContext().asType(ProdProcess.class);
    if (prodProcess.getIsConsProOnOperation()) {
      BillOfMaterial bom = null;
      if (request.getContext().getParent() != null
          && request
              .getContext()
              .getParent()
              .getContextClass()
              .getName()
              .equals(BillOfMaterial.class.getName())) {
        bom = request.getContext().getParent().asType(BillOfMaterial.class);
      } else {
        bom =
            Beans.get(BillOfMaterialRepository.class)
                .all()
                .filter("self.prodProcess.id = ?1", prodProcess.getId())
                .fetchOne();
      }
      if (bom != null) {
        try {
          prodProcessService.validateProdProcess(prodProcess, bom);
        } catch (AxelorException e) {
          TraceBackService.trace(response, e, ResponseMessageType.ERROR);
        }
      }
    }
  }

  public void changeProdProcessListOutsourcing(ActionRequest request, ActionResponse response)
      throws AxelorException {
    ProdProcess prodProcess = request.getContext().asType(ProdProcess.class);
    if (prodProcess.getProdProcessLineList() != null) {
      prodProcessService.changeProdProcessListOutsourcing(prodProcess);
    }
    response.setValue("prodProcessLineList", prodProcess.getProdProcessLineList());
  }

  public void print(ActionRequest request, ActionResponse response) throws AxelorException {

    ProdProcess prodProcess = request.getContext().asType(ProdProcess.class);
    String prodProcessId = prodProcess.getId().toString();
    String prodProcessLabel = prodProcess.getName().toString();

    String fileLink =
        ReportFactory.createReport(
                "productionProdProcess",
                prodProcess.getCompany(),
                IReport.PROD_PROCESS,
                prodProcessLabel + "-${date}")
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .addParam("ProdProcessId", prodProcessId)
            .generate()
            .getFileLink();

    response.setView(ActionView.define(prodProcessLabel).add("html", fileLink).map());
  }

  public void checkOriginalProductionProcess(ActionRequest request, ActionResponse response) {

    ProdProcess prodProcess =
        prodProcessRepo.find(request.getContext().asType(ProdProcess.class).getId());

    List<ProdProcess> prodProcessSet = Lists.newArrayList();
    prodProcessSet =
        prodProcessRepo
            .all()
            .filter("self.originalProdProcess = :origin")
            .bind("origin", prodProcess)
            .fetch();
    String message;

    if (!prodProcessSet.isEmpty()) {

      String existingVersions = "";
      for (ProdProcess prodProcessVersion : prodProcessSet) {
        existingVersions += "<li>" + prodProcessVersion.getFullName() + "</li>";
      }
      message =
          String.format(
              I18n.get(
                  "This production process already has the following versions : <br/><ul> %s </ul>And these versions may also have ones. Do you still wish to create a new one ?"),
              existingVersions);
    } else {
      message = I18n.get("Do you really wish to create a new version of this production process ?");
    }

    response.setAlert(message);
  }

  public void generateNewVersion(ActionRequest request, ActionResponse response) {

    ProdProcess prodProcess =
        prodProcessRepo.find(request.getContext().asType(ProdProcess.class).getId());

    ProdProcess copy = prodProcessService.generateNewVersion(prodProcess);

    response.setView(
        ActionView.define("Production process")
            .model(ProdProcess.class.getName())
            .add("form", "prod-process-form")
            .add("grid", "prod-process-grid")
            .context("_showRecord", String.valueOf(copy.getId()))
            .map());
  }
}
