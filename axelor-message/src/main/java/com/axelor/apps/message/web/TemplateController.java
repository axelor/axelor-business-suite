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
package com.axelor.apps.message.web;

import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.db.repo.TemplateRepository;
import com.axelor.apps.message.service.TemplateService;
import com.axelor.apps.message.translation.ITranslation;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;

public class TemplateController {

  public void generateDraftMessage(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    Template template = context.asType(Template.class);
    template = Beans.get(TemplateRepository.class).find(template.getId());
    MetaModel metaModel =
        Beans.get(MetaModelRepository.class)
            .all()
            .filter("self.fullName = ?", context.get("reference").toString())
            .fetchOne();

    try {
      Message message =
          Beans.get(TemplateService.class)
              .generateDraftMessage(template, metaModel, context.get("referenceId").toString());
      response.setView(
          ActionView.define(I18n.get(ITranslation.MESSAGE_TEST_TEMPLATE))
              .model(Message.class.getName())
              .add("form", "message-form")
              .add("grid", "message-grid")
              .param("forceTitle", "true")
              .context("_message", message)
              .map());
    } catch (NumberFormatException | ClassNotFoundException | AxelorException e) {
      TraceBackService.trace(response, e);
    }
  }
}
