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
package com.axelor.apps.production.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.production.db.repo.BillOfMaterialManagementRepository;
import com.axelor.apps.production.db.repo.BillOfMaterialRepository;
import com.axelor.apps.production.db.repo.ConfiguratorBOMRepository;
import com.axelor.apps.production.db.repo.MachineRepository;
import com.axelor.apps.production.db.repo.MachineToolManagementRepository;
import com.axelor.apps.production.db.repo.ManufOrderManagementRepository;
import com.axelor.apps.production.db.repo.ManufOrderRepository;
import com.axelor.apps.production.db.repo.OperationOrderManagementRepository;
import com.axelor.apps.production.db.repo.OperationOrderRepository;
import com.axelor.apps.production.db.repo.ProdProcessManagementRepository;
import com.axelor.apps.production.db.repo.ProdProcessRepository;
import com.axelor.apps.production.db.repo.ProdProductRepository;
import com.axelor.apps.production.db.repo.ProductProductionRepository;
import com.axelor.apps.production.db.repo.ProductionBatchManagementRepository;
import com.axelor.apps.production.db.repo.ProductionBatchRepository;
import com.axelor.apps.production.db.repo.ProductionConfiguratorBOMRepository;
import com.axelor.apps.production.db.repo.RawMaterialRequirementProductionRepository;
import com.axelor.apps.production.db.repo.RawMaterialRequirementRepository;
import com.axelor.apps.production.db.repo.StockMoveLineProductionRepository;
import com.axelor.apps.production.db.repo.StockMoveProductionRepository;
import com.axelor.apps.production.db.repo.UnitCostCalculationManagementRepository;
import com.axelor.apps.production.db.repo.UnitCostCalculationRepository;
import com.axelor.apps.production.service.BillOfMaterialService;
import com.axelor.apps.production.service.BillOfMaterialServiceImpl;
import com.axelor.apps.production.service.MpsChargeService;
import com.axelor.apps.production.service.MpsChargeServiceImpl;
import com.axelor.apps.production.service.MpsWeeklyScheduleService;
import com.axelor.apps.production.service.MpsWeeklyScheduleServiceImpl;
import com.axelor.apps.production.service.MrpForecastProductionService;
import com.axelor.apps.production.service.MrpForecastProductionServiceImpl;
import com.axelor.apps.production.service.MrpLineServiceProductionImpl;
import com.axelor.apps.production.service.MrpServiceProductionImpl;
import com.axelor.apps.production.service.ProdProcessLineService;
import com.axelor.apps.production.service.ProdProcessLineServiceImpl;
import com.axelor.apps.production.service.ProdProductProductionRepository;
import com.axelor.apps.production.service.ProductionProductStockLocationServiceImpl;
import com.axelor.apps.production.service.PurchaseOrderServiceProductionImpl;
import com.axelor.apps.production.service.RawMaterialRequirementService;
import com.axelor.apps.production.service.RawMaterialRequirementServiceImpl;
import com.axelor.apps.production.service.SaleOrderWorkflowServiceProductionImpl;
import com.axelor.apps.production.service.SopService;
import com.axelor.apps.production.service.SopServiceImpl;
import com.axelor.apps.production.service.StockMoveProductionServiceImpl;
import com.axelor.apps.production.service.StockRulesServiceProductionImpl;
import com.axelor.apps.production.service.WorkCenterService;
import com.axelor.apps.production.service.WorkCenterServiceImpl;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.production.service.app.AppProductionServiceImpl;
import com.axelor.apps.production.service.app.ConfiguratorServiceProductionImpl;
import com.axelor.apps.production.service.config.StockConfigProductionService;
import com.axelor.apps.production.service.configurator.ConfiguratorBomService;
import com.axelor.apps.production.service.configurator.ConfiguratorBomServiceImpl;
import com.axelor.apps.production.service.configurator.ConfiguratorCreatorImportServiceProductionImpl;
import com.axelor.apps.production.service.configurator.ConfiguratorProdProcessLineService;
import com.axelor.apps.production.service.configurator.ConfiguratorProdProcessLineServiceImpl;
import com.axelor.apps.production.service.configurator.ConfiguratorProdProcessService;
import com.axelor.apps.production.service.configurator.ConfiguratorProdProcessServiceImpl;
import com.axelor.apps.production.service.configurator.ConfiguratorProdProductService;
import com.axelor.apps.production.service.configurator.ConfiguratorProdProductServiceImpl;
import com.axelor.apps.production.service.configurator.ProductionConfiguratorJaxbIEServiceImpl;
import com.axelor.apps.production.service.costsheet.CostSheetLineService;
import com.axelor.apps.production.service.costsheet.CostSheetLineServiceImpl;
import com.axelor.apps.production.service.costsheet.CostSheetService;
import com.axelor.apps.production.service.costsheet.CostSheetServiceImpl;
import com.axelor.apps.production.service.costsheet.UnitCostCalcLineService;
import com.axelor.apps.production.service.costsheet.UnitCostCalcLineServiceImpl;
import com.axelor.apps.production.service.costsheet.UnitCostCalculationService;
import com.axelor.apps.production.service.costsheet.UnitCostCalculationServiceImpl;
import com.axelor.apps.production.service.manuforder.ManufOrderPrintService;
import com.axelor.apps.production.service.manuforder.ManufOrderPrintServiceImpl;
import com.axelor.apps.production.service.manuforder.ManufOrderReservedQtyService;
import com.axelor.apps.production.service.manuforder.ManufOrderReservedQtyServiceImpl;
import com.axelor.apps.production.service.manuforder.ManufOrderService;
import com.axelor.apps.production.service.manuforder.ManufOrderServiceImpl;
import com.axelor.apps.production.service.operationorder.OperationOrderService;
import com.axelor.apps.production.service.operationorder.OperationOrderServiceImpl;
import com.axelor.apps.production.service.productionorder.ProductionOrderSaleOrderService;
import com.axelor.apps.production.service.productionorder.ProductionOrderSaleOrderServiceImpl;
import com.axelor.apps.production.service.productionorder.ProductionOrderService;
import com.axelor.apps.production.service.productionorder.ProductionOrderServiceImpl;
import com.axelor.apps.production.service.productionorder.ProductionOrderWizardService;
import com.axelor.apps.production.service.productionorder.ProductionOrderWizardServiceImpl;
import com.axelor.apps.sale.service.configurator.ConfiguratorCreatorImportServiceImpl;
import com.axelor.apps.sale.service.configurator.ConfiguratorJaxbIEServiceImpl;
import com.axelor.apps.sale.service.configurator.ConfiguratorServiceImpl;
import com.axelor.apps.stock.db.repo.ProductStockRepository;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.supplychain.db.repo.StockMoveLineSupplychainRepository;
import com.axelor.apps.supplychain.db.repo.StockMoveSupplychainRepository;
import com.axelor.apps.supplychain.service.MrpLineServiceImpl;
import com.axelor.apps.supplychain.service.MrpServiceImpl;
import com.axelor.apps.supplychain.service.ProductStockLocationServiceImpl;
import com.axelor.apps.supplychain.service.PurchaseOrderServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.SaleOrderWorkflowServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.StockMoveServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.StockRulesServiceSupplychainImpl;

public class ProductionModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(ManufOrderRepository.class).to(ManufOrderManagementRepository.class);
    bind(OperationOrderRepository.class).to(OperationOrderManagementRepository.class);
    bind(ProductionOrderService.class).to(ProductionOrderServiceImpl.class);
    bind(BillOfMaterialService.class).to(BillOfMaterialServiceImpl.class);
    bind(ManufOrderService.class).to(ManufOrderServiceImpl.class);
    bind(OperationOrderService.class).to(OperationOrderServiceImpl.class);
    bind(ProductionOrderService.class).to(ProductionOrderServiceImpl.class);
    bind(ProductionOrderWizardService.class).to(ProductionOrderWizardServiceImpl.class);
    bind(ProductionOrderSaleOrderService.class).to(ProductionOrderSaleOrderServiceImpl.class);
    bind(MrpLineServiceImpl.class).to(MrpLineServiceProductionImpl.class);
    bind(MrpServiceImpl.class).to(MrpServiceProductionImpl.class);
    bind(CostSheetService.class).to(CostSheetServiceImpl.class);
    bind(CostSheetLineService.class).to(CostSheetLineServiceImpl.class);
    bind(SaleOrderWorkflowServiceSupplychainImpl.class)
        .to(SaleOrderWorkflowServiceProductionImpl.class);
    bind(StockRulesServiceSupplychainImpl.class).to(StockRulesServiceProductionImpl.class);
    bind(BillOfMaterialRepository.class).to(BillOfMaterialManagementRepository.class);
    bind(StockConfigService.class).to(StockConfigProductionService.class);
    bind(ConfiguratorBomService.class).to(ConfiguratorBomServiceImpl.class);
    bind(ConfiguratorProdProcessService.class).to(ConfiguratorProdProcessServiceImpl.class);
    bind(ConfiguratorProdProcessLineService.class).to(ConfiguratorProdProcessLineServiceImpl.class);
    bind(ConfiguratorServiceImpl.class).to(ConfiguratorServiceProductionImpl.class);
    bind(AppProductionService.class).to(AppProductionServiceImpl.class);
    bind(ProdProcessRepository.class).to(ProdProcessManagementRepository.class);
    bind(StockMoveLineSupplychainRepository.class).to(StockMoveLineProductionRepository.class);
    bind(ProdProcessLineService.class).to(ProdProcessLineServiceImpl.class);
    bind(StockMoveServiceSupplychainImpl.class).to(StockMoveProductionServiceImpl.class);
    bind(ProdProductRepository.class).to(ProdProductProductionRepository.class);
    bind(RawMaterialRequirementService.class).to(RawMaterialRequirementServiceImpl.class);
    bind(RawMaterialRequirementRepository.class)
        .to(RawMaterialRequirementProductionRepository.class);
    bind(ProductionBatchRepository.class).to(ProductionBatchManagementRepository.class);
    bind(UnitCostCalculationRepository.class).to(UnitCostCalculationManagementRepository.class);
    bind(UnitCostCalculationService.class).to(UnitCostCalculationServiceImpl.class);
    bind(UnitCostCalcLineService.class).to(UnitCostCalcLineServiceImpl.class);
    bind(ProductStockRepository.class).to(ProductProductionRepository.class);
    bind(ConfiguratorCreatorImportServiceImpl.class)
        .to(ConfiguratorCreatorImportServiceProductionImpl.class);
    bind(ProductStockLocationServiceImpl.class).to(ProductionProductStockLocationServiceImpl.class);
    bind(StockMoveSupplychainRepository.class).to(StockMoveProductionRepository.class);
    bind(ManufOrderPrintService.class).to(ManufOrderPrintServiceImpl.class);
    bind(MrpForecastProductionService.class).to(MrpForecastProductionServiceImpl.class);
    bind(MpsWeeklyScheduleService.class).to(MpsWeeklyScheduleServiceImpl.class);
    bind(MpsChargeService.class).to(MpsChargeServiceImpl.class);
    bind(MachineRepository.class).to(MachineToolManagementRepository.class);
    bind(PurchaseOrderServiceSupplychainImpl.class).to(PurchaseOrderServiceProductionImpl.class);
    bind(SopService.class).to(SopServiceImpl.class);
    bind(ManufOrderReservedQtyService.class).to(ManufOrderReservedQtyServiceImpl.class);
    bind(WorkCenterService.class).to(WorkCenterServiceImpl.class);
    bind(ConfiguratorProdProductService.class).to(ConfiguratorProdProductServiceImpl.class);
    bind(ConfiguratorJaxbIEServiceImpl.class).to(ProductionConfiguratorJaxbIEServiceImpl.class);
    bind(ConfiguratorBOMRepository.class).to(ProductionConfiguratorBOMRepository.class);
  }
}
