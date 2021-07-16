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
package com.axelor.apps.sale.web;

import com.axelor.apps.sale.db.ConfiguratorCreator;
import com.axelor.apps.sale.db.repo.ConfiguratorCreatorRepository;
import com.axelor.apps.sale.service.configurator.ConfiguratorCreatorService;
import com.axelor.apps.sale.service.configurator.ConfiguratorJaxbIEService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ConfiguratorCreatorController {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  /**
   * Called from the configurator creator form on formula changes
   *
   * @param request
   * @param response
   */
  public void updateAndActivate(ActionRequest request, ActionResponse response) {
    try {
      ConfiguratorCreator creator = request.getContext().asType(ConfiguratorCreator.class);
      ConfiguratorCreatorService configuratorCreatorService =
          Beans.get(ConfiguratorCreatorService.class);
      creator = Beans.get(ConfiguratorCreatorRepository.class).find(creator.getId());
      configuratorCreatorService.updateIndicators(creator);
      configuratorCreatorService.activate(creator);
      response.setSignal("refresh-app", true);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  /**
   * Called from configurator creator grid view, on clicking import button. Call {@link
   * ConfiguratorCreatorService#importConfiguratorCreators(String)}.
   *
   * @param request
   * @param response
   */
  public void importConfiguratorCreators(ActionRequest request, ActionResponse response) {
    try {
      String pathDiff = (String) ((Map) request.getContext().get("dataFile")).get("filePath");
      String importLog =
          Beans.get(ConfiguratorJaxbIEService.class).importXMLToConfigurators(pathDiff);
      response.setValue("importLog", importLog);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  public void updateAttributes(ActionRequest request, ActionResponse response) {
    try {
      ConfiguratorCreator creator = request.getContext().asType(ConfiguratorCreator.class);
      creator = Beans.get(ConfiguratorCreatorRepository.class).find(creator.getId());
      Beans.get(ConfiguratorCreatorService.class).updateAttributes(creator);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  public void exportConfiguratorCreator(ActionRequest request, ActionResponse response) {

    try {
      List<Integer> ids = (List<Integer>) request.getContext().get("_ids");
      ConfiguratorCreatorRepository ccRepository = Beans.get(ConfiguratorCreatorRepository.class);
      List<ConfiguratorCreator> ccList =
          ids.stream().map(id -> ccRepository.find(id.longValue())).collect(Collectors.toList());

      MetaFile dataFile =
          Beans.get(ConfiguratorJaxbIEService.class).exportConfiguratorsToXML(ccList);

      if (dataFile != null) {
        response.setView(
            ActionView.define(I18n.get("Data"))
                .add(
                    "html",
                    "ws/rest/com.axelor.meta.db.MetaFile/"
                        + dataFile.getId()
                        + "/content/download?v="
                        + dataFile.getVersion())
                .param("download", "true")
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }
}
