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
package com.axelor.apps.account.service.invoice.generator.tax;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.InvoiceLineTax;
import com.axelor.apps.account.db.TaxEquiv;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.service.invoice.generator.TaxGenerator;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaxInvoiceLine extends TaxGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public TaxInvoiceLine(Invoice invoice, List<InvoiceLine> invoiceLines) {

    super(invoice, invoiceLines);
  }

  /**
   * Créer les lignes de TVA de la facure. La création des lignes de TVA se basent sur les lignes de
   * factures
   *
   * @return La liste des lignes de TVA de la facture.
   */
  @Override
  public List<InvoiceLineTax> creates() {

    Map<TaxLine, InvoiceLineTax> map = new HashMap<>();

    if (invoiceLines != null && !invoiceLines.isEmpty()) {

      LOG.debug("Création des lignes de tva pour les lignes de factures.");

      for (InvoiceLine invoiceLine : invoiceLines) {
        // map is updated with created invoice line taxes
        createInvoiceLineTaxes(invoiceLine, map);
      }
    }

    if (invoice.getPartner().getFiscalPosition() == null
        || !invoice.getPartner().getFiscalPosition().getCustomerSpecificNote()) {
      if (invoiceLines != null) {
        invoice.setSpecificNotes(
            invoiceLines.stream()
                .map(InvoiceLine::getTaxEquiv)
                .filter(Objects::nonNull)
                .map(TaxEquiv::getSpecificNote)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n")));
      }
    } else {
      invoice.setSpecificNotes(invoice.getPartner().getSpecificTaxNote());
    }

    return finalizeInvoiceLineTaxes(map);
  }

  protected void createInvoiceLineTaxes(InvoiceLine invoiceLine, Map<TaxLine, InvoiceLineTax> map) {
    TaxLine taxLine = invoiceLine.getTaxLine();
    TaxEquiv taxEquiv = invoiceLine.getTaxEquiv();
    TaxLine taxLineRC =
        (taxEquiv != null && taxEquiv.getReverseCharge() && taxEquiv.getReverseChargeTax() != null)
            ? taxEquiv.getReverseChargeTax().getActiveTaxLine()
            : null;

    if (taxLine != null) {
      createOrUpdateInvoiceLineTax(invoiceLine, taxLine, map);
    }

    if (taxLineRC != null) {
      createOrUpdateInvoiceLineTaxRc(invoiceLine, taxLineRC, taxEquiv, map);
    }
  }

  protected void createOrUpdateInvoiceLineTax(
      InvoiceLine invoiceLine, TaxLine taxLine, Map<TaxLine, InvoiceLineTax> map) {
    LOG.debug("TVA {}", taxLine);
    InvoiceLineTax invoiceLineTax = map.get(taxLine);
    if (invoiceLineTax != null) {
      updateInvoiceLineTax(invoiceLine, invoiceLineTax);
      invoiceLineTax.setReverseCharged(false);
    } else {

      invoiceLineTax = createInvoiceLineTax(invoiceLine, taxLine);
      invoiceLineTax.setReverseCharged(false);
      map.put(taxLine, invoiceLineTax);
    }
  }

  protected void createOrUpdateInvoiceLineTaxRc(
      InvoiceLine invoiceLine,
      TaxLine taxLineRC,
      TaxEquiv taxEquiv,
      Map<TaxLine, InvoiceLineTax> map) {
    if (map.containsKey(taxLineRC)) {
      InvoiceLineTax invoiceLineTaxRC = map.get(taxEquiv.getReverseChargeTax().getActiveTaxLine());
      updateInvoiceLineTax(invoiceLine, invoiceLineTaxRC);
      invoiceLineTaxRC.setReverseCharged(true);
    } else {
      InvoiceLineTax invoiceLineTaxRC = createInvoiceLineTax(invoiceLine, taxLineRC);
      invoiceLineTaxRC.setReverseCharged(true);
      map.put(taxLineRC, invoiceLineTaxRC);
    }
  }

  protected void updateInvoiceLineTax(InvoiceLine invoiceLine, InvoiceLineTax invoiceLineTax) {

    // Dans la devise de la facture
    invoiceLineTax.setExTaxBase(invoiceLineTax.getExTaxBase().add(invoiceLine.getExTaxTotal()));
    // Dans la devise de la société
    invoiceLineTax.setCompanyExTaxBase(
        invoiceLineTax
            .getCompanyExTaxBase()
            .add(invoiceLine.getCompanyExTaxTotal())
            .setScale(2, RoundingMode.HALF_UP));

    if (!invoiceLine.getFixedAssets()) {
      invoiceLineTax.setSubTotalExcludingFixedAssets(
          invoiceLineTax
              .getSubTotalExcludingFixedAssets()
              .add(invoiceLine.getExTaxTotal())
              .setScale(2, RoundingMode.HALF_UP));
      invoiceLineTax.setCompanySubTotalExcludingFixedAssets(
          invoiceLineTax
              .getCompanySubTotalExcludingFixedAssets()
              .add(invoiceLine.getCompanyExTaxTotal())
              .setScale(2, RoundingMode.HALF_UP));
    }
  }

  protected InvoiceLineTax createInvoiceLineTax(InvoiceLine invoiceLine, TaxLine taxLine) {
    InvoiceLineTax invoiceLineTax = new InvoiceLineTax();
    invoiceLineTax.setInvoice(invoice);

    // Dans la devise de la facture
    invoiceLineTax.setExTaxBase(invoiceLine.getExTaxTotal());
    // Dans la devise de la comptabilité du tiers
    invoiceLineTax.setCompanyExTaxBase(
        invoiceLine.getCompanyExTaxTotal().setScale(2, RoundingMode.HALF_UP));

    if (!invoiceLine.getFixedAssets()) {
      invoiceLineTax.setSubTotalExcludingFixedAssets(
          invoiceLine.getCompanyExTaxTotal().setScale(2, RoundingMode.HALF_UP));
      invoiceLineTax.setCompanySubTotalExcludingFixedAssets(
          invoiceLineTax
              .getCompanySubTotalExcludingFixedAssets()
              .add(invoiceLine.getCompanyExTaxTotal())
              .setScale(2, RoundingMode.HALF_UP));
    }

    invoiceLineTax.setTaxLine(taxLine);
    return invoiceLineTax;
  }

  protected List<InvoiceLineTax> finalizeInvoiceLineTaxes(Map<TaxLine, InvoiceLineTax> map) {

    List<InvoiceLineTax> invoiceLineTaxList = new ArrayList<>();

    for (InvoiceLineTax invoiceLineTax : map.values()) {

      BigDecimal taxValue = invoiceLineTax.getTaxLine().getValue().divide(new BigDecimal(100));
      // Dans la devise de la facture
      BigDecimal exTaxBase =
          (invoiceLineTax.getReverseCharged())
              ? invoiceLineTax.getExTaxBase().negate()
              : invoiceLineTax.getExTaxBase();
      BigDecimal taxTotal = computeAmount(exTaxBase, taxValue);

      invoiceLineTax.setTaxTotal(taxTotal);
      invoiceLineTax.setInTaxTotal(invoiceLineTax.getExTaxBase().add(taxTotal));

      // Dans la devise de la société
      BigDecimal companyExTaxBase =
          (invoiceLineTax.getReverseCharged())
              ? invoiceLineTax.getCompanyExTaxBase().negate()
              : invoiceLineTax.getCompanyExTaxBase();
      BigDecimal companyTaxTotal = computeAmount(companyExTaxBase, taxValue);

      invoiceLineTax.setCompanyTaxTotal(companyTaxTotal);
      invoiceLineTax.setCompanyInTaxTotal(
          invoiceLineTax.getCompanyExTaxBase().add(companyTaxTotal));

      BigDecimal subTotalExcludingFixedAssets =
          invoiceLineTax.getReverseCharged()
              ? invoiceLineTax.getSubTotalExcludingFixedAssets().negate()
              : invoiceLineTax.getSubTotalExcludingFixedAssets();
      invoiceLineTax.setSubTotalExcludingFixedAssets(
          computeAmount(subTotalExcludingFixedAssets, taxValue));

      invoiceLineTax.setSubTotalOfFixedAssets(
          taxTotal
              .subtract(invoiceLineTax.getSubTotalExcludingFixedAssets())
              .setScale(2, RoundingMode.HALF_UP));

      BigDecimal companySubTotalExcludingFixedAssets =
          invoiceLineTax.getReverseCharged()
              ? invoiceLineTax.getCompanySubTotalExcludingFixedAssets().negate()
              : invoiceLineTax.getCompanySubTotalExcludingFixedAssets();
      invoiceLineTax.setCompanySubTotalExcludingFixedAssets(
          computeAmount(companySubTotalExcludingFixedAssets, taxValue));
      invoiceLineTax.setCompanySubTotalOfFixedAssets(
          companyTaxTotal
              .subtract(invoiceLineTax.getCompanySubTotalExcludingFixedAssets())
              .setScale(2, RoundingMode.HALF_UP));
      invoiceLineTaxList.add(invoiceLineTax);

      LOG.debug(
          "Ligne de TVA : Total TVA => {}, Total HT => {}",
          invoiceLineTax.getTaxTotal(),
          invoiceLineTax.getInTaxTotal());
    }

    return invoiceLineTaxList;
  }
}
