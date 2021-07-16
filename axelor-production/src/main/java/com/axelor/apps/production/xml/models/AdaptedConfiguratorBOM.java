package com.axelor.apps.production.xml.models;

import com.axelor.apps.base.xml.models.ExportedModel;
import com.axelor.apps.production.db.ConfiguratorBOM;
import com.axelor.apps.production.xml.adapters.ConfiguratorBOMXmlAdapter;
import java.math.BigDecimal;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(XmlAccessType.FIELD)
public class AdaptedConfiguratorBOM extends ExportedModel {

  private String companyCode;

  private String name;

  private String nameFormula;

  private Boolean defNameAsFormula;

  private String productFormula;

  private Boolean defProductAsFormula;

  private Boolean defProductFromConfigurator;

  private BigDecimal qty;

  private String qtyFormula;

  private Boolean defQtyAsFormula;

  private String unitFormula;

  private Boolean defUnitAsFormula;

  private String prodProcessFormula;

  private Boolean defProdProcessAsFormula;

  private Boolean defProdProcessAsConfigurator;

  @XmlElement(name = "configurator-bom")
  @XmlElementWrapper(name = "configurator-boms")
  @XmlJavaTypeAdapter(value = ConfiguratorBOMXmlAdapter.class)
  private List<ConfiguratorBOM> configuratorBomList;

  private Long parentConfiguratorBOMId;

  private Boolean defineSubBillOfMaterial;

  private Integer statusSelect;

  private String useCondition;

  private Long billOfMaterialId;

  public String getCompanyCode() {
    return companyCode;
  }

  public void setCompanyCode(String companyCode) {
    this.companyCode = companyCode;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getNameFormula() {
    return nameFormula;
  }

  public void setNameFormula(String nameFormula) {
    this.nameFormula = nameFormula;
  }

  public Boolean getDefNameAsFormula() {
    return defNameAsFormula;
  }

  public void setDefNameAsFormula(Boolean defNameAsFormula) {
    this.defNameAsFormula = defNameAsFormula;
  }

  public String getProductFormula() {
    return productFormula;
  }

  public void setProductFormula(String productFormula) {
    this.productFormula = productFormula;
  }

  public Boolean getDefProductAsFormula() {
    return defProductAsFormula;
  }

  public void setDefProductAsFormula(Boolean defProductAsFormula) {
    this.defProductAsFormula = defProductAsFormula;
  }

  public Boolean getDefProductFromConfigurator() {
    return defProductFromConfigurator;
  }

  public void setDefProductFromConfigurator(Boolean defProductFromConfigurator) {
    this.defProductFromConfigurator = defProductFromConfigurator;
  }

  public BigDecimal getQty() {
    return qty;
  }

  public void setQty(BigDecimal qty) {
    this.qty = qty;
  }

  public String getQtyFormula() {
    return qtyFormula;
  }

  public void setQtyFormula(String qtyFormula) {
    this.qtyFormula = qtyFormula;
  }

  public Boolean getDefQtyAsFormula() {
    return defQtyAsFormula;
  }

  public void setDefQtyAsFormula(Boolean defQtyAsFormula) {
    this.defQtyAsFormula = defQtyAsFormula;
  }

  public String getUnitFormula() {
    return unitFormula;
  }

  public void setUnitFormula(String unitFormula) {
    this.unitFormula = unitFormula;
  }

  public Boolean getDefUnitAsFormula() {
    return defUnitAsFormula;
  }

  public void setDefUnitAsFormula(Boolean defUnitAsFormula) {
    this.defUnitAsFormula = defUnitAsFormula;
  }

  public String getProdProcessFormula() {
    return prodProcessFormula;
  }

  public void setProdProcessFormula(String prodProcessFormula) {
    this.prodProcessFormula = prodProcessFormula;
  }

  public Boolean getDefProdProcessAsFormula() {
    return defProdProcessAsFormula;
  }

  public void setDefProdProcessAsFormula(Boolean defProdProcessAsFormula) {
    this.defProdProcessAsFormula = defProdProcessAsFormula;
  }

  public Boolean getDefProdProcessAsConfigurator() {
    return defProdProcessAsConfigurator;
  }

  public void setDefProdProcessAsConfigurator(Boolean defProdProcessAsConfigurator) {
    this.defProdProcessAsConfigurator = defProdProcessAsConfigurator;
  }

  public List<ConfiguratorBOM> getConfiguratorBomList() {
    return configuratorBomList;
  }

  public void setConfiguratorBomList(List<ConfiguratorBOM> configuratorBomList) {
    this.configuratorBomList = configuratorBomList;
  }

  public Long getParentConfiguratorBOMId() {
    return parentConfiguratorBOMId;
  }

  public void setParentConfiguratorBOMId(Long parentConfiguratorBOMId) {
    this.parentConfiguratorBOMId = parentConfiguratorBOMId;
  }

  public Boolean getDefineSubBillOfMaterial() {
    return defineSubBillOfMaterial;
  }

  public void setDefineSubBillOfMaterial(Boolean defineSubBillOfMaterial) {
    this.defineSubBillOfMaterial = defineSubBillOfMaterial;
  }

  public Integer getStatusSelect() {
    return statusSelect;
  }

  public void setStatusSelect(Integer statusSelect) {
    this.statusSelect = statusSelect;
  }

  public String getUseCondition() {
    return useCondition;
  }

  public void setUseCondition(String useCondition) {
    this.useCondition = useCondition;
  }

  public Long getBillOfMaterialId() {
    return billOfMaterialId;
  }

  public void setBillOfMaterialId(Long billOfMaterialId) {
    this.billOfMaterialId = billOfMaterialId;
  }
}
