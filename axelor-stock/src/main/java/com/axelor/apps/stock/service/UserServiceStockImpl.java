package com.axelor.apps.stock.service;

import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.auth.db.Group;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.GroupRepository;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.meta.db.repo.MetaMenuRepository;
import com.axelor.meta.db.repo.MetaPermissionRepository;
import com.axelor.meta.db.repo.MetaViewRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class UserServiceStockImpl implements UserServiceStock {

  protected UserRepository userRepository;
  protected GroupRepository groupRepository;
  protected MetaPermissionRepository metaPermissionRepository;
  protected MetaMenuRepository metaMenuRepository;
  protected StockLocationRepository stockLocationRepository;

  @Inject
  public UserServiceStockImpl(
      UserRepository userRepository,
      GroupRepository groupRepository,
      MetaPermissionRepository metaPermissionRepository,
      MetaMenuRepository metaMenuRepository,
      StockLocationRepository stockLocationRepository) {
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
    this.metaPermissionRepository = metaPermissionRepository;
    this.metaMenuRepository = metaMenuRepository;
    this.stockLocationRepository = stockLocationRepository;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public User updateUserForLoginToCell(User user) throws AxelorException {
    Group blockedGroup;

    blockedGroup = groupRepository.findByCode("BlockedTempGroupToLoginCell");
    if (blockedGroup == null) {
      blockedGroup = new Group();
      blockedGroup.setCode("BlockedTempGroupToLoginCell");
      blockedGroup.setNavigation("hidden");
      blockedGroup.setHomeAction("action.open.user.login.to.cell.form");
      blockedGroup.setName("BlockedTempGroupToLoginCell");
      blockedGroup.setIsStockLocationConnection(true);
      blockedGroup.addView(
          Beans.get(MetaViewRepository.class).findByName("user-login-to-cell-form"));
      blockedGroup.addMetaPermission(metaPermissionRepository.findByName("perm.meta.menu.r"));
      blockedGroup.addMetaPermission(metaPermissionRepository.findByName("perms.user"));
      blockedGroup.addMenu(metaMenuRepository.findByName("action-open-user-login-to-cell-form"));
    }

    if (!user.getGroup().getCode().equals("BlockedTempGroupToLoginCell")) {
      user.setSavedGroup(user.getGroup());
      user.setSavedHomeAction(user.getHomeAction());
      user.setHomeAction(null);
      user.setGroup(blockedGroup);
    }

    return user;
  }

  @Override
  @Transactional
  public boolean isSerialNumberOk(User user, String serialNumber) throws AxelorException {
    boolean isSerialNumberOk = false;

    StockLocation stockLocation =
        stockLocationRepository
            .all()
            .filter("self.serialNumber like :serialNumer")
            .bind("serialNumer", serialNumber)
            .fetchOne();

    if (stockLocation != null) {
      isSerialNumberOk = true;
      user.setActiveStockLocation(stockLocation);
      user.setGroup(user.getSavedGroup());
      user.setHomeAction(user.getSavedHomeAction());
      userRepository.save(user);
    }

    return isSerialNumberOk;
  }
}
