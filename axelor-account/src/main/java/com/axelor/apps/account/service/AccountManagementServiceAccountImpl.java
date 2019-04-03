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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.AnalyticDistributionTemplate;
import com.axelor.apps.account.db.FiscalPosition;
import com.axelor.apps.account.db.FixedAssetCategory;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.tax.AccountManagementServiceImpl;
import com.axelor.apps.base.service.tax.FiscalPositionService;
import com.axelor.apps.base.service.tax.TaxService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountManagementServiceAccountImpl extends AccountManagementServiceImpl
    implements AccountManagementAccountService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  @Inject AccountConfigService accountConfigService;
  @Inject TaxService taxService;

  @Inject
  public AccountManagementServiceAccountImpl(
      FiscalPositionService fiscalPositionService, TaxService taxService) {
    super(fiscalPositionService, taxService);
  }

  /**
   * Get the product tax according to the fiscal position
   *
   * @param product
   * @param company
   * @param fiscalPosition
   * @param isPurchase Specify if we want get the tax for purchase or sale
   * @param fixedAsset Specify if we should get the purchase account for fixed asset or not. Used
   *     only if isPurchase param is true.
   * @return the tax defined for the product, according to the fiscal position
   * @throws AxelorException
   */
  public Account getProductAccount(
      Product product,
      Company company,
      FiscalPosition fiscalPosition,
      boolean isPurchase,
      boolean fixedAsset)
      throws AxelorException {

    log.debug(
        "Get the account for the product {} (company : {}, purchase : {}, fixed asset : {}, fiscal position : {})",
        new Object[] {
          product.getCode(),
          company.getName(),
          isPurchase,
          fixedAsset,
          fiscalPosition != null ? fiscalPosition.getCode() : null
        });

    Account generalAccount =
        this.getProductAccount(product, company, isPurchase, fixedAsset, CONFIG_OBJECT_PRODUCT);

    Account account =
        new FiscalPositionAccountServiceImpl().getAccount(fiscalPosition, generalAccount);

    AccountConfig accountConfig = accountConfigService.getAccountConfig(company);
    if (account != null
        || (accountConfig.getAdvancePaymentProduct() != null
            && accountConfig.getAdvancePaymentProduct().equals(product))) {
      return account;
    }

    throw new AxelorException(
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        I18n.get(IExceptionMessage.ACCOUNT_MANAGEMENT_1_ACCOUNT),
        product.getCode(),
        company.getName());
  }

  /**
   * Get the product tax
   *
   * @param product
   * @param company
   * @param isPurchase
   * @param fixedAsset Specify if we should get the purchase account for fixed asset or not. Used
   *     only if isPurchase param is true.
   * @param configObject Specify if we want get the tax from the product or its product family
   *     <li>1 : product
   *     <li>2 : product family
   * @return
   * @throws AxelorException
   */
  protected Account getProductAccount(
      Product product, Company company, boolean isPurchase, boolean fixedAsset, int configObject) {

    AccountManagement accountManagement = this.getAccountManagement(product, company, configObject);

    Account account = null;

    if (accountManagement != null) {
      if (isPurchase) {
        if (fixedAsset) {
          account = accountManagement.getPurchFixedAssetsAccount();
        } else {
          account = accountManagement.getPurchaseAccount();
        }
      } else {
        account = accountManagement.getSaleAccount();
      }
    }

    if (account == null && configObject == CONFIG_OBJECT_PRODUCT) {
      return getProductAccount(
          product, company, isPurchase, fixedAsset, CONFIG_OBJECT_PRODUCT_FAMILY);
    }

    return account;
  }

  /**
   * Get the product analytic distribution template
   *
   * @param product
   * @param company
   * @return
   * @throws AxelorException
   */
  public AnalyticDistributionTemplate getAnalyticDistributionTemplate(
      Product product, Company company) {

    return getAnalyticDistributionTemplate(product, company, CONFIG_OBJECT_PRODUCT);
  }

  /**
   * Get the product analytic distribution template
   *
   * @param product
   * @param compan
   * @param configObject Specify if we want get the tax from the product or its product family
   *     <li>1 : product
   *     <li>2 : product family
   * @return
   * @throws AxelorException
   */
  protected AnalyticDistributionTemplate getAnalyticDistributionTemplate(
      Product product, Company company, int configObject) {

    AccountManagement accountManagement = this.getAccountManagement(product, company, configObject);

    AnalyticDistributionTemplate analyticDistributionTemplate = null;

    if (accountManagement != null) {
      analyticDistributionTemplate = accountManagement.getAnalyticDistributionTemplate();
    }

    if (analyticDistributionTemplate == null && configObject == CONFIG_OBJECT_PRODUCT) {
      return getAnalyticDistributionTemplate(product, company, CONFIG_OBJECT_PRODUCT_FAMILY);
    }

    return analyticDistributionTemplate;
  }

  /**
   * Get the product fixed asset category
   *
   * @param product
   * @param company
   * @return
   * @throws AxelorException
   */
  public FixedAssetCategory getProductFixedAssetCategory(Product product, Company company) {

    return getProductFixedAssetCategory(product, company, CONFIG_OBJECT_PRODUCT);
  }

  /**
   * Get the product fixed asset category
   *
   * @param product
   * @param company
   * @param configObject Specify if we want get the fixed asset category from the product or its
   *     product family
   *     <li>1 : product
   *     <li>2 : product family
   * @return
   * @throws AxelorException
   */
  protected FixedAssetCategory getProductFixedAssetCategory(
      Product product, Company company, int configObject) {

    AccountManagement accountManagement = this.getAccountManagement(product, company, configObject);

    FixedAssetCategory fixedAssetCategory = null;

    if (accountManagement != null) {
      fixedAssetCategory = accountManagement.getFixedAssetCategory();
    }

    if (fixedAssetCategory == null && configObject == CONFIG_OBJECT_PRODUCT) {
      return getProductFixedAssetCategory(product, company, CONFIG_OBJECT_PRODUCT_FAMILY);
    }

    return fixedAssetCategory;
  }

  @Override
  public TaxLine getTaxLine(
      LocalDate date,
      Product product,
      Company company,
      FiscalPosition fiscalPosition,
      boolean isPurchase)
      throws AxelorException {

    AccountConfig accountConfig = accountConfigService.getAccountConfig(company);
    try {
      return super.getTaxLine(date, product, company, fiscalPosition, isPurchase);
    } catch (Exception e) {
      if (accountConfig.getAdvancePaymentProduct() != null
          && accountConfig.getAdvancePaymentProduct().equals(product)) {
        return null;
      }
      throw e;
    }
  }

  @Override
  public Tax getProductTax(
      Product product, Company company, FiscalPosition fiscalPosition, boolean isPurchase)
      throws AxelorException {
    try {
      return super.getProductTax(product, company, fiscalPosition, isPurchase);
    } catch (Exception e) {
      AccountConfig accountConfig = accountConfigService.getAccountConfig(company);
      if (accountConfig.getAdvancePaymentProduct() != null
          && accountConfig.getAdvancePaymentProduct().equals(product)) {
        return null;
      }
      throw e;
    }
  }
}
