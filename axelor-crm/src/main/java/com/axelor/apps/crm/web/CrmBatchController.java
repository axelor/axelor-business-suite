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
package com.axelor.apps.crm.web;

import com.axelor.apps.base.callable.ControllerCallableTool;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.crm.db.CrmBatch;
import com.axelor.apps.crm.db.repo.CrmBatchRepository;
import com.axelor.apps.crm.service.batch.CrmBatchService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class CrmBatchController {

  /**
   * Called from batch form view, run the batch.
   *
   * @param request
   * @param response
   */
  public void runBatch(ActionRequest request, ActionResponse response) {
    try {
      CrmBatch crmBatch = request.getContext().asType(CrmBatch.class);
      CrmBatchService crmBatchService = Beans.get(CrmBatchService.class);
      crmBatchService.setBatchModel(Beans.get(CrmBatchRepository.class).find(crmBatch.getId()));
      ControllerCallableTool<Batch> controllerCallableTool = new ControllerCallableTool<>();

      Batch batch = controllerCallableTool.runInSeparateThread(crmBatchService, response);

      if (batch != null) {
        response.setFlash(batch.getComments());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  // WS

  /**
   * Lancer le batch à travers un web service.
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void run(ActionRequest request, ActionResponse response) throws AxelorException {

    Batch batch = Beans.get(CrmBatchService.class).run((String) request.getContext().get("code"));
    Map<String, Object> mapData = new HashMap<String, Object>();
    mapData.put("anomaly", batch.getAnomaly());
    response.setData(mapData);
  }
}
