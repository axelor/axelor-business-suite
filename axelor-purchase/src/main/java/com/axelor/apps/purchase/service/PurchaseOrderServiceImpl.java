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
package com.axelor.apps.purchase.service;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Blocking;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.base.service.ProductService;
import com.axelor.apps.base.service.ShippingCoefService;
import com.axelor.apps.base.service.TradingNameService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.purchase.db.IPurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.PurchaseOrderLineTax;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.purchase.exception.IExceptionMessage;
import com.axelor.apps.purchase.report.IReport;
import com.axelor.apps.purchase.service.app.AppPurchaseService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurchaseOrderServiceImpl implements PurchaseOrderService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject protected PurchaseOrderLineTaxService purchaseOrderLineVatService;

  @Inject protected SequenceService sequenceService;

  @Inject protected PartnerRepository partnerRepo;

  @Inject protected AppPurchaseService appPurchaseService;

  @Inject protected PurchaseOrderRepository purchaseOrderRepo;

  @Override
  public PurchaseOrder _computePurchaseOrderLines(PurchaseOrder purchaseOrder)
      throws AxelorException {

    if (purchaseOrder.getPurchaseOrderLineList() != null) {
      PurchaseOrderLineService purchaseOrderLineService = Beans.get(PurchaseOrderLineService.class);
      for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
        purchaseOrderLine.setExTaxTotal(
            purchaseOrderLineService.computePurchaseOrderLine(purchaseOrderLine));
        purchaseOrderLine.setCompanyExTaxTotal(
            purchaseOrderLineService.getCompanyExTaxTotal(
                purchaseOrderLine.getExTaxTotal(), purchaseOrder));
      }
    }

    return purchaseOrder;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public PurchaseOrder computePurchaseOrder(PurchaseOrder purchaseOrder) throws AxelorException {

    this.initPurchaseOrderLineTax(purchaseOrder);

    this._computePurchaseOrderLines(purchaseOrder);

    this._populatePurchaseOrder(purchaseOrder);

    this._computePurchaseOrder(purchaseOrder);

    return purchaseOrder;
  }

  /**
   * Peupler une commande.
   *
   * <p>Cette fonction permet de déterminer les tva d'une commande à partir des lignes de factures
   * passées en paramètres.
   *
   * @param purchaseOrder
   * @throws AxelorException
   */
  @Override
  public void _populatePurchaseOrder(PurchaseOrder purchaseOrder) throws AxelorException {

    logger.debug(
        "Peupler une facture => lignes de devis: {} ",
        new Object[] {purchaseOrder.getPurchaseOrderLineList().size()});

    // create Tva lines
    purchaseOrder
        .getPurchaseOrderLineTaxList()
        .addAll(
            purchaseOrderLineVatService.createsPurchaseOrderLineTax(
                purchaseOrder, purchaseOrder.getPurchaseOrderLineList()));
  }

  /**
   * Compute the purchase order total amounts
   *
   * @param purchaseOrder
   * @throws AxelorException
   */
  @Override
  public void _computePurchaseOrder(PurchaseOrder purchaseOrder) throws AxelorException {

    purchaseOrder.setExTaxTotal(BigDecimal.ZERO);
    purchaseOrder.setCompanyExTaxTotal(BigDecimal.ZERO);
    purchaseOrder.setTaxTotal(BigDecimal.ZERO);
    purchaseOrder.setInTaxTotal(BigDecimal.ZERO);

    for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
      purchaseOrder.setExTaxTotal(
          purchaseOrder.getExTaxTotal().add(purchaseOrderLine.getExTaxTotal()));

      // In the company accounting currency
      purchaseOrder.setCompanyExTaxTotal(
          purchaseOrder.getCompanyExTaxTotal().add(purchaseOrderLine.getCompanyExTaxTotal()));
    }

    for (PurchaseOrderLineTax purchaseOrderLineVat : purchaseOrder.getPurchaseOrderLineTaxList()) {

      // In the purchase order currency
      purchaseOrder.setTaxTotal(
          purchaseOrder.getTaxTotal().add(purchaseOrderLineVat.getTaxTotal()));
    }

    purchaseOrder.setInTaxTotal(purchaseOrder.getExTaxTotal().add(purchaseOrder.getTaxTotal()));

    logger.debug(
        "Montant de la facture: HTT = {},  HT = {}, TVA = {}, TTC = {}",
        new Object[] {
          purchaseOrder.getExTaxTotal(), purchaseOrder.getTaxTotal(), purchaseOrder.getInTaxTotal()
        });
  }

  /**
   * Permet de réinitialiser la liste des lignes de TVA
   *
   * @param purchaseOrder Une commande.
   */
  @Override
  public void initPurchaseOrderLineTax(PurchaseOrder purchaseOrder) {

    if (purchaseOrder.getPurchaseOrderLineTaxList() == null) {
      purchaseOrder.setPurchaseOrderLineTaxList(new ArrayList<PurchaseOrderLineTax>());
    } else {
      purchaseOrder.getPurchaseOrderLineTaxList().clear();
    }
  }

  @Override
  public PurchaseOrder createPurchaseOrder(
      User buyerUser,
      Company company,
      Partner contactPartner,
      Currency currency,
      LocalDate deliveryDate,
      String internalReference,
      String externalReference,
      LocalDate orderDate,
      PriceList priceList,
      Partner supplierPartner,
      TradingName tradingName)
      throws AxelorException {

    logger.debug(
        "Création d'une commande fournisseur : Société = {},  Reference externe = {}, Fournisseur = {}",
        new Object[] {company.getName(), externalReference, supplierPartner.getFullName()});

    PurchaseOrder purchaseOrder = new PurchaseOrder();
    purchaseOrder.setBuyerUser(buyerUser);
    purchaseOrder.setCompany(company);
    purchaseOrder.setContactPartner(contactPartner);
    purchaseOrder.setCurrency(currency);
    purchaseOrder.setDeliveryDate(deliveryDate);
    purchaseOrder.setInternalReference(internalReference);
    purchaseOrder.setExternalReference(externalReference);
    purchaseOrder.setOrderDate(orderDate);
    purchaseOrder.setPriceList(priceList);
    purchaseOrder.setTradingName(tradingName);
    purchaseOrder.setPurchaseOrderLineList(new ArrayList<>());

    purchaseOrder.setPrintingSettings(
        Beans.get(TradingNameService.class).getDefaultPrintingSettings(null, company));

    purchaseOrder.setPurchaseOrderSeq(this.getSequence(company));
    purchaseOrder.setStatusSelect(IPurchaseOrder.STATUS_DRAFT);
    purchaseOrder.setSupplierPartner(supplierPartner);

    return purchaseOrder;
  }

  @Override
  public String getSequence(Company company) throws AxelorException {
    String seq = sequenceService.getSequenceNumber(SequenceRepository.PURCHASE_ORDER, company);
    if (seq == null) {
      throw new AxelorException(
          company,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.PURCHASE_ORDER_1),
          company.getName());
    }
    return seq;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public Partner validateSupplier(PurchaseOrder purchaseOrder) {

    Partner supplierPartner = partnerRepo.find(purchaseOrder.getSupplierPartner().getId());
    supplierPartner.setIsSupplier(true);

    return partnerRepo.save(supplierPartner);
  }

  @Override
  public void savePurchaseOrderPDFAsAttachment(PurchaseOrder purchaseOrder) throws AxelorException {
    if (purchaseOrder.getPrintingSettings() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          String.format(
              I18n.get(IExceptionMessage.PURCHASE_ORDER_MISSING_PRINTING_SETTINGS),
              purchaseOrder.getPurchaseOrderSeq()));
    }

    String language = ReportSettings.getPrintingLocale(purchaseOrder.getSupplierPartner());

    String title =
        I18n.get("Purchase order")
            + purchaseOrder.getPurchaseOrderSeq()
            + ((purchaseOrder.getVersionNumber() > 1)
                ? "-V" + purchaseOrder.getVersionNumber()
                : "");

    // TODO allow per purchase order template!
    ReportFactory.createReport(
            "purchaseOrder", purchaseOrder.getCompany(), IReport.PURCHASE_ORDER, title + "-${date}")
        .addParam("PurchaseOrderId", purchaseOrder.getId().toString())
        .addParam("Locale", language)
        .toAttach(purchaseOrder)
        .generate()
        .getFileLink();
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void requestPurchaseOrder(PurchaseOrder purchaseOrder) throws AxelorException {
    purchaseOrder.setStatusSelect(IPurchaseOrder.STATUS_REQUESTED);
    Partner partner = purchaseOrder.getSupplierPartner();
    Company company = purchaseOrder.getCompany();
    Blocking blocking =
        Beans.get(BlockingService.class)
            .getBlocking(partner, company, BlockingRepository.PURCHASE_BLOCKING);

    if (blocking != null) {
      String reason =
          blocking.getBlockingReason() != null ? blocking.getBlockingReason().getName() : "";
      throw new AxelorException(
          TraceBackRepository.TYPE_FUNCTIONNAL,
          I18n.get(IExceptionMessage.SUPPLIER_BLOCKED) + " " + reason,
          partner);
    }
    if (purchaseOrder.getVersionNumber() == 1
        && sequenceService.isEmptyOrDraftSequenceNumber(purchaseOrder.getPurchaseOrderSeq())) {
      purchaseOrder.setPurchaseOrderSeq(this.getSequence(purchaseOrder.getCompany()));
    }
    purchaseOrderRepo.save(purchaseOrder);
    if (appPurchaseService.getAppPurchase().getManagePurchaseOrderVersion()) {
      this.savePurchaseOrderPDFAsAttachment(purchaseOrder);
    }
  }

  @Override
  @Transactional
  public PurchaseOrder mergePurchaseOrders(
      List<PurchaseOrder> purchaseOrderList,
      Currency currency,
      Partner supplierPartner,
      Company company,
      Partner contactPartner,
      PriceList priceList,
      TradingName tradingName)
      throws AxelorException {

    String numSeq = "";
    String externalRef = "";
    for (PurchaseOrder purchaseOrderLocal : purchaseOrderList) {
      if (!numSeq.isEmpty()) {
        numSeq += "-";
      }
      numSeq += purchaseOrderLocal.getPurchaseOrderSeq();

      if (!externalRef.isEmpty()) {
        externalRef += "|";
      }
      if (purchaseOrderLocal.getExternalReference() != null) {
        externalRef += purchaseOrderLocal.getExternalReference();
      }
    }

    PurchaseOrder purchaseOrderMerged =
        this.createPurchaseOrder(
            AuthUtils.getUser(),
            company,
            contactPartner,
            currency,
            null,
            numSeq,
            externalRef,
            LocalDate.now(),
            priceList,
            supplierPartner,
            tradingName);

    this.attachToNewPurchaseOrder(purchaseOrderList, purchaseOrderMerged);

    this.computePurchaseOrder(purchaseOrderMerged);

    purchaseOrderRepo.save(purchaseOrderMerged);

    this.removeOldPurchaseOrders(purchaseOrderList);

    return purchaseOrderMerged;
  }

  // Attachment of all purchase order lines to new purchase order
  public void attachToNewPurchaseOrder(
      List<PurchaseOrder> purchaseOrderList, PurchaseOrder purchaseOrderMerged) {
    for (PurchaseOrder purchaseOrder : purchaseOrderList) {
      int countLine = 1;
      for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
        purchaseOrderLine.setSequence(countLine * 10);
        purchaseOrderMerged.addPurchaseOrderLineListItem(purchaseOrderLine);
        countLine++;
      }
    }
  }

  // Remove old purchase orders after merge
  public void removeOldPurchaseOrders(List<PurchaseOrder> purchaseOrderList) {
    for (PurchaseOrder purchaseOrder : purchaseOrderList) {
      purchaseOrderRepo.remove(purchaseOrder);
    }
  }

  @Override
  public void setDraftSequence(PurchaseOrder purchaseOrder) throws AxelorException {

    if (purchaseOrder.getId() != null
        && Strings.isNullOrEmpty(purchaseOrder.getPurchaseOrderSeq())) {
      purchaseOrder.setPurchaseOrderSeq(sequenceService.getDraftSequenceNumber(purchaseOrder));
    }
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void updateCostPrice(PurchaseOrder purchaseOrder) throws AxelorException {
    if (purchaseOrder.getPurchaseOrderLineList() != null) {
      for (PurchaseOrderLine purchaseOrderLine : purchaseOrder.getPurchaseOrderLineList()) {
        Product product = purchaseOrderLine.getProduct();
        if (product != null) {
          product.setPurchasePrice(
              purchaseOrder.getInAti()
                  ? purchaseOrderLine.getInTaxPrice()
                  : purchaseOrderLine.getPrice());
          if (product.getDefShipCoefByPartner()) {
            Unit productPurchaseUnit =
                product.getPurchasesUnit() != null ? product.getPurchasesUnit() : product.getUnit();
            BigDecimal convertedQty =
                Beans.get(UnitConversionService.class)
                    .convert(
                        purchaseOrderLine.getUnit(),
                        productPurchaseUnit,
                        purchaseOrderLine.getQty(),
                        purchaseOrderLine.getQty().scale(),
                        product);
            BigDecimal shippingCoef =
                Beans.get(ShippingCoefService.class)
                    .getShippingCoefDefByPartner(
                        product,
                        purchaseOrder.getSupplierPartner(),
                        purchaseOrder.getCompany(),
                        convertedQty);
            if (shippingCoef.compareTo(BigDecimal.ZERO) != 0) {
              product.setShippingCoef(shippingCoef);
            }
          }
          if (product.getCostTypeSelect() == ProductRepository.COST_TYPE_LAST_PURCHASE_PRICE) {
            product.setCostPrice(
                purchaseOrder.getInAti()
                    ? purchaseOrderLine.getInTaxPrice()
                    : purchaseOrderLine.getPrice());
            if (product.getAutoUpdateSalePrice()) {
              Beans.get(ProductService.class).updateSalePrice(product);
            }
          }
        }
      }
      purchaseOrderRepo.save(purchaseOrder);
    }
  }

  @Override
  @Transactional
  public void draftPurchaseOrder(PurchaseOrder purchaseOrder) {

    purchaseOrder.setStatusSelect(IPurchaseOrder.STATUS_DRAFT);
    purchaseOrderRepo.save(purchaseOrder);
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void validatePurchaseOrder(PurchaseOrder purchaseOrder) throws AxelorException {
    computePurchaseOrder(purchaseOrder);

    purchaseOrder.setStatusSelect(IPurchaseOrder.STATUS_VALIDATED);
    purchaseOrder.setValidationDate(appPurchaseService.getTodayDate());
    purchaseOrder.setValidatedByUser(AuthUtils.getUser());

    purchaseOrder.setSupplierPartner(validateSupplier(purchaseOrder));

    updateCostPrice(purchaseOrder);
  }

  @Override
  @Transactional
  public void finishPurchaseOrder(PurchaseOrder purchaseOrder) {

    purchaseOrder.setStatusSelect(IPurchaseOrder.STATUS_FINISHED);
    purchaseOrderRepo.save(purchaseOrder);
  }

  @Override
  @Transactional
  public void cancelPurchaseOrder(PurchaseOrder purchaseOrder) {

    purchaseOrder.setStatusSelect(IPurchaseOrder.STATUS_CANCELED);
    purchaseOrderRepo.save(purchaseOrder);
  }
}
