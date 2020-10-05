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
package com.axelor.apps.stock.service;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.ProductCompany;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.ProductService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaField;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.google.inject.servlet.RequestScoped;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RequestScoped
public class WeightedAveragePriceServiceImpl implements WeightedAveragePriceService {

  protected ProductRepository productRepo;
  protected AppBaseService appBaseService;
  protected ProductCompanyService productCompanyService;

  @Inject
  public WeightedAveragePriceServiceImpl(
      ProductRepository productRepo,
      AppBaseService appBaseService,
      ProductCompanyService productCompanyService) {
    this.productRepo = productRepo;
    this.appBaseService = appBaseService;
    this.productCompanyService = productCompanyService;
  }

  @Override
  @Transactional
  public void computeAvgPriceForProduct(Product product) throws AxelorException {

    Boolean avgPriceHandledByCompany = false;
    Set<MetaField> companySpecificFields =
        appBaseService.getAppBase().getCompanySpecificProductFieldsList();
    for (MetaField field : companySpecificFields) {
      if (field.getName().equals("avgPrice")) {
        avgPriceHandledByCompany = true;
        break;
      }
    }
    if (appBaseService.getAppBase().getEnableMultiCompany()) {
      if (avgPriceHandledByCompany
          && product.getProductCompanyList() != null
          && !product.getProductCompanyList().isEmpty()) {
        for (ProductCompany productCompany : product.getProductCompanyList()) {
          Company company = productCompany.getCompany();
          BigDecimal productAvgPrice = this.computeAvgPriceForCompany(product, company);
          if (productAvgPrice.compareTo(BigDecimal.ZERO) == 0) {
            continue;
          }

          productCompanyService.set(product, "avgPrice", productAvgPrice, company);
          if ((Integer) productCompanyService.get(product, "costTypeSelect", company)
              == ProductRepository.COST_TYPE_AVERAGE_PRICE) {
            productCompanyService.set(product, "costPrice", productAvgPrice, company);
            if ((Boolean) productCompanyService.get(product, "autoUpdateSalePrice", company)) {
              Beans.get(ProductService.class).updateSalePrice(product, company);
            }
          }
        }
      } else {
        BigDecimal productAvgPrice = this.computeAvgPriceForCompany(product, null);

        if (productAvgPrice.compareTo(BigDecimal.ZERO) == 0) {
          return;
        }

        product.setAvgPrice(productAvgPrice);
        if (product.getCostTypeSelect() == ProductRepository.COST_TYPE_AVERAGE_PRICE) {
          product.setCostPrice(productAvgPrice);
          if (product.getAutoUpdateSalePrice()) {
            Beans.get(ProductService.class).updateSalePrice(product, null);
          }
        }
      }
    }
    productRepo.save(product);
  }

  @Override
  public BigDecimal computeAvgPriceForCompany(Product product, Company company) {
    Long productId = product.getId();
    String query =
        "SELECT new list(self.id, self.avgPrice, self.currentQty) FROM StockLocationLine as self "
            + "WHERE self.product.id = "
            + productId
            + " AND self.stockLocation.typeSelect != "
            + StockLocationRepository.TYPE_VIRTUAL;

    if (company != null) {
      query += " AND self.stockLocation.company = " + company.getId();
    }

    int scale = appBaseService.getNbDecimalDigitForUnitPrice();
    BigDecimal productAvgPrice = BigDecimal.ZERO;
    BigDecimal qtyTot = BigDecimal.ZERO;
    List<List<Object>> results = JPA.em().createQuery(query).getResultList();
    if (results.isEmpty()) {
      return BigDecimal.ZERO;
    }
    for (List<Object> result : results) {
      BigDecimal avgPrice = (BigDecimal) result.get(1);
      BigDecimal qty = (BigDecimal) result.get(2);
      productAvgPrice = productAvgPrice.add(avgPrice.multiply(qty));
      qtyTot = qtyTot.add(qty);
    }
    if (qtyTot.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    productAvgPrice = productAvgPrice.divide(qtyTot, scale, BigDecimal.ROUND_HALF_UP);
    return productAvgPrice;
  }
}
