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
package com.axelor.apps.account.web;

import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.account.service.IrrecoverableService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.account.service.invoice.print.InvoicePrintService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Language;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PrintingSettings;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.base.db.repo.LanguageRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.base.service.BankDetailsService;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.TradingNameService;
import com.axelor.apps.tool.StringTool;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InvoiceController {

  @SuppressWarnings("unused")
  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private InvoiceService invoiceService;

  @Inject private InvoiceRepository invoiceRepo;

  @Inject private InvoicePrintService invoicePrintService;

  /**
   * Fonction appeler par le bouton calculer
   *
   * @param request
   * @param response
   * @return
   */
  public void compute(ActionRequest request, ActionResponse response) {

    Invoice invoice = request.getContext().asType(Invoice.class);

    try {
      invoice = invoiceService.compute(invoice);
      response.setValues(invoice);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Fonction appeler par le bouton valider
   *
   * @param request
   * @param response
   * @return
   */
  public void validate(ActionRequest request, ActionResponse response) throws AxelorException {

    Invoice invoice = request.getContext().asType(Invoice.class);
    invoice = invoiceRepo.find(invoice.getId());

    try {
      invoiceService.validate(invoice);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Fonction appeler par le bouton ventiler
   *
   * @param request
   * @param response
   * @return
   * @throws AxelorException
   */
  public void ventilate(ActionRequest request, ActionResponse response) throws AxelorException {

    Invoice invoice = request.getContext().asType(Invoice.class);
    invoice = invoiceRepo.find(invoice.getId());

    try {
      invoiceService.ventilate(invoice);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called by the validate button, if the ventilation is skipped in invoice config
   *
   * @param request
   * @param response
   * @return
   * @throws AxelorException
   */
  public void validateAndVentilate(ActionRequest request, ActionResponse response)
      throws AxelorException {

    Invoice invoice = request.getContext().asType(Invoice.class);
    invoice = invoiceRepo.find(invoice.getId());

    try {
      invoiceService.validateAndVentilate(invoice);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Passe l'état de la facture à "annulée"
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void cancel(ActionRequest request, ActionResponse response) throws AxelorException {

    Invoice invoice = request.getContext().asType(Invoice.class);
    invoice = invoiceRepo.find(invoice.getId());

    invoiceService.cancel(invoice);
    response.setFlash(I18n.get(IExceptionMessage.INVOICE_1));
    response.setReload(true);
  }

  /**
   * Function returning both the paymentMode and the paymentCondition
   *
   * @param request
   * @param response
   */
  public void fillPaymentModeAndCondition(ActionRequest request, ActionResponse response) {
    Invoice invoice = request.getContext().asType(Invoice.class);
    try {
      if (invoice.getOperationTypeSelect() == null) {
        return;
      }
      PaymentMode paymentMode = InvoiceToolService.getPaymentMode(invoice);
      PaymentCondition paymentCondition = InvoiceToolService.getPaymentCondition(invoice);
      response.setValue("paymentMode", paymentMode);
      response.setValue("paymentCondition", paymentCondition);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void checkNotImputedRefunds(ActionRequest request, ActionResponse response) {
    Invoice invoice = request.getContext().asType(Invoice.class);
    invoice = invoiceRepo.find(invoice.getId());

    try {
      String msg = invoiceService.checkNotImputedRefunds(invoice);
      if (msg != null) {
        response.setFlash(msg);
      }
    } catch (AxelorException e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Fonction appeler par le bouton générer un avoir.
   *
   * @param request
   * @param response
   */
  public void createRefund(ActionRequest request, ActionResponse response) {

    Invoice invoice = request.getContext().asType(Invoice.class);

    try {

      invoice = invoiceRepo.find(invoice.getId());
      Invoice refund = invoiceService.createRefund(invoice);
      response.setReload(true);
      response.setNotify(I18n.get(IExceptionMessage.INVOICE_2));

      response.setView(
          ActionView.define(
                  String.format(I18n.get(IExceptionMessage.INVOICE_4), invoice.getInvoiceId()))
              .model(Invoice.class.getName())
              .add("form", "invoice-form")
              .add("grid", "invoice-grid")
              .param("forceTitle", "true")
              .context("_showRecord", refund.getId().toString())
              .domain("self.originalInvoice.id = " + invoice.getId())
              .map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void usherProcess(ActionRequest request, ActionResponse response) {

    Invoice invoice = request.getContext().asType(Invoice.class);
    invoice = invoiceRepo.find(invoice.getId());

    try {
      invoiceService.usherProcess(invoice);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void passInIrrecoverable(ActionRequest request, ActionResponse response) {

    Invoice invoice = request.getContext().asType(Invoice.class);
    invoice = invoiceRepo.find(invoice.getId());

    try {
      Beans.get(IrrecoverableService.class).passInIrrecoverable(invoice, true);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void notPassInIrrecoverable(ActionRequest request, ActionResponse response) {

    Invoice invoice = request.getContext().asType(Invoice.class);
    invoice = invoiceRepo.find(invoice.getId());

    try {
      Beans.get(IrrecoverableService.class).notPassInIrrecoverable(invoice);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /** Method to generate invoice as a Pdf */
  @SuppressWarnings("unchecked")
  public void showInvoice(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();
    String fileLink;
    String title;

    try {
      if (!ObjectUtils.isEmpty(request.getContext().get("_ids"))) {
        List<Long> ids =
            Lists.transform(
                (List) request.getContext().get("_ids"),
                new Function<Object, Long>() {
                  @Nullable
                  @Override
                  public Long apply(@Nullable Object input) {
                    return Long.parseLong(input.toString());
                  }
                });
        fileLink = invoicePrintService.printInvoices(ids);
        title = I18n.get("Invoices");
      } else if (context.get("id") != null) {
        String format = context.get("format") != null ? context.get("format").toString() : "pdf";
        Integer reportType =
            context.get("reportType") != null
                ? Integer.parseInt(context.get("reportType").toString())
                : null;

        Map languageMap =
            (reportType == 1 || reportType == 3) && context.get("language") != null
                ? (Map<String, Object>) request.getContext().get("language")
                : null;
        Language language =
            languageMap.get("id") != null
                ? Beans.get(LanguageRepository.class)
                    .find(Long.parseLong(languageMap.get("id").toString()))
                : null;
        String locale = language != null ? language.getCode() : null;
        fileLink =
            invoicePrintService.printInvoice(
                invoiceRepo.find(Long.parseLong(context.get("id").toString())),
                false,
                format,
                reportType,
                locale);
        title = I18n.get("Invoice");
        response.setCanClose(true);
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.INVOICE_3));
      }
      response.setView(ActionView.define(title).add("html", fileLink).map());
      response.setCanClose(true);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void regenerateAndShowInvoice(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();
    Invoice invoice = invoiceRepo.find(Long.parseLong(context.get("id").toString()));
    Integer reportType =
        context.get("reportType") != null
            ? Integer.parseInt(context.get("reportType").toString())
            : null;

    try {
      response.setCanClose(true);
      response.setView(
          ActionView.define(I18n.get("Invoice"))
              .add("html", invoicePrintService.printInvoice(invoice, true, "pdf", reportType, null))
              .map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  private String buildMassMessage(int doneCount, int errorCount) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        String.format(
            I18n.get(
                com.axelor.apps.base.exceptions.IExceptionMessage.ABSTRACT_BATCH_DONE_SINGULAR,
                com.axelor.apps.base.exceptions.IExceptionMessage.ABSTRACT_BATCH_DONE_PLURAL,
                doneCount),
            doneCount));
    sb.append(" ");
    sb.append(
        String.format(
            I18n.get(
                com.axelor.apps.base.exceptions.IExceptionMessage.ABSTRACT_BATCH_ANOMALY_SINGULAR,
                com.axelor.apps.base.exceptions.IExceptionMessage.ABSTRACT_BATCH_ANOMALY_PLURAL,
                errorCount),
            errorCount));
    return sb.toString();
  }

  private void massProcess(
      ActionRequest request,
      ActionResponse response,
      Function<Collection<? extends Number>, Pair<Integer, Integer>> function) {

    try {
      @SuppressWarnings("unchecked")
      List<Number> ids = (List<Number>) request.getContext().get("_ids");

      if (ObjectUtils.isEmpty(ids)) {
        response.setError(com.axelor.apps.base.exceptions.IExceptionMessage.RECORD_NONE_SELECTED);
        return;
      }

      Pair<Integer, Integer> massCount = function.apply(ids);

      String message = buildMassMessage(massCount.getLeft(), massCount.getRight());
      response.setFlash(message);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    } finally {
      response.setReload(true);
    }
  }

  public void massValidation(ActionRequest request, ActionResponse response) {
    try {
      Function<Collection<? extends Number>, Pair<Integer, Integer>> function;

      if (Beans.get(AppAccountService.class).getAppInvoice().getIsVentilationSkipped()) {
        function = invoiceService::massValidateAndVentilate;
      } else {
        function = invoiceService::massValidate;
      }

      massProcess(request, response, function);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void massVentilation(ActionRequest request, ActionResponse response) {
    try {
      massProcess(request, response, invoiceService::massVentilate);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  // Generate single invoice from several
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void mergeInvoice(ActionRequest request, ActionResponse response) {
    List<Invoice> invoiceList = new ArrayList<>();
    List<Long> invoiceIdList = new ArrayList<>();
    boolean fromPopup = false;

    if (request.getContext().get("invoiceToMerge") != null) {

      if (request.getContext().get("invoiceToMerge") instanceof List) {
        // No confirmation popup, invoices are content in a parameter list
        List<Map> invoiceMap = (List<Map>) request.getContext().get("invoiceToMerge");
        for (Map map : invoiceMap) {
          invoiceIdList.add(new Long((Integer) map.get("id")));
        }
      } else {
        // After confirmation popup, invoice's id are in a string separated by ","
        String invoiceIdListStr = (String) request.getContext().get("invoiceToMerge");
        for (String invoiceId : invoiceIdListStr.split(",")) {
          invoiceIdList.add(new Long(invoiceId));
        }
        fromPopup = true;
      }
    }

    // Check if company, currency and partner are the same for all selected invoices
    Company commonCompany = null;
    Currency commonCurrency = null;
    Partner commonPartner = null;
    PaymentCondition commonPaymentCondition = null;
    // Useful to determine if a difference exists between payment conditions of all invoices
    boolean existPaymentConditionDiff = false;
    Partner commonContactPartner = null;
    // Useful to determine if a difference exists between contact partners of all purchase orders
    boolean existContactPartnerDiff = false;
    PriceList commonPriceList = null;
    // Useful to determine if a difference exists between price lists of all purchase orders
    boolean existPriceListDiff = false;
    PaymentMode commonPaymentMode = null;
    // Useful to determine if a difference exists between locations of all purchase orders
    boolean existPaymentModeDiff = false;

    Invoice invoiceTemp;
    int count = 1;
    for (Long invoiceId : invoiceIdList) {
      invoiceTemp = invoiceRepo.find(invoiceId);
      invoiceList.add(invoiceTemp);
      if (count == 1) {
        commonCompany = invoiceTemp.getCompany();
        commonCurrency = invoiceTemp.getCurrency();
        commonPartner = invoiceTemp.getPartner();
        commonPaymentCondition = invoiceTemp.getPaymentCondition();
        commonContactPartner = invoiceTemp.getContactPartner();
        commonPriceList = invoiceTemp.getPriceList();
        commonPaymentMode = invoiceTemp.getPaymentMode();
      } else {
        if (commonCompany != null && !commonCompany.equals(invoiceTemp.getCompany())) {
          commonCompany = null;
        }
        if (commonCurrency != null && !commonCurrency.equals(invoiceTemp.getCurrency())) {
          commonCurrency = null;
        }
        if (commonPartner != null && !commonPartner.equals(invoiceTemp.getPartner())) {
          commonPartner = null;
        }
        if (commonPaymentCondition != null
            && !commonPaymentCondition.equals(invoiceTemp.getPaymentCondition())) {
          commonPaymentCondition = null;
          existPaymentConditionDiff = true;
        }
        if (commonContactPartner != null
            && !commonContactPartner.equals(invoiceTemp.getContactPartner())) {
          commonContactPartner = null;
          existContactPartnerDiff = true;
        }
        if (commonPriceList != null && !commonPriceList.equals(invoiceTemp.getPriceList())) {
          commonPriceList = null;
          existPriceListDiff = true;
        }
        if (commonPaymentMode != null && !commonPaymentMode.equals(invoiceTemp.getPaymentMode())) {
          commonPaymentMode = null;
          existPaymentModeDiff = true;
        }
      }
      count++;
    }

    StringBuilder fieldErrors = new StringBuilder();
    if (commonCurrency == null) {
      fieldErrors.append(I18n.get(IExceptionMessage.INVOICE_MERGE_ERROR_CURRENCY));
    }
    if (commonCompany == null) {
      if (fieldErrors.length() > 0) {
        fieldErrors.append("<br/>");
      }
      fieldErrors.append(I18n.get(IExceptionMessage.INVOICE_MERGE_ERROR_COMPANY));
    }
    if (commonPartner == null) {
      if (fieldErrors.length() > 0) {
        fieldErrors.append("<br/>");
      }
      fieldErrors.append(I18n.get(IExceptionMessage.INVOICE_MERGE_ERROR_PARTNER));
    }

    if (fieldErrors.length() > 0) {
      response.setFlash(fieldErrors.toString());
      return;
    }

    // Check if contactPartner or priceList or paymentMode or paymentCondition  or saleOrder are
    // content in parameters
    if (request.getContext().get("contactPartner") != null) {
      commonContactPartner =
          JPA.em()
              .find(
                  Partner.class,
                  new Long((Integer) ((Map) request.getContext().get("contactPartner")).get("id")));
    }
    if (request.getContext().get("priceList") != null) {
      commonPriceList =
          JPA.em()
              .find(
                  PriceList.class,
                  new Long((Integer) ((Map) request.getContext().get("priceList")).get("id")));
    }
    if (request.getContext().get("paymentMode") != null) {
      commonPaymentMode =
          JPA.em()
              .find(
                  PaymentMode.class,
                  new Long((Integer) ((Map) request.getContext().get("paymentMode")).get("id")));
    }
    if (request.getContext().get("paymentCondition") != null) {
      commonPaymentCondition =
          JPA.em()
              .find(
                  PaymentCondition.class,
                  new Long(
                      (Integer) ((Map) request.getContext().get("paymentCondition")).get("id")));
    }

    if (!fromPopup
        && (existPaymentConditionDiff
            || existContactPartnerDiff
            || existPriceListDiff
            || existPaymentModeDiff)) {
      // Need to display intermediate screen to select some values
      ActionViewBuilder confirmView =
          ActionView.define("Confirm merge invoice")
              .model(Wizard.class.getName())
              .add("form", "customer-invoices-merge-confirm-form")
              .param("popup", "true")
              .param("show-toolbar", "false")
              .param("show-confirm", "false")
              .param("popup-save", "false")
              .param("forceEdit", "true");

      if (existContactPartnerDiff) {
        confirmView.context("contextContactPartnerToCheck", "true");
        confirmView.context("contextPartnerId", commonPartner.getId().toString());
      }
      if (existPriceListDiff) {
        confirmView.context("contextPriceListToCheck", "true");
      }
      if (existPaymentModeDiff) {
        confirmView.context("contextPaymentModeToCheck", "true");
      }
      if (existPaymentConditionDiff) {
        confirmView.context("contextPaymentConditionToCheck", "true");
      }
      confirmView.context("invoiceToMerge", Joiner.on(",").join(invoiceIdList));

      response.setView(confirmView.map());

      return;
    }
    try {
      Invoice invoice =
          invoiceService.mergeInvoice(
              invoiceList,
              commonCompany,
              commonCurrency,
              commonPartner,
              commonContactPartner,
              commonPriceList,
              commonPaymentMode,
              commonPaymentCondition);
      if (invoice != null) {
        // Open the generated invoice in a new tab
        response.setView(
            ActionView.define("Invoice")
                .model(Invoice.class.getName())
                .add("grid", "invoice-grid")
                .add("form", "invoice-form")
                .param("forceEdit", "true")
                .context("_showRecord", String.valueOf(invoice.getId()))
                .map());
        response.setCanClose(true);
      }
    } catch (Exception e) {
      response.setFlash(e.getLocalizedMessage());
    }
  }

  public void computeAddressStr(ActionRequest request, ActionResponse response) {
    Invoice invoice = request.getContext().asType(Invoice.class);
    response.setValue(
        "addressStr", Beans.get(AddressService.class).computeAddressStr(invoice.getAddress()));
  }
  /**
   * Called on load and in partner, company or payment mode change. Fill the bank details with a
   * default value.
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void fillCompanyBankDetails(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Invoice invoice = request.getContext().asType(Invoice.class);
    PaymentMode paymentMode = invoice.getPaymentMode();
    Company company = invoice.getCompany();
    Partner partner = invoice.getPartner();
    if (company == null) {
      return;
    }
    if (partner != null) {
      partner = Beans.get(PartnerRepository.class).find(partner.getId());
    }
    BankDetails defaultBankDetails =
        Beans.get(BankDetailsService.class)
            .getDefaultCompanyBankDetails(
                company, paymentMode, partner, invoice.getOperationTypeSelect());
    response.setValue("companyBankDetails", defaultBankDetails);
  }

  /**
   * Called on load and on new, create the domain for the field {@link
   * Invoice#advancePaymentInvoiceSet}
   *
   * @param request
   * @param response
   */
  public void fillAdvancePaymentInvoiceSetDomain(ActionRequest request, ActionResponse response) {

    Invoice invoice = request.getContext().asType(Invoice.class);
    try {
      String domain = invoiceService.createAdvancePaymentInvoiceSetDomain(invoice);
      response.setAttr("advancePaymentInvoiceSet", "domain", domain);

    } catch (Exception e) {
      TraceBackService.trace(e);
      response.setError(e.getMessage());
    }
  }

  /**
   * Called on partner and currency change, fill the domain of the field {@link
   * Invoice#advancePaymentInvoiceSet} with default values. The default values are every invoices
   * found in the domain.
   *
   * @param request
   * @param response
   */
  public void fillAdvancePaymentInvoiceSet(ActionRequest request, ActionResponse response) {

    Invoice invoice = request.getContext().asType(Invoice.class);
    try {
      Set<Invoice> invoices = invoiceService.getDefaultAdvancePaymentInvoice(invoice);
      response.setValue("advancePaymentInvoiceSet", invoices);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * set default value for automatic invoice printing
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void setDefaultMail(ActionRequest request, ActionResponse response) {
    Invoice invoice = request.getContext().asType(Invoice.class);
    Company company = invoice.getCompany();
    Partner partner = invoice.getPartner();
    if (company != null && partner != null) {
      AccountingSituation accountingSituation =
          Beans.get(AccountingSituationService.class).getAccountingSituation(partner, company);
      if (accountingSituation != null) {
        response.setValue("invoiceAutomaticMail", accountingSituation.getInvoiceAutomaticMail());
        response.setValue(
            "invoiceMessageTemplate", accountingSituation.getInvoiceMessageTemplate());
      }
    }
  }

  /**
   * Called on printing settings select. Set the domain for {@link Invoice#printingSettings}
   *
   * @param request
   * @param response
   */
  public void filterPrintingSettings(ActionRequest request, ActionResponse response) {
    Invoice invoice = request.getContext().asType(Invoice.class);

    List<PrintingSettings> printingSettingsList =
        Beans.get(TradingNameService.class)
            .getPrintingSettingsList(invoice.getTradingName(), invoice.getCompany());
    String domain =
        String.format(
            "self.id IN (%s)",
            !printingSettingsList.isEmpty()
                ? StringTool.getIdListString(printingSettingsList)
                : "0");

    response.setAttr("printingSettings", "domain", domain);
  }

  /**
   * Called on trading name change. Set the default value for {@link Invoice#printingSettings}
   *
   * @param request
   * @param response
   */
  public void fillDefaultPrintingSettings(ActionRequest request, ActionResponse response) {
    try {
      Invoice invoice = request.getContext().asType(Invoice.class);
      response.setValue(
          "printingSettings",
          Beans.get(TradingNameService.class)
              .getDefaultPrintingSettings(invoice.getTradingName(), invoice.getCompany()));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from invoice form view on partner change. Get the default price list for the invoice.
   * Call {@link PartnerPriceListService#getDefaultPriceList(Partner, int)}.
   *
   * @param request
   * @param response
   */
  public void fillPriceList(ActionRequest request, ActionResponse response) {
    try {
      Invoice invoice = request.getContext().asType(Invoice.class);
      Partner partner = invoice.getPartner();
      if (partner == null) {
        return;
      }
      int priceListTypeSelect = invoiceService.getPurchaseTypeOrSaleType(invoice);
      response.setValue(
          "priceList",
          Beans.get(PartnerPriceListService.class)
              .getDefaultPriceList(partner, priceListTypeSelect));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  /**
   * Called from invoice view on price list select. Call {@link
   * PartnerPriceListService#getPriceListDomain(Partner, int)}.
   *
   * @param request
   * @param response
   */
  public void changePriceListDomain(ActionRequest request, ActionResponse response) {
    try {
      Invoice invoice = request.getContext().asType(Invoice.class);
      int priceListTypeSelect = invoiceService.getPurchaseTypeOrSaleType(invoice);
      String domain =
          Beans.get(PartnerPriceListService.class)
              .getPriceListDomain(invoice.getPartner(), priceListTypeSelect);
      response.setAttr("priceList", "domain", domain);
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void massPaymentOnSupplierInvoices(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();

      if (!ObjectUtils.isEmpty(context.get("_ids"))) {
        List<Long> invoiceIdList =
            Lists.transform(
                (List) context.get("_ids"),
                new Function<Object, Long>() {
                  @Nullable
                  @Override
                  public Long apply(@Nullable Object input) {
                    return Long.parseLong(input.toString());
                  }
                });

        Company company = null;
        Currency currency = null;

        List<Long> invoiceToPay = new ArrayList<>();

        for (Long invoiceId : invoiceIdList) {
          Invoice invoice = invoiceRepo.find(invoiceId);

          if (invoice.getStatusSelect() != InvoiceRepository.STATUS_VENTILATED
              && (invoice.getOperationSubTypeSelect()
                      == InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE
                  && invoice.getStatusSelect() != InvoiceRepository.STATUS_VALIDATED)) {

            continue;
          }
          if (invoice.getAmountRemaining().compareTo(BigDecimal.ZERO) == 0) {

            continue;
          }

          if (company == null) {
            company = invoice.getCompany();
          }
          if (currency == null) {
            currency = invoice.getCurrency();
          }

          if (invoice.getCompany() == null
              || company == null
              || !invoice.getCompany().equals(company)) {
            response.setError(I18n.get(IExceptionMessage.INVOICE_MERGE_ERROR_COMPANY));
            return;
          }
          if (invoice.getCurrency() == null
              || currency == null
              || !invoice.getCurrency().equals(currency)) {
            response.setError(I18n.get(IExceptionMessage.INVOICE_MERGE_ERROR_CURRENCY));
            return;
          }

          invoiceToPay.add(invoiceId);
        }

        if (invoiceToPay.isEmpty()) {
          response.setError(I18n.get(IExceptionMessage.INVOICE_NO_INVOICE_TO_PAY));
        }

        response.setView(
            ActionView.define(I18n.get("Register a mass payment"))
                .model(InvoicePayment.class.getName())
                .add("form", "invoice-payment-mass-form")
                .param("popup", "reload")
                .param("show-toolbar", "false")
                .param("show-confirm", "false")
                .param("popup-save", "false")
                .param("forceEdit", "true")
                .context("_invoices", invoiceToPay)
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void checkPartnerBankDetailsList(ActionRequest request, ActionResponse response) {
    Invoice invoice = request.getContext().asType(Invoice.class);
    response.setAttr(
        "$partnerBankDetailsListWarning",
        "hidden",
        invoiceService.checkPartnerBankDetailsList(invoice));
  }
}
