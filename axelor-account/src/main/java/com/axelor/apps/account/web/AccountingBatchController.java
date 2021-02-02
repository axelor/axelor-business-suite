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
package com.axelor.apps.account.web;

import com.axelor.apps.account.db.AccountingBatch;
import com.axelor.apps.account.db.repo.AccountingBatchRepository;
import com.axelor.apps.account.service.batch.AccountingBatchService;
import com.axelor.apps.base.callable.ControllerCallableTool;
import com.axelor.apps.base.db.Batch;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class AccountingBatchController {

  /**
   * Lancer le batch de relance
   *
   * @param request
   * @param response
   */
  public void actionDebtRecovery(ActionRequest request, ActionResponse response) {

    runBatch(AccountingBatchRepository.ACTION_DEBT_RECOVERY, request, response);
  }

  /**
   * Lancer le batch de détermination des créances douteuses
   *
   * @param request
   * @param response
   */
  public void actionDoubtfulCustomer(ActionRequest request, ActionResponse response) {
    runBatch(AccountingBatchRepository.ACTION_DOUBTFUL_CUSTOMER, request, response);
  }

  /**
   * Lancer le batch de remboursement
   *
   * @param request
   * @param response
   */
  public void actionReimbursement(ActionRequest request, ActionResponse response) {
    runBatch(AccountingBatchRepository.ACTION_REIMBURSEMENT, request, response);
  }

  /**
   * Lancer le batch de prélèvement
   *
   * @param request
   * @param response
   */
  public void actionDirectDebit(ActionRequest request, ActionResponse response) {
    runBatch(null, request, response);
  }

  /**
   * Lancer le batch de calcul du compte client
   *
   * @param request
   * @param response
   */
  public void actionAccountingCustomer(ActionRequest request, ActionResponse response) {
    runBatch(AccountingBatchRepository.ACTION_ACCOUNT_CUSTOMER, request, response);
  }

  /**
   * Lancer le batch de calcul du compte client
   *
   * @param request
   * @param response
   */
  public void actionMoveLineExport(ActionRequest request, ActionResponse response) {
    runBatch(AccountingBatchRepository.ACTION_MOVE_LINE_EXPORT, request, response);
  }

  public void actionCreditTransfer(ActionRequest request, ActionResponse response) {
    runBatch(AccountingBatchRepository.ACTION_CREDIT_TRANSFER, request, response);
  }

  public void actionRealizeFixedAssetLines(ActionRequest request, ActionResponse response) {
    runBatch(AccountingBatchRepository.ACTION_REALIZE_FIXED_ASSET_LINES, request, response);
  }

  public void actionCloseAnnualAccounts(ActionRequest request, ActionResponse response) {
    runBatch(
        AccountingBatchRepository.ACTION_CLOSE_ANNUAL_ACCOUNTS_OF_PAST_YEAR, request, response);
  }

  public void runBatch(Integer actionSelect, ActionRequest request, ActionResponse response) {
    try {

      AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);
      if (actionSelect != null && !actionSelect.equals(accountingBatch.getActionSelect())) {
        return;
      }
      AccountingBatchService accountingBatchService = Beans.get(AccountingBatchService.class);
      accountingBatchService.setBatchModel(accountingBatch);

      ControllerCallableTool<Batch> batchControllerCallableTool = new ControllerCallableTool<>();
      Batch batch =
          batchControllerCallableTool.runInSeparateThread(accountingBatchService, response);
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

    Batch batch =
        Beans.get(AccountingBatchService.class).run((String) request.getContext().get("code"));
    Map<String, Object> mapData = new HashMap<String, Object>();
    mapData.put("anomaly", batch.getAnomaly());
    response.setData(mapData);
  }
}
