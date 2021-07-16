package com.axelor.apps.production.xml.adapters;

import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.production.db.ConfiguratorBOM;
import com.axelor.apps.production.db.repo.ProductionConfiguratorBOMRepository;
import com.axelor.apps.production.xml.models.AdaptedConfiguratorBOM;
import com.axelor.inject.Beans;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class ConfiguratorBOMXmlAdapter extends XmlAdapter<AdaptedConfiguratorBOM, ConfiguratorBOM> {

  @Override
  public AdaptedConfiguratorBOM marshal(ConfiguratorBOM configuratorBom) throws Exception {
    AdaptedConfiguratorBOM adaptedConfiguratorBom = new AdaptedConfiguratorBOM();
    adaptedConfiguratorBom.setImportId(configuratorBom.getId());
    if (configuratorBom.getCompany() != null) {
      adaptedConfiguratorBom.setCompanyCode(configuratorBom.getCompany().getCode());
    }
    adaptedConfiguratorBom.setName(configuratorBom.getName());
    adaptedConfiguratorBom.setNameFormula(configuratorBom.getNameFormula());
    adaptedConfiguratorBom.setDefNameAsFormula(configuratorBom.getDefNameAsFormula());
    adaptedConfiguratorBom.setProductFormula(configuratorBom.getProductFormula());
    adaptedConfiguratorBom.setDefProductAsFormula(configuratorBom.getDefProductAsFormula());
    adaptedConfiguratorBom.setDefProductFromConfigurator(
        configuratorBom.getDefProductFromConfigurator());
    adaptedConfiguratorBom.setQty(configuratorBom.getQty());
    adaptedConfiguratorBom.setQtyFormula(configuratorBom.getQtyFormula());
    adaptedConfiguratorBom.setDefQtyAsFormula(configuratorBom.getDefQtyAsFormula());
    adaptedConfiguratorBom.setUnitFormula(configuratorBom.getUnitFormula());
    adaptedConfiguratorBom.setDefUnitAsFormula(configuratorBom.getDefUnitAsFormula());
    adaptedConfiguratorBom.setProdProcessFormula(configuratorBom.getProdProcessFormula());
    adaptedConfiguratorBom.setDefProdProcessAsFormula(configuratorBom.getDefProdProcessAsFormula());
    adaptedConfiguratorBom.setDefProdProcessAsConfigurator(
        configuratorBom.getDefProdProcessAsConfigurator());
    adaptedConfiguratorBom.setConfiguratorBomList(configuratorBom.getConfiguratorBomList());
    if (configuratorBom.getParentConfiguratorBOM() != null) {
      adaptedConfiguratorBom.setParentConfiguratorBOMId(
          configuratorBom.getParentConfiguratorBOM().getId());
    }
    adaptedConfiguratorBom.setDefineSubBillOfMaterial(configuratorBom.getDefineSubBillOfMaterial());
    adaptedConfiguratorBom.setStatusSelect(configuratorBom.getStatusSelect());
    adaptedConfiguratorBom.setUseCondition(configuratorBom.getUseCondition());
    adaptedConfiguratorBom.setBillOfMaterialId(configuratorBom.getBillOfMaterialId());
    return adaptedConfiguratorBom;
  }

  @Override
  public ConfiguratorBOM unmarshal(AdaptedConfiguratorBOM adaptedConfiguratorBom) throws Exception {

    ConfiguratorBOM existingConfiguratorBom =
        Beans.get(ProductionConfiguratorBOMRepository.class)
            .findByImportId(adaptedConfiguratorBom.getImportId());
    if (existingConfiguratorBom != null) {
      return existingConfiguratorBom;
    }
    ConfiguratorBOM configuratorBom = new ConfiguratorBOM();

    configuratorBom.setImportId(adaptedConfiguratorBom.getImportId().toString());
    configuratorBom.setCompany(
        Beans.get(CompanyRepository.class).findByCode(adaptedConfiguratorBom.getCompanyCode()));
    configuratorBom.setName(adaptedConfiguratorBom.getName());
    configuratorBom.setNameFormula(adaptedConfiguratorBom.getNameFormula());
    configuratorBom.setDefNameAsFormula(adaptedConfiguratorBom.getDefNameAsFormula());
    configuratorBom.setProductFormula(adaptedConfiguratorBom.getProductFormula());
    configuratorBom.setDefProductAsFormula(adaptedConfiguratorBom.getDefProductAsFormula());
    configuratorBom.setDefProductFromConfigurator(
        adaptedConfiguratorBom.getDefProductFromConfigurator());
    configuratorBom.setQty(adaptedConfiguratorBom.getQty());
    configuratorBom.setQtyFormula(adaptedConfiguratorBom.getQtyFormula());
    configuratorBom.setDefQtyAsFormula(adaptedConfiguratorBom.getDefQtyAsFormula());
    configuratorBom.setUnitFormula(adaptedConfiguratorBom.getUnitFormula());
    configuratorBom.setDefUnitAsFormula(adaptedConfiguratorBom.getDefUnitAsFormula());
    configuratorBom.setProdProcessFormula(adaptedConfiguratorBom.getProdProcessFormula());
    configuratorBom.setDefProdProcessAsFormula(adaptedConfiguratorBom.getDefProdProcessAsFormula());
    configuratorBom.setDefProdProcessAsConfigurator(
        adaptedConfiguratorBom.getDefProdProcessAsConfigurator());
    configuratorBom.setConfiguratorBomList(adaptedConfiguratorBom.getConfiguratorBomList());
    configuratorBom.setDefineSubBillOfMaterial(adaptedConfiguratorBom.getDefineSubBillOfMaterial());
    configuratorBom.setStatusSelect(adaptedConfiguratorBom.getStatusSelect());
    configuratorBom.setUseCondition(adaptedConfiguratorBom.getUseCondition());
    configuratorBom.setBillOfMaterialId(adaptedConfiguratorBom.getBillOfMaterialId());
    return configuratorBom;
  }
}
