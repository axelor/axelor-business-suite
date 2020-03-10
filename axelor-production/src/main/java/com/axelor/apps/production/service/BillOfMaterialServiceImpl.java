/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.production.service;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.ProductService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.TempBomTree;
import com.axelor.apps.production.db.repo.BillOfMaterialRepository;
import com.axelor.apps.production.db.repo.TempBomTreeRepository;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.apps.production.report.IReport;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BillOfMaterialServiceImpl implements BillOfMaterialService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject protected BillOfMaterialRepository billOfMaterialRepo;

  @Inject private TempBomTreeRepository tempBomTreeRepo;

  @Inject private ProductRepository productRepo;

  private List<Long> processedBom;

  @Override
  public List<BillOfMaterial> getBillOfMaterialSet(Product product) {

    return billOfMaterialRepo.all().filter("self.product = ?1", product).fetch();
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateProductCostPrice(BillOfMaterial billOfMaterial) throws AxelorException {

    Product product = billOfMaterial.getProduct();

    if (product.getCostTypeSelect() != ProductRepository.COST_TYPE_STANDARD) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.COST_TYPE_CANNOT_BE_CHANGED));
    }

    product.setCostPrice(
        billOfMaterial
            .getCostPrice()
            .divide(
                billOfMaterial.getQty(),
                Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice(),
                BigDecimal.ROUND_HALF_UP));

    Beans.get(ProductService.class).updateSalePrice(product);

    billOfMaterialRepo.save(billOfMaterial);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public BillOfMaterial customizeBillOfMaterial(SaleOrderLine saleOrderLine)
      throws AxelorException {

    BillOfMaterial billOfMaterial = saleOrderLine.getBillOfMaterial();
    return customizeBillOfMaterial(billOfMaterial);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public BillOfMaterial customizeBillOfMaterial(BillOfMaterial billOfMaterial)
      throws AxelorException {
    return customizeBillOfMaterial(billOfMaterial, 0);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public BillOfMaterial customizeBillOfMaterial(BillOfMaterial billOfMaterial, int depth)
      throws AxelorException {
    if (depth > 1000) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.MAX_DEPTH_REACHED));
    }

    if (billOfMaterial != null) {
      BillOfMaterial personalizedBOM = JPA.copy(billOfMaterial, true);
      billOfMaterialRepo.save(personalizedBOM);
      personalizedBOM.setName(
          personalizedBOM.getName()
              + " ("
              + I18n.get(IExceptionMessage.BOM_1)
              + " "
              + personalizedBOM.getId()
              + ")");
      personalizedBOM.setPersonalized(true);
      Set<BillOfMaterial> personalizedBOMSet = new HashSet<BillOfMaterial>();
      for (BillOfMaterial childBillOfMaterial : billOfMaterial.getBillOfMaterialSet()) {
        personalizedBOMSet.add(customizeBillOfMaterial(childBillOfMaterial, depth + 1));
      }
      personalizedBOM.setBillOfMaterialSet(personalizedBOMSet);

      return personalizedBOM;
    }

    return null;
  }

  @Override
  @Transactional
  public BillOfMaterial generateNewVersion(BillOfMaterial billOfMaterial) {

    BillOfMaterial copy = billOfMaterialRepo.copy(billOfMaterial, true);

    copy.setOriginalBillOfMaterial(billOfMaterial);
    copy.clearCostSheetList();
    copy.setCostPrice(BigDecimal.ZERO);
    copy.setOriginalBillOfMaterial(billOfMaterial);
    copy.setVersionNumber(
        this.getLatestBillOfMaterialVersion(billOfMaterial, billOfMaterial.getVersionNumber(), true)
            + 1);

    return billOfMaterialRepo.save(copy);
  }

  public int getLatestBillOfMaterialVersion(
      BillOfMaterial billOfMaterial, int latestVersion, boolean deep) {

    List<BillOfMaterial> billOfMaterialSet;
    BillOfMaterial up = billOfMaterial;
    Long previousId = 0L;
    do {
      billOfMaterialSet =
          billOfMaterialRepo
              .all()
              .filter("self.originalBillOfMaterial = :origin AND self.id != :id")
              .bind("origin", up)
              .bind("id", previousId)
              .order("-versionNumber")
              .fetch();
      if (!billOfMaterialSet.isEmpty()) {
        latestVersion =
            (billOfMaterialSet.get(0).getVersionNumber() > latestVersion)
                ? billOfMaterialSet.get(0).getVersionNumber()
                : latestVersion;
        for (BillOfMaterial billOfMaterialIterator : billOfMaterialSet) {
          int search =
              this.getLatestBillOfMaterialVersion(billOfMaterialIterator, latestVersion, false);
          latestVersion = (search > latestVersion) ? search : latestVersion;
        }
      }
      previousId = up.getId();
      up = up.getOriginalBillOfMaterial();
    } while (up != null && deep);

    return latestVersion;
  }

  @Override
  public String getFileName(BillOfMaterial billOfMaterial) {

    return I18n.get("Bill of Material")
        + "-"
        + billOfMaterial.getName()
        + ((billOfMaterial.getVersionNumber() > 1) ? "-V" + billOfMaterial.getVersionNumber() : "");
  }

  @Override
  public String getReportLink(
      BillOfMaterial billOfMaterial, String name, String language, String format)
      throws AxelorException {

    return ReportFactory.createReport(IReport.BILL_OF_MATERIAL, name + "-${date}")
        .addParam("Locale", language)
        .addParam("BillOfMaterialId", billOfMaterial.getId())
        .addFormat(format)
        .generate()
        .getFileLink();
  }

  @Override
  public TempBomTree generateTree(BillOfMaterial billOfMaterial) {

    processedBom = new ArrayList<>();

    return getBomTree(billOfMaterial, null, null);
  }

  @Transactional
  public TempBomTree getBomTree(BillOfMaterial bom, BillOfMaterial parentBom, TempBomTree parent) {

    TempBomTree bomTree;
    if (parentBom == null) {
      bomTree =
          tempBomTreeRepo.all().filter("self.bom = ?1 and self.parentBom = null", bom).fetchOne();
    } else {
      bomTree =
          tempBomTreeRepo
              .all()
              .filter("self.bom = ?1 and self.parentBom = ?2", bom, parentBom)
              .fetchOne();
    }

    if (bomTree == null) {
      bomTree = new TempBomTree();
    }
    bomTree.setProdProcess(bom.getProdProcess());
    bomTree.setProduct(bom.getProduct());
    bomTree.setQty(bom.getQty());
    bomTree.setUnit(bom.getUnit());
    bomTree.setParentBom(parentBom);
    bomTree.setParent(parent);
    bomTree.setBom(bom);
    bomTree = tempBomTreeRepo.save(bomTree);

    processedBom.add(bom.getId());

    List<Long> validBomIds = processChildBom(bom, bomTree);

    validBomIds.add(0L);

    removeInvalidTree(validBomIds, bom);

    return bomTree;
  }

  protected List<Long> processChildBom(BillOfMaterial bom, TempBomTree bomTree) {

    List<Long> validBomIds = new ArrayList<Long>();

    for (BillOfMaterial childBom : bom.getBillOfMaterialSet()) {
      if (!processedBom.contains(childBom.getId())) {
        getBomTree(childBom, bom, bomTree);
      } else {
        log.debug("Already processed: {}", childBom.getId());
      }
      validBomIds.add(childBom.getId());
    }

    return validBomIds;
  }

  @Transactional
  public void removeInvalidTree(List<Long> validBomIds, BillOfMaterial bom) {

    List<TempBomTree> invalidBomTrees =
        tempBomTreeRepo
            .all()
            .filter("self.bom.id not in (?1) and self.parentBom = ?2", validBomIds, bom)
            .fetch();

    log.debug("Invalid bom trees: {}", invalidBomTrees);

    if (!invalidBomTrees.isEmpty()) {
      List<TempBomTree> childBomTrees =
          tempBomTreeRepo.all().filter("self.parent in (?1)", invalidBomTrees).fetch();

      for (TempBomTree childBomTree : childBomTrees) {
        childBomTree.setParent(null);
        tempBomTreeRepo.save(childBomTree);
      }
    }

    for (TempBomTree invalidBomTree : invalidBomTrees) {
      tempBomTreeRepo.remove(invalidBomTree);
    }
  }

  @Override
  @Transactional
  public void setBillOfMaterialAsDefault(BillOfMaterial billOfMaterial) {
    billOfMaterial.getProduct().setDefaultBillOfMaterial(billOfMaterial);
  }

  @Override
  public String computeName(BillOfMaterial bom) {
    Integer nbDecimalDigitForBomQty =
        Beans.get(AppProductionService.class).getAppProduction().getNbDecimalDigitForBomQty();
    return bom.getProduct().getName()
        + " - "
        + bom.getQty().setScale(nbDecimalDigitForBomQty, RoundingMode.HALF_EVEN)
        + " "
        + bom.getUnit().getName()
        + " - "
        + bom.getId();
  }

  @Override
  @Transactional
  public void addRawMaterials(
      long billOfMaterialId, ArrayList<LinkedHashMap<String, Object>> rawMaterials) {
    if (rawMaterials != null && !rawMaterials.isEmpty()) {
      BillOfMaterial billOfMaterial = billOfMaterialRepo.find(billOfMaterialId);
      int priority = 0;
      if (billOfMaterial.getBillOfMaterialSet() != null
          && !billOfMaterial.getBillOfMaterialSet().isEmpty()) {
        priority =
            Collections.max(
                billOfMaterial
                    .getBillOfMaterialSet()
                    .stream()
                    .map(it -> it.getPriority())
                    .collect(Collectors.toSet()));
      }

      for (LinkedHashMap<String, Object> rawMaterial : rawMaterials) {
        priority += 10;
        BillOfMaterial newComponent =
            createBomFromRawMaterial(Long.valueOf((int) rawMaterial.get("id")), priority);
        billOfMaterial.getBillOfMaterialSet().add(newComponent);
      }
    } else {
      return;
    }
  }

  @Transactional
  protected BillOfMaterial createBomFromRawMaterial(long productId, int priority) {
    BillOfMaterial newBom = new BillOfMaterial();
    Product rawMaterial = productRepo.find(productId);
    newBom.setDefineSubBillOfMaterial(false);
    newBom.setPriority(priority);
    newBom.setProduct(rawMaterial);
    newBom.setQty(new BigDecimal(1));
    newBom.setUnit(rawMaterial.getUnit());
    newBom.setWasteRate(BigDecimal.ZERO);
    newBom.setHasNoManageStock(false);

    billOfMaterialRepo.save(newBom);
    String name = this.computeName(newBom); // need to save first cuz computeName uses the id.
    newBom.setName(name);
    newBom.setFullName(name);

    return newBom;
  }
}
