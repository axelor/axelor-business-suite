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

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.ProductVariant;
import com.axelor.apps.base.db.ProductVariantAttr;
import com.axelor.apps.base.db.ProductVariantValue;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.ProductVariantRepository;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductVariantService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected ProductRepository productRepo;
  protected ProductVariantRepository productVariantRepo;

  @Inject
  public ProductVariantService(
      ProductRepository productRepo, ProductVariantRepository productVariantRepo) {
    this.productRepo = productRepo;
    this.productVariantRepo = productVariantRepo;
  }

  public ProductVariant createProductVariant(
      ProductVariantAttr productVariantAttr1,
      ProductVariantAttr productVariantAttr2,
      ProductVariantAttr productVariantAttr3,
      ProductVariantAttr productVariantAttr4,
      ProductVariantValue productVariantValue1,
      ProductVariantValue productVariantValue2,
      ProductVariantValue productVariantValue3,
      ProductVariantValue productVariantValue4,
      boolean usedForStock) {

    ProductVariant productVariant = new ProductVariant();
    productVariant.setProductVariantAttr1(productVariantAttr1);
    productVariant.setProductVariantAttr2(productVariantAttr2);
    productVariant.setProductVariantAttr3(productVariantAttr3);
    productVariant.setProductVariantAttr4(productVariantAttr4);

    productVariant.setProductVariantValue1(productVariantValue1);
    productVariant.setProductVariantValue2(productVariantValue2);
    productVariant.setProductVariantValue3(productVariantValue3);
    productVariant.setProductVariantValue4(productVariantValue4);

    productVariant.setUsedForStock(usedForStock);

    return productVariant;
  }

  public ProductVariantValue createProductVariantValue(
      ProductVariantAttr productVariantAttr, String code, String name, BigDecimal priceExtra) {

    ProductVariantValue productVariantValue = new ProductVariantValue();
    productVariantValue.setCode(code);
    productVariantValue.setName(name);
    productVariantValue.setPriceExtra(priceExtra);
    productVariantValue.setProductVariantAttr(productVariantAttr);

    return productVariantValue;
  }

  public ProductVariantAttr createProductVariantAttr(String name) {

    ProductVariantAttr productVariantAttr = new ProductVariantAttr();
    productVariantAttr.setName(name);
    productVariantAttr.setProductVariantValueList(new ArrayList<ProductVariantValue>());

    return productVariantAttr;
  }

  public boolean equalsName(ProductVariant productVariant1, ProductVariant productVariant2) {

    if (productVariant1 != null
        && productVariant2 != null
        && productVariant1.getName().equals(productVariant2.getName())) {
      return true;
    }

    return false;
  }

  public boolean equals(ProductVariant productVariant1, ProductVariant productVariant2) {

    if (productVariant1 != null
        && productVariant2 != null
        && productVariant1.getProductVariantAttr1().equals(productVariant2.getProductVariantAttr1())
        && productVariant1.getProductVariantAttr2().equals(productVariant2.getProductVariantAttr2())
        && productVariant1.getProductVariantAttr3().equals(productVariant2.getProductVariantAttr3())
        && productVariant1.getProductVariantAttr4().equals(productVariant2.getProductVariantAttr4())
        && productVariant1
            .getProductVariantValue1()
            .equals(productVariant2.getProductVariantValue1())
        && productVariant1
            .getProductVariantValue2()
            .equals(productVariant2.getProductVariantValue2())
        && productVariant1
            .getProductVariantValue3()
            .equals(productVariant2.getProductVariantValue3())
        && productVariant1
            .getProductVariantValue4()
            .equals(productVariant2.getProductVariantValue4())) {
      return true;
    }

    return false;
  }

  protected ProductVariant getProductVariant(
      ProductVariantAttr productVariantAttr1,
      ProductVariantAttr productVariantAttr2,
      ProductVariantAttr productVariantAttr3,
      ProductVariantAttr productVariantAttr4,
      ProductVariantValue productVariantValue1,
      ProductVariantValue productVariantValue2,
      ProductVariantValue productVariantValue3,
      ProductVariantValue productVariantValue4,
      boolean usedForStock) {

    return productVariantRepo
        .all()
        .filter(
            "self.productVariantAttr1 = ?1 AND self.productVariantAttr2 = ?2 AND self.productVariantAttr3 = ?3 AND "
                + "self.productVariantAttr4 = ?4 AND self.productVariantValue1 = ?5 AND self.productVariantValue2 = ?6 AND self.productVariantValue3 = ?7 AND "
                + "self.productVariantValue4 = ?8 AND self.usedForStock = 'true'",
            productVariantAttr1,
            productVariantAttr2,
            productVariantAttr3,
            productVariantAttr4,
            productVariantValue1,
            productVariantValue2,
            productVariantValue3,
            productVariantValue4,
            usedForStock)
        .fetchOne();
  }

  public ProductVariant copyProductVariant(ProductVariant productVariant, boolean usedForStock) {

    return this.createProductVariant(
        productVariant.getProductVariantAttr1(),
        productVariant.getProductVariantAttr2(),
        productVariant.getProductVariantAttr3(),
        productVariant.getProductVariantAttr4(),
        productVariant.getProductVariantValue1(),
        productVariant.getProductVariantValue2(),
        productVariant.getProductVariantValue3(),
        productVariant.getProductVariantValue4(),
        usedForStock);
  }

  public ProductVariant getStockProductVariant(ProductVariant productVariant) {

    ProductVariant stockProductVariant =
        this.getProductVariant(
            productVariant.getProductVariantAttr1(),
            productVariant.getProductVariantAttr2(),
            productVariant.getProductVariantAttr3(),
            productVariant.getProductVariantAttr4(),
            productVariant.getProductVariantValue1(),
            productVariant.getProductVariantValue2(),
            productVariant.getProductVariantValue3(),
            productVariant.getProductVariantValue4(),
            true);

    if (stockProductVariant == null) {
      stockProductVariant = this.copyProductVariant(productVariant, true);
    }

    return stockProductVariant;
  }

  /**
   * Méthode permettant de récupérer la déclinaison de produit en fonction des attributs du parent
   * (produit fabriqué)
   *
   * @param parentProduct Le produit finis ou semi-finis
   * @param productModel Le modele de produit (Produit consommé)
   * @return La déclinaison de produit consommé
   */
  public Product getProductVariant(Product parentProduct, Product productModel) {

    ProductVariant productVariant = parentProduct.getProductVariant();

    if (productVariant != null && productModel.getIsModel()) {

      return this.getProductVariant(productVariant, productModel);
    }

    return productModel;
  }

  protected Product getProductVariant(
      ProductVariant parentProductVariant, Product productSearched) {

    LOG.debug(
        "Recherche d'un variant du produit {} ayant des attributs communs avec {}",
        productSearched.getCode(),
        parentProductVariant.getName());

    ProductVariantValue productVariantValue1 = parentProductVariant.getProductVariantValue1();
    ProductVariantValue productVariantValue2 = parentProductVariant.getProductVariantValue2();
    ProductVariantValue productVariantValue3 = parentProductVariant.getProductVariantValue3();
    ProductVariantValue productVariantValue4 = parentProductVariant.getProductVariantValue4();

    if (productVariantValue1 != null) {

      LOG.debug(
          "Recherche d'un variant de produit ayant au moins comme attributs {} : {}",
          productVariantValue1.getProductVariantAttr().getCode(),
          productVariantValue1.getCode());

      List<? extends Product> productList =
          productRepo
              .all()
              .filter(
                  "self.parentProduct = ?1 "
                      + "AND ((self.productVariant.productVariantAttr1.code = ?2 AND self.productVariant.productVariantValue1.code = ?3) "
                      + "OR (self.productVariant.productVariantAttr2.code = ?2 AND self.productVariant.productVariantValue2.code = ?3) "
                      + "OR (self.productVariant.productVariantAttr3.code = ?2 AND self.productVariant.productVariantValue3.code = ?3) "
                      + "OR (self.productVariant.productVariantAttr4.code = ?2 AND self.productVariant.productVariantValue4.code = ?3)) ",
                  productSearched,
                  productVariantValue1.getProductVariantAttr().getCode(),
                  productVariantValue1.getCode())
              .fetch();

      if (productList == null || productList.isEmpty()) {

        return productSearched;
      }

      Product productFind = null;
      int nbAttr = 0;

      for (Product product : productList) {

        if (productVariantValue1 != null
            && productVariantValue2 != null
            && productVariantValue3 != null
            && productVariantValue4 != null) {

          // 4
          if (this.containsProductVariantValue(product, productVariantValue1)
              && this.containsProductVariantValue(product, productVariantValue2)
              && this.containsProductVariantValue(product, productVariantValue3)
              && this.containsProductVariantValue(product, productVariantValue4)) {

            LOG.debug(
                "Variant de produit trouvé directement : {} avec l'ensemble des attributs (4) en commun",
                product.getCode());
            return product;
          }

          // 3
          if (nbAttr < 3) {
            if (this.containsProductVariantValue(product, productVariantValue1)
                && this.containsProductVariantValue(product, productVariantValue2)
                && this.containsProductVariantValue(product, productVariantValue3)) {

              productFind = product;
              nbAttr = 3;
            }
            if (this.containsProductVariantValue(product, productVariantValue1)
                && this.containsProductVariantValue(product, productVariantValue2)
                && this.containsProductVariantValue(product, productVariantValue4)) {

              productFind = product;
              nbAttr = 3;
            }
            if (this.containsProductVariantValue(product, productVariantValue1)
                && this.containsProductVariantValue(product, productVariantValue3)
                && this.containsProductVariantValue(product, productVariantValue4)) {

              productFind = product;
              nbAttr = 3;
            }
            if (this.containsProductVariantValue(product, productVariantValue2)
                && this.containsProductVariantValue(product, productVariantValue3)
                && this.containsProductVariantValue(product, productVariantValue4)) {

              productFind = product;
              nbAttr = 3;
            }
          }

          if (nbAttr < 2) {

            // 2
            if (this.containsProductVariantValue(product, productVariantValue1)
                && this.containsProductVariantValue(product, productVariantValue2)) {

              productFind = product;
              nbAttr = 2;
            }
            if (this.containsProductVariantValue(product, productVariantValue1)
                && this.containsProductVariantValue(product, productVariantValue3)) {

              productFind = product;
              nbAttr = 2;
            }
            if (this.containsProductVariantValue(product, productVariantValue1)
                && this.containsProductVariantValue(product, productVariantValue4)) {

              productFind = product;
              nbAttr = 2;
            }
            if (this.containsProductVariantValue(product, productVariantValue2)
                && this.containsProductVariantValue(product, productVariantValue3)) {

              productFind = product;
              nbAttr = 2;
            }
            if (this.containsProductVariantValue(product, productVariantValue2)
                && this.containsProductVariantValue(product, productVariantValue4)) {

              productFind = product;
              nbAttr = 2;
            }
            if (this.containsProductVariantValue(product, productVariantValue3)
                && this.containsProductVariantValue(product, productVariantValue4)) {

              productFind = product;
              nbAttr = 2;
            }
          }

          if (nbAttr < 1) {

            // 1
            if (this.containsProductVariantValue(product, productVariantValue1)) {

              productFind = product;
              nbAttr = 1;
            }
            if (this.containsProductVariantValue(product, productVariantValue2)) {

              productFind = product;
              nbAttr = 1;
            }
            if (this.containsProductVariantValue(product, productVariantValue3)) {

              productFind = product;
              nbAttr = 1;
            }
            if (this.containsProductVariantValue(product, productVariantValue4)) {

              productFind = product;
              nbAttr = 1;
            }
          }
        }

        if (productVariantValue1 != null
            && productVariantValue2 != null
            && productVariantValue3 != null) {

          // 3
          if (this.containsProductVariantValue(product, productVariantValue1)
              && this.containsProductVariantValue(product, productVariantValue2)
              && this.containsProductVariantValue(product, productVariantValue3)) {

            LOG.debug(
                "Variant de produit trouvé directement : {} avec l'ensemble des attributs (3) en commun",
                product.getCode());
            return product;
          }

          if (nbAttr < 2) {
            // 2
            if (this.containsProductVariantValue(product, productVariantValue1)
                && this.containsProductVariantValue(product, productVariantValue2)) {

              productFind = product;
              nbAttr = 2;
            }
            if (this.containsProductVariantValue(product, productVariantValue1)
                && this.containsProductVariantValue(product, productVariantValue3)) {

              productFind = product;
              nbAttr = 2;
            }
            if (this.containsProductVariantValue(product, productVariantValue2)
                && this.containsProductVariantValue(product, productVariantValue3)) {

              productFind = product;
              nbAttr = 2;
            }
          }

          if (nbAttr < 1) {
            // 1
            if (this.containsProductVariantValue(product, productVariantValue1)) {

              productFind = product;
              nbAttr = 1;
            }
            if (this.containsProductVariantValue(product, productVariantValue2)) {

              productFind = product;
              nbAttr = 1;
            }
            if (this.containsProductVariantValue(product, productVariantValue3)) {

              productFind = product;
              nbAttr = 1;
            }
          }
        }

        if (productVariantValue1 != null && productVariantValue2 != null) {

          // 2
          if (this.containsProductVariantValue(product, productVariantValue1)
              && this.containsProductVariantValue(product, productVariantValue2)) {

            LOG.debug(
                "Variant de produit trouvé directement : {} avec l'ensemble des attributs (2) en commun",
                product.getCode());
            return product;
          }

          if (nbAttr < 1) {
            // 1
            if (this.containsProductVariantValue(product, productVariantValue1)) {

              productFind = product;
              nbAttr = 1;
            }
            if (this.containsProductVariantValue(product, productVariantValue2)) {

              productFind = product;
              nbAttr = 1;
            }
          }
        }

        if (productVariantValue1 != null) {

          if (this.containsProductVariantValue(product, productVariantValue1)) {

            LOG.debug(
                "Variant de produit trouvé directement : {} avec l'ensemble des attributs (1) en commun",
                product.getCode());
            return product;
          }
        }
      }

      if (productFind != null) {
        LOG.debug(
            "Variant de produit trouvé : {} avec {} attributs en commun",
            productFind.getCode(),
            nbAttr);
        return productFind;
      }
    }

    return productSearched;
  }

  protected boolean containsProductVariantValue(
      Product product, ProductVariantValue productVariantValue) {

    ProductVariant productVariantFind = product.getProductVariant();

    ProductVariantValue productVariantValue1 = productVariantFind.getProductVariantValue1();
    ProductVariantValue productVariantValue2 = productVariantFind.getProductVariantValue2();
    ProductVariantValue productVariantValue3 = productVariantFind.getProductVariantValue3();
    ProductVariantValue productVariantValue4 = productVariantFind.getProductVariantValue4();

    if ((productVariantValue1 != null
            && productVariantValue1.getCode().equals(productVariantValue.getCode())
            && productVariantValue1
                .getProductVariantAttr()
                .getCode()
                .equals(productVariantValue.getProductVariantAttr().getCode()))
        || (productVariantValue2 != null
            && productVariantValue2.getCode().equals(productVariantValue.getCode())
            && productVariantValue2
                .getProductVariantAttr()
                .getCode()
                .equals(productVariantValue.getProductVariantAttr().getCode()))
        || (productVariantValue3 != null
            && productVariantValue3.getCode().equals(productVariantValue.getCode())
            && productVariantValue3
                .getProductVariantAttr()
                .getCode()
                .equals(productVariantValue.getProductVariantAttr().getCode()))
        || (productVariantValue4 != null
            && productVariantValue4.getCode().equals(productVariantValue.getCode())
            && productVariantValue4
                .getProductVariantAttr()
                .getCode()
                .equals(productVariantValue.getProductVariantAttr().getCode()))) {

      return true;
    }

    return false;
  }
}
