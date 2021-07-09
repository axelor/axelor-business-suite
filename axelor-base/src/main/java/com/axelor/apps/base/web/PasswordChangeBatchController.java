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

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.PasswordChangeBatch;
import com.axelor.apps.base.db.repo.PasswordChangeBatchRepository;
import com.axelor.apps.base.service.batch.PasswordChangeBatchService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class PasswordChangeBatchController {

  public void runBatch(ActionRequest request, ActionResponse response) {

    PasswordChangeBatch passwordChangeBatch =
        request.getContext().asType(PasswordChangeBatch.class);
    passwordChangeBatch =
        Beans.get(PasswordChangeBatchRepository.class).find(passwordChangeBatch.getId());
    Batch batch = Beans.get(PasswordChangeBatchService.class).run(passwordChangeBatch);
    response.setFlash(batch.getComments());
    response.setReload(true);
  }
}
