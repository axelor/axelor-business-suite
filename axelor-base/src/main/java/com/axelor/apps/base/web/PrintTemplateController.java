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

import com.axelor.apps.base.db.Print;
import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.base.db.repo.PrintTemplateRepository;
import com.axelor.apps.base.service.PrintService;
import com.axelor.apps.base.service.PrintTemplateService;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintTemplateController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void openPrint(ActionRequest request, ActionResponse response) {
    Model context = request.getContext().asType(Model.class);
    String model = request.getModel();

    LOG.debug("Print template wizard call for model : {}", model);

    Query<PrintTemplate> printTemplateQuery =
        Beans.get(PrintTemplateRepository.class).all().filter("self.metaModel.fullName = ?", model);

    try {
      long templatesCount = printTemplateQuery.count();

      LOG.debug("Print templates count : {} ", templatesCount);

      if (templatesCount == 0) {
        response.setError(I18n.get("Please define a print template for the model :" + model));
      } else if (templatesCount == 1) {
        Print print =
            Beans.get(PrintTemplateService.class)
                .generatePrint(context.getId(), printTemplateQuery.fetchOne());

        response.setView(getPrintView(print));

      } else if (templatesCount >= 2) {
        response.setView(
            ActionView.define(I18n.get("Select template"))
                .model(Wizard.class.getName())
                .add("form", "select-print-template-wizard-form")
                .param("show-confirm", "false")
                .context("_objectId", context.getId().toString())
                .context("_templateContextModel", model)
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void generatePrint(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();

    Map templateContext = (Map) context.get("template");
    PrintTemplate printTemplate = null;
    if (templateContext != null) {
      printTemplate =
          Beans.get(PrintTemplateRepository.class)
              .find(Long.parseLong(templateContext.get("id").toString()));
    }

    Long objectId = Long.parseLong(context.get("_objectId").toString());

    try {
      response.setCanClose(true);
      Print print = Beans.get(PrintTemplateService.class).generatePrint(objectId, printTemplate);
      response.setView(getPrintView(print));

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  private Map<String, Object> getPrintView(Print print) throws AxelorException {
    if (print.getIsEditable()) {
      return ActionView.define("Create print")
          .model(Print.class.getName())
          .add("form", "print-form")
          .param("forceEdit", "true")
          .context("_showRecord", print.getId().toString())
          .map();
    } else {
      return Beans.get(PrintService.class).generatePDF(print);
    }
  }
}
