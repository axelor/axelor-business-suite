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
package com.axelor.apps.purchase.web;

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.tax.FiscalPositionService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.service.PurchaseOrderLineService;
import com.axelor.apps.purchase.service.app.AppPurchaseService;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class PurchaseOrderLineController {

  public void compute(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();
      PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);
      PurchaseOrder purchaseOrder = this.getPurchaseOrder(context);

      Map<String, BigDecimal> map =
          Beans.get(PurchaseOrderLineService.class).compute(purchaseOrderLine, purchaseOrder);
      response.setValues(map);
      response.setAttr(
          "priceDiscounted",
          "hidden",
          map.getOrDefault("priceDiscounted", BigDecimal.ZERO)
                  .compareTo(
                      purchaseOrder.getInAti()
                          ? purchaseOrderLine.getInTaxPrice()
                          : purchaseOrderLine.getPrice())
              == 0);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void getProductInformation(ActionRequest request, ActionResponse response) {
    try {
      PurchaseOrderLineService service = Beans.get(PurchaseOrderLineService.class);

      Context context = request.getContext();
      PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);
      PurchaseOrder purchaseOrder = this.getPurchaseOrder(context);
      Product product = purchaseOrderLine.getProduct();

      this.resetProductInformation(response);
      response.setValues(service.reset(purchaseOrderLine));

      if (purchaseOrder == null || product == null) {
        return;
      }

      purchaseOrderLine.setPurchaseOrder(purchaseOrder);
      service.fill(purchaseOrderLine, purchaseOrder);
      response.setValues(purchaseOrderLine);

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void resetProductInformation(ActionResponse response) {
    response.setAttr("minQtyNotRespectedLabel", "hidden", true);
    response.setAttr("multipleQtyNotRespectedLabel", "hidden", true);
  }

  public void getTaxEquiv(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);
    PurchaseOrder purchaseOrder = getPurchaseOrder(context);

    response.setValue("taxEquiv", null);

    if (purchaseOrder == null
        || purchaseOrderLine == null
        || purchaseOrder.getSupplierPartner() == null
        || purchaseOrderLine.getTaxLine() == null) return;

    response.setValue(
        "taxEquiv",
        Beans.get(FiscalPositionService.class)
            .getTaxEquiv(
                purchaseOrder.getSupplierPartner().getFiscalPosition(),
                purchaseOrderLine.getTaxLine().getTax()));
  }

  public void updateProductInformation(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();

    PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);

    PurchaseOrder purchaseOrder = this.getPurchaseOrder(context);

    if (purchaseOrder == null || purchaseOrderLine.getProduct() == null) {
      return;
    }

    try {
      PurchaseOrderLineService purchaseOrderLineService = Beans.get(PurchaseOrderLineService.class);

      BigDecimal price =
          purchaseOrderLine.getProduct().getInAti()
              ? purchaseOrderLineService.getInTaxUnitPrice(
                  purchaseOrder, purchaseOrderLine, purchaseOrderLine.getTaxLine())
              : purchaseOrderLineService.getExTaxUnitPrice(
                  purchaseOrder, purchaseOrderLine, purchaseOrderLine.getTaxLine());

      Map<String, Object> catalogInfo =
          purchaseOrderLineService.updateInfoFromCatalog(purchaseOrder, purchaseOrderLine);

      Product product = purchaseOrderLine.getProduct();
      String productName = null;
      String productCode = null;
      ProductCompanyService productCompanyService = Beans.get(ProductCompanyService.class);
      if (catalogInfo != null) {
        if (catalogInfo.get("price") != null) {
          price = (BigDecimal) catalogInfo.get("price");
        }
        productName =
            catalogInfo.get("productName") != null
                ? (String) catalogInfo.get("productName")
                : (String) productCompanyService.get(product, "name", purchaseOrder.getCompany());
        productCode =
            catalogInfo.get("productCode") != null
                ? (String) catalogInfo.get("productCode")
                : (String) productCompanyService.get(product, "code", purchaseOrder.getCompany());
      } else {
        price =
            (BigDecimal)
                productCompanyService.get(product, "purchasePrice", purchaseOrder.getCompany());
        productName =
            (String) productCompanyService.get(product, "name", purchaseOrder.getCompany());
        productCode =
            (String) productCompanyService.get(product, "code", purchaseOrder.getCompany());
      }
      if (purchaseOrderLine.getProductName() == null) {
        response.setValue("productName", productName);
      }
      if (purchaseOrderLine.getProductCode() == null) {
        response.setValue("productCode", productCode);
      }

      Map<String, Object> discounts =
          purchaseOrderLineService.getDiscountsFromPriceLists(
              purchaseOrder, purchaseOrderLine, price);

      if (discounts != null) {
        if (discounts.get("price") != null) {
          price = (BigDecimal) discounts.get("price");
        }
        if (purchaseOrderLine.getProduct().getInAti() != purchaseOrder.getInAti()
            && (Integer) discounts.get("discountTypeSelect")
                != PriceListLineRepository.AMOUNT_TYPE_PERCENT) {
          response.setValue(
              "discountAmount",
              purchaseOrderLineService.convertUnitPrice(
                  purchaseOrderLine.getProduct().getInAti(),
                  purchaseOrderLine.getTaxLine(),
                  (BigDecimal) discounts.get("discountAmount")));
        } else {
          response.setValue("discountAmount", discounts.get("discountAmount"));
        }
        response.setValue("discountTypeSelect", discounts.get("discountTypeSelect"));
      }

      if (price.compareTo(
              purchaseOrderLine.getProduct().getInAti()
                  ? purchaseOrderLine.getInTaxPrice()
                  : purchaseOrderLine.getPrice())
          != 0) {
        if (purchaseOrderLine.getProduct().getInAti()) {
          response.setValue("inTaxPrice", price);
          response.setValue(
              "price",
              purchaseOrderLineService.convertUnitPrice(
                  true, purchaseOrderLine.getTaxLine(), price));
        } else {
          response.setValue("price", price);
          response.setValue(
              "inTaxPrice",
              purchaseOrderLineService.convertUnitPrice(
                  false, purchaseOrderLine.getTaxLine(), price));
        }
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Update the ex. tax unit price of an invoice line from its in. tax unit price.
   *
   * @param request
   * @param response
   */
  public void updatePrice(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();

    PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);

    try {
      BigDecimal inTaxPrice = purchaseOrderLine.getInTaxPrice();
      TaxLine taxLine = purchaseOrderLine.getTaxLine();

      response.setValue(
          "price",
          Beans.get(PurchaseOrderLineService.class).convertUnitPrice(true, taxLine, inTaxPrice));
    } catch (Exception e) {
      response.setFlash(e.getMessage());
    }
  }

  /**
   * Update the in. tax unit price of an invoice line from its ex. tax unit price.
   *
   * @param request
   * @param response
   */
  public void updateInTaxPrice(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();

    PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);

    try {
      BigDecimal exTaxPrice = purchaseOrderLine.getPrice();
      TaxLine taxLine = purchaseOrderLine.getTaxLine();

      response.setValue(
          "inTaxPrice",
          Beans.get(PurchaseOrderLineService.class).convertUnitPrice(false, taxLine, exTaxPrice));
    } catch (Exception e) {
      response.setFlash(e.getMessage());
    }
  }

  public void convertUnitPrice(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();

    PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);

    PurchaseOrder purchaseOrder = this.getPurchaseOrder(context);

    if (purchaseOrder == null
        || purchaseOrderLine.getProduct() == null
        || purchaseOrderLine.getPrice() == null
        || purchaseOrderLine.getInTaxPrice() == null
        || purchaseOrderLine.getTaxLine() == null) {
      return;
    }

    try {
      BigDecimal price = purchaseOrderLine.getPrice();
      BigDecimal inTaxPrice =
          price.add(
              price.multiply(
                  purchaseOrderLine.getTaxLine().getValue().divide(new BigDecimal(100))));

      response.setValue("inTaxPrice", inTaxPrice);

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public PurchaseOrder getPurchaseOrder(Context context) {

    Context parentContext = context.getParent();
    PurchaseOrder purchaseOrder = null;

    if (parentContext != null && parentContext.getContextClass() == PurchaseOrder.class) {

      purchaseOrder = parentContext.asType(PurchaseOrder.class);
      if (!parentContext.getContextClass().toString().equals(PurchaseOrder.class.toString())) {

        PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);

        purchaseOrder = purchaseOrderLine.getPurchaseOrder();
      }

    } else {
      PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);
      purchaseOrder = purchaseOrderLine.getPurchaseOrder();
    }

    return purchaseOrder;
  }

  public void emptyLine(ActionRequest request, ActionResponse response) {
    try {
      PurchaseOrderLine purchaseOrderLine = request.getContext().asType(PurchaseOrderLine.class);
      if (purchaseOrderLine.getIsTitleLine()) {
        PurchaseOrderLine newPurchaseOrderLine = new PurchaseOrderLine();
        newPurchaseOrderLine.setIsTitleLine(true);
        newPurchaseOrderLine.setQty(BigDecimal.ZERO);
        newPurchaseOrderLine.setId(purchaseOrderLine.getId());
        newPurchaseOrderLine.setVersion(purchaseOrderLine.getVersion());
        response.setValues(Mapper.toMap(purchaseOrderLine));
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void checkQty(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();
      PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);
      PurchaseOrder purchaseOrder = getPurchaseOrder(context);
      PurchaseOrderLineService service = Beans.get(PurchaseOrderLineService.class);

      service.checkMinQty(purchaseOrder, purchaseOrderLine, request, response);
      service.checkMultipleQty(purchaseOrderLine, response);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called on supplier partner select. Set the domain for the field supplierPartner
   *
   * @param request
   * @param response
   */
  public void supplierPartnerDomain(ActionRequest request, ActionResponse response) {
    PurchaseOrderLine purchaseOrderLine = request.getContext().asType(PurchaseOrderLine.class);

    PurchaseOrder purchaseOrder = purchaseOrderLine.getPurchaseOrder();
    if (purchaseOrder == null) {
      purchaseOrder = request.getContext().getParent().asType(PurchaseOrder.class);
    }
    Company company = purchaseOrder.getCompany();

    String domain = "";
    if (Beans.get(AppPurchaseService.class).getAppPurchase().getManageSupplierCatalog()
        && purchaseOrderLine.getProduct() != null
        && !purchaseOrderLine.getProduct().getSupplierCatalogList().isEmpty()) {
      domain +=
          "self.id != "
              + company.getPartner().getId()
              + " AND self.id IN "
              + purchaseOrderLine.getProduct().getSupplierCatalogList().stream()
                  .map(s -> s.getSupplierPartner().getId())
                  .collect(Collectors.toList())
                  .toString()
                  .replace('[', '(')
                  .replace(']', ')');

      String blockedPartnerQuery =
          Beans.get(BlockingService.class)
              .listOfBlockedPartner(company, BlockingRepository.PURCHASE_BLOCKING);

      if (!Strings.isNullOrEmpty(blockedPartnerQuery)) {
        domain += String.format(" AND self.id NOT in (%s)", blockedPartnerQuery);
      }
    } else {
      domain += "self.id = 0";
    }

    domain += " AND " + company.getId() + " in (SELECT id FROM self.companySet)";

    response.setAttr("supplierPartner", "domain", domain);
  }

  public void checkDifferentSupplier(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();
      PurchaseOrderLine purchaseOrderLine = context.asType(PurchaseOrderLine.class);
      PurchaseOrder purchaseOrder = getPurchaseOrder(context);
      PurchaseOrderLineService service = Beans.get(PurchaseOrderLineService.class);

      service.checkDifferentSupplier(purchaseOrder, purchaseOrderLine, response);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
