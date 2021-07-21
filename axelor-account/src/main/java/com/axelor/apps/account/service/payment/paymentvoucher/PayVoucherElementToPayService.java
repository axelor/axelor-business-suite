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
package com.axelor.apps.account.service.payment.paymentvoucher;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PayVoucherElementToPay;
import com.axelor.apps.account.db.PaymentVoucher;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayVoucherElementToPayService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * Generic method for creating invoice to pay lines (2nd O2M in the view)
   *
   * @param pv
   * @param seq
   * @return
   */
  public PayVoucherElementToPay createPayVoucherElementToPay(
      PaymentVoucher pv,
      int seq,
      Invoice invoice,
      MoveLine ml,
      BigDecimal totalAmount,
      BigDecimal remainingAmount,
      BigDecimal amountToPay) {

    log.debug("In  createPayVoucherElementToPay....");

    if (pv != null && ml != null) {
      PayVoucherElementToPay piToPay = new PayVoucherElementToPay();
      piToPay.setSequence(seq);
      piToPay.setMoveLine(ml);
      piToPay.setTotalAmount(totalAmount);
      piToPay.setRemainingAmount(remainingAmount);
      piToPay.setAmountToPay(amountToPay);
      piToPay.setPaymentVoucher(pv);

      log.debug("End createPayVoucherElementToPay IF.");

      return piToPay;
    } else {
      log.debug("End createPayVoucherElementToPay ELSE.");
      return null;
    }
  }
}
