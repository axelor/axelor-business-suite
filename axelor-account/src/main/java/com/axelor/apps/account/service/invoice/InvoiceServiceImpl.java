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
package com.axelor.apps.account.service.invoice;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.BudgetDistribution;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.report.IReport;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.factory.CancelFactory;
import com.axelor.apps.account.service.invoice.factory.ValidateFactory;
import com.axelor.apps.account.service.invoice.factory.VentilateFactory;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.invoice.RefundInvoice;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentToolService;
import com.axelor.apps.base.db.Alarm;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.repo.BankDetailsRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.alarm.AlarmEngineService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.tool.StringTool;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

/**
 * InvoiceService est une classe implémentant l'ensemble des services de
 * facturation.
 * 
 */
public class InvoiceServiceImpl extends InvoiceRepository implements InvoiceService  {
	
	@Inject
	protected PartnerService partnerService;
	
	private final Logger log = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );
	
	protected ValidateFactory validateFactory;
	protected VentilateFactory ventilateFactory;
	protected CancelFactory cancelFactory;
	protected AlarmEngineService<Invoice> alarmEngineService;
	protected InvoiceRepository invoiceRepo;
	protected AppAccountService appAccountService;

	@Inject
	public InvoiceServiceImpl(ValidateFactory validateFactory, VentilateFactory ventilateFactory,
			CancelFactory cancelFactory, AlarmEngineService<Invoice> alarmEngineService, InvoiceRepository invoiceRepo,
			AppAccountService appAccountService) {

		this.validateFactory = validateFactory;
		this.ventilateFactory = ventilateFactory;
		this.cancelFactory = cancelFactory;
		this.alarmEngineService = alarmEngineService;
		this.invoiceRepo = invoiceRepo;
		this.appAccountService = appAccountService;
	}
	
// WKF
	
	public Map<Invoice, List<Alarm>> getAlarms(Invoice... invoices){
		return alarmEngineService.get( Invoice.class, invoices );
	}
	
	
	/**
	 * Lever l'ensemble des alarmes d'une facture.
	 * 
	 * @param invoice
	 * 			Une facture.
	 * 
	 * @throws Exception 
	 */
	public void raisingAlarms(Invoice invoice, String alarmEngineCode) {

		Alarm alarm = alarmEngineService.get(alarmEngineCode, invoice, true);
		
		if (alarm != null){
			
			alarm.setInvoice(invoice);
			
		}

	}

	
	
	/**
	 * Fonction permettant de calculer l'intégralité d'une facture :
	 * <ul>
	 * 	<li>Détermine les taxes;</li>
	 * 	<li>Détermine la TVA;</li>
	 * 	<li>Détermine les totaux.</li>
	 * </ul>
	 * (Transaction)
	 * 
	 * @param invoice
	 * 		Une facture.
	 * 
	 * @throws AxelorException
	 */
	public Invoice compute(final Invoice invoice) throws AxelorException {

		log.debug("Calcul de la facture");
		
		InvoiceGenerator invoiceGenerator = new InvoiceGenerator() {
			
			@Override
			public Invoice generate() throws AxelorException {

				List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
				invoiceLines.addAll(invoice.getInvoiceLineList());
				
				populate(invoice, invoiceLines);
				
				return invoice;
			}
			
		};
		
		Invoice invoice1 = invoiceGenerator.generate();
		invoice1.setAdvancePaymentInvoiceSet(this.getDefaultAdvancePaymentInvoice(invoice1));
		return invoice1;
		
	}
	
	
	/**
	 * Validation d'une facture.
	 * (Transaction)
	 * 
	 * @param invoice
	 * 		Une facture.
	 * 
	 * @throws AxelorException
	 */
	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void validate(Invoice invoice, boolean compute) throws AxelorException {

		log.debug("Validation de la facture");

		if (compute) {
			compute(invoice);
		}

		validateFactory.getValidator(invoice).process( );
		if(appAccountService.isApp("budget") && !appAccountService.getAppBudget().getManageMultiBudget()){
			this.generateBudgetDistribution(invoice);
		}
		//if the invoice is an advance payment invoice, we also "ventilate" it
		//without creating the move
		if (invoice.getOperationSubTypeSelect()
				== InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE) {
		    ventilate(invoice);
		}

	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void validate(Invoice invoice) throws AxelorException {
		validate(invoice, false);
	}

	@Override
	public void generateBudgetDistribution(Invoice invoice){
		if(invoice.getInvoiceLineList() != null){
			for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {
				if(invoiceLine.getBudget() != null && invoiceLine.getBudgetDistributionList().isEmpty()){
					BudgetDistribution budgetDistribution = new BudgetDistribution();
					budgetDistribution.setBudget(invoiceLine.getBudget());
					budgetDistribution.setAmount(invoiceLine.getExTaxTotal());
					invoiceLine.addBudgetDistributionListItem(budgetDistribution);
				}
			}
		}
	}
	
	/**
	 * Ventilation comptable d'une facture.
	 * (Transaction)
	 * 
	 * @param invoice
	 * 		Une facture.
	 * 
	 * @throws AxelorException
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void ventilate( Invoice invoice ) throws AxelorException {
		for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {
			if (invoiceLine.getTypeSelect() != InvoiceLineRepository.TYPE_TITLE && invoiceLine.getAccount() == null) {
				throw new AxelorException(invoice, IException.MISSING_FIELD, I18n.get(IExceptionMessage.VENTILATE_STATE_6), invoiceLine.getProductName());
			}
		}

		log.debug("Ventilation de la facture {}", invoice.getInvoiceId());
		
		ventilateFactory.getVentilator(invoice).process();
		
		invoiceRepo.save(invoice);
		
		if(appAccountService.getAppAccount().getPrintReportOnVentilation()){
			printInvoice(invoice, true);
		}
		
	}

	/**
	 * Annuler une facture.
	 * (Transaction)
	 * 
	 * @param invoice
	 * 		Une facture.
	 * 
	 * @throws AxelorException
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void cancel(Invoice invoice) throws AxelorException {

		log.debug("Annulation de la facture {}", invoice.getInvoiceId());
		
		cancelFactory.getCanceller(invoice).process();
		
		invoiceRepo.save(invoice);
		
	}
	

	
	/**
	 * Procédure permettant d'impacter la case à cocher "Passage à l'huissier" sur l'écriture de facture.
	 * (Transaction)
	 * 
	 * @param invoice
	 * 		Une facture
	 */
	@Transactional
	public void usherProcess(Invoice invoice)  {
		Move move = invoice.getMove();
		
		if(move != null)  {
			if(invoice.getUsherPassageOk())  {
				for(MoveLine moveLine : move.getMoveLineList())  {
					moveLine.setUsherPassageOk(true);
				}
			}
			else  {
				for(MoveLine moveLine : move.getMoveLineList())  {
					moveLine.setUsherPassageOk(false);
				}
			}
			
			Beans.get(MoveRepository.class).save(move);
		}
	}
	
	/**
	 * Créer un avoir.
	 * <p>
	 * Un avoir est une facture "inversée". Tout le montant sont opposés à la facture originale.
	 * </p>
	 * 
	 * @param invoice
	 * 
	 * @return
	 * @throws AxelorException 
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Invoice createRefund(Invoice invoice) throws AxelorException {
		
		log.debug("Créer un avoir pour la facture {}", new Object[] { invoice.getInvoiceId() });
		Invoice refund = new RefundInvoice(invoice).generate();
		invoice.addRefundInvoiceListItem( refund );
		invoiceRepo.save(invoice);
		
		return refund;
		
	}
	
    @Override
	public void setDraftSequence(Invoice invoice) throws AxelorException {
		
		if (invoice.getId() != null && Strings.isNullOrEmpty(invoice.getInvoiceId()))  {
			invoice.setInvoiceId(Beans.get(SequenceService.class).getDraftSequenceNumber(invoice));
		}
		
	}
	

	@Override
	@Transactional
	public Invoice mergeInvoice(List<Invoice> invoiceList, Company company, Currency currency,
			Partner partner, Partner contactPartner, PriceList priceList,
			PaymentMode paymentMode, PaymentCondition paymentCondition)throws AxelorException {
		String numSeq = "";
		String externalRef = "";
		for (Invoice invoiceLocal : invoiceList) {
			if (!numSeq.isEmpty()){
				numSeq += "-";
			}
			if (invoiceLocal.getInternalReference() != null){
				numSeq += invoiceLocal.getInternalReference();
			}

			if (!externalRef.isEmpty()){
				externalRef += "|";
			}
			if (invoiceLocal.getExternalReference() != null){
				externalRef += invoiceLocal.getExternalReference();
			}
		}
		
		InvoiceGenerator invoiceGenerator = new InvoiceGenerator(InvoiceRepository.OPERATION_TYPE_CLIENT_SALE, company, paymentCondition,
				paymentMode, partnerService.getInvoicingAddress(partner), partner, contactPartner,
				currency, priceList, numSeq, externalRef, null, company.getDefaultBankDetails()){
		
					@Override
					public Invoice generate() throws AxelorException {
						
						return super.createInvoiceHeader();
					}
		};
		Invoice invoiceMerged = invoiceGenerator.generate();
		List<InvoiceLine> invoiceLines = this.getInvoiceLinesFromInvoiceList(invoiceList);
		invoiceGenerator.populate(invoiceMerged, invoiceLines);
		this.setInvoiceForInvoiceLines(invoiceLines, invoiceMerged);
		Beans.get(InvoiceRepository.class).save(invoiceMerged);
		deleteOldInvoices(invoiceList);
		return invoiceMerged;
	}
	
	@Override
	public void deleteOldInvoices(List<Invoice> invoiceList) {
		for(Invoice invoicetemp : invoiceList) {
			invoiceRepo.remove(invoicetemp);
		}
	}


	@Override
	public List<InvoiceLine>  getInvoiceLinesFromInvoiceList(List<Invoice> invoiceList) {
		List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
		for(Invoice invoice : invoiceList)  {
			int countLine = 1;
			for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {
				invoiceLine.setSequence(countLine * 10);
				invoiceLines.add(invoiceLine);
				countLine++;
			}
		}
		return invoiceLines;
	}
	
	@Override
	public void setInvoiceForInvoiceLines(List<InvoiceLine> invoiceLines, Invoice invoice) {
		for(InvoiceLine invoiceLine : invoiceLines)  {
			invoiceLine.setInvoice(invoice);
		}
	}
	
	protected String getDefaultPrintingLocale(Invoice invoice, Company company) {
		String locale = null;
		
		if(invoice != null && invoice.getPartner() != null) {
			locale = invoice.getPartner().getLanguageSelect();
		}
		
		if(locale == null && company != null && company.getPrintingSettings() != null) {
			locale = company.getPrintingSettings().getLanguageSelect();
		}
		
		User user = AuthUtils.getUser();
		if(user != null && user.getLanguage() != null) {
			locale = user.getLanguage();
		}
		
		return locale == null ? "en" : locale;
	}
	
	@Override
	public ReportSettings printInvoice(Invoice invoice, boolean toAttach) throws AxelorException {
		String locale = getDefaultPrintingLocale(invoice, invoice.getCompany());
		
		String title = I18n.get("Invoice");
		if(invoice.getInvoiceId() != null) { title += " " + invoice.getInvoiceId(); }
		
		ReportSettings reportSetting = ReportFactory.createReport(IReport.INVOICE, title + " - ${date}");
		if (toAttach) { reportSetting.toAttach(invoice); }
		
		return reportSetting.addParam("InvoiceId", invoice.getId().toString())
				.addParam("Locale", locale)
				.addParam("InvoicesCopy", invoice.getInvoicesCopySelect())
				.generate();
	}
	
	@Override
	public ReportSettings printInvoices(List<Long> ids) throws AxelorException {
		User user = AuthUtils.getUser();
		String locale = getDefaultPrintingLocale(null, user == null ? null : user.getActiveCompany());
		
		String title = I18n.get("Invoices");
		
		ReportSettings reportSetting = ReportFactory.createReport(IReport.INVOICE, title + " - ${date}");	
		return reportSetting.addParam("InvoiceId", Joiner.on(",").join(ids))
				.addParam("Locale", locale)
				.addParam("InvoicesCopy", 0)
				.generate();
	}
	
	

	/**
	 * Méthode permettant de récupérer la facture depuis une ligne d'écriture de facture ou une ligne d'écriture de rejet de facture
	 * @param moveLine
	 * 			Une ligne d'écriture de facture ou une ligne d'écriture de rejet de facture
	 * @return
	 * 			La facture trouvée
	 */
	public Invoice getInvoice(MoveLine moveLine)  {
		Invoice invoice = null;
		if(moveLine.getMove().getRejectOk())  {
			invoice = moveLine.getInvoiceReject();
		}
		else  {
			invoice = moveLine.getMove().getInvoice();
		}
		return invoice;
	}


	@Override
	public String createAdvancePaymentInvoiceSetDomain(Invoice invoice) throws AxelorException {
		Set<Invoice> invoices = getDefaultAdvancePaymentInvoice(invoice);
		String domain = "self.id IN (" +
				StringTool.getIdListString(invoices)
				+ ")";

		return domain;
	}

	@Override
	public Set<Invoice> getDefaultAdvancePaymentInvoice(Invoice invoice) throws AxelorException {
	    Set<Invoice> advancePaymentInvoices;

	    Company company = invoice.getCompany();
		Currency currency = invoice.getCurrency();
		Partner partner = invoice.getPartner();
		if (company == null
				|| currency == null
				|| partner == null) {
			return new HashSet<>();
		}
		String filter = writeGeneralFilterForAdvancePayment();
		filter += " AND self.partner = :_partner AND self.currency = :_currency";

		advancePaymentInvoices = new HashSet<>(
				Beans.get(InvoiceRepository.class).all().filter(filter)
						.bind("_status", InvoiceRepository.STATUS_VALIDATED)
						.bind("_operationSubType",
								InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE)
						.bind("_partner", partner)
						.bind("_currency", currency)
						.fetch()
		);

		filterAdvancePaymentInvoice(invoice, advancePaymentInvoices);
		return advancePaymentInvoices;
	}

	@Override
	public void filterAdvancePaymentInvoice(Invoice invoice,
											Set<Invoice> advancePaymentInvoices) throws AxelorException {
		Iterator<Invoice> advPaymentInvoiceIt = advancePaymentInvoices.iterator();
		while (advPaymentInvoiceIt.hasNext()) {
			Invoice candidateAdvancePayment = advPaymentInvoiceIt.next();
			if (removeBecauseOfTotalAmount(invoice, candidateAdvancePayment)
					|| removeBecauseOfAmountRemaining(invoice, candidateAdvancePayment)) {
				advPaymentInvoiceIt.remove();
			}
		}
	}

	protected boolean removeBecauseOfTotalAmount(Invoice invoice,
												 Invoice candidateAdvancePayment) throws AxelorException {
		if (Beans.get(AccountConfigService.class).getAccountConfig(invoice.getCompany())
				.getGenerateMoveForInvoicePayment()) {
			return false;
		}
		BigDecimal invoiceTotal = invoice.getInTaxTotal();
		List<InvoicePayment> invoicePayments = candidateAdvancePayment
				.getInvoicePaymentList();
		if (invoicePayments == null) {
		    return false;
		}
		BigDecimal totalAmount = invoicePayments.stream()
				.map(InvoicePayment::getAmount)
				.reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
		return totalAmount.compareTo(invoiceTotal) > 0;
	}

	protected boolean removeBecauseOfAmountRemaining(Invoice invoice,
													 Invoice candidateAdvancePayment) throws AxelorException {
	    List<InvoicePayment> invoicePayments = candidateAdvancePayment.getInvoicePaymentList();
	    //no payment : remove the candidate invoice
	    if (invoicePayments == null || invoicePayments.isEmpty()) {
	    	return true;
		}

		// if there is no move generated, we simply check if the payment was
		// imputed
		if (!Beans.get(AccountConfigService.class)
				.getAccountConfig(invoice.getCompany())
				.getGenerateMoveForInvoicePayment()) {
			for (InvoicePayment invoicePayment : invoicePayments) {
				if (invoicePayment.getImputedBy() == null) {
					return false;
				}
			}
			return true;
        }

        // else we check the remaining amount
		for (InvoicePayment invoicePayment : invoicePayments) {
	    	Move move = invoicePayment.getMove();
	    	if (move == null) {
	    		continue;
			}
			List<MoveLine> moveLineList = move.getMoveLineList();
	    	if (moveLineList == null || moveLineList.isEmpty()) {
	    		continue;
			}
			for (MoveLine moveLine : moveLineList) {
	    		BigDecimal amountRemaining = moveLine.getAmountRemaining();
	    		if (amountRemaining != null
						&& amountRemaining.compareTo(BigDecimal.ZERO) > 0) {
	    			return false;
				}
			}
		}
		return true;
	}

	@Override
	public List<MoveLine> getMoveLinesFromAdvancePayments(Invoice invoice) throws AxelorException {
		if (Beans.get(AppAccountService.class).getAppAccount().getManageAdvancePaymentInvoice()) {
			return getMoveLinesFromInvoiceAdvancePayments(invoice);
		} else {
			return getMoveLinesFromSOAdvancePayments(invoice);
		}
	}

	@Override
	public List<MoveLine> getMoveLinesFromInvoiceAdvancePayments(Invoice invoice) {
		List<MoveLine> advancePaymentMoveLines = new ArrayList<>();

		Set<Invoice> advancePayments = invoice.getAdvancePaymentInvoiceSet();
		List<InvoicePayment> invoicePayments;
		if (advancePayments == null || advancePayments.isEmpty()) {
			return advancePaymentMoveLines;
		}
		InvoicePaymentToolService invoicePaymentToolService =
				Beans.get(InvoicePaymentToolService.class);
		for (Invoice advancePayment : advancePayments) {
			invoicePayments = advancePayment.getInvoicePaymentList();
			List<MoveLine> creditMoveLines = invoicePaymentToolService
					.getCreditMoveLinesFromPayments(invoicePayments);
			advancePaymentMoveLines.addAll(creditMoveLines);
		}
		return advancePaymentMoveLines;
	}

	@Override
	public List<MoveLine> getMoveLinesFromSOAdvancePayments(Invoice invoice) {
		return new ArrayList<>();
	}

	protected String writeGeneralFilterForAdvancePayment() {
		return "self.statusSelect = :_status"
				+ " AND self.operationSubTypeSelect = :_operationSubType";
	}

	@Override
    public BankDetails getBankDetails(Invoice invoice) throws AxelorException {
        BankDetails bankDetails;

        if (invoice.getSchedulePaymentOk() && invoice.getPaymentSchedule() != null) {
            bankDetails = invoice.getPaymentSchedule().getBankDetails();
            if (bankDetails != null) {
                return bankDetails;
            }
        }

        bankDetails = invoice.getBankDetails();

        if (bankDetails != null) {
            return bankDetails;
        }

        Partner partner = invoice.getPartner();
        Preconditions.checkNotNull(partner);
        bankDetails = Beans.get(BankDetailsRepository.class).findDefaultByPartner(partner);

        if (bankDetails != null) {
            return bankDetails;
        }

        throw new AxelorException(invoice, IException.MISSING_FIELD,
                I18n.get(IExceptionMessage.PARTNER_BANK_DETAILS_MISSING), partner.getName());
    }

	public int getPurchaseTypeOrSaleType(Invoice invoice) {
		if (invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_CLIENT_SALE
				|| invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND) {
			return PriceListRepository.TYPE_SALE;
		} else if (invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE
				|| invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_SUPPLIER_REFUND) {
			return PriceListRepository.TYPE_PURCHASE;
		}
		return -1;
	}

}




