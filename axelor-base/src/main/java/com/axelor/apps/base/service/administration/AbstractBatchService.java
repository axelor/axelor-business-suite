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
package com.axelor.apps.base.service.administration;

import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.message.service.MailMessageService;
import com.axelor.auth.AuthUtils;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import com.google.inject.servlet.RequestScoper;
import com.google.inject.servlet.ServletScopes;
import java.util.Collections;
import java.util.concurrent.Callable;

public abstract class AbstractBatchService implements Callable<Batch> {

  private Model batchModel;

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
   */
  public Model findModelByCode(String code) {
    return Query.of(getModelClass()).filter("self.code = :code").bind("code", code).fetchOne();
  }

  public void setBatchModel(Model batchModel) {
    this.batchModel = batchModel;
  }

  @Override
  public Batch call() throws AxelorException {
    final RequestScoper scope = ServletScopes.scopeRequest(Collections.emptyMap());
    try (RequestScoper.CloseableScope ignored = scope.open()) {
      batchModel = JPA.find(getModelClass(), batchModel.getId());
      Batch batch = this.run(batchModel);
      if (batch != null) {
        Beans.get(MailMessageService.class)
            .sendNotification(
                AuthUtils.getUser(),
                String.format(
                    I18n.get(IExceptionMessage.ABSTRACT_BATCH_FINISHED_SUBJECT), batch.getId()),
                batch.getComments(),
                batch.getId(),
                batch.getClass());
      } else if (batchModel != null) {
        Beans.get(MailMessageService.class)
            .sendNotification(
                AuthUtils.getUser(),
                String.format(
                    I18n.get(IExceptionMessage.ABSTRACT_BATCH_FINISHED_SUBJECT),
                    batchModel.getId()),
                I18n.get(IExceptionMessage.ABSTRACT_BATCH_FINISHED_DEFAULT_MESSAGE),
                batchModel.getId(),
                batchModel.getClass());
      }
      return batch;
    } catch (Exception e) {
      onRunnerException(e);
      throw e;
    }
  }

  @Transactional
  protected void onRunnerException(Exception e) {
    TraceBackService.trace(e);
    Beans.get(MailMessageService.class)
        .sendNotification(
            AuthUtils.getUser(),
            I18n.get(IExceptionMessage.ABSTRACT_BATCH_MESSAGE_ON_EXCEPTION),
            e.getMessage(),
            batchModel.getId(),
            batchModel.getClass());
  }
}
