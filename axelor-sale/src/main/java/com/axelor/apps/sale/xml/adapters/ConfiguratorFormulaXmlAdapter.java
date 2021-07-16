package com.axelor.apps.sale.xml.adapters;

import com.axelor.apps.sale.db.ConfiguratorFormula;
import com.axelor.apps.sale.xml.models.AdaptedConfiguratorFormula;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.google.inject.Inject;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class ConfiguratorFormulaXmlAdapter
    extends XmlAdapter<AdaptedConfiguratorFormula, ConfiguratorFormula> {

  private MetaFieldRepository metaFieldRepository;

  public ConfiguratorFormulaXmlAdapter() {}

  @Inject
  public ConfiguratorFormulaXmlAdapter(MetaFieldRepository metaFieldRepository) {

    this.metaFieldRepository = metaFieldRepository;
  }

  @Override
  public AdaptedConfiguratorFormula marshal(ConfiguratorFormula configuratorFormula)
      throws Exception {

    AdaptedConfiguratorFormula adaptedConfiguratorFormula = new AdaptedConfiguratorFormula();
    adaptedConfiguratorFormula.setFormula(configuratorFormula.getFormula());
    adaptedConfiguratorFormula.setMetaFieldName(configuratorFormula.getMetaField().getName());
    adaptedConfiguratorFormula.setShowOnConfigurator(configuratorFormula.getShowOnConfigurator());
    return adaptedConfiguratorFormula;
  }

  @Override
  public ConfiguratorFormula unmarshal(AdaptedConfiguratorFormula adaptedConfiguratorFormula)
      throws Exception {

    ConfiguratorFormula configuratorFormula = new ConfiguratorFormula();
    configuratorFormula.setFormula(adaptedConfiguratorFormula.getFormula());
    configuratorFormula.setMetaField(
        metaFieldRepository.findByName(adaptedConfiguratorFormula.getMetaFieldName()));
    configuratorFormula.setShowOnConfigurator(adaptedConfiguratorFormula.getShowOnConfigurator());
    return configuratorFormula;
  }
}
