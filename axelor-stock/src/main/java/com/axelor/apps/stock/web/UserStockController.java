package com.axelor.apps.stock.web;

import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.service.UserServiceStock;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class UserStockController {

  public void loginWithStockLocation(ActionRequest request, ActionResponse response) {
    try {
      String serialNumber = (String) request.getContext().get("serialNumber");

      boolean isSerialNumberOk =
          Beans.get(UserServiceStock.class).isSerialNumberOk(request.getUser(), serialNumber);

      if (isSerialNumberOk) {
        response.setSignal("refresh-app", true);
      } else {
        response.setError(
            I18n.get(IExceptionMessage.USER_LOGIN_WITH_STOCK_LOCATION_WRONG_SERIAL_NUMBER));
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
