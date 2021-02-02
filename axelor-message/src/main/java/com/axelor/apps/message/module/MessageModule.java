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
package com.axelor.apps.message.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.message.db.repo.MessageManagementRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.service.MailAccountService;
import com.axelor.apps.message.service.MailAccountServiceImpl;
import com.axelor.apps.message.service.MailMessageService;
import com.axelor.apps.message.service.MailMessageServiceImpl;
import com.axelor.apps.message.service.MailServiceMessageImpl;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.MessageServiceImpl;
import com.axelor.apps.message.service.SendMailQueueService;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.apps.message.service.TemplateMessageServiceImpl;
import com.axelor.mail.service.MailServiceImpl;

public class MessageModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(TemplateMessageService.class).to(TemplateMessageServiceImpl.class);
    bind(MessageService.class).to(MessageServiceImpl.class);
    bind(MessageRepository.class).to(MessageManagementRepository.class);
    bind(MailAccountService.class).to(MailAccountServiceImpl.class);
    bind(MailServiceImpl.class).to(MailServiceMessageImpl.class);
    bind(MailMessageService.class).to(MailMessageServiceImpl.class);
    // needed to use event notification methods
    bind(SendMailQueueService.class);
  }
}
