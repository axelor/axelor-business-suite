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
package com.axelor.apps.base.db.repo;

import com.axelor.apps.base.db.BirtTemplate;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.i18n.I18n;
import javax.persistence.PersistenceException;

public class BirtTemplateBaseRepository extends BirtTemplateRepository {

  @Override
  public BirtTemplate save(BirtTemplate birtTemplate) {
    if (!birtTemplate.getEmbeddedReport() && birtTemplate.getTemplateFile() == null) {
      throw new PersistenceException(I18n.get(IExceptionMessage.MISSING_TEMPLATE_FILE));
    }
    return super.save(birtTemplate);
  }
}
