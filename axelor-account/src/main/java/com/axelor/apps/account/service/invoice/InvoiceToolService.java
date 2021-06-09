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
package com.axelor.apps.account.service.invoice;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceTerm;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentConditionLine;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.PaymentConditionLineRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Partner;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.CallMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.commons.collections.CollectionUtils;

/** InvoiceService est une classe implémentant l'ensemble des services de facturations. */
public class InvoiceToolService {

  /**
   * Method to get the oldest due date in non paid invoice terms return invoiceDate if null
   *
   * @param invoice
   * @return
   * @throws AxelorException
   */
  @CallMethod
  public static LocalDate getDueDate(Invoice invoice) throws AxelorException {

    LocalDate invoiceDate =
        isPurchase(invoice) ? invoice.getOriginDate() : invoice.getInvoiceDate();
    if (CollectionUtils.isEmpty(invoice.getInvoiceTermList())) {
      return invoiceDate;
    }

    LocalDate dueDate = null;
    for (InvoiceTerm invoiceTerm : invoice.getInvoiceTermList()) {
      if (!invoiceTerm.getIsPaid() && (dueDate == null || invoiceDate.isBefore(dueDate))) {
        dueDate = invoiceTerm.getDueDate();
      }
    }

    if (dueDate != null) {
      return dueDate;
    }
    return invoiceDate;
  }

  /**
   * Method to compute due date based on paymentConditionLine and invoiceDate
   *
   * @param paymentConditionLine
   * @param invoiceDate
   * @return
   */
  public static LocalDate getDueDate(
      PaymentConditionLine paymentConditionLine, LocalDate invoiceDate) {

    return getDueDate(
        paymentConditionLine.getTypeSelect(),
        paymentConditionLine.getPaymentTime(),
        paymentConditionLine.getPeriodTypeSelect(),
        paymentConditionLine.getDaySelect(),
        invoiceDate);
  }

  /**
   * Method to compute due date based on paymentCondition and invoiceDate
   *
   * @param typeSelect
   * @param paymentTime
   * @param periodTypeSelect
   * @param daySelect
   * @param invoiceDate
   * @return
   */
  public static LocalDate getDueDate(
      Integer typeSelect,
      Integer paymentTime,
      Integer periodTypeSelect,
      Integer daySelect,
      LocalDate invoiceDate) {

    LocalDate nDaysDate = null;
    if (periodTypeSelect.equals(PaymentConditionLineRepository.PERIOD_TYPE_DAYS)) {
      nDaysDate = invoiceDate.plusDays(paymentTime);
    } else {
      nDaysDate = invoiceDate.plusMonths(paymentTime);
    }

    switch (typeSelect) {
      case PaymentConditionLineRepository.TYPE_NET:
        return nDaysDate;

      case PaymentConditionLineRepository.TYPE_END_OF_MONTH_N_DAYS:
        if (periodTypeSelect.equals(PaymentConditionLineRepository.PERIOD_TYPE_DAYS)) {
          return invoiceDate.withDayOfMonth(invoiceDate.lengthOfMonth()).plusDays(paymentTime);
        } else {
          return invoiceDate.withDayOfMonth(invoiceDate.lengthOfMonth()).plusMonths(paymentTime);
        }
      case PaymentConditionLineRepository.TYPE_N_DAYS_END_OF_MONTH:
        return nDaysDate.withDayOfMonth(nDaysDate.lengthOfMonth());

      case PaymentConditionLineRepository.TYPE_N_DAYS_END_OF_MONTH_AT:
        return nDaysDate.withDayOfMonth(nDaysDate.lengthOfMonth()).plusDays(daySelect);

      default:
        return invoiceDate;
    }
  }

  /**
   * @param invoice
   *     <p>OperationTypeSelect 1 : Achat fournisseur 2 : Avoir fournisseur 3 : Vente client 4 :
   *     Avoir client
   * @return
   * @throws AxelorException
   */
  public static boolean isPurchase(Invoice invoice) throws AxelorException {

    switch (invoice.getOperationTypeSelect()) {
      case InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE: // fall-through
      case InvoiceRepository.OPERATION_TYPE_SUPPLIER_REFUND:
        return true;
      case InvoiceRepository.OPERATION_TYPE_CLIENT_SALE:
      case InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND: // fall-through
        return false;

      default:
        throw new AxelorException(
            invoice,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.MOVE_1),
            invoice.getInvoiceId());
    }
  }

  /**
   * @param invoice
   *     <p>OperationTypeSelect 1 : Achat fournisseur 2 : Avoir fournisseur 3 : Vente client 4 :
   *     Avoir client
   * @return
   * @throws AxelorException
   */
  public static boolean isRefund(Invoice invoice) throws AxelorException {

    boolean isRefund;

    switch (invoice.getOperationTypeSelect()) {
      case InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE:
        isRefund = false;
        break;
      case InvoiceRepository.OPERATION_TYPE_SUPPLIER_REFUND:
        isRefund = true;
        break;
      case InvoiceRepository.OPERATION_TYPE_CLIENT_SALE:
        isRefund = false;
        break;
      case InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND:
        isRefund = true;
        break;

      default:
        throw new AxelorException(
            invoice,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.MOVE_1),
            invoice.getInvoiceId());
    }

    return isRefund;
  }

  /**
   * @param invoice
   * @return
   * @throws AxelorException
   */
  public static boolean isOutPayment(Invoice invoice) throws AxelorException {
    if (invoice.getInTaxTotal().compareTo(BigDecimal.ZERO) >= 0) {
      // result of XOR operator, we could also have written "bool1 ^ bool2"
      return (isPurchase(invoice) != isRefund(invoice));
    } else {
      // return opposite if total amount is negative
      return (isPurchase(invoice) == isRefund(invoice));
    }
  }

  public static PaymentMode getPaymentMode(Invoice invoice) throws AxelorException {
    Partner partner = invoice.getPartner();

    if (InvoiceToolService.isOutPayment(invoice)) {
      if (partner != null) {
        PaymentMode paymentMode = partner.getOutPaymentMode();
        if (paymentMode != null) {
          return paymentMode;
        }
      }
      return Beans.get(AccountConfigService.class)
          .getAccountConfig(invoice.getCompany())
          .getOutPaymentMode();
    } else {
      if (partner != null) {
        PaymentMode paymentMode = partner.getInPaymentMode();
        if (paymentMode != null) {
          return paymentMode;
        }
      }
      return Beans.get(AccountConfigService.class)
          .getAccountConfig(invoice.getCompany())
          .getInPaymentMode();
    }
  }

  public static PaymentCondition getPaymentCondition(Invoice invoice) throws AxelorException {
    Partner partner = invoice.getPartner();

    if (partner != null) {
      PaymentCondition paymentCondition = partner.getPaymentCondition();
      if (paymentCondition != null) {
        return paymentCondition;
      }
    }
    return Beans.get(AccountConfigService.class)
        .getAccountConfig(invoice.getCompany())
        .getDefPaymentCondition();
  }

  /**
   * Method to call after copying an invoice to reset the status. Can be used after JPA.copy to
   * reset invoice status without losing references to other objets.<br>
   * <b>Most of the time you do not want to use this method directly but call {@link
   * InvoiceRepository#save(Invoice)} instead.</b>
   *
   * @param copy a copy of an invoice
   */
  public static void resetInvoiceStatusOnCopy(Invoice copy) {
    copy.setStatusSelect(InvoiceRepository.STATUS_DRAFT);
    copy.setInvoiceId(null);
    copy.setInvoiceDate(null);
    copy.setDueDate(null);
    copy.setValidatedByUser(null);
    copy.setMove(null);
    copy.setOriginalInvoice(null);
    copy.setCompanyInTaxTotalRemaining(BigDecimal.ZERO);
    copy.setAmountPaid(BigDecimal.ZERO);
    copy.setIrrecoverableStatusSelect(InvoiceRepository.IRRECOVERABLE_STATUS_NOT_IRRECOUVRABLE);
    copy.setAmountRejected(BigDecimal.ZERO);
    copy.clearBatchSet();
    copy.setDebitNumber(null);
    copy.setDoubtfulCustomerOk(false);
    copy.setMove(null);
    copy.setInterbankCodeLine(null);
    copy.setPaymentMove(null);
    copy.clearRefundInvoiceList();
    copy.setRejectDate(null);
    copy.setOriginalInvoice(null);
    copy.setUsherPassageOk(false);
    copy.setAlreadyPrintedOk(false);
    copy.setCanceledPaymentSchedule(null);
    copy.setDirectDebitAmount(BigDecimal.ZERO);
    copy.setImportId(null);
    copy.setPartnerAccount(null);
    copy.setJournal(null);
    copy.clearInvoicePaymentList();
    copy.setPrintedPDF(null);
    copy.setValidatedDate(null);
    copy.setVentilatedByUser(null);
    copy.setVentilatedDate(null);
    copy.setPfpValidateStatusSelect(InvoiceRepository.PFP_STATUS_AWAITING);
    copy.setDecisionPfpTakenDate(null);
    copy.setInternalReference(null);
    copy.setExternalReference(null);
    copy.clearInvoiceTermList();
  }
}
