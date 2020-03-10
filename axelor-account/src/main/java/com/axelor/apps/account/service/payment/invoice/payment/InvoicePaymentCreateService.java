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
package com.axelor.apps.account.service.payment.invoice.payment;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Currency;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface InvoicePaymentCreateService {

  @Inject
  public InvoicePayment createInvoicePayment(
      Invoice invoice,
      BigDecimal amount,
      LocalDate paymentDate,
      Currency currency,
      PaymentMode paymentMode,
      int typeSelect);

  public InvoicePayment createInvoicePayment(
      Invoice invoice, BigDecimal amount, Move paymentMove, LocalDate paymentDate)
      throws AxelorException;

  /**
   * Create an invoice payment for the specified invoice and with the specified bank details.
   *
   * @param invoice
   * @param companyBankDetails
   * @return
   */
  public InvoicePayment createInvoicePayment(Invoice invoice, BankDetails companyBankDetails);

  /**
   * Create an invoice payment for each invoice
   *
   * @param invoiceList
   * @param paymentMode
   * @param companyBankDetails
   * @return
   */
  public List<InvoicePayment> createMassInvoicePayment(
      List<Long> invoiceList,
      PaymentMode paymentMode,
      BankDetails companyBankDetails,
      LocalDate paymentDate,
      LocalDate bankDepositDate,
      String chequeNumber)
      throws AxelorException;

  public List<Long> getInvoiceIdsToPay(List<Long> invoiceIdList) throws AxelorException;
}
