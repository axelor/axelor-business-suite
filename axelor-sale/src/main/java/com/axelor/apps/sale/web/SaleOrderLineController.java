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
package com.axelor.apps.sale.web;

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.sale.db.PackLine;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.service.SaleOrderLineService;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SaleOrderLineController {

	@Inject
	private SaleOrderLineService saleOrderLineService;
	
	@Inject
	private ProductRepository productRepo;
	
	public void compute(ActionRequest request, ActionResponse response) throws AxelorException {

		Context context = request.getContext();
		
		SaleOrderLine saleOrderLine = context.asType(SaleOrderLine.class);

		SaleOrder saleOrder = saleOrderLineService.getSaleOrder(context);
		
		try{
			BigDecimal[] values = saleOrderLineService.computeValues(saleOrder, saleOrderLine);
			if (values == null) {
				this.resetProductInformation(response);
				return;
			}
			response.setValue("exTaxTotal", values[0]);
			response.setValue("inTaxTotal", values[1]);
			response.setValue("companyInTaxTotal", values[2]);
			response.setValue("companyExTaxTotal", values[3]);
			response.setValue("priceDiscounted", values[4]);
			response.setAttr("priceDiscounted", "hidden", values[4].compareTo(saleOrderLine.getPrice()) == 0);

		}
		catch(Exception e) {
			response.setFlash(e.getMessage());
		}
	}
	
	public void computeSubMargin(ActionRequest request, ActionResponse response) throws AxelorException {

		Context context = request.getContext();

		SaleOrderLine saleOrderLine = context.asType(SaleOrderLine.class);
		SaleOrder saleOrder = saleOrderLineService.getSaleOrder(context);
		
		saleOrderLine.setSaleOrder(saleOrder);
		saleOrderLineService.computeSubMargin(saleOrderLine);

		response.setValue("subTotalCostPrice", saleOrderLine.getSubTotalCostPrice());
		response.setValue("subTotalGrossMargin", saleOrderLine.getSubTotalGrossMargin());
		response.setValue("subMarginRate", saleOrderLine.getSubMarginRate());
	}



	/**
	 * Called by the sale order line form.
	 * Update all fields when the product is changed.
	 * @param request
	 * @param response
	 */
	public void getProductInformation(ActionRequest request, ActionResponse response)  {

		Context context = request.getContext();
		SaleOrderLine saleOrderLine = context.asType(SaleOrderLine.class);
		SaleOrder saleOrder = saleOrderLineService.getSaleOrder(context);
		Integer parentPackPriceSelect = (Integer) context.getParent().get("packPriceSelect");
		
		Product product = saleOrderLine.getProduct();

		if(saleOrder == null || product == null) { 
			this.resetProductInformation(response);
			return;
		}

		try  {
			saleOrderLineService.computeProductInformation(saleOrderLine, saleOrder);
			response.setValue("taxLine", saleOrderLine.getTaxLine());
            response.setValue("taxEquiv", saleOrderLine.getTaxEquiv());
			response.setValue("productName", saleOrderLine.getProductName());
			response.setValue("saleSupplySelect", product.getSaleSupplySelect());
			response.setValue("unit", saleOrderLineService.getSaleUnit(saleOrderLine));
			response.setValue("companyCostPrice", saleOrderLineService.getCompanyCostPrice(saleOrder, saleOrderLine));

			if (saleOrderLine.getDiscountAmount() != null) {
				response.setValue("discountAmount", saleOrderLine.getDiscountAmount());
			}
			if (saleOrderLine.getDiscountTypeSelect() != null) {
				response.setValue("discountTypeSelect", saleOrderLine.getDiscountTypeSelect());
			}
			
			if(saleOrderLine.getPackPriceSelect() == SaleOrderLineRepository.SUBLINE_PRICE_ONLY && saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_PACK) {
				response.setValue("price", 0.00);
			} else if (saleOrderLine.getParentLine() != null || parentPackPriceSelect != null){
				
				if(parentPackPriceSelect != null) {
					if (parentPackPriceSelect == SaleOrderLineRepository.SUBLINE_PRICE_ONLY && saleOrderLine.getIsSubLine()) {
						response.setValue("price", saleOrderLine.getPrice());
					} else {
						response.setValue("price", 0.00);
					}
				} else if (saleOrderLine.getParentLine().getPackPriceSelect() == SaleOrderLineRepository.SUBLINE_PRICE_ONLY && saleOrderLine.getIsSubLine()) {
					response.setValue("price", saleOrderLine.getPrice());
				} else {
					response.setValue("price", 0.00);
				}
			} else {
				response.setValue("price", saleOrderLine.getPrice());
			}
			
		}
		catch(Exception e)  {
			response.setFlash(e.getMessage());
			this.resetProductInformation(response);
		}
	}
	

	public void resetProductInformation(ActionResponse response)  {

		response.setValue("taxLine", null);
		response.setValue("taxEquiv", null);
		response.setValue("productName", null);
		response.setValue("saleSupplySelect", null);
		response.setValue("unit", null);
		response.setValue("companyCostPrice", null);
		response.setValue("discountAmount", null);
		response.setValue("discountTypeSelect", null);
		response.setValue("price", null);
		response.setValue("exTaxTotal", null);
		response.setValue("inTaxTotal", null);
		response.setValue("companyInTaxTotal", null);
		response.setValue("companyExTaxTotal", null);

	}


	public void getDiscount(ActionRequest request, ActionResponse response) {

		Context context = request.getContext();
		
		SaleOrderLine saleOrderLine = context.asType(SaleOrderLine.class);

		SaleOrder saleOrder = saleOrderLineService.getSaleOrder(context);

		if(saleOrder == null || saleOrderLine.getProduct() == null) {  return;  }

		try  {
			BigDecimal price = saleOrderLine.getPrice();

			Map<String,Object> discounts = saleOrderLineService.getDiscount(saleOrder, saleOrderLine, price);
			
			if(discounts == null)  {  return;  }
			
			response.setValue("discountAmount", discounts.get("discountAmount"));
			response.setValue("discountTypeSelect", discounts.get("discountTypeSelect"));
			if(discounts.get("price") != null)  {
				response.setValue("price", (BigDecimal) discounts.get("price"));
			}
			
		}
		catch(Exception e)  {
			response.setFlash(e.getMessage());
		}
	}

	public void convertUnitPrice(ActionRequest request, ActionResponse response) {

		Context context = request.getContext();
		
		SaleOrderLine saleOrderLine = context.asType(SaleOrderLine.class);

		SaleOrder saleOrder = saleOrderLineService.getSaleOrder(context);

		if(saleOrder == null || saleOrderLine.getProduct() == null || !saleOrderLineService.unitPriceShouldBeUpdate(saleOrder, saleOrderLine.getProduct())) {  return;  }

		try  {

			BigDecimal price = saleOrderLineService.getUnitPrice(saleOrder, saleOrderLine, saleOrderLine.getTaxLine());

			Map<String,Object> discounts = saleOrderLineService.getDiscount(saleOrder, saleOrderLine, price);
			
			if(discounts != null)  {  
			
				response.setValue("discountAmount", discounts.get("discountAmount"));
				response.setValue("discountTypeSelect", discounts.get("discountTypeSelect"));
				if(discounts.get("price") != null)  {
					price = (BigDecimal) discounts.get("price");
				}
			}
			
			response.setValue("price", price);
			
		}
		catch(Exception e)  {
			response.setFlash(e.getMessage());
		}
	}
	
	public void emptyLine(ActionRequest request, ActionResponse response){
		SaleOrderLine saleOrderLine = request.getContext().asType(SaleOrderLine.class);
		if(saleOrderLine.getTypeSelect() != SaleOrderLineRepository.TYPE_NORMAL){

			Map<String,Object> newSaleOrderLine =  Mapper.toMap(new SaleOrderLine());
			newSaleOrderLine.put("qty", BigDecimal.ZERO);
			newSaleOrderLine.put("id", saleOrderLine.getId());
			newSaleOrderLine.put("version", saleOrderLine.getVersion());
			newSaleOrderLine.put("typeSelect", saleOrderLine.getTypeSelect());
			response.setValues(newSaleOrderLine);
		}
	}
	
	
	public void createPackLines(ActionRequest request, ActionResponse response) throws AxelorException {
		
		SaleOrderLine soLine = request.getContext().asType(SaleOrderLine.class);
		
		if (soLine.getIsSubLine()) {
			return;
		}
		
		Product product = soLine.getProduct();
		
		if (product != null) {
			
			product = productRepo.find(product.getId());
			
			if (product.getIsPack()) {
				SaleOrder saleOrder = saleOrderLineService.getSaleOrder(request.getContext());
				List<SaleOrderLine> subLines = new ArrayList<SaleOrderLine>();
				
				for (PackLine packLine : product.getPackLines()) {
					SaleOrderLine subLine = new SaleOrderLine();
					Product subProduct = packLine.getProduct();
					subLine.setProduct(subProduct);
					subLine.setProductName(subProduct.getName());
					subLine.setPrice(BigDecimal.ZERO);
					if (product.getPackPriceSelect() == 1) {
						subLine.setPrice(subProduct.getSalePrice());
					}
					subLine.setUnit(saleOrderLineService.getSaleUnit(subLine));
					subLine.setQty(new BigDecimal(packLine.getQuantity()));
					subLine.setIsSubLine(true);
					subLine.setCompanyCostPrice(saleOrderLineService.getCompanyCostPrice(saleOrder, subLine));
					TaxLine taxLine = saleOrderLineService.getTaxLine(saleOrder, subLine);
					subLine.setTaxLine(taxLine);
					saleOrderLineService.computeValues(saleOrder, subLine);

					BigDecimal price = saleOrderLineService.getUnitPrice(saleOrder, subLine, taxLine);

					Map<String,Object> discounts = saleOrderLineService.getDiscount(saleOrder, subLine, price);
					
					if(discounts != null)  {
						subLine.setDiscountAmount((BigDecimal) discounts.get("discountAmount"));
						subLine.setDiscountTypeSelect((Integer) discounts.get("discountTypeSelect"));
						if(discounts.get("price") != null)  {
							price = (BigDecimal) discounts.get("price");
						}
					}
					if (product.getPackPriceSelect() != 1) {
						price = BigDecimal.ZERO;
					}
					subLine.setPrice(price);
					subLine.setPriceDiscounted(saleOrderLineService.computeDiscount(subLine));
					subLines.add(subLine);
				}
				
				if (!subLines.isEmpty()) {
					response.setValue("subLineList", subLines);
				}
				response.setValue("typeSelect", 2);
				response.setValue("qty", 1);
				response.setValue("packPriceSelect", product.getPackPriceSelect());
			}
			
		}
		
	}	
	
	public void updateSubLineQty(ActionRequest request, ActionResponse response) throws AxelorException {
		
		SaleOrderLine newkitLine = request.getContext().asType(SaleOrderLine.class);
		BigDecimal qty = BigDecimal.ZERO;
		BigDecimal oldKitQty = BigDecimal.ZERO;
		BigDecimal newKitQty = BigDecimal.ZERO;
		BigDecimal exTaxTotal = BigDecimal.ZERO;
		BigDecimal inTaxTotal = BigDecimal.ZERO;
		BigDecimal priceDiscounted = BigDecimal.ZERO;
		BigDecimal taxRate = BigDecimal.ZERO;;
		BigDecimal companyExTaxTotal = BigDecimal.ZERO;
		BigDecimal companyInTaxTotal = BigDecimal.ZERO;
		
		Context context = request.getContext();
		SaleOrder saleOrder = saleOrderLineService.getSaleOrder(context);
		
		if(newkitLine.getTypeSelect() == SaleOrderLineRepository.TYPE_PACK) {
			
			if(newkitLine.getOldQty().compareTo(BigDecimal.ZERO) == 0) {
				oldKitQty = BigDecimal.ONE;
			} else {
				oldKitQty = newkitLine.getOldQty();
			}
			
			if(newkitLine.getQty().compareTo(BigDecimal.ZERO) != 0) {
				newKitQty = newkitLine.getQty(); 
			}
			
			List<SaleOrderLine> orderLines = newkitLine.getSubLineList();
				
			if(orderLines != null) {
				if(newKitQty.compareTo(BigDecimal.ZERO) != 0) {
					for(SaleOrderLine line : orderLines) {
						qty = (line.getQty().divide(oldKitQty, 2, RoundingMode.HALF_EVEN)).multiply(newKitQty);
						priceDiscounted = saleOrderLineService.computeDiscount(line);
						
						if(line.getTaxLine() != null)  {  taxRate = line.getTaxLine().getValue();  }
						
						if(!saleOrder.getInAti()) {
							exTaxTotal = saleOrderLineService.computeAmount(qty, priceDiscounted);
							inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
							companyExTaxTotal = saleOrderLineService.getAmountInCompanyCurrency(exTaxTotal, saleOrder);
							companyInTaxTotal = companyExTaxTotal.add(companyExTaxTotal.multiply(taxRate));
						} else {
							inTaxTotal = saleOrderLineService.computeAmount(qty, priceDiscounted);
							exTaxTotal = inTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
							companyInTaxTotal = saleOrderLineService.getAmountInCompanyCurrency(inTaxTotal, line.getSaleOrder());
							companyExTaxTotal = companyInTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
						}
						
						line.setQty(qty.setScale(2, RoundingMode.HALF_EVEN));
						line.setPriceDiscounted(priceDiscounted);
						line.setExTaxTotal(exTaxTotal);
						line.setInTaxTotal(inTaxTotal);
						line.setCompanyExTaxTotal(companyExTaxTotal);
						line.setCompanyInTaxTotal(companyInTaxTotal);
						
					}
				} else {
					for(SaleOrderLine line : orderLines) {
						line.setQty(qty.setScale(2, RoundingMode.HALF_EVEN));
					}
				}
				
				response.setValue("oldQty", newKitQty);
				response.setValue("subLineList", orderLines);
			}
		}
	}
	
	public void resetSubLines(ActionRequest request, ActionResponse response) {
		
		SaleOrderLine packLine = request.getContext().asType(SaleOrderLine.class);
		List<SaleOrderLine> subLines = packLine.getSubLineList();
		
		if(subLines != null) {
			for(SaleOrderLine line : subLines) {
				line.setPrice(BigDecimal.ZERO);
				line.setPriceDiscounted(BigDecimal.ZERO);
				line.setExTaxTotal(BigDecimal.ZERO);
				line.setInTaxTotal(BigDecimal.ZERO);
			}
		}
		response.setValue("subLineList", subLines);
	}
}
