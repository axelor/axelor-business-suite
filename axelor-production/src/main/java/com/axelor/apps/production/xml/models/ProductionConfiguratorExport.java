package com.axelor.apps.production.xml.models;

import com.axelor.apps.base.xml.models.ExportedModel;
import com.axelor.apps.production.xml.adapters.ProductionConfiguratorCreatorAdapter;
import com.axelor.apps.sale.db.ConfiguratorCreator;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "configurator-export")
public class ProductionConfiguratorExport extends ExportedModel {

  @XmlElement(name = "configurator-creator")
  @XmlElementWrapper(name = "configurator-creators")
  @XmlJavaTypeAdapter(value = ProductionConfiguratorCreatorAdapter.class)
  private List<ConfiguratorCreator> configuratorsCreators;

  public ProductionConfiguratorExport() {}

  public ProductionConfiguratorExport(List<ConfiguratorCreator> configuratorCreatorList) {
    this.configuratorsCreators = configuratorCreatorList;
  }

  public List<ConfiguratorCreator> getConfiguratorsCreators() {
    return configuratorsCreators;
  }

  public void setConfiguratorsCreators(List<ConfiguratorCreator> configuratorsCreators) {
    this.configuratorsCreators = configuratorsCreators;
  }
}
