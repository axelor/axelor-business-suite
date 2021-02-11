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

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.UnitRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.ShippingCoefService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.CostSheetGroup;
import com.axelor.apps.production.db.CostSheetLine;
import com.axelor.apps.production.db.ProdHumanResource;
import com.axelor.apps.production.db.UnitCostCalcLine;
import com.axelor.apps.production.db.UnitCostCalculation;
import com.axelor.apps.production.db.WorkCenter;
import com.axelor.apps.production.db.repo.CostSheetGroupRepository;
import com.axelor.apps.production.db.repo.CostSheetLineRepository;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.apps.purchase.db.SupplierCatalog;
import com.axelor.apps.stock.service.WeightedAveragePriceService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CostSheetLineServiceImpl implements CostSheetLineService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AppBaseService appBaseService;
  protected AppProductionService appProductionService;
  protected CostSheetGroupRepository costSheetGroupRepository;
  protected UnitConversionService unitConversionService;
  protected UnitRepository unitRepo;
  protected WeightedAveragePriceService weightedAveragePriceService;
  protected UnitCostCalcLineServiceImpl unitCostCalcLineServiceImpl;
  protected CurrencyService currencyService;
  protected ShippingCoefService shippingCoefService;
  protected ProductCompanyService productCompanyService;

  @Inject
  public CostSheetLineServiceImpl(
      AppBaseService appBaseService,
      AppProductionService appProductionService,
      CostSheetGroupRepository costSheetGroupRepository,
      UnitConversionService unitConversionService,
      UnitRepository unitRepo,
      WeightedAveragePriceService weightedAveragePriceService,
      UnitCostCalcLineServiceImpl unitCostCalcLineServiceImpl,
      CurrencyService currencyService,
      ShippingCoefService shippingCoefService,
      ProductCompanyService productCompanyService) {
    this.appBaseService = appBaseService;
    this.appProductionService = appProductionService;
    this.costSheetGroupRepository = costSheetGroupRepository;
    this.unitConversionService = unitConversionService;
    this.unitRepo = unitRepo;
    this.weightedAveragePriceService = weightedAveragePriceService;
    this.unitCostCalcLineServiceImpl = unitCostCalcLineServiceImpl;
    this.currencyService = currencyService;
    this.shippingCoefService = shippingCoefService;
    this.productCompanyService = productCompanyService;
  }

  public CostSheetLine createCostSheetLine(
      String name,
      String code,
      int bomLevel,
      BigDecimal consumptionQty,
      BigDecimal costPrice,
      CostSheetGroup costSheetGroup,
      Product product,
      int typeSelect,
      int typeSelectIcon,
      Unit unit,
      WorkCenter workCenter,
      CostSheetLine parentCostSheetLine) {

    logger.debug(
        "Add a new line of cost sheet ({} - {} - BOM level {} - cost price : {})",
        code,
        name,
        bomLevel,
        costPrice);

    CostSheetLine costSheetLine = new CostSheetLine(code, name);
    costSheetLine.setBomLevel(bomLevel);
    costSheetLine.setConsumptionQty(
        consumptionQty.setScale(appBaseService.getNbDecimalDigitForQty(), RoundingMode.HALF_EVEN));
    costSheetLine.setCostSheetGroup(costSheetGroup);
    costSheetLine.setProduct(product);
    costSheetLine.setTypeSelect(typeSelect);
    costSheetLine.setTypeSelectIcon(typeSelectIcon);
    if (unit != null) {
      costSheetLine.setUnit(unitRepo.find(unit.getId()));
    }
    costSheetLine.setWorkCenter(workCenter);

    if (costPrice == null) {
      costPrice = BigDecimal.ZERO;
    }

    costSheetLine.setCostPrice(
        costPrice.setScale(
            appProductionService.getNbDecimalDigitForUnitPrice(), BigDecimal.ROUND_HALF_EVEN));

    if (parentCostSheetLine != null) {
      parentCostSheetLine.addCostSheetLineListItem(costSheetLine);
      this.createIndirectCostSheetGroups(
          costSheetGroup, parentCostSheetLine, costSheetLine.getCostPrice());
    }

    return costSheetLine;
  }

  public CostSheetLine createProducedProductCostSheetLine(
      Product product, Unit unit, BigDecimal consumptionQty) {

    return this.createCostSheetLine(
        product.getName(),
        product.getCode(),
        0,
        consumptionQty,
        null,
        product.getCostSheetGroup(),
        product,
        CostSheetLineRepository.TYPE_PRODUCED_PRODUCT,
        CostSheetLineRepository.TYPE_PRODUCED_PRODUCT,
        unit,
        null,
        null);
  }

  public CostSheetLine createResidualProductCostSheetLine(
      Product product, Unit unit, BigDecimal consumptionQty, Company company)
      throws AxelorException {

    if (appProductionService.getAppProduction().getSubtractProdResidualOnCostSheet()) {
      consumptionQty = consumptionQty.negate();
    }

    BigDecimal costPrice =
        unitConversionService
            .convert(
                unit,
                (Unit) productCompanyService.get(product, "unit", company),
                (BigDecimal) productCompanyService.get(product, "costPrice", company),
                appProductionService.getNbDecimalDigitForUnitPrice(),
                product)
            .multiply(consumptionQty);

    return this.createCostSheetLine(
        (String) productCompanyService.get(product, "name", company),
        (String) productCompanyService.get(product, "code", company),
        0,
        consumptionQty,
        costPrice,
        (CostSheetGroup) productCompanyService.get(product, "costSheetGroup", company),
        product,
        CostSheetLineRepository.TYPE_PRODUCED_PRODUCT,
        CostSheetLineRepository.TYPE_PRODUCED_PRODUCT,
        unit,
        null,
        null);
  }

  public CostSheetLine createConsumedProductCostSheetLine(
      Company company,
      Product product,
      Unit unit,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      BigDecimal consumptionQty,
      int origin,
      UnitCostCalculation unitCostCalculation)
      throws AxelorException {

    Product parentProduct = parentCostSheetLine.getProduct();

    BigDecimal costPrice = null;
    switch (origin) {
      case CostSheetService.ORIGIN_MANUF_ORDER:
        costPrice =
            this.getComponentCostPrice(
                product, parentProduct.getManufOrderCompValuMethodSelect(), company);
        break;

      case CostSheetService.ORIGIN_BULK_UNIT_COST_CALCULATION:
        BillOfMaterial componentDefaultBillOfMaterial = product.getDefaultBillOfMaterial();
        if (componentDefaultBillOfMaterial != null) {

          UnitCostCalcLine unitCostCalcLine =
              unitCostCalcLineServiceImpl.getUnitCostCalcLine(unitCostCalculation, product);
          if (unitCostCalcLine != null) {
            costPrice = unitCostCalcLine.getComputedCost();
            break;
          }
        }

        costPrice =
            this.getComponentCostPrice(
                product, parentProduct.getBomCompValuMethodSelect(), company);
        break;
        // If we didn't have a computed price in cost calculation session, so we compute the price
        // from its bill of materials
      case CostSheetService.ORIGIN_BILL_OF_MATERIAL:
        costPrice =
            this.getComponentCostPrice(
                product, parentProduct.getBomCompValuMethodSelect(), company);
        break;

      default:
        costPrice = BigDecimal.ZERO;
    }

    consumptionQty =
        consumptionQty.setScale(appBaseService.getNbDecimalDigitForQty(), RoundingMode.HALF_EVEN);

    costPrice = costPrice.multiply(consumptionQty);
    costPrice =
        unitConversionService.convert(
            unit,
            product.getUnit(),
            costPrice,
            appProductionService.getNbDecimalDigitForUnitPrice(),
            product);

    List<CostSheetLine> costSheetLineList =
        parentCostSheetLine.getCostSheetLineList() != null
            ? parentCostSheetLine.getCostSheetLineList()
            : new ArrayList<CostSheetLine>();
    for (CostSheetLine costSheetLine : costSheetLineList) {
      if (product.equals(costSheetLine.getProduct()) && unit.equals(costSheetLine.getUnit())) {
        BigDecimal qty = costSheetLine.getConsumptionQty().add(consumptionQty);
        costSheetLine.setConsumptionQty(qty);
        costSheetLine.setCostPrice(
            costPrice
                .add(costSheetLine.getCostPrice())
                .setScale(
                    appProductionService.getNbDecimalDigitForUnitPrice(),
                    BigDecimal.ROUND_HALF_EVEN));
        return costSheetLine;
      }
    }

    return this.createCostSheetLine(
        product.getName(),
        product.getCode(),
        bomLevel,
        consumptionQty,
        costPrice,
        product.getCostSheetGroup(),
        product,
        CostSheetLineRepository.TYPE_CONSUMED_PRODUCT,
        CostSheetLineRepository.TYPE_CONSUMED_PRODUCT,
        unit,
        null,
        parentCostSheetLine);
  }

  protected BigDecimal getComponentCostPrice(
      Product product, int componentsValuationMethod, Company company) throws AxelorException {

    BigDecimal price = null;
    Currency companyCurrency = company.getCurrency();

    if (componentsValuationMethod == ProductRepository.COMPONENTS_VALUATION_METHOD_AVERAGE) {
      price = weightedAveragePriceService.computeAvgPriceForCompany(product, company);
      if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
        price = weightedAveragePriceService.getNotWeightedAveragePricePerCompany(product, company);
      }

      if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
        price = (BigDecimal) productCompanyService.get(product, "costPrice", company);
      }
    } else if (componentsValuationMethod == ProductRepository.COMPONENTS_VALUATION_METHOD_COST) {
      price = (BigDecimal) productCompanyService.get(product, "costPrice", company);

      if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
        price = weightedAveragePriceService.computeAvgPriceForCompany(product, company);
      }
      if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
        price = weightedAveragePriceService.getNotWeightedAveragePricePerCompany(product, company);
      }
    }

    if (product.getPurchasable() && (price == null || price.compareTo(BigDecimal.ZERO) == 0)) {

      price = (BigDecimal) productCompanyService.get(product, "purchasePrice", company);
      price =
          unitConversionService.convert(
              product.getUnit(),
              product.getPurchasesUnit() != null ? product.getPurchasesUnit() : product.getUnit(),
              price,
              appProductionService.getNbDecimalDigitForUnitPrice(),
              product);

      BigDecimal shippingCoef =
          shippingCoefService.getShippingCoef(
              product,
              (Partner) productCompanyService.get(product, "defaultSupplierPartner", company),
              company,
              new BigDecimal(9999999));

      price = price.multiply(shippingCoef);

      if (productCompanyService.get(product, "purchaseCurrency", company) == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.MISSING_PRODUCT_PURCHASE_CURRENCY),
            product.getFullName());
      }

      price =
          currencyService.getAmountCurrencyConvertedAtDate(
              (Currency) productCompanyService.get(product, "purchaseCurrency", company),
              companyCurrency,
              price,
              appProductionService.getTodayDate(company));

      if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
        @SuppressWarnings("unchecked")
        List<SupplierCatalog> supplierCatalogList =
            (List<SupplierCatalog>)
                productCompanyService.get(product, "supplierCatalogList", company);
        for (SupplierCatalog supplierCatalog : supplierCatalogList) {
          if (BigDecimal.ZERO.compareTo(supplierCatalog.getPrice()) < 0) {
            price =
                unitConversionService.convert(
                    product.getUnit(),
                    product.getPurchasesUnit() != null
                        ? product.getPurchasesUnit()
                        : product.getUnit(),
                    supplierCatalog.getPrice(),
                    appProductionService.getNbDecimalDigitForUnitPrice(),
                    product);
            Partner supplierPartner = supplierCatalog.getSupplierPartner();
            if (supplierPartner != null) {
              shippingCoef =
                  shippingCoefService.getShippingCoef(
                      product, supplierPartner, company, new BigDecimal(9999999));
              price = price.multiply(shippingCoef);

              price =
                  currencyService.getAmountCurrencyConvertedAtDate(
                      supplierPartner.getCurrency(),
                      companyCurrency,
                      price,
                      appProductionService.getTodayDate(company));
            }
            break;
          }
        }
      }
    }
    return price;
  }

  public CostSheetLine createConsumedProductWasteCostSheetLine(
      Company company,
      Product product,
      Unit unit,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      BigDecimal consumptionQty,
      BigDecimal wasteRate,
      int origin,
      UnitCostCalculation unitCostCalculation)
      throws AxelorException {

    Product parentProduct = parentCostSheetLine.getProduct();

    BigDecimal qty =
        consumptionQty
            .multiply(wasteRate)
            .divide(
                new BigDecimal("100"),
                appBaseService.getNbDecimalDigitForQty(),
                BigDecimal.ROUND_HALF_EVEN);

    BigDecimal costPrice = null;
    switch (origin) {
      case CostSheetService.ORIGIN_BULK_UNIT_COST_CALCULATION:
        BillOfMaterial componentDefaultBillOfMaterial = product.getDefaultBillOfMaterial();
        if (componentDefaultBillOfMaterial != null) {

          UnitCostCalcLine unitCostCalcLine =
              unitCostCalcLineServiceImpl.getUnitCostCalcLine(unitCostCalculation, product);
          if (unitCostCalcLine != null) {
            costPrice = unitCostCalcLine.getComputedCost();
            break;
          }
        }

        costPrice =
            this.getComponentCostPrice(
                product, parentProduct.getBomCompValuMethodSelect(), company);
        break;

      case CostSheetService.ORIGIN_BILL_OF_MATERIAL:
        costPrice =
            this.getComponentCostPrice(
                product, parentProduct.getBomCompValuMethodSelect(), company);
        break;

      default:
        costPrice = BigDecimal.ZERO;
    }

    costPrice =
        unitConversionService
            .convert(
                unit,
                product.getUnit(),
                costPrice,
                appProductionService.getNbDecimalDigitForUnitPrice(),
                product)
            .multiply(qty);

    return this.createCostSheetLine(
        product.getName(),
        product.getCode(),
        bomLevel,
        qty,
        costPrice.setScale(
            appProductionService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_EVEN),
        product.getCostSheetGroup(),
        product,
        CostSheetLineRepository.TYPE_CONSUMED_PRODUCT_WASTE,
        CostSheetLineRepository.TYPE_CONSUMED_PRODUCT_WASTE,
        unit,
        null,
        parentCostSheetLine);
  }

  public CostSheetLine createWorkCenterHRCostSheetLine(
      WorkCenter workCenter,
      ProdHumanResource prodHumanResource,
      int priority,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      BigDecimal consumptionQty,
      BigDecimal costPrice,
      Unit unit) {

    return this.createWorkCenterCostSheetLine(
        workCenter,
        priority,
        bomLevel,
        parentCostSheetLine,
        consumptionQty,
        costPrice,
        unit,
        null,
        CostSheetLineRepository.TYPE_HUMAN);
  }

  public CostSheetLine createWorkCenterMachineCostSheetLine(
      WorkCenter workCenter,
      int priority,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      BigDecimal consumptionQty,
      BigDecimal costPrice,
      Unit unit) {

    return this.createWorkCenterCostSheetLine(
        workCenter,
        priority,
        bomLevel,
        parentCostSheetLine,
        consumptionQty,
        costPrice,
        unit,
        workCenter.getCostSheetGroup(),
        CostSheetLineRepository.TYPE_WORK_CENTER);
  }

  protected CostSheetLine createWorkCenterCostSheetLine(
      WorkCenter workCenter,
      int priority,
      int bomLevel,
      CostSheetLine parentCostSheetLine,
      BigDecimal consumptionQty,
      BigDecimal costPrice,
      Unit unit,
      CostSheetGroup costSheetGroup,
      int typeSelectIcon) {

    return this.createCostSheetLine(
        workCenter.getName(),
        priority + " - " + workCenter.getCode(),
        bomLevel,
        consumptionQty,
        costPrice,
        costSheetGroup,
        null,
        CostSheetLineRepository.TYPE_WORK_CENTER,
        typeSelectIcon,
        unit,
        workCenter,
        parentCostSheetLine);
  }

  protected List<CostSheetGroup> getIndirectCostSheetGroups(CostSheetGroup costSheetGroup) {

    if (costSheetGroup == null) {
      return Lists.newArrayList();
    }

    return costSheetGroupRepository
        .all()
        .filter(
            "?1 member of self.costSheetGroupSet AND self.costTypeSelect = ?2",
            costSheetGroup,
            CostSheetGroupRepository.COST_TYPE_INDIRECT)
        .fetch();
  }

  protected void createIndirectCostSheetGroups(
      CostSheetGroup costSheetGroup, CostSheetLine parentCostSheetLine, BigDecimal costPrice) {

    if (costSheetGroup == null) {
      return;
    }

    for (CostSheetGroup indirectCostSheetGroup : this.getIndirectCostSheetGroups(costSheetGroup)) {

      this.createIndirectCostSheetLine(parentCostSheetLine, indirectCostSheetGroup, costPrice);
    }
  }

  protected CostSheetLine createIndirectCostSheetLine(
      CostSheetLine parentCostSheetLine, CostSheetGroup costSheetGroup, BigDecimal costPrice) {

    CostSheetLine indirectCostSheetLine =
        this.getCostSheetLine(costSheetGroup, parentCostSheetLine);

    if (indirectCostSheetLine == null) {
      indirectCostSheetLine =
          this.createCostSheetLine(
              costSheetGroup.getCode(),
              costSheetGroup.getName(),
              parentCostSheetLine.getBomLevel() + 1,
              BigDecimal.ONE,
              null,
              costSheetGroup,
              null,
              CostSheetLineRepository.TYPE_INDIRECT_COST,
              CostSheetLineRepository.TYPE_INDIRECT_COST,
              null,
              null,
              parentCostSheetLine);
      parentCostSheetLine.addCostSheetLineListItem(indirectCostSheetLine);
    }

    indirectCostSheetLine.setCostPrice(
        indirectCostSheetLine
            .getCostPrice()
            .add(this.getIndirectCostPrice(costSheetGroup, costPrice)));

    return indirectCostSheetLine;
  }

  protected BigDecimal getIndirectCostPrice(CostSheetGroup costSheetGroup, BigDecimal costPrice) {

    BigDecimal indirectCostPrice = BigDecimal.ZERO;

    indirectCostPrice =
        costPrice
            .multiply(costSheetGroup.getRate())
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);

    if (costSheetGroup.getRateTypeSelect() == CostSheetGroupRepository.COST_TYPE_SURCHARGE) {
      indirectCostPrice = indirectCostPrice.add(costPrice);
    }

    return indirectCostPrice;
  }

  protected CostSheetLine getCostSheetLine(
      CostSheetGroup indirectCostSheetGroup, CostSheetLine parentCostSheetLine) {

    for (CostSheetLine costSheetLine : parentCostSheetLine.getCostSheetLineList()) {

      CostSheetGroup costSheetGroup = costSheetLine.getCostSheetGroup();

      if (costSheetGroup != null
          && costSheetGroup.getCostTypeSelect() == CostSheetGroupRepository.COST_TYPE_INDIRECT
          && costSheetGroup.equals(indirectCostSheetGroup)) {
        return costSheetLine;
      }
    }

    return null;
  }
}
