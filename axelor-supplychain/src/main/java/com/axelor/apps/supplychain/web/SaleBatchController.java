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
package com.axelor.apps.supplychain.web;

import com.axelor.apps.base.callable.ControllerCallableTool;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.sale.db.SaleBatch;
import com.axelor.apps.sale.db.repo.SaleBatchRepository;
import com.axelor.apps.supplychain.service.batch.SaleBatchService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class SaleBatchController {

  /**
   * Lancer le batch à travers un web service.
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void run(ActionRequest request, ActionResponse response) throws AxelorException {

    Batch batch = Beans.get(SaleBatchService.class).run((String) request.getContext().get("code"));
    Map<String, Object> mapData = new HashMap<String, Object>();
    mapData.put("anomaly", batch.getAnomaly());
    response.setData(mapData);
  }

  public void runBatch(ActionRequest request, ActionResponse response) {
    try {
      SaleBatch saleBatch = request.getContext().asType(SaleBatch.class);
      saleBatch = Beans.get(SaleBatchRepository.class).find(saleBatch.getId());
      SaleBatchService saleBatchService = Beans.get(SaleBatchService.class);
      saleBatchService.setBatchModel(saleBatch);
      ControllerCallableTool<Batch> controllerCallableTool = new ControllerCallableTool<>();
      Batch batch = controllerCallableTool.runInSeparateThread(saleBatchService, response);
      if (batch != null) {
        response.setFlash(batch.getComments());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }
}
