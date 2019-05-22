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
package com.axelor.apps.production.db.repo;

import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.production.db.ManufOrder;
import com.axelor.apps.production.db.OperationOrder;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.apps.production.service.operationorder.OperationOrderService;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import javax.persistence.PersistenceException;

public class ManufOrderManagementRepository extends ManufOrderRepository {

  @Inject OperationOrderService operationOrderService;

  @Override
  public ManufOrder copy(ManufOrder entity, boolean deep) {
    entity.setStatusSelect(ManufOrderRepository.STATUS_DRAFT);
    entity.setManufOrderSeq(null);
    entity.setPlannedStartDateT(null);
    entity.setPlannedEndDateT(null);
    entity.setRealStartDateT(null);
    entity.setRealEndDateT(null);
    entity.setInStockMoveList(null);
    entity.setOutStockMoveList(null);
    entity.setWasteStockMove(null);
    entity.setToConsumeProdProductList(null);
    entity.setConsumedStockMoveLineList(null);
    entity.setDiffConsumeProdProductList(null);
    entity.setToProduceProdProductList(null);
    entity.setProducedStockMoveLineList(null);
    entity.setWasteProdProductList(null);
    if (entity.getOperationOrderList() != null) {
      for (OperationOrder operationOrder : entity.getOperationOrderList()) {
        operationOrder.setStatusSelect(OperationOrderRepository.STATUS_DRAFT);
        operationOrder.setPlannedStartDateT(null);
        operationOrder.setPlannedEndDateT(null);
        operationOrder.setPlannedDuration(0L);
        operationOrder.setRealStartDateT(null);
        operationOrder.setRealEndDateT(null);
        operationOrder.setRealDuration(0L);
        operationOrder.setOperationOrderDurationList(null);
        operationOrder.setInStockMoveList(null);
        operationOrder.setToConsumeProdProductList(null);
        operationOrder.setConsumedStockMoveLineList(null);
        operationOrder.setDiffConsumeProdProductList(null);
        operationOrder.setBarCode(null);
      }
    }
    return super.copy(entity, deep);
  }

  @Override
  public ManufOrder save(ManufOrder entity) {
    entity = super.save(entity);

    try {
      if (Strings.isNullOrEmpty(entity.getManufOrderSeq())
          && entity.getStatusSelect() == ManufOrderRepository.STATUS_DRAFT) {
        entity.setManufOrderSeq(Beans.get(SequenceService.class).getDraftSequenceNumber(entity));
      }
    } catch (AxelorException e) {
      throw new PersistenceException(e);
    }

    if (entity.getOperationOrderList() != null) {
      for (OperationOrder operationOrder : entity.getOperationOrderList()) {
        if (operationOrder.getBarCode() == null) {
          operationOrderService.createBarcode(operationOrder);
        }
      }
    }
    return super.save(entity);
  }

  @Override
  public void remove(ManufOrder entity) {
    Integer status = entity.getStatusSelect();
    if (status == ManufOrderRepository.STATUS_PLANNED
        || status == ManufOrderRepository.STATUS_STANDBY
        || status == ManufOrderRepository.STATUS_IN_PROGRESS) {
      throw new PersistenceException(I18n.get(IExceptionMessage.ORDER_REMOVE_NOT_OK));
    } else if (status == ManufOrderRepository.STATUS_FINISHED) {
      entity.setArchived(true);
    } else {
      super.remove(entity);
    }
  }
}
