/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.batch.AccountingBatchService;
import com.axelor.apps.account.service.batch.BatchInterestInvoice;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Company;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
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

    AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);

    Batch batch = null;

    if (accountingBatch.getActionSelect() == AccountingBatchRepository.ACTION_DEBT_RECOVERY) {
      batch =
          Beans.get(AccountingBatchService.class)
              .debtRecovery(
                  Beans.get(AccountingBatchRepository.class).find(accountingBatch.getId()));
    }
    if (batch != null) response.setFlash(batch.getComments());
    response.setReload(true);
  }

  /**
   * Lancer le batch de détermination des créances douteuses
   *
   * @param request
   * @param response
   */
  public void actionDoubtfulCustomer(ActionRequest request, ActionResponse response) {

    AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);

    Batch batch = null;

    batch =
        Beans.get(AccountingBatchService.class)
            .doubtfulCustomer(
                Beans.get(AccountingBatchRepository.class).find(accountingBatch.getId()));

    if (batch != null) response.setFlash(batch.getComments());
    response.setReload(true);
  }

  /**
   * Lancer le batch de remboursement
   *
   * @param request
   * @param response
   */
  public void actionReimbursement(ActionRequest request, ActionResponse response) {

    AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);
    AccountingBatchService accountingBatchService = Beans.get(AccountingBatchService.class);
    AccountingBatchRepository accountingBatchRepository =
        Beans.get(AccountingBatchRepository.class);

    Batch batch = null;

    if (accountingBatch.getReimbursementTypeSelect()
        == AccountingBatchRepository.REIMBURSEMENT_TYPE_EXPORT) {
      batch =
          accountingBatchService.reimbursementExport(
              accountingBatchRepository.find(accountingBatch.getId()));
    } else if (accountingBatch.getReimbursementTypeSelect()
        == AccountingBatchRepository.REIMBURSEMENT_TYPE_IMPORT) {
      batch =
          accountingBatchService.reimbursementImport(
              accountingBatchRepository.find(accountingBatch.getId()));
    }

    if (batch != null) response.setFlash(batch.getComments());
    response.setReload(true);
  }

  /**
   * Lancer le batch de prélèvement
   *
   * @param request
   * @param response
   */
  public void actionDirectDebit(ActionRequest request, ActionResponse response) {
    try {
      AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);
      accountingBatch = Beans.get(AccountingBatchRepository.class).find(accountingBatch.getId());
      Batch batch = Beans.get(AccountingBatchService.class).directDebit(accountingBatch);
      response.setFlash(batch.getComments());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  /**
   * Lancer le batch de calcul du compte client
   *
   * @param request
   * @param response
   */
  public void actionAccountingCustomer(ActionRequest request, ActionResponse response) {

    AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);

    Batch batch = null;

    batch =
        Beans.get(AccountingBatchService.class)
            .accountCustomer(
                Beans.get(AccountingBatchRepository.class).find(accountingBatch.getId()));

    if (batch != null) response.setFlash(batch.getComments());
    response.setReload(true);
  }

  /**
   * Lancer le batch de calcul du compte client
   *
   * @param request
   * @param response
   */
  public void actionMoveLineExport(ActionRequest request, ActionResponse response) {

    AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);

    Batch batch = null;

    batch =
        Beans.get(AccountingBatchService.class)
            .moveLineExport(
                Beans.get(AccountingBatchRepository.class).find(accountingBatch.getId()));

    if (batch != null) response.setFlash(batch.getComments());
    response.setReload(true);
  }

  public void actionCreditTransfer(ActionRequest request, ActionResponse response) {
    AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);
    accountingBatch = Beans.get(AccountingBatchRepository.class).find(accountingBatch.getId());
    Batch batch = Beans.get(AccountingBatchService.class).creditTransfer(accountingBatch);
    response.setFlash(batch.getComments());
    response.setReload(true);
  }

  public void actionRealizeFixedAssetLines(ActionRequest request, ActionResponse response) {

    AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);
    accountingBatch = Beans.get(AccountingBatchRepository.class).find(accountingBatch.getId());
    Batch batch = Beans.get(AccountingBatchService.class).realizeFixedAssetLines(accountingBatch);
    if (batch != null) response.setFlash(batch.getComments());
    response.setReload(true);
  }

  public void actionCloseAnnualAccounts(ActionRequest request, ActionResponse response) {

    AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);
    accountingBatch = Beans.get(AccountingBatchRepository.class).find(accountingBatch.getId());
    Batch batch = Beans.get(AccountingBatchService.class).closeAnnualAccounts(accountingBatch);
    if (batch != null) response.setFlash(batch.getComments());
    response.setReload(true);
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

  @Transactional
  public void getInterestInvoiceBatch(ActionRequest request, ActionResponse response)
      throws Exception {

    AccountingBatch accountingBatch = request.getContext().asType(AccountingBatch.class);
    accountingBatch = Beans.get(AccountingBatchRepository.class).find(accountingBatch.getId());

    Company company = accountingBatch.getInterestInvoiceCompany();

    if (company.getAccountConfig().getInterestProduct() == null) {
      response.setError(I18n.get(IExceptionMessage.NO_INTEREST_PRODUCT));
      return;
    }

    Batch batch = Beans.get(BatchInterestInvoice.class).run(accountingBatch);

    if (batch != null) {
      response.setFlash(batch.getComments());
    }
    response.setReload(true);
  }
}
