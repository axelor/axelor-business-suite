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
package com.axelor.apps.contract.service;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.contract.db.Contract;
import com.axelor.apps.contract.db.ContractLine;
import com.axelor.apps.contract.exception.IExceptionMessage;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class ContractLineServiceImpl implements ContractLineService {
  protected AppBaseService appBaseService;
  protected AccountManagementService accountManagementService;
  protected CurrencyService currencyService;
  protected ProductCompanyService productCompanyService;

  @Inject
  public ContractLineServiceImpl(
      AppBaseService appBaseService,
      AccountManagementService accountManagementService,
      CurrencyService currencyService,
      ProductCompanyService productCompanyService) {
    this.appBaseService = appBaseService;
    this.accountManagementService = accountManagementService;
    this.currencyService = currencyService;
    this.productCompanyService = productCompanyService;
  }

  @Override
  public ContractLine reset(ContractLine contractLine) {
    if (contractLine == null) {
      return new ContractLine();
    }
    contractLine.setTaxLine(null);
    contractLine.setProductName(null);
    contractLine.setUnit(null);
    contractLine.setPrice(null);
    contractLine.setExTaxTotal(null);
    contractLine.setInTaxTotal(null);
    contractLine.setDescription(null);
    return contractLine;
  }

  @Override
  public ContractLine fill(ContractLine contractLine, Product product) throws AxelorException {
    Preconditions.checkNotNull(product, I18n.get(IExceptionMessage.CONTRACT_EMPTY_PRODUCT));
    Company company =
        contractLine.getContractVersion() != null
            ? contractLine.getContractVersion().getContract() != null
                ? contractLine.getContractVersion().getContract().getCompany()
                : null
            : null;
    contractLine.setProductName((String) productCompanyService.get(product, "name", company));
    Unit unit = (Unit) productCompanyService.get(product, "salesUnit", company);
    if (unit != null) {
      contractLine.setUnit(unit);
    } else {
      contractLine.setUnit((Unit) productCompanyService.get(product, "unit", company));
    }
    contractLine.setPrice((BigDecimal) productCompanyService.get(product, "salePrice", company));
    contractLine.setDescription(
        (String) productCompanyService.get(product, "description", company));
    return contractLine;
  }

  @Override
  public ContractLine compute(ContractLine contractLine, Contract contract, Product product)
      throws AxelorException {
    Preconditions.checkNotNull(
        contract, I18n.get("Contract can't be " + "empty for compute contract line price."));
    Preconditions.checkNotNull(
        product, "Product can't be " + "empty for compute contract line price.");

    // TODO: maybe put tax computing in another method
    contractLine.setFiscalPosition(contract.getPartner().getFiscalPosition());

    TaxLine taxLine =
        accountManagementService.getTaxLine(
            appBaseService.getTodayDate(contract.getCompany()),
            product,
            contract.getCompany(),
            contractLine.getFiscalPosition(),
            false);
    contractLine.setTaxLine(taxLine);

    if (taxLine != null
        && (Boolean) productCompanyService.get(product, "inAti", contract.getCompany())) {
      BigDecimal price = contractLine.getPrice();
      price =
          price.divide(
              taxLine.getValue().divide(new BigDecimal(100)).add(BigDecimal.ONE),
              2,
              BigDecimal.ROUND_HALF_UP);
      contractLine.setPrice(price);
    }

    BigDecimal price = contractLine.getPrice();
    BigDecimal convert =
        currencyService.getCurrencyConversionRate(
            (Currency) productCompanyService.get(product, "saleCurrency", contract.getCompany()),
            contract.getCurrency(),
            appBaseService.getTodayDate(contract.getCompany()));
    contractLine.setPrice(price.multiply(convert));

    return contractLine;
  }

  @Override
  public ContractLine fillAndCompute(ContractLine contractLine, Contract contract, Product product)
      throws AxelorException {
    contractLine = fill(contractLine, product);
    contractLine = compute(contractLine, contract, product);
    return contractLine;
  }

  @Override
  public ContractLine computeTotal(ContractLine contractLine) {
    BigDecimal taxRate = BigDecimal.ZERO;

    if (contractLine.getTaxLine() != null) {
      taxRate = contractLine.getTaxLine().getValue().divide(new BigDecimal(100));
    }

    BigDecimal exTaxTotal =
        contractLine.getQty().multiply(contractLine.getPrice()).setScale(2, RoundingMode.HALF_EVEN);
    contractLine.setExTaxTotal(exTaxTotal);
    BigDecimal inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
    contractLine.setInTaxTotal(inTaxTotal);

    return contractLine;
  }

  @Override
  public ContractLine createAnalyticDistributionWithTemplate(
      ContractLine contractLine, Contract contract) {

    AppAccountService appAccountService = Beans.get(AppAccountService.class);

    List<AnalyticMoveLine> analyticMoveLineList =
        Beans.get(AnalyticMoveLineService.class)
            .generateLines(
                contractLine.getAnalyticDistributionTemplate(),
                contractLine.getExTaxTotal(),
                AnalyticMoveLineRepository.STATUS_FORECAST_CONTRACT,
                appAccountService.getTodayDate(contract.getCompany()));

    contractLine.setAnalyticMoveLineList(analyticMoveLineList);
    return contractLine;
  }
}
