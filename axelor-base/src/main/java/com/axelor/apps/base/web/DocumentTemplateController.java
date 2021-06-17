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

import com.axelor.apps.base.db.DocumentTemplate;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.repo.MetaSelectItemRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Strings;
import com.google.inject.Singleton;

@Singleton
public class DocumentTemplateController {

  public void getDefaultTitle(ActionRequest request, ActionResponse response) {
    DocumentTemplate template = request.getContext().asType(DocumentTemplate.class);
    if (!Strings.isNullOrEmpty(template.getType())) {
      response.setValue(
          "title",
          I18n.get(
              Beans.get(MetaSelectItemRepository.class)
                  .all()
                  .filter(
                      "self.select.name = ? and self.value = ?",
                      "base.document.template.type.select",
                      template.getType())
                  .fetchOne()
                  .getTitle()));
    }
  }
}
