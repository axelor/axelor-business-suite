/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.stock.service;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.TrackingNumber;
import com.axelor.exception.AxelorException;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface StockLocationLineService {

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void updateLocation(
      StockLocation stockLocation,
      Product product,
      Unit stockMoveLineUnit,
      BigDecimal qty,
      boolean current,
      boolean future,
      boolean isIncrement,
      LocalDate lastFutureStockMoveDate,
      TrackingNumber trackingNumber,
      BigDecimal requestedReservedQty)
      throws AxelorException;

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void updateLocation(
      StockLocation stockLocation,
      Product product,
      Unit stockMoveLineUnit,
      BigDecimal qty,
      boolean current,
      boolean future,
      boolean isIncrement,
      LocalDate lastFutureStockMoveDate,
      BigDecimal requestedReservedQty)
      throws AxelorException;

  public void minStockRules(
      Product product,
      BigDecimal qty,
      StockLocationLine stockLocationLine,
      boolean current,
      boolean future)
      throws AxelorException;

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void updateDetailLocation(
      StockLocation stockLocation,
      Product product,
      Unit stockMoveLineUnit,
      BigDecimal qty,
      boolean current,
      boolean future,
      boolean isIncrement,
      LocalDate lastFutureStockMoveDate,
      TrackingNumber trackingNumber,
      BigDecimal requestedReservedQty)
      throws AxelorException;

  public void checkStockMin(StockLocationLine stockLocationLine, boolean isDetailLocationLine)
      throws AxelorException;

  /**
   * Check if the stock location has more than qty units of the product
   *
   * @param stockLocation
   * @param product
   * @param qty
   * @throws AxelorException if there is not enough qty in stock
   */
  public void checkIfEnoughStock(StockLocation stockLocation, Product product, BigDecimal qty)
      throws AxelorException;

  public StockLocationLine updateLocation(
      StockLocationLine stockLocationLine,
      Unit stockMoveLineUnit,
      Product product,
      BigDecimal qty,
      boolean current,
      boolean future,
      boolean isIncrement,
      LocalDate lastFutureStockMoveDate,
      BigDecimal requestedReservedQty)
      throws AxelorException;

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void updateStockLocationFromProduct(StockLocationLine stockLocationLine, Product product)
      throws AxelorException;

  public StockLocationLine updateLocationFromProduct(
      StockLocationLine stockLocationLine, Product product) throws AxelorException;

  /**
   * Getting the stock location line : We check if the location has a detailed line for a given
   * product. If no detailed location line is found, we create it.
   *
   * @param stockLocation A location
   * @param product A product
   * @return The found or created location line
   */
  public StockLocationLine getOrCreateStockLocationLine(
      StockLocation stockLocation, Product product);

  /**
   * Getting the detailed stock location line : We check if the location has a detailed line for a
   * given product, product variant and tracking number. If no detailed location line is found, we
   * create it.
   *
   * @param detailLocation A location
   * @param product A product
   * @param trackingNumber A tracking number
   * @return The found or created detailed location line
   */
  public StockLocationLine getOrCreateDetailLocationLine(
      StockLocation detailLocation, Product product, TrackingNumber trackingNumber);

  /**
   * Allow to get the location line of a given product in a given location.
   *
   * @param stockLocation A location
   * @param product A product
   * @return The stock location line if found, else null
   */
  public StockLocationLine getStockLocationLine(StockLocation stockLocation, Product product);

  /**
   * Allow to get the location lines of a given product.
   *
   * @param product
   * @return
   */
  public List<StockLocationLine> getStockLocationLines(Product product);

  /**
   * Allow to get the detailed location line of a given product, product variant and tracking number
   * in a given location.
   *
   * @param stockLocation A location
   * @param product A product
   * @param trackingNumber A tracking number
   * @return The stock location line if found, else null
   */
  public StockLocationLine getDetailLocationLine(
      StockLocation stockLocation, Product product, TrackingNumber trackingNumber);

  /**
   * Allow the creation of a location line of a given product in a given location.
   *
   * @param stockLocation A location
   * @param product A product
   * @return The created stock location line
   */
  public StockLocationLine createLocationLine(StockLocation stockLocation, Product product);

  /**
   * Allow the creation of a detailed location line of a given product, product variant, and
   * tracking number in a given location.
   *
   * @param stockLocation A location
   * @param product A product
   * @param trackingNumber A tracking number
   * @return The created detailed stock location line
   */
  public StockLocationLine createDetailLocationLine(
      StockLocation stockLocation, Product product, TrackingNumber trackingNumber);

  /**
   * Allow to get the available qty of product in a given location.
   *
   * @param stockLocation
   * @param product
   * @return
   */
  public BigDecimal getAvailableQty(StockLocation stockLocation, Product product);

  public Map<String, Object> updateQty(StockLocationLine stockLocationLine, Unit endUnit)
      throws AxelorException;
}
