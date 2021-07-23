package com.axelor.apps.base.db.repo;

import com.axelor.apps.base.db.MainActivity;
import com.axelor.exception.AxelorException;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

public class MainActivityListener {

  @PrePersist
  @PreUpdate
  private void computeFullName(MainActivity mainActivity) throws AxelorException {
    if (mainActivity != null) {
      mainActivity.setFullName(mainActivity.getCode() + " - " + mainActivity.getShortName());
    }
  }
}
