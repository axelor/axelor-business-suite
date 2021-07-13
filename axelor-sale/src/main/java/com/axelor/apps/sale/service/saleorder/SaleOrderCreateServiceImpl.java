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
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.TradingNameService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.team.db.Team;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleOrderCreateServiceImpl implements SaleOrderCreateService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected PartnerService partnerService;
  protected SaleOrderRepository saleOrderRepo;
  protected AppSaleService appSaleService;
  protected SaleOrderService saleOrderService;
  protected SaleOrderComputeService saleOrderComputeService;

  @Inject
  public SaleOrderCreateServiceImpl(
      PartnerService partnerService,
      SaleOrderRepository saleOrderRepo,
      AppSaleService appSaleService,
      SaleOrderService saleOrderService,
      SaleOrderComputeService saleOrderComputeService) {

    this.partnerService = partnerService;
    this.saleOrderRepo = saleOrderRepo;
    this.appSaleService = appSaleService;
    this.saleOrderService = saleOrderService;
    this.saleOrderComputeService = saleOrderComputeService;
  }

  @Override
  public SaleOrder createSaleOrder(Company company) throws AxelorException {
    SaleOrder saleOrder = new SaleOrder();
    saleOrder.setCreationDate(appSaleService.getTodayDate(company));
    if (company != null) {
      saleOrder.setCompany(company);
      saleOrder.setCurrency(company.getCurrency());
    }
    saleOrder.setSalespersonUser(AuthUtils.getUser());
    saleOrder.setTeam(saleOrder.getSalespersonUser().getActiveTeam());
    saleOrder.setStatusSelect(SaleOrderRepository.STATUS_DRAFT_QUOTATION);
    saleOrderService.computeEndOfValidityDate(saleOrder);
    return saleOrder;
  }

  @Override
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
      throws AxelorException {

    logger.debug(
        "Création d'un devis client : Société = {},  Reference externe = {}, Client = {}",
        company,
        externalReference,
        clientPartner.getFullName());

    SaleOrder saleOrder = new SaleOrder();
    saleOrder.setClientPartner(clientPartner);
    saleOrder.setCreationDate(appSaleService.getTodayDate(company));
    saleOrder.setContactPartner(contactPartner);
    saleOrder.setCurrency(currency);
    saleOrder.setExternalReference(externalReference);
    saleOrder.setDeliveryDate(deliveryDate);

    saleOrder.setPrintingSettings(
        Beans.get(TradingNameService.class).getDefaultPrintingSettings(tradingName, company));

    if (salespersonUser == null) {
      salespersonUser = AuthUtils.getUser();
    }
    saleOrder.setSalespersonUser(salespersonUser);

    if (team == null) {
      team = salespersonUser.getActiveTeam();
    }
    saleOrder.setTeam(team);

    if (company == null) {
      company = salespersonUser.getActiveCompany();
    }
    saleOrder.setCompany(company);

    saleOrder.setMainInvoicingAddress(partnerService.getInvoicingAddress(clientPartner));
    saleOrder.setDeliveryAddress(partnerService.getDeliveryAddress(clientPartner));

    saleOrderService.computeAddressStr(saleOrder);

    if (priceList == null) {
      priceList =
          Beans.get(PartnerPriceListService.class)
              .getDefaultPriceList(clientPartner, PriceListRepository.TYPE_SALE);
    }
    saleOrder.setPriceList(priceList);

    saleOrder.setSaleOrderLineList(new ArrayList<>());

    saleOrder.setStatusSelect(SaleOrderRepository.STATUS_DRAFT_QUOTATION);

    saleOrderService.computeEndOfValidityDate(saleOrder);

    return saleOrder;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public SaleOrder mergeSaleOrders(
      List<SaleOrder> saleOrderList,
      Currency currency,
      Partner clientPartner,
      Company company,
      Partner contactPartner,
      PriceList priceList,
      Team team)
      throws AxelorException {

    String numSeq = "";
    String externalRef = "";
    for (SaleOrder saleOrderLocal : saleOrderList) {
      if (!numSeq.isEmpty()) {
        numSeq += "-";
      }
      numSeq += saleOrderLocal.getSaleOrderSeq();

      if (!externalRef.isEmpty()) {
        externalRef += "|";
      }
      if (saleOrderLocal.getExternalReference() != null) {
        externalRef += saleOrderLocal.getExternalReference();
      }
    }

    SaleOrder saleOrderMerged =
        this.createSaleOrder(
            AuthUtils.getUser(),
            company,
            contactPartner,
            currency,
            null,
            numSeq,
            externalRef,
            priceList,
            clientPartner,
            team);

    this.attachToNewSaleOrder(saleOrderList, saleOrderMerged);

    saleOrderComputeService.computeSaleOrder(saleOrderMerged);

    saleOrderRepo.save(saleOrderMerged);

    this.removeOldSaleOrders(saleOrderList);

    return saleOrderMerged;
  }

  // Attachment of all sale order lines to new sale order
  protected void attachToNewSaleOrder(List<SaleOrder> saleOrderList, SaleOrder saleOrderMerged) {
    for (SaleOrder saleOrder : saleOrderList) {
      int countLine = 1;
      for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
        saleOrderLine.setSequence(countLine * 10);
        saleOrderMerged.addSaleOrderLineListItem(saleOrderLine);
        countLine++;
      }
    }
  }

  // Remove old sale orders after merge
  protected void removeOldSaleOrders(List<SaleOrder> saleOrderList) {
    for (SaleOrder saleOrder : saleOrderList) {
      saleOrderRepo.remove(saleOrder);
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public SaleOrder createSaleOrder(
      SaleOrder context, Currency wizardCurrency, PriceList wizardPriceList)
      throws AxelorException, ClassNotFoundException {
    SaleOrder copy = saleOrderRepo.copy(context, true);
    copy.setCreationDate(appSaleService.getTodayDate(context.getCompany()));
    copy.setCurrency(wizardCurrency);
    copy.setPriceList(wizardPriceList);

    saleOrderService.computeEndOfValidityDate(copy);

    this.updateSaleOrderLineList(copy);

    saleOrderComputeService.computeSaleOrder(copy);

    copy.setTemplate(false);
    copy.setTemplateUser(null);

    return copy;
  }

  public void updateSaleOrderLineList(SaleOrder saleOrder)
      throws AxelorException, ClassNotFoundException {
    List<SaleOrderLine> saleOrderLineList = saleOrder.getSaleOrderLineList();
    if (saleOrderLineList != null) {
      for (SaleOrderLine saleOrderLine : saleOrderLineList) {
        Beans.get(SaleOrderLineService.class).fillPrice(saleOrderLine, saleOrder);
        Beans.get(SaleOrderLineService.class).computeValues(saleOrder, saleOrderLine);
      }
    }
  }

  @Override
  @Transactional
  public SaleOrder createTemplate(SaleOrder context) {
    SaleOrder copy = saleOrderRepo.copy(context, true);
    copy.setTemplate(true);
    copy.setTemplateUser(AuthUtils.getUser());
    return copy;
  }
}
