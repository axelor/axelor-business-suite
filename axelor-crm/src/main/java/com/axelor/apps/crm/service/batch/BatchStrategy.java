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
package com.axelor.apps.crm.service.batch;

import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.crm.db.EventReminder;
import com.axelor.apps.crm.db.TargetConfiguration;
import com.axelor.apps.crm.db.repo.EventReminderRepository;
import com.axelor.apps.crm.message.MessageServiceCrmImpl;
import com.axelor.apps.crm.service.TargetService;
import com.axelor.apps.message.service.MailAccountService;
import com.google.inject.Inject;

public abstract class BatchStrategy extends AbstractBatch {

  protected MessageServiceCrmImpl messageServiceCrmImpl;
  protected MailAccountService mailAccountService;
  protected TargetService targetService;

  @Inject protected EventReminderRepository eventReminderRepo;

  protected BatchStrategy(
      MessageServiceCrmImpl messageServiceCrmImpl, MailAccountService mailAccountService) {
    super();
    this.messageServiceCrmImpl = messageServiceCrmImpl;
    this.mailAccountService = mailAccountService;
  }

  protected BatchStrategy(TargetService targetService) {
    super();
    this.targetService = targetService;
  }

  protected void updateEventReminder(EventReminder eventReminder) {

    eventReminder.addBatchSetItem(batchRepo.find(batch.getId()));
    eventReminder.setIsReminded(true);
    incrementDone();
    //		eventReminderService.save(eventReminder);
  }

  //	protected void updateEvent( Event event ){
  //
  //		event.addBatchSetItem( batchRepo.find( batch.getId() ) );
  //
  //		incrementDone();
  //	}

  protected void updateTargetConfiguration(TargetConfiguration targetConfiguration) {

    targetConfiguration.addBatchSetItem(batchRepo.find(batch.getId()));

    incrementDone();
  }

  @Override
  public int getFetchLimit() {
    return batch.getCrmBatch().getBatchFetchLimit() > 0
        ? batch.getCrmBatch().getBatchFetchLimit()
        : super.getFetchLimit();
  }
}
