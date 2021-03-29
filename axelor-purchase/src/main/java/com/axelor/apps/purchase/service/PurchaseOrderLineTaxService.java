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
package com.axelor.apps.purchase.service;

import com.axelor.apps.account.db.TaxEquiv;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.PurchaseOrderLineTax;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurchaseOrderLineTaxService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private PurchaseOrderToolService purchaseOrderToolService;

  /**
   * Créer les lignes de TVA de la commande. La création des lignes de TVA se basent sur les lignes
   * de commande.
   *
   * @param purchaseOrder La commande.
   * @param purchaseOrderLineList Les lignes de commandes.
   * @return La liste des lignes de TVA de la commande.
   */
  public List<PurchaseOrderLineTax> createsPurchaseOrderLineTax(
      PurchaseOrder purchaseOrder, List<PurchaseOrderLine> purchaseOrderLineList) {

    List<PurchaseOrderLineTax> purchaseOrderLineTaxList = new ArrayList<PurchaseOrderLineTax>();
    Map<TaxLine, PurchaseOrderLineTax> map = new HashMap<TaxLine, PurchaseOrderLineTax>();

    if (purchaseOrderLineList != null && !purchaseOrderLineList.isEmpty()) {

      LOG.debug("Création des lignes de tva pour les lignes de commande.");

      for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLineList) {

        TaxLine taxLine = purchaseOrderLine.getTaxLine();
        TaxEquiv taxEquiv = purchaseOrderLine.getTaxEquiv();
        TaxLine taxLineRC =
            (taxEquiv != null
                    && taxEquiv.getReverseCharge()
                    && taxEquiv.getReverseChargeTax() != null)
                ? taxEquiv.getReverseChargeTax().getActiveTaxLine()
                : null;

        if (taxLine != null) {
          LOG.debug("TVA {}", taxLine);

          if (map.containsKey(taxLine)) {

            PurchaseOrderLineTax purchaseOrderLineVat = map.get(taxLine);

            purchaseOrderLineVat.setExTaxBase(
                purchaseOrderLineVat.getExTaxBase().add(purchaseOrderLine.getExTaxTotal()));

            purchaseOrderLineVat.setReverseCharged(false);

          } else {

            PurchaseOrderLineTax purchaseOrderLineTax = new PurchaseOrderLineTax();
            purchaseOrderLineTax.setPurchaseOrder(purchaseOrder);

            purchaseOrderLineTax.setExTaxBase(purchaseOrderLine.getExTaxTotal());

            purchaseOrderLineTax.setReverseCharged(false);

            purchaseOrderLineTax.setTaxLine(taxLine);
            map.put(taxLine, purchaseOrderLineTax);
          }
        }

        if (taxLineRC != null) {
          LOG.debug("TVA {}", taxLineRC);

          if (map.containsKey(taxLineRC)) {

            PurchaseOrderLineTax purchaseOrderLineRC =
                map.get(taxEquiv.getReverseChargeTax().getActiveTaxLine());

            purchaseOrderLineRC.setExTaxBase(
                purchaseOrderLineRC.getExTaxBase().add(purchaseOrderLine.getExTaxTotal()));

            purchaseOrderLineRC.setReverseCharged(true);

          } else {

            PurchaseOrderLineTax purchaseOrderLineTaxRC = new PurchaseOrderLineTax();
            purchaseOrderLineTaxRC.setPurchaseOrder(purchaseOrder);

            purchaseOrderLineTaxRC.setExTaxBase(purchaseOrderLine.getExTaxTotal());

            purchaseOrderLineTaxRC.setReverseCharged(true);

            purchaseOrderLineTaxRC.setTaxLine(taxLineRC);
            map.put(taxLineRC, purchaseOrderLineTaxRC);
          }
        }
      }
    }

    for (PurchaseOrderLineTax purchaseOrderLineTax : map.values()) {

      // Dans la devise de la commande
      BigDecimal exTaxBase =
          (purchaseOrderLineTax.getReverseCharged())
              ? purchaseOrderLineTax.getExTaxBase().negate()
              : purchaseOrderLineTax.getExTaxBase();
      BigDecimal taxTotal = BigDecimal.ZERO;
      if (purchaseOrderLineTax.getTaxLine() != null)
        taxTotal =
            purchaseOrderToolService.computeAmount(
                exTaxBase,
                purchaseOrderLineTax.getTaxLine().getValue().divide(new BigDecimal(100)));
      purchaseOrderLineTax.setTaxTotal(taxTotal);
      purchaseOrderLineTax.setInTaxTotal(purchaseOrderLineTax.getExTaxBase().add(taxTotal));

      purchaseOrderLineTaxList.add(purchaseOrderLineTax);

      LOG.debug(
          "Ligne de TVA : Total TVA => {}, Total HT => {}",
          new Object[] {purchaseOrderLineTax.getTaxTotal(), purchaseOrderLineTax.getInTaxTotal()});
    }

    return purchaseOrderLineTaxList;
  }
}
