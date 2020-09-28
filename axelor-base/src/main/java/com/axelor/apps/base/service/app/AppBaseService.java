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
package com.axelor.apps.base.service.app;

import com.axelor.apps.base.db.AppBase;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.CurrencyConversionLine;
import com.axelor.apps.base.db.Unit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public interface AppBaseService extends AppService {

  public static final int DEFAULT_NB_DECIMAL_DIGITS = 2;

  public static final int DEFAULT_TRACKING_MONTHS_PERSISTENCE = 1;

  public AppBase getAppBase();

  // Date du jour

  /**
   * Récupérer la date du jour avec l'heure en fonction de la timezone du serveur. Retourne la date
   * du jour paramétré dans l'utilisateur si existe, sinon récupère celle de l'administration
   * générale, sinon date du jour. private
   *
   * @return
   */
  public ZonedDateTime getTodayDateTime();

  /**
   * Récupérer la date du jour avec l'heure en fonction de la timezone renseignée dans la société
   * donnée. Retourne la date du jour paramétré dans l'utilisateur si existe, sinon récupère celle
   * de l'administration générale, sinon date du jour. private
   *
   * @return
   */
  public ZonedDateTime getTodayDateTime(Company company);

  /**
   * This method is deprecated. Please use the
   * com.axelor.apps.base.service.app.AppBaseService#getTodayDate(com.axelor.apps.base.db.Company)
   * method instead.
   *
   * @return
   */
  @Deprecated
  public LocalDate getTodayDate();

    /**
   * Récupérer la date du jour en fonction de la timezone renseignée dans la société donnée.
   * Retourne la date du jour paramétré dans l'utilisateur si existe, sinon récupère celle de
   * l'administration générale, sinon date du jour.
   *
   * @return
   */
  public LocalDate getTodayDate(Company company);

  public Unit getUnit();

  public int getNbDecimalDigitForUnitPrice();

  public int getNbDecimalDigitForQty();

  public int getGlobalTrackingLogPersistence();

  public String getDefaultPartnerLanguageCode();

  // Conversion de devise

  /**
   * Obtenir la tva à 0%
   *
   * @return
   */
  public List<CurrencyConversionLine> getCurrencyConfigurationLineList();

  public BigDecimal getDurationHours(BigDecimal duration);

  public BigDecimal getGeneralDuration(BigDecimal duration);

  /**
   * Set the manageMultiBanks boolean in the general object.
   *
   * @param manageMultiBanks the new value for the manageMultiBanks boolean
   */
  void setManageMultiBanks(boolean manageMultiBanks);
}
