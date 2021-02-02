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
package com.axelor.apps.contract.web;

import com.axelor.apps.base.callable.ControllerCallableTool;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.contract.batch.service.BatchContractService;
import com.axelor.apps.contract.db.ContractBatch;
import com.axelor.apps.contract.db.repo.ContractBatchRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class ContractBatchController {

  public void runBatch(ActionRequest request, ActionResponse response) {
    try {
      ContractBatch contractBatch = request.getContext().asType(ContractBatch.class);
      contractBatch = Beans.get(ContractBatchRepository.class).find(contractBatch.getId());
      BatchContractService batchContractService = Beans.get(BatchContractService.class);
      batchContractService.setBatchModel(contractBatch);
      ControllerCallableTool<Batch> controllerCallableTool = new ControllerCallableTool<>();
      Batch batch = controllerCallableTool.runInSeparateThread(batchContractService, response);
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
