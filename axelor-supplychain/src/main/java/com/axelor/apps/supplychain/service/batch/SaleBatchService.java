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
package com.axelor.apps.supplychain.service.batch;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.administration.AbstractBatchService;
import com.axelor.apps.sale.db.SaleBatch;
import com.axelor.apps.sale.db.repo.SaleBatchRepository;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

public class SaleBatchService extends AbstractBatchService {

  @Inject private SaleBatchRepository saleBatchRepo;

  @Override
  protected Class<? extends Model> getModelClass() {
    return SaleBatch.class;
  }

  @Override
  public Batch run(Model batchModel) throws AxelorException {
    SaleBatch saleBatch = (SaleBatch) batchModel;
    switch (saleBatch.getActionSelect()) {
      case SaleBatchRepository.ACTION_INVOICING:
        return generateSubscriberInvoices(saleBatch);
      default:
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BASE_BATCH_1),
            saleBatch.getActionSelect(),
            saleBatch.getCode());
    }
  }

  protected Batch generateSubscriberInvoices(SaleBatch saleBatch) {
    return Beans.get(BatchInvoicing.class).run(saleBatch);
  }

  /**
   * Lancer un batch à partir de son code.
   *
   * @param batchCode Le code du batch souhaité.
   * @throws AxelorException
   */
  public Batch run(String batchCode) throws AxelorException {

    SaleBatch saleBatch = saleBatchRepo.findByCode(batchCode);

    if (saleBatch != null) {
      return run(saleBatch);
    } else {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BASE_BATCH_1),
          batchCode);
    }
  }
}
