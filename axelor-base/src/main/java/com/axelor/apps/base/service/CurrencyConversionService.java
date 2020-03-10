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

import com.axelor.apps.base.db.AppBase;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.CurrencyConversionLine;
import com.axelor.apps.base.db.repo.CurrencyConversionLineRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.http.HTTPClient;
import wslite.http.HTTPMethod;
import wslite.http.HTTPRequest;
import wslite.http.HTTPResponse;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;

@Singleton
public class CurrencyConversionService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject protected AppBaseService appBaseService;

  @Inject protected CurrencyConversionLineRepository cclRepo;

  public void updateCurrencyConverion() throws AxelorException {
    AppBase appBase = appBaseService.getAppBase();
    LocalDate today = appBaseService.getTodayDate();

    Map<Long, Set<Long>> currencyMap = new HashMap<Long, Set<Long>>();

    for (CurrencyConversionLine ccl : appBase.getCurrencyConversionLineList()) {
      if (currencyMap.containsKey(ccl.getEndCurrency().getId())) {
        currencyMap.get(ccl.getEndCurrency().getId()).add(ccl.getStartCurrency().getId());

      } else {
        Set<Long> startCurrencyIds = new HashSet<>();
        startCurrencyIds.add(ccl.getStartCurrency().getId());
        currencyMap.put(ccl.getEndCurrency().getId(), startCurrencyIds);
      }
    }

    for (Long key : currencyMap.keySet()) {
      List<CurrencyConversionLine> cclList =
          cclRepo
              .all()
              .filter(
                  "startCurrency.id IN (?1) AND endCurrency.id = ?2 AND fromDate <= ?3 AND toDate is null",
                  currencyMap.get(key),
                  key,
                  today)
              .fetch();

      for (CurrencyConversionLine ccl : cclList) {
        LOG.trace("Currency Conversion Line without toDate : {}", ccl);
        BigDecimal currentRate = BigDecimal.ZERO;
        try {
          currentRate = this.convert(ccl.getStartCurrency(), ccl.getEndCurrency());
        } catch (Exception e) {
          TraceBackService.trace(e);
        }
        if (currentRate.compareTo(new BigDecimal(-1)) == 0) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.CURRENCY_6));
        }

        ccl = cclRepo.find(ccl.getId());
        BigDecimal previousRate = ccl.getExchangeRate();
        if (currentRate.compareTo(previousRate) != 0) {
          ccl.setToDate(today.minusDays(1));
          this.saveCurrencyConversionLine(ccl);
          String variations = this.getVariations(currentRate, previousRate);
          this.createCurrencyConversionLine(
              ccl.getStartCurrency(),
              ccl.getEndCurrency(),
              today,
              currentRate,
              appBase,
              variations);
        }
      }
    }
  }

  public BigDecimal convert(Currency currencyFrom, Currency currencyTo)
      throws MalformedURLException, JSONException, AxelorException {
    BigDecimal rate = new BigDecimal(-1);

    LOG.trace("Currerncy conversion From: {} To: {}", new Object[] {currencyFrom, currencyTo});
    String wsUrl = appBaseService.getAppBase().getCurrencyWsURL();
    if (wsUrl == null) {
      LOG.trace("Currency WS URL not configured");
      return rate;
    }

    if (currencyFrom != null && currencyTo != null) {
      Float rt =
          this.validateAndGetRate(
              1, wsUrl, currencyFrom, currencyTo, appBaseService.getTodayDate());
      rate = BigDecimal.valueOf(rt).setScale(8, RoundingMode.HALF_EVEN);
      //	        Float rt =
      // Float.parseFloat(json.getJSONObject("rates").get(currencyTo.getCode()).toString());
      //	        rate = BigDecimal.valueOf(rt).setScale(4,RoundingMode.HALF_EVEN);

    } else LOG.trace("Currency from and to must be filled to get rate");
    LOG.trace("Currerncy conversion rate: {}", new Object[] {rate});
    return rate;
  }

  protected Float validateAndGetRate(
      int dayCount, String wsUrl, Currency currencyFrom, Currency currencyTo, LocalDate date)
      throws MalformedURLException, JSONException, AxelorException {

    HTTPResponse response = null;

    if (dayCount < 8) {
      HTTPClient httpclient = new HTTPClient();
      HTTPRequest request = new HTTPRequest();
      Map<String, Object> headers = new HashMap<>();
      headers.put("Accept", "application/json");
      request.setHeaders(headers);
      URL url =
          new URL(String.format(wsUrl, currencyFrom.getCode(), currencyTo.getCode(), date, date));
      // URL url = new URL(String.format(wsUrl,currencyFrom.getCode()));
      LOG.trace("Currency conversion webservice URL: {}", new Object[] {url.toString()});
      request.setUrl(url);
      request.setMethod(HTTPMethod.GET);
      try {
        response = httpclient.execute(request);
      } catch (Exception e) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CURRENCY_6));
      }
      // JSONObject json = new JSONObject(response.getContentAsString());
      LOG.trace(
          "Webservice response code: {}, response message: {}",
          response.getStatusCode(),
          response.getStatusMessage());
      if (response.getStatusCode() != 200) return -1f;

    } else {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          String.format(
              I18n.get(IExceptionMessage.CURRENCY_7),
              date.plus(Period.ofDays(1)),
              appBaseService.getTodayDate()));
    }

    if (response.getContentAsString().isEmpty()) {
      return this.validateAndGetRate(
          (dayCount + 1), wsUrl, currencyFrom, currencyTo, date.minus(Period.ofDays(1)));
    } else {
      return this.getRateFromJson(currencyFrom, currencyTo, response);
    }
  }

  protected Float getRateFromJson(Currency currencyFrom, Currency currencyTo, HTTPResponse response)
      throws JSONException {
    int compareCode = currencyFrom.getCode().compareTo(currencyTo.getCode());
    Float rt = null;
    String[] currencyRateArr = new String[2];

    JSONObject jsonResult = new JSONObject(response.getContentAsString());
    JSONObject dataSets = new JSONObject(jsonResult.getJSONArray("dataSets").get(0).toString());
    JSONObject series = new JSONObject(dataSets.getJSONObject("series").toString());
    JSONObject seriesOf = null;
    JSONObject observations = null;
    JSONArray rateValue = null;

    if (series.size() > 1) {
      for (int i = 0; i < series.size(); i++) {
        seriesOf = new JSONObject(series.getJSONObject("0:" + i + ":0:0:0").toString());
        observations = new JSONObject(seriesOf.getJSONObject("observations").toString());
        rateValue = new JSONArray(observations.get(observations.length() - 1).toString());
        currencyRateArr[i] = rateValue.get(0).toString();
      }
      if (compareCode > 0) {
        rt = Float.parseFloat(currencyRateArr[0]) / Float.parseFloat(currencyRateArr[1]);
      } else {
        rt = Float.parseFloat(currencyRateArr[1]) / Float.parseFloat(currencyRateArr[0]);
      }

    } else {
      seriesOf = new JSONObject(series.getJSONObject("0:0:0:0:0").toString());
      observations = new JSONObject(seriesOf.getJSONObject("observations").toString());
      rateValue = new JSONArray(observations.get(observations.length() - 1).toString());

      if (currencyTo.getCode().equals("EUR")) {
        rt = 1.0f / Float.parseFloat(rateValue.get(0).toString());
      } else {
        rt = Float.parseFloat(rateValue.get(0).toString());
      }
    }
    return rt;
  }

  public String getVariations(BigDecimal currentRate, BigDecimal previousRate) {
    String variations = "0";
    LOG.trace(
        "Currency rate variation calculation for CurrentRate: {} PreviousRate: {}",
        new Object[] {currentRate, previousRate});

    if (currentRate != null
        && previousRate != null
        && previousRate.compareTo(BigDecimal.ZERO) != 0) {
      BigDecimal diffRate = currentRate.subtract(previousRate);
      BigDecimal variation =
          diffRate.multiply(new BigDecimal(100)).divide(previousRate, RoundingMode.HALF_EVEN);
      variation =
          variation.setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_EVEN);
      variations = variation.toString() + "%";
    }

    LOG.trace("Currency rate variation result: {}", new Object[] {variations});
    return variations;
  }

  @Transactional
  public void createCurrencyConversionLine(
      Currency currencyFrom,
      Currency currencyTo,
      LocalDate fromDate,
      BigDecimal rate,
      AppBase appBase,
      String variations) {
    LOG.trace(
        "Create new currency conversion line CurrencyFrom: {}, CurrencyTo: {},FromDate: {},ConversionRate: {}, AppBase: {}, Variations: {}",
        new Object[] {currencyFrom, currencyTo, fromDate, rate, appBase, variations});

    CurrencyConversionLine ccl = new CurrencyConversionLine();
    ccl.setStartCurrency(currencyFrom);
    ccl.setEndCurrency(currencyTo);
    ccl.setFromDate(fromDate);
    ccl.setExchangeRate(rate);
    ccl.setAppBase(appBase);
    ccl.setVariations(variations);
    cclRepo.save(ccl);
  }

  @Transactional
  public void saveCurrencyConversionLine(CurrencyConversionLine ccl) {
    cclRepo.save(ccl);
  }

  //	public BigDecimal getRate(Currency currencyFrom, Currency currencyTo, LocalDate rateDate){
  //
  //		LOG.debug("Get Last rate for CurrencyFrom: {} CurrencyTo: {} RateDate: {}",new
  // Object[]{currencyFrom,currencyTo,rateDate});
  //
  //		BigDecimal rate = null;
  //
  //		if(currencyFrom != null && currencyTo != null && rateDate != null){
  //			currencyFrom = currencyRepo.find(currencyFrom.getId());
  //			currencyTo = currencyRepo.find(currencyTo.getId());
  //			CurrencyConversionLine ccl = cclRepo.all().filter("startCurrency = ?1 AND endCurrency = ?2
  // AND fromDate <= ?3 AND (toDate >= ?3 OR toDate =
  // null)",currencyFrom,currencyTo,rateDate).fetchOne();
  //			if(ccl != null)
  //				rate =  ccl.getExchangeRate();
  //		}
  //
  //		LOG.debug("Current Rate: {}",new Object[]{rate});
  //
  //		return rate;
  //	}

}
