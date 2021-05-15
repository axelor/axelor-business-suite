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
package com.axelor.apps.production.service;

import com.axelor.apps.production.db.ProdHumanResource;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.WorkCenter;
import com.axelor.apps.production.db.WorkCenterGroup;
import com.axelor.apps.production.db.repo.ProdProcessLineRepository;
import com.axelor.apps.production.db.repo.WorkCenterRepository;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;

public class ProdProcessLineServiceImpl implements ProdProcessLineService {

  protected ProdProcessLineRepository prodProcessLineRepo;

  @Inject
  public ProdProcessLineServiceImpl(ProdProcessLineRepository prodProcessLineRepo) {
    this.prodProcessLineRepo = prodProcessLineRepo;
  }

  @Override
  public Long getProdProcessLineDurationFromWorkCenter(WorkCenter workCenter) {
    List<Long> durations = new ArrayList<>();

    if (workCenter.getWorkCenterTypeSelect() == WorkCenterRepository.WORK_CENTER_TYPE_MACHINE
        || workCenter.getWorkCenterTypeSelect() == WorkCenterRepository.WORK_CENTER_TYPE_BOTH) {
      durations.add(workCenter.getDurationPerCycle());
    }

    if (workCenter.getWorkCenterTypeSelect() == WorkCenterRepository.WORK_CENTER_TYPE_HUMAN
        || workCenter.getWorkCenterTypeSelect() == WorkCenterRepository.WORK_CENTER_TYPE_BOTH) {
      if (workCenter.getProdHumanResourceList() != null) {
        for (ProdHumanResource prodHumanResource : workCenter.getProdHumanResourceList()) {
          durations.add(prodHumanResource.getDuration());
        }
      }
    }

    return !CollectionUtils.isEmpty(durations) ? Collections.max(durations) : 0L;
  }

  @Override
  public BigDecimal getProdProcessLineMinCapacityPerCycleFromWorkCenter(WorkCenter workCenter) {
    if (workCenter.getWorkCenterTypeSelect() == WorkCenterRepository.WORK_CENTER_TYPE_MACHINE
        || workCenter.getWorkCenterTypeSelect() == WorkCenterRepository.WORK_CENTER_TYPE_BOTH) {
      return workCenter.getMinCapacityPerCycle();
    } else {
      return BigDecimal.ONE;
    }
  }

  @Override
  public BigDecimal getProdProcessLineMaxCapacityPerCycleFromWorkCenter(WorkCenter workCenter) {
    if (workCenter.getWorkCenterTypeSelect() == WorkCenterRepository.WORK_CENTER_TYPE_MACHINE
        || workCenter.getWorkCenterTypeSelect() == WorkCenterRepository.WORK_CENTER_TYPE_BOTH) {
      return workCenter.getMaxCapacityPerCycle();
    } else {
      return BigDecimal.ONE;
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void setWorkCenterGroup(ProdProcessLine prodProcessLine, WorkCenterGroup workCenterGroup)
      throws AxelorException {
    prodProcessLine = copyWorkCenterGroup(prodProcessLine, workCenterGroup);
    WorkCenter workCenter = getMainWorkCenterFromGroup(prodProcessLine);
    prodProcessLine.setWorkCenter(workCenter);
    prodProcessLine.setDurationPerCycle(getProdProcessLineDurationFromWorkCenter(workCenter));
    prodProcessLine.setMinCapacityPerCycle(
        getProdProcessLineMinCapacityPerCycleFromWorkCenter(workCenter));
    prodProcessLine.setMaxCapacityPerCycle(
        getProdProcessLineMaxCapacityPerCycleFromWorkCenter(workCenter));
  }

  /**
   * Create a work center group from a template. Since a template is also a work center group, we
   * copy and set template field to false.
   */
  protected ProdProcessLine copyWorkCenterGroup(
      ProdProcessLine prodProcessLine, WorkCenterGroup workCenterGroup) {
    WorkCenterGroup workCenterGroupCopy = JPA.copy(workCenterGroup, false);
    workCenterGroupCopy.setWorkCenterGroupModel(workCenterGroup);
    workCenterGroupCopy.clearWorkCenterSet();
    workCenterGroupCopy.setTemplate(false);
    workCenterGroup
        .getWorkCenterSet()
        .forEach(
            (k -> {
              WorkCenter w = JPA.copy(k, false);
              workCenterGroupCopy.addWorkCenterSetItem(w);
            }));
    prodProcessLine.setWorkCenterGroup(workCenterGroupCopy);
    return Beans.get(ProdProcessLineRepository.class).save(prodProcessLine);
  }

  @Override
  public WorkCenter getMainWorkCenterFromGroup(ProdProcessLine prodProcessLine)
      throws AxelorException {
    WorkCenterGroup workCenterGroup = prodProcessLine.getWorkCenterGroup();
    if (workCenterGroup == null) {
      return null;
    }
    Set<WorkCenter> workCenterSet = workCenterGroup.getWorkCenterSet();
    if (workCenterSet == null || workCenterSet.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.NO_WORK_CENTER_GROUP));
    }
    return workCenterSet.stream()
        .min(Comparator.comparing(WorkCenter::getSequence))
        .orElseThrow(
            () ->
                new AxelorException(
                    TraceBackRepository.CATEGORY_INCONSISTENCY,
                    I18n.get(IExceptionMessage.NO_WORK_CENTER_GROUP)));
  }
}
