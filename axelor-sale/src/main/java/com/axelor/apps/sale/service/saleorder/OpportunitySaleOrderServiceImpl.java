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
package com.axelor.apps.sale.service.saleorder;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.crm.db.Opportunity;
import com.axelor.apps.crm.db.repo.OpportunityRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.List;

public class OpportunitySaleOrderServiceImpl implements OpportunitySaleOrderService {

  @Inject protected SaleOrderCreateService saleOrderCreateService;

  @Inject protected SaleOrderRepository saleOrderRepo;

  @Inject protected AppBaseService appBaseService;

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public SaleOrder createSaleOrderFromOpportunity(Opportunity opportunity) throws AxelorException {
    Currency currency = null;
    Company company = opportunity.getCompany();
    if (opportunity.getCurrency() != null) {
      currency = opportunity.getCurrency();
    } else if (opportunity.getPartner() != null && opportunity.getPartner().getCurrency() != null) {
      currency = opportunity.getPartner().getCurrency();
    } else if (company != null) {
      currency = company.getCurrency();
    }

    SaleOrder saleOrder = createSaleOrder(opportunity, currency);
    if (company != null && company.getSaleConfig() != null) {
      saleOrder.setDuration(company.getSaleConfig().getDefaultValidityDuration());
    }

    opportunity.addSaleOrderListItem(saleOrder);

    if (opportunity.getSalesStageSelect() < OpportunityRepository.SALES_STAGE_PROPOSITION) {
      opportunity.setSalesStageSelect(OpportunityRepository.SALES_STAGE_PROPOSITION);
    }

    saleOrder.setTradingName(opportunity.getTradingName());

    saleOrderRepo.save(saleOrder);

    return saleOrder;
  }

  protected SaleOrder createSaleOrder(Opportunity opportunity, Currency currency)
      throws AxelorException {
    return saleOrderCreateService.createSaleOrder(
        opportunity.getUser(),
        opportunity.getCompany(),
        null,
        currency,
        null,
        opportunity.getName(),
        null,
        appBaseService.getTodayDate(),
        Beans.get(PartnerPriceListService.class)
            .getDefaultPriceList(opportunity.getPartner(), PriceListRepository.TYPE_SALE),
        opportunity.getPartner(),
        opportunity.getTeam(),
        opportunity.getTradingName());
  }

  @Transactional
  public void linkSaleOrderToOpportunity(List<Long> saleOrderIdList, Opportunity opportunity) {
    for (Long id : saleOrderIdList) {
      SaleOrder saleOrder = saleOrderRepo.find(id);
      saleOrder.setOpportunity(opportunity);
      saleOrderRepo.save(saleOrder);
    }
  }
}
