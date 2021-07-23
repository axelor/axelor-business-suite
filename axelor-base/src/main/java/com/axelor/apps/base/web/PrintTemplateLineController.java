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
package com.axelor.apps.base.web;

import com.axelor.apps.base.db.PrintTemplateLine;
import com.axelor.apps.base.db.repo.PrintTemplateLineRepository;
import com.axelor.apps.base.service.PrintTemplateLineService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import java.io.IOException;
import java.util.HashMap;

public class PrintTemplateLineController {

  @SuppressWarnings("unchecked")
  public void checkTemplateLineExpression(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    HashMap<String, Object> printTemplateLineMap =
        (HashMap<String, Object>) context.get("_printTemplateLine");
    PrintTemplateLine printTemplateLine =
        Beans.get(PrintTemplateLineRepository.class)
            .find(Long.valueOf(printTemplateLineMap.get("id").toString()));
    MetaModel metaModel =
        Beans.get(MetaModelRepository.class)
            .all()
            .filter("self.fullName = ?", context.get("reference"))
            .fetchOne();
    try {
      String result =
          Beans.get(PrintTemplateLineService.class)
              .checkExpression(
                  Long.valueOf(context.get("referenceId").toString()),
                  metaModel,
                  printTemplateLine);
      response.setValue("$contentResult", result);
    } catch (NumberFormatException | ClassNotFoundException | AxelorException | IOException e) {
      TraceBackService.trace(response, e);
    }
  }
}
