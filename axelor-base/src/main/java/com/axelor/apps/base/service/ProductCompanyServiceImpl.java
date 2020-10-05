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
package com.axelor.apps.base.service;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.ProductCompany;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.CallMethod;
import com.axelor.meta.db.MetaField;
import com.google.inject.Inject;
import java.util.Set;

public class ProductCompanyServiceImpl implements ProductCompanyService {

  @Inject protected AppBaseService appBaseService;

  @Override
  @CallMethod
  public Object get(Product originalProduct, String fieldName, Company company)
      throws AxelorException {
    Mapper mapper = Mapper.of(Product.class);
    Product product = findAppropriateProductCompany(originalProduct, fieldName, company);

    return mapper.get(product, fieldName);
  }

  @Override
  @CallMethod
  public void set(Product originalProduct, String fieldName, Object fieldValue, Company company)
      throws AxelorException {
    Mapper mapper = Mapper.of(Product.class);
    Product product = findAppropriateProductCompany(originalProduct, fieldName, company);

    mapper.set(product, fieldName, fieldValue);
  }

  /**
   * Finds the appropriate company-specific version of a product if searched field is overwritten by
   * company
   *
   * @param originalProduct
   * @param fieldName
   * @param company
   * @return
   * @throws AxelorException
   */
  private Product findAppropriateProductCompany(
      Product originalProduct, String fieldName, Company company) throws AxelorException {

    if (appBaseService.getAppBase().getEnableMultiCompany()) {
      if (originalProduct == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.PRODUCT_COMPANY_NO_PRODUCT),
            fieldName);
      } else if (fieldName == null || fieldName.trim().equals("")) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.PRODUCT_COMPANY_NO_FIELD),
            originalProduct.getFullName());
      }

      Product product = originalProduct;
      if (company != null && originalProduct.getProductCompanyList() != null) {
        for (ProductCompany productCompany : originalProduct.getProductCompanyList()) {
          if (productCompany.getCompany().getId() == company.getId()) {
            Set<MetaField> companySpecificFields =
                appBaseService.getAppBase().getCompanySpecificProductFieldsList();
            for (MetaField field : companySpecificFields) {
              if (field.getName().equals(fieldName)) {
                product = productCompany;
                break;
              }
            }
            break;
          }
        }
      }
      return product;
    } else {
      return originalProduct;
    }
  }
}
