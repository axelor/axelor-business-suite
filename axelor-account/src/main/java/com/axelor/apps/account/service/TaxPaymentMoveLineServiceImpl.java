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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.TaxPaymentMoveLine;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class TaxPaymentMoveLineServiceImpl implements TaxPaymentMoveLineService {

  @Override
  public TaxPaymentMoveLine computeTaxAmount(TaxPaymentMoveLine taxPaymentMoveLine)
      throws AxelorException {
    BigDecimal taxRate = taxPaymentMoveLine.getTaxRate().divide(new BigDecimal(100));
    BigDecimal base = taxPaymentMoveLine.getDetailPaymentAmount();
    taxPaymentMoveLine.setTaxAmount(base.multiply(taxRate).setScale(2, RoundingMode.HALF_UP));
    return taxPaymentMoveLine;
  }

  @Override
  public TaxPaymentMoveLine getReverseTaxPaymentMoveLine(TaxPaymentMoveLine taxPaymentMoveLine)
      throws AxelorException {
    TaxPaymentMoveLine reversetaxPaymentMoveLine =
        new TaxPaymentMoveLine(
            taxPaymentMoveLine.getMoveLine(),
            taxPaymentMoveLine.getOriginTaxLine(),
            taxPaymentMoveLine.getReconcile(),
            taxPaymentMoveLine.getTaxRate(),
            taxPaymentMoveLine.getDetailPaymentAmount().negate(),
            Beans.get(AppBaseService.class)
                .getTodayDate(taxPaymentMoveLine.getReconcile().getCompany()));
    reversetaxPaymentMoveLine = this.computeTaxAmount(reversetaxPaymentMoveLine);
    reversetaxPaymentMoveLine.setIsAlreadyReverse(true);
    taxPaymentMoveLine.setIsAlreadyReverse(true);
    return reversetaxPaymentMoveLine;
  }
}
