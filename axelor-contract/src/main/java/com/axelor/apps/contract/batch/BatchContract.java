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
package com.axelor.apps.contract.batch;

import com.axelor.apps.contract.db.Contract;
import com.axelor.apps.contract.db.repo.ContractBatchRepository;
import com.axelor.apps.contract.db.repo.ContractRepository;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.List;

public class BatchContract extends BatchStrategy {

  protected ContractRepository repository;

  @Inject
  public BatchContract(ContractRepository repository) {
    this.repository = repository;
  }

  public static BatchContractFactory getFactory(int action) {
    switch (action) {
      case ContractBatchRepository.INVOICING:
        return Beans.get(BatchContractFactoryInvoicing.class);
      case ContractBatchRepository.TERMINATE:
        return Beans.get(BatchContractFactoryTerminate.class);
      case ContractBatchRepository.NEXT_VERSION_ACTIVATION:
        return Beans.get(BatchContractFactoryNextActivation.class);
      case ContractBatchRepository.CURRENT_VERSION_ACTIVATION:
        return Beans.get(BatchContractFactoryCurrentActivation.class);
      default:
        return null;
    }
  }

  @Override
  protected void process() {
    try {
      int fetchLimit = getFetchLimit();
      BatchContractFactory factory = getFactory(batch.getContractBatch().getActionSelect());
      Preconditions.checkNotNull(
          factory,
          String.format(
              I18n.get("Action %s has no Batch implementation."),
              batch.getContractBatch().getActionSelect()));

      Query<Contract> query = factory.prepare(batch);
      List<Contract> contracts;

      int offset = 0;
      while (!(contracts = query.fetch(fetchLimit, offset)).isEmpty()) {
        findBatch();
        for (Contract contract : contracts) {
          try {
            ++offset;
            factory.process(contract);
            incrementDone(contract);
          } catch (Exception e) {
            TraceBackService.trace(e);
            incrementAnomaly(contract);
          }
        }
        JPA.clear();
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
      LOG.error(e.getMessage());
    }
  }

  protected void incrementDone(Contract contract) {
    contract.addBatchSetItem(batch);
    super.incrementDone();
  }

  protected void incrementAnomaly(Contract contract) {
    findBatch();
    contract = repository.find(contract.getId());
    contract.addBatchSetItem(batch);
    super.incrementAnomaly();
  }

  @Override
  protected void stop() {
    super.stop();
    addComment(
        String.format(
            "%d contract(s) treated and %d anomaly(ies) reported !",
            batch.getDone(), batch.getAnomaly()));
  }
}
