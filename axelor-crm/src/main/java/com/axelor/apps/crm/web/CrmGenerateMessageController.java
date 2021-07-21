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
package com.axelor.apps.crm.web;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.crm.db.Lead;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.apps.message.web.GenerateMessageController;
import com.axelor.auth.AuthUtils;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrmGenerateMessageController extends GenerateMessageController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public ActionViewBuilder getActionView(
      long templateNumber, Model context, String model, String simpleModel, Message message) {

    ActionViewBuilder builder =
        super.getActionView(templateNumber, context, model, simpleModel, message);

    if (!model.equals(Lead.class.getName())
        && !model.equals(Partner.class.getName())
        && !model.equals("com.axelor.apps.sale.db.SaleOrder")) {
      return builder;
    }

    builder.param("popup", "reload");
    if (templateNumber > 1) {
      builder.param("popup-save", "false");
      builder.param("show-toolbar", "false");
    } else if (templateNumber != 0) {
      builder.context("_message", message);
    }
    return builder;
  }

  @Override
  public Map<String, Object> generateMessage(
      long objectId, String model, String tag, Template template)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException,
          AxelorException, IOException {

    if (!model.equals(Lead.class.getName())
        && !model.equals(Partner.class.getName())
        && !model.equals("com.axelor.apps.sale.db.SaleOrder")) {
      return super.generateMessage(objectId, model, tag, template);
    }

    LOG.debug("template : {} ", template);
    LOG.debug("object id : {} ", objectId);
    LOG.debug("model : {} ", model);
    LOG.debug("tag : {} ", tag);
    Message message = null;
    if (template != null) {
      message =
          Beans.get(TemplateMessageService.class)
              .generateMessage(objectId, model, tag, template, true);
    } else {
      message =
          Beans.get(MessageService.class)
              .createMessage(
                  model,
                  Math.toIntExact(objectId),
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  MessageRepository.MEDIA_TYPE_EMAIL,
                  null,
                  null,
                  true);
    }
    message.setSenderUser(AuthUtils.getUser());
    message.setCompany(Beans.get(UserService.class).getUserActiveCompany());

    ActionViewBuilder builder = getActionView(1, null, model, null, message);
    return builder.map();
  }
}
