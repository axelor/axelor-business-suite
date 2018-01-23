/**
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
package com.axelor.apps.hr.service.expense;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AccountManagement;
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.AnalyticAccount;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.AccountManagementAccountService;
import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.repo.BankOrderRepository;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.IPriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Sequence;
import com.axelor.apps.base.db.repo.AppAccountRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.PeriodService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.hr.db.EmployeeAdvanceUsage;
import com.axelor.apps.hr.db.EmployeeVehicle;
import com.axelor.apps.hr.db.Expense;
import com.axelor.apps.hr.db.ExpenseLine;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.KilometricAllowParam;
import com.axelor.apps.hr.db.repo.ExpenseLineRepository;
import com.axelor.apps.hr.db.repo.ExpenseRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.EmployeeAdvanceService;
import com.axelor.apps.hr.service.KilometricService;
import com.axelor.apps.hr.service.bankorder.BankOrderCreateServiceHr;
import com.axelor.apps.hr.service.config.AccountConfigHRService;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.apache.commons.codec.binary.Base64;

import javax.mail.MessagingException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExpenseServiceImpl implements ExpenseService {

	protected MoveService moveService;
	protected ExpenseRepository expenseRepository;
	protected ExpenseLineRepository expenseLineRepository;
	protected MoveLineService moveLineService;
	protected AccountManagementAccountService accountManagementService;
	protected AppAccountService appAccountService;
	protected AccountConfigHRService accountConfigService;
	protected AccountingSituationService accountingSituationService;
	protected AnalyticMoveLineService analyticMoveLineService;
	protected HRConfigService hrConfigService;
	protected TemplateMessageService templateMessageService;

	@Inject
	public ExpenseServiceImpl(MoveService moveService, ExpenseRepository expenseRepository, ExpenseLineRepository expenseLineRepository, MoveLineService moveLineService,
			AccountManagementAccountService accountManagementService, AppAccountService appAccountService,
			AccountConfigHRService accountConfigService, AccountingSituationService accountingSituationService, AnalyticMoveLineService analyticMoveLineService,
			HRConfigService  hrConfigService, TemplateMessageService  templateMessageService)  {

		this.moveService = moveService;
		this.expenseRepository = expenseRepository;
		this.expenseLineRepository = expenseLineRepository;
		this.moveLineService = moveLineService;
		this.accountManagementService = accountManagementService;
		this.appAccountService = appAccountService;
		this.accountConfigService = accountConfigService;
		this.accountingSituationService = accountingSituationService;
		this.analyticMoveLineService = analyticMoveLineService;
		this.hrConfigService = hrConfigService;
		this.templateMessageService = templateMessageService;

	}

	public ExpenseLine computeAnalyticDistribution(ExpenseLine expenseLine) throws AxelorException{

		if(appAccountService.getAppAccount().getAnalyticDistributionTypeSelect() == AppAccountRepository.DISTRIBUTION_TYPE_FREE)  {
			return expenseLine;
		}

		Expense expense = expenseLine.getExpense();
		List<AnalyticMoveLine> analyticMoveLineList = expenseLine.getAnalyticMoveLineList();
		if ((analyticMoveLineList == null || analyticMoveLineList.isEmpty())) {
			analyticMoveLineList = analyticMoveLineService.generateLines(expenseLine.getUser().getPartner(), expenseLine.getExpenseProduct(), expense.getCompany(), expenseLine.getUntaxedAmount());
			expenseLine.setAnalyticMoveLineList(analyticMoveLineList);
		}
		if (analyticMoveLineList != null) {
			for (AnalyticMoveLine analyticMoveLine : analyticMoveLineList) {
				this.updateAnalyticMoveLine(analyticMoveLine, expenseLine);
			}
		}
		return expenseLine;
	}

	public void updateAnalyticMoveLine(AnalyticMoveLine analyticMoveLine, ExpenseLine expenseLine) {

		analyticMoveLine.setExpenseLine(expenseLine);
		analyticMoveLine.setAmount(
				analyticMoveLine.getPercentage().multiply(analyticMoveLine.getExpenseLine().getUntaxedAmount()
				.divide(new BigDecimal(100), 2, RoundingMode.HALF_UP)));
		analyticMoveLine.setDate(appAccountService.getTodayDate());
		analyticMoveLine.setTypeSelect(AnalyticMoveLineRepository.STATUS_FORECAST_INVOICE);

	}

	public ExpenseLine createAnalyticDistributionWithTemplate(ExpenseLine expenseLine) throws AxelorException {
		List<AnalyticMoveLine> analyticMoveLineList = null;
		analyticMoveLineList = analyticMoveLineService.generateLinesWithTemplate(expenseLine.getAnalyticDistributionTemplate(), expenseLine.getUntaxedAmount());
		if (analyticMoveLineList != null) {
			for (AnalyticMoveLine analyticMoveLine : analyticMoveLineList) {
				analyticMoveLine.setExpenseLine(expenseLine);
			}
		}
		expenseLine.setAnalyticMoveLineList(analyticMoveLineList);
		return expenseLine;
	}


	public Expense compute(Expense expense) {

		BigDecimal exTaxTotal = BigDecimal.ZERO;
		BigDecimal taxTotal = BigDecimal.ZERO;
		BigDecimal inTaxTotal = BigDecimal.ZERO;
		List<ExpenseLine> expenseLineList = expense.getExpenseLineList();
		List<ExpenseLine> kilometricExpenseLineList = expense.getKilometricExpenseLineList();

		if (expenseLineList != null) {
			for (ExpenseLine expenseLine : expenseLineList) {
				//if the distance in expense line is not null or zero, the expenseline is a kilometricExpenseLine
				//so we ignore it, it will be taken into account in the next loop.
				if (expenseLine.getDistance() == null || expenseLine.getDistance().compareTo(BigDecimal.ZERO) == 0) {
					exTaxTotal = exTaxTotal.add(expenseLine.getUntaxedAmount());
					taxTotal = taxTotal.add(expenseLine.getTotalTax());
					inTaxTotal = inTaxTotal.add(expenseLine.getTotalAmount());
				}
			}
		}
		if (kilometricExpenseLineList != null) {
			for (ExpenseLine kilometricExpenseLine : kilometricExpenseLineList) {
				if (kilometricExpenseLine.getUntaxedAmount() != null) {
					exTaxTotal = exTaxTotal.add(kilometricExpenseLine.getUntaxedAmount());
				}
				if (kilometricExpenseLine.getTotalTax() != null) {
					taxTotal = taxTotal.add(kilometricExpenseLine.getTotalTax());
				}
				if (kilometricExpenseLine.getTotalAmount() != null) { 
					inTaxTotal = inTaxTotal.add(kilometricExpenseLine.getTotalAmount());
				}
			}
		}
		expense.setExTaxTotal(exTaxTotal);
		expense.setTaxTotal(taxTotal);
		expense.setInTaxTotal(inTaxTotal);
		return expense;
	}

	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void confirm(Expense expense) throws AxelorException {

		expense.setStatusSelect(ExpenseRepository.STATUS_CONFIRMED);
		expense.setSentDate(appAccountService.getTodayDate());
		expenseRepository.save(expense);

	}


	public Message sendConfirmationEmail(Expense expense) throws AxelorException, ClassNotFoundException, InstantiationException, IllegalAccessException, MessagingException, IOException {

		HRConfig hrConfig = hrConfigService.getHRConfig(expense.getCompany());

		if (hrConfig.getExpenseMailNotification()) {

			return templateMessageService.generateAndSendMessage(expense, hrConfigService.getSentExpenseTemplate(hrConfig));

		}

		return null;

	}


	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void validate(Expense expense) throws AxelorException {

		if (expense.getUser().getEmployee() == null) {
			throw new AxelorException(expense,IException.CONFIGURATION_ERROR, I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE), expense.getUser().getFullName());
		}

		if (expense.getPeriod() == null) {
			throw new AxelorException(expense,IException.MISSING_FIELD, I18n.get(IExceptionMessage.EXPENSE_MISSING_PERIOD));
		}

		if (expense.getKilometricExpenseLineList() != null && !expense.getKilometricExpenseLineList().isEmpty()) {
			for (ExpenseLine line : expense.getKilometricExpenseLineList()) {
				BigDecimal amount = Beans.get(KilometricService.class).computeKilometricExpense(line, expense.getUser().getEmployee());
				line.setTotalAmount(amount);
				line.setUntaxedAmount(amount);

				Beans.get(KilometricService.class).updateKilometricLog(line, expense.getUser().getEmployee());
			}
			compute(expense);
		}

		Beans.get(EmployeeAdvanceService.class).fillExpenseWithAdvances(expense);
		expense.setStatusSelect(ExpenseRepository.STATUS_VALIDATED);
		expense.setValidatedBy(AuthUtils.getUser());
		expense.setValidationDate(appAccountService.getTodayDate());

		PaymentMode paymentMode = expense.getUser().getPartner().getOutPaymentMode();
		expense.setPaymentMode(paymentMode);

		expenseRepository.save(expense);
	}


	public Message sendValidationEmail(Expense expense) throws AxelorException, ClassNotFoundException, InstantiationException, IllegalAccessException, MessagingException, IOException {

		HRConfig hrConfig = hrConfigService.getHRConfig(expense.getCompany());

		if (hrConfig.getExpenseMailNotification()) {

			return templateMessageService.generateAndSendMessage(expense, hrConfigService.getValidatedExpenseTemplate(hrConfig));

		}

		return null;

	}

	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void refuse(Expense expense) throws AxelorException {

		expense.setStatusSelect(ExpenseRepository.STATUS_REFUSED);
		expense.setRefusedBy(AuthUtils.getUser());
		expense.setRefusalDate(appAccountService.getTodayDate());
		expenseRepository.save(expense);

	}

	public Message sendRefusalEmail(Expense expense) throws AxelorException, ClassNotFoundException, InstantiationException, IllegalAccessException, MessagingException, IOException {

		HRConfig hrConfig = hrConfigService.getHRConfig(expense.getCompany());

		if (hrConfig.getExpenseMailNotification()) {

			return templateMessageService.generateAndSendMessage(expense, hrConfigService.getRefusedExpenseTemplate(hrConfig));

		}

		return null;

	}


	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Move ventilate(Expense expense) throws AxelorException {

	    Move move = null;
		setExpenseSeq(expense);

		if (expense.getInTaxTotal().compareTo(BigDecimal.ZERO) != 0) {
			move = createAndSetMove(expense);
		}
		expense.setVentilated(true);
		expenseRepository.save(expense);

		return move;
	}

	protected Move createAndSetMove(Expense expense) throws AxelorException {
		LocalDate moveDate = expense.getMoveDate();
		if(moveDate == null){
			moveDate = appAccountService.getTodayDate();
			expense.setMoveDate(moveDate);
		}

		Account account = null;
		AccountConfig accountConfig = accountConfigService.getAccountConfig(expense.getCompany());

		if (expense.getUser().getPartner() == null) {
			throw new AxelorException(expense, IException.CONFIGURATION_ERROR, I18n.get(com.axelor.apps.account.exception.IExceptionMessage.USER_PARTNER), expense.getUser().getName());
		}

		Move move = moveService.getMoveCreateService().createMove(accountConfigService.getExpenseJournal(accountConfig), accountConfig.getCompany(), null, expense.getUser().getPartner(), moveDate, expense.getUser().getPartner().getInPaymentMode(), MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC);

		List<MoveLine> moveLines = new ArrayList<>();

		AccountManagement accountManagement = null;
		Set<AnalyticAccount> analyticAccounts = new HashSet<>();
		BigDecimal exTaxTotal = null;

		int moveLineId = 1;
		int expenseLineId = 1;
		AccountingSituation accSituation =  accountingSituationService.getAccountingSituation(expense.getUser().getPartner(), expense.getCompany());
		Account employeeAccount = null;
		if (accSituation != null) {
			employeeAccount = accSituation.getEmployeeAccount();
		}
		if (employeeAccount == null) {
			employeeAccount = accountConfigService.getExpenseEmployeeAccount(accountConfig);
		}
		moveLines.add(moveLineService.createMoveLine(move, expense.getUser().getPartner(), employeeAccount, expense.getInTaxTotal(), false, moveDate, moveDate, moveLineId++, ""));

		for (ExpenseLine expenseLine : expense.getExpenseLineList()) {
			analyticAccounts.clear();
			Product product = expenseLine.getExpenseProduct();
			accountManagement = accountManagementService.getAccountManagement(product, expense.getCompany());

			account = accountManagementService.getProductAccount(accountManagement, true);

			if (account == null) {
				throw new AxelorException(expense, IException.CONFIGURATION_ERROR, I18n.get(com.axelor.apps.account.exception.IExceptionMessage.MOVE_LINE_4), expenseLineId, expense.getCompany().getName());
			}

			exTaxTotal = expenseLine.getUntaxedAmount();
			MoveLine moveLine = moveLineService.createMoveLine(move, expense.getUser().getPartner(), account, exTaxTotal, true, moveDate, moveDate, moveLineId++, "");
			for (AnalyticMoveLine analyticDistributionLineIt : expenseLine.getAnalyticMoveLineList()) {
				AnalyticMoveLine analyticDistributionLine = Beans.get(AnalyticMoveLineRepository.class).copy(analyticDistributionLineIt, false);
				analyticDistributionLine.setExpenseLine(null);
				moveLine.addAnalyticMoveLineListItem(analyticDistributionLine);
			}
			moveLines.add(moveLine);
			expenseLineId++;

		}

		moveLineService.consolidateMoveLines(moveLines);
		account = accountConfigService.getExpenseTaxAccount(accountConfig);
		BigDecimal taxTotal = BigDecimal.ZERO;
		for (ExpenseLine expenseLine : expense.getExpenseLineList()) {
			exTaxTotal = expenseLine.getTotalTax();
			taxTotal = taxTotal.add(exTaxTotal);
		}

		if (taxTotal.compareTo(BigDecimal.ZERO) != 0) {
			MoveLine moveLine = moveLineService.createMoveLine(move, expense.getUser().getPartner(), account, taxTotal, true, moveDate, moveDate, moveLineId++, "");
			moveLines.add(moveLine);
		}

		move.getMoveLineList().addAll(moveLines);

		moveService.getMoveValidateService().validateMove(move);

		expense.setMove(move);
		return move;
	}

	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void cancel(Expense expense) throws AxelorException {
		Move move = expense.getMove();
		if (move == null) {
			expense.setStatusSelect(ExpenseRepository.STATUS_CANCELED);
			expenseRepository.save(expense);
			return;
		}
		Beans.get(PeriodService.class).testOpenPeriod(move.getPeriod());
		try {
			Beans.get(MoveRepository.class).remove(move);
			expense.setMove(null);
			expense.setVentilated(false);
			expense.setStatusSelect(ExpenseRepository.STATUS_CANCELED);
		} catch (Exception e) {
			throw new AxelorException(e, expense, IException.CONFIGURATION_ERROR, I18n.get(com.axelor.apps.hr.exception.IExceptionMessage.EXPENSE_CANCEL_MOVE));
		}

		expenseRepository.save(expense);
	}

	public Message sendCancellationEmail(Expense expense) throws AxelorException, ClassNotFoundException, InstantiationException, IllegalAccessException, MessagingException, IOException {

		HRConfig hrConfig = hrConfigService.getHRConfig(expense.getCompany());

		if (hrConfig.getTimesheetMailNotification()) {

			return templateMessageService.generateAndSendMessage(expense, hrConfigService.getCanceledExpenseTemplate(hrConfig));

		}

		return null;

	}

	@Transactional(rollbackOn = { AxelorException.class, Exception.class })
	public void addPayment(Expense expense, BankDetails bankDetails) throws AxelorException {

		expense.setPaymentDate(appAccountService.getTodayDate());

		PaymentMode paymentMode = expense.getPaymentMode();

		if (paymentMode == null) {
			paymentMode = expense.getUser().getPartner().getOutPaymentMode();

			if (paymentMode == null) {
				throw new AxelorException(expense, IException.MISSING_FIELD, I18n.get(IExceptionMessage.EXPENSE_MISSING_PAYMENT_MODE));
			}
			expense.setPaymentMode(paymentMode);
		}

		if (paymentMode.getGenerateBankOrder()) {
			BankOrder bankOrder = Beans.get(BankOrderCreateServiceHr.class).createBankOrder(expense, bankDetails);
			expense.setBankOrder(bankOrder);
			bankOrder = Beans.get(BankOrderRepository.class).save(bankOrder);
		}

		if (paymentMode.getAutomaticTransmission()) {
			expense.setPaymentStatusSelect(InvoicePaymentRepository.STATUS_PENDING);
		} else {
			expense.setPaymentStatusSelect(InvoicePaymentRepository.STATUS_VALIDATED);
			expense.setStatusSelect(ExpenseRepository.STATUS_REIMBURSED);
		}

		expense.setPaymentAmount(expense.getInTaxTotal().subtract(expense.getAdvanceAmount())
				.subtract(expense.getWithdrawnCash()).subtract(expense.getPersonalExpenseAmount()));
	}

	public void addPayment(Expense expense) throws AxelorException {
		addPayment(expense, expense.getCompany().getDefaultBankDetails());
	}

    @Transactional(rollbackOn = {AxelorException.class, Exception.class})
    public void cancelPayment(Expense expense) throws AxelorException {
        BankOrder bankOrder = expense.getBankOrder();

        if (bankOrder != null) {
            if (bankOrder.getStatusSelect() == BankOrderRepository.STATUS_CARRIED_OUT || bankOrder.getStatusSelect() == BankOrderRepository.STATUS_REJECTED) {
                throw new AxelorException(IException.FUNCTIONNAL, I18n.get(IExceptionMessage.EXPENSE_PAYMENT_CANCEL));
            } else {
                Beans.get(BankOrderService.class).cancelBankOrder(bankOrder);
			}
		}
		expense.setPaymentStatusSelect(InvoicePaymentRepository.STATUS_CANCELED);
		expense.setStatusSelect(ExpenseRepository.STATUS_VALIDATED);
		expense.setPaymentDate(null);
		expense.setPaymentAmount(BigDecimal.ZERO);
		expenseRepository.save(expense);
    }

	public List<InvoiceLine> createInvoiceLines(Invoice invoice, List<ExpenseLine> expenseLineList, int priority) throws AxelorException {

		List<InvoiceLine> invoiceLineList = new ArrayList<>();
		int count = 0;
		for (ExpenseLine expenseLine : expenseLineList) {

			invoiceLineList.addAll(this.createInvoiceLine(invoice, expenseLine, priority * 100 + count));
			count++;
			expenseLine.setInvoiced(true);

		}

		return invoiceLineList;

	}

	public List<InvoiceLine> createInvoiceLine(Invoice invoice, ExpenseLine expenseLine, int priority) throws AxelorException {

		Product product = expenseLine.getExpenseProduct();
		InvoiceLineGenerator invoiceLineGenerator = null;
		Integer atiChoice = invoice.getCompany().getAccountConfig().getInvoiceInAtiSelect();
		if (atiChoice == 1 || atiChoice == 3) {
			invoiceLineGenerator = new InvoiceLineGenerator(invoice, product, product.getName(), expenseLine.getUntaxedAmount(),
					expenseLine.getUntaxedAmount(), expenseLine.getComments(), BigDecimal.ONE, product.getUnit(), null, priority, BigDecimal.ZERO, IPriceListLine.AMOUNT_TYPE_NONE,
					expenseLine.getUntaxedAmount(), expenseLine.getTotalAmount(), false, false) {

				@Override
				public List<InvoiceLine> creates() throws AxelorException {

					InvoiceLine invoiceLine = this.createInvoiceLine();

					List<InvoiceLine> invoiceLines = new ArrayList<>();
					invoiceLines.add(invoiceLine);

					return invoiceLines;
				}
			};
		} else {
			invoiceLineGenerator = new InvoiceLineGenerator(invoice, product, product.getName(), expenseLine.getTotalAmount(),
					expenseLine.getTotalAmount(), expenseLine.getComments(), BigDecimal.ONE, product.getUnit(), null, priority, BigDecimal.ZERO, IPriceListLine.AMOUNT_TYPE_NONE,
					expenseLine.getUntaxedAmount(), expenseLine.getTotalAmount(), false, false) {

				@Override
				public List<InvoiceLine> creates() throws AxelorException {

					InvoiceLine invoiceLine = this.createInvoiceLine();

					List<InvoiceLine> invoiceLines = new ArrayList<>();
					invoiceLines.add(invoiceLine);

					return invoiceLines;
				}
			};
		}

		return invoiceLineGenerator.creates();
	}

	public void getExpensesTypes(ActionRequest request, ActionResponse response) {
		List<Map<String, String>> dataList = new ArrayList<>();
		try {
			List<Product> productList = Beans.get(ProductRepository.class).all().filter("self.expense = true").fetch();
			for (Product product : productList) {
				Map<String, String> map = new HashMap<>();
				map.put("name", product.getName());
				map.put("id", product.getId().toString());
				dataList.add(map);
			}
			response.setData(dataList);
			response.setTotal(dataList.size());
		}
		catch(Exception e){
			response.setStatus(-1);
			response.setError(e.getMessage());
		}
	}

	@Transactional
	public void insertExpenseLine(ActionRequest request, ActionResponse response) {
		User user = AuthUtils.getUser();
		Map<String, Object> requestData = request.getData();
		Project project = Beans.get(ProjectRepository.class).find(new Long(requestData.get("project").toString()));
		Product product = Beans.get(ProductRepository.class).find(new Long(requestData.get("expenseType").toString()));
		if (user != null) {
		    Expense expense = getOrCreateExpense(user);
			ExpenseLine expenseLine = new ExpenseLine();
			expenseLine.setExpenseDate(LocalDate.parse(requestData.get("date").toString(), DateTimeFormatter.ISO_DATE));
			expenseLine.setComments(requestData.get("comments").toString());
			expenseLine.setExpenseProduct(product);
			expenseLine.setProject(project);
			expenseLine.setUser(user);
			expenseLine.setTotalAmount(new BigDecimal(requestData.get("unTaxTotal").toString()));
			expenseLine.setTotalTax(new BigDecimal(requestData.get("taxTotal").toString()));
			expenseLine.setUntaxedAmount(expenseLine.getTotalAmount().subtract(expenseLine.getTotalTax()));
			expenseLine.setToInvoice(new Boolean(requestData.get("toInvoice").toString()));
			String justification  = (String) requestData.get("justification");
			if (!Strings.isNullOrEmpty(justification)) {
				expenseLine.setJustification(Base64.decodeBase64(justification));
			}
			expense.addExpenseLineListItem(expenseLine);

			Beans.get(ExpenseRepository.class).save(expense);
			HashMap<String, Object> data = new HashMap<String, Object>();
			data.put("id", expenseLine.getId());
			response.setData(data);
			response.setTotal(1);
		}
	}

	@Override
	public Expense getOrCreateExpense(User user) {
		Expense expense = Beans.get(ExpenseRepository.class).all()
				.filter("self.statusSelect = ?1 AND self.user.id = ?2",
						ExpenseRepository.STATUS_DRAFT,
						user.getId())
				.order("-id")
				.fetchOne();
		if (expense == null) {
			expense = new Expense();
			expense.setUser(user);
			Company company = null;
			if (user.getEmployee() != null
					&& user.getEmployee().getMainEmploymentContract() != null) {
				company = user.getEmployee().getMainEmploymentContract().getPayCompany();
			}
			expense.setCompany(company);
			expense.setStatusSelect(ExpenseRepository.STATUS_DRAFT);
		}
		return expense;
	}

	public BigDecimal computePersonalExpenseAmount(Expense expense) {

		BigDecimal personalExpenseAmount = new BigDecimal("0.00");

		if (expense.getExpenseLineList() != null && !expense.getExpenseLineList().isEmpty()) {
			for (ExpenseLine expenseLine : expense.getExpenseLineList()) {
				if (expenseLine.getExpenseProduct() != null && expenseLine.getExpenseProduct().getPersonalExpense()) {
					personalExpenseAmount = personalExpenseAmount.add(expenseLine.getTotalAmount());
				}
			}
		}
		return personalExpenseAmount;
	}


	public BigDecimal computeAdvanceAmount(Expense expense) {

		BigDecimal advanceAmount = new BigDecimal("0.00");

		if (expense.getEmployeeAdvanceUsageList() != null && !expense.getEmployeeAdvanceUsageList().isEmpty()) {
			for (EmployeeAdvanceUsage advanceLine : expense.getEmployeeAdvanceUsageList()) {
				advanceAmount = advanceAmount.add(advanceLine.getUsedAmount());
			}
		}

		return advanceAmount;
	}

	public Product getKilometricExpenseProduct(Expense expense) throws AxelorException {

		HRConfig hrConfig = hrConfigService.getHRConfig(expense.getCompany());
		Product expenseProduct = hrConfigService.getKilometricExpenseProduct(hrConfig);

		return expenseProduct;
	}


	public void setDraftSequence(Expense expense) throws AxelorException {
		if (expense.getId() != null && Strings.isNullOrEmpty(expense.getExpenseSeq())) {
			expense.setExpenseSeq(Beans.get(SequenceService.class).getDraftSequenceNumber(expense));
		}
	}

	private void setExpenseSeq(Expense expense) throws AxelorException {
        if (!Beans.get(SequenceService.class).isEmptyOrDraftSequenceNumber(expense.getExpenseSeq())) {
            return;
        }

		 HRConfig hrConfig = hrConfigService.getHRConfig(expense.getCompany());
		 Sequence sequence = hrConfigService.getExpenseSequence(hrConfig);

		if (sequence != null) {
			expense.setExpenseSeq(Beans.get(SequenceService.class).getSequenceNumber(sequence, expense.getSentDate()));

			if (expense.getExpenseSeq() != null) {
				return;
			}
		}

		throw new AxelorException(expense, IException.CONFIGURATION_ERROR, I18n.get(IExceptionMessage.HR_CONFIG_NO_EXPENSE_SEQUENCE), expense.getCompany().getName());
	}

	@Override
	public List<KilometricAllowParam> getListOfKilometricAllowParamVehicleFilter(ExpenseLine expenseLine) {
		
		List<KilometricAllowParam> kilometricAllowParamList = new ArrayList<>();
		
		Expense expense = expenseLine.getExpense();
		
		if (expense == null) {
			return kilometricAllowParamList;
		}
		
		if (expense.getId() != null) {
			expense = expenseRepository.find(expense.getId());
		}
		
		LocalDate expenseDate = expenseLine.getExpenseDate();
		if (expense.getUser() == null || expense.getUser().getEmployee() == null || expenseDate == null) {
			return kilometricAllowParamList;
		}
		
		List<EmployeeVehicle> vehicleList = expense.getUser().getEmployee().getEmployeeVehicleList();


		for (EmployeeVehicle vehicle : vehicleList) {
		    LocalDate startDate = vehicle.getStartDate();
			LocalDate endDate = vehicle.getEndDate();
			if (startDate == null) {
			    if (endDate == null) {
					kilometricAllowParamList.add(vehicle.getKilometricAllowParam());
				} else if(expenseDate.compareTo(endDate)<=0) {
					kilometricAllowParamList.add(vehicle.getKilometricAllowParam());
				}
			} else if (endDate == null) {
				if (expenseDate.compareTo(startDate)>=0) {
					kilometricAllowParamList.add(vehicle.getKilometricAllowParam());
				}
			} else if (expenseDate.compareTo(startDate)>=0 && expenseDate.compareTo(endDate)<=0) {
				kilometricAllowParamList.add(vehicle.getKilometricAllowParam());
			}
		}
		
		return kilometricAllowParamList;
	}
}
