package com.axelor.apps.sale.xml.adapters;

import com.axelor.apps.sale.db.ConfiguratorProductFormula;
import com.axelor.apps.sale.xml.models.AdaptedConfiguratorProductFormula;
import com.axelor.inject.Beans;
import com.axelor.meta.db.repo.MetaFieldRepository;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class ConfiguratorProductFormulaXmlAdapter
    extends XmlAdapter<AdaptedConfiguratorProductFormula, ConfiguratorProductFormula> {

  public ConfiguratorProductFormulaXmlAdapter() {}

  @Override
  public AdaptedConfiguratorProductFormula marshal(
      ConfiguratorProductFormula configuratorProductFormula) throws Exception {
    AdaptedConfiguratorProductFormula adaptedConfiguratorProductFormula =
        new AdaptedConfiguratorProductFormula();
    adaptedConfiguratorProductFormula.setFormula(configuratorProductFormula.getFormula());
    adaptedConfiguratorProductFormula.setMetaFieldName(
        configuratorProductFormula.getMetaField().getName());
    adaptedConfiguratorProductFormula.setShowOnConfigurator(
        configuratorProductFormula.getShowOnConfigurator());
    if (configuratorProductFormula.getProductCreator() != null) {
      adaptedConfiguratorProductFormula.setConfiguratorCreatorImportId(
          configuratorProductFormula.getProductCreator().getId());
    }

    return adaptedConfiguratorProductFormula;
  }

  @Override
  public ConfiguratorProductFormula unmarshal(
      AdaptedConfiguratorProductFormula adaptedConfiguratorProductFormula) throws Exception {
    ConfiguratorProductFormula configuratorProductFormula = new ConfiguratorProductFormula();
    configuratorProductFormula.setFormula(adaptedConfiguratorProductFormula.getFormula());
    configuratorProductFormula.setMetaField(
        Beans.get(MetaFieldRepository.class)
            .findByName(adaptedConfiguratorProductFormula.getMetaFieldName()));
    configuratorProductFormula.setShowOnConfigurator(
        adaptedConfiguratorProductFormula.getShowOnConfigurator());
    return configuratorProductFormula;
  }
}
