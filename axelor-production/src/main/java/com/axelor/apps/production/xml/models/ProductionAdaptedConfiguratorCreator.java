package com.axelor.apps.production.xml.models;

import com.axelor.apps.production.db.ConfiguratorBOM;
import com.axelor.apps.production.xml.adapters.ConfiguratorBOMXmlAdapter;
import com.axelor.apps.sale.db.ConfiguratorCreator;
import com.axelor.apps.sale.xml.models.AdaptedConfiguratorCreator;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(XmlAccessType.FIELD)
public class ProductionAdaptedConfiguratorCreator extends AdaptedConfiguratorCreator {

  @XmlElement(name = "configurator-bom")
  @XmlJavaTypeAdapter(value = ConfiguratorBOMXmlAdapter.class)
  private ConfiguratorBOM configuratorBoms;

  public ProductionAdaptedConfiguratorCreator() {}

  public ProductionAdaptedConfiguratorCreator(
      ConfiguratorCreator configuratorCreator, ConfiguratorBOM configuratorBom) {
    super(configuratorCreator);
    this.configuratorBoms = configuratorBom;
  }

  @Override
  public ConfiguratorCreator toConfiguratorCreator() {
    ConfiguratorCreator configuratorCreator = super.toConfiguratorCreator();
    configuratorCreator.setConfiguratorBom(this.configuratorBoms);
    return configuratorCreator;
  }

  public ConfiguratorBOM getConfiguratorBoms() {
    return configuratorBoms;
  }

  public void setConfiguratorBoms(ConfiguratorBOM configuratorBoms) {
    this.configuratorBoms = configuratorBoms;
  }
}
