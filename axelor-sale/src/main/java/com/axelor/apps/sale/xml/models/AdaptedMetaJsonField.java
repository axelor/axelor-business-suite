package com.axelor.apps.sale.xml.models;

import com.axelor.apps.base.xml.models.ExportedModel;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class AdaptedMetaJsonField extends ExportedModel {

  private String name;

  private String title;

  private String type;

  private String defaultValue;

  private String model;

  private String modelField;

  private String jsonModelCode;

  private String selection;

  private String widget;

  private String help;

  private String showIf;

  private String hideIf;

  private String requiredIf;

  private String readonlyIf;

  private String includeIf;

  private String contextField;

  private String contextFieldTarget;

  private String contextFieldTargetName;

  private String contextFieldValue;

  private String contextFieldTitle;

  private Boolean hidden = Boolean.FALSE;

  private Boolean required = Boolean.FALSE;

  private Boolean readonly = Boolean.FALSE;

  private Boolean nameField = Boolean.FALSE;

  private Boolean visibleInGrid = Boolean.FALSE;

  private Integer minSize = 0;

  private Integer maxSize = 0;

  private Integer precision = 6;

  private Integer scale = 2;

  private Integer sequence = 0;

  private Integer columnSequence = 0;

  private String regex;

  private String valueExpr;

  private String targetModel;

  private String enumType;

  private String formView;

  private String gridView;

  private String domain;

  private String targetJsonModel;

  private String onChange;

  private String onClick;

  private String widgetAttrs;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getModelField() {
    return modelField;
  }

  public void setModelField(String modelField) {
    this.modelField = modelField;
  }

  public String getJsonModelCode() {
    return jsonModelCode;
  }

  public void setJsonModelCode(String jsonModelCode) {
    this.jsonModelCode = jsonModelCode;
  }

  public String getSelection() {
    return selection;
  }

  public void setSelection(String selection) {
    this.selection = selection;
  }

  public String getWidget() {
    return widget;
  }

  public void setWidget(String widget) {
    this.widget = widget;
  }

  public String getHelp() {
    return help;
  }

  public void setHelp(String help) {
    this.help = help;
  }

  public String getShowIf() {
    return showIf;
  }

  public void setShowIf(String showIf) {
    this.showIf = showIf;
  }

  public String getHideIf() {
    return hideIf;
  }

  public void setHideIf(String hideIf) {
    this.hideIf = hideIf;
  }

  public String getRequiredIf() {
    return requiredIf;
  }

  public void setRequiredIf(String requiredIf) {
    this.requiredIf = requiredIf;
  }

  public String getReadonlyIf() {
    return readonlyIf;
  }

  public void setReadonlyIf(String readonlyIf) {
    this.readonlyIf = readonlyIf;
  }

  public String getIncludeIf() {
    return includeIf;
  }

  public void setIncludeIf(String includeIf) {
    this.includeIf = includeIf;
  }

  public String getContextField() {
    return contextField;
  }

  public void setContextField(String contextField) {
    this.contextField = contextField;
  }

  public String getContextFieldTarget() {
    return contextFieldTarget;
  }

  public void setContextFieldTarget(String contextFieldTarget) {
    this.contextFieldTarget = contextFieldTarget;
  }

  public String getContextFieldTargetName() {
    return contextFieldTargetName;
  }

  public void setContextFieldTargetName(String contextFieldTargetName) {
    this.contextFieldTargetName = contextFieldTargetName;
  }

  public String getContextFieldValue() {
    return contextFieldValue;
  }

  public void setContextFieldValue(String contextFieldValue) {
    this.contextFieldValue = contextFieldValue;
  }

  public String getContextFieldTitle() {
    return contextFieldTitle;
  }

  public void setContextFieldTitle(String contextFieldTitle) {
    this.contextFieldTitle = contextFieldTitle;
  }

  public Boolean getHidden() {
    return hidden;
  }

  public void setHidden(Boolean hidden) {
    this.hidden = hidden;
  }

  public Boolean getRequired() {
    return required;
  }

  public void setRequired(Boolean required) {
    this.required = required;
  }

  public Boolean getReadonly() {
    return readonly;
  }

  public void setReadonly(Boolean readonly) {
    this.readonly = readonly;
  }

  public Boolean getNameField() {
    return nameField;
  }

  public void setNameField(Boolean nameField) {
    this.nameField = nameField;
  }

  public Boolean getVisibleInGrid() {
    return visibleInGrid;
  }

  public void setVisibleInGrid(Boolean visibleInGrid) {
    this.visibleInGrid = visibleInGrid;
  }

  public Integer getMinSize() {
    return minSize;
  }

  public void setMinSize(Integer minSize) {
    this.minSize = minSize;
  }

  public Integer getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(Integer maxSize) {
    this.maxSize = maxSize;
  }

  public Integer getPrecision() {
    return precision;
  }

  public void setPrecision(Integer precision) {
    this.precision = precision;
  }

  public Integer getScale() {
    return scale;
  }

  public void setScale(Integer scale) {
    this.scale = scale;
  }

  public Integer getSequence() {
    return sequence;
  }

  public void setSequence(Integer sequence) {
    this.sequence = sequence;
  }

  public Integer getColumnSequence() {
    return columnSequence;
  }

  public void setColumnSequence(Integer columnSequence) {
    this.columnSequence = columnSequence;
  }

  public String getRegex() {
    return regex;
  }

  public void setRegex(String regex) {
    this.regex = regex;
  }

  public String getValueExpr() {
    return valueExpr;
  }

  public void setValueExpr(String valueExpr) {
    this.valueExpr = valueExpr;
  }

  public String getTargetModel() {
    return targetModel;
  }

  public void setTargetModel(String targetModel) {
    this.targetModel = targetModel;
  }

  public String getEnumType() {
    return enumType;
  }

  public void setEnumType(String enumType) {
    this.enumType = enumType;
  }

  public String getFormView() {
    return formView;
  }

  public void setFormView(String formView) {
    this.formView = formView;
  }

  public String getGridView() {
    return gridView;
  }

  public void setGridView(String gridView) {
    this.gridView = gridView;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public String getTargetJsonModel() {
    return targetJsonModel;
  }

  public void setTargetJsonModel(String targetJsonModel) {
    this.targetJsonModel = targetJsonModel;
  }

  public String getOnChange() {
    return onChange;
  }

  public void setOnChange(String onChange) {
    this.onChange = onChange;
  }

  public String getOnClick() {
    return onClick;
  }

  public void setOnClick(String onClick) {
    this.onClick = onClick;
  }

  public String getWidgetAttrs() {
    return widgetAttrs;
  }

  public void setWidgetAttrs(String widgetAttrs) {
    this.widgetAttrs = widgetAttrs;
  }
}
