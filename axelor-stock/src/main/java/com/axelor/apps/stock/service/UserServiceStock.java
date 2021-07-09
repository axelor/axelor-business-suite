package com.axelor.apps.stock.service;

import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;

public interface UserServiceStock {

  public User updateUserForLoginToCell(User user) throws AxelorException;

  public boolean isSerialNumberOk(User user, String serialNumber) throws AxelorException;
}
