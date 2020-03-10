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
package com.axelor.studio.service;

import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.MetaJsonModel;
import com.axelor.meta.db.repo.MetaFileRepository;
import com.axelor.meta.db.repo.MetaJsonFieldRepository;
import com.axelor.meta.db.repo.MetaJsonModelRepository;
import com.axelor.studio.db.ActionBuilder;
import com.axelor.studio.db.AppBuilder;
import com.axelor.studio.db.ChartBuilder;
import com.axelor.studio.db.DashboardBuilder;
import com.axelor.studio.db.MenuBuilder;
import com.axelor.studio.db.Wkf;
import com.axelor.studio.db.repo.ActionBuilderRepository;
import com.axelor.studio.db.repo.AppBuilderRepository;
import com.axelor.studio.db.repo.ChartBuilderRepository;
import com.axelor.studio.db.repo.DashboardBuilderRepository;
import com.axelor.studio.db.repo.MenuBuilderRepository;
import com.axelor.studio.service.wkf.WkfService;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import javax.xml.bind.JAXBException;

public class ImportService {

  @Inject private ChartBuilderRepository chartBuilderRepo;

  @Inject private MetaJsonModelRepository metaJsonModelRepo;

  @Inject private MetaJsonFieldRepository metaJsonFieldRepo;

  @Inject private DashboardBuilderRepository dashboardBuilderRepo;

  @Inject private MenuBuilderRepository menuBuilderRepo;

  @Inject private ActionBuilderRepository actionBuilderRepo;

  @Inject private AppBuilderRepository appBuilderRepo;

  @Inject private WkfService wkfService;

  @Inject private MetaFiles metaFiles;

  @Inject private MetaFileRepository metaFileRepo;

  public Object importMetaJsonModel(Object bean, Map<String, Object> values) {

    assert bean instanceof MetaJsonModel;

    return metaJsonModelRepo.save((MetaJsonModel) bean);
  }

  public Object importMetaJsonField(Object bean, Map<String, Object> values) {

    assert bean instanceof MetaJsonField;

    return metaJsonFieldRepo.save((MetaJsonField) bean);
  }

  public Object importChartBuilder(Object bean, Map<String, Object> values)
      throws JAXBException, AxelorException {

    assert bean instanceof ChartBuilder;

    return chartBuilderRepo.save((ChartBuilder) bean);
  }

  public Object importDashboardBuilder(Object bean, Map<String, Object> values) {

    assert bean instanceof DashboardBuilder;

    return dashboardBuilderRepo.save((DashboardBuilder) bean);
  }

  public Object importMenuBuilder(Object bean, Map<String, Object> values) {

    assert bean instanceof MenuBuilder;

    return menuBuilderRepo.save((MenuBuilder) bean);
  }

  public Object importActionBuilder(Object bean, Map<String, Object> values) {

    assert bean instanceof ActionBuilder;

    return actionBuilderRepo.save((ActionBuilder) bean);
  }

  public Object importWkf(Object bean, Map<String, Object> values) {

    assert bean instanceof Wkf;

    Wkf wkf = (Wkf) bean;

    wkfService.process(wkf);

    return wkf;
  }

  public Object importAppBuilderImg(Object bean, Map<String, Object> values) {

    assert bean instanceof AppBuilder;

    AppBuilder appBuilder = (AppBuilder) bean;
    String fileName = (String) values.get("fileName");
    String imageData = (String) values.get("imageData");

    if (fileName != null && imageData != null) {
      appBuilder.setImage(importImg(fileName, imageData));
    }

    appBuilder = appBuilderRepo.save(appBuilder);

    return appBuilder;
  }

  public Object importAppBuilder(Object bean, Map<String, Object> values) {

    assert bean instanceof AppBuilder;

    AppBuilder appBuilder = (AppBuilder) bean;

    return appBuilderRepo.save(appBuilder);
  }

  // Import methods specific for import from AppBuilder
  protected MetaFile importImg(String name, String data) {

    if (data == null) {
      return null;
    }

    byte[] img = Base64.getDecoder().decode(data);

    ByteArrayInputStream inImg = new ByteArrayInputStream(img);

    MetaFile metaFile = metaFileRepo.all().filter("self.fileName = ?1", name).fetchOne();

    try {
      if (metaFile != null) {
        return metaFiles.upload(inImg, metaFile);
      } else {
        return metaFiles.upload(inImg, name);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  public MetaJsonField importJsonModelField(Object bean, Map<String, Object> values) {

    assert bean instanceof MetaJsonField;

    MetaJsonField field = (MetaJsonField) bean;

    if (field.getJsonModel() == null) {
      return null;
    }

    return field;
  }

  public MetaJsonField importJsonField(Object bean, Map<String, Object> values) {

    assert bean instanceof MetaJsonField;

    MetaJsonField field = (MetaJsonField) bean;

    if (field.getJsonModel() != null) {
      return null;
    }

    return field;
  }

  public Object importAppMetaJsonModel(Object bean, Map<String, Object> values) {

    assert bean instanceof MetaJsonModel;

    MetaJsonModel model = (MetaJsonModel) bean;

    JPA.flush();

    JPA.refresh(model);

    return metaJsonModelRepo.save(model);
  }

  public Object importAppDashboardBuilder(Object bean, Map<String, Object> values) {

    assert bean instanceof DashboardBuilder;

    DashboardBuilder dashboard = (DashboardBuilder) bean;

    JPA.flush();

    JPA.refresh(dashboard);

    return dashboardBuilderRepo.save(dashboard);
  }

  public Object importAppWkf(Object bean, Map<String, Object> values) {

    assert bean instanceof Wkf;

    Wkf wkf = (Wkf) bean;

    JPA.flush();

    JPA.refresh(wkf);

    wkfService.process(wkf);

    return wkf;
  }
}
