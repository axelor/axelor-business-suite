/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service.administration;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;

public abstract class AbstractBatchService {

  /**
   * Get batch model class.
   *
   * @return
   */
  protected abstract Class<? extends Model> getModelClass();

  /**
   * Run a batch with the given batch model.
   *
   * @param model
   * @return
   * @throws AxelorException
   */
  public abstract Batch run(Model model) throws AxelorException;

  /**
   * Run a batch from its code.
   *
   * @param code
   * @return
   * @throws AxelorException
   */
  public Batch run(String code) throws AxelorException {
    Model model = findModelByCode(code);

    if (model == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BASE_BATCH_2),
          code);
    }

    return run(model);
  }

  /**
   * Find batch model by its code.
   *
   * @param code
   * @return
   * @throws AxelorException
   */
  public Model findModelByCode(String code) throws AxelorException {
    if (code == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_NO_VALUE, I18n.get("Code cannot be null."));
    }
    return Query.of(getModelClass()).filter("self.code = :code").bind("code", code).fetchOne();
  }
}
