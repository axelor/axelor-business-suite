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
package com.axelor.apps.supplychain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Query;

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.AdvancePaymentAccount;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceServiceImpl;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.sale.db.AdvancePayment;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.supplychain.db.Subscription;
import com.axelor.apps.supplychain.db.repo.SubscriptionRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceGeneratorSupplyChain;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineGeneratorSupplyChain;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class SaleOrderInvoiceServiceImpl extends SaleOrderRepository implements SaleOrderInvoiceService {

	private static final Logger LOG = LoggerFactory.getLogger(SaleOrderInvoiceServiceImpl.class);

	private LocalDate today;

	protected GeneralService generalService;

	@Inject
	private SaleOrderLineRepository saleOrderLineRepo;

	@Inject
	public SaleOrderInvoiceServiceImpl(GeneralService generalService) {

		this.generalService = generalService;
		this.today = this.generalService.getTodayDate();

	}


	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Invoice generateInvoice(SaleOrder saleOrder) throws AxelorException  {

		Invoice invoice = this.createInvoice(saleOrder);

		Beans.get(InvoiceRepository.class).save(invoice);

		save(fillSaleOrder(saleOrder, invoice));

		return invoice;
	}

	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Invoice generateInvoice(SaleOrder saleOrder, List<SaleOrderLine> saleOrderLinesSelected) throws AxelorException  {

		Invoice invoice = this.createInvoice(saleOrder, saleOrderLinesSelected);

		Beans.get(InvoiceRepository.class).save(invoice);

		save(fillSaleOrder(saleOrder, invoice));

		return invoice;
	}

	public BigDecimal computeInTaxTotalInvoiced(Invoice invoice)  {

		BigDecimal total = BigDecimal.ZERO;

		if(invoice.getStatusSelect() == InvoiceRepository.STATUS_VENTILATED)  {
			if(invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)  {
				total = total.add(invoice.getInTaxTotal());
			}
			if(invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND)  {
				total = total.subtract(invoice.getInTaxTotal());
			}
		}

		if(invoice.getRefundInvoiceList() != null)  {
			for(Invoice refund : invoice.getRefundInvoiceList())  {
				total = total.add(this.computeInTaxTotalInvoiced(refund));
			}
		}

		return total;

	}


	@Override
	public SaleOrder fillSaleOrder(SaleOrder saleOrder, Invoice invoice)  {

		saleOrder.setOrderDate(this.today);

		// TODO Créer une séquence pour les commandes (Porter sur la facture ?)
//		saleOrder.setOrderNumber();

		return saleOrder;

	}


	@Override
	public Invoice createInvoice(SaleOrder saleOrder) throws AxelorException  {

		return createInvoice(saleOrder, saleOrder.getSaleOrderLineList());

	}

	@Override
	public Invoice createInvoice(SaleOrder saleOrder, List<SaleOrderLine> saleOrderLineList) throws AxelorException  {

		InvoiceGenerator invoiceGenerator = this.createInvoiceGenerator(saleOrder);

		Invoice invoice = invoiceGenerator.generate();

		invoiceGenerator.populate(invoice, this.createInvoiceLines(invoice, saleOrderLineList));

		//function Advance Payment
		
		this.fillAdvancePayment(invoice, saleOrder, saleOrderLineList);
		
		this.fillInLines(invoice);

		return invoice;

	}



	@Override
	public InvoiceGenerator createInvoiceGenerator(SaleOrder saleOrder) throws AxelorException  {

		if(saleOrder.getCurrency() == null)  {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.SO_INVOICE_6), saleOrder.getSaleOrderSeq()), IException.CONFIGURATION_ERROR);
		}

		InvoiceGenerator invoiceGenerator = new InvoiceGeneratorSupplyChain(InvoiceRepository.OPERATION_TYPE_CLIENT_SALE, saleOrder.getCompany(),saleOrder.getPaymentCondition(),
				saleOrder.getPaymentMode(), saleOrder.getMainInvoicingAddress(), saleOrder.getClientPartner(), saleOrder.getContactPartner(),
				saleOrder.getCurrency(), saleOrder.getPriceList(), saleOrder.getSaleOrderSeq(), saleOrder.getExternalReference(), saleOrder) {

			@Override
			public Invoice generate() throws AxelorException {

				return super.createInvoiceHeader();
			}
		};

		return invoiceGenerator;

	}



	// TODO ajouter tri sur les séquences
	@Override
	public List<InvoiceLine> createInvoiceLines(Invoice invoice, List<SaleOrderLine> saleOrderLineList) throws AxelorException  {

		List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();

		for(SaleOrderLine saleOrderLine : saleOrderLineList)  {

			//Lines of subscription type are invoiced directly from sale order line or from the subscription batch
			if (!ProductRepository.PRODUCT_TYPE_SUBSCRIPTABLE.equals(saleOrderLine.getProduct().getProductTypeSelect())){
				invoiceLineList.addAll(this.createInvoiceLine(invoice, saleOrderLine));

				saleOrderLine.setInvoiced(true);
			}
		}

		return invoiceLineList;

	}

	//Need to create this new method because createInvoiceLines doesn't take into account lines with subscriptable products anymore
	public List<InvoiceLine> createSubscriptionInvoiceLines(Invoice invoice, List<Subscription> subscriptionList) throws AxelorException  {

		List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();

		Integer sequence = 10;

		for(Subscription subscription : subscriptionList)  {

			invoiceLineList.addAll(this.createSubscriptionInvoiceLine(invoice, subscription, sequence));

			subscription.setInvoiced(true);

			sequence += 10;
		}

		return invoiceLineList;

	}

	@Override
	public List<InvoiceLine> createInvoiceLine(Invoice invoice, SaleOrderLine saleOrderLine) throws AxelorException  {

		Product product = saleOrderLine.getProduct();

		InvoiceLineGenerator invoiceLineGenerator = new InvoiceLineGeneratorSupplyChain(invoice, product, saleOrderLine.getProductName(),
				saleOrderLine.getDescription(), saleOrderLine.getQty(), saleOrderLine.getUnit(),
				saleOrderLine.getSequence(), false, saleOrderLine, null, null)  {

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

	public List<InvoiceLine> createSubscriptionInvoiceLine(Invoice invoice, Subscription subscription, Integer sequence) throws AxelorException  {

		SaleOrderLine saleOrderLine = subscription.getSaleOrderLine();
		Product product = saleOrderLine.getProduct();

		InvoiceLineGenerator invoiceLineGenerator = new InvoiceLineGeneratorSupplyChain(invoice, product, saleOrderLine.getProductName()+"("+saleOrderLine.getPeriodicity()+" "+I18n.get("month(s)")+")",
				saleOrderLine.getDescription(), saleOrderLine.getQty(), saleOrderLine.getUnit(),
				sequence, false, saleOrderLine, null, null, subscription)  {

			@Override
			public List<InvoiceLine> creates() throws AxelorException {

				InvoiceLine invoiceLine = this.createInvoiceLine();

				invoiceLine.setSubscriptionFromDate(subscription.getFromPeriodDate());
				invoiceLine.setSubscriptionToDate(subscription.getToPeriodDate());

				List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
				invoiceLines.add(invoiceLine);

				return invoiceLines;
			}
		};

		return invoiceLineGenerator.creates();
	}


	@Override
	public BigDecimal getAmountInvoiced(SaleOrder saleOrder){
		return this.getAmountInvoiced(saleOrder, null, false);
	}

	/**
	 * Return the remaining amount to invoice for the saleOrder in parameter
	 *
	 * @param saleOrder
	 *
	 * @param currentInvoiceId
	 * In the case of invoice ventilation or cancellation, the invoice status isn't modify in database but it will be integrated in calculation
	 * For ventilation, the invoice should be integrated in calculation
	 * For cancellation,  the invoice shouldn't be integrated in calculation
	 *
	 * @param includeInvoice
	 * To know if the invoice should be or not integrated in calculation
	 */
	@Override
	public BigDecimal getAmountInvoiced(SaleOrder saleOrder, Long currentInvoiceId, boolean includeInvoice){

		Query q = null;
		String query;
		query = "SELECT SUM(self.companyExTaxTotal)"
				+ " FROM InvoiceLine as self"
				+ " WHERE ( self.saleOrderLine.id IN (SELECT id FROM SaleOrderLine WHERE saleOrder.id = :saleOrderId)"
						+ " OR self.saleOrder.id = :saleOrderId )"
					+ " AND self.invoice.statusSelect = :statusVentilated";
		if (currentInvoiceId != null){
			if (includeInvoice){
				query += " OR self.invoice.id = :invoiceId";
			}else{
				query += " AND self.invoice.id <> :invoiceId";
			}
		}
		q = JPA.em().createQuery(query, BigDecimal.class);

		q.setParameter("saleOrderId", saleOrder.getId());
		q.setParameter("statusVentilated", InvoiceRepository.STATUS_VENTILATED);
		if (currentInvoiceId != null){
			q.setParameter("invoiceId", currentInvoiceId);
		}

		BigDecimal invoicedAmount = (BigDecimal) q.getSingleResult();
		if(invoicedAmount != null){
			if (!saleOrder.getCurrency().equals(saleOrder.getCompany().getCurrency())){
				BigDecimal rate = invoicedAmount.divide(saleOrder.getCompanyExTaxTotal(), 4, RoundingMode.HALF_UP);
				invoicedAmount = saleOrder.getExTaxTotal().multiply(rate);
			}
		}

		return invoicedAmount;

	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Invoice generateSubscriptionInvoice(List<Subscription> subscriptionList, SaleOrder saleOrder) throws AxelorException{

		if(subscriptionList == null || subscriptionList.isEmpty()){
			return null;
		}

		InvoiceGenerator invoiceGenerator = this.createInvoiceGenerator(saleOrder);

		Invoice invoice = invoiceGenerator.generate();

		invoiceGenerator.populate(invoice, this.createSubscriptionInvoiceLines(invoice, subscriptionList));

		this.fillInLines(invoice);

		invoice.setIsSubscription(true);

		Beans.get(InvoiceServiceImpl.class).save(invoice);

		return invoice;
	}

	@Override
	public void fillInLines(Invoice invoice){
		List<InvoiceLine> invoiceLineList = invoice.getInvoiceLineList();
		for (InvoiceLine invoiceLine : invoiceLineList) {
			invoiceLine.setSaleOrder(invoice.getSaleOrder());
		}
	}
	
	public void fillAdvancePayment(Invoice invoice, SaleOrder saleOrder, List<SaleOrderLine> saleOrderLineList)
	{
		if(!saleOrder.getAdvancePaymentList().isEmpty())
		{
			BigDecimal total = BigDecimal.ZERO;
			for (SaleOrderLine saleOrderLine : saleOrderLineList)
				total = total.add(saleOrderLine.getInTaxTotal());
			
			for (AdvancePayment advancePayment : saleOrder.getAdvancePaymentList()) 
			{
				if(advancePayment.getAmountRemainingToUse().compareTo(BigDecimal.ZERO) != 0 && total.compareTo(BigDecimal.ZERO) != 0)
				{
					if(total.max(advancePayment.getAmountRemainingToUse()) == total)
					{
						
						total = total.subtract(advancePayment.getAmountRemainingToUse());
						AdvancePaymentAccount advancePaymentAccount = null;
						advancePaymentAccount = createAdvancePaymentAccount(advancePayment, invoice, false, total);
						List <AdvancePaymentAccount> APAlist = new ArrayList<>();
						
						if (invoice.getAdvancePaymentList() != null)
							APAlist.addAll(invoice.getAdvancePaymentList());
						
						APAlist.add(advancePaymentAccount);
						invoice.setAdvancePaymentList(APAlist);
						advancePayment.setAmountRemainingToUse(BigDecimal.ZERO);
					}
					else
					{
						advancePayment.setAmountRemainingToUse(advancePayment.getAmountRemainingToUse().subtract(total));
						AdvancePaymentAccount advancePaymentAccount = null;
						advancePaymentAccount = createAdvancePaymentAccount(advancePayment, invoice, true, total);
						List <AdvancePaymentAccount> APAlist = new ArrayList<>();
						if (invoice.getAdvancePaymentList() != null)
						 APAlist.addAll(invoice.getAdvancePaymentList());
						APAlist.add(advancePaymentAccount);
						invoice.setAdvancePaymentList(APAlist);
						
						total = BigDecimal.ZERO;
					}
				}
				
			}
			
		
		}
		
		
		
	}

	public AdvancePaymentAccount createAdvancePaymentAccount(AdvancePayment advancePayment, Invoice invoice, boolean isFinished, BigDecimal total)
	{
		AdvancePaymentAccount advancePaymentAccount = new AdvancePaymentAccount();
		
		advancePaymentAccount.setAdvancePaymentDate(advancePayment.getAdvancePaymentDate());
		advancePaymentAccount.setCurrency(advancePayment.getCurrency());
		advancePaymentAccount.setMove(advancePayment.getMove());
		advancePaymentAccount.setInvoice(invoice);
		advancePaymentAccount.setPaymentMode(advancePayment.getPaymentMode());
		advancePaymentAccount.setTypeSelect(1);
		
		if(isFinished)
			advancePaymentAccount.setAmount(total);
		else
			advancePaymentAccount.setAmount(advancePayment.getAmountRemainingToUse());
		
		return advancePaymentAccount;
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Invoice generateSubcriptionInvoiceForSaleOrder(SaleOrder saleOrder) throws AxelorException{
		List<Subscription> subscriptionList = Beans.get(SubscriptionRepository.class).all().filter("self.invoicingDate <= ?1 AND self.saleOrderLine.saleOrder.id = ?2 AND self.invoiced = false",generalService.getTodayDate(),saleOrder.getId()).fetch();
		if(subscriptionList != null && !subscriptionList.isEmpty()){
			return this.generateSubscriptionInvoice(subscriptionList,saleOrder);
		}
		return null;
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Invoice generateSubcriptionInvoiceForSaleOrderAndListSubscrip(Long saleOrderId, List<Long> subscriptionIdList) throws AxelorException{
		List<Subscription> subscriptionList = Beans.get(SubscriptionRepository.class).all().filter("self.id IN (:subscriptionIds)").bind("subscriptionIds", subscriptionIdList).fetch();
		if(subscriptionList != null && !subscriptionList.isEmpty()){
			return this.generateSubscriptionInvoice(subscriptionList, this.find(saleOrderId));
		}
		return null;
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Invoice generateSubcriptionInvoiceForSaleOrderLine(SaleOrderLine saleOrderLine) throws AxelorException{
		List<Subscription> subscriptionList = Beans.get(SubscriptionRepository.class).all().filter("self.invoicingDate <= ?1 AND self.saleOrderLine.id = ?2 AND self.invoiced = false",generalService.getTodayDate(),saleOrderLine.getId()).fetch();
		if(subscriptionList != null && !subscriptionList.isEmpty()){
			return this.generateSubscriptionInvoice(subscriptionList, saleOrderLine.getSaleOrder());
		}
		return null;
	}
}


