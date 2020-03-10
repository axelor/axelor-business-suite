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
package com.axelor.studio.service.builder;

import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaJsonRecord;
import com.axelor.meta.db.MetaView;
import com.axelor.meta.schema.views.AbstractWidget;
import com.axelor.meta.schema.views.Dashboard;
import com.axelor.meta.schema.views.Dashlet;
import com.axelor.studio.db.DashboardBuilder;
import com.axelor.studio.db.DashletBuilder;
import com.axelor.studio.service.StudioMetaService;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This service class used to generate dashboard from ViewBuilder of type Dashboard. It will take
 * all selected charts and generate xml from it.
 *
 * @author axelor
 */
public class DashboardBuilderService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private StudioMetaService metaService;

  /**
   * Method to generate Dashboard (meta schema) from View Builder.
   *
   * @param viewBuilder ViewBuilder of type dashboard.
   * @return Dashboard.
   */
  public MetaView build(DashboardBuilder dashboardBuilder) {

    log.debug("Processing dashboard: {}", dashboardBuilder.getName());

    log.debug("Dashlet list: {}", dashboardBuilder.getDashletBuilderList());

    if (dashboardBuilder.getDashletBuilderList() == null
        || dashboardBuilder.getDashletBuilderList().isEmpty()) {
      return null;
    }

    Dashboard dashboard = new Dashboard();
    String boardName = dashboardBuilder.getName();
    dashboard.setTitle(dashboardBuilder.getTitle());
    dashboard.setName(dashboardBuilder.getName());
    List<AbstractWidget> dashlets = new ArrayList<AbstractWidget>();

    dashboardBuilder.clearGeneratedActions();
    for (DashletBuilder dashletBuilder : dashboardBuilder.getDashletBuilderList()) {

      Dashlet dashlet = new Dashlet();
      String name = null;
      String model = null;
      MetaView metaView = dashletBuilder.getMetaView();
      MetaAction action = dashletBuilder.getAction();

      String actionName = null;
      if (metaView != null) {
        name = metaView.getName();
        model = metaView.getModel();
        MetaAction metaAction = getAction(boardName, name, model, dashletBuilder);
        actionName = metaAction.getName();
        dashboardBuilder.addGeneratedAction(metaAction);
      } else if (action != null) {
        model = action.getModel();
        actionName = action.getName();
      }

      dashlet.setAction(actionName);
      dashlet.setHeight("350");

      Integer colSpan = dashletBuilder.getColspan();
      if (colSpan > 12) {
        colSpan = 12;
      } else if (colSpan <= 0) {
        colSpan = 6;
      }

      dashlet.setColSpan(colSpan);
      dashlets.add(dashlet);
    }

    if (dashlets.isEmpty()) {
      return null;
    }

    dashboard.setItems(dashlets);

    MetaStore.clear();

    return metaService.generateMetaView(dashboard);
  }

  /**
   * Method to generate action-view for a chart
   *
   * @param dashboard Dashboard in which chart to be used.
   * @param actions
   * @param chart Chart to open from action-view.
   * @return Name of action-view.
   */
  protected MetaAction getAction(
      String dashboard, String name, String model, DashletBuilder dashletBuilder) {

    String actionName = "action-" + (dashboard + "-" + name).replace(".", "-");

    boolean isJson = model != null && model.contentEquals(MetaJsonRecord.class.getName());

    String otherView = "form";
    String view = dashletBuilder.getViewType();
    if (view.equals("form")) {
      otherView = "grid";
    }

    String xmlId = StudioMetaService.XML_ID_PREFIX + actionName;
    StringBuilder xml = new StringBuilder();
    xml.append("<action-view name=\"" + actionName + "\" ");
    xml.append("id=\"" + xmlId + "\" ");
    xml.append("title=\"" + dashletBuilder.getName() + "\" ");
    if (model != null) {
      xml.append("model=\"" + model + "\"");
    }
    xml.append(">");
    xml.append("\n\t<view type=\"" + view + "\" ");
    xml.append("name=\"" + name + "\" />");
    if (isJson) {
      xml.append("\n\t<view type=\"" + otherView + "\" ");
      xml.append("name=\"" + name.replace("-" + view, "-" + otherView) + "\" />");
    } else {
      xml.append("\n\t<view type=\"" + otherView + "\" />");
    }
    if (dashletBuilder.getPaginationLimit() > 0) {
      xml.append(
          "\n\t<view-param name=\"limit\" value=\""
              + dashletBuilder.getPaginationLimit().toString()
              + "\"/>");
    }

    if (isJson) {
      String[] models = name.split("-");
      xml.append("\n\t<domain>self.jsonModel = '" + models[models.length - 2] + "'</domain>");
    }
    xml.append("\n</action-view>");

    return metaService.updateMetaAction(actionName, "action-view", xml.toString(), model);
  }
}
