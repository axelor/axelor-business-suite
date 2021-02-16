/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.cash.management.service;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.cash.management.db.Forecast;
import com.axelor.apps.cash.management.db.ForecastRecap;
import com.axelor.apps.cash.management.db.ForecastRecapLine;
import com.axelor.apps.cash.management.db.ForecastRecapLineType;
import com.axelor.apps.cash.management.db.repo.ForecastRecapLineRepository;
import com.axelor.apps.cash.management.db.repo.ForecastRecapLineTypeRepository;
import com.axelor.apps.cash.management.db.repo.ForecastRecapRepository;
import com.axelor.apps.cash.management.db.repo.ForecastRepository;
import com.axelor.apps.cash.management.exception.IExceptionMessage;
import com.axelor.apps.cash.management.report.IReport;
import com.axelor.apps.cash.management.translation.ITranslation;
import com.axelor.apps.crm.db.Opportunity;
import com.axelor.apps.crm.db.repo.OpportunityRepository;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.Expense;
import com.axelor.apps.hr.db.repo.EmployeeHRRepository;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.ExpenseRepository;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.supplychain.db.Timetable;
import com.axelor.apps.supplychain.db.repo.TimetableRepository;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ForecastRecapService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AppBaseService appBaseService;
  protected ForecastRepository forecastRepo;
  protected ForecastRecapLineRepository forecastRecapLineRepo;
  protected CurrencyService currencyService;
  protected ForecastRecapLineTypeRepository forecastRecapLineTypeRepo;
  protected ForecastRecapRepository forecastRecapRepo;
  protected OpportunityRepository opportunityRepo;
  protected InvoiceRepository invoiceRepo;
  protected EmployeeRepository employeeRepo;
  protected SaleOrderRepository saleOrderRepo;
  protected PurchaseOrderRepository purchaseOrderRepo;
  protected TimetableRepository timetableRepo;
  protected ExpenseRepository expenseRepo;

  protected LocalDate today;

  @Inject
  public ForecastRecapService(
      AppBaseService appBaseService,
      ForecastRepository forecastRepo,
      ForecastRecapLineRepository forecastRecapLineRepo,
      CurrencyService currencyService,
      ForecastRecapLineTypeRepository forecastRecapLineTypeRepo,
      ForecastRecapRepository forecastRecapRepo,
      OpportunityRepository opportunityRepo,
      InvoiceRepository invoiceRepo,
      EmployeeRepository employeeRepo,
      SaleOrderRepository saleOrderRepo,
      PurchaseOrderRepository purchaseOrderRepo,
      TimetableRepository timetableRepo,
      ExpenseRepository expenseRepo) {
    this.appBaseService = appBaseService;
    this.forecastRepo = forecastRepo;
    this.forecastRecapLineRepo = forecastRecapLineRepo;
    this.forecastRecapLineTypeRepo = forecastRecapLineTypeRepo;
    this.forecastRecapRepo = forecastRecapRepo;
    this.currencyService = currencyService;
    this.opportunityRepo = opportunityRepo;
    this.invoiceRepo = invoiceRepo;
    this.employeeRepo = employeeRepo;
    this.saleOrderRepo = saleOrderRepo;
    this.purchaseOrderRepo = purchaseOrderRepo;
    this.timetableRepo = timetableRepo;
    this.expenseRepo = expenseRepo;
  }

  @Transactional
  public void reset(ForecastRecap forecastRecap) {
    forecastRecap.clearForecastRecapLineList();
    forecastRecap.setCurrentBalance(forecastRecap.getStartingBalance());

    today = appBaseService.getTodayDate(forecastRecap.getCompany());
    forecastRecapRepo.save(forecastRecap);
  }

  @Transactional
  public void finish(ForecastRecap forecastRecap) {
    this.computeForecastRecapLineBalance(forecastRecap);
    forecastRecap.setEndingBalance(forecastRecap.getCurrentBalance());
    forecastRecap.setCalculationDate(today);
    forecastRecap.setIsComplete(true);
    forecastRecapRepo.save(forecastRecap);
  }

  public void populate(ForecastRecap forecastRecap) throws AxelorException {
    this.reset(forecastRecapRepo.find(forecastRecap.getId()));

    if (forecastRecap.getOpportunitiesTypeSelect() != null
        && forecastRecap.getOpportunitiesTypeSelect()
            > ForecastRecapRepository.OPPORTUNITY_TYPE_NO) {
      this.populateWithOpportunities(forecastRecapRepo.find(forecastRecap.getId()));
    }
    this.populateWithInvoices(forecastRecapRepo.find(forecastRecap.getId()));
    this.populateWithSalaries(forecastRecapRepo.find(forecastRecap.getId()));
    this.populateWithSaleOrders(forecastRecapRepo.find(forecastRecap.getId()));
    this.populateWithPurchaseOrders(forecastRecapRepo.find(forecastRecap.getId()));
    if (forecastRecap.getIsReport()) {
      this.populateWithForecastsNoSave(forecastRecap);
    } else {
      this.populateWithForecasts(forecastRecapRepo.find(forecastRecap.getId()));
    }
    this.populateWithExpenses(forecastRecapRepo.find(forecastRecap.getId()));

    this.finish(forecastRecapRepo.find(forecastRecap.getId()));
  }

  public void populateWithOpportunities(ForecastRecap forecastRecap) throws AxelorException {
    List<Opportunity> opportunityList = new ArrayList<Opportunity>();
    ForecastRecapLineType opportunityForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_OPPORTUNITY);
    List<Integer> statusList =
        StringTool.getIntegerList(opportunityForecastRecapLineType.getStatusSelect());
    opportunityList =
        opportunityRepo
            .all()
            .filter(
                "self.company = ?1"
                    + (forecastRecap.getBankDetails() != null ? " AND self.bankDetails = ?2" : "")
                    + " AND self.expectedCloseDate BETWEEN ?3 AND ?4 AND self.saleOrderList IS EMPTY"
                    + (statusList.isEmpty() ? "" : " AND self.salesStageSelect IN ?5"),
                forecastRecap.getCompany(),
                forecastRecap.getBankDetails(),
                forecastRecap.getFromDate(),
                forecastRecap.getToDate(),
                statusList)
            .fetch();
    for (Opportunity opportunity : opportunityList) {
      BigDecimal amountCompanyCurr = BigDecimal.ZERO;
      opportunity = opportunityRepo.find(opportunity.getId());
      if (forecastRecap.getOpportunitiesTypeSelect()
          == ForecastRecapRepository.OPPORTUNITY_TYPE_BASE) {
        amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    opportunity.getCurrency(),
                    opportunity.getCompany().getCurrency(),
                    opportunity
                        .getAmount()
                        .multiply(opportunity.getProbability())
                        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP),
                    today)
                .setScale(2, RoundingMode.HALF_UP);
      }
      this.createForecastRecapLine(
          opportunity.getExpectedCloseDate(),
          opportunityForecastRecapLineType.getTypeSelect(),
          amountCompanyCurr,
          Opportunity.class.getName(),
          opportunity.getId(),
          opportunity.getName(),
          forecastRecapLineTypeRepo.find(opportunityForecastRecapLineType.getId()),
          forecastRecapRepo.find(forecastRecap.getId()));
      JPA.clear();
    }
  }

  public void getOpportunities(
      ForecastRecap forecastRecap,
      Map<LocalDate, BigDecimal> mapExpected,
      Map<LocalDate, BigDecimal> mapConfirmed)
      throws AxelorException {
    List<Opportunity> opportunityList = new ArrayList<Opportunity>();
    if (forecastRecap.getBankDetails() != null) {
      opportunityList =
          Beans.get(OpportunityRepository.class)
              .all()
              .filter(
                  "self.company = ?1 AND self.bankDetails = ?2 AND self.expectedCloseDate BETWEEN ?3 AND ?4 AND self.saleOrderList IS EMPTY",
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails(),
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate())
              .fetch();
    } else {
      opportunityList =
          Beans.get(OpportunityRepository.class)
              .all()
              .filter(
                  "self.company = ?1 AND self.expectedCloseDate BETWEEN ?2 AND ?3 AND self.saleOrderList IS EMPTY",
                  forecastRecap.getCompany(),
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate())
              .fetch();
    }
    for (Opportunity opportunity : opportunityList) {
      BigDecimal amountCompanyCurr = BigDecimal.ZERO;
      if (forecastRecap.getOpportunitiesTypeSelect()
          == ForecastRecapRepository.OPPORTUNITY_TYPE_BASE) {
        amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    opportunity.getCurrency(),
                    opportunity.getCompany().getCurrency(),
                    opportunity
                        .getAmount()
                        .multiply(opportunity.getProbability())
                        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP),
                    appBaseService.getTodayDate(forecastRecap.getCompany()))
                .setScale(2, RoundingMode.HALF_UP);
      }
      if (opportunity.getSalesStageSelect() == 9) {
        if (mapExpected.containsKey(opportunity.getExpectedCloseDate())) {
          mapExpected.put(
              opportunity.getExpectedCloseDate(),
              mapExpected.get(opportunity.getExpectedCloseDate()).add(amountCompanyCurr));
        } else {
          mapExpected.put(opportunity.getExpectedCloseDate(), amountCompanyCurr);
        }
      } else {
        if (mapConfirmed.containsKey(opportunity.getExpectedCloseDate())) {
          mapConfirmed.put(
              opportunity.getExpectedCloseDate(),
              mapConfirmed.get(opportunity.getExpectedCloseDate()).add(amountCompanyCurr));
        } else {
          mapConfirmed.put(opportunity.getExpectedCloseDate(), amountCompanyCurr);
        }
      }
    }
  }

  public void populateWithInvoices(ForecastRecap forecastRecap) throws AxelorException {
    List<Invoice> invoiceList = new ArrayList<Invoice>();
    List<ForecastRecapLineType> invoiceForecastRecapLineTypeList =
        this.getForecastRecapLineTypeList(ForecastRecapLineTypeRepository.ELEMENT_INVOICE);

    List<Integer> statusList =
        StringTool.getIntegerList(
            invoiceForecastRecapLineTypeList.stream()
                .map(ForecastRecapLineType::getStatusSelect)
                .reduce("", String::join));

    List<Integer> operationTypeSelectList =
        invoiceForecastRecapLineTypeList.stream()
            .map(ForecastRecapLineType::getOperationTypeSelect)
            .collect(Collectors.toList());

    if (statusList.isEmpty()) {
      statusList.add(InvoiceRepository.STATUS_VALIDATED);
    }

    invoiceList =
        invoiceRepo
            .all()
            .filter(
                "self.company = ?1"
                    + (forecastRecap.getBankDetails() != null
                        ? " AND self.companyBankDetails = ?2"
                        : "")
                    + " AND self.statusSelect IN (?3) AND self.operationTypeSelect IN (?4) AND self.estimatedPaymentDate BETWEEN ?5 AND ?6 AND self.companyInTaxTotalRemaining != 0",
                forecastRecap.getCompany(),
                forecastRecap.getBankDetails(),
                statusList,
                operationTypeSelectList,
                forecastRecap.getFromDate(),
                forecastRecap.getToDate())
            .fetch();
    for (Invoice invoice : invoiceList) {
      invoice = invoiceRepo.find(invoice.getId());
      BigDecimal amount;
      if (invoice.getMove() == null) {
        amount =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    invoice.getCurrency(),
                    forecastRecap.getCompany().getCurrency(),
                    invoice.getCompanyInTaxTotal(),
                    today)
                .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
      } else {
        amount =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    invoice.getCurrency(),
                    forecastRecap.getCompany().getCurrency(),
                    invoice.getCompanyInTaxTotalRemaining(),
                    today)
                .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
      }
      this.createForecastRecapLine(
          invoice.getEstimatedPaymentDate(),
          invoice.getOperationTypeSelect() == 2 || invoice.getOperationTypeSelect() == 3 ? 1 : 2,
          amount,
          invoice.getClass().getName(),
          invoice.getId(),
          invoice.getInvoiceId(),
          forecastRecapLineTypeRepo
              .all()
              .filter(
                  "self.operationTypeSelect = ?1 AND self.statusSelect LIKE ?2",
                  invoice.getOperationTypeSelect(),
                  "%" + invoice.getStatusSelect() + "%")
              .fetchOne(),
          forecastRecapRepo.find(forecastRecap.getId()));
      JPA.clear();
    }
  }

  public void getInvoices(
      ForecastRecap forecastRecap,
      Map<LocalDate, BigDecimal> mapExpected,
      Map<LocalDate, BigDecimal> mapConfirmed) {
    List<Invoice> invoiceList = new ArrayList<Invoice>();
    if (forecastRecap.getBankDetails() != null) {
      invoiceList =
          Beans.get(InvoiceRepository.class)
              .all()
              .filter(
                  "self.company = ?1 AND self.companyBankDetails = ?2 AND self.statusSelect = 3 AND self.estimatedPaymentDate BETWEEN ?3 AND ?4 AND self.companyInTaxTotalRemaining != 0",
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails(),
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate())
              .fetch();
    } else {
      invoiceList =
          Beans.get(InvoiceRepository.class)
              .all()
              .filter(
                  "self.company = ?1 AND self.statusSelect = 3 AND self.estimatedPaymentDate BETWEEN ?2 AND ?3 AND self.companyInTaxTotalRemaining != 0",
                  forecastRecap.getCompany(),
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate())
              .fetch();
    }
    for (Invoice invoice : invoiceList) {
      BigDecimal amountPaidExTax =
          invoice
              .getAmountPaid()
              .multiply(invoice.getCompanyExTaxTotal())
              .divide(invoice.getCompanyInTaxTotal(), 2, RoundingMode.HALF_UP);
      BigDecimal amount = invoice.getCompanyExTaxTotal().subtract(amountPaidExTax);
      if (invoice.getOperationTypeSelect() == 2 || invoice.getOperationTypeSelect() == 3) {
        if (mapConfirmed.containsKey(invoice.getEstimatedPaymentDate())) {
          mapConfirmed.put(
              invoice.getEstimatedPaymentDate(),
              mapConfirmed.get(invoice.getEstimatedPaymentDate()).add(amount));
        } else {
          mapConfirmed.put(invoice.getEstimatedPaymentDate(), amount);
        }
      }
    }
  }

  public void populateWithSalaries(ForecastRecap forecastRecap) throws AxelorException {
    List<Employee> employeeList = new ArrayList<Employee>();
    ForecastRecapLineType salaryForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_SALARY);
    employeeList =
        employeeRepo
            .all()
            .filter(
                "self.mainEmploymentContract.payCompany = ?1 AND self.mainEmploymentContract.monthlyGlobalCost != 0"
                    + (forecastRecap.getBankDetails() != null ? " AND self.bankDetails = ?2" : ""),
                forecastRecap.getCompany(),
                forecastRecap.getBankDetails())
            .fetch();

    LocalDate itDate =
        LocalDate.parse(forecastRecap.getFromDate().toString(), DateTimeFormatter.ISO_DATE);
    while (!itDate.isAfter(forecastRecap.getToDate())) {
      LocalDate payDay =
          itDate.withDayOfMonth(
              salaryForecastRecapLineType.getPayDaySelect() == 0
                  ? itDate.lengthOfMonth()
                  : salaryForecastRecapLineType.getPayDaySelect());
      if (itDate.isEqual(payDay)) {
        for (Employee employee : employeeList) {
          if (EmployeeHRRepository.isEmployeeFormerOrNew(employee)) {
            continue;
          }
          employeeRepo.find(employee.getId());
          this.createForecastRecapLine(
              itDate,
              salaryForecastRecapLineType.getTypeSelect(),
              employee.getMainEmploymentContract().getMonthlyGlobalCost(),
              employee.getClass().getName(),
              employee.getId(),
              employee.getName(),
              forecastRecapLineTypeRepo.find(salaryForecastRecapLineType.getId()),
              forecastRecapRepo.find(forecastRecap.getId()));
          JPA.clear();
        }
        itDate = itDate.plusMonths(1);
      } else {
        itDate = payDay;
      }
    }
  }

  public void populateWithSaleOrders(ForecastRecap forecastRecap) throws AxelorException {
    ForecastRecapLineType saleOrderForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_SALE_ORDER);
    List<Integer> statusList =
        StringTool.getIntegerList(saleOrderForecastRecapLineType.getStatusSelect());
    List<SaleOrder> saleOrderList = new ArrayList<SaleOrder>();
    if (statusList.isEmpty()) {
      statusList.add(SaleOrderRepository.STATUS_ORDER_CONFIRMED);
    }
    this.populateWithTimetables(forecastRecap, saleOrderForecastRecapLineType, statusList);
    if (saleOrderForecastRecapLineType.getEstimatedDuration() != null) {
      saleOrderList =
          saleOrderRepo
              .all()
              .filter(
                  "(self.expectedRealisationDate BETWEEN ?1 AND ?2 OR (self.creationDate BETWEEN ?3 AND ?4 AND self.expectedRealisationDate IS NULL)) AND self.company = ?5"
                      + " AND self.statusSelect IN (?6) AND self.inTaxTotal != 0"
                      + (forecastRecap.getBankDetails() != null
                          ? " AND self.companyBankDetails = ?7"
                          : "")
                      + " AND self.timetableList IS EMPTY",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap
                      .getFromDate()
                      .minusDays(saleOrderForecastRecapLineType.getEstimatedDuration()),
                  forecastRecap
                      .getToDate()
                      .minusDays(saleOrderForecastRecapLineType.getEstimatedDuration()),
                  forecastRecap.getCompany(),
                  statusList,
                  forecastRecap.getBankDetails())
              .fetch();
    } else {
      saleOrderList =
          saleOrderRepo
              .all()
              .filter(
                  "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3"
                      + " AND self.statusSelect IN (?4) AND self.inTaxTotal != 0"
                      + (forecastRecap.getBankDetails() != null
                          ? " AND self.companyBankDetails = ?5"
                          : "")
                      + " AND self.timetableList IS EMPTY",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  statusList,
                  forecastRecap.getBankDetails())
              .fetch();
    }
    for (SaleOrder saleOrder : saleOrderList) {
      saleOrder = saleOrderRepo.find(saleOrder.getId());
      BigDecimal amount =
          currencyService
              .getAmountCurrencyConvertedAtDate(
                  saleOrder.getCurrency(),
                  forecastRecap.getCompany().getCurrency(),
                  saleOrder.getInTaxTotal(),
                  today)
              .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);

      this.createForecastRecapLine(
          saleOrder.getExpectedRealisationDate() == null
              ? saleOrder
                  .getCreationDate()
                  .plusDays(saleOrderForecastRecapLineType.getEstimatedDuration())
              : saleOrder.getExpectedRealisationDate(),
          saleOrderForecastRecapLineType.getTypeSelect(),
          amount,
          saleOrder.getClass().getName(),
          saleOrder.getId(),
          saleOrder.getSaleOrderSeq(),
          forecastRecapLineTypeRepo.find(saleOrderForecastRecapLineType.getId()),
          forecastRecapRepo.find(forecastRecap.getId()));
      JPA.clear();
    }
  }

  public void populateWithPurchaseOrders(ForecastRecap forecastRecap) throws AxelorException {
    ForecastRecapLineType purchaseOrderForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_PURCHASE_ORDER);
    List<Integer> statusList =
        StringTool.getIntegerList(purchaseOrderForecastRecapLineType.getStatusSelect());
    List<PurchaseOrder> purchaseOrderList = new ArrayList<PurchaseOrder>();
    if (statusList.isEmpty()) {
      statusList.add(PurchaseOrderRepository.STATUS_VALIDATED);
    }
    this.populateWithTimetables(forecastRecap, purchaseOrderForecastRecapLineType, statusList);
    if (purchaseOrderForecastRecapLineType.getEstimatedDuration() != null) {
      purchaseOrderList =
          purchaseOrderRepo
              .all()
              .filter(
                  "(self.expectedRealisationDate BETWEEN ?1 AND ?2 OR (self.orderDate BETWEEN ?3 AND ?4 AND self.expectedRealisationDate IS NULL)) AND self.company = ?5"
                      + " AND self.statusSelect IN (?6) AND self.inTaxTotal != 0"
                      + (forecastRecap.getBankDetails() != null
                          ? " AND self.companyBankDetails = ?7"
                          : "")
                      + " AND self.timetableList IS EMPTY",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap
                      .getFromDate()
                      .minusDays(purchaseOrderForecastRecapLineType.getEstimatedDuration()),
                  forecastRecap
                      .getToDate()
                      .minusDays(purchaseOrderForecastRecapLineType.getEstimatedDuration()),
                  forecastRecap.getCompany(),
                  statusList,
                  forecastRecap.getBankDetails())
              .fetch();
    } else {
      purchaseOrderList =
          purchaseOrderRepo
              .all()
              .filter(
                  "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3"
                      + " AND self.statusSelect IN (?4) AND self.inTaxTotal != 0"
                      + (forecastRecap.getBankDetails() != null
                          ? " AND self.companyBankDetails = ?5"
                          : "")
                      + " AND self.timetableList IS EMPTY",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  statusList,
                  forecastRecap.getBankDetails())
              .fetch();
    }
    for (PurchaseOrder purchaseOrder : purchaseOrderList) {
      purchaseOrder = purchaseOrderRepo.find(purchaseOrder.getId());
      BigDecimal amount =
          currencyService
              .getAmountCurrencyConvertedAtDate(
                  purchaseOrder.getCurrency(),
                  forecastRecap.getCompany().getCurrency(),
                  purchaseOrder.getInTaxTotal(),
                  today)
              .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);

      this.createForecastRecapLine(
          purchaseOrder.getExpectedRealisationDate() == null
              ? purchaseOrder
                  .getOrderDate()
                  .plusDays(purchaseOrderForecastRecapLineType.getEstimatedDuration())
              : purchaseOrder.getExpectedRealisationDate(),
          purchaseOrderForecastRecapLineType.getTypeSelect(),
          amount,
          purchaseOrder.getClass().getName(),
          purchaseOrder.getId(),
          purchaseOrder.getPurchaseOrderSeq(),
          forecastRecapLineTypeRepo.find(purchaseOrderForecastRecapLineType.getId()),
          forecastRecapRepo.find(forecastRecap.getId()));
      JPA.clear();
    }
  }

  public void populateWithTimetables(
      ForecastRecap forecastRecap,
      ForecastRecapLineType forecastRecapLineType,
      List<Integer> statusList)
      throws AxelorException {
    List<Timetable> timetableList = new ArrayList<Timetable>();
    if (forecastRecapLineType.getElementSelect()
        == ForecastRecapLineTypeRepository.ELEMENT_SALE_ORDER) {
      timetableList =
          timetableRepo
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.saleOrder.company = ?3"
                      + " AND self.saleOrder.statusSelect IN (?4) AND self.amount != 0"
                      + (forecastRecap.getBankDetails() != null
                          ? " AND self.saleOrder.companyBankDetails = ?5"
                          : ""),
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  statusList,
                  forecastRecap.getBankDetails())
              .fetch();
    } else if (forecastRecapLineType.getElementSelect()
        == ForecastRecapLineTypeRepository.ELEMENT_PURCHASE_ORDER) {
      timetableList =
          timetableRepo
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.purchaseOrder.company = ?3"
                      + " AND self.purchaseOrder.statusSelect IN (?4) AND self.amount != 0"
                      + (forecastRecap.getBankDetails() != null
                          ? " AND self.purchaseOrder.companyBankDetails = ?5"
                          : ""),
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  statusList,
                  forecastRecap.getBankDetails())
              .fetch();
    }
    if (forecastRecapLineType.getElementSelect()
        == ForecastRecapLineTypeRepository.ELEMENT_SALE_ORDER) {
      for (Timetable timetable : timetableList) {
        timetable = timetableRepo.find(timetable.getId());
        BigDecimal amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    timetable.getSaleOrder().getCurrency(),
                    forecastRecap.getCompany().getCurrency(),
                    timetable.getAmount(),
                    today)
                .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
        this.createForecastRecapLine(
            timetable.getEstimatedDate(),
            forecastRecapLineType.getTypeSelect(),
            amountCompanyCurr,
            SaleOrder.class.getName(),
            timetable.getSaleOrder().getId(),
            timetable.getSaleOrder().getSaleOrderSeq(),
            forecastRecapLineTypeRepo.find(forecastRecapLineType.getId()),
            forecastRecapRepo.find(forecastRecap.getId()));
        JPA.clear();
      }
    } else if (forecastRecapLineType.getElementSelect()
        == ForecastRecapLineTypeRepository.ELEMENT_PURCHASE_ORDER) {
      for (Timetable timetable : timetableList) {
        timetable = timetableRepo.find(timetable.getId());
        BigDecimal amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    timetable.getPurchaseOrder().getCurrency(),
                    forecastRecap.getCompany().getCurrency(),
                    timetable.getAmount(),
                    today)
                .setScale(AppBaseService.DEFAULT_NB_DECIMAL_DIGITS, RoundingMode.HALF_UP);
        this.createForecastRecapLine(
            timetable.getEstimatedDate(),
            forecastRecapLineType.getTypeSelect(),
            amountCompanyCurr,
            PurchaseOrder.class.getName(),
            timetable.getPurchaseOrder().getId(),
            timetable.getPurchaseOrder().getPurchaseOrderSeq(),
            forecastRecapLineTypeRepo.find(forecastRecapLineType.getId()),
            forecastRecapRepo.find(forecastRecap.getId()));
        JPA.clear();
      }
    }
  }

  public void getTimetablesOrOrders(
      ForecastRecap forecastRecap,
      Map<LocalDate, BigDecimal> mapExpected,
      Map<LocalDate, BigDecimal> mapConfirmed)
      throws AxelorException {
    List<Timetable> timetableSaleOrderList = new ArrayList<Timetable>();
    List<SaleOrder> saleOrderList = new ArrayList<SaleOrder>();
    if (forecastRecap.getBankDetails() != null) {
      timetableSaleOrderList =
          Beans.get(TimetableRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.saleOrder.company = ?3 AND"
                      + " self.saleOrder.companyBankDetails = ?4 AND (self.saleOrder.statusSelect = 2 OR self.saleOrder.statusSelect = 3) AND self.amount != 0",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails())
              .fetch();
    } else {
      timetableSaleOrderList =
          Beans.get(TimetableRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.saleOrder.company = ?3 AND"
                      + " (self.saleOrder.statusSelect = 2 OR self.saleOrder.statusSelect = 3) AND self.amount != 0",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany())
              .fetch();
    }
    for (Timetable timetable : timetableSaleOrderList) {
      saleOrderList.add(timetable.getSaleOrder());
      BigDecimal amountCompanyCurr =
          currencyService
              .getAmountCurrencyConvertedAtDate(
                  timetable.getSaleOrder().getCurrency(),
                  timetable.getSaleOrder().getCompany().getCurrency(),
                  timetable.getAmount(),
                  appBaseService.getTodayDate(forecastRecap.getCompany()))
              .setScale(2, RoundingMode.HALF_UP);
      if (timetable.getSaleOrder().getStatusSelect() == 2) {
        if (mapExpected.containsKey(timetable.getEstimatedDate())) {
          mapExpected.put(
              timetable.getEstimatedDate(),
              mapExpected.get(timetable.getEstimatedDate()).add(amountCompanyCurr));
        } else {
          mapExpected.put(timetable.getEstimatedDate(), amountCompanyCurr);
        }
      } else {
        if (mapConfirmed.containsKey(timetable.getEstimatedDate())) {
          mapConfirmed.put(
              timetable.getEstimatedDate(),
              mapConfirmed.get(timetable.getEstimatedDate()).add(amountCompanyCurr));
        } else {
          mapConfirmed.put(timetable.getEstimatedDate(), amountCompanyCurr);
        }
      }
    }
    List<SaleOrder> saleOrderNoTimeTableList = new ArrayList<SaleOrder>();
    if (forecastRecap.getBankDetails() != null) {
      saleOrderNoTimeTableList =
          Beans.get(SaleOrderRepository.class)
              .all()
              .filter(
                  "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " self.companyBankDetails = ?4 AND (self.statusSelect = 2 OR self.statusSelect = 3)",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails())
              .fetch();
    } else {
      saleOrderNoTimeTableList =
          Beans.get(SaleOrderRepository.class)
              .all()
              .filter(
                  "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " (self.statusSelect = 2 OR self.statusSelect = 3)",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany())
              .fetch();
    }
    for (SaleOrder saleOrder : saleOrderNoTimeTableList) {
      if (!saleOrderList.contains(saleOrder)) {
        BigDecimal amountCompanyCurr =
            saleOrder.getCompanyExTaxTotal().subtract(saleOrder.getAmountInvoiced());
        if (amountCompanyCurr.compareTo(BigDecimal.ZERO) == 0) {
          if (saleOrder.getStatusSelect() == 2) {
            if (mapExpected.containsKey(saleOrder.getExpectedRealisationDate())) {
              mapExpected.put(
                  saleOrder.getExpectedRealisationDate(),
                  mapExpected.get(saleOrder.getExpectedRealisationDate()).add(amountCompanyCurr));
            } else {
              mapExpected.put(saleOrder.getExpectedRealisationDate(), amountCompanyCurr);
            }
          } else {
            if (mapConfirmed.containsKey(saleOrder.getExpectedRealisationDate())) {
              mapConfirmed.put(
                  saleOrder.getExpectedRealisationDate(),
                  mapConfirmed.get(saleOrder.getExpectedRealisationDate()).add(amountCompanyCurr));
            } else {
              mapConfirmed.put(saleOrder.getExpectedRealisationDate(), amountCompanyCurr);
            }
          }
        }
      }
    }
  }

  @Transactional
  public void populateWithForecasts(ForecastRecap forecastRecap) throws AxelorException {
    List<Forecast> forecastList = new ArrayList<Forecast>();
    ForecastRecapLineType forecastForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_FORECAST);
    forecastList =
        forecastRepo
            .all()
            .filter(
                "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3"
                    + (forecastRecap.getBankDetails() != null ? " AND self.bankDetails = ?4" : "")
                    + " AND self.realizationDate IS NULL",
                forecastRecap.getFromDate(),
                forecastRecap.getToDate(),
                forecastRecap.getCompany(),
                forecastRecap.getBankDetails())
            .fetch();
    for (Forecast forecast : forecastList) {
      forecast = forecastRepo.find(forecast.getId());
      LocalDate realizationDate =
          forecast
                  .getEstimatedDate()
                  .isAfter(appBaseService.getTodayDate(forecastRecap.getCompany()))
              ? forecast.getEstimatedDate()
              : appBaseService.getTodayDate(forecastRecap.getCompany());
      this.createForecastRecapLine(
          realizationDate,
          forecast.getTypeSelect(),
          forecast.getAmount().abs(),
          Forecast.class.getName(),
          forecast.getId(),
          forecast.getForecastSeq(),
          forecastRecapLineTypeRepo.find(forecastForecastRecapLineType.getId()),
          forecastRecapRepo.find(forecastRecap.getId()));
      forecastRepo.save(forecast);
      JPA.clear();
    }
  }

  public void populateWithForecastsNoSave(ForecastRecap forecastRecap) throws AxelorException {
    ForecastRecapLineType forecastForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_FORECAST);
    List<Forecast> forecastList = new ArrayList<Forecast>();
    forecastList =
        forecastRepo
            .all()
            .filter(
                "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3"
                    + (forecastRecap.getBankDetails() != null ? " AND self.bankDetails = ?4" : "")
                    + " AND self.realizationDate IS NULL",
                forecastRecap.getFromDate(),
                forecastRecap.getToDate(),
                forecastRecap.getCompany(),
                forecastRecap.getBankDetails())
            .fetch();
    for (Forecast forecast : forecastList) {
      forecast = forecastRepo.find(forecast.getId());
      LocalDate realizationDate =
          forecast
                  .getEstimatedDate()
                  .isAfter(appBaseService.getTodayDate(forecastRecap.getCompany()))
              ? forecast.getEstimatedDate()
              : appBaseService.getTodayDate(forecastRecap.getCompany());
      this.createForecastRecapLine(
          realizationDate,
          forecast.getTypeSelect(),
          forecast.getAmount().abs(),
          Forecast.class.getName(),
          forecast.getId(),
          forecast.getForecastSeq(),
          forecastRecapLineTypeRepo.find(forecastForecastRecapLineType.getId()),
          forecastRecapRepo.find(forecastRecap.getId()));
      JPA.clear();
    }
  }

  public void getForecasts(
      ForecastRecap forecastRecap,
      Map<LocalDate, BigDecimal> mapExpected,
      Map<LocalDate, BigDecimal> mapConfirmed) {
    List<Forecast> forecastList = new ArrayList<Forecast>();
    if (forecastRecap.getBankDetails() != null) {
      forecastList =
          Beans.get(ForecastRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " self.bankDetails = ?4 AND self.realizationDate IS NULL",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails())
              .fetch();
    } else {
      forecastList =
          Beans.get(ForecastRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " self.realizationDate IS NULL",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany())
              .fetch();
    }
    for (Forecast forecast : forecastList) {
      if (forecast.getAmount().compareTo(BigDecimal.ZERO) != -1) {
        if (mapExpected.containsKey(forecast.getEstimatedDate())) {
          mapExpected.put(
              forecast.getEstimatedDate(),
              mapExpected.get(forecast.getEstimatedDate()).add(forecast.getAmount()));
        } else {
          mapExpected.put(forecast.getEstimatedDate(), forecast.getAmount());
        }
      }
    }
  }

  public void populateWithExpenses(ForecastRecap forecastRecap) throws AxelorException {
    ForecastRecapLineType expenseForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_EXPENSE);
    List<Integer> statusList =
        StringTool.getIntegerList(expenseForecastRecapLineType.getStatusSelect());

    if (statusList.isEmpty()) {
      statusList.add(ExpenseRepository.STATUS_VALIDATED);
    }
    List<Expense> expenseList = new ArrayList<Expense>();
    expenseList =
        expenseRepo
            .all()
            .filter(
                "self.validationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND self.statusSelect IN (?4)"
                    + (forecastRecap.getBankDetails() != null ? " AND self.bankDetails = ?5" : ""),
                forecastRecap.getFromDate(),
                forecastRecap.getToDate(),
                forecastRecap.getCompany(),
                statusList,
                forecastRecap.getBankDetails())
            .fetch();

    for (Expense expense : expenseList) {
      expense = expenseRepo.find(expense.getId());
      this.createForecastRecapLine(
          expense.getValidationDate(),
          expenseForecastRecapLineType.getTypeSelect(),
          expense.getExTaxTotal(),
          Expense.class.getName(),
          expense.getId(),
          expense.getExpenseSeq(),
          forecastRecapLineTypeRepo.find(expenseForecastRecapLineType.getId()),
          forecastRecapRepo.find(forecastRecap.getId()));
      JPA.clear();
    }
  }

  @Transactional
  public void createForecastRecapLine(
      LocalDate date,
      int type,
      BigDecimal amount,
      String relatedToSelect,
      Long relatedToSelectId,
      String relatedToSelectName,
      ForecastRecapLineType forecastRecapLineType,
      ForecastRecap forecastRecap) {
    ForecastRecapLine forecastRecapLine = new ForecastRecapLine();
    forecastRecapLine.setEstimatedDate(date);
    forecastRecapLine.setTypeSelect(type);
    forecastRecapLine.setAmount(type == 1 ? amount.abs() : amount.negate());
    forecastRecapLine.setRelatedToSelect(relatedToSelect);
    forecastRecapLine.setRelatedToSelectId(relatedToSelectId);
    forecastRecapLine.setRelatedToSelectName(relatedToSelectName);
    forecastRecapLine.setForecastRecapLineType(forecastRecapLineType);
    forecastRecap.addForecastRecapLineListItem(forecastRecapLine);
    forecastRecapRepo.save(forecastRecap);
  }

  public void computeForecastRecapLineBalance(ForecastRecap forecastRecap) {

    List<ForecastRecapLine> forecastRecapLines = forecastRecap.getForecastRecapLineList();

    Collections.sort(
        forecastRecapLines,
        new Comparator<ForecastRecapLine>() {
          @Override
          public int compare(
              ForecastRecapLine forecastRecapLine1, ForecastRecapLine forecastRecapLine2) {
            int compareEstimatedDate =
                forecastRecapLine1
                    .getEstimatedDate()
                    .compareTo(forecastRecapLine2.getEstimatedDate());
            int compareTypeSelect =
                forecastRecapLine1
                    .getForecastRecapLineType()
                    .getSequence()
                    .compareTo(forecastRecapLine2.getForecastRecapLineType().getSequence());
            int compareId = forecastRecapLine1.getId().compareTo(forecastRecapLine2.getId());
            if (compareEstimatedDate == 0) {
              return compareTypeSelect == 0 ? compareId : compareTypeSelect;
            }
            return compareEstimatedDate;
          }
        });

    for (ForecastRecapLine forecastRecapLine : forecastRecapLines) {
      forecastRecap.setCurrentBalance(
          forecastRecap.getCurrentBalance().add(forecastRecapLine.getAmount()));
      forecastRecapLine.setBalance(forecastRecap.getCurrentBalance());
    }
    forecastRecap.setForecastRecapLineList(forecastRecapLines);
  }

  public String getForecastRecapFileLink(ForecastRecap forecastRecap, String reportType)
      throws AxelorException {
    String title = I18n.get(ITranslation.CASH_MANAGEMENT_REPORT_TITLE);
    title += "-" + forecastRecap.getForecastRecapSeq();

    return ReportFactory.createReport(IReport.FORECAST_RECAP, title + "-${date}")
        .addParam("ForecastRecapId", forecastRecap.getId().toString())
        .addParam(
            "Timezone",
            forecastRecap.getCompany() != null ? forecastRecap.getCompany().getTimezone() : null)
        .addParam("Locale", ReportSettings.getPrintingLocale(null))
        .addFormat(reportType)
        .generate()
        .getFileLink();
  }

  protected ForecastRecapLineType getForecastRecapLineType(int elementSelect)
      throws AxelorException {

    ForecastRecapLineType forecastRecapLineType =
        forecastRecapLineTypeRepo.all().filter("self.elementSelect = ?1", elementSelect).fetchOne();

    if (forecastRecapLineType != null) {
      return forecastRecapLineType;
    }

    throw new AxelorException(
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        I18n.get(IExceptionMessage.FORECAST_RECAP_MISSING_FORECAST_RECAP_LINE_TYPE),
        elementSelect);

    // TODO get the right label in fact of integer value

  }

  protected List<ForecastRecapLineType> getForecastRecapLineTypeList(int elementInvoice)
      throws AxelorException {
    List<ForecastRecapLineType> forecastRecapLineTypeList =
        forecastRecapLineTypeRepo.all().filter("self.elementSelect = ?1", elementInvoice).fetch();

    if (forecastRecapLineTypeList == null || forecastRecapLineTypeList.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.FORECAST_RECAP_MISSING_FORECAST_RECAP_LINE_TYPE),
          elementInvoice);
    }
    return forecastRecapLineTypeList;
  }
}
