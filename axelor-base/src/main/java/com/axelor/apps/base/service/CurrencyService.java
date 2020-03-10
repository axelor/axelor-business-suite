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
package com.axelor.apps.base.service;

import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.CurrencyConversionLine;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.CallMethod;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CurrencyService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AppBaseService appBaseService;

  private LocalDate today;

  @Inject
  public CurrencyService(AppBaseService appBaseService) {

    this.appBaseService = appBaseService;
    this.today = appBaseService.getTodayDate();
  }

  public CurrencyService(LocalDate today) {

    this.appBaseService = Beans.get(AppBaseService.class);
    this.today = today;
  }

  @CallMethod
  public BigDecimal getCurrencyConversionRate(Currency startCurrency, Currency endCurrency)
      throws AxelorException {
    return this.getCurrencyConversionRate(startCurrency, endCurrency, this.today);
  }

  public BigDecimal getCurrencyConversionRate(
      Currency startCurrency, Currency endCurrency, LocalDate date) throws AxelorException {

    // If the start currency is different from end currency
    // So we convert the amount
    if (startCurrency != null && endCurrency != null && !startCurrency.equals(endCurrency)) {

      LocalDate dateToConvert = this.getDateToConvert(date);
      boolean isInverse = true;
      BigDecimal exchangeRate = null;

      CurrencyConversionLine currencyConversionLine =
          this.getCurrencyConversionLine(startCurrency, endCurrency, dateToConvert);
      if (currencyConversionLine != null) {
        exchangeRate = currencyConversionLine.getExchangeRate();
        isInverse = false;

      } else {
        currencyConversionLine =
            this.getCurrencyConversionLine(endCurrency, startCurrency, dateToConvert);

        if (currencyConversionLine == null) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.CURRENCY_1),
              startCurrency.getName(),
              endCurrency.getName(),
              dateToConvert);
        }
        exchangeRate = currencyConversionLine.getExchangeRate();
      }

      if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) == 0) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CURRENCY_2),
            startCurrency.getName(),
            endCurrency.getName(),
            dateToConvert);
      }

      return isInverse
          ? BigDecimal.ONE.divide(exchangeRate, 10, RoundingMode.HALF_EVEN)
          : exchangeRate;
    }

    return BigDecimal.ONE;
  }

  protected CurrencyConversionLine getCurrencyConversionLine(
      Currency startCurrency, Currency endCurrency, LocalDate localDate) {

    List<CurrencyConversionLine> currencyConversionLineList =
        appBaseService.getCurrencyConfigurationLineList();

    if (currencyConversionLineList == null) {
      return null;
    }

    log.debug(
        "Currency from: {}, Currency to: {}, localDate: {}", startCurrency, endCurrency, localDate);

    for (CurrencyConversionLine ccl : currencyConversionLineList) {

      String cclStartCode = ccl.getStartCurrency().getCode();
      String cclEndCode = ccl.getEndCurrency().getCode();
      String startCode = startCurrency.getCode();
      String endCode = endCurrency.getCode();
      LocalDate fromDate = ccl.getFromDate();
      LocalDate toDate = ccl.getToDate();

      if (cclStartCode.equals(startCode) && cclEndCode.equals(endCode)) {
        if ((fromDate.isBefore(localDate) || fromDate.equals(localDate))
            && (toDate == null || toDate.isAfter(localDate) || toDate.isEqual(localDate))) {
          return ccl;
        }
      }
    }

    return null;
  }

  /**
   * Convert the amount in start currency into the end currency according to the date to convert
   *
   * @param startCurrency
   * @param endCurrency
   * @param amount
   * @param date
   * @return
   * @throws AxelorException
   */
  public BigDecimal getAmountCurrencyConvertedAtDate(
      Currency startCurrency, Currency endCurrency, BigDecimal amount, LocalDate date)
      throws AxelorException {

    // If the start currency is different from end currency
    // So we convert the amount
    if (startCurrency != null && endCurrency != null && !startCurrency.equals(endCurrency)) {

      return this.getAmountCurrencyConvertedUsingExchangeRate(
          amount, this.getCurrencyConversionRate(startCurrency, endCurrency, date));
    }

    return amount;
  }

  /**
   * Convert the amount in start currency into the end currency according to the exchange rate
   *
   * @param startCurrency
   * @param endCurrency
   * @param amount
   * @param exchangeRate
   * @return
   * @throws AxelorException
   */
  public BigDecimal getAmountCurrencyConvertedUsingExchangeRate(
      BigDecimal amount, BigDecimal exchangeRate) throws AxelorException {

    // If the start currency is different from end currency
    // So we convert the amount
    if (exchangeRate.compareTo(BigDecimal.ONE) != 0) {

      return amount.multiply(exchangeRate).setScale(2, RoundingMode.HALF_EVEN);
    }

    return amount;
  }

  public LocalDate getDateToConvert(LocalDate date) {

    if (date == null) {
      date = this.today;
    }

    return date;
  }
}
