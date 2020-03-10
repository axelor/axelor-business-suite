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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.message.db.repo.TemplateRepository;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.SupplierCatalog;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.purchase.service.PurchaseOrderLineService;
import com.axelor.apps.purchase.service.app.AppPurchaseService;
import com.axelor.apps.stock.db.StockConfig;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockRules;
import com.axelor.apps.stock.db.repo.StockConfigRepository;
import com.axelor.apps.stock.db.repo.StockRulesRepository;
import com.axelor.apps.stock.service.StockRulesServiceImpl;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.mail.MessagingException;

public class StockRulesServiceSupplychainImpl extends StockRulesServiceImpl {

  protected PurchaseOrderLineService purchaseOrderLineService;
  protected PurchaseOrderRepository purchaseOrderRepo;
  protected TemplateRepository templateRepo;
  protected TemplateMessageService templateMessageService;
  protected MessageRepository messageRepo;
  protected StockConfigRepository stockConfigRepo;

  @Inject
  public StockRulesServiceSupplychainImpl(
      StockRulesRepository stockRuleRepo,
      PurchaseOrderLineService purchaseOrderLineService,
      PurchaseOrderRepository purchaseOrderRepo,
      TemplateRepository templateRepo,
      TemplateMessageService templateMessageService,
      MessageRepository messageRepo,
      StockConfigRepository stockConfigRepo) {
    super(stockRuleRepo);
    this.purchaseOrderLineService = purchaseOrderLineService;
    this.purchaseOrderRepo = purchaseOrderRepo;
    this.templateRepo = templateRepo;
    this.templateMessageService = templateMessageService;
    this.messageRepo = messageRepo;
    this.stockConfigRepo = stockConfigRepo;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void generatePurchaseOrder(
      Product product, BigDecimal qty, StockLocationLine stockLocationLine, int type)
      throws AxelorException {

    if (!Beans.get(AppSupplychainService.class).isApp("supplychain")) {
      super.generatePurchaseOrder(product, qty, stockLocationLine, type);
      return;
    }

    StockLocation stockLocation = stockLocationLine.getStockLocation();

    // TODO à supprimer après suppression des variantes
    if (stockLocation == null) {
      return;
    }

    StockRules stockRules =
        this.getStockRules(
            product, stockLocation, type, StockRulesRepository.USE_CASE_STOCK_CONTROL);

    if (stockRules == null) {
      return;
    }

    if (this.useMinStockRules(stockLocationLine, stockRules, qty, type)) {

      if (stockRules.getOrderAlertSelect() == StockRulesRepository.ORDER_ALERT_ALERT) {

        Template template = stockRules.getStockRuleMessageTemplate();

        if (template == null) {
          StockConfig stockConfig =
              stockConfigRepo
                  .all()
                  .filter(
                      "self.company = ?1 AND self.stockRuleMessageTemplate IS NOT NULL",
                      stockRules.getStockLocation().getCompany())
                  .fetchOne();
          if (stockConfig != null) {
            template = stockConfig.getStockRuleMessageTemplate();
          } else {
            template =
                templateRepo
                    .all()
                    .filter(
                        "self.metaModel.fullName = ?1 AND self.isSystem != true",
                        StockRules.class.getName())
                    .fetchOne();
          }
        }

        if (template != null) {
          try {
            Message message = templateMessageService.generateAndSendMessage(stockRules, template);
          } catch (ClassNotFoundException
              | InstantiationException
              | IllegalAccessException
              | MessagingException
              | IOException e) {
            throw new AxelorException(e, TraceBackRepository.TYPE_TECHNICAL);
          }
        }

      } else if (stockRules.getOrderAlertSelect()
          == StockRulesRepository.ORDER_ALERT_PRODUCTION_ORDER) {

      } else if (stockRules.getOrderAlertSelect()
          == StockRulesRepository.ORDER_ALERT_PURCHASE_ORDER) {

        BigDecimal minReorderQty = getDefaultSupplierMinQty(product);
        BigDecimal qtyToOrder =
            this.getQtyToOrder(qty, stockLocationLine, type, stockRules, minReorderQty);
        Partner supplierPartner = product.getDefaultSupplierPartner();

        if (supplierPartner != null) {

          Company company = stockLocation.getCompany();
          LocalDate today = Beans.get(AppBaseService.class).getTodayDate();

          PurchaseOrderServiceSupplychainImpl purchaseOrderServiceSupplychainImpl =
              Beans.get(PurchaseOrderServiceSupplychainImpl.class);

          PurchaseOrder purchaseOrder =
              purchaseOrderRepo.save(
                  purchaseOrderServiceSupplychainImpl.createPurchaseOrder(
                      AuthUtils.getUser(),
                      company,
                      null,
                      supplierPartner.getCurrency(),
                      today.plusDays(supplierPartner.getDeliveryDelay()),
                      stockRules.getName(),
                      null,
                      stockLocation,
                      today,
                      Beans.get(PartnerPriceListService.class)
                          .getDefaultPriceList(supplierPartner, PriceListRepository.TYPE_PURCHASE),
                      supplierPartner,
                      null));

          purchaseOrder.addPurchaseOrderLineListItem(
              purchaseOrderLineService.createPurchaseOrderLine(
                  purchaseOrder, product, null, null, qtyToOrder, product.getUnit()));

          purchaseOrderServiceSupplychainImpl.computePurchaseOrder(purchaseOrder);

          purchaseOrderRepo.save(purchaseOrder);
        }
      }
    }
  }

  /**
   * Get minimum quantity from default supplier.
   *
   * @param product
   * @return
   */
  protected BigDecimal getDefaultSupplierMinQty(Product product) {
    Partner defaultSupplierPartner = product.getDefaultSupplierPartner();
    if (Beans.get(AppPurchaseService.class).getAppPurchase().getManageSupplierCatalog()) {
      List<SupplierCatalog> supplierCatalogList = product.getSupplierCatalogList();
      if (defaultSupplierPartner != null && supplierCatalogList != null) {
        for (SupplierCatalog supplierCatalog : supplierCatalogList) {
          if (supplierCatalog.getSupplierPartner().equals(defaultSupplierPartner)) {
            return supplierCatalog.getMinQty();
          }
        }
      }
    }
    return BigDecimal.ZERO;
  }
}
