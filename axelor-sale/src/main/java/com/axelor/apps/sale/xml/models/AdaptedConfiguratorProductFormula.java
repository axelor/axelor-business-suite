package com.axelor.apps.sale.xml.models;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class AdaptedConfiguratorProductFormula extends AdaptedConfiguratorFormula {

  private Long configuratorCreatorImportId;

  public Long getConfiguratorCreatorImportId() {
    return configuratorCreatorImportId;
  }

  public void setConfiguratorCreatorImportId(Long configuratorCreatorImportId) {
    this.configuratorCreatorImportId = configuratorCreatorImportId;
  }
}
