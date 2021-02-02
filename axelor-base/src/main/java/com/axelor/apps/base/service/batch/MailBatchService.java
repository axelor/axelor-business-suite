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
package com.axelor.apps.base.service.batch;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.MailBatch;
import com.axelor.apps.base.db.repo.MailBatchRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.administration.AbstractBatchService;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;

public class MailBatchService extends AbstractBatchService {

  @Inject protected BatchReminderMail batchReminderMail;

  @Override
  protected Class<? extends Model> getModelClass() {
    return MailBatch.class;
  }

  @Override
  public Batch run(Model batchModel) throws AxelorException {
    MailBatch mailBatch = (MailBatch) batchModel;

    switch (mailBatch.getActionSelect()) {
      case MailBatchRepository.ACTION_TIMESHEET_VALIDATION_REMINDER:
        return remindMail(mailBatch);
      default:
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BASE_BATCH_1),
            mailBatch.getActionSelect(),
            mailBatch.getCode());
    }
  }

  public Batch remindMail(MailBatch mailBatch) throws AxelorException {
    return this.run(mailBatch.getCode());
  }
}
