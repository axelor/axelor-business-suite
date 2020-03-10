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
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.MetaJsonRecord;
import com.axelor.studio.db.ActionBuilder;
import com.axelor.studio.db.ActionBuilderLine;
import com.axelor.studio.db.repo.ActionBuilderLineRepository;
import com.axelor.studio.db.repo.ActionBuilderRepository;
import com.axelor.studio.service.StudioMetaService;
import com.axelor.studio.service.filter.FilterSqlService;
import com.axelor.studio.service.wkf.WkfTrackingService;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActionScriptBuilderService {

  private static final String INDENT = "\t";

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Inflector inflector = Inflector.getInstance();

  private List<StringBuilder> fbuilder = null;

  private int varCount = 0;

  private boolean isCreate = false;

  @Inject private ActionBuilderLineRepository builderLineRepo;

  @Inject private StudioMetaService metaService;

  @Inject private FilterSqlService filterSqlService;

  public MetaAction build(ActionBuilder builder) {

    String name = builder.getName();
    String code = null;
    String lang = "js";
    String transactional = "true";

    if (builder.getTypeSelect() == ActionBuilderRepository.TYPE_SELECT_SCRIPT) {
      code = "\n" + builder.getScriptText();
      if (builder.getScriptType() == 1) {
        lang = "groovy";
      }
      if (builder.getTransactional()) {
        transactional = "false";
      }
    } else {
      code = generateScriptCode(builder);
    }

    String xml =
        "<action-script name=\""
            + name
            + "\" "
            + "id=\"studio-"
            + name
            + "\" model=\""
            + MetaJsonRecord.class.getName()
            + "\">\n\t"
            + "<script language=\""
            + lang
            + "\" transactional=\""
            + transactional
            + "\">\n\t<![CDATA["
            + code
            + "\n\t]]>\n\t</script>\n</action-script>";

    return metaService.updateMetaAction(builder.getName(), "action-script", xml, null);
  }

  protected String generateScriptCode(ActionBuilder builder) {

    StringBuilder stb = new StringBuilder();
    fbuilder = new ArrayList<>();
    varCount = 1;
    int level = 1;

    stb.append(format("var ctx = $request.context;", level));
    String targetModel;
    if (builder.getTypeSelect() == ActionBuilderRepository.TYPE_SELECT_CREATE) {
      targetModel = builder.getTargetModel();
      isCreate = true;
      addCreateCode(builder.getIsJson(), stb, level, targetModel);
      if (builder.getOpenRecord()) {
        addOpenRecord(builder.getIsJson(), stb, level, targetModel);
      }
      if (!Strings.isNullOrEmpty(builder.getDisplayMsg())) {
        stb.append("\n");
        stb.append(format("$response.setFlash('" + builder.getDisplayMsg() + "')", level));
      }
    } else {
      targetModel = builder.getModel();
      isCreate = false;
      addUpdateCode(builder.getIsJson(), stb, level, targetModel);
    }

    stb.append("\n");

    addRootFunction(builder, stb, level);

    stb.append(Joiner.on("").join(fbuilder));

    return stb.toString();
  }

  protected void addCreateCode(boolean isJson, StringBuilder stb, int level, String targetModel) {

    if (isJson) {
      stb.append(format("var target = $json.create('" + targetModel + "');", level));
      stb.append(format("target = setVar0(null, ctx, {});", level));
      stb.append(format("target = $json.save(target);", level));
      stb.append(
          format(
              "Beans.get(" + WkfTrackingService.class.getName() + ".class).track(target);", level));
    } else {
      stb.append(format("var target = new " + targetModel + "();", level));
      stb.append(format("target = setVar0(null, ctx, {});", level));
      stb.append(format("$em.persist(target);", level));
    }
  }

  protected void addOpenRecord(boolean isJson, StringBuilder stb, int level, String targetModel) {

    stb.append("\n");

    if (isJson) {
      String title = inflector.humanize(targetModel);
      stb.append(
          format(
              "$response.setView(com.axelor.meta.schema.actions.ActionView.define('" + title + "')",
              level));
      stb.append(format(".model('com.axelor.meta.db.MetaJsonRecord')", level + 1));
      stb.append(format(".add('grid','custom-model-" + targetModel + "-grid')", level + 1));
      stb.append(format(".add('form','custom-model-" + targetModel + "-form')", level + 1));
      stb.append(format(".domain('self.jsonModel = :jsonModel')", level + 1));
      stb.append(format(".context('jsonModel', '" + targetModel + "')", level + 1));
      stb.append(format(".context('_showRecord', target.id)", level + 1));
      stb.append(format(".map())", level + 1));
    } else {
      String title = inflector.humanize(targetModel.substring(targetModel.lastIndexOf('.') + 1));
      stb.append(
          format(
              "$response.setView(com.axelor.meta.schema.actions.ActionView.define('" + title + "')",
              level));
      stb.append(format(".model('" + targetModel + "')", level + 1));
      stb.append(format(".add('grid')", level + 1));
      stb.append(format(".add('form')", level + 1));
      stb.append(format(".context('_showRecord', target.id)", level + 1));
      stb.append(format(".map())", level + 1));
    }
  }

  protected void addUpdateCode(boolean isJson, StringBuilder stb, int level, String targetModel) {

    if (isJson) {
      stb.append(format("var target = {};", level));
    } else {
      stb.append(format("var target = ctx.asType(" + targetModel + ".class)", level));
    }

    stb.append(format("target = setVar0(null, ctx, {});", level));
    stb.append(format("$response.setValues(target);", level));
  }

  protected void addRootFunction(ActionBuilder builder, StringBuilder stb, int level) {

    stb.append(format("function setVar0($$, $, _$){", level));
    String bindings = addFieldsBinding("target", builder.getLines(), level + 1);
    stb.append(bindings);
    stb.append(format("return target;", level + 1));
    stb.append(format("}", level));
  }

  protected String format(String line, int level) {

    return "\n" + Strings.repeat(INDENT, level) + line;
  }

  protected String addFieldsBinding(String target, List<ActionBuilderLine> lines, int level) {

    StringBuilder stb = new StringBuilder();

    lines.sort(
        (l1, l2) -> {
          if (l1.getDummy() && !l2.getDummy()) {
            return -1;
          }
          if (!l1.getDummy() && l2.getDummy()) {
            return 1;
          }
          return 0;
        });

    for (ActionBuilderLine line : lines) {

      String name = line.getName();
      String value = line.getValue();
      if (value != null && value.contains(".sum(")) {
        value = getSum(value, line.getFilter());
      }
      if (line.getDummy()) {
        stb.append(format("_$." + name + " = " + value + ";", level));
        continue;
      }

      MetaJsonField jsonField = line.getMetaJsonField();
      MetaField metaField = line.getMetaField();

      if (jsonField != null
          && (jsonField.getTargetJsonModel() != null || jsonField.getTargetModel() != null)) {
        value = addRelationalBinding(line, target, true);
      } else if (metaField != null && metaField.getRelationship() != null) {
        value = addRelationalBinding(line, target, false);
      }
      // else {
      // MetaJsonField valueJson = line.getValueJson();
      // if (valueJson != null && valueJson.getType().contentEquals("many-to-one")) {
      // value = value.replace("$." + valueJson.getName(),"$json.create($json.find($."
      // +
      // valueJson.getName() + ".id))");
      // }
      // }

      if (value != null
          && metaField != null
          && metaField.getTypeName().equals(BigDecimal.class.getSimpleName())) {
        value = "new BigDecimal(" + value + ")";
      }

      String condition = line.getConditionText();
      if (condition != null) {
        stb.append(
            format("if(" + condition + "){" + target + "." + name + " = " + value + ";}", level));
      } else {
        stb.append(format(target + "." + name + " = " + value + ";", level));
      }
    }

    return stb.toString();
  }

  protected String addRelationalBinding(ActionBuilderLine line, String target, boolean json) {

    line = builderLineRepo.find(line.getId());
    String subCode = null;

    String type =
        json
            ? line.getMetaJsonField().getType()
            : inflector.dasherize(line.getMetaField().getRelationship());

    switch (type) {
      case "many-to-one":
        subCode = addM2OBinding(line, true, true);
        break;
      case "many-to-many":
        subCode = addM2MBinding(line);
        break;
      case "one-to-many":
        subCode = addO2MBinding(line, target);
        break;
      case "one-to-one":
        subCode = addM2OBinding(line, true, true);
        break;
      case "json-many-to-one":
        subCode = addJsonM2OBinding(line, true, true);
        break;
      case "json-many-to-many":
        subCode = addJsonM2MBinding(line);
        break;
      case "json-one-to-many":
        subCode = addJsonO2MBinding(line);
        break;
      default:
        throw new IllegalArgumentException("Unknown type");
    }

    return subCode + "($," + line.getValue() + ", _$)";
  }

  protected String getTargetModel(ActionBuilderLine line) {

    MetaJsonField jsonField = line.getMetaJsonField();

    String targetModel = "";
    if (jsonField != null && jsonField.getTargetModel() != null) {
      targetModel = jsonField.getTargetModel();
    }

    MetaField field = line.getMetaField();
    if (field != null && field.getTypeName() != null) {
      targetModel = field.getTypeName();
    }

    return targetModel;
  }

  protected String getTargetJsonModel(ActionBuilderLine line) {

    MetaJsonField jsonField = line.getMetaJsonField();

    if (jsonField != null) {
      return jsonField.getTargetJsonModel().getName();
    }

    return "";
  }

  protected String getRootSourceModel(ActionBuilderLine line) {

    if (line.getActionBuilder() != null) {
      return line.getActionBuilder().getModel();
    }

    return null;
  }

  protected String getSourceModel(ActionBuilderLine line) {

    MetaJsonField jsonField = line.getValueJson();

    String sourceModel = null;
    Object targetObject = null;

    try {
      if (jsonField != null && jsonField.getTargetModel() != null) {
        if (line.getValue() != null && !line.getValue().contentEquals("$." + jsonField.getName())) {
          targetObject =
              filterSqlService.parseJsonField(
                  jsonField, line.getValue().replace("$.", ""), null, null);
        } else {
          sourceModel = jsonField.getTargetModel();
        }
      }

      MetaField field = line.getValueField();
      if (field != null && field.getTypeName() != null) {
        if (line.getValue() != null && !line.getValue().contentEquals("$." + field.getName())) {
          targetObject =
              filterSqlService.parseMetaField(
                  field, line.getValue().replace("$.", ""), null, null, false);
        } else {
          sourceModel = field.getTypeName();
        }
      }
    } catch (AxelorException e) {
      TraceBackService.trace(e);
    }

    if (sourceModel == null && line.getValue() != null && line.getValue().equals("$")) {
      sourceModel = getRootSourceModel(line);
    }

    if (sourceModel == null && line.getValue() != null && line.getValue().equals("$$")) {
      sourceModel = getRootSourceModel(line);
    }

    if (targetObject != null) {
      if (targetObject instanceof MetaJsonField) {
        sourceModel = ((MetaJsonField) targetObject).getTargetModel();
      } else if (targetObject instanceof MetaField) {
        sourceModel = ((MetaField) targetObject).getTypeName();
      }
    }

    return sourceModel;
  }

  protected String addM2OBinding(ActionBuilderLine line, boolean search, boolean filter) {

    String fname = "setVar" + varCount;
    varCount += 1;

    String tModel = getTargetModel(line);
    String srcModel = getSourceModel(line);

    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    if (tModel.contains(".")) {
      tModel = tModel.substring(tModel.lastIndexOf('.') + 1);
    }
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val = null;", 2));
    if (srcModel != null) {
      stb.append(format("if ($ != null && $.id != null){", 2));
      srcModel = srcModel.substring(srcModel.lastIndexOf('.') + 1);
      stb.append(format("$ = $em.find(" + srcModel + ".class, $.id);", 3));
      log.debug("src model: {}, Target model: {}", srcModel, tModel);
      if (srcModel.contentEquals(tModel)) {
        stb.append(format("val = $", 3));
      }
      stb.append(format("}", 2));
    }

    if (filter && line.getFilter() != null) {
      if (line.getValue() != null) {
        stb.append(format("var map = com.axelor.db.mapper.Mapper.toMap($);", 2));
      } else {
        stb.append(format("var map = com.axelor.db.mapper.Mapper.toMap($$);", 2));
      }
      stb.append(format("val = " + getQuery(tModel, line.getFilter(), false, false), 2));
    }

    List<ActionBuilderLine> lines = line.getSubLines();
    if (lines != null && !lines.isEmpty()) {
      stb.append(format("if (!val) {", 2));
      stb.append(format("val = new " + tModel + "();", 3));
      stb.append(format("}", 2));
      stb.append(addFieldsBinding("val", lines, 2));
      // stb.append(format("$em.persist(val);", 2));
    }
    stb.append(format("return val;", 2));
    stb.append(format("}", 1));

    return fname;
  }

  protected String addM2MBinding(ActionBuilderLine line) {

    String fname = "setVar" + varCount;
    varCount += 1;
    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val  = new HashSet();", 2));
    if (line.getFilter() != null) {
      String model = getTargetModel(line);
      stb.append(format("var map = com.axelor.db.mapper.Mapper.toMap($$);", 2));
      stb.append(format("val.addAll(" + getQuery(model, line.getFilter(), false, true) + ");", 2));
      stb.append(format("if(!val.empty){return val;}", 2));
    }

    stb.append(format("if(!$){return val;}", 2));
    stb.append(format("$.forEach(function(v){", 2));
    stb.append(format("v = " + addM2OBinding(line, true, false) + "($$, v, _$);", 3));
    stb.append(format("val.add(v);", 3));
    stb.append(format("})", 2));
    stb.append(format("return val;", 2));
    stb.append(format("}", 1));

    return fname;
  }

  protected String addO2MBinding(ActionBuilderLine line, String target) {

    String fname = "setVar" + varCount;
    varCount += 1;
    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val  = new ArrayList();", 2));
    stb.append(format("if(!$){return val;}", 2));
    stb.append(format("$.forEach(function(v){", 2));
    stb.append(format("var item = " + addM2OBinding(line, false, false) + "($$, v, _$);", 3));
    if (isCreate && line.getMetaField() != null && line.getMetaField().getMappedBy() != null) {
      stb.append(format("item." + line.getMetaField().getMappedBy() + " = " + target, 3));
    }
    stb.append(format("val.add(item);", 3));
    stb.append(format("})", 2));
    stb.append(format("return val;", 2));
    stb.append(format("}", 1));

    return fname;
  }

  protected String addJsonM2OBinding(ActionBuilderLine line, boolean search, boolean filter) {

    String fname = "setVar" + varCount;
    varCount += 1;

    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    String model = getTargetJsonModel(line);
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val = null;", 2));
    // stb.append(format("if ($ != null && $.id != null){", 2));
    // stb.append(format("$ = $json.find($.id);", 3));
    if (search) {
      stb.append(format("if ($ != null && $.id != null) {", 2));
      stb.append(format("val = $json.find($.id);", 3));
      stb.append(format("if (val.jsonModel != '" + model + "'){val = null;} ", 3));
      stb.append(format("}", 2));
    }
    // stb.append(format("}",2));
    if (filter && line.getFilter() != null) {
      String query = getQuery(model, line.getFilter(), true, false);
      stb.append(format("val = " + query, 2));
    }
    List<ActionBuilderLine> lines = line.getSubLines();
    if (lines != null && !lines.isEmpty()) {
      stb.append(format("if (!val) {", 2));
      stb.append(format("val = $json.create('" + model + "');", 3));
      stb.append(format("}", 2));
      // stb.append(format("if($ instanceof MetaJsonRecord){$ =
      // $json.create($json.find($.id))};",
      // 2));
      stb.append(addFieldsBinding("val", lines, 2));
      stb.append(format("val = $json.save(val);", 2));
    }
    stb.append(format("return val;", 2));
    stb.append(format("}", 1));

    return fname;
  }

  protected String addJsonM2MBinding(ActionBuilderLine line) {

    String fname = "setVar" + varCount;
    varCount += 1;
    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val  = new HashSet();", 2));
    if (line.getFilter() != null) {
      String model = getTargetJsonModel(line);
      stb.append(format("val.addAll(" + getQuery(model, line.getFilter(), true, true) + ");", 2));
      stb.append(format("if(!val.empty){return val;}", 2));
    }
    stb.append(format("if(!$){return val;}", 2));
    stb.append(format("$.forEach(function(v){", 2));
    stb.append(format("v = " + addJsonM2OBinding(line, true, false) + "($$, v, _$);", 3));
    stb.append(format("val.add(v);", 3));
    stb.append(format("})", 2));
    stb.append(format("return val;", 2));
    stb.append(format("}", 1));

    return fname;
  }

  protected String addJsonO2MBinding(ActionBuilderLine line) {

    String fname = "setVar" + varCount;
    varCount += 1;
    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val  = new ArrayList();", 2));
    stb.append(format("if(!$){return val;}", 2));
    stb.append(format("$.forEach(function(v){", 2));
    stb.append(format("v = " + addJsonM2OBinding(line, false, false) + "($$, v, _$);", 3));
    stb.append(format("val.add(v);", 3));
    stb.append(format("})", 2));
    stb.append(format("return val;", 2));
    stb.append(format("}", 1));

    return fname;
  }

  protected String getQuery(String model, String filter, boolean json, boolean all) {

    if (model.contains(".")) {
      model = model.substring(model.lastIndexOf('.') + 1);
    }

    String nRecords = "fetchOne()";
    if (all) {
      nRecords = "fetch()";
    }

    String query = null;

    if (json) {
      query = "$json.all('" + model + "').by(" + filter + ")." + nRecords;
    } else {
      query =
          "__repo__("
              + model
              + ".class).all().filter(\""
              + filter
              + "\").bind(map).bind(_$)."
              + nRecords;
    }

    return query;
  }

  protected String getSum(String value, String filter) {

    value = value.substring(0, value.length() - 1);
    String[] expr = value.split("\\.sum\\(");

    String fname = "setVar" + varCount;
    varCount += 1;

    StringBuilder stb = new StringBuilder();
    stb.append(format("", 1));
    stb.append(format("function " + fname + "(sumOf$, $$, filter){", 1));
    stb.append(format("var val  = 0", 2));
    stb.append(format("if (sumOf$ == null){ return val;}", 2));
    stb.append(format("sumOf$.forEach(function($){", 2));
    // stb.append(format("if ($ instanceof MetaJsonRecord){ $ =
    // $json.create($json.find($.id)); }",
    // 3));
    String val = "val += " + expr[1] + ";";
    if (filter != null) {
      val = "if(filter){" + val + "}";
    }
    stb.append(format(val, 3));
    stb.append(format("})", 2));
    stb.append(format("return new BigDecimal(val);", 2));
    stb.append(format("}", 1));

    fbuilder.add(stb);
    return fname + "(" + expr[0] + ",$," + filter + ")";
  }
}
