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
package com.axelor.apps.purchase.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.IAdministration;
import com.axelor.apps.base.db.IPriceListLine;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.SupplierCatalog;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderLineRepository;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;

public class PurchaseOrderLineServiceImpl  extends PurchaseOrderLineRepository implements PurchaseOrderLineService {
	private static final Logger LOG = LoggerFactory.getLogger(PurchaseOrderLineServiceImpl.class);

	@Inject
	private CurrencyService currencyService;

	@Inject
	private AccountManagementService accountManagementService;

	@Inject
	private PriceListService priceListService;

	@Inject
	protected GeneralService generalService;

	private int sequence = 0;

	/**
	 * Calculer le montant HT d'une ligne de commande.
	 *
	 * @param quantity
	 *          Quantité.
	 * @param price
	 *          Le prix.
	 *
	 * @return
	 * 			Le montant HT de la ligne.
	 */
	public static BigDecimal computeAmount(BigDecimal quantity, BigDecimal price) {

		BigDecimal amount = quantity.multiply(price).setScale(IAdministration.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_EVEN);

		LOG.debug("Calcul du montant HT avec une quantité de {} pour {} : {}", new Object[] { quantity, price, amount });

		return amount;
	}


	@Override
	public BigDecimal getUnitPrice(PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine) throws AxelorException  {

		Product product = purchaseOrderLine.getProduct();

		return currencyService.getAmountCurrencyConverted(
			product.getPurchaseCurrency(), purchaseOrder.getCurrency(), product.getPurchasePrice(), purchaseOrder.getOrderDate())
			.setScale(generalService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
	}

	@Override
	public BigDecimal getMinSalePrice(PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine) throws AxelorException  {

		Product product = purchaseOrderLine.getProduct();

		return currencyService.getAmountCurrencyConverted(
			product.getSaleCurrency(), purchaseOrder.getCurrency(), product.getSalePrice(), purchaseOrder.getOrderDate())
			.setScale(generalService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);
	}

	@Override
	public BigDecimal getSalePrice(PurchaseOrder purchaseOrder, Product product,BigDecimal price) throws AxelorException  {

		return currencyService.getAmountCurrencyConverted(
				product.getSaleCurrency(), purchaseOrder.getCurrency(), price.multiply(product.getManagPriceCoef()), purchaseOrder.getOrderDate())
				.setScale(generalService.getNbDecimalDigitForUnitPrice(), RoundingMode.HALF_UP);

	}


	@Override
	public TaxLine getTaxLine(PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine) throws AxelorException  {

		return accountManagementService.getTaxLine(
				purchaseOrder.getOrderDate(), purchaseOrderLine.getProduct(), purchaseOrder.getCompany(), purchaseOrder.getSupplierPartner().getFiscalPosition(), true);

	}


	@Override
	public BigDecimal computePurchaseOrderLine(PurchaseOrderLine purchaseOrderLine)  {

		return purchaseOrderLine.getExTaxTotal();
	}


	@Override
	public BigDecimal getCompanyExTaxTotal(BigDecimal exTaxTotal, PurchaseOrder purchaseOrder) throws AxelorException  {

		return currencyService.getAmountCurrencyConverted(
				purchaseOrder.getCurrency(), purchaseOrder.getCompany().getCurrency(), exTaxTotal, purchaseOrder.getOrderDate())
				.setScale(IAdministration.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
	}


	@Override
	public PriceListLine getPriceListLine(PurchaseOrderLine purchaseOrderLine, PriceList priceList)  {

		return priceListService.getPriceListLine(purchaseOrderLine.getProduct(), purchaseOrderLine.getQty(), priceList);

	}


	@Override
	public BigDecimal computeDiscount(PurchaseOrderLine purchaseOrderLine)  {

		return priceListService.computeDiscount(purchaseOrderLine.getPrice(), purchaseOrderLine.getDiscountTypeSelect(),purchaseOrderLine.getDiscountAmount());

	}


	@Override
	public PurchaseOrderLine createPurchaseOrderLine(PurchaseOrder purchaseOrder, Product product, String description, BigDecimal qty, Unit unit) throws AxelorException  {

		PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
		purchaseOrderLine.setPurchaseOrder(purchaseOrder);

		purchaseOrderLine.setEstimatedDelivDate(purchaseOrder.getDeliveryDate());
		purchaseOrderLine.setDescription(description);

		purchaseOrderLine.setIsOrdered(false);

		purchaseOrderLine.setProduct(product);
		purchaseOrderLine.setProductName(product.getName());

		purchaseOrderLine.setQty(qty);
		purchaseOrderLine.setSequence(sequence);
		sequence++;

		purchaseOrderLine.setUnit(unit);
		purchaseOrderLine.setTaxLine(this.getTaxLine(purchaseOrder, purchaseOrderLine));

		BigDecimal price = this.getUnitPrice(purchaseOrder, purchaseOrderLine);

		PriceList priceList = purchaseOrder.getPriceList();
		if(priceList != null)  {
			PriceListLine priceListLine = this.getPriceListLine(purchaseOrderLine, priceList);

			Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);

			purchaseOrderLine.setDiscountAmount((BigDecimal) discounts.get("discountAmount"));
			purchaseOrderLine.setDiscountTypeSelect((Integer) discounts.get("discountTypeSelect"));

			if(discounts.get("price") != null)  {
				price = (BigDecimal) discounts.get("price");
			}
		}
		purchaseOrderLine.setPrice(price);

		purchaseOrderLine.setPrice(this.convertUnitPrice(purchaseOrderLine, purchaseOrder));
		purchaseOrderLine.setDiscountAmount(this.convertDiscountAmount(purchaseOrderLine, purchaseOrder));
		if(!purchaseOrder.getInAti()){
			BigDecimal exTaxTotal = PurchaseOrderLineServiceImpl.computeAmount(purchaseOrderLine.getQty(), this.computeDiscount(purchaseOrderLine));
			BigDecimal inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(purchaseOrderLine.getTaxLine().getValue()));
			BigDecimal priceDiscounted = this.computeDiscount(purchaseOrderLine);
			BigDecimal companyExTaxTotal = this.getCompanyExTaxTotal(exTaxTotal, purchaseOrder);
			BigDecimal companyInTaxTotal = companyExTaxTotal.add(companyExTaxTotal.multiply(purchaseOrderLine.getTaxLine().getValue()));

			purchaseOrderLine.setExTaxTotal(exTaxTotal);
			purchaseOrderLine.setCompanyExTaxTotal(companyExTaxTotal);
			purchaseOrderLine.setCompanyInTaxTotal(companyInTaxTotal);
			purchaseOrderLine.setInTaxTotal(inTaxTotal);
			purchaseOrderLine.setPriceDiscounted(priceDiscounted);
		}
		else{
			BigDecimal inTaxTotal = PurchaseOrderLineServiceImpl.computeAmount(purchaseOrderLine.getQty(), this.computeDiscount(purchaseOrderLine));
			BigDecimal exTaxTotal = inTaxTotal.divide(purchaseOrderLine.getTaxLine().getValue().add(new BigDecimal(1)), 2, BigDecimal.ROUND_HALF_UP);
			BigDecimal priceDiscounted = this.computeDiscount(purchaseOrderLine);
			BigDecimal companyInTaxTotal = this.getCompanyExTaxTotal(inTaxTotal, purchaseOrder);
			BigDecimal companyExTaxTotal = companyInTaxTotal.divide(purchaseOrderLine.getTaxLine().getValue().add(new BigDecimal(1)), 2, BigDecimal.ROUND_HALF_UP);

			purchaseOrderLine.setExTaxTotal(exTaxTotal);
			purchaseOrderLine.setCompanyExTaxTotal(companyExTaxTotal);
			purchaseOrderLine.setCompanyExTaxTotal(companyInTaxTotal);
			purchaseOrderLine.setInTaxTotal(inTaxTotal);
			purchaseOrderLine.setPriceDiscounted(priceDiscounted);
		}

		return purchaseOrderLine;
	}


	@Override
	public BigDecimal getQty(PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine)  {

		SupplierCatalog supplierCatalog = this.getSupplierCatalog(purchaseOrder,purchaseOrderLine);

		if(supplierCatalog != null)  {

			return supplierCatalog.getMinQty();

		}

		return BigDecimal.ONE;

	}

	@Override
	public SupplierCatalog getSupplierCatalog(PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine)  {

		Product product = purchaseOrderLine.getProduct();

		SupplierCatalog supplierCatalog = this.getSupplierCatalog(product, purchaseOrder.getSupplierPartner());

//		If there is no catalog for supplier, then we don't take the default catalog.

//		if(supplierCatalog == null)  {
//
//			supplierCatalog = this.getSupplierCatalog(product, product.getDefaultSupplierPartner());
//		}

		return supplierCatalog;

	}


	@Override
	public SupplierCatalog getSupplierCatalog(Product product, Partner supplierPartner)  {

		if(product.getSupplierCatalogList() != null)  {

			for(SupplierCatalog supplierCatalog : product.getSupplierCatalogList())  {

				if(supplierCatalog.getSupplierPartner().equals(supplierPartner))  {
					return supplierCatalog;
				}

			}

		}
		return null;

	}

	@Override
	public BigDecimal convertUnitPrice(PurchaseOrderLine purchaseOrderLine, PurchaseOrder purchaseOrder){
		BigDecimal price = purchaseOrderLine.getPrice();

		if(purchaseOrderLine.getProduct().getInAti() && !purchaseOrder.getInAti()){
			price = price.divide(purchaseOrderLine.getTaxLine().getValue().add(new BigDecimal(1)), 2, BigDecimal.ROUND_HALF_UP);

		}
		else if(!purchaseOrderLine.getProduct().getInAti() && purchaseOrder.getInAti()){
			price = price.add(price.multiply(purchaseOrderLine.getTaxLine().getValue()));
		}
		return price;
	}

	@Override
	public BigDecimal convertDiscountAmount(PurchaseOrderLine purchaseOrderLine, PurchaseOrder purchaseOrder){
		BigDecimal discountAmount = BigDecimal.ZERO;
		if(purchaseOrderLine.getDiscountTypeSelect() == IPriceListLine.AMOUNT_TYPE_FIXED){
			discountAmount = purchaseOrderLine.getProduct().getPurchasePrice().subtract(this.computeDiscount(purchaseOrderLine));
		}
		else{
			discountAmount = (purchaseOrderLine.getProduct().getPurchasePrice().subtract(this.computeDiscount(purchaseOrderLine))).multiply(new BigDecimal(100)).divide(purchaseOrderLine.getProduct().getPurchasePrice(), 2, BigDecimal.ROUND_HALF_UP);
		}
		if(purchaseOrderLine.getProduct().getInAti() && !purchaseOrder.getInAti()){
			discountAmount = discountAmount.divide(purchaseOrderLine.getTaxLine().getValue().add(new BigDecimal(1)), 2, BigDecimal.ROUND_HALF_UP);
		}
		else if(!purchaseOrderLine.getProduct().getInAti() && purchaseOrder.getInAti()){
			discountAmount = discountAmount.add(discountAmount.multiply(purchaseOrderLine.getTaxLine().getValue()));
		}
		return discountAmount;
	}

	@Override
	public int getDiscountTypeSelect(PurchaseOrderLine purchaseOrderLine, PurchaseOrder purchaseOrder){
		PriceList priceList = purchaseOrder.getPriceList();
		if(priceList != null)  {
			PriceListLine priceListLine = this.getPriceListLine(purchaseOrderLine, priceList);

			return priceListLine.getTypeSelect();
		}
		return 0;
	}

}
