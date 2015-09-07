/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2014 Axelor (<http://axelor.com>).
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
package com.axelor.apps.sale.web;

import java.math.BigDecimal;
import java.util.Map;

import com.axelor.apps.base.db.IPriceListLine;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.repo.GeneralRepository;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.exception.IExceptionMessage;
import com.axelor.apps.sale.service.SaleOrderLineService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;

public class SaleOrderLineController {

	@Inject
	private SaleOrderLineService saleOrderLineService;

	@Inject
	private PriceListService priceListService;

	@Inject
	protected GeneralService generalService;


	public void compute(ActionRequest request, ActionResponse response) throws AxelorException {

		SaleOrderLine saleOrderLine = request.getContext().asType(SaleOrderLine.class);

		BigDecimal exTaxTotal = BigDecimal.ZERO;
		BigDecimal companyExTaxTotal = BigDecimal.ZERO;
		BigDecimal inTaxTotal = BigDecimal.ZERO;
		BigDecimal companyInTaxTotal = BigDecimal.ZERO;
		BigDecimal priceDiscounted = BigDecimal.ZERO;

		try{
			if(saleOrderLine.getTaxLine()==null){
				throw new AxelorException(String.format(I18n.get(IExceptionMessage.SALE_ORDER_LINE_TAX_LINE)), IException.CONFIGURATION_ERROR);
			}
			if(!request.getContext().getParentContext().asType(SaleOrder.class).getInAti()){
				if (saleOrderLine.getPrice() != null && saleOrderLine.getQty() != null) {
					exTaxTotal = saleOrderLineService.computeAmount(saleOrderLine);
					inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(saleOrderLine.getTaxLine().getValue()));
					priceDiscounted = saleOrderLineService.computeDiscount(saleOrderLine);
				}

				if(exTaxTotal != null) {

					SaleOrder saleOrder = request.getContext().getParentContext().asType(SaleOrder.class);
					if(!request.getContext().getParentContext().getContextClass().toString().equals(SaleOrder.class.toString())){
						saleOrder = saleOrderLine.getSaleOrder();
					}

					if(saleOrder != null) {

						companyExTaxTotal = saleOrderLineService.getAmountInCompanyCurrency(exTaxTotal, saleOrder);
						companyInTaxTotal = companyExTaxTotal.add(companyExTaxTotal.multiply(saleOrderLine.getTaxLine().getValue()));
					}
				}

				response.setValue("exTaxTotal", exTaxTotal);
				response.setValue("inTaxTotal", inTaxTotal);
				response.setValue("companyExTaxTotal", companyExTaxTotal);
				response.setValue("companyInTaxTotal", companyInTaxTotal);
				response.setValue("priceDiscounted", priceDiscounted);

			}
			else{
				if (saleOrderLine.getPrice() != null && saleOrderLine.getQty() != null) {
					inTaxTotal = saleOrderLineService.computeAmount(saleOrderLine);
					exTaxTotal = inTaxTotal.divide(saleOrderLine.getTaxLine().getValue().add(new BigDecimal(1)), 2, BigDecimal.ROUND_HALF_UP);
					priceDiscounted = saleOrderLineService.computeDiscount(saleOrderLine);
				}

				if(inTaxTotal != null) {

					SaleOrder saleOrder = request.getContext().getParentContext().asType(SaleOrder.class);
					if(!request.getContext().getParentContext().getContextClass().toString().equals(SaleOrder.class.toString())){
						saleOrder = saleOrderLine.getSaleOrder();
					}

					if(saleOrder != null) {

						companyInTaxTotal = saleOrderLineService.getAmountInCompanyCurrency(inTaxTotal, saleOrder);
						companyExTaxTotal = companyInTaxTotal.divide(saleOrderLine.getTaxLine().getValue().add(new BigDecimal(1)), 2, BigDecimal.ROUND_HALF_UP);
					}
				}

				response.setValue("exTaxTotal", exTaxTotal);
				response.setValue("inTaxTotal", inTaxTotal);
				response.setValue("companyInTaxTotal", companyInTaxTotal);
				response.setValue("companyExTaxTotal", companyExTaxTotal);
				response.setValue("priceDiscounted", priceDiscounted);

			}
			response.setAttr("priceDiscounted", "hidden", priceDiscounted.equals(saleOrderLine.getPrice()));
		}
		catch(Exception e) {
			response.setFlash(e.getMessage());
		}
	}

	public void getProductInformation(ActionRequest request, ActionResponse response) {

		SaleOrderLine saleOrderLine = request.getContext().asType(SaleOrderLine.class);

		SaleOrder saleOrder = request.getContext().getParentContext().asType(SaleOrder.class);
		if(!request.getContext().getParentContext().getContextClass().toString().equals(SaleOrder.class.toString())){
			saleOrder = saleOrderLine.getSaleOrder();
		}

		if(saleOrder != null && saleOrderLine.getProduct() != null) {

			try  {
				BigDecimal price = saleOrderLineService.getUnitPrice(saleOrder, saleOrderLine);
				int discountTypeSelect = 0;

				response.setValue("taxLine", saleOrderLineService.getTaxLine(saleOrder, saleOrderLine));
				response.setValue("productName", saleOrderLine.getProduct().getName());
				response.setValue("saleSupplySelect", saleOrderLine.getProduct().getSaleSupplySelect());
				response.setValue("unit", saleOrderLine.getProduct().getUnit());
				response.setValue("companyCostPrice", saleOrderLineService.getCompanyCostPrice(saleOrder, saleOrderLine));

				PriceList priceList = saleOrder.getPriceList();
				if(priceList != null)  {
					PriceListLine priceListLine = saleOrderLineService.getPriceListLine(saleOrderLine, priceList);
					if(priceListLine!=null){
						discountTypeSelect = priceListLine.getTypeSelect();
					}
					if((generalService.getGeneral().getComputeMethodDiscountSelect() == GeneralRepository.INCLUDE_DISCOUNT_REPLACE_ONLY && discountTypeSelect == IPriceListLine.TYPE_REPLACE) || generalService.getGeneral().getComputeMethodDiscountSelect() == GeneralRepository.INCLUDE_DISCOUNT)
					{
						Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);
						price = priceListService.computeDiscount(price, (int) discounts.get("discountTypeSelect"), (BigDecimal) discounts.get("discountAmount"));

					}
					else{
						Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);

						response.setValue("discountAmount", discounts.get("discountAmount"));
						response.setValue("discountTypeSelect", discounts.get("discountTypeSelect"));
						if(discounts.get("price") != null)  {
							price = (BigDecimal) discounts.get("price");
						}
					}
				}

				response.setValue("price", price);

			}
			catch(Exception e)  {
				response.setFlash(e.getMessage());
				this.resetProductInformation(response);
			}
		}
		else  {
			this.resetProductInformation(response);
		}
	}


	public void resetProductInformation(ActionResponse response)  {

		response.setValue("taxLine", null);
		response.setValue("productName", null);
		response.setValue("saleSupplySelect", null);
		response.setValue("unit", null);
		response.setValue("companyCostPrice", null);
		response.setValue("discountAmount", null);
		response.setValue("discountTypeSelect", null);
		response.setValue("price", null);

	}


	public void getDiscount(ActionRequest request, ActionResponse response) {

		SaleOrderLine saleOrderLine = request.getContext().asType(SaleOrderLine.class);

		SaleOrder saleOrder = request.getContext().getParentContext().asType(SaleOrder.class);
		if(!request.getContext().getParentContext().getContextClass().toString().equals(SaleOrder.class.toString())){
			saleOrder = saleOrderLine.getSaleOrder();
		}

		if(saleOrder != null && saleOrderLine.getProduct() != null) {

			try  {
				BigDecimal price = saleOrderLine.getPrice();

				PriceList priceList = saleOrder.getPriceList();
				int discountTypeSelect = 0;
				if(priceList != null)  {
					PriceListLine priceListLine = saleOrderLineService.getPriceListLine(saleOrderLine, priceList);
					if(priceListLine!=null){
						discountTypeSelect = priceListLine.getTypeSelect();
					}

					if((generalService.getGeneral().getComputeMethodDiscountSelect() == GeneralRepository.INCLUDE_DISCOUNT_REPLACE_ONLY && discountTypeSelect == IPriceListLine.TYPE_REPLACE) || generalService.getGeneral().getComputeMethodDiscountSelect() == GeneralRepository.INCLUDE_DISCOUNT)
					{
						Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);
						price = priceListService.computeDiscount(price, (int) discounts.get("discountTypeSelect"), (BigDecimal) discounts.get("discountAmount"));

					}
					else{

						Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);

						response.setValue("discountAmount", discounts.get("discountAmount"));
						response.setValue("discountTypeSelect", discounts.get("discountTypeSelect"));
						if(discounts.get("price") != null)  {
							price = (BigDecimal) discounts.get("price");
						}
					}
				}

				response.setValue("price", price);

			}
			catch(Exception e)  {
				response.setFlash(e.getMessage());
			}
		}
	}

	public void convertUnitPrice(ActionRequest request, ActionResponse response) {

		SaleOrderLine saleOrderLine = request.getContext().asType(SaleOrderLine.class);

		SaleOrder saleOrder = request.getContext().getParentContext().asType(SaleOrder.class);
		if(!request.getContext().getParentContext().getContextClass().toString().equals(SaleOrder.class.toString())){
			saleOrder = saleOrderLine.getSaleOrder();
		}

		if(saleOrder != null) {

			try  {

				BigDecimal price = saleOrderLineService.convertUnitPrice(saleOrderLine, saleOrder);
				BigDecimal discountAmount = saleOrderLineService.convertDiscountAmount(saleOrderLine, saleOrder);

				response.setValue("price", price);
				response.setValue("discountAmount",discountAmount);

			}
			catch(Exception e)  {
				response.setFlash(e.getMessage());
			}
		}
	}


}
