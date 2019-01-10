/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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

import com.axelor.app.production.db.IWorkCenter;
import com.axelor.apps.base.db.AppProduction;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.production.db.*;
import com.axelor.apps.production.db.repo.BillOfMaterialRepository;
import com.axelor.apps.production.db.repo.CostSheetRepository;
import com.axelor.apps.production.db.repo.ManufOrderRepository;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.production.service.manuforder.ManufOrderService;
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
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CostSheetServiceImpl implements CostSheetService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final int QTY_MAX_SCALE = 10;

  protected UnitConversionService unitConversionService;
  protected CostSheetLineService costSheetLineService;
  protected BillOfMaterialRepository billOfMaterialRepo;
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
      BillOfMaterialRepository billOfMaterialRepo) {

    this.appProductionService = appProductionService;
    this.unitConversionService = unitConversionService;
    this.costSheetLineService = costSheetLineService;
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
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public CostSheet computeCostPrice(BillOfMaterial billOfMaterial) throws AxelorException {

    this.init();

    CostSheetLine producedCostSheetLine =
        costSheetLineService.createProducedProductCostSheetLine(
            billOfMaterial.getProduct(), billOfMaterial.getUnit(), billOfMaterial.getQty());

    costSheet.addCostSheetLineListItem(producedCostSheetLine);
    costSheet.setCalculationTypeSelect(CostSheetRepository.CALCULATION_BILL_OF_MATERIAL);
    costSheet.setCalculationDate(Beans.get(AppBaseService.class).getTodayDate());
    Company company = billOfMaterial.getCompany();
    if (company != null && company.getCurrency() != null) {
      costSheet.setCurrency(company.getCurrency());
    }

    this._computeCostPrice(billOfMaterial, 0, producedCostSheetLine);

    this.computeResidualProduct(billOfMaterial);

    billOfMaterial.setCostPrice(this.computeCostPrice(costSheet));

    billOfMaterial.addCostSheetListItem(costSheet);

    billOfMaterialRepo.save(billOfMaterial);

    return costSheet;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
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

    costSheet.setCalculationTypeSelect(calculationTypeSelect);
    costSheet.setCalculationDate(
        calculationDate != null ? calculationDate : Beans.get(AppBaseService.class).getTodayDate());
    BigDecimal producedQuantity =
        Beans.get(ManufOrderService.class)
            .getProducedQuantity(
                manufOrder,
                calculationTypeSelect == CostSheetRepository.CALCULATION_WORK_IN_PROGRESS
                    ? costSheet.getCalculationDate()
                    : previousCostSheetDate,
                calculationTypeSelect);
    CostSheetLine producedCostSheetLine =
        costSheetLineService.createProducedProductCostSheetLine(
            manufOrder.getProduct(), manufOrder.getBillOfMaterial().getUnit(), producedQuantity);
    costSheet.addCostSheetLineListItem(producedCostSheetLine);

    Company company = manufOrder.getCompany();
    if (company != null && company.getCurrency() != null) {
      costSheet.setCurrency(company.getCurrency());
    }

    this.computeRealCostPrice(manufOrder, 0, producedCostSheetLine, previousCostSheetDate);

    this.computeRealResidualProduct(manufOrder);

    this.computeCostPrice(costSheet);
    manufOrder.addCostSheetListItem(costSheet);
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
                prodResidualProduct.getQty());

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
      BillOfMaterial billOfMaterial, int bomLevel, CostSheetLine parentCostSheetLine)
      throws AxelorException {

    bomLevel++;

    // Cout des composants
    this._computeToConsumeProduct(billOfMaterial, bomLevel, parentCostSheetLine);

    // Cout des operations
    this._computeProcess(
        billOfMaterial.getProdProcess(),
        billOfMaterial.getQty(),
        billOfMaterial.getProduct().getUnit(),
        bomLevel,
        parentCostSheetLine);
  }

  protected void _computeToConsumeProduct(
      BillOfMaterial billOfMaterial, int bomLevel, CostSheetLine parentCostSheetLine)
      throws AxelorException {

    if (billOfMaterial.getBillOfMaterialSet() != null) {

      for (BillOfMaterial billOfMaterialLine : billOfMaterial.getBillOfMaterialSet()) {

        Product product = billOfMaterialLine.getProduct();

        if (product != null) {

          CostSheetLine costSheetLine =
              costSheetLineService.createConsumedProductCostSheetLine(
                  product,
                  billOfMaterialLine.getUnit(),
                  bomLevel,
                  parentCostSheetLine,
                  billOfMaterialLine.getQty());

          BigDecimal wasteRate = billOfMaterialLine.getWasteRate();

          if (wasteRate != null && wasteRate.compareTo(BigDecimal.ZERO) > 0) {
            costSheetLineService.createConsumedProductWasteCostSheetLine(
                product,
                billOfMaterialLine.getUnit(),
                bomLevel,
                parentCostSheetLine,
                billOfMaterialLine.getQty(),
                wasteRate);
          }

          if (billOfMaterialLine.getDefineSubBillOfMaterial()) {
            this._computeCostPrice(billOfMaterialLine, bomLevel, costSheetLine);
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

          if (workCenterTypeSelect == IWorkCenter.WORK_CENTER_HUMAN
              || workCenterTypeSelect == IWorkCenter.WORK_CENTER_BOTH) {

            this._computeHumanResourceCost(
                workCenter, prodProcessLine.getPriority(), bomLevel, parentCostSheetLine);
          }
          if (workCenterTypeSelect == IWorkCenter.WORK_CENTER_MACHINE
              || workCenterTypeSelect == IWorkCenter.WORK_CENTER_BOTH) {

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

    if (costType == IWorkCenter.COST_PER_CYCLE) {

      costSheetLineService.createWorkCenterMachineCostSheetLine(
          workCenter,
          prodProcessLine.getPriority(),
          bomLevel,
          parentCostSheetLine,
          this.getNbCycle(producedQty, prodProcessLine.getMaxCapacityPerCycle()),
          workCenter.getCostAmount(),
          cycleUnit);

    } else if (costType == IWorkCenter.COST_PER_HOUR) {

      BigDecimal qty =
          new BigDecimal(prodProcessLine.getDurationPerCycle())
              .divide(
                  new BigDecimal(3600),
                  appProductionService.getNbDecimalDigitForUnitPrice(),
                  BigDecimal.ROUND_HALF_EVEN)
              .multiply(this.getNbCycle(producedQty, prodProcessLine.getMaxCapacityPerCycle()));
      qty = qty.setScale(QTY_MAX_SCALE, BigDecimal.ROUND_HALF_EVEN);
      BigDecimal costPrice = workCenter.getCostAmount().multiply(qty);

      costSheetLineService.createWorkCenterMachineCostSheetLine(
          workCenter,
          prodProcessLine.getPriority(),
          bomLevel,
          parentCostSheetLine,
          qty,
          costPrice,
          hourUnit);

    } else if (costType == IWorkCenter.COST_PER_PIECE) {

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
                stockMoveLine.getProduct(), stockMoveLine.getUnit(), stockMoveLine.getRealQty());
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

    this.computeConsumedProduct(manufOrder, bomLevel, parentCostSheetLine);
    BigDecimal producedQty =
        Beans.get(ManufOrderService.class).getProducedQuantity(manufOrder, null, 0);
    this.computeRealProcess(
        manufOrder.getOperationOrderList(),
        manufOrder.getProduct().getUnit(),
        producedQty,
        bomLevel,
        parentCostSheetLine,
        previousCostSheetDate);
  }

  protected void computeConsumedProduct(
      ManufOrder manufOrder, int bomLevel, CostSheetLine parentCostSheetLine)
      throws AxelorException {

    BigDecimal qty = BigDecimal.ZERO;
    BigDecimal consumedQuantity = null;
    BigDecimal producedQuantity = null;
    BigDecimal ratio = null;

    if (parentCostSheetLine.getCostSheet().getCalculationTypeSelect()
            == CostSheetRepository.CALCULATION_END_OF_PRODUCTION
        || parentCostSheetLine.getCostSheet().getCalculationTypeSelect()
            == CostSheetRepository.CALCULATION_PARTIAL_END_OF_PRODUCTION) {
      qty =
          this.computeQty(
              manufOrder.getProduct(),
              parentCostSheetLine.getCostSheet().getCalculationDate(),
              manufOrder.getConsumedStockMoveLineList(),
              StockMoveRepository.STATUS_REALIZED,
              true);
    } else if (parentCostSheetLine.getCostSheet().getCalculationTypeSelect()
        == CostSheetRepository.CALCULATION_WORK_IN_PROGRESS) {
      producedQuantity =
          this.computeQty(
              manufOrder.getProduct(),
              parentCostSheetLine.getCostSheet().getCalculationDate(),
              manufOrder.getProducedStockMoveLineList(),
              StockMoveRepository.STATUS_REALIZED,
              false);
      if (producedQuantity.compareTo(BigDecimal.ZERO) == 0) {
        return;
      }
      ratio = producedQuantity.divide(manufOrder.getQty());
    }
    if (manufOrder.getIsConsProOnOperation()) {
      for (OperationOrder operation : manufOrder.getOperationOrderList()) {
        if (operation.getConsumedStockMoveLineList() == null) {
          continue;
        }
        for (StockMoveLine stockMoveLine : operation.getConsumedStockMoveLineList()) {
          if (stockMoveLine.getProduct() == null || qty.compareTo(BigDecimal.ZERO) == 0) {
            continue;
          }
          costSheetLineService.createConsumedProductCostSheetLine(
              stockMoveLine.getProduct(),
              stockMoveLine.getUnit(),
              bomLevel,
              parentCostSheetLine,
              qty);
        }
      }
    } else {
      for (StockMoveLine stockMoveLine : manufOrder.getConsumedStockMoveLineList()) {
        if (parentCostSheetLine.getCostSheetLineList() != null
            && parentCostSheetLine
                    .getCostSheet()
                    .getCostSheetLineList()
                    .stream()
                    .filter(
                        costSheetLine ->
                            costSheetLine.getProduct().equals(stockMoveLine.getProduct())
                                && costSheetLine.getUnit().equals(stockMoveLine.getUnit()))
                    .findFirst()
                != null) {
          continue;
        }
        if (stockMoveLine.getProduct() == null
            || (stockMoveLine.getStockMove() == null
                || (stockMoveLine.getStockMove() != null
                    && stockMoveLine.getStockMove().getStatusSelect()
                        != StockMoveRepository.STATUS_REALIZED))) {
          continue;
        }
        consumedQuantity =
            this.computeQty(
                stockMoveLine.getProduct(),
                parentCostSheetLine.getCostSheet().getCalculationDate(),
                manufOrder.getConsumedStockMoveLineList(),
                StockMoveRepository.STATUS_REALIZED,
                false);
        for (ProdProduct prodProduct : manufOrder.getToConsumeProdProductList()) {
          if (parentCostSheetLine.getCostSheet().getCalculationTypeSelect()
                  == CostSheetRepository.CALCULATION_WORK_IN_PROGRESS
              && stockMoveLine.getProduct().equals(prodProduct.getProduct())
              && prodProduct.getQty().multiply(ratio).compareTo(consumedQuantity) != 0
              && prodProduct.getRealQty().compareTo(stockMoveLine.getRealQty()) != 0) {
            qty = prodProduct.getQty().multiply(ratio).subtract(stockMoveLine.getQty()).abs();
          }
        }
        if (qty.compareTo(BigDecimal.ZERO) == 0
            || (parentCostSheetLine.getCostSheet().getCalculationTypeSelect()
                    == CostSheetRepository.CALCULATION_WORK_IN_PROGRESS
                && qty == stockMoveLine.getRealQty())) {
          continue;
        }

        costSheetLineService.createConsumedProductCostSheetLine(
            stockMoveLine.getProduct(),
            stockMoveLine.getUnit(),
            bomLevel,
            parentCostSheetLine,
            qty);
      }
    }
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

      WorkCenter workCenter = operationOrder.getMachineWorkCenter();
      if (workCenter == null) {
        workCenter = operationOrder.getWorkCenter();
      }
      if (workCenter == null) {
        continue;
      }
      int workCenterTypeSelect = workCenter.getWorkCenterTypeSelect();
      if (workCenterTypeSelect == IWorkCenter.WORK_CENTER_HUMAN
          || workCenterTypeSelect == IWorkCenter.WORK_CENTER_BOTH) {

        this.computeRealHumanResourceCost(
            operationOrder,
            operationOrder.getPriority(),
            bomLevel,
            parentCostSheetLine,
            previousCostSheetDate);
      }
      if (workCenterTypeSelect == IWorkCenter.WORK_CENTER_MACHINE
          || workCenterTypeSelect == IWorkCenter.WORK_CENTER_BOTH) {

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
        BigDecimal producedQuantity =
            this.computeQty(
                operationOrder.getManufOrder().getProduct(),
                parentCostSheetLine.getCostSheet().getCalculationDate(),
                operationOrder.getManufOrder().getProducedStockMoveLineList(),
                StockMoveRepository.STATUS_REALIZED,
                false);
        if (producedQuantity.compareTo(BigDecimal.ZERO) == 0) {
          return;
        }
        BigDecimal ratio = producedQuantity.divide(operationOrder.getManufOrder().getQty());
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

    if (costType == IWorkCenter.COST_PER_CYCLE) {
      costSheetLineService.createWorkCenterMachineCostSheetLine(
          workCenter,
          operationOrder.getPriority(),
          bomLevel,
          parentCostSheetLine,
          this.getNbCycle(producedQty, workCenter.getMaxCapacityPerCycle()),
          workCenter.getCostAmount(),
          cycleUnit);
    } else if (costType == IWorkCenter.COST_PER_HOUR) {
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
                  .setScale(QTY_MAX_SCALE, BigDecimal.ROUND_HALF_EVEN);
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
    } else if (costType == IWorkCenter.COST_PER_PIECE) {

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

  protected BigDecimal computeQty(
      Product product,
      LocalDate calculationDate,
      List<StockMoveLine> stockMoveLineList,
      int stockMoveStatus,
      boolean isAfterCalculationDate) {
    BigDecimal qty = BigDecimal.ZERO;
    stockMoveLineList =
        stockMoveLineList
            .stream()
            .filter(
                stockMoveLine ->
                    product.equals(stockMoveLine.getProduct())
                        && stockMoveLine.getStockMove().getStatusSelect() == stockMoveStatus)
            .collect(Collectors.toList());
    if (isAfterCalculationDate && calculationDate != null) {
      qty =
          stockMoveLineList
              .stream()
              .filter(
                  stockMoveLine ->
                      stockMoveLine.getStockMove().getRealDate().isAfter(calculationDate))
              .map(StockMoveLine::getRealQty)
              .reduce(BigDecimal::add)
              .orElse(BigDecimal.ZERO);
    } else if (!isAfterCalculationDate && calculationDate != null) {
      qty =
          stockMoveLineList
              .stream()
              .filter(
                  stockMoveLine ->
                      !stockMoveLine.getStockMove().getRealDate().isAfter(calculationDate))
              .map(StockMoveLine::getRealQty)
              .reduce(BigDecimal::add)
              .orElse(BigDecimal.ZERO);
    }
    return qty;
  }
}
