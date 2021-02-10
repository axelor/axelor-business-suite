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
package com.axelor.apps.base.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.ProductTradingNameLine;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.TradingNameRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.report.IReport;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Joiner;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ProductController {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void generateProductVariants(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Product product = request.getContext().asType(Product.class);
    product = Beans.get(ProductRepository.class).find(product.getId());

    if (product.getProductVariantConfig() != null) {
      Beans.get(ProductService.class).generateProductVariants(product);

      response.setFlash(I18n.get(IExceptionMessage.PRODUCT_1));
      response.setReload(true);
    }
  }

  public void checkPriceList(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Product newProduct = request.getContext().asType(Product.class);

    if ((!newProduct.getSellable() || newProduct.getIsUnrenewed())
        && Beans.get(ProductService.class).hasActivePriceList(newProduct)) {
      response.setAlert(I18n.get("Warning, this product is present in at least one price list"));
    }
  }

  public void setPriceListLineAnomaly(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Product newProduct = request.getContext().asType(Product.class);
    // Set anomaly when a product exists in list Price
    if (newProduct.getId() != null) {
      Product product = Beans.get(ProductRepository.class).find(newProduct.getId());
      Beans.get(PriceListService.class).setPriceListLineAnomaly(newProduct);
    }
  }

  public void updateProductsPrices(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Product product = request.getContext().asType(Product.class);

    product = Beans.get(ProductRepository.class).find(product.getId());

    Beans.get(ProductService.class).updateProductPrice(product);

    response.setFlash(I18n.get(IExceptionMessage.PRODUCT_2));
    response.setReload(true);
  }

  @SuppressWarnings("unchecked")
  public void printProductCatalog(ActionRequest request, ActionResponse response)
      throws AxelorException {

    User user = Beans.get(UserService.class).getUser();

    int currentYear = Beans.get(AppBaseService.class).getTodayDateTime().getYear();
    String productIds = "";

    List<Integer> lstSelectedProduct = (List<Integer>) request.getContext().get("_ids");

    if (lstSelectedProduct != null) {
      productIds = Joiner.on(",").join(lstSelectedProduct);
    }

    String name = I18n.get("Product Catalog");

    String fileLink =
        ReportFactory.createReport(IReport.PRODUCT_CATALOG, name + "-${date}")
            .addParam("UserId", user.getId())
            .addParam("CurrYear", Integer.toString(currentYear))
            .addParam("ProductIds", productIds)
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .addParam(
                "Timezone",
                user.getActiveCompany() != null ? user.getActiveCompany().getTimezone() : null)
            .generate()
            .getFileLink();

    logger.debug("Printing " + name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }

  public void printProductSheet(ActionRequest request, ActionResponse response)
      throws AxelorException {
    try {
      Product product = request.getContext().asType(Product.class);
      User user = Beans.get(UserService.class).getUser();

      String name = I18n.get("Product") + " " + product.getCode();

      if (user.getActiveCompany() == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.PRODUCT_NO_ACTIVE_COMPANY));
      }

      String fileLink =
          ReportFactory.createReport(IReport.PRODUCT_SHEET, name + "-${date}")
              .addParam("ProductId", product.getId())
              .addParam("CompanyId", user.getActiveCompany().getId())
              .addParam("Locale", ReportSettings.getPrintingLocale(null))
              .addParam(
                  "Timezone",
                  user.getActiveCompany() != null ? user.getActiveCompany().getTimezone() : null)
              .generate()
              .getFileLink();

      logger.debug("Printing " + name);

      response.setView(ActionView.define(name).add("html", fileLink).map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void newProductTradingNameList(ActionRequest request, ActionResponse response) {
    try {
      Product product = request.getContext().asType(Product.class);
      List<TradingName> tradingNameList = Beans.get(TradingNameRepository.class).all().fetch();
      List<ProductTradingNameLine> productTradingNameLineList =
          new ArrayList<ProductTradingNameLine>();
      if (tradingNameList != null && !tradingNameList.isEmpty()) {
        for (TradingName tradingName : tradingNameList) {
          ProductTradingNameLine productTradingNameLine = new ProductTradingNameLine();
          productTradingNameLine.setTradingName(tradingName);
          productTradingNameLine.setProduct(product);
          productTradingNameLineList.add(productTradingNameLine);
        }
      }
      response.setValue("productTradingNameLineList", productTradingNameLineList);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
