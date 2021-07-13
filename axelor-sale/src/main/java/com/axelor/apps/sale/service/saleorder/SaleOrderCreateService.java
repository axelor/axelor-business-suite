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
package com.axelor.apps.sale.service.saleorder;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.team.db.Team;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.util.List;

public interface SaleOrderCreateService {

  /**
   * Initialize a new sale order
   *
   * @param salespersonUser User recorded as salesperson on order, if <code>null</code>, will be set
   *     to current user.
   * @param company Company bound to the order, if <code>null</code>, will be bound to salesperson
   *     active company.
   * @param contactPartner Customer contact to assign to the user, might be <code>null</code>.
   * @param currency Order's currency, should not be <code>null</code>.
   * @param deliveryDate Expected delivery date for order (might be <code>null</code>).
   * @param internalReference Unused (…)
   * @param externalReference Client reference for order, if any
   * @param priceList Pricelist to use, if <code>null</code>, will default to partner's default
   *     price list.
   * @param clientPartner Customer bound to the order, should not be <code>null</code>
   * @param team Team managing the order, if <code>null</code>, will default to salesperson active
   *     team.
   * @return The created order
   * @throws AxelorException
   */
  default SaleOrder createSaleOrder(
      User salespersonUser,
      Company company,
      Partner contactPartner,
      Currency currency,
      LocalDate deliveryDate,
      String internalReference,
      String externalReference,
      PriceList priceList,
      Partner clientPartner,
      Team team)
      throws AxelorException {
    return createSaleOrder(
        salespersonUser,
        company,
        contactPartner,
        currency,
        deliveryDate,
        internalReference,
        externalReference,
        priceList,
        clientPartner,
        team,
        null);
  }

  /**
   * Initialize a new sale order
   *
   * @param salespersonUser User recorded as salesperson on order, if <code>null</code>, will be set
   *     to current user.
   * @param company Company bound to the order, if <code>null</code>, will be bound to salesperson
   *     active company.
   * @param contactPartner Customer contact to assign to the user, might be <code>null</code>.
   * @param currency Order's currency, should not be <code>null</code>.
   * @param deliveryDate Expected delivery date for order (might be <code>null</code>).
   * @param internalReference Unused (…)
   * @param externalReference Client reference for order, if any
   * @param priceList Pricelist to use, if <code>null</code>, will default to partner's default
   *     price list.
   * @param clientPartner Customer bound to the order, should not be <code>null</code>
   * @param team Team managing the order, if <code>null</code>, will default to salesperson active
   *     team.
   * @param tradingName
   * @return The created order
   * @throws AxelorException
   */
  public SaleOrder createSaleOrder(
      User salespersonUser,
      Company company,
      Partner contactPartner,
      Currency currency,
      LocalDate deliveryDate,
      String internalReference,
      String externalReference,
      PriceList priceList,
      Partner clientPartner,
      Team team,
      TradingName tradingName)
      throws AxelorException;

  public SaleOrder createSaleOrder(Company company) throws AxelorException;

  public SaleOrder mergeSaleOrders(
      List<SaleOrder> saleOrderList,
      Currency currency,
      Partner clientPartner,
      Company company,
      Partner contactPartner,
      PriceList priceList,
      Team team)
      throws AxelorException;

  @Transactional
  public SaleOrder createTemplate(SaleOrder context);

  @Transactional(rollbackOn = {Exception.class})
  public SaleOrder createSaleOrder(
      SaleOrder context, Currency wizardCurrency, PriceList wizardPriceList)
      throws AxelorException, ClassNotFoundException;

  public void updateSaleOrderLineList(SaleOrder saleOrder)
      throws AxelorException, ClassNotFoundException;
}
