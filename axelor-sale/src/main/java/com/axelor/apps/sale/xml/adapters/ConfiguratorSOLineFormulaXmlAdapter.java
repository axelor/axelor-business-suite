package com.axelor.apps.sale.xml.adapters;

import com.axelor.apps.sale.db.ConfiguratorSOLineFormula;
import com.axelor.apps.sale.xml.models.AdaptedConfiguratorSOLineFormula;
import com.axelor.inject.Beans;
import com.axelor.meta.db.repo.MetaFieldRepository;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class ConfiguratorSOLineFormulaXmlAdapter
    extends XmlAdapter<AdaptedConfiguratorSOLineFormula, ConfiguratorSOLineFormula> {

  public ConfiguratorSOLineFormulaXmlAdapter() {}

  @Override
  public AdaptedConfiguratorSOLineFormula marshal(
      ConfiguratorSOLineFormula configuratorSOLineFormula) throws Exception {
    AdaptedConfiguratorSOLineFormula adaptedConfiguratorSOLineFormula =
        new AdaptedConfiguratorSOLineFormula();
    adaptedConfiguratorSOLineFormula.setFormula(configuratorSOLineFormula.getFormula());
    if (configuratorSOLineFormula.getMetaField() != null) {
      adaptedConfiguratorSOLineFormula.setMetaFieldName(
          configuratorSOLineFormula.getMetaField().getName());
    }
    adaptedConfiguratorSOLineFormula.setShowOnConfigurator(
        configuratorSOLineFormula.getShowOnConfigurator());
    adaptedConfiguratorSOLineFormula.setUpdateFromSelect(
        configuratorSOLineFormula.getUpdateFromSelect());
    if (configuratorSOLineFormula.getSoLineCreator() != null) {
      adaptedConfiguratorSOLineFormula.setConfiguratorCreatorImportId(
          configuratorSOLineFormula.getSoLineCreator().getId());
    }

    return adaptedConfiguratorSOLineFormula;
  }

  @Override
  public ConfiguratorSOLineFormula unmarshal(
      AdaptedConfiguratorSOLineFormula adaptedConfiguratorSOLineFormula) throws Exception {

    ConfiguratorSOLineFormula configuratorSOLineFormula = new ConfiguratorSOLineFormula();
    configuratorSOLineFormula.setFormula(adaptedConfiguratorSOLineFormula.getFormula());
    configuratorSOLineFormula.setMetaField(
        Beans.get(MetaFieldRepository.class)
            .findByName(adaptedConfiguratorSOLineFormula.getMetaFieldName()));
    configuratorSOLineFormula.setShowOnConfigurator(
        adaptedConfiguratorSOLineFormula.getShowOnConfigurator());
    configuratorSOLineFormula.setUpdateFromSelect(
        adaptedConfiguratorSOLineFormula.getUpdateFromSelect());

    return configuratorSOLineFormula;
  }
}
