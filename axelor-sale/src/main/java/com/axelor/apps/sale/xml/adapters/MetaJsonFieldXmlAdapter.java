package com.axelor.apps.sale.xml.adapters;

import com.axelor.apps.sale.xml.models.AdaptedMetaJsonField;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.repo.MetaJsonModelRepository;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class MetaJsonFieldXmlAdapter extends XmlAdapter<AdaptedMetaJsonField, MetaJsonField> {

  @Override
  public AdaptedMetaJsonField marshal(MetaJsonField metaJsonField) throws Exception {
    AdaptedMetaJsonField adaptedMetaJsonField = new AdaptedMetaJsonField();

    adaptedMetaJsonField.setName(metaJsonField.getName());
    adaptedMetaJsonField.setTitle(metaJsonField.getTitle());
    adaptedMetaJsonField.setType(metaJsonField.getType());
    adaptedMetaJsonField.setDefaultValue(metaJsonField.getDefaultValue());
    adaptedMetaJsonField.setModel(metaJsonField.getModel());
    adaptedMetaJsonField.setModelField(metaJsonField.getModelField());
    if (metaJsonField.getJsonModel() != null) {
      adaptedMetaJsonField.setJsonModelCode(metaJsonField.getJsonModel().getName());
    }

    adaptedMetaJsonField.setSelection(metaJsonField.getSelection());
    adaptedMetaJsonField.setWidget(metaJsonField.getWidget());
    adaptedMetaJsonField.setHelp(metaJsonField.getHelp());
    adaptedMetaJsonField.setShowIf(metaJsonField.getShowIf());
    adaptedMetaJsonField.setHideIf(metaJsonField.getHideIf());
    adaptedMetaJsonField.setRequiredIf(metaJsonField.getRequiredIf());
    adaptedMetaJsonField.setIncludeIf(metaJsonField.getIncludeIf());
    adaptedMetaJsonField.setContextField(metaJsonField.getContextField());
    adaptedMetaJsonField.setContextFieldTarget(metaJsonField.getContextFieldTarget());
    adaptedMetaJsonField.setContextFieldTargetName(metaJsonField.getContextFieldTargetName());
    adaptedMetaJsonField.setContextFieldValue(metaJsonField.getContextFieldValue());
    adaptedMetaJsonField.setContextFieldTitle(metaJsonField.getContextFieldTitle());
    adaptedMetaJsonField.setHidden(metaJsonField.getHidden());
    adaptedMetaJsonField.setRequired(metaJsonField.getRequired());
    adaptedMetaJsonField.setReadonly(metaJsonField.getReadonly());
    adaptedMetaJsonField.setNameField(metaJsonField.getNameField());
    adaptedMetaJsonField.setVisibleInGrid(metaJsonField.getVisibleInGrid());
    adaptedMetaJsonField.setMinSize(metaJsonField.getMinSize());
    adaptedMetaJsonField.setMaxSize(metaJsonField.getMaxSize());
    adaptedMetaJsonField.setPrecision(metaJsonField.getPrecision());
    adaptedMetaJsonField.setScale(metaJsonField.getScale());
    adaptedMetaJsonField.setSequence(metaJsonField.getSequence());
    adaptedMetaJsonField.setColumnSequence(metaJsonField.getColumnSequence());
    adaptedMetaJsonField.setRegex(metaJsonField.getRegex());
    adaptedMetaJsonField.setValueExpr(metaJsonField.getValueExpr());
    adaptedMetaJsonField.setTargetModel(metaJsonField.getTargetModel());
    adaptedMetaJsonField.setEnumType(metaJsonField.getEnumType());
    adaptedMetaJsonField.setFormView(metaJsonField.getFormView());
    adaptedMetaJsonField.setGridView(metaJsonField.getGridView());
    adaptedMetaJsonField.setDomain(metaJsonField.getDomain());

    return adaptedMetaJsonField;
  }

  @Override
  public MetaJsonField unmarshal(AdaptedMetaJsonField adaptedMetaJsonField) throws Exception {
    MetaJsonField metaJsonField = new MetaJsonField();

    metaJsonField.setName(adaptedMetaJsonField.getName());
    metaJsonField.setTitle(adaptedMetaJsonField.getTitle());
    metaJsonField.setType(adaptedMetaJsonField.getType());
    metaJsonField.setDefaultValue(adaptedMetaJsonField.getDefaultValue());
    metaJsonField.setModel(adaptedMetaJsonField.getModel());
    metaJsonField.setModelField(adaptedMetaJsonField.getModelField());
    metaJsonField.setJsonModel(
        Beans.get(MetaJsonModelRepository.class)
            .findByName(adaptedMetaJsonField.getJsonModelCode()));
    metaJsonField.setSelection(adaptedMetaJsonField.getSelection());
    metaJsonField.setWidget(adaptedMetaJsonField.getWidget());
    metaJsonField.setHelp(adaptedMetaJsonField.getHelp());
    metaJsonField.setShowIf(adaptedMetaJsonField.getShowIf());
    metaJsonField.setHideIf(adaptedMetaJsonField.getHideIf());
    metaJsonField.setRequiredIf(adaptedMetaJsonField.getRequiredIf());
    metaJsonField.setIncludeIf(adaptedMetaJsonField.getIncludeIf());
    metaJsonField.setContextField(adaptedMetaJsonField.getContextField());
    metaJsonField.setContextFieldTarget(adaptedMetaJsonField.getContextFieldTarget());
    metaJsonField.setContextFieldTargetName(adaptedMetaJsonField.getContextFieldTargetName());
    metaJsonField.setContextFieldValue(adaptedMetaJsonField.getContextFieldValue());
    metaJsonField.setContextFieldTitle(adaptedMetaJsonField.getContextFieldTitle());
    metaJsonField.setHidden(adaptedMetaJsonField.getHidden());
    metaJsonField.setRequired(adaptedMetaJsonField.getRequired());
    metaJsonField.setReadonly(adaptedMetaJsonField.getReadonly());
    metaJsonField.setNameField(adaptedMetaJsonField.getNameField());
    metaJsonField.setVisibleInGrid(adaptedMetaJsonField.getVisibleInGrid());
    metaJsonField.setMinSize(adaptedMetaJsonField.getMinSize());
    metaJsonField.setMaxSize(adaptedMetaJsonField.getMaxSize());
    metaJsonField.setPrecision(adaptedMetaJsonField.getPrecision());
    metaJsonField.setScale(adaptedMetaJsonField.getScale());
    metaJsonField.setSequence(adaptedMetaJsonField.getSequence());
    metaJsonField.setColumnSequence(adaptedMetaJsonField.getColumnSequence());
    metaJsonField.setRegex(adaptedMetaJsonField.getRegex());
    metaJsonField.setValueExpr(adaptedMetaJsonField.getValueExpr());
    metaJsonField.setTargetModel(adaptedMetaJsonField.getTargetModel());
    metaJsonField.setEnumType(adaptedMetaJsonField.getEnumType());
    metaJsonField.setFormView(adaptedMetaJsonField.getFormView());
    metaJsonField.setGridView(adaptedMetaJsonField.getGridView());
    metaJsonField.setDomain(adaptedMetaJsonField.getDomain());
    return metaJsonField;
  }
}
