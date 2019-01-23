/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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

import com.axelor.common.Inflector;
import java.util.Arrays;
import java.util.List;

public class CommonService {

  public static final String[] HEADERS =
      new String[] {
        "Note",
        "View",
        "Type",
        "Name",
        "Position",
        "Title",
        "Title FR",
        "Selection",
        "Selection FR",
        "Menu",
        "Menu FR",
        "Context Field",
        "Context Field target",
        "context Field target name",
        "context Field title",
        "Context Field value",
        "Domain",
        "Enum Type",
        "Sequence",
        "Form View",
        "Grid View",
        "Help",
        "Hidden",
        "If Config",
        "Include If",
        "Max Size",
        "Min Size",
        "Name Field",
        "On change",
        "On click",
        "Formula",
        "Regex",
        "Readonly",
        "Required",
        "Show If",
        "Roles",
        "Precision",
        "Scale",
        "Value Expression",
        "Visible In Grid",
        "Widget",
        "Widget Attrs"
      };

  public static final String NOTE = "Note";
  public static final String VIEW = "View";
  public static final String TYPE = "Type";
  public static final String NAME = "Name";
  public static final String POSITION = "Position";
  public static final String TITLE = "Title";
  public static final String TITLE_FR = "Title FR";
  public static final String SELECT = "Selection";
  public static final String SELECT_FR = "Selection FR";
  public static final String MENU = "Menu";
  public static final String MENU_FR = "Menu FR";
  public static final String CONTEXT_FIELD = "Context Field";
  public static final String CONTEXT_FIELD_TARGET = "Context Field target";
  public static final String CONTEXT_FIELD_TARGET_NAME = "context Field target name";
  public static final String CONTEXT_FIELD_TITLE = "context Field title";
  public static final String CONTEXT_FIELD_VALUE = "Context Field value";
  public static final String DOMAIN = "Domain";
  public static final String ENUM_TYPE = "Enum Type";
  public static final String SEQUENCE = "Sequence";
  public static final String FORM_VIEW = "Form View";
  public static final String GRID_VIEW = "Grid View";
  public static final String HELP = "Help";
  public static final String HIDDEN = "Hidden";
  public static final String IF_CONFIG = "If Config";
  public static final String INCLUDE_IF = "Include If";
  public static final String MAX_SIZE = "Max Size";
  public static final String MIN_SIZE = "Min Size";
  public static final String NAME_FIELD = "Name Field";
  public static final String ON_CHANGE = "On change";
  public static final String ON_CLICK = "On click";
  public static final String FORMULA = "Formula";
  public static final String REGEX = "Regex";
  public static final String READONLY = "Readonly";
  public static final String REQUIRED = "Required";
  public static final String SHOW_IF = "Show If";
  public static final String ROLES = "Roles";
  public static final String PRECISION = "Precision";
  public static final String SCALE = "Scale";
  public static final String VALUE_EXPR = "Value Expression";
  public static final String VISIBLE_IN_GRID = "Visible In Grid";
  public static final String WIDGET = "Widget";
  public static final String WIDGET_ATTRS = "Widget Attrs";

  public static final List<String> RELATIONAL_JSON_FIELD_TYPES =
      Arrays.asList(new String[] {"json-many-to-one", "json-one-to-many", "json-many-to-many"});

  public static final List<String> RELATIONAL_FIELD_TYPES =
      Arrays.asList(new String[] {"many-to-one", "one-to-many", "many-to-many"});

  public final Inflector inflector = Inflector.getInstance();

  /**
   * Method to create field name from title if name of field is blank. It will simplify title and
   * make standard field name from it.
   *
   * @param title Title string to process.
   * @return Name created from title.
   */
  public String getFieldName(String title) {

    title = title.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("^[0-9]+", "");

    return inflector.camelize(inflector.simplify(title.trim()), true);
  }

  /**
   * Method create default view name from given modelName and viewType.
   *
   * @param modelName Model name.
   * @param viewType Type of view
   * @return View name.
   */
  public static String getDefaultViewName(String modelName, String viewType) {

    if (modelName.contains(".")) {
      String[] model = modelName.split("\\.");
      modelName = model[model.length - 1];
    }

    modelName =
        modelName
            .trim()
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
            .replaceAll("([a-z\\d])([A-Z])", "$1-$2")
            .toLowerCase();

    return modelName + "-" + viewType;
  }
}
