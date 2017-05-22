/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
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
package com.axelor.apps.supplychain.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.account.db.repo.AnalyticMoveLineMngtRepository;
import com.axelor.apps.account.service.BudgetService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.workflow.cancel.CancelState;
import com.axelor.apps.account.service.invoice.workflow.ventilate.VentilateState;
import com.axelor.apps.purchase.db.repo.PurchaseOrderManagementRepository;
import com.axelor.apps.purchase.service.PurchaseOrderLineService;
import com.axelor.apps.purchase.service.PurchaseOrderLineServiceImpl;
import com.axelor.apps.purchase.service.PurchaseOrderServiceImpl;
import com.axelor.apps.sale.db.repo.AdvancePaymentRepository;
import com.axelor.apps.sale.db.repo.SaleOrderManagementRepository;
import com.axelor.apps.sale.service.AdvancePaymentServiceImpl;
import com.axelor.apps.sale.service.OpportunitySaleOrderServiceImpl;
import com.axelor.apps.sale.service.SaleOrderLineServiceImpl;
import com.axelor.apps.sale.service.SaleOrderServiceImpl;
import com.axelor.apps.stock.service.StockRulesService;
import com.axelor.apps.stock.service.StockRulesServiceImpl;
import com.axelor.apps.stock.service.StockMoveLineServiceImpl;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.StockMoveServiceImpl;
import com.axelor.apps.supplychain.db.repo.AdvancePaymentSupplychainRepository;
import com.axelor.apps.supplychain.db.repo.AnalyticMoveLineSupplychainRepository;
import com.axelor.apps.supplychain.db.repo.PurchaseOrderSupplychainRepository;
import com.axelor.apps.supplychain.db.repo.SaleOrderSupplychainRepository;
import com.axelor.apps.supplychain.service.AdvancePaymentServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.BudgetSupplychainService;
import com.axelor.apps.supplychain.service.InvoiceLineSupplychainService;
import com.axelor.apps.supplychain.service.StockRulesServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.MrpLineService;
import com.axelor.apps.supplychain.service.MrpLineServiceImpl;
import com.axelor.apps.supplychain.service.MrpService;
import com.axelor.apps.supplychain.service.MrpServiceImpl;
import com.axelor.apps.supplychain.service.OpportunitySaleOrderServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.PurchaseOrderServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.SaleOrderLineServiceSupplyChainImpl;
import com.axelor.apps.supplychain.service.SaleOrderPurchaseService;
import com.axelor.apps.supplychain.service.SaleOrderPurchaseServiceImpl;
import com.axelor.apps.supplychain.service.SaleOrderServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.SaleOrderStockService;
import com.axelor.apps.supplychain.service.SaleOrderStockServiceImpl;
import com.axelor.apps.supplychain.service.StockMoveInvoiceService;
import com.axelor.apps.supplychain.service.StockMoveInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.StockMoveLineSupplychainServiceImpl;
import com.axelor.apps.supplychain.service.StockMoveServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.SubscriptionService;
import com.axelor.apps.supplychain.service.SubscriptionServiceImpl;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.app.AppSupplychainServiceImpl;
import com.axelor.apps.supplychain.service.workflow.CancelStateSupplyChain;
import com.axelor.apps.supplychain.service.workflow.VentilateStateSupplyChain;


public class SupplychainModule extends AxelorModule {

    @Override
    protected void configure() {
    	bind(StockRulesService.class).to(StockRulesServiceImpl.class);
        bind(StockRulesServiceImpl.class).to(StockRulesServiceSupplychainImpl.class);
        bind(StockMoveService.class).to(StockMoveServiceImpl.class);
        bind(PurchaseOrderServiceImpl.class).to(PurchaseOrderServiceSupplychainImpl.class);
        bind(PurchaseOrderLineService.class).to(PurchaseOrderLineServiceImpl.class);
        bind(SaleOrderServiceImpl.class).to(SaleOrderServiceSupplychainImpl.class);
        bind(PurchaseOrderInvoiceService.class).to(PurchaseOrderInvoiceServiceImpl.class);
        bind(SaleOrderInvoiceService.class).to(SaleOrderInvoiceServiceImpl.class);
        bind(SaleOrderPurchaseService.class).to(SaleOrderPurchaseServiceImpl.class);
        bind(StockMoveInvoiceService.class).to(StockMoveInvoiceServiceImpl.class);
        bind(SaleOrderManagementRepository.class).to(SaleOrderSupplychainRepository.class);
        bind(StockMoveServiceImpl.class).to(StockMoveServiceSupplychainImpl.class);
        bind(VentilateState.class).to(VentilateStateSupplyChain.class);
        bind(CancelState.class).to(CancelStateSupplyChain.class);
        bind(SubscriptionService.class).to(SubscriptionServiceImpl.class);
        bind(OpportunitySaleOrderServiceImpl.class).to(OpportunitySaleOrderServiceSupplychainImpl.class);
        bind(SaleOrderLineServiceImpl.class).to(SaleOrderLineServiceSupplyChainImpl.class);
        bind(AdvancePaymentRepository.class).to(AdvancePaymentSupplychainRepository.class);
        bind(AdvancePaymentServiceImpl.class).to(AdvancePaymentServiceSupplychainImpl.class);
        bind(MrpService.class).to(MrpServiceImpl.class);
        bind(MrpLineService.class).to(MrpLineServiceImpl.class);
        bind(AnalyticMoveLineMngtRepository.class).to(AnalyticMoveLineSupplychainRepository.class);
        bind(StockMoveLineServiceImpl.class).to(StockMoveLineSupplychainServiceImpl.class);
        bind(BudgetService.class).to(BudgetSupplychainService.class);
        bind(InvoiceLineService.class).to(InvoiceLineSupplychainService.class);
        bind(SaleOrderStockService.class).to(SaleOrderStockServiceImpl.class);
        bind(PurchaseOrderManagementRepository.class).to(PurchaseOrderSupplychainRepository.class);
        bind(AppSupplychainService.class).to(AppSupplychainServiceImpl.class);
    }
}
