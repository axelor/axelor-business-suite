package com.axelor.apps.sale.xml.models;

import com.axelor.apps.base.xml.models.ExportedModel;
import com.axelor.apps.sale.db.ConfiguratorCreator;
import com.axelor.apps.sale.xml.adapters.ConfiguratorCreatorXmlAdapter;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "configurator-export")
public class ConfiguratorExport extends ExportedModel {

  @XmlElement(name = "configurator-creator")
  @XmlElementWrapper(name = "configurator-creators")
  @XmlJavaTypeAdapter(value = ConfiguratorCreatorXmlAdapter.class)
  private List<ConfiguratorCreator> configuratorsCreators;

  public ConfiguratorExport() {}

  public ConfiguratorExport(List<ConfiguratorCreator> configuratorCreatorList) {
    this.configuratorsCreators = configuratorCreatorList;
  }

  public List<ConfiguratorCreator> getConfiguratorsCreators() {
    return configuratorsCreators;
  }

  public void setConfiguratorsCreators(List<ConfiguratorCreator> configuratorsCreators) {
    this.configuratorsCreators = configuratorsCreators;
  }
}
