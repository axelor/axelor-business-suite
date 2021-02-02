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
package com.axelor.apps.base.web;

import com.axelor.apps.base.callable.ControllerCallableTool;
import com.axelor.apps.base.db.BaseBatch;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.repo.BaseBatchRepository;
import com.axelor.apps.base.service.batch.BaseBatchService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class BaseBatchController {

  // WS

  /**
   * Lancer le batch à travers un web service.
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void run(ActionRequest request, ActionResponse response) throws AxelorException {

    Batch batch = Beans.get(BaseBatchService.class).run((String) request.getContext().get("code"));
    Map<String, Object> mapData = new HashMap<String, Object>();
    mapData.put("anomaly", batch.getAnomaly());
    response.setData(mapData);
  }

  public void actionSynchronizeCalendars(ActionRequest request, ActionResponse response) {
    try {
      BaseBatch baseBatch = request.getContext().asType(BaseBatch.class);
      baseBatch = Beans.get(BaseBatchRepository.class).find(baseBatch.getId());
      BaseBatchService baseBatchService = Beans.get(BaseBatchService.class);
      baseBatchService.setBatchModel(baseBatch);
      ControllerCallableTool<Batch> batchControllerCallableTool = new ControllerCallableTool<>();
      Batch batch = batchControllerCallableTool.runInSeparateThread(baseBatchService, response);
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
