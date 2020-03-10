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

import com.axelor.common.Inflector;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.MetaView;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.meta.db.repo.MetaViewRepository;
import com.axelor.meta.loader.XMLViews;
import com.axelor.meta.schema.ObjectViews;
import com.axelor.meta.schema.views.AbstractView;
import com.axelor.meta.schema.views.AbstractWidget;
import com.axelor.meta.schema.views.Field;
import com.axelor.meta.schema.views.FormView;
import com.axelor.meta.schema.views.GridView;
import com.axelor.meta.schema.views.Panel;
import com.axelor.meta.schema.views.PanelField;
import com.axelor.meta.schema.views.PanelRelated;
import com.axelor.meta.schema.views.PanelTabs;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class generate html template from selected metaView for ReportBuilder. It also add button
 * into MetaView selected as buttonView in ReportBuilder.
 *
 * @author axelor
 */
public class ReportBuilderService {

  private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private List<String[]> panels;

  private List<String[]> sidePanels;

  private String model;

  private String template;

  private Inflector inflector;

  @Inject private MetaFieldRepository metaFieldRepo;

  @Inject private MetaViewRepository metaViewRepo;

  @Inject private MetaModelRepository metaModelRepo;

  /**
   * Method create html template from MetaView. This is root method to create template. Html
   * contains table with two columns. If field is o2m/m2m it will take full row span.
   *
   * @param metaView MetaView to process
   * @return Html template generated.
   */
  public String generateTemplate(MetaView metaView) {

    panels = new ArrayList<String[]>();
    sidePanels = new ArrayList<String[]>();
    model = metaView.getModel();
    template = "";
    inflector = Inflector.getInstance();

    try {
      processView(metaView.getXml());
      generateHtml(false);
      generateHtml(true);
      template = "<table class=\"table no-border\"><tr>" + template + "</tr></table>";
      log.debug("Template generated: {}", template);
      return template;
    } catch (JAXBException e) {
      e.printStackTrace();
    }

    return null;
  }

  /**
   * Method parse given xml to create html.
   *
   * @param xml View xml passed.
   * @throws JAXBException Xml parsing exception.
   */
  protected void processView(String xml) throws JAXBException {

    ObjectViews objectViews = XMLViews.fromXML(xml);
    AbstractView view = objectViews.getViews().get(0);

    FormView formView = (FormView) view;

    for (AbstractWidget widget : formView.getItems()) {
      if (widget instanceof PanelTabs) {
        PanelTabs panelTabs = (PanelTabs) widget;
        AbstractWidget tabItem = panelTabs.getItems().get(0);
        processAbstractWidget(tabItem, false);
        continue;
      }
      processAbstractWidget(widget, false);
    }
  }

  /**
   * Check given AbstractWidget and process it according to its type.
   *
   * @param widget AbstractWidget to process
   * @param sidePanel Boolean to check if widget is sidePanel.
   */
  protected void processAbstractWidget(AbstractWidget widget, Boolean sidePanel) {

    if (widget instanceof Panel) {
      processPanel((Panel) widget, sidePanel);
    } else if (widget instanceof PanelRelated) {
      PanelRelated panelRelated = (PanelRelated) widget;
      sidePanel = sidePanel != null && sidePanel ? sidePanel : panelRelated.getSidebar();
      processPanelRelated(panelRelated, sidePanel, "12");
    } else if (widget instanceof PanelField) {
      processField((PanelField) widget, sidePanel);
    }
  }

  /**
   * Process panel to create html from it and to process items of panels. It add panel title in full
   * row span of html table.
   *
   * @param panel Panels to process.
   * @param sidePanel Boolean to check if panel is sidePanel.
   */
  protected void processPanel(Panel panel, Boolean sidePanel) {

    sidePanel = sidePanel != null && sidePanel ? sidePanel : panel.getSidebar();

    String title = panel.getTitle();

    if (title != null) {
      title = "<td><h4><u>" + title + "</u></h4></td>";
      if (sidePanel != null && sidePanel) {
        sidePanels.add(new String[] {"12", title});
      } else {
        panels.add(new String[] {"12", title});
      }
    }
    for (AbstractWidget widget : panel.getItems()) {
      processAbstractWidget(widget, sidePanel);
    }
  }

  /**
   * Process PanelField and extend html table of html template.
   *
   * @param field PanelField to process.
   * @param sidePanel Boolean to check if field is in sidePanel.
   * @param colSpan Colspan of field.
   */
  protected void processField(PanelField field, Boolean sidePanel) {

    String title = field.getTitle();
    String name = field.getName();

    MetaField metaField = getMetaField(name, model);

    if (Strings.isNullOrEmpty(title)) {
      title = getFieldTitle(name, metaField);
    }

    name = processRelational(name, metaField);

    String value = "<td><b>" + title + "</b> : $" + name + "$</td>";
    String colSpan = "6";

    if (field.getColSpan() != null && field.getColSpan() == 12) {
      colSpan = "12";
      value = "<td colSpan=\"2\"><b>" + title + "</b> : $" + name + "$</td>";
    } else {
      value = "<td><b>" + title + "</b> : $" + name + "$</td>";
    }

    if (sidePanel != null && sidePanel) {
      sidePanels.add(new String[] {"12", value});
    } else {
      panels.add(new String[] {colSpan, value});
    }
  }

  /**
   * Check given metaField and process relational field to create proper string template friendly
   * string.
   *
   * @param name Name of field.
   * @param metaField MetaField to process.
   * @return Template friendly string created for relational field.
   */
  protected String processRelational(String name, MetaField metaField) {

    if (metaField == null) {
      return name;
    }

    String relationship = metaField.getRelationship();
    if (relationship == null) {
      return name;
    }

    String nameColumn = getNameColumn(name, metaField);
    if (!relationship.equals("ManyToOne")) {
      String[] names = nameColumn.split("\\.");
      nameColumn = name + " : {item | $item." + names[1] + "$ }";
    }

    return nameColumn;
  }

  /**
   * Get nameColumn for given relational metaField.
   *
   * @param name Field name
   * @param metaField MetaField to check.
   * @return Name column of field.
   */
  protected String getNameColumn(String name, MetaField metaField) {

    String refModel = getRefModel(metaField.getTypeName());
    try {
      Mapper mapper = Mapper.of(Class.forName(refModel));
      boolean nameField = false;
      boolean codeField = false;
      for (Property property : Arrays.asList(mapper.getProperties())) {
        if (property.isNameColumn()) {
          return name + "." + property.getName();
        } else if (property.getName().equals("name")) {
          nameField = true;
        } else if (property.getName().equals("code")) {
          codeField = true;
        }
      }
      if (nameField) {
        return name + ".name";
      }
      if (codeField) {
        return name + ".code";
      }
      return name + "." + "id";
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    return name;
  }

  /**
   * Method generate main html table of html template, from 'panels' and 'sidePanels' list created
   * during view xml processing.
   *
   * @param sidePanel Boolean to check which panel list to process.
   */
  protected void generateHtml(boolean sidePanel) {

    List<String[]> panelList = sidePanel ? sidePanels : panels;

    if (panelList.isEmpty()) {
      return;
    }

    template += "<td><table>";
    int totalSpan = 0;
    for (String[] field : panelList) {

      int colSpan = Integer.parseInt(field[0]);

      if (colSpan == 12) {
        if (totalSpan > 0) {
          totalSpan = 0;
          template += "</tr>";
        }

        template += "<tr>" + field[1] + "</tr>";
      } else {
        if (totalSpan == 0) {
          template += "<tr>";
        }
        template += field[1];
        totalSpan += colSpan;

        if (totalSpan == 12) {
          totalSpan = 0;
          template += "</tr>";
        }
      }
    }

    if (totalSpan > 0) {
      template += "</tr>";
    }

    template += "</table></td>";
  }

  /**
   * It returns title of MetaField and if title is empty it create human readable title from field
   * name.
   *
   * @param name Name of field.
   * @param metaField MetaField to process.
   * @return Title of field.
   */
  protected String getFieldTitle(String name, MetaField metaField) {

    log.debug("Meta field for name : {}, metaField: {}", name, metaField);

    if (metaField != null) {
      String title = metaField.getLabel();
      if (!Strings.isNullOrEmpty(title)) {
        return metaField.getLabel();
      }
    }

    return inflector.humanize(name);
  }

  /**
   * Method to get MetaField record from field name and model.
   *
   * @param name Field name to search.
   * @param modelName Model of field.
   * @return Return MetaField found.
   */
  protected MetaField getMetaField(String name, String modelName) {

    MetaField metaField =
        metaFieldRepo
            .all()
            .filter("self.name = ? AND self.metaModel.fullName = ?", name, modelName)
            .fetchOne();

    return metaField;
  }

  /**
   * Process panelRelated and update 'panels' or 'sidePanels' list with new table created for
   * panelRelated.
   *
   * @param panelRelated PanelRelated to process.
   * @param sidePanel Boolean to check if panelRelated is sidePanel.
   * @param colSpan Colspan to add in panel lists.
   */
  public void processPanelRelated(PanelRelated panelRelated, Boolean sidePanel, String colSpan) {

    String title = panelRelated.getTitle();
    String name = panelRelated.getName();
    MetaField metaField = getMetaField(name, model);

    if (Strings.isNullOrEmpty(title) && metaField != null) {
      title = metaField.getLabel();
    }

    if (Strings.isNullOrEmpty(title)) {
      title = inflector.humanize(name);
    }

    String field = "<td colSpan=\"2\"><h4>" + title + "</h4></td>";

    String refModel = getRefModel(metaField.getTypeName());

    String itemTable = createTable(panelRelated, refModel);

    if (sidePanel != null && sidePanel) {
      sidePanels.add(new String[] {"12", field});
      sidePanels.add(new String[] {"12", itemTable});
    } else {
      panels.add(new String[] {colSpan, field});
      panels.add(new String[] {colSpan, itemTable});
    }
  }

  /**
   * Process panelRelated to find right grid view of reference model. Grid view used to create html
   * table.
   *
   * @param panelRelated PanelRelated to use.
   * @param refModel Name of model refer by panelRelated.
   * @return Html table string created.
   */
  protected String createTable(PanelRelated panelRelated, String refModel) {

    List<AbstractWidget> items = panelRelated.getItems();
    if (items != null && !items.isEmpty()) {
      return getHtmlTable(panelRelated.getName(), items, refModel);
    }

    MetaView gridView = findGridView(panelRelated.getGridView(), refModel);

    if (gridView != null) {
      try {
        ObjectViews views = XMLViews.fromXML(gridView.getXml());

        GridView grid = (GridView) views.getViews().get(0);

        return getHtmlTable(panelRelated.getName(), grid.getItems(), refModel);

      } catch (JAXBException e) {
        e.printStackTrace();
      }
    }

    return "";
  }

  /**
   * Create html table string from list of widgets(Items of grd view).
   *
   * @param fieldName Name of relational field.
   * @param widgets List of widgets in reference model's grid view.
   * @param refModel Name of reference model.
   * @return Html table created.
   */
  protected String getHtmlTable(String fieldName, List<AbstractWidget> widgets, String refModel) {

    String header = "";
    String row = "";

    for (AbstractWidget widget : widgets) {

      if (widget instanceof Field) {

        Field field = (Field) widget;
        String name = field.getName();
        String title = field.getTitle();
        MetaField metaField = getMetaField(name, refModel);
        if (Strings.isNullOrEmpty(title)) {
          title = getFieldTitle(name, metaField);
        }
        name = processRelational(name, metaField);

        header += "<th>" + title + "</th>";
        row += "<td>$" + fieldName + "." + name + "$</td>";
      }
    }

    String table =
        "<td colSpan=\"2\"><table class=\"table table-bordered table-header\">"
            + "<tr>"
            + header
            + "</tr>"
            + "<tr>"
            + row
            + "</tr>"
            + "</table></td>";

    return table;
  }

  /**
   * Find MetaView record from given gridName and reference model.
   *
   * @param gridName Name of grid view.
   * @param refModel Model of grid view.
   * @return MetaView record searched.
   */
  protected MetaView findGridView(String gridName, String refModel) {

    MetaView gridView = null;

    if (gridName != null) {
      gridView = metaViewRepo.findByName(gridName);
    }

    if (gridView == null) {
      gridView =
          metaViewRepo.all().filter("self.type = 'grid' and self.model = ?", refModel).fetchOne();
    }

    return gridView;
  }

  /**
   * Get fullName of reference model.
   *
   * @param refModel Name of reference model.
   * @return FullName of Model
   */
  protected String getRefModel(String refModel) {

    MetaModel metaModel = metaModelRepo.findByName(refModel);
    if (metaModel != null) {
      refModel = metaModel.getFullName();
    }

    return refModel;
  }
}
