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
package com.axelor.apps.production.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.production.db.OperationOrder;
import com.axelor.apps.production.db.repo.ManufOrderRepository;
import com.axelor.apps.production.db.repo.OperationOrderRepository;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.apps.production.report.IReport;
import com.axelor.apps.production.service.manuforder.ManufOrderWorkflowService;
import com.axelor.apps.production.service.operationorder.OperationOrderService;
import com.axelor.apps.production.service.operationorder.OperationOrderStockMoveService;
import com.axelor.apps.production.service.operationorder.OperationOrderWorkflowService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.eclipse.birt.core.exception.BirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OperationOrderController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void computeDuration(ActionRequest request, ActionResponse response) {

    OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
    operationOrder = Beans.get(OperationOrderRepository.class).find(operationOrder.getId());

    Beans.get(OperationOrderWorkflowService.class).computeDuration(operationOrder);
    response.setReload(true);
  }

  public void setPlannedDates(ActionRequest request, ActionResponse response) {
    OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
    LocalDateTime plannedStartDateT = operationOrder.getPlannedStartDateT();
    LocalDateTime plannedEndDateT = operationOrder.getPlannedEndDateT();
    operationOrder = Beans.get(OperationOrderRepository.class).find(operationOrder.getId());
    Beans.get(OperationOrderWorkflowService.class)
        .setPlannedDates(operationOrder, plannedStartDateT, plannedEndDateT);
  }

  public void setRealDates(ActionRequest request, ActionResponse response) {
    OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
    LocalDateTime realStartDateT = operationOrder.getRealStartDateT();
    LocalDateTime realEndDateT = operationOrder.getRealEndDateT();
    operationOrder = Beans.get(OperationOrderRepository.class).find(operationOrder.getId());
    Beans.get(OperationOrderWorkflowService.class)
        .setRealDates(operationOrder, realStartDateT, realEndDateT);
  }

  public void machineChange(ActionRequest request, ActionResponse response) {
    try {
      OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
      OperationOrderRepository operationOrderRepo = Beans.get(OperationOrderRepository.class);
      OperationOrderWorkflowService operationOrderWorkflowService =
          Beans.get(OperationOrderWorkflowService.class);

      operationOrder = operationOrderRepo.find(operationOrder.getId());
      if (operationOrder != null
          && operationOrder.getStatusSelect() == OperationOrderRepository.STATUS_PLANNED) {
        operationOrder = operationOrderWorkflowService.replan(operationOrder);
        List<OperationOrder> operationOrderList =
            operationOrderRepo
                .all()
                .filter(
                    "self.manufOrder = ?1 AND self.priority >= ?2 AND self.statusSelect = 3 AND self.id != ?3",
                    operationOrder.getManufOrder(),
                    operationOrder.getPriority(),
                    operationOrder.getId())
                .order("priority")
                .order("plannedEndDateT")
                .fetch();
        for (OperationOrder operationOrderIt : operationOrderList) {
          operationOrderWorkflowService.replan(operationOrderIt);
        }
        response.setReload(true);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void plan(ActionRequest request, ActionResponse response) {
    try {
      OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
      if (operationOrder.getManufOrder() != null
          && operationOrder.getManufOrder().getStatusSelect()
              < ManufOrderRepository.STATUS_PLANNED) {
        return;
      }
      Beans.get(OperationOrderWorkflowService.class)
          .plan(Beans.get(OperationOrderRepository.class).find(operationOrder.getId()));
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void start(ActionRequest request, ActionResponse response) {
    try {
      OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
      operationOrder = Beans.get(OperationOrderRepository.class).find(operationOrder.getId());
      Beans.get(OperationOrderWorkflowService.class).start(operationOrder);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void pause(ActionRequest request, ActionResponse response) {
    try {
      OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
      operationOrder = Beans.get(OperationOrderRepository.class).find(operationOrder.getId());
      Beans.get(OperationOrderWorkflowService.class).pause(operationOrder);

      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void resume(ActionRequest request, ActionResponse response) {
    try {
      OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
      operationOrder = Beans.get(OperationOrderRepository.class).find(operationOrder.getId());
      Beans.get(ManufOrderWorkflowService.class).resume(operationOrder.getManufOrder());

      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void finish(ActionRequest request, ActionResponse response) {
    try {
      OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
      // this attribute is not in the database, only in the view
      LocalDateTime realStartDateT = operationOrder.getRealStartDateT();
      operationOrder = Beans.get(OperationOrderRepository.class).find(operationOrder.getId());
      operationOrder.setRealStartDateT(realStartDateT);
      Beans.get(OperationOrderWorkflowService.class).finishAndAllOpFinished(operationOrder);

      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void partialFinish(ActionRequest request, ActionResponse response) {
    try {
      OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
      operationOrder = Beans.get(OperationOrderRepository.class).find(operationOrder.getId());

      Beans.get(OperationOrderStockMoveService.class).partialFinish(operationOrder);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void cancel(ActionRequest request, ActionResponse response) {
    try {
      OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
      Beans.get(OperationOrderWorkflowService.class)
          .cancel(Beans.get(OperationOrderRepository.class).find(operationOrder.getId()));

      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Method that generate a Pdf file for an operation order
   *
   * @param request
   * @param response
   * @return
   * @throws BirtException
   * @throws IOException
   */
  public void print(ActionRequest request, ActionResponse response) {
    OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
    String operationOrderIds = "";
    try {

      @SuppressWarnings("unchecked")
      List<Integer> lstSelectedOperationOrder = (List<Integer>) request.getContext().get("_ids");
      if (lstSelectedOperationOrder != null) {
        for (Integer it : lstSelectedOperationOrder) {
          operationOrderIds += it.toString() + ",";
        }
      }

      if (!operationOrderIds.equals("")) {
        operationOrderIds = operationOrderIds.substring(0, operationOrderIds.length() - 1);
        operationOrder =
            Beans.get(OperationOrderRepository.class)
                .find(new Long(lstSelectedOperationOrder.get(0)));
      } else if (operationOrder.getId() != null) {
        operationOrderIds = operationOrder.getId().toString();
      }

      if (!operationOrderIds.equals("")) {

        String name = " ";
        if (operationOrder.getName() != null) {
          name += lstSelectedOperationOrder == null ? "Op " + operationOrder.getName() : "Ops";
        }

        String fileLink =
            ReportFactory.createReport(
                    "productionOperationOrder",
                    operationOrder.getManufOrder().getCompany(),
                    IReport.OPERATION_ORDER,
                    name + "-${date}")
                .addParam("Locale", ReportSettings.getPrintingLocale(null))
                .addParam("OperationOrderId", operationOrderIds)
                .generate()
                .getFileLink();

        LOG.debug("Printing " + name);

        response.setView(ActionView.define(name).add("html", fileLink).map());
      } else {
        response.setFlash(I18n.get(IExceptionMessage.OPERATION_ORDER_1));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void chargeByMachineHours(ActionRequest request, ActionResponse response) {
    try {
      LocalDateTime fromDateTime =
          LocalDateTime.parse(
              request.getContext().get("fromDateTime").toString(), DateTimeFormatter.ISO_DATE_TIME);
      LocalDateTime toDateTime =
          LocalDateTime.parse(
              request.getContext().get("toDateTime").toString(), DateTimeFormatter.ISO_DATE_TIME);

      List<Map<String, Object>> dataList =
          Beans.get(OperationOrderService.class).chargeByMachineHours(fromDateTime, toDateTime);

      response.setData(dataList);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void chargeByMachineDays(ActionRequest request, ActionResponse response) {

    try {
      LocalDateTime fromDateTime =
          LocalDateTime.parse(
              request.getContext().get("fromDateTime").toString(), DateTimeFormatter.ISO_DATE_TIME);
      LocalDateTime toDateTime =
          LocalDateTime.parse(
              request.getContext().get("toDateTime").toString(), DateTimeFormatter.ISO_DATE_TIME);

      List<Map<String, Object>> dataList =
          Beans.get(OperationOrderService.class).chargeByMachineDays(fromDateTime, toDateTime);
      response.setData(dataList);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from operation order form, on consumed stock move line change.
   *
   * @param request
   * @param response
   */
  public void updateConsumedStockMoveFromOperationOrder(
      ActionRequest request, ActionResponse response) {
    try {
      OperationOrder operationOrder = request.getContext().asType(OperationOrder.class);
      operationOrder = Beans.get(OperationOrderRepository.class).find(operationOrder.getId());
      Beans.get(OperationOrderService.class)
          .updateConsumedStockMoveFromOperationOrder(operationOrder);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
