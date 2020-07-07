/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.auth.db.AuditableModel;
import com.axelor.db.EntityHelper;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.common.base.Preconditions;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBatch {
  private static final ThreadLocal<Long> threadBatchId = new ThreadLocal<>();

  public static final int FETCH_LIMIT = 10;

  @Inject protected AppBaseService appBaseService;

  protected static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected Batch batch;
  protected Model model;

  @Inject protected BatchRepository batchRepo;

  private int done;
  private int anomaly;

  protected AbstractBatch() {
    this.batch = new Batch();

    this.batch.setStartDate(ZonedDateTime.now());

    this.done = 0;
    this.anomaly = 0;

    this.batch.setDone(this.done);
    this.batch.setAnomaly(this.anomaly);

    JPA.runInTransaction(() -> batch = JPA.persist(batch));
  }

  public Batch getBatch() {
    return batch;
  }

  /**
   * Returns the currently running batch.
   *
   * @return The Batch instance currently being run or <code>null</code> if no batch is in progress.
   */
  public static Batch getCurrentBatch() {
    return JPA.find(Batch.class, getCurrentBatchId());
  }

  /**
   * Returns the ID of the currently running batch.
   *
   * @return The currently running batch's ID or <code>0</code> if no batch is being run (allowing
   *     it to be directly used with {@link TraceBackService}.
   */
  public static long getCurrentBatchId() {
    Long id = threadBatchId.get();
    return id == null ? 0 : id;
  }

  public Batch run(AuditableModel model) {
    Preconditions.checkNotNull(model);
    if (threadBatchId.get() != null) {
      throw new IllegalStateException(I18n.get(IExceptionMessage.ABSTRACT_BATCH_2));
    }

    if (isRunnable(model)) {
      try {
        threadBatchId.set(batch.getId());
        start();
        process();
        stop();
        return batch;
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        threadBatchId.remove();
        unarchived();
      }
    } else {
      throw new RuntimeException(I18n.get(IExceptionMessage.ABSTRACT_BATCH_1));
    }
  }

  protected abstract void process();

  protected boolean isRunnable(Model model) {
    this.model = model;
    return !Boolean.TRUE.equals(model.getArchived());
  }

  protected void start() throws IllegalAccessException {
    LOG.info("Début batch {} ::: {}", model, batch.getStartDate());

    model.setArchived(true);
    associateModel();
    checkPoint();
  }

  /**
   * As {@code batch} entity can be detached from the session, call {@code Batch.find()} get the
   * entity in the persistant context. Warning : {@code batch} entity have to be saved before.
   */
  protected void stop() {
    findBatch();

    batch.setEndDate(ZonedDateTime.now());
    batch.setDuration(getDuring());

    checkPoint();

    LOG.info("Fin batch {} ::: {}", model, batch.getEndDate());
  }

  protected void incrementDone() {
    findBatch();
    _incrementDone();
  }

  protected void _incrementDone() {
    done += 1;
    batch.setDone(done);
    checkPoint();

    LOG.debug("Done ::: {}", done);
  }

  protected void incrementAnomaly() {
    findBatch();
    _incrementAnomaly();
  }

  protected void _incrementAnomaly() {
    anomaly += 1;
    batch.setAnomaly(anomaly);
    checkPoint();

    LOG.debug("Anomaly ::: {}", anomaly);
  }

  protected void addComment(String comment) {
    findBatch();

    batch.setComments(comment);

    checkPoint();
  }

  @Transactional
  protected Batch checkPoint() {
    return findBatch();
  }

  @Transactional
  protected void unarchived() {
    try {
      final Class<? extends Model> entityClass = EntityHelper.getEntityClass(model);
      final Long modelId = model.getId();
      model = JPA.find(entityClass, modelId);
      model.setArchived(false);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    }
  }

  private Long getDuring() {
    return ChronoUnit.MINUTES.between(batch.getStartDate(), batch.getEndDate());
  }

  private void associateModel() throws IllegalAccessException {
    LOG.debug("ASSOCIATE batch:{} TO model:{}", batch, model);

    for (Field field : batch.getClass().getDeclaredFields()) {

      LOG.debug(
          "TRY TO ASSOCIATE field:{} TO model:{}",
          field.getType().getName(),
          model.getClass().getName());
      if (isAssociable(field)) {

        LOG.debug("FIELD ASSOCIATE TO MODEL");
        field.setAccessible(true);
        field.set(batch, model);
        field.setAccessible(false);

        break;
      }
    }
  }

  private boolean isAssociable(Field field) {
    return field.getType().equals(EntityHelper.getEntityClass(model));
  }

  protected Batch findBatch() {
    if (!JPA.em().contains(batch)) {
      batch = JPA.find(Batch.class, batch.getId());
    }

    return batch;
  }

  public int getFetchLimit() {
    int limit = appBaseService.getAppBase().getBatchFetchLimit();
    return limit > 0 ? limit : 1;
  }
}
