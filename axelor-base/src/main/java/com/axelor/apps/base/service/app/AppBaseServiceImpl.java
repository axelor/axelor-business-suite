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
package com.axelor.apps.base.service.app;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.AppBase;
import com.axelor.apps.base.db.CurrencyConversionLine;
import com.axelor.apps.base.db.Language;
import com.axelor.apps.base.db.Unit;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.Query;
import com.axelor.event.Observes;
import com.axelor.events.PostRequest;
import com.axelor.events.RequestEvent;
import com.axelor.events.qualifiers.EntityType;
import com.axelor.exception.AxelorException;
import com.google.common.base.Strings;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class AppBaseServiceImpl extends AppServiceImpl implements AppBaseService {

  protected static String DEFAULT_LOCALE = "en";

  @Override
  public AppBase getAppBase() {
    return Query.of(AppBase.class).cacheable().fetchOne();
  }

  /**
   * Get the today date time Get the value defined in User if not null, Else get the valued defined
   * in Base app Else get the server current date time
   */
  @Override
  public ZonedDateTime getTodayDateTime() {

    ZonedDateTime todayDateTime = ZonedDateTime.now();

    String applicationMode = AppSettings.get().get("application.mode", "prod");

    if ("dev".equals(applicationMode)) {
      User user = AuthUtils.getUser();
      if (user != null && user.getToday() != null) {
        todayDateTime = user.getToday();
      } else {
        AppBase appBase = getAppBase();
        if (appBase != null && appBase.getToday() != null) {
          return appBase.getToday();
        }
      }
    }

    return todayDateTime;
  }

  /**
   * Get the today date Get the value defined in User if not null, Else get the valued defined in
   * Base app Else get the server current date
   */
  @Override
  public LocalDate getTodayDate() {

    return getTodayDateTime().toLocalDate();
  }

  @Override
  public Unit getUnit() {

    AppBase appBase = getAppBase();

    if (appBase != null) {
      return appBase.getDefaultProjectUnit();
    }

    return null;
  }

  @Override
  public int getNbDecimalDigitForUnitPrice() {

    AppBase appBase = getAppBase();

    if (appBase != null) {
      return appBase.getNbDecimalDigitForUnitPrice();
    }

    return DEFAULT_NB_DECIMAL_DIGITS;
  }

  @Override
  public String getDefaultPartnerLanguageCode() {

    AppBase appBase = getAppBase();

    if (appBase != null) {
      Language language = appBase.getDefaultPartnerLanguage();
      if (language != null && !Strings.isNullOrEmpty(language.getCode())) {
        return language.getCode();
      }
    }
    return DEFAULT_LOCALE;
  }

  // Conversion de devise

  /**
   * Obtenir la tva à 0%
   *
   * @return
   */
  @Override
  public List<CurrencyConversionLine> getCurrencyConfigurationLineList() {
    AppBase appBase = getAppBase();
    if (appBase != null) {
      return appBase.getCurrencyConversionLineList();
    } else {
      return null;
    }
  }

  @Override
  public BigDecimal getDurationHours(BigDecimal duration) {

    if (duration == null) {
      return null;
    }

    AppBase appBase = this.getAppBase();

    if (appBase != null) {
      String timePref = appBase.getTimeLoggingPreferenceSelect();

      if (timePref.equals("days")) {
        duration = duration.multiply(appBase.getDailyWorkHours());
      } else if (timePref.equals("minutes")) {
        duration = duration.divide(new BigDecimal(60), 2, RoundingMode.HALF_EVEN);
      }
    }

    return duration;
  }

  @Override
  public BigDecimal getGeneralDuration(BigDecimal duration) {

    if (duration == null) {
      return null;
    }

    AppBase appBase = getAppBase();

    if (appBase != null) {
      String timePref = appBase.getTimeLoggingPreferenceSelect();

      BigDecimal dailyWorkHrs = appBase.getDailyWorkHours();

      if (timePref.equals("days")
          && dailyWorkHrs != null
          && dailyWorkHrs.compareTo(BigDecimal.ZERO) != 0) {
        duration = duration.divide(dailyWorkHrs, 2, RoundingMode.HALF_EVEN);
      } else if (timePref.equals("minutes")) {
        duration = duration.multiply(new BigDecimal(60));
      }
    }

    return duration;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void setManageMultiBanks(boolean manageMultiBanks) {
    getAppBase().setManageMultiBanks(manageMultiBanks);
  }

  void onAppBasePostSave(
      @Observes @Named(RequestEvent.SAVE) @EntityType(AppBase.class) PostRequest event) {
    fireFeatureChanged(event, getAppBase());
  }
}
