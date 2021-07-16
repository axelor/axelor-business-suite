package com.axelor.apps.base.xml.models;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public abstract class ExportedModel {

  private Long importId;

  public Long getImportId() {
    return importId;
  }

  public void setImportId(Long id) {
    this.importId = id;
  }
}
