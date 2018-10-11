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
package com.axelor.apps.base.job;

import com.axelor.apps.base.service.administration.AbstractBatchService;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaSchedule;
import com.axelor.meta.db.repo.MetaScheduleRepository;
import com.axelor.rpc.Context;
import com.axelor.script.GroovyScriptHelper;
import com.axelor.script.ScriptHelper;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchJob implements Job {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final MetaScheduleRepository metaScheduleRepository;

  @Inject
  public BatchJob(MetaScheduleRepository metaScheduleRepository) {
    this.metaScheduleRepository = metaScheduleRepository;
  }

  @Override
  @Transactional
  public void execute(JobExecutionContext context) throws JobExecutionException {
    JobDetail jobDetail = context.getJobDetail();
    MetaSchedule metaSchedule = metaScheduleRepository.findByName(jobDetail.getKey().getName());
    String batchServiceClassName = metaSchedule.getBatchServiceSelect();
    String batchCode = metaSchedule.getBatchCode();
    Class<? extends AbstractBatchService> batchServiceClass;

    try {
      batchServiceClass =
          Class.forName(batchServiceClassName).asSubclass(AbstractBatchService.class);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      throw new JobExecutionException(e);
    }

    AbstractBatchService batchService = Beans.get(batchServiceClass);
    Model batchModel;
    try {
      batchModel = batchService.findModelByCode(batchCode);
    } catch (AxelorException e) {
      throw new JobExecutionException(e);
    }

    if (batchModel == null) {
      String msg =
          String.format("Batch %s not found with service %s", batchCode, batchServiceClassName);
      log.error(msg);
      throw new JobExecutionException(msg);
    }

    Context scriptContext = new Context(Mapper.toMap(batchModel), batchModel.getClass());
    ScriptHelper scriptHelper = new GroovyScriptHelper(scriptContext);
    JobDataMap jobDataMap = jobDetail.getJobDataMap();
    Map<String, Object> originalValues = new HashMap<>();

    try {
      // Apply job's parameters to the batch.
      for (Map.Entry<String, Object> entry : jobDataMap.entrySet()) {
        String key = entry.getKey();
        if (PropertyUtils.isWriteable(batchModel, key)) {
          try {
            originalValues.put(key, BeanUtils.getProperty(batchModel, key));
            Object value = scriptHelper.eval(entry.getValue().toString());
            BeanUtils.setProperty(batchModel, key, value);
          } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
          }
        }
      }

      // Run the batch.
      try {
        batchService.run(batchModel);
      } catch (AxelorException e) {
        e.printStackTrace();
        throw new JobExecutionException(e);
      }
    } finally {
      // Restore original values on the batch.
      for (Map.Entry<String, Object> entry : originalValues.entrySet()) {
        try {
          BeanUtils.setProperty(batchModel, entry.getKey(), entry.getValue());
        } catch (IllegalAccessException | InvocationTargetException e) {
          e.printStackTrace();
        }
      }
    }
  }
}
