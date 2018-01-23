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
package com.axelor.apps.businessproject.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.IPriceListLine;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.AppBaseRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.businessproject.service.app.AppBusinessProjectService;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.service.PurchaseOrderLineServiceImpl;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceServiceImpl;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineGeneratorSupplyChain;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PurchaseOrderInvoiceProjectServiceImpl extends PurchaseOrderInvoiceServiceImpl{

	@Inject
	private PriceListService priceListService;

	@Inject
	private PurchaseOrderLineServiceImpl purchaseOrderLineServiceImpl;

	@Inject
	protected AppBusinessProjectService appBusinessProjectService;

	@Override
	protected void processPurchaseOrderLine(Invoice invoice, List<InvoiceLine> invoiceLineList, PurchaseOrderLine purchaseOrderLine) throws AxelorException {
		super.processPurchaseOrderLine(invoice,invoiceLineList,purchaseOrderLine);
		invoiceLineList.get(invoiceLineList.size()-1).setProject(purchaseOrderLine.getProject());
	}

	@Override
	public List<InvoiceLine> createInvoiceLine(Invoice invoice, PurchaseOrderLine purchaseOrderLine) throws AxelorException  {

		Product product = purchaseOrderLine.getProduct();
		BigDecimal price = product.getCostPrice();
		BigDecimal discountAmount = product.getCostPrice();
		int discountTypeSelect = 1;
		if(invoice.getPartner().getChargeBackPurchaseSelect() == PartnerRepository.CHARGING_BACK_TYPE_PRICE_LIST){
			PriceList priceList = Beans.get(PartnerPriceListService.class).getDefaultPriceList(invoice.getPartner(), PriceListRepository.TYPE_SALE);
			if(priceList != null)  {
				PriceListLine priceListLine = purchaseOrderLineServiceImpl.getPriceListLine(purchaseOrderLine, priceList);
				if(priceListLine!=null){
					discountTypeSelect = priceListLine.getTypeSelect();
				}
				if((appBusinessProjectService.getAppBase().getComputeMethodDiscountSelect() == AppBaseRepository.INCLUDE_DISCOUNT_REPLACE_ONLY && discountTypeSelect == IPriceListLine.TYPE_REPLACE) || appBusinessProjectService.getAppBase().getComputeMethodDiscountSelect() == AppBaseRepository.INCLUDE_DISCOUNT)
				{
					Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);
					if(discounts != null){
						discountAmount = (BigDecimal) discounts.get("discountAmount");
						price = priceListService.computeDiscount(price, (int) discounts.get("discountTypeSelect"), discountAmount);
					}

				}
				else{
					Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);
					if(discounts != null){
						discountAmount = (BigDecimal) discounts.get("discountAmount");
						if(discounts.get("price") != null)  {
							price = (BigDecimal) discounts.get("price");
						}
					}
				}

			}


			InvoiceLineGenerator invoiceLineGenerator = new InvoiceLineGenerator(invoice, product, product.getName(), price,
						price,purchaseOrderLine.getDescription(),purchaseOrderLine.getQty(),purchaseOrderLine.getUnit(), null,InvoiceLineGenerator.DEFAULT_SEQUENCE,discountAmount,discountTypeSelect,
						null, null,false, false)  {
				@Override
				public List<InvoiceLine> creates() throws AxelorException {

					InvoiceLine invoiceLine = this.createInvoiceLine();

					List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
					invoiceLines.add(invoiceLine);

					return invoiceLines;
				}
			};
			return invoiceLineGenerator.creates();
		}
		else if(invoice.getPartner().getChargeBackPurchaseSelect() == PartnerRepository.CHARGING_BACK_TYPE_PERCENTAGE){
			price = price.multiply(invoice.getPartner().getChargeBackPurchase().divide(new BigDecimal(100), appBusinessProjectService.getNbDecimalDigitForUnitPrice(), BigDecimal.ROUND_HALF_UP)).setScale(appBusinessProjectService.getNbDecimalDigitForUnitPrice(), BigDecimal.ROUND_HALF_UP);
			InvoiceLineGenerator invoiceLineGenerator = new InvoiceLineGenerator(invoice, product, product.getName(), price,
						price,purchaseOrderLine.getDescription(),purchaseOrderLine.getQty(),purchaseOrderLine.getUnit(), null,InvoiceLineGenerator.DEFAULT_SEQUENCE,discountAmount,discountTypeSelect,
						null, null,false, false)  {
				@Override
				public List<InvoiceLine> creates() throws AxelorException {

					InvoiceLine invoiceLine = this.createInvoiceLine();

					List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
					invoiceLines.add(invoiceLine);

					return invoiceLines;
				}
			};
			return invoiceLineGenerator.creates();
		}

		else{
			InvoiceLineGeneratorSupplyChain invoiceLineGenerator = new InvoiceLineGeneratorSupplyChain(invoice, product, purchaseOrderLine.getProductName(),
					purchaseOrderLine.getDescription(), purchaseOrderLine.getQty(), purchaseOrderLine.getUnit(),
					purchaseOrderLine.getSequence(), false, null, purchaseOrderLine, null, false)  {
				@Override
				public List<InvoiceLine> creates() throws AxelorException {

					InvoiceLine invoiceLine = this.createInvoiceLine();

					List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
					invoiceLines.add(invoiceLine);

					return invoiceLines;
				}
			};
			return invoiceLineGenerator.creates();
		}

	}
}
