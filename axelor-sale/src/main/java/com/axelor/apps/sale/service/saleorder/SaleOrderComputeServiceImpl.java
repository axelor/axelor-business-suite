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

import com.axelor.apps.base.db.AppSale;
import com.axelor.apps.sale.db.AdvancePayment;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.SaleOrderLineTax;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleOrderComputeServiceImpl implements SaleOrderComputeService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected SaleOrderLineService saleOrderLineService;
  protected SaleOrderLineTaxService saleOrderLineTaxService;

  @Inject
  public SaleOrderComputeServiceImpl(
      SaleOrderLineService saleOrderLineService, SaleOrderLineTaxService saleOrderLineTaxService) {

    this.saleOrderLineService = saleOrderLineService;
    this.saleOrderLineTaxService = saleOrderLineTaxService;
  }

  @Override
  public SaleOrder _computeSaleOrderLineList(SaleOrder saleOrder) throws AxelorException {

    if (saleOrder.getSaleOrderLineList() != null) {
      for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
        saleOrderLine.setCompanyExTaxTotal(
            saleOrderLineService.getAmountInCompanyCurrency(
                saleOrderLine.getExTaxTotal(), saleOrder));
      }
    }

    return saleOrder;
  }

  @Override
  public SaleOrder computeSaleOrder(SaleOrder saleOrder) throws AxelorException {

    AppSale appSale = Beans.get(AppSaleService.class).getAppSale();
    if (appSale != null && appSale.getActive() && appSale.getProductPackMgt()) {
      this._addPackLines(saleOrder);
    }

    this.initSaleOrderLineTaxList(saleOrder);

    this._computeSaleOrderLineList(saleOrder);

    this._populateSaleOrder(saleOrder);

    this._computeSaleOrder(saleOrder);

    return saleOrder;
  }

  /**
   * Peupler un devis.
   *
   * <p>Cette fonction permet de déterminer les tva d'un devis.
   *
   * @param saleOrder
   * @throws AxelorException
   */
  @Override
  public void _populateSaleOrder(SaleOrder saleOrder) throws AxelorException {
    if (saleOrder.getSaleOrderLineList() == null) {
      saleOrder.setSaleOrderLineList(new ArrayList<>());
    }

    if (saleOrder.getSaleOrderLineTaxList() == null) {
      saleOrder.setSaleOrderLineTaxList(new ArrayList<>());
    }

    logger.debug(
        "Peupler un devis => lignes de devis: {} ",
        new Object[] {saleOrder.getSaleOrderLineList().size()});

    // create Tva lines
    if (saleOrder.getClientPartner() != null) {
      saleOrder
          .getSaleOrderLineTaxList()
          .addAll(
              saleOrderLineTaxService.createsSaleOrderLineTax(
                  saleOrder, saleOrder.getSaleOrderLineList()));
    }
  }

  /**
   * Compute the sale order total amounts
   *
   * @param saleOrder
   * @throws AxelorException
   */
  @Override
  public void _computeSaleOrder(SaleOrder saleOrder) throws AxelorException {

    saleOrder.setExTaxTotal(BigDecimal.ZERO);
    saleOrder.setCompanyExTaxTotal(BigDecimal.ZERO);
    saleOrder.setTaxTotal(BigDecimal.ZERO);
    saleOrder.setInTaxTotal(BigDecimal.ZERO);

    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
      saleOrder.setExTaxTotal(saleOrder.getExTaxTotal().add(saleOrderLine.getExTaxTotal()));

      // In the company accounting currency
      saleOrder.setCompanyExTaxTotal(
          saleOrder.getCompanyExTaxTotal().add(saleOrderLine.getCompanyExTaxTotal()));
    }

    for (SaleOrderLineTax saleOrderLineVat : saleOrder.getSaleOrderLineTaxList()) {

      // In the sale order currency
      saleOrder.setTaxTotal(saleOrder.getTaxTotal().add(saleOrderLineVat.getTaxTotal()));
    }

    saleOrder.setInTaxTotal(saleOrder.getExTaxTotal().add(saleOrder.getTaxTotal()));
    saleOrder.setAdvanceTotal(computeTotalAdvancePayment(saleOrder));
    logger.debug(
        "Montant de la facture: HTT = {},  HT = {}, Taxe = {}, TTC = {}",
        new Object[] {
          saleOrder.getExTaxTotal(), saleOrder.getTaxTotal(), saleOrder.getInTaxTotal()
        });
  }

  protected BigDecimal computeTotalAdvancePayment(SaleOrder saleOrder) {
    List<AdvancePayment> advancePaymentList = saleOrder.getAdvancePaymentList();
    BigDecimal total = BigDecimal.ZERO;
    if (advancePaymentList == null || advancePaymentList.isEmpty()) {
      return total;
    }
    for (AdvancePayment advancePayment : advancePaymentList) {
      total = total.add(advancePayment.getAmount());
    }
    return total;
  }

  protected void _addPackLines(SaleOrder saleOrder) {

    if (saleOrder.getSaleOrderLineList() == null) {
      return;
    }

    List<SaleOrderLine> saleOrderLines = saleOrder.getSaleOrderLineList();

    List<SaleOrderLine> lines = new ArrayList<SaleOrderLine>();
    lines.addAll(saleOrderLines);
    for (SaleOrderLine line : lines) {
      if (line.getSubLineList() == null || line.getSubLineList().isEmpty()) {
        continue;
      }
      for (SaleOrderLine subLine : line.getSubLineList()) {
        if (subLine.getSaleOrder() == null) {
          saleOrderLines.add(subLine);
        }
      }
    }
  }

  /**
   * Permet de réinitialiser la liste des lignes de TVA
   *
   * @param saleOrder Un devis
   */
  @Override
  public void initSaleOrderLineTaxList(SaleOrder saleOrder) {

    if (saleOrder.getSaleOrderLineTaxList() == null) {
      saleOrder.setSaleOrderLineTaxList(new ArrayList<SaleOrderLineTax>());
    } else {
      saleOrder.getSaleOrderLineTaxList().clear();
    }
  }

  @Override
  public BigDecimal getTotalSaleOrderPrice(SaleOrder saleOrder) {
    BigDecimal price = BigDecimal.ZERO;
    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
      price = price.add(saleOrderLine.getQty().multiply(saleOrderLine.getPriceDiscounted()));
    }
    return price;
  }

  @Override
  public List<SaleOrderLine> removeSubLines(List<SaleOrderLine> soLines) {

    if (soLines == null) {
      return soLines;
    }

    List<SaleOrderLine> subLines = new ArrayList<SaleOrderLine>();
    for (SaleOrderLine packLine : soLines) {
      if (packLine.getTypeSelect() == 2 && packLine.getSubLineList() != null) {
        packLine.getSubLineList().removeIf(it -> it.getId() != null && !soLines.contains(it));
        packLine.setTotalPack(
            packLine
                .getSubLineList()
                .stream()
                .map(it -> it.getExTaxTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        subLines.addAll(packLine.getSubLineList());
      }
    }
    Iterator<SaleOrderLine> lines = soLines.iterator();

    while (lines.hasNext()) {
      SaleOrderLine subLine = lines.next();
      if (subLine.getId() != null
          && subLine.getParentLine() != null
          && !subLines.contains(subLine)) {
        lines.remove();
      }
    }

    return soLines;
  }
}
