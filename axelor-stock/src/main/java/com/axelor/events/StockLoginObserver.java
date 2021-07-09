package com.axelor.events;

import com.axelor.apps.stock.service.UserServiceStock;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.event.Observes;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.google.inject.servlet.RequestScoped;
import javax.inject.Named;

@RequestScoped
public class StockLoginObserver {

  // Observes successful login.
  void onLoginSuccess(@Observes @Named(PostLogin.SUCCESS) PostLogin event) {
    try {

      User user = event.getUser();

      user = Beans.get(UserRepository.class).find(user.getId());
      if (user.getGroup() != null
          && user.getGroup().getIsStockLocationConnection() != null
          && user.getGroup().getIsStockLocationConnection() == true) {
        Beans.get(UserServiceStock.class).updateUserForLoginToCell(user);
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }
}
