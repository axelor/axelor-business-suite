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

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.ManufOrder;
import com.axelor.apps.production.db.OperationOrder;
import com.axelor.apps.production.db.ProdProduct;
import com.axelor.apps.production.db.repo.ManufOrderRepository;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.purchase.db.repo.PurchaseOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockRules;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockRulesRepository;
import com.axelor.apps.stock.service.StockRulesService;
import com.axelor.apps.supplychain.db.Mrp;
import com.axelor.apps.supplychain.db.MrpLine;
import com.axelor.apps.supplychain.db.MrpLineOrigin;
import com.axelor.apps.supplychain.db.MrpLineType;
import com.axelor.apps.supplychain.db.repo.MrpForecastRepository;
import com.axelor.apps.supplychain.db.repo.MrpLineRepository;
import com.axelor.apps.supplychain.db.repo.MrpLineTypeRepository;
import com.axelor.apps.supplychain.db.repo.MrpRepository;
import com.axelor.apps.supplychain.service.MrpLineService;
import com.axelor.apps.supplychain.service.MrpServiceImpl;
import com.axelor.apps.tool.StringTool;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MrpServiceProductionImpl extends MrpServiceImpl {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected ManufOrderRepository manufOrderRepository;

  @Inject
  public MrpServiceProductionImpl(
      AppProductionService appProductionService,
      MrpRepository mrpRepository,
      StockLocationRepository stockLocationRepository,
      ProductRepository productRepository,
      StockLocationLineRepository stockLocationLineRepository,
      MrpLineTypeRepository mrpLineTypeRepository,
      PurchaseOrderLineRepository purchaseOrderLineRepository,
      SaleOrderLineRepository saleOrderLineRepository,
      MrpLineRepository mrpLineRepository,
      StockRulesService stockRulesService,
      MrpLineService mrpLineService,
      MrpForecastRepository mrpForecastRepository,
      ManufOrderRepository manufOrderRepository) {

    super(
        appProductionService,
        mrpRepository,
        stockLocationRepository,
        productRepository,
        stockLocationLineRepository,
        mrpLineTypeRepository,
        purchaseOrderLineRepository,
        saleOrderLineRepository,
        mrpLineRepository,
        stockRulesService,
        mrpLineService,
        mrpForecastRepository);

    this.manufOrderRepository = manufOrderRepository;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  protected void completeMrp(Mrp mrp) throws AxelorException {

    super.completeMrp(mrp);

    this.createManufOrderMrpLines();

    mrpRepository.save(mrp);
  }

  // Manufacturing order AND manufacturing order need
  protected void createManufOrderMrpLines() throws AxelorException {

    MrpLineType manufOrderMrpLineType =
        this.getMrpLineType(MrpLineTypeRepository.ELEMENT_MANUFACTURING_ORDER);
    MrpLineType manufOrderNeedMrpLineType =
        this.getMrpLineType(MrpLineTypeRepository.ELEMENT_MANUFACTURING_ORDER_NEED);

    String statusSelect = manufOrderMrpLineType.getStatusSelect();
    List<Integer> statusList = StringTool.getIntegerList(statusSelect);

    if (statusList.isEmpty()) {
      statusList.add(ManufOrderRepository.STATUS_FINISHED);
    }

    List<ManufOrder> manufOrderList =
        manufOrderRepository
            .all()
            .filter(
                "self.product.id in (?1) AND self.prodProcess.stockLocation in (?2) "
                    + "AND self.statusSelect NOT IN (?3) AND self.plannedStartDateT > ?4",
                this.productMap.keySet(),
                this.stockLocationList,
                statusList,
                appBaseService.getTodayDate().atStartOfDay())
            .fetch();

    for (ManufOrder manufOrder : manufOrderList) {

      StockLocation stockLocation = manufOrder.getProdProcess().getStockLocation();

      for (ProdProduct prodProduct : manufOrder.getToProduceProdProductList()) {

        Product product = prodProduct.getProduct();

        LocalDate maturityDate = null;

        if (manufOrder.getPlannedEndDateT() != null) {
          maturityDate = manufOrder.getPlannedEndDateT().toLocalDate();
        } else {
          maturityDate = manufOrder.getPlannedStartDateT().toLocalDate();
        }

        if (this.isBeforeEndDate(maturityDate) && this.isMrpProduct(product)) {

          MrpLine mrpLine =
              this.createMrpLine(
                  product,
                  manufOrderMrpLineType,
                  prodProduct.getQty(),
                  maturityDate,
                  BigDecimal.ZERO,
                  stockLocation,
                  manufOrder);
          if (mrpLine != null) {
            mrp.addMrpLineListItem(mrpLine);
          }
        }
      }

      if (manufOrder.getIsConsProOnOperation()) {
        for (OperationOrder operationOrder : manufOrder.getOperationOrderList()) {
          for (ProdProduct prodProduct : operationOrder.getToConsumeProdProductList()) {

            Product product = prodProduct.getProduct();

            if (this.isMrpProduct(product)) {

              MrpLine mrpLine =
                  this.createMrpLine(
                      prodProduct.getProduct(),
                      manufOrderNeedMrpLineType,
                      prodProduct.getQty(),
                      operationOrder.getPlannedStartDateT().toLocalDate(),
                      BigDecimal.ZERO,
                      stockLocation,
                      operationOrder);
              if (mrpLine != null) {
                mrp.addMrpLineListItem(mrpLine);
              }
            }
          }
        }
      } else {
        for (ProdProduct prodProduct : manufOrder.getToConsumeProdProductList()) {

          Product product = prodProduct.getProduct();

          if (this.isMrpProduct(product)) {

            // A component of a manuf order that is not loaded on MRP because there is no default
            // BOM or
            // because the component of manuf order is not a component of the bill of material, we
            // add it with the level of manuf order product + 1.
            if (!this.productMap.containsKey(product.getId())) {
              this.assignProductAndLevel(product, manufOrder.getProduct());
              this.createAvailableStockMrpLine(
                  product, manufOrder.getProdProcess().getStockLocation());
            }
            MrpLine mrpLine =
                this.createMrpLine(
                    product,
                    manufOrderNeedMrpLineType,
                    prodProduct.getQty(),
                    manufOrder.getPlannedStartDateT().toLocalDate(),
                    BigDecimal.ZERO,
                    stockLocation,
                    manufOrder);
            if (mrpLine != null) {
              mrp.addMrpLineListItem(mrpLine);
            }
          }
        }
      }
    }
  }

  @Override
  protected void createProposalMrpLine(
      Product product,
      MrpLineType mrpLineType,
      BigDecimal reorderQty,
      StockLocation stockLocation,
      LocalDate maturityDate,
      List<MrpLineOrigin> mrpLineOriginList,
      String relatedToSelectName)
      throws AxelorException {

    super.createProposalMrpLine(
        product,
        mrpLineType,
        reorderQty,
        stockLocation,
        maturityDate,
        mrpLineOriginList,
        relatedToSelectName);

    BillOfMaterial defaultBillOfMaterial = product.getDefaultBillOfMaterial();

    if (mrpLineType.getElementSelect() == MrpLineTypeRepository.ELEMENT_MANUFACTURING_PROPOSAL
        && defaultBillOfMaterial != null) {

      MrpLineType manufProposalNeedMrpLineType =
          this.getMrpLineType(MrpLineTypeRepository.ELEMENT_MANUFACTURING_PROPOSAL_NEED);

      for (BillOfMaterial billOfMaterial : defaultBillOfMaterial.getBillOfMaterialSet()) {

        Product subProduct = billOfMaterial.getProduct();

        if (this.isMrpProduct(subProduct)) {
          // TODO take the time to do the Manuf order (use machine planning)
          super.createProposalMrpLine(
              subProduct,
              manufProposalNeedMrpLineType,
              reorderQty.multiply(billOfMaterial.getQty()),
              stockLocation,
              maturityDate,
              mrpLineOriginList,
              relatedToSelectName);
        }
      }
    }
  }

  /**
   * Returns the type of an mrp proposal.
   *
   * <p>First checks for a stock rule, then if there isn't one, checks for the
   * procurementMethodSelect field of the product. If the product can be both bought and produced, a
   * manufacturing order is generated.
   */
  @Override
  protected MrpLineType getMrpLineTypeForProposal(StockRules stockRules, Product product)
      throws AxelorException {

    if (stockRules != null) {
      if (stockRules.getOrderAlertSelect() == StockRulesRepository.ORDER_ALERT_PRODUCTION_ORDER) {
        return this.getMrpLineType(MrpLineTypeRepository.ELEMENT_MANUFACTURING_PROPOSAL);
      } else {
        return this.getMrpLineType(MrpLineTypeRepository.ELEMENT_PURCHASE_PROPOSAL);
      }
    }

    if (product.getProcurementMethodSelect().equals(ProductRepository.PROCUREMENT_METHOD_BUY)) {
      return this.getMrpLineType(MrpLineTypeRepository.ELEMENT_PURCHASE_PROPOSAL);
    } else {
      return this.getMrpLineType(MrpLineTypeRepository.ELEMENT_MANUFACTURING_PROPOSAL);
    }
  }

  @Override
  protected boolean isProposalElement(MrpLineType mrpLineType) {

    if (mrpLineType.getElementSelect() == MrpLineTypeRepository.ELEMENT_PURCHASE_PROPOSAL
        || mrpLineType.getElementSelect() == MrpLineTypeRepository.ELEMENT_MANUFACTURING_PROPOSAL
        || mrpLineType.getElementSelect()
            == MrpLineTypeRepository.ELEMENT_MANUFACTURING_PROPOSAL_NEED) {

      return true;
    }

    return false;
  }

  @Override
  protected void assignProductAndLevel(Product product) {

    log.debug("Add of the product : {}", product.getFullName());
    this.productMap.put(product.getId(), this.getMaxLevel(product, 0));

    if (product.getDefaultBillOfMaterial() != null) {
      this.assignProductLevel(product.getDefaultBillOfMaterial(), 0);
    }
  }

  public int getMaxLevel(Product product, int level) {

    if (this.productMap.containsKey(product.getId())) {
      return Math.max(level, this.productMap.get(product.getId()));
    }

    return level;
  }

  /**
   * Update the level of Bill of material. The highest for each product (0: product with parent, 1:
   * product with a parent, 2: product with a parent that have a parent, ...)
   *
   * @param billOfMaterial
   * @param level
   */
  protected void assignProductLevel(BillOfMaterial billOfMaterial, int level) {

    if (billOfMaterial.getBillOfMaterialSet() == null
        || billOfMaterial.getBillOfMaterialSet().isEmpty()
        || level > 100) {

      Product subProduct = billOfMaterial.getProduct();

      log.debug("Add of the sub product : {} for the level : {} ", subProduct.getFullName(), level);
      this.productMap.put(subProduct.getId(), this.getMaxLevel(subProduct, level));

    } else {

      level = level + 1;

      for (BillOfMaterial subBillOfMaterial : billOfMaterial.getBillOfMaterialSet()) {

        Product subProduct = subBillOfMaterial.getProduct();

        if (this.isMrpProduct(subProduct)) {
          this.assignProductLevel(subBillOfMaterial, level);

          if (subProduct.getDefaultBillOfMaterial() != null) {
            this.assignProductLevel(subProduct.getDefaultBillOfMaterial(), level);
          }
        }
      }
    }
  }

  /**
   * Add a component product of a manuf order where the component product is not contained on the
   * default bill of material of the produced product.
   *
   * @param manufOrderComponentProduct
   * @param manufOrderProducedProduct
   */
  protected void assignProductAndLevel(
      Product manufOrderComponentProduct, Product manufOrderProducedProduct) {

    log.debug("Add of the product : {}", manufOrderComponentProduct.getFullName());
    this.productMap.put(
        manufOrderComponentProduct.getId(), this.getMaxLevel(manufOrderProducedProduct, 0) + 1);
  }

  protected void createAvailableStockMrpLine(Product product, StockLocation stockLocation)
      throws AxelorException {

    MrpLineType availableStockMrpLineType =
        this.getMrpLineType(MrpLineTypeRepository.ELEMENT_AVAILABLE_STOCK);

    mrp.addMrpLineListItem(
        this.createAvailableStockMrpLine(
            productRepository.find(product.getId()), stockLocation, availableStockMrpLineType));
  }
}
