package com.axelor.apps.sale.xml.models;

import com.axelor.apps.base.xml.models.ExportedModel;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class AdaptedConfiguratorFormula extends ExportedModel {

  @XmlElement(name = "metaField")
  private String metaFieldName;

  private String formula;

  private Boolean showOnConfigurator;

  public String getMetaFieldName() {
    return metaFieldName;
  }

  public void setMetaFieldName(String metaFieldName) {
    this.metaFieldName = metaFieldName;
  }

  public String getFormula() {
    return formula;
  }

  public void setFormula(String formula) {
    this.formula = formula;
  }

  public Boolean getShowOnConfigurator() {
    return showOnConfigurator;
  }

  public void setShowOnConfigurator(Boolean showOnConfigurator) {
    this.showOnConfigurator = showOnConfigurator;
  }
}
