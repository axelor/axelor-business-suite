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
package com.axelor.apps.base.service.app;

import com.axelor.apps.base.db.AccessConfig;
import com.axelor.apps.base.db.App;
import com.axelor.apps.base.db.repo.AccessConfigRepository;
import com.axelor.auth.db.Permission;
import com.axelor.auth.db.Role;
import com.axelor.auth.db.repo.PermissionRepository;
import com.axelor.auth.db.repo.RoleRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaMenu;
import com.axelor.meta.db.repo.MetaMenuRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class AccessConfigImportServiceImpl implements AccessConfigImportService {

  @Inject private AppService appService;

  @Inject private AccessConfigRepository accessConfigRepo;

  @Inject private PermissionRepository permissionRepo;

  @Inject private RoleRepository roleRepo;

  @Inject private MetaMenuRepository metaMenuRepo;

  @Override
  public void importAccessConfig(MetaFile metaFile) throws AxelorException {

    if (metaFile == null) {
      return;
    }
    try {
      OPCPackage pkg = OPCPackage.open(MetaFiles.getPath(metaFile).toString());
      XSSFWorkbook workBook = new XSSFWorkbook(pkg);
      processWorkbook(workBook);
    } catch (InvalidFormatException | IOException e) {
      TraceBackService.trace(e);
      throw new AxelorException(TraceBackRepository.TYPE_TECHNICAL, e.getMessage());
    }
  }

  protected void processWorkbook(XSSFWorkbook workBook) {

    Iterator<XSSFSheet> sheetIter = workBook.iterator();

    while (sheetIter.hasNext()) {
      XSSFSheet sheet = sheetIter.next();
      String name = sheet.getSheetName();
      if (name.endsWith("-menu")) {
        importMenuAccess(sheet);
      } else {
        importObjectAccess(sheet);
      }
    }
  }

  protected void importObjectAccess(XSSFSheet sheet) {

    App app = appService.getApp(sheet.getSheetName());
    if (app == null) {
      return;
    }
    Iterator<Row> rowIter = sheet.iterator();
    Map<Integer, AccessConfig> accessMap = null;
    while (rowIter.hasNext()) {
      if (accessMap == null) {
        accessMap = getAccessConfig(rowIter.next(), app);
        continue;
      }
      createObjectRoles(accessMap, rowIter.next());
    }
  }

  @Transactional
  public Map<Integer, AccessConfig> getAccessConfig(Row row, App app) {

    Map<Integer, AccessConfig> configMap = new HashMap<>();

    Iterator<Cell> cellIter = row.iterator();
    cellIter.next();
    while (cellIter.hasNext()) {
      Cell cell = cellIter.next();
      String name = cell.getStringCellValue();
      if (name != null) {
        AccessConfig config =
            accessConfigRepo.all().filter("self.name = ?1 and self.app = ?2", name, app).fetchOne();
        if (config == null) {
          config = new AccessConfig(name);
          config.setApp(app);
          config = accessConfigRepo.save(config);
        }
        configMap.put(cell.getColumnIndex(), config);
      }
    }

    return configMap;
  }

  protected void createObjectRoles(Map<Integer, AccessConfig> accessMap, Row row) {

    Iterator<Cell> cellIter = row.iterator();
    String obj = cellIter.next().getStringCellValue();
    while (cellIter.hasNext()) {
      Cell cell = cellIter.next();
      String value = cell.getStringCellValue();
      if (cell == null || Strings.isNullOrEmpty(value) || invalidValue(value)) {
        continue;
      }
      AccessConfig config = accessMap.get(cell.getColumnIndex());
      Permission permission = getPermission(obj, value.trim(), config);
      addRole(config, permission);
    }
  }

  protected boolean invalidValue(String value) {

    return !"rwcde".startsWith(value);
  }

  @Transactional
  public Permission getPermission(String model, String value, AccessConfig config) {

    String[] objs = model.split("\\.");
    String obj = objs[objs.length - 1];
    String pkg = objs[objs.length - 3];
    String name = "perm." + pkg + "." + obj + "." + value;
    Permission permission =
        permissionRepo.all().filter("self.name = ?1 and self.object = ?2", name, model).fetchOne();
    if (permission == null) {
      permission = new Permission(name);
      permission.setObject(model);
    }
    boolean defaultRight = value.equals("all");
    permission.setCanCreate(defaultRight);
    permission.setCanRead(defaultRight);
    permission.setCanWrite(defaultRight);
    permission.setCanRemove(defaultRight);
    permission.setCanExport(defaultRight);

    if (!defaultRight) {
      for (char c : value.toCharArray()) {
        switch (c) {
          case 'r':
            {
              permission.setCanRead(true);
              break;
            }
          case 'c':
            {
              permission.setCanCreate(true);
              break;
            }
          case 'w':
            {
              permission.setCanWrite(true);
              break;
            }
          case 'd':
            {
              permission.setCanRemove(true);
              break;
            }
          case 'e':
            {
              permission.setCanExport(true);
              break;
            }
        }
      }
    }

    return permissionRepo.save(permission);
  }

  @Transactional
  public void addRole(AccessConfig config, Permission permission) {

    String name = config.getApp().getCode() + "." + config.getName();
    Role role = roleRepo.findByName(name);
    if (role == null) {
      role = new Role(name);
    }
    role.addPermission(permission);
    role = roleRepo.save(role);
    config.addRoleSetItem(role);
    accessConfigRepo.save(config);
  }

  protected void importMenuAccess(XSSFSheet sheet) {

    App app = appService.getApp(sheet.getSheetName().split("-")[0]);
    if (app == null) {
      return;
    }
    Iterator<Row> rowIter = sheet.iterator();
    Map<Integer, AccessConfig> accessMap = null;
    while (rowIter.hasNext()) {
      if (accessMap == null) {
        accessMap = getAccessConfig(rowIter.next(), app);
        continue;
      }
      createMenuRoles(accessMap, rowIter.next());
    }
  }

  protected void createMenuRoles(Map<Integer, AccessConfig> accessMap, Row row) {

    Iterator<Cell> cellIter = row.iterator();
    String menu = cellIter.next().getStringCellValue().trim();
    while (cellIter.hasNext()) {
      Cell cell = cellIter.next();
      String value = cell.getStringCellValue();
      if (cell == null || Strings.isNullOrEmpty(value)) {
        continue;
      }
      AccessConfig config = accessMap.get(cell.getColumnIndex());
      addRole(config, menu);
    }
  }

  @Transactional
  public void addRole(AccessConfig config, String menu) {

    String name = config.getApp().getCode() + "." + config.getName();
    Role role = roleRepo.findByName(name);
    if (role == null) {
      role = new Role(name);
    }
    MetaMenu metaMenu = metaMenuRepo.findByName(menu);
    if (metaMenu != null) {
      metaMenu.addRole(role);
      metaMenuRepo.save(metaMenu);
    }
    config.addRoleSetItem(role);
    accessConfigRepo.save(config);
  }
}
