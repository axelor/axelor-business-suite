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
package com.axelor.apps.account.service.payment.invoice.payment;

import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.InvoiceTerm;
import com.axelor.apps.account.db.InvoiceTermPayment;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;
import java.util.List;

public interface InvoiceTermPaymentService {

  /**
   * Method to init invoiceTermPayments based on invoiceTerm to pay
   *
   * @param invoicePayment
   * @param invoiceTermsToPay
   * @return
   */
  public List<InvoiceTermPayment> initInvoiceTermPayments(
      InvoicePayment invoicePayment, List<InvoiceTerm> invoiceTermsToPay);

  /**
   * Method to init invoiceTermPayments based on invoiceTerm to pay and inserted amount
   *
   * @param invoicePayment
   * @param invoiceTermsToPay
   * @param availableAmount
   * @return
   */
  public List<InvoiceTermPayment> initInvoiceTermPaymentsWithAmount(
      InvoicePayment invoicePayment,
      List<InvoiceTerm> invoiceTermsToPay,
      BigDecimal availableAmount)
      throws AxelorException;

  /**
   * Method to create invoiceTermPayments for an invoicePayment
   *
   * @param invoicePayment
   * @return
   */
  public void createInvoicePaymentTerms(InvoicePayment invoicePayment) throws AxelorException;

  /**
   * Method to create new InvoiceTermPayment usign invoiceTerm amountRemaining
   *
   * @param invoicePayment
   * @param invoiceTermToPay
   * @param paidAmount
   * @return
   */
  public InvoiceTermPayment createInvoiceTermPayment(
      InvoicePayment invoicePayment, InvoiceTerm invoiceTermToPay, BigDecimal paidAmount);

  /**
   * Method to compute total paid amount of invoiceTermPayments
   *
   * @param invoicePayment
   * @param invoiceTermPayments
   * @return
   */
  public BigDecimal computeInvoicePaymentAmount(
      InvoicePayment invoicePayment, List<InvoiceTermPayment> invoiceTermPayments)
      throws AxelorException;

  /**
   * Method to update invoice Payment Amount based on its invoiceTermPayments
   *
   * @param invoicePayment
   * @return
   */
  public InvoicePayment updateInvoicePaymentAmount(InvoicePayment invoicePayment)
      throws AxelorException;
}
