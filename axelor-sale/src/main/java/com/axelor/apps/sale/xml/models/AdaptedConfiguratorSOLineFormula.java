package com.axelor.apps.sale.xml.models;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "configuratorSOLineFormula")
@XmlAccessorType(XmlAccessType.FIELD)
public class AdaptedConfiguratorSOLineFormula extends AdaptedConfiguratorFormula {

  private Integer updateFromSelect;

  private Long configuratorCreatorImportId;

  public Integer getUpdateFromSelect() {
    return updateFromSelect;
  }

  public void setUpdateFromSelect(Integer updateFromSelect) {
    this.updateFromSelect = updateFromSelect;
  }

  public Long getConfiguratorCreatorImportId() {
    return configuratorCreatorImportId;
  }

  public void setConfiguratorCreatorImportId(Long configuratorCreatorImportId) {
    this.configuratorCreatorImportId = configuratorCreatorImportId;
  }
}
