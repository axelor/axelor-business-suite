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

import com.axelor.apps.base.db.PrintTemplateLineTest;
import com.axelor.apps.base.db.repo.PrintTemplateLineTestRepository;
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

public class PrintTemplateLineController {

  public void checkTemplateLineExpression(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    PrintTemplateLineTest printTemplateLineTest = context.asType(PrintTemplateLineTest.class);
    printTemplateLineTest =
        Beans.get(PrintTemplateLineTestRepository.class).find(printTemplateLineTest.getId());
    MetaModel metaModel =
        Beans.get(MetaModelRepository.class)
            .all()
            .filter("self.fullName = ?", printTemplateLineTest.getReference())
            .fetchOne();
    try {
      Beans.get(PrintTemplateLineService.class)
          .checkExpression(
              Long.valueOf(printTemplateLineTest.getReferenceId().toString()),
              metaModel,
              printTemplateLineTest.getPrintTemplateLine());
    } catch (NumberFormatException | ClassNotFoundException | AxelorException | IOException e) {
      TraceBackService.trace(response, e);
    }

    response.setReload(true);
  }
}
