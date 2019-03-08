/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.report.IReport;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.eclipse.birt.core.exception.BirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MessageController extends com.axelor.apps.message.web.MessageController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private MessageRepository messageRepo;

  @Inject private MessageService messageService;

  /**
   * Method that generate message as a pdf
   *
   * @param request
   * @param response
   * @return
   * @throws BirtException
   * @throws IOException
   */
  public void printMessage(ActionRequest request, ActionResponse response) throws AxelorException {

    Message message = request.getContext().asType(Message.class);
    String pdfPath = messageService.printMessage(message);

    if (pdfPath != null) {

      response.setView(
          ActionView.define("Message " + message.getSubject()).add("html", pdfPath).map());

    } else response.setFlash(I18n.get(IExceptionMessage.MESSAGE_1));
  }

  public void print(ActionRequest request, ActionResponse response) throws AxelorException {

    Message message = request.getContext().asType(Message.class);
    String messageIds = "";

    @SuppressWarnings("unchecked")
    List<Integer> lstSelectedMessages = (List<Integer>) request.getContext().get("_ids");
    if (lstSelectedMessages != null) {
      for (Integer it : lstSelectedMessages) {
        messageIds += it.toString() + ",";
      }
    }

    if (!messageIds.equals("")) {
      messageIds = messageIds.substring(0, messageIds.length() - 1);
      message = messageRepo.find(new Long(lstSelectedMessages.get(0)));
    } else if (message.getId() != null) {
      messageIds = message.getId().toString();
    }

    if (!messageIds.equals("")) {
      String language = ReportSettings.getPrintingLocale(null);

      String title = " ";
      if (message.getSubject() != null) {
        title += lstSelectedMessages == null ? "Message " + message.getSubject() : "Messages";
      }

      String fileLink =
          ReportFactory.createReport(
                  "messagePdf", message.getCompany(), IReport.MESSAGE_PDF, title + "-${date}")
              .addParam("Locale", language)
              .addParam("MessageId", messageIds)
              .addFormat(ReportSettings.FORMAT_XLS)
              .generate()
              .getFileLink();

      logger.debug("Printing " + title);

      response.setView(ActionView.define(title).add("html", fileLink).map());

    } else {
      response.setFlash(I18n.get(IExceptionMessage.MESSAGE_2));
    }
  }
}
