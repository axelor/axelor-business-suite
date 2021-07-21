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

import com.axelor.apps.account.db.PayVoucherElementToPay;
import com.axelor.apps.account.db.repo.PayVoucherElementToPayRepository;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;

@Singleton
public class PayVoucherElementController {

  @Transactional
  public void updateAmountToPayCurrency(ActionRequest request, ActionResponse response) {
    PayVoucherElementToPay elementToPayContext =
        request.getContext().asType(PayVoucherElementToPay.class);
    PayVoucherElementToPay elementToPay =
        Beans.get(PayVoucherElementToPayRepository.class).find(elementToPayContext.getId());
    Currency paymentVoucherCurrency = elementToPay.getPaymentVoucher().getCurrency();
    BigDecimal amountToPayCurrency = null;
    try {
      amountToPayCurrency =
          Beans.get(CurrencyService.class)
              .getAmountCurrencyConvertedAtDate(
                  elementToPay.getCurrency(),
                  paymentVoucherCurrency,
                  elementToPay.getAmountToPay(),
                  elementToPay.getPaymentVoucher().getPaymentDate());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
    if (amountToPayCurrency != null) {
      elementToPay.setAmountToPayCurrency(amountToPayCurrency);
      elementToPay.setRemainingAmountAfterPayment(
          elementToPay.getRemainingAmount().subtract(elementToPay.getAmountToPay()));
      Beans.get(PayVoucherElementToPayRepository.class).save(elementToPay);
      response.setReload(true);
    }
  }
}
