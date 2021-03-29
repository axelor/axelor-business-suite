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
package com.axelor.apps.account.web;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.FixedAssetCategory;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.AccountTypeRepository;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.AccountManagementServiceAccountImpl;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.account.service.invoice.generator.line.InvoiceLineManagement;
import com.axelor.apps.account.translation.ITranslation;
import com.axelor.apps.base.db.Product;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class InvoiceLineController {

  public void getAndComputeAnalyticDistribution(ActionRequest request, ActionResponse response)
      throws AxelorException {
    InvoiceLine invoiceLine = request.getContext().asType(InvoiceLine.class);
    Invoice invoice = invoiceLine.getInvoice();
    if (invoice == null) {
      invoice = request.getContext().getParent().asType(Invoice.class);
    }

    response.setValue(
        "analyticMoveLineList",
        Beans.get(InvoiceLineService.class)
            .getAndComputeAnalyticDistribution(invoiceLine, invoice));
    response.setValue(
        "analyticDistributionTemplate", invoiceLine.getAnalyticDistributionTemplate());
  }

  public void createAnalyticDistributionWithTemplate(ActionRequest request, ActionResponse response)
      throws AxelorException {
    InvoiceLine invoiceLine = request.getContext().asType(InvoiceLine.class);

    response.setValue(
        "analyticMoveLineList",
        Beans.get(InvoiceLineService.class).createAnalyticDistributionWithTemplate(invoiceLine));
  }

  public void computeAnalyticDistribution(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Context context = request.getContext();
    InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

    if (Beans.get(AppAccountService.class).getAppAccount().getManageAnalyticAccounting()) {
      response.setValue(
          "analyticMoveLineList",
          Beans.get(InvoiceLineService.class).computeAnalyticDistribution(invoiceLine));
    }
  }

  public void compute(ActionRequest request, ActionResponse response) throws AxelorException {

    Context context = request.getContext();
    InvoiceLineService invoiceLineService = Beans.get(InvoiceLineService.class);

    InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

    if (context.getParent().getContextClass() == InvoiceLine.class) {
      context = request.getContext().getParent();
    }

    Invoice invoice = this.getInvoice(context);

    if (invoice == null
        || invoiceLine.getPrice() == null
        || invoiceLine.getInTaxPrice() == null
        || invoiceLine.getQty() == null) {
      return;
    }

    BigDecimal exTaxTotal;
    BigDecimal companyExTaxTotal;
    BigDecimal inTaxTotal;
    BigDecimal companyInTaxTotal;
    BigDecimal priceDiscounted =
        invoiceLineService.computeDiscount(invoiceLine, invoice.getInAti());

    response.setValue("priceDiscounted", priceDiscounted);
    response.setAttr(
        "priceDiscounted",
        "hidden",
        priceDiscounted.compareTo(
                invoice.getInAti() ? invoiceLine.getInTaxPrice() : invoiceLine.getPrice())
            == 0);

    BigDecimal taxRate = BigDecimal.ZERO;
    if (invoiceLine.getTaxLine() != null) {
      taxRate = invoiceLine.getTaxLine().getValue();
      response.setValue("taxRate", taxRate);
      response.setValue("taxCode", invoiceLine.getTaxLine().getTax().getCode());
    }

    if (!invoice.getInAti()) {
      exTaxTotal = InvoiceLineManagement.computeAmount(invoiceLine.getQty(), priceDiscounted);
      inTaxTotal =
          exTaxTotal.add(
              exTaxTotal.multiply(taxRate.divide(new BigDecimal(100)).divide(new BigDecimal(100))));
    } else {
      inTaxTotal = InvoiceLineManagement.computeAmount(invoiceLine.getQty(), priceDiscounted);
      exTaxTotal =
          inTaxTotal.divide(
              taxRate.divide(new BigDecimal(100)).divide(new BigDecimal(100)).add(BigDecimal.ONE),
              2,
              BigDecimal.ROUND_HALF_UP);
    }

    companyExTaxTotal = invoiceLineService.getCompanyExTaxTotal(exTaxTotal, invoice);
    companyInTaxTotal = invoiceLineService.getCompanyExTaxTotal(inTaxTotal, invoice);

    response.setValue("exTaxTotal", exTaxTotal);
    response.setValue("inTaxTotal", inTaxTotal);
    response.setValue("companyInTaxTotal", companyInTaxTotal);
    response.setValue("companyExTaxTotal", companyExTaxTotal);
  }

  public void getProductInformation(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Context context = request.getContext();
    InvoiceLine invoiceLine = context.asType(InvoiceLine.class);
    if (context.getParent().getContextClass() == InvoiceLine.class) {
      context = request.getContext().getParent();
    }
    Invoice invoice = this.getInvoice(context);
    Product product = invoiceLine.getProduct();
    Map<String, Object> productInformation = new HashMap<>();
    if (invoice != null && product != null) {
      try {
        productInformation =
            Beans.get(InvoiceLineService.class).fillProductInformation(invoice, invoiceLine);

        String errorMsg = (String) productInformation.get("error");

        if (!Strings.isNullOrEmpty(errorMsg)) {
          response.setFlash(errorMsg);
        }
      } catch (Exception e) {
        TraceBackService.trace(response, e);
      }
    } else {
      productInformation = Beans.get(InvoiceLineService.class).resetProductInformation(invoice);
    }
    response.setValues(productInformation);
  }

  public void getDiscount(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();

    InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

    if (context.getParent().getContextClass() == InvoiceLine.class) {
      context = request.getContext().getParent();
    }

    Invoice invoice = this.getInvoice(context);

    if (invoice == null || invoiceLine.getProduct() == null) {
      return;
    }

    try {

      Map<String, Object> discounts =
          Beans.get(InvoiceLineService.class)
              .getDiscount(
                  invoice,
                  invoiceLine,
                  invoiceLine.getProduct().getInAti()
                      ? Beans.get(InvoiceLineService.class)
                          .getInTaxUnitPrice(
                              invoice,
                              invoiceLine,
                              invoiceLine.getTaxLine(),
                              InvoiceToolService.isPurchase(invoice))
                      : Beans.get(InvoiceLineService.class)
                          .getExTaxUnitPrice(
                              invoice,
                              invoiceLine,
                              invoiceLine.getTaxLine(),
                              InvoiceToolService.isPurchase(invoice)));

      for (Entry<String, Object> entry : discounts.entrySet()) {
        response.setValue(entry.getKey(), entry.getValue());
      }

    } catch (Exception e) {
      response.setFlash(e.getMessage());
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
    InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

    try {
      BigDecimal inTaxPrice = invoiceLine.getInTaxPrice();
      TaxLine taxLine = invoiceLine.getTaxLine();

      response.setValue(
          "price", Beans.get(InvoiceLineService.class).convertUnitPrice(true, taxLine, inTaxPrice));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
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

    InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

    try {
      BigDecimal exTaxPrice = invoiceLine.getPrice();
      TaxLine taxLine = invoiceLine.getTaxLine();

      response.setValue(
          "inTaxPrice",
          Beans.get(InvoiceLineService.class).convertUnitPrice(false, taxLine, exTaxPrice));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void convertUnitPrice(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();

    InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

    if (context.getParent().getContextClass() == InvoiceLine.class) {
      context = request.getContext().getParent();
    }

    Invoice invoice = this.getInvoice(context);

    if (invoice == null
        || invoiceLine.getProduct() == null
        || invoiceLine.getPrice() == null
        || invoiceLine.getInTaxPrice() == null) {
      return;
    }

    try {
      BigDecimal price = invoiceLine.getPrice();
      BigDecimal inTaxPrice =
          price.add(
              price.multiply(invoiceLine.getTaxLine().getValue().divide(new BigDecimal(100))));

      response.setValue("inTaxPrice", inTaxPrice);

    } catch (Exception e) {
      response.setFlash(e.getMessage());
    }
  }

  public void emptyLine(ActionRequest request, ActionResponse response) {
    InvoiceLine invoiceLine = request.getContext().asType(InvoiceLine.class);

    if (invoiceLine.getTypeSelect() != InvoiceLineRepository.TYPE_NORMAL) {
      Map<String, Object> newInvoiceLine = Mapper.toMap(new InvoiceLine());
      newInvoiceLine.put("qty", BigDecimal.ZERO);
      newInvoiceLine.put("id", invoiceLine.getId());
      newInvoiceLine.put("version", invoiceLine.getVersion());
      newInvoiceLine.put("typeSelect", invoiceLine.getTypeSelect());
      if (invoiceLine.getTypeSelect() == InvoiceLineRepository.TYPE_END_OF_PACK) {
        newInvoiceLine.put("productName", I18n.get(ITranslation.INVOICE_LINE_END_OF_PACK));
      }
      response.setValues(newInvoiceLine);
    }
  }

  public Invoice getInvoice(Context context) {

    Context parentContext = context.getParent();

    Invoice invoice = parentContext.asType(Invoice.class);

    if (!parentContext.getContextClass().toString().equals(Invoice.class.toString())) {

      InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

      invoice = invoiceLine.getInvoice();
    }

    return invoice;
  }

  public void getAccount(ActionRequest request, ActionResponse response) {

    try {

      InvoiceLine invoiceLine = request.getContext().asType(InvoiceLine.class);

      if (invoiceLine != null) {
        Product product = invoiceLine.getProduct();
        Invoice invoice = this.getInvoice(request.getContext());

        if (product != null) {
          Account account =
              Beans.get(AccountManagementServiceAccountImpl.class)
                  .getProductAccount(
                      product,
                      invoice.getCompany(),
                      invoice.getPartner().getFiscalPosition(),
                      InvoiceToolService.isPurchase(invoice),
                      invoiceLine.getFixedAssets());
          response.setValue("account", account);
        }
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void filterAccount(ActionRequest request, ActionResponse response) throws AxelorException {
    Context context = request.getContext();
    Invoice invoice = this.getInvoice(context);
    InvoiceLine invoiceLine = request.getContext().asType(InvoiceLine.class);
    if (invoice != null && invoice.getCompany() != null) {
      List<String> technicalTypeSelectList = new ArrayList<>();
      if (InvoiceToolService.isPurchase(invoice)) {
        if (invoiceLine.getFixedAssets()) {
          technicalTypeSelectList.add(AccountTypeRepository.TYPE_IMMOBILISATION);
        } else {
          technicalTypeSelectList.add(AccountTypeRepository.TYPE_DEBT);
          technicalTypeSelectList.add(AccountTypeRepository.TYPE_CHARGE);
        }
      } else {
        technicalTypeSelectList.add(AccountTypeRepository.TYPE_INCOME);
      }
      String domain =
          "self.company.id = "
              + invoice.getCompany().getId()
              + " AND self.accountType.technicalTypeSelect IN "
              + technicalTypeSelectList.stream().collect(Collectors.joining("','", "('", "')"));
      response.setAttr("account", "domain", domain);
    }
  }

  public void getFixedAssetCategory(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();
    InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

    if (context.getParent().getContextClass() == InvoiceLine.class) {
      context = request.getContext().getParent();
    }
    Invoice invoice = this.getInvoice(context);
    Product product = invoiceLine.getProduct();

    if (invoice == null || product == null) {
      return;
    }
    FixedAssetCategory fixedAssetCategory = null;
    if (!product.getAccountManagementList().isEmpty()
        && (invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE
            || invoice.getOperationTypeSelect()
                == InvoiceRepository.OPERATION_TYPE_SUPPLIER_REFUND)) {

      Optional<AccountManagement> optionalFixedAssetCategory =
          product.getAccountManagementList().stream()
              .filter(am -> am.getCompany() == invoice.getCompany())
              .findFirst();

      fixedAssetCategory =
          optionalFixedAssetCategory.isPresent()
              ? optionalFixedAssetCategory.get().getFixedAssetCategory()
              : null;
    }
    response.setValue("fixedAssetCategory", fixedAssetCategory);
  }
}
