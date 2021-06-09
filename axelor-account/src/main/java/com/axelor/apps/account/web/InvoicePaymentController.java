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

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.InvoiceTerm;
import com.axelor.apps.account.db.InvoiceTermPayment;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.InvoiceTermRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.account.service.move.MoveCustAccountService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCancelService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCreateService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentToolService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoiceTermPaymentService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.BankDetailsService;
import com.axelor.apps.tool.StringTool;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.collections.CollectionUtils;

@Singleton
public class InvoicePaymentController {

  public void cancelInvoicePayment(ActionRequest request, ActionResponse response) {
    InvoicePayment invoicePayment = request.getContext().asType(InvoicePayment.class);

    invoicePayment = Beans.get(InvoicePaymentRepository.class).find(invoicePayment.getId());
    try {
      Beans.get(InvoicePaymentCancelService.class).cancel(invoicePayment);
      Beans.get(MoveCustAccountService.class).updateCustomerAccount(invoicePayment.getMove());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
    response.setReload(true);
  }

  // filter the payment mode depending on the target invoice

  @SuppressWarnings("unchecked")
  public void filterPaymentMode(ActionRequest request, ActionResponse response) {
    Map<String, Object> partialInvoice = (Map<String, Object>) request.getContext().get("_invoice");
    Invoice invoice =
        Beans.get(InvoiceRepository.class).find(Long.valueOf(partialInvoice.get("id").toString()));
    PaymentMode paymentMode = invoice.getPaymentMode();
    if (invoice != null && paymentMode != null) {
      if (paymentMode.getInOutSelect() != null) {
        response.setAttr(
            "paymentMode", "domain", "self.inOutSelect = " + paymentMode.getInOutSelect());
      }
    }
  }

  /**
   * Create the domain for companyBankDetails field.
   *
   * @param request
   * @param response
   */
  @SuppressWarnings("unchecked")
  public void filterBankDetails(ActionRequest request, ActionResponse response) {
    InvoicePayment invoicePayment = request.getContext().asType(InvoicePayment.class);
    Map<String, Object> partialInvoice = (Map<String, Object>) request.getContext().get("_invoice");

    Invoice invoice =
        Beans.get(InvoiceRepository.class).find(((Integer) partialInvoice.get("id")).longValue());
    Company company = invoice.getCompany();
    List<BankDetails> bankDetailsList =
        Beans.get(InvoicePaymentToolService.class)
            .findCompatibleBankDetails(company, invoicePayment);
    if (bankDetailsList.isEmpty()) {
      response.setAttr("companyBankDetails", "domain", "self.id IN (0)");
    } else {
      String idList = StringTool.getIdListString(bankDetailsList);
      response.setAttr("companyBankDetails", "domain", "self.id IN (" + idList + ")");
    }
  }

  /**
   * On payment mode change, fill the bank details field if we find precisely one bank details
   * available in the payment mode for the current company.
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  @SuppressWarnings("unchecked")
  public void fillBankDetails(ActionRequest request, ActionResponse response)
      throws AxelorException {
    InvoicePayment invoicePayment = request.getContext().asType(InvoicePayment.class);
    Map<String, Object> partialInvoice = (Map<String, Object>) request.getContext().get("_invoice");

    Invoice invoice =
        Beans.get(InvoiceRepository.class).find(((Integer) partialInvoice.get("id")).longValue());
    PaymentMode paymentMode = invoicePayment.getPaymentMode();
    Company company = invoice.getCompany();
    List<BankDetails> bankDetailsList =
        Beans.get(InvoicePaymentToolService.class)
            .findCompatibleBankDetails(company, invoicePayment);
    if (bankDetailsList.size() == 1) {
      response.setValue("companyBankDetails", bankDetailsList.get(0));
    } else {
      response.setValue("companyBankDetails", null);
    }
    Partner partner = invoice.getPartner();
    if (company == null) {
      return;
    }
    if (partner != null) {
      partner = Beans.get(PartnerRepository.class).find(partner.getId());
    }
    BankDetails defaultBankDetails =
        Beans.get(BankDetailsService.class)
            .getDefaultCompanyBankDetails(company, paymentMode, partner, null);
    response.setValue("bankDetails", defaultBankDetails);
  }

  public void validateMassPayment(ActionRequest request, ActionResponse response) {
    try {

      InvoicePayment invoicePayment = request.getContext().asType(InvoicePayment.class);

      if (!ObjectUtils.isEmpty(request.getContext().get("_selectedInvoices"))) {
        List<Long> invoiceIdList =
            Lists.transform(
                (List) request.getContext().get("_selectedInvoices"),
                new Function<Object, Long>() {
                  @Nullable
                  @Override
                  public Long apply(@Nullable Object input) {
                    return Long.parseLong(input.toString());
                  }
                });

        Beans.get(InvoicePaymentCreateService.class)
            .createMassInvoicePayment(
                invoiceIdList,
                invoicePayment.getPaymentMode(),
                invoicePayment.getCompanyBankDetails(),
                invoicePayment.getPaymentDate(),
                invoicePayment.getBankDepositDate(),
                invoicePayment.getChequeNumber());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
    response.setReload(true);
  }
  /**
   * Method that check the invoice payment before save and save. Only use when add payment is used
   * in invoice
   *
   * @param request
   * @param response
   */
  public void checkConditionBeforeSave(ActionRequest request, ActionResponse response) {
    try {
      InvoicePayment invoicePayment = request.getContext().asType(InvoicePayment.class);
      Beans.get(InvoicePaymentToolService.class).checkConditionBeforeSave(invoicePayment);
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  /**
   * Method that loads unpaid invoice terms and init invoiceTermPayments
   *
   * @param request
   * @param response
   */
  public void loadInvoiceTerms(ActionRequest request, ActionResponse response) {
    try {
      InvoicePayment invoicePayment = request.getContext().asType(InvoicePayment.class);
      List<InvoiceTerm> invoiceTerms =
          Beans.get(InvoiceTermService.class)
              .getUnpaidInvoiceTermsFiltered(invoicePayment.getInvoice());
      if (CollectionUtils.isEmpty(invoiceTerms)) {
        return;
      }
      InvoiceTermPaymentService invoiceTermPaymentService =
          Beans.get(InvoiceTermPaymentService.class);
      List<InvoiceTermPayment> invoiceTermPayments =
          invoiceTermPaymentService.initInvoiceTermPayments(invoicePayment, invoiceTerms);
      response.setValue("$invoiceTerms", invoiceTerms);
      response.setValue("invoiceTermPaymentList", invoiceTermPayments);
      response.setValue(
          "amount",
          invoiceTermPaymentService.computeInvoicePaymentAmount(
              invoicePayment, invoiceTermPayments));
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  @SuppressWarnings("unchecked")
  public void addSelectedInvoiceTermsToPay(ActionRequest request, ActionResponse response) {
    try {
      InvoicePayment invoicePayment = request.getContext().asType(InvoicePayment.class);
      if (request.getContext().get("invoiceTerms") != null) {
        List<InvoiceTerm> selectedInvoiceTerms = Lists.newArrayList();
        InvoiceTermRepository invoiceTermRepo = Beans.get(InvoiceTermRepository.class);

        List<Map> invoiceTermsMap = (List<Map>) request.getContext().get("invoiceTerms");
        for (Map map : invoiceTermsMap) {
          if (map.get("selected") != null && ((boolean) map.get("selected"))) {
            selectedInvoiceTerms.add(invoiceTermRepo.find(new Long((Integer) map.get("id"))));
          }
        }
        if (selectedInvoiceTerms.isEmpty()) {
          response.setError(I18n.get(IExceptionMessage.INVOICE_PAYMENT_MISSING_TERM_LINE));
        } else {
          selectedInvoiceTerms =
              Beans.get(InvoiceTermService.class)
                  .filterInvoiceTermsByHoldBack(selectedInvoiceTerms);
          InvoiceTermPaymentService invoiceTermPaymentService =
              Beans.get(InvoiceTermPaymentService.class);
          List<InvoiceTermPayment> invoiceTermPayments =
              invoiceTermPaymentService.initInvoiceTermPayments(
                  invoicePayment, selectedInvoiceTerms);
          response.setValue("invoiceTermPaymentList", invoiceTermPayments);
          response.setValue(
              "amount",
              invoiceTermPaymentService.computeInvoicePaymentAmount(
                  invoicePayment, invoiceTermPayments));
        }
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void updateInvoiceTermsToPay(ActionRequest request, ActionResponse response) {
    try {
      InvoicePayment invoicePayment = request.getContext().asType(InvoicePayment.class);
      BigDecimal amount = invoicePayment.getAmount();
      List<InvoiceTerm> invoiceTerms =
          Beans.get(InvoiceTermService.class)
              .getUnpaidInvoiceTermsFiltered(invoicePayment.getInvoice());
      if (!CollectionUtils.isEmpty(invoiceTerms)) {
        response.setValue("$invoiceTerms", invoiceTerms);

        List<InvoiceTermPayment> invoiceTermPayments = Lists.newArrayList();
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
          invoiceTermPayments =
              Beans.get(InvoiceTermPaymentService.class)
                  .initInvoiceTermPaymentsWithAmount(invoicePayment, invoiceTerms, amount);
        }
        response.setValue("invoiceTermPaymentList", invoiceTermPayments);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}
