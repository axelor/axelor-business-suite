/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.production.service.costsheet;

import com.axelor.apps.base.db.AppProduction;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.CostSheet;
import com.axelor.apps.production.db.CostSheetLine;
import com.axelor.apps.production.db.ManufOrder;
import com.axelor.apps.production.db.OperationOrder;
import com.axelor.apps.production.db.ProdHumanResource;
import com.axelor.apps.production.db.ProdProcess;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.ProdProduct;
import com.axelor.apps.production.db.ProdResidualProduct;
import com.axelor.apps.production.db.UnitCostCalculation;
import com.axelor.apps.production.db.WorkCenter;
import com.axelor.apps.production.db.repo.BillOfMaterialRepository;
import com.axelor.apps.production.db.repo.CostSheetRepository;
import com.axelor.apps.production.db.repo.ManufOrderRepository;
import com.axelor.apps.production.db.repo.WorkCenterRepository;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.tool.date.DurationTool;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CostSheetServiceImpl implements CostSheetService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected UnitConversionService unitConversionService;
  protected CostSheetLineService costSheetLineService;
  protected BillOfMaterialRepository billOfMaterialRepo;
  protected AppBaseService appBaseService;
  protected AppProductionService appProductionService;

  protected Unit hourUnit;
  protected Unit cycleUnit;
  protected boolean manageResidualProductOnBom;
  protected CostSheet costSheet;

  @Inject
  public CostSheetServiceImpl(
      AppProductionService appProductionService,
      UnitConversionService unitConversionService,
      CostSheetLineService costSheetLineService,
      AppBaseService appBaseService,
      BillOfMaterialRepository billOfMaterialRepo) {

    this.appProductionService = appProductionService;
    this.unitConversionService = unitConversionService;
    this.costSheetLineService = costSheetLineService;
    this.appBaseService = appBaseService;
    this.billOfMaterialRepo = billOfMaterialRepo;
  }

  protected void init() {

    AppProduction appProduction = appProductionService.getAppProduction();
    this.hourUnit = appProductionService.getAppBase().getUnitHours();
    this.cycleUnit = appProduction.getCycleUnit();
    this.manageResidualProductOnBom = appProduction.getManageResidualProductOnBom();

    costSheet = new CostSheet();
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public CostSheet computeCostPrice(
      BillOfMaterial billOfMaterial, int origin, UnitCostCalculation unitCostCalculation)
      throws AxelorException {

    this.init();

    billOfMaterial.addCostSheetListItem(costSheet);

    CostSheetLine producedCostSheetLine =
        costSheetLineService.createProducedProductCostSheetLine(
            billOfMaterial.getProduct(), billOfMaterial.getUnit(), billOfMaterial.getQty());

    costSheet.addCostSheetLineListItem(producedCostSheetLine);
    costSheet.setCalculationTypeSelect(CostSheetRepository.CALCULATION_BILL_OF_MATERIAL);
    costSheet.setCalculationDate(
        Beans.get(AppBaseService.class).getTodayDate(billOfMaterial.getCompany()));
    Company company = billOfMaterial.getCompany();
    if (company != null && company.getCurrency() != null) {
      costSheet.setCurrency(company.getCurrency());
    }

    this._computeCostPrice(
        billOfMaterial.getCompany(),
        billOfMaterial,
        0,
        producedCostSheetLine,
        origin,
        unitCostCalculation);

    this.computeResidualProduct(billOfMaterial);

    billOfMaterial.setCostPrice(this.computeCostPrice(costSheet));

    billOfMaterialRepo.save(billOfMaterial);

    return costSheet;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public CostSheet computeCostPrice(
      ManufOrder manufOrder, int calculationTypeSelect, LocalDate calculationDate)
      throws AxelorException {
    this.init();

    List<CostSheet> costSheetList = manufOrder.getCostSheetList();
    LocalDate previousCostSheetDate = null;
    for (CostSheet costSheet : costSheetList) {
      if ((costSheet.getCalculationTypeSelect() == CostSheetRepository.CALCULATION_END_OF_PRODUCTION
              || costSheet.getCalculationTypeSelect()
                  == CostSheetRepository.CALCULATION_PARTIAL_END_OF_PRODUCTION)
          && costSheet.getCalculationDate() != null) {
        if (previousCostSheetDate == null) {
          previousCostSheetDate = costSheet.getCalculationDate();
        } else if (costSheet.getCalculationDate().isAfter(previousCostSheetDate)) {
          previousCostSheetDate = costSheet.getCalculationDate();
        }
      }
    }
    manufOrder.addCostSheetListItem(costSheet);

    costSheet.setCalculationTypeSelect(calculationTypeSelect);
    costSheet.setCalculationDate(
        calculationDate != null
            ? calculationDate
            : Beans.get(AppBaseService.class).getTodayDate(manufOrder.getCompany()));

    BigDecimal producedQty =
        computeTotalProducedQty(
            manufOrder.getProduct(),
            manufOrder.getProducedStockMoveLineList(),
            costSheet.getCalculationDate(),
            previousCostSheetDate,
            costSheet.getCalculationTypeSelect());

    CostSheetLine producedCostSheetLine =
        costSheetLineService.createProducedProductCostSheetLine(
            manufOrder.getProduct(), manufOrder.getUnit(), producedQty);
    costSheet.addCostSheetLineListItem(producedCostSheetLine);

    Company company = manufOrder.getCompany();
    if (company != null && company.getCurrency() != null) {
      costSheet.setCurrency(company.getCurrency());
    }

    BigDecimal totalToProduceQty = getTotalToProduceQty(manufOrder);
    BigDecimal ratio = BigDecimal.ZERO;
    if (totalToProduceQty.compareTo(BigDecimal.ZERO) != 0) {
      ratio = producedQty.divide(totalToProduceQty, 5, RoundingMode.HALF_UP);
    }

    costSheet.setManufOrderProducedRatio(ratio);

    this.computeRealCostPrice(manufOrder, 0, producedCostSheetLine, previousCostSheetDate);

    this.computeRealResidualProduct(manufOrder);

    this.computeCostPrice(costSheet);
    manufOrder.setCostPrice(costSheet.getCostPrice());
    Beans.get(ManufOrderRepository.class).save(manufOrder);

    return costSheet;
  }

  protected void computeResidualProduct(BillOfMaterial billOfMaterial) throws AxelorException {

    if (this.manageResidualProductOnBom && billOfMaterial.getProdResidualProductList() != null) {

      for (ProdResidualProduct prodResidualProduct : billOfMaterial.getProdResidualProductList()) {

        CostSheetLine costSheetLine =
            costSheetLineService.createResidualProductCostSheetLine(
                prodResidualProduct.getProduct(),
                prodResidualProduct.getUnit(),
                prodResidualProduct.getQty(),
                billOfMaterial.getCompany());

        costSheet.addCostSheetLineListItem(costSheetLine);
      }
    }
  }

  protected BigDecimal computeCostPrice(CostSheet costSheet) {

    BigDecimal costPrice = BigDecimal.ZERO;

    if (costSheet.getCostSheetLineList() != null) {
      for (CostSheetLine costSheetLine : costSheet.getCostSheetLineList()) {

        if (costSheetLine.getCostSheetLineList() != null
            && !costSheetLine.getCostSheetLineList().isEmpty()) {
          costPrice = costPrice.add(this.computeCostPrice(costSheetLine));
        } else {
          costPrice = costPrice.add(costSheetLine.getCostPrice());
        }
      }
    }

    costSheet.setCostPrice(costPrice);

    return costPrice;
  }

  protected BigDecimal computeCostPrice(CostSheetLine parentCostSheetLine) {

    BigDecimal costPrice = BigDecimal.ZERO;

    if (parentCostSheetLine.getCostSheetLineList() != null) {
      for (CostSheetLine costSheetLine : parentCostSheetLine.getCostSheetLineList()) {

        if (costSheetLine.getCostSheetLineList() != null
            && !costSheetLine.getCostSheetLineList().isEmpty()) {
          costPrice = costPrice.add(this.computeCostPrice(costSheetLine));
        } else {
          costPrice = costPrice.add(costSheetLine.getCostPrice());
        }
      }
    }

    parentCostSheetLine.setCostPrice(costPrice);

    return costPrice;
  }

  protected void _computeCostPrice(
      Company company,
      BillOfMaterial billOfMaterial,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      int origin,
      UnitCostCalculation unitCostCalculation)
      throws AxelorException {

    bomLevel++;

    // Cout des composants
    this._computeToConsumeProduct(
        company, billOfMaterial, bomLevel, parentCostSheetLine, origin, unitCostCalculation);

    // Cout des operations
    this._computeProcess(
        billOfMaterial.getProdProcess(),
        billOfMaterial.getQty(),
        billOfMaterial.getProduct().getUnit(),
        bomLevel,
        parentCostSheetLine);
  }

  protected void _computeToConsumeProduct(
      Company company,
      BillOfMaterial billOfMaterial,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      int origin,
      UnitCostCalculation unitCostCalculation)
      throws AxelorException {

    if (billOfMaterial.getBillOfMaterialSet() != null) {

      for (BillOfMaterial billOfMaterialLine : billOfMaterial.getBillOfMaterialSet()) {

        Product product = billOfMaterialLine.getProduct();

        if (product != null) {

          CostSheetLine costSheetLine =
              costSheetLineService.createConsumedProductCostSheetLine(
                  company,
                  product,
                  billOfMaterialLine.getUnit(),
                  bomLevel,
                  parentCostSheetLine,
                  billOfMaterialLine.getQty(),
                  origin,
                  unitCostCalculation);

          BigDecimal wasteRate = billOfMaterialLine.getWasteRate();

          if (wasteRate != null && wasteRate.compareTo(BigDecimal.ZERO) > 0) {
            costSheetLineService.createConsumedProductWasteCostSheetLine(
                company,
                product,
                billOfMaterialLine.getUnit(),
                bomLevel,
                parentCostSheetLine,
                billOfMaterialLine.getQty(),
                wasteRate,
                origin,
                unitCostCalculation);
          }

          if (billOfMaterialLine.getDefineSubBillOfMaterial()) {
            this._computeCostPrice(
                company, billOfMaterialLine, bomLevel, costSheetLine, origin, unitCostCalculation);
          }
        }
      }
    }
  }

  protected void _computeProcess(
      ProdProcess prodProcess,
      BigDecimal producedQty,
      Unit pieceUnit,
      int bomLevel,
      CostSheetLine parentCostSheetLine)
      throws AxelorException {

    if (prodProcess != null && prodProcess.getProdProcessLineList() != null) {

      for (ProdProcessLine prodProcessLine : prodProcess.getProdProcessLineList()) {

        WorkCenter workCenter = prodProcessLine.getWorkCenter();

        if (workCenter != null) {

          int workCenterTypeSelect = workCenter.getWorkCenterTypeSelect();

          if (workCenterTypeSelect == WorkCenterRepository.WORK_CENTER_TYPE_HUMAN
              || workCenterTypeSelect == WorkCenterRepository.WORK_CENTER_TYPE_BOTH) {

            this._computeHumanResourceCost(
                workCenter, prodProcessLine.getPriority(), bomLevel, parentCostSheetLine);
          }
          if (workCenterTypeSelect == WorkCenterRepository.WORK_CENTER_TYPE_MACHINE
              || workCenterTypeSelect == WorkCenterRepository.WORK_CENTER_TYPE_BOTH) {

            this._computeMachineCost(
                prodProcessLine, producedQty, pieceUnit, bomLevel, parentCostSheetLine);
          }
        }
      }
    }
  }

  protected void _computeHumanResourceCost(
      WorkCenter workCenter, int priority, int bomLevel, CostSheetLine parentCostSheetLine)
      throws AxelorException {

    if (workCenter.getProdHumanResourceList() != null) {

      for (ProdHumanResource prodHumanResource : workCenter.getProdHumanResourceList()) {

        this._computeHumanResourceCost(prodHumanResource, priority, bomLevel, parentCostSheetLine);
      }
    }
  }

  protected void _computeHumanResourceCost(
      ProdHumanResource prodHumanResource,
      int priority,
      int bomLevel,
      CostSheetLine parentCostSheetLine)
      throws AxelorException {

    BigDecimal costPerHour = BigDecimal.ZERO;

    if (prodHumanResource.getProduct() != null) {

      Product product = prodHumanResource.getProduct();

      costPerHour =
          unitConversionService.convert(
              hourUnit,
              product.getUnit(),
              product.getCostPrice(),
              appProductionService.getNbDecimalDigitForUnitPrice(),
              product);
    }

    BigDecimal durationHours =
        BigDecimal.valueOf(prodHumanResource.getDuration())
            .divide(
                BigDecimal.valueOf(3600),
                appProductionService.getNbDecimalDigitForUnitPrice(),
                RoundingMode.HALF_EVEN);

    costSheetLineService.createWorkCenterHRCostSheetLine(
        prodHumanResource.getWorkCenter(),
        prodHumanResource,
        priority,
        bomLevel,
        parentCostSheetLine,
        durationHours,
        costPerHour.multiply(durationHours),
        hourUnit);
  }

  protected void _computeMachineCost(
      ProdProcessLine prodProcessLine,
      BigDecimal producedQty,
      Unit pieceUnit,
      int bomLevel,
      CostSheetLine parentCostSheetLine) {

    WorkCenter workCenter = prodProcessLine.getWorkCenter();

    int costType = workCenter.getCostTypeSelect();

    if (costType == WorkCenterRepository.COST_TYPE_PER_CYCLE) {

      costSheetLineService.createWorkCenterMachineCostSheetLine(
          workCenter,
          prodProcessLine.getPriority(),
          bomLevel,
          parentCostSheetLine,
          this.getNbCycle(producedQty, prodProcessLine.getMaxCapacityPerCycle()),
          workCenter.getCostAmount(),
          cycleUnit);

    } else if (costType == WorkCenterRepository.COST_TYPE_PER_HOUR) {

      BigDecimal qty =
          new BigDecimal(prodProcessLine.getDurationPerCycle())
              .divide(
                  new BigDecimal(3600),
                  appProductionService.getNbDecimalDigitForUnitPrice(),
                  RoundingMode.HALF_UP)
              .multiply(this.getNbCycle(producedQty, prodProcessLine.getMaxCapacityPerCycle()));
      BigDecimal costPrice = workCenter.getCostAmount().multiply(qty);

      costSheetLineService.createWorkCenterMachineCostSheetLine(
          workCenter,
          prodProcessLine.getPriority(),
          bomLevel,
          parentCostSheetLine,
          qty,
          costPrice,
          hourUnit);

    } else if (costType == WorkCenterRepository.COST_TYPE_PER_PIECE) {

      BigDecimal costPrice = workCenter.getCostAmount().multiply(producedQty);

      costSheetLineService.createWorkCenterMachineCostSheetLine(
          workCenter,
          prodProcessLine.getPriority(),
          bomLevel,
          parentCostSheetLine,
          producedQty,
          costPrice,
          pieceUnit);
    }
  }

  protected BigDecimal getNbCycle(BigDecimal producedQty, BigDecimal capacityPerCycle) {

    if (capacityPerCycle.compareTo(BigDecimal.ZERO) == 0) {
      return producedQty;
    }

    return producedQty.divide(capacityPerCycle, RoundingMode.CEILING);
  }

  protected void computeRealResidualProduct(ManufOrder manufOrder) throws AxelorException {
    for (StockMoveLine stockMoveLine : manufOrder.getProducedStockMoveLineList()) {
      if (stockMoveLine.getProduct() != null
          && manufOrder.getProduct() != null
          && (!stockMoveLine.getProduct().equals(manufOrder.getProduct()))) {
        CostSheetLine costSheetLine =
            costSheetLineService.createResidualProductCostSheetLine(
                stockMoveLine.getProduct(),
                stockMoveLine.getUnit(),
                stockMoveLine.getRealQty(),
                manufOrder.getCompany());
        costSheet.addCostSheetLineListItem(costSheetLine);
      }
    }
  }

  protected void computeRealCostPrice(
      ManufOrder manufOrder,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      LocalDate previousCostSheetDate)
      throws AxelorException {

    bomLevel++;

    this.computeConsumedProduct(manufOrder, bomLevel, parentCostSheetLine, previousCostSheetDate);
    BigDecimal producedQty = parentCostSheetLine.getConsumptionQty();
    this.computeRealProcess(
        manufOrder.getOperationOrderList(),
        manufOrder.getProduct().getUnit(),
        producedQty,
        bomLevel,
        parentCostSheetLine,
        previousCostSheetDate);
  }

  protected void computeConsumedProduct(
      ManufOrder manufOrder,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      LocalDate previousCostSheetDate)
      throws AxelorException {

    BigDecimal ratio = costSheet.getManufOrderProducedRatio();

    if (manufOrder.getIsConsProOnOperation()) {
      for (OperationOrder operation : manufOrder.getOperationOrderList()) {

        this.computeConsumedProduct(
            bomLevel,
            previousCostSheetDate,
            parentCostSheetLine,
            operation.getConsumedStockMoveLineList(),
            operation.getToConsumeProdProductList(),
            ratio);
      }
    } else {

      this.computeConsumedProduct(
          bomLevel,
          previousCostSheetDate,
          parentCostSheetLine,
          manufOrder.getConsumedStockMoveLineList(),
          manufOrder.getToConsumeProdProductList(),
          ratio);
    }
  }

  protected void computeConsumedProduct(
      int bomLevel,
      LocalDate previousCostSheetDate,
      CostSheetLine parentCostSheetLine,
      List<StockMoveLine> consumedStockMoveLineList,
      List<ProdProduct> toConsumeProdProductList,
      BigDecimal ratio)
      throws AxelorException {

    CostSheet parentCostSheet = parentCostSheetLine.getCostSheet();
    int calculationTypeSelect = parentCostSheet.getCalculationTypeSelect();
    LocalDate calculationDate = parentCostSheet.getCalculationDate();

    Map<List<Object>, BigDecimal> consumedStockMoveLinePerProductAndUnit =
        getTotalQtyPerProductAndUnit(
            consumedStockMoveLineList,
            calculationDate,
            previousCostSheetDate,
            calculationTypeSelect,
            true);

    for (List<Object> keys : consumedStockMoveLinePerProductAndUnit.keySet()) {

      Iterator<Object> iterator = keys.iterator();
      Product product = (Product) iterator.next();
      Unit unit = (Unit) iterator.next();
      BigDecimal realQty = consumedStockMoveLinePerProductAndUnit.get(keys);

      if (product == null) {
        continue;
      }

      BigDecimal valuationQty = BigDecimal.ZERO;

      BigDecimal plannedConsumeQty =
          computeTotalQtyPerUnit(toConsumeProdProductList, product, unit);

      valuationQty = realQty.subtract(plannedConsumeQty.multiply(ratio));

      valuationQty =
          valuationQty.setScale(appBaseService.getNbDecimalDigitForQty(), RoundingMode.HALF_UP);

      if (valuationQty.compareTo(BigDecimal.ZERO) == 0) {
        continue;
      }

      costSheetLineService.createConsumedProductCostSheetLine(
          parentCostSheet.getManufOrder().getCompany(),
          product,
          unit,
          bomLevel,
          parentCostSheetLine,
          valuationQty,
          CostSheetService.ORIGIN_MANUF_ORDER,
          null);
    }
  }

  protected BigDecimal computeTotalProducedQty(
      Product producedProduct,
      List<StockMoveLine> producedStockMoveLineList,
      LocalDate calculationDate,
      LocalDate previousCostSheetDate,
      int calculationTypeSelect)
      throws AxelorException {

    BigDecimal totalQty = BigDecimal.ZERO;

    Map<List<Object>, BigDecimal> producedStockMoveLinePerProductAndUnit =
        getTotalQtyPerProductAndUnit(
            producedStockMoveLineList,
            calculationDate,
            previousCostSheetDate,
            calculationTypeSelect,
            false);

    for (List<Object> keys : producedStockMoveLinePerProductAndUnit.keySet()) {

      Iterator<Object> iterator = keys.iterator();
      Product product = (Product) iterator.next();
      Unit unit = (Unit) iterator.next();
      BigDecimal realQty = producedStockMoveLinePerProductAndUnit.get(keys);

      if (product == null || !product.equals(producedProduct)) {
        continue;
      }

      totalQty =
          totalQty.add(
              unitConversionService.convert(
                  unit, costSheet.getManufOrder().getUnit(), realQty, realQty.scale(), product));
    }

    return totalQty;
  }

  protected BigDecimal computeTotalQtyPerUnit(
      List<ProdProduct> prodProductList, Product product, Unit unit) {

    BigDecimal totalQty = BigDecimal.ZERO;

    for (ProdProduct prodProduct : prodProductList) {
      if (product.equals(prodProduct.getProduct()) && unit.equals(prodProduct.getUnit())) {
        totalQty = totalQty.add(prodProduct.getQty());
      }
    }

    return totalQty;
  }

  protected Map<List<Object>, BigDecimal> getTotalQtyPerProductAndUnit(
      List<StockMoveLine> stockMoveLineList,
      LocalDate calculationDate,
      LocalDate previousCostSheetDate,
      int calculationType,
      boolean isConsumed) {

    Map<List<Object>, BigDecimal> stockMoveLinePerProductAndUnitMap = new HashMap<>();

    if (stockMoveLineList == null) {
      return stockMoveLinePerProductAndUnitMap;
    }

    for (StockMoveLine stockMoveLine : stockMoveLineList) {

      StockMove stockMove = stockMoveLine.getStockMove();

      if (stockMove == null) {
        continue;
      }

      int status = stockMove.getStatusSelect();

      if (isConsumed) {
        if ((calculationType == CostSheetRepository.CALCULATION_WORK_IN_PROGRESS
                || calculationType == CostSheetRepository.CALCULATION_PARTIAL_END_OF_PRODUCTION)
            && status != StockMoveRepository.STATUS_REALIZED) {
          continue;
        }
      } else {
        if (status != StockMoveRepository.STATUS_REALIZED) {
          continue;
        }
      }

      if ((calculationType == CostSheetRepository.CALCULATION_PARTIAL_END_OF_PRODUCTION
              || calculationType == CostSheetRepository.CALCULATION_END_OF_PRODUCTION)
          && previousCostSheetDate != null
          && !previousCostSheetDate.isBefore(stockMove.getRealDate())) {
        continue;

      } else if (calculationType == CostSheetRepository.CALCULATION_WORK_IN_PROGRESS
          && calculationDate.isBefore(stockMove.getRealDate())) {
        continue;
      }

      Product productKey = stockMoveLine.getProduct();
      Unit unitKey = stockMoveLine.getUnit();

      List<Object> keys = new ArrayList<Object>();
      keys.add(productKey);
      keys.add(unitKey);

      BigDecimal qty = stockMoveLinePerProductAndUnitMap.get(keys);

      if (qty == null) {
        qty = BigDecimal.ZERO;
      }

      stockMoveLinePerProductAndUnitMap.put(keys, qty.add(stockMoveLine.getRealQty()));
    }

    return stockMoveLinePerProductAndUnitMap;
  }

  protected void computeRealProcess(
      List<OperationOrder> operationOrders,
      Unit pieceUnit,
      BigDecimal producedQty,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      LocalDate previousCostSheetDate)
      throws AxelorException {
    for (OperationOrder operationOrder : operationOrders) {

      WorkCenter workCenter = operationOrder.getWorkCenter();
      if (workCenter == null) {
        continue;
      }
      int workCenterTypeSelect = workCenter.getWorkCenterTypeSelect();
      if (workCenterTypeSelect == WorkCenterRepository.WORK_CENTER_TYPE_HUMAN
          || workCenterTypeSelect == WorkCenterRepository.WORK_CENTER_TYPE_BOTH) {

        this.computeRealHumanResourceCost(
            operationOrder,
            operationOrder.getPriority(),
            bomLevel,
            parentCostSheetLine,
            previousCostSheetDate);
      }
      if (workCenterTypeSelect == WorkCenterRepository.WORK_CENTER_TYPE_MACHINE
          || workCenterTypeSelect == WorkCenterRepository.WORK_CENTER_TYPE_BOTH) {

        this.computeRealMachineCost(
            operationOrder,
            workCenter,
            producedQty,
            pieceUnit,
            bomLevel,
            parentCostSheetLine,
            previousCostSheetDate);
      }
    }
  }

  protected void computeRealHumanResourceCost(
      OperationOrder operationOrder,
      int priority,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      LocalDate previousCostSheetDate)
      throws AxelorException {
    if (operationOrder.getProdHumanResourceList() != null) {
      Long duration = 0L;
      if (parentCostSheetLine.getCostSheet().getCalculationTypeSelect()
              == CostSheetRepository.CALCULATION_END_OF_PRODUCTION
          || parentCostSheetLine.getCostSheet().getCalculationTypeSelect()
              == CostSheetRepository.CALCULATION_PARTIAL_END_OF_PRODUCTION) {
        Period period =
            previousCostSheetDate != null
                ? Period.between(
                    parentCostSheetLine.getCostSheet().getCalculationDate(), previousCostSheetDate)
                : null;
        duration =
            period != null ? Long.valueOf(period.getDays() * 24) : operationOrder.getRealDuration();
      } else if (parentCostSheetLine.getCostSheet().getCalculationTypeSelect()
          == CostSheetRepository.CALCULATION_WORK_IN_PROGRESS) {

        BigDecimal ratio = costSheet.getManufOrderProducedRatio();

        Long plannedDuration =
            DurationTool.getSecondsDuration(
                    Duration.between(
                        operationOrder.getPlannedStartDateT(), operationOrder.getPlannedEndDateT()))
                * ratio.longValue();
        Long totalPlannedDuration = 0L;
        for (OperationOrder manufOperationOrder :
            operationOrder.getManufOrder().getOperationOrderList()) {
          if (manufOperationOrder.getId() == operationOrder.getId()) {
            totalPlannedDuration += manufOperationOrder.getPlannedDuration();
          }
        }
        duration = Math.abs(totalPlannedDuration - plannedDuration);
      }
      for (ProdHumanResource prodHumanResource : operationOrder.getProdHumanResourceList()) {
        this.computeRealHumanResourceCost(
            prodHumanResource,
            operationOrder.getWorkCenter(),
            priority,
            bomLevel,
            parentCostSheetLine,
            duration);
      }
    }
  }

  protected void computeRealHumanResourceCost(
      ProdHumanResource prodHumanResource,
      WorkCenter workCenter,
      int priority,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      Long realDuration)
      throws AxelorException {
    BigDecimal costPerHour = BigDecimal.ZERO;
    //    if (prodHumanResource.getProduct() != null) {
    //      Product product = prodHumanResource.getProduct();
    //      costPerHour =
    //          unitConversionService.convert(
    //              hourUnit,
    //              product.getUnit(),
    //              product.getCostPrice(),
    //              appProductionService.getNbDecimalDigitForUnitPrice(),
    //              product);
    //    }
    costPerHour = workCenter.getCostAmount();
    BigDecimal durationHours =
        new BigDecimal(realDuration)
            .divide(
                new BigDecimal(3600),
                appProductionService.getNbDecimalDigitForUnitPrice(),
                BigDecimal.ROUND_HALF_EVEN);

    costSheetLineService.createWorkCenterHRCostSheetLine(
        workCenter,
        prodHumanResource,
        priority,
        bomLevel,
        parentCostSheetLine,
        durationHours,
        costPerHour.multiply(durationHours),
        hourUnit);
  }

  protected void computeRealMachineCost(
      OperationOrder operationOrder,
      WorkCenter workCenter,
      BigDecimal producedQty,
      Unit pieceUnit,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      LocalDate previousCostSheetDate) {
    int costType = workCenter.getCostTypeSelect();

    if (costType == WorkCenterRepository.COST_TYPE_PER_CYCLE) {
      costSheetLineService.createWorkCenterMachineCostSheetLine(
          workCenter,
          operationOrder.getPriority(),
          bomLevel,
          parentCostSheetLine,
          this.getNbCycle(producedQty, workCenter.getMaxCapacityPerCycle()),
          workCenter.getCostAmount(),
          cycleUnit);
    } else if (costType == WorkCenterRepository.COST_TYPE_PER_HOUR) {
      BigDecimal qty = BigDecimal.ZERO;

      if (workCenter.getIsRevaluationAtActualPrices()) {

        qty =
            new BigDecimal(operationOrder.getRealDuration())
                .divide(
                    new BigDecimal(3600),
                    appProductionService.getNbDecimalDigitForUnitPrice(),
                    BigDecimal.ROUND_HALF_EVEN);
      } else {

        BigDecimal manufOrderQty = operationOrder.getManufOrder().getQty();
        BigDecimal durationPerCycle =
            new BigDecimal(workCenter.getDurationPerCycle())
                .divide(
                    new BigDecimal(3600),
                    appProductionService.getNbDecimalDigitForUnitPrice(),
                    BigDecimal.ROUND_HALF_EVEN);

        if (manufOrderQty.compareTo(workCenter.getMinCapacityPerCycle()) == 1) {
          BigDecimal maxCapacityPerCycle =
              workCenter.getMaxCapacityPerCycle().compareTo(BigDecimal.ZERO) == 0
                  ? BigDecimal.ONE
                  : workCenter.getMaxCapacityPerCycle();
          qty =
              manufOrderQty
                  .divide(
                      maxCapacityPerCycle,
                      appProductionService.getNbDecimalDigitForUnitPrice(),
                      BigDecimal.ROUND_HALF_EVEN)
                  .multiply(durationPerCycle)
                  .setScale(appBaseService.getNbDecimalDigitForQty(), BigDecimal.ROUND_HALF_EVEN);
        } else {
          qty = durationPerCycle;
        }
      }
      BigDecimal costPrice = workCenter.getCostAmount().multiply(qty);
      costSheetLineService.createWorkCenterMachineCostSheetLine(
          workCenter,
          operationOrder.getPriority(),
          bomLevel,
          parentCostSheetLine,
          qty,
          costPrice,
          hourUnit);
    } else if (costType == WorkCenterRepository.COST_TYPE_PER_PIECE) {

      BigDecimal costPrice = workCenter.getCostAmount().multiply(producedQty);
      costSheetLineService.createWorkCenterMachineCostSheetLine(
          workCenter,
          operationOrder.getPriority(),
          bomLevel,
          parentCostSheetLine,
          producedQty,
          costPrice,
          pieceUnit);
    }
  }

  protected BigDecimal getTotalToProduceQty(ManufOrder manufOrder) throws AxelorException {

    BigDecimal totalProducedQty = BigDecimal.ZERO;

    for (StockMoveLine stockMoveLine : manufOrder.getProducedStockMoveLineList()) {

      if (stockMoveLine.getUnit().equals(manufOrder.getUnit())
          && (stockMoveLine.getStockMove().getStatusSelect() == StockMoveRepository.STATUS_PLANNED
              || stockMoveLine.getStockMove().getStatusSelect()
                  == StockMoveRepository.STATUS_REALIZED)) {
        Product product = stockMoveLine.getProduct();
        totalProducedQty =
            totalProducedQty.add(
                unitConversionService.convert(
                    stockMoveLine.getUnit(),
                    costSheet.getManufOrder().getUnit(),
                    stockMoveLine.getQty(),
                    stockMoveLine.getQty().scale(),
                    product));
      }
    }

    return totalProducedQty;
  }
}
