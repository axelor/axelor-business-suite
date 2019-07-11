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
package com.axelor.apps.production.report;

public interface ITranslation {

  public static final String OPERATION_ORDER_TITLE = /*$$(*/ "OperationOrder.title"; /*)*/
  public static final String OPERATION_ORDER_OPERATION_NAME = /*$$(*/
      "OperationOrder.operationName"; /*)*/
  public static final String OPERATION_ORDER_RELATED_TO_PRODUCT = /*$$(*/
      "OperationOrder.relatedToProduct"; /*)*/
  public static final String OPERATION_ORDER_ASSOCIATED_BOM = /*$$(*/
      "OperationOrder.associatedBOM"; /*)*/
  public static final String OPERATION_ORDER_DATES = /*$$(*/ "OperationOrder.dates"; /*)*/
  public static final String OPERATION_ORDER_PLANNED_START_DATE = /*$$(*/
      "OperationOrder.plannedStartDate"; /*)*/
  public static final String OPERATION_ORDER_PLANNED_END_DATE = /*$$(*/
      "OperationOrder.plannedEndDate"; /*)*/
  public static final String OPERATION_ORDER_REAL_START_DATE = /*$$(*/
      "OperationOrder.realStartDate"; /*)*/
  public static final String OPERATION_ORDER_REAL_END_DATE = /*$$(*/
      "OperationOrder.realEndDate"; /*)*/
  public static final String OPERATION_ORDER_OPERATION_REQUIREMENT = /*$$(*/
      "OperationOrder.operationRequirement"; /*)*/
  public static final String OPERATION_ORDER_MACHINE = /*$$(*/ "OperationOrder.machine"; /*)*/
  public static final String OPERATION_ORDER_CYCLE_DURATION = /*$$(*/
      "OperationOrder.cycleDuration"; /*)*/
  public static final String OPERATION_ORDER_START_TIME = /*$$(*/ "OperationOrder.startTime"; /*)*/
  public static final String OPERATION_ORDER_STOP_TIME = /*$$(*/ "OperationOrder.stopTime"; /*)*/
  public static final String OPERATION_ORDER_DELAY_BTW_CYCLE = /*$$(*/
      "OperationOrder.delayBtwCycle"; /*)*/
  public static final String OPERATION_ORDER_OPERATION_DETAILS = /*$$(*/
      "OperationOrder.operationDetails"; /*)*/
  public static final String OPERATION_ORDER_PRODUCT_PREPARE = /*$$(*/
      "OperationOrder.productToPrepare"; /*)*/
  public static final String OPERATION_ORDER_PRODUCT_BARCODE = /*$$(*/
      "OperationOrder.productBarcode"; /*)*/
  public static final String OPERATION_ORDER_PRODUCT_NAME = /*$$(*/
      "OperationOrder.productName"; /*)*/
  public static final String OPERATION_ORDER_QTY = /*$$(*/ "OperationOrder.qty"; /*)*/
  public static final String OPERATION_ORDER_TRACKING_NUMBER = /*$$(*/
      "OperationOrder.trackingNumber"; /*)*/
  public static final String OPERATION_ORDER_NUM = /*$$(*/ "OperationOrder.num"; /*)*/

  public static final String WORK_IN_PROGRESS_VALUATION_TITLE = /*$$(*/
      "WorkInProgressValuation.title"; /*)*/
  public static final String DETAILED_WORK_IN_PROGRESS_VALUATION_TITLE = /*$$(*/
      "DetailedWorkInProgressValuation.title"; /*)*/

  public static final String MANUF_ORDER_COMPANY = /*$$(*/ "ManufOrder.company"; /*)*/
  public static final String MANUF_ORDER_TITLE = /*$$(*/ "ManufOrder.title"; /*)*/
  public static final String MANUF_ORDER_PLANNED_START_DATE = /*$$(*/
      "ManufOrder.plannedStartDate"; /*)*/
  public static final String MANUF_ORDER_PLANNED_END_DATE = /*$$(*/
      "ManufOrder.plannedEndDate"; /*)*/
  public static final String MANUF_ORDER_PRODUCT = /*$$(*/ "ManufOrder.product"; /*)*/
  public static final String MANUF_ORDER_QUANTITY = /*$$(*/ "ManufOrder.quantity"; /*)*/
  public static final String MANUF_ORDER_ORIGIN = /*$$(*/ "ManufOrder.origin"; /*)*/
  public static final String MANUF_ORDER_BILL_OF_MATERIAL = /*$$(*/
      "ManufOrder.billOfMaterial"; /*)*/
  public static final String MANUF_ORDER_PRODUCTION_PROCESS = /*$$(*/
      "ManufOrder.productionProcess"; /*)*/
  public static final String MANUF_ORDER_OPERATIONS = /*$$(*/ "ManufOrder.operations"; /*)*/
  public static final String MANUF_ORDER_OPERATION_DETAILS = /*$$(*/
      "ManufOrder.operationDetails"; /*)*/
  public static final String MANUF_ORDER_FORECAST = /*$$(*/ "ManufOrder.forecast"; /*)*/
  public static final String MANUF_ORDER_EFFECTIVE = /*$$(*/ "ManufOrder.effective"; /*)*/
  public static final String MANUF_ORDER_NUM = /*$$(*/ "ManufOrder.num"; /*)*/
  public static final String MANUF_ORDER_SEQ = /*$$(*/ "ManufOrder.seq"; /*)*/
  public static final String MANUF_ORDER_MACHINE = /*$$(*/ "ManufOrder.machine"; /*)*/
  public static final String MANUF_ORDER_OPERATION = /*$$(*/ "ManufOrder.operation"; /*)*/
  public static final String MANUF_ORDER_START_DATE = /*$$(*/ "ManufOrder.startDate"; /*)*/
  public static final String MANUF_ORDER_END_DATE = /*$$(*/ "ManufOrder.endDate"; /*)*/
  public static final String MANUF_ORDER_EDITION_DATE = /*$$(*/ "ManufOrder.editionDate"; /*)*/
  public static final String MANUF_ORDER_STATUS = /*$$(*/ "ManufOrder.status"; /*)*/
  public static final String MANUF_ORDER_PRODUCTS_TO_PREPARE = /*$$(*/
      "ManufOrder.productsToPrepare"; /*)*/
  public static final String MANUF_ORDER_PRODUCT_CODE = /*$$(*/ "ManufOrder.productCode"; /*)*/
  public static final String MANUF_ORDER_PRODUCT_NAME = /*$$(*/ "ManufOrder.productName"; /*)*/
  public static final String MANUF_ORDER_PRODUCT_BARCODE = /*$$(*/
      "ManufOrder.productBarcode"; /*)*/
  public static final String MANUF_ORDER_TRACKING_NUMBER = /*$$(*/
      "ManufOrder.trackingNumber"; /*)*/
  public static final String MANUF_ORDER_FROM = /*$$(*/ "ManufOrder.from"; /*)*/
  public static final String MANUF_ORDER_TO = /*$$(*/ "ManufOrder.to"; /*)*/
  public static final String MANUF_ORDER_SERIAL = /*$$(*/ "ManufOrder.serial"; /*)*/
  public static final String MANUF_ORDER_STOCK_LOCATION = /*$$(*/ "ManufOrder.stockLocation"; /*)*/
  public static final String MANUF_ORDER_NO_LOCATION = /*$$(*/ "ManufOrder.noLocation"; /*)*/
  public static final String MANUF_ORDER_RACK = /*$$(*/ "ManufOrder.rack"; /*)*/
  public static final String MANUF_ORDER_WORK_CENTER = /*$$(*/ "ManufOrder.workCenter"; /*)*/
  public static final String MANUF_COST_PRICE = /*$$(*/ "ManufOrder.costPrice"; /*)*/
  public static final String MANUF_ORDER_IN_PROGRESS = /*$$(*/ "ManufOrder.inProgress"; /*)*/
  public static final String MANUF_ORDER_STANDBY = /*$$(*/ "ManufOrder.standBy"; /*)*/
  public static final String MANUF_ORDER_COMPANY_TOTAL = /*$$(*/ "ManufOrder.total"; /*)*/

  public static final String PRODUCTION_PROCESS_LABEL = /*$$(*/ "ProdProcess.label"; /*)*/
  public static final String PRODUCTION_PROCESS_NAME = /*$$(*/ "ProdProcess.name"; /*)*/
  public static final String PRODUCTION_PROCESS_STOCK_LOCATION = /*$$(*/
      "ProdProcess.stockLocation"; /*)*/
  public static final String PRODUCTION_PROCESS_FINAL_STOCK_LOCATION = /*$$(*/
      "ProdProcess.producedProductStockLocation"; /*)*/
  public static final String PRODUCTION_PROCESS_PRODUCT = /*$$(*/ "ProdProcess.product"; /*)*/
  public static final String PRODUCTION_PROCESS_ISOPERATION = /*$$(*/
      "ProdProcess.isOperation"; /*)*/
  public static final String PRODUCTION_PROCESS_ISOUTSOURCING = /*$$(*/
      "ProdProcess.isOutsourcing"; /*)*/
  public static final String PRODUCTION_PROCESS_COMPANY = /*$$(*/ "ProdProcess.company"; /*)*/
  public static final String PRODUCTION_PROCESS_PHASES = /*$$(*/ "ProdProcess.phases"; /*)*/
  public static final String PRODUCTION_PROCESS_PRIORITY = /*$$(*/ "ProdProcess.priority"; /*)*/
  public static final String PRODUCTION_PROCESS_WORK_CENTER = /*$$(*/
      "ProdProcess.workcenter"; /*)*/
  public static final String PRODUCTION_PROCESS_OUTSOURCING = /*$$(*/
      "ProdProcess.outsourcing"; /*)*/
  public static final String PRODUCTION_PROCESS_DESCRIPTION = /*$$(*/
      "ProdProcess.description"; /*)*/

  public static final String PRODUCTION_PROCESS_QTY = /*$$(*/ "ProdProcess.qty"; /*)*/
  public static final String PRODUCTION_PROCESS_UNIT = /*$$(*/ "ProdProcess.unit"; /*)*/
  public static final String PRODUCTION_PROCESS_CONSUME_PRODUCT = /*$$(*/
      "ProdProcess.consumeProduct"; /*)*/

  public static final String BILL_OF_MATERIAL_TITLE = /*$$(*/ "BOM.title"; /*)*/
  public static final String BILL_OF_MATERIAL_COMPANY = /*$$(*/ "BOM.company"; /*)*/
  public static final String BILL_OF_MATERIAL_UNIT = /*$$(*/ "BOM.unit"; /*)*/
  public static final String BILL_OF_MATERIAL_QTY = /*$$(*/ "BOM.qty"; /*)*/
  public static final String BILL_OF_MATERIAL_VERSION_NUMBER = /*$$(*/ "BOM.versionNumber"; /*)*/
  public static final String BILL_OF_MATERIAL_COMPONENTS = /*$$(*/ "BOM.billOfMaterialList"; /*)*/
  public static final String BILL_OF_MATERIAL_PRIORITY = /*$$(*/ "BOM.priority"; /*)*/
  public static final String BILL_OF_MATERIAL_DEFINE_SUB_BOM = /*$$(*/
      "BOM.defineSubBillOfMaterial"; /*)*/
  public static final String BILL_OF_MATERIAL_HAS_NO_MANAGE_STOCK = /*$$(*/
      "BOM.hasNoManageStock"; /*)*/

  String RAW_MATERIAL_REQUIREMENT_TITLE = /*$$(*/ "RawMaterialRequirement.title"; /*)*/
  String RAW_MATERIAL_REQUIREMENT_COMPANY = /*$$(*/ "RawMaterialRequirement.company"; /*)*/
  String RAW_MATERIAL_REQUIREMENT_FROM = /*$$(*/ "RawMaterialRequirement.from"; /*)*/
  String RAW_MATERIAL_REQUIREMENT_TO = /*$$(*/ "RawMaterialRequirement.to"; /*)*/
  String RAW_MATERIAL_REQUIREMENT_PRODUCT = /*$$(*/ "RawMaterialRequirement.product"; /*)*/
  String RAW_MATERIAL_REQUIREMENT_MANUF_ORDER = /*$$(*/ "RawMaterialRequirement.manufOrder"; /*)*/
  String RAW_MATERIAL_REQUIREMENT_STOCK_LOCATION = /*$$(*/
      "RawMaterialRequirement.stockLocation"; /*)*/
  String RAW_MATERIAL_REQUIREMENT_STOCK_LOCATIONS = /*$$(*/
      "RawMaterialRequirement.stockLocations"; /*)*/
  String RAW_MATERIAL_REQUIREMENT_NEEDED_QUANTITY = /*$$(*/
      "RawMaterialRequirement.neededQuantity"; /*)*/
  String RAW_MATERIAL_REQUIREMENT_MISSING_QUANTITY = /*$$(*/
      "RawMaterialRequirement.missingQuantity"; /*)*/
  String RAW_MATERIAL_REQUIREMENT_AVAILABLE = /*$$(*/ "RawMaterialRequirement.available"; /*)*/

  public static final String COST_SHEET_TITLE = /*$$(*/ "CostSheet.title"; /*)*/
  public static final String COST_SHEET_COST_PRICE = /*$$(*/ "CostSheet.costPrice"; /*)*/
  public static final String COST_SHEET_COMPANY = /*$$(*/ "CostSheet.company"; /*)*/
  public static final String COST_SHEET_MANUF_ORDER = /*$$(*/ "CostSheet.manufOrder"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINES = /*$$(*/ "CostSheet.costSheetLines"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_CODE = /*$$(*/
      "CostSheet.costSheetLineCode"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_NAME = /*$$(*/
      "CostSheet.costSheetLineName"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_BOM_LEVEL = /*$$(*/
      "CostSheet.costSheetLineBomLevel"; /*)*/
  public static final String COST_SHEET_GROUP = /*$$(*/
      "CostSheet.costSheetLineCostSheetGroup"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_CONSUMPTION_QTY = /*$$(*/
      "CostSheet.costSheetLineConsumptionQty"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_UNIT = /*$$(*/
      "CostSheet.costSheetLineUnit"; /*)*/
  public static final String COST_SHEET_CREATED_ON = /*$$(*/ "CostSheet.createdOn"; /*)*/
  public static final String COST_SHEET_CREATED_BY = /*$$(*/ "CostSheet.createdBy"; /*)*/
  public static final String COST_SHEET_DATE_OF_EDITION = /*$$(*/ "CostSheet.dateOfEdition"; /*)*/
  public static final String COST_SHEET_MO_NUMBER = /*$$(*/ "CostSheet.ManufOrderNumber"; /*)*/
  public static final String COST_SHEET_BILL_OF_MATERIAL = /*$$(*/ "CostSheet.billOfMaterial"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_TYPE = /*$$(*/ "CostSheet.lineType"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_TYPE_PRODUCE_PRODUCT = /*$$(*/
      "CostSheet.lineTypeProduceProduct"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_TYPE_CONSUME_PRODUCT = /*$$(*/
      "CostSheet.lineTypeConsumeProduct"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_TYPE_WORK_CENTER = /*$$(*/
      "CostSheet.lineTypeWorkCenter"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_TYPE_INDIRECT_COST = /*$$(*/
      "CostSheet.lineTypeIndirectCost"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_TYPE_CONSUME_PRODUCT_WASTE = /*$$(*/
      "CostSheet.lineTypeConsumeProductWaste"; /*)*/
  public static final String COST_SHEET_COST_SHEET_LINE_TYPE_HUMAN = /*$$(*/
      "CostSheet.lineTypeHuman"; /*)*/
  public static final String COST_SHEET_CURRENCY = /*$$(*/ "CostSheet.currency"; /*)*/
  public static final String COST_SHEET_LEGEND = /*$$(*/ "CostSheet.legend"; /*)*/
  public static final String COST_SHEET_CALCULATION_TYPE = /*$$(*/
      "CostSheet.calculationType"; /*)*/
  public static final String COST_SHEET_CALCULATION_TYPE_PARTIAL_END_OF_PRODUCTION = /*$$(*/
      "CostSheet.calculationTypePartialEndOfProduction"; /*)*/
  public static final String COST_SHEET_CALCULATION_TYPE_END_OF_PRODUCTION = /*$$(*/
      "CostSheet.calculationTypeEndOfProduction"; /*)*/
  public static final String COST_SHEET_CALCULATION_TYPE_WORK_IN_PROGRESS = /*$$(*/
      "CostSheet.calculationTypeWorkInProgress"; /*)*/
  public static final String COST_SHEET_CALCULATION_TYPE_BILL_OF_MATERIAL = /*$$(*/
      "CostSheet.calculationTypeBillOfMaterial"; /*)*/
  public static final String COST_SHEET_CALCULATION_DATE = /*$$(*/
      "CostSheet.calculationDate"; /*)*/
}
