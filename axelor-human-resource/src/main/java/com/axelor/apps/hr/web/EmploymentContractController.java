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
package com.axelor.apps.hr.web;

import com.axelor.apps.hr.db.EmploymentContract;
import com.axelor.apps.hr.db.repo.EmploymentContractRepository;
import com.axelor.apps.hr.service.EmploymentContractService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class EmploymentContractController {

  public void addAmendment(ActionRequest request, ActionResponse response) {

    EmploymentContract employmentContract = request.getContext().asType(EmploymentContract.class);

    try {

      Beans.get(EmploymentContractService.class)
          .addAmendment(
              Beans.get(EmploymentContractRepository.class).find(employmentContract.getId()));
      response.setFlash(
          String.format(
              "Contrat %s - avenant %s",
              employmentContract.getFullName(), employmentContract.getEmploymentContractVersion()));
      response.setReload(true);

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void exportEmploymentContract(ActionRequest request, ActionResponse response)
      throws IOException {
    EmploymentContract employmentContract =
        Beans.get(EmploymentContractRepository.class)
            .find(request.getContext().asType(EmploymentContract.class).getId());

    MetaFile metaFile =
        Beans.get(EmploymentContractService.class).exportEmploymentContract(employmentContract);

    response.setView(
        ActionView.define(metaFile.getFileName())
            .model(EmploymentContract.class.getName())
            .add(
                "html",
                "ws/rest/com.axelor.meta.db.MetaFile/"
                    + metaFile.getId()
                    + "/content/download?v="
                    + metaFile.getVersion())
            .param("download", "true")
            .map());

    response.setReload(true);
  }
}
