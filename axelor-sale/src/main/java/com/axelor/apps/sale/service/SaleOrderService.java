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
package com.axelor.apps.sale.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.team.db.Team;
import com.google.inject.persist.Transactional;

public interface SaleOrderService {


	public SaleOrder _computeSaleOrderLineList(SaleOrder saleOrder) throws AxelorException;


	public SaleOrder computeSaleOrder(SaleOrder saleOrder) throws AxelorException;
	
	public void computeMarginSaleOrder(SaleOrder saleOrder);


	/**
	 * Peupler un devis.
	 * <p>
	 * Cette fonction permet de déterminer les tva d'un devis.
	 * </p>
	 *
	 * @param saleOrder
	 *
	 * @throws AxelorException
	 */
	public void _populateSaleOrder(SaleOrder saleOrder) throws AxelorException;


	/**
	 * Calculer le montant d'une facture.
	 * <p>
	 * Le calcul est basé sur les lignes de TVA préalablement créées.
	 * </p>
	 *
	 * @param invoice
	 * @param vatLines
	 * @throws AxelorException
	 */
	public void _computeSaleOrder(SaleOrder saleOrder) throws AxelorException;


	/**
	 * Permet de réinitialiser la liste des lignes de TVA
	 * @param saleOrder
	 * 			Un devis
	 */
	public void initSaleOrderLineTaxList(SaleOrder saleOrder);


	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Partner validateCustomer(SaleOrder saleOrder);


	public String getSequence(Company company) throws AxelorException;


	public SaleOrder createSaleOrder(User buyerUser, Company company, Partner contactPartner, Currency currency,
			LocalDate deliveryDate, String internalReference, String externalReference, LocalDate orderDate,
			PriceList priceList, Partner clientPartner, Team team) throws AxelorException;

	public SaleOrder createSaleOrder(Company company) throws AxelorException;

	public void cancelSaleOrder(SaleOrder saleOrder, CancelReason cancelReason, String cancelReasonStr);

	public void finalizeSaleOrder(SaleOrder saleOrder) throws Exception;

	public void confirmSaleOrder(SaleOrder saleOrder) throws Exception;

	public void saveSaleOrderPDFAsAttachment(SaleOrder saleOrder) throws AxelorException;
	
	public SaleOrder mergeSaleOrders(List<SaleOrder> saleOrderList, Currency currency, Partner clientPartner, Company company, Partner contactPartner, PriceList priceList, Team team) throws AxelorException;

	public String getLanguageForPrinting(SaleOrder saleOrder);
	
	public String getFileName(SaleOrder saleOrder);

	@Transactional
	public SaleOrder createTemplate(SaleOrder context);

	@Transactional
	public SaleOrder createSaleOrder(SaleOrder context);

	public SaleOrder computeEndOfValidityDate(SaleOrder saleOrder);
	
	public String getReportLink(SaleOrder saleOrder, String name, String language, boolean proforma, String format) throws AxelorException;

	/**
	 * Fill {@link SaleOrder#mainInvoicingAddressStr}
	 * and {@link SaleOrder#deliveryAddressStr}
	 * @param saleOrder
	 */
	public void computeAddressStr(SaleOrder saleOrder);

	/**
	 * Return the total price, computed from the lines.
	 * This price is usually equals to {@link SaleOrder#exTaxTotal} but not
	 * in all cases.
	 * @param saleOrder
	 * @return  total price from the sale order lines
	 */
	public BigDecimal getTotalSaleOrderPrice(SaleOrder saleOrder);

	/**
	 * Enable edit order.
	 * 
	 * @param saleOrder
	 * @throws AxelorException
	 */
	void enableEditOrder(SaleOrder saleOrder) throws AxelorException;

    /**
     * Validate changes.
     * 
     * @param saleOrder
     * @param saleOrderView
     * @throws AxelorException
     */
    void validateChanges(SaleOrder saleOrder, SaleOrder saleOrderView) throws AxelorException;

    /**
     * Sort detail lines by sequence.
     * 
     * @param saleOrder
     */
    void sortSaleOrderLineList(SaleOrder saleOrder);
    
    public List<SaleOrderLine> removeSubLines(List<SaleOrderLine> lines);

}
