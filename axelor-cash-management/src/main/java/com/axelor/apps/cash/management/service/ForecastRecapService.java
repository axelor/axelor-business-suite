/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
import com.axelor.apps.cash.management.db.ForecastReason;
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
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ForecastRecapService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject protected AppBaseService appBaseService;

  @Inject protected ForecastRepository forecastRepo;

  @Inject protected ForecastRecapLineRepository forecastRecapLineRepo;

  @Inject protected CurrencyService currencyService;
  @Inject protected ForecastRecapLineTypeRepository forecastRecapLineTypeRepository;

  @Transactional
  public void populate(ForecastRecap forecastRecap) throws AxelorException {
    List<ForecastRecapLine> forecastRecapLineList = forecastRecap.getForecastRecapLineList();
    if (forecastRecapLineList != null && !forecastRecapLineList.isEmpty()) {
      for (ForecastRecapLine forecastRecapLine : forecastRecapLineList) {
        if (forecastRecapLine.getId() != null && forecastRecapLine.getId() > 0) {
          forecastRecapLineRepo.remove(forecastRecapLine);
        }
      }
      forecastRecapLineList.clear();
    }
    forecastRecap.setCurrentBalance(forecastRecap.getStartingBalance());
    if (forecastRecap.getOpportunitiesTypeSelect() != null
        && forecastRecap.getOpportunitiesTypeSelect()
            > ForecastRecapRepository.OPPORTUNITY_TYPE_NO) {
      this.populateWithOpportunities(forecastRecap);
    }
    this.populateWithInvoices(forecastRecap);
    this.populateWithSalaries(forecastRecap);
    this.populateWithTimetables(forecastRecap);
    this.populateWithForecasts(forecastRecap);
    this.populateWithExpenses(forecastRecap);

    Beans.get(ForecastRecapRepository.class).save(forecastRecap);
    this.computeForecastRecapLineBalance(forecastRecap);
    forecastRecap.setEndingBalance(forecastRecap.getCurrentBalance());
    forecastRecap.setCalculationDate(appBaseService.getTodayDate());
    Beans.get(ForecastRecapRepository.class).save(forecastRecap);
  }

  public void populateWithOpportunities(ForecastRecap forecastRecap) throws AxelorException {
    List<Opportunity> opportunityList = new ArrayList<Opportunity>();
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        opportunityList =
            Beans.get(OpportunityRepository.class)
                .all()
                .filter(
                    "self.company = ?1 AND self.bankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ") AND self.expectedCloseDate BETWEEN ?2 AND ?3 AND self.saleOrderList IS EMPTY",
                    forecastRecap.getCompany(),
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate())
                .fetch();
      }
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
    ForecastRecapLineType opportuntityForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_OPPORTUNITY);
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
                    appBaseService.getTodayDate())
                .setScale(2, RoundingMode.HALF_UP);
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                opportunity.getExpectedCloseDate(),
                opportuntityForecastRecapLineType.getTypeSelect(),
                amountCompanyCurr,
                null,
                null,
                null,
                opportuntityForecastRecapLineType));
      } else if (forecastRecap.getOpportunitiesTypeSelect()
          == ForecastRecapRepository.OPPORTUNITY_TYPE_BEST) {
        amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    opportunity.getCurrency(),
                    opportunity.getCompany().getCurrency(),
                    new BigDecimal(opportunity.getBestCase())
                        .multiply(opportunity.getProbability())
                        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP),
                    appBaseService.getTodayDate())
                .setScale(2, RoundingMode.HALF_UP);
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                opportunity.getExpectedCloseDate(),
                opportuntityForecastRecapLineType.getTypeSelect(),
                amountCompanyCurr,
                null,
                null,
                null,
                opportuntityForecastRecapLineType));
      } else {
        amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    opportunity.getCurrency(),
                    opportunity.getCompany().getCurrency(),
                    new BigDecimal(opportunity.getWorstCase())
                        .multiply(opportunity.getProbability())
                        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP),
                    appBaseService.getTodayDate())
                .setScale(2, RoundingMode.HALF_UP);
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                opportunity.getExpectedCloseDate(),
                opportuntityForecastRecapLineType.getTypeSelect(),
                amountCompanyCurr,
                null,
                null,
                null,
                opportuntityForecastRecapLineType));
      }
    }
  }

  public void getOpportunities(
      ForecastRecap forecastRecap,
      Map<LocalDate, BigDecimal> mapExpected,
      Map<LocalDate, BigDecimal> mapConfirmed)
      throws AxelorException {
    List<Opportunity> opportunityList = new ArrayList<Opportunity>();
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        opportunityList =
            Beans.get(OpportunityRepository.class)
                .all()
                .filter(
                    "self.company = ?1 AND self.bankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ") AND self.expectedCloseDate BETWEEN ?2 AND ?3 AND self.saleOrderList IS EMPTY",
                    forecastRecap.getCompany(),
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate())
                .fetch();
      }
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
                    appBaseService.getTodayDate())
                .setScale(2, RoundingMode.HALF_UP);
      } else if (forecastRecap.getOpportunitiesTypeSelect()
          == ForecastRecapRepository.OPPORTUNITY_TYPE_BEST) {
        amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    opportunity.getCurrency(),
                    opportunity.getCompany().getCurrency(),
                    new BigDecimal(opportunity.getBestCase())
                        .multiply(opportunity.getProbability())
                        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP),
                    appBaseService.getTodayDate())
                .setScale(2, RoundingMode.HALF_UP);
      } else {
        amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    opportunity.getCurrency(),
                    opportunity.getCompany().getCurrency(),
                    new BigDecimal(opportunity.getWorstCase())
                        .multiply(opportunity.getProbability())
                        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP),
                    appBaseService.getTodayDate())
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
    ForecastRecapLineType invoiceForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_INVOICE);
    List<Integer> statusList =
        StringTool.getIntegerList(invoiceForecastRecapLineType.getStatusSelect());

    if (statusList.isEmpty()) {
      statusList.add(InvoiceRepository.STATUS_VALIDATED);
    }
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        invoiceList =
            Beans.get(InvoiceRepository.class)
                .all()
                .filter(
                    "self.company = ?1 AND self.companyBankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ") AND self.statusSelect IN (?2) AND self.operationTypeSelect = ?3 AND self.estimatedPaymentDate BETWEEN ?4 AND ?5 AND self.companyInTaxTotalRemaining != 0",
                    forecastRecap.getCompany(),
                    statusList,
                    invoiceForecastRecapLineType.getOperationTypeSelect(),
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate())
                .fetch();
      }
    } else {
      invoiceList =
          Beans.get(InvoiceRepository.class)
              .all()
              .filter(
                  "self.company = ?1 AND self.statusSelect IN (?2) AND self.operationTypeSelect = ?3 AND self.estimatedPaymentDate BETWEEN ?4 AND ?5 AND self.companyInTaxTotalRemaining != 0",
                  forecastRecap.getCompany(),
                  statusList,
                  invoiceForecastRecapLineType.getOperationTypeSelect(),
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

      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              invoice.getEstimatedPaymentDate(),
              invoiceForecastRecapLineType.getTypeSelect(),
              amount,
              Invoice.class.getName(),
              invoice.getId(),
              invoice.getInvoiceId(),
              invoiceForecastRecapLineType));
    }
  }

  public void getInvoices(
      ForecastRecap forecastRecap,
      Map<LocalDate, BigDecimal> mapExpected,
      Map<LocalDate, BigDecimal> mapConfirmed) {
    List<Invoice> invoiceList = new ArrayList<Invoice>();
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        invoiceList =
            Beans.get(InvoiceRepository.class)
                .all()
                .filter(
                    "self.company = ?1 AND self.companyBankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ") AND self.statusSelect = 3 AND self.estimatedPaymentDate BETWEEN ?2 AND ?3 AND self.companyInTaxTotalRemaining != 0",
                    forecastRecap.getCompany(),
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate())
                .fetch();
      }
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
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        employeeList =
            Beans.get(EmployeeRepository.class)
                .all()
                .filter(
                    "self.mainEmploymentContract.payCompany = ?1 AND self.bankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ")",
                    forecastRecap.getCompany())
                .fetch();
      }
    } else {
      employeeList =
          Beans.get(EmployeeRepository.class)
              .all()
              .filter("self.mainEmploymentContract.payCompany = ?1", forecastRecap.getCompany())
              .fetch();
    }
    LocalDate itDate =
        LocalDate.parse(forecastRecap.getFromDate().toString(), DateTimeFormatter.ISO_DATE);
    while (!itDate.isAfter(forecastRecap.getToDate())) {
      LocalDate monthEnd = itDate.withDayOfMonth(itDate.lengthOfMonth());
      if (itDate.isEqual(monthEnd)) {
        for (Employee employee : employeeList) {
          forecastRecap.addForecastRecapLineListItem(
              this.createForecastRecapLine(
                  itDate,
                  salaryForecastRecapLineType.getTypeSelect(),
                  employee
                      .getHourlyRate()
                      .multiply(employee.getWeeklyWorkHours().multiply(new BigDecimal(4))),
                  null,
                  null,
                  null,
                  salaryForecastRecapLineType));
        }
        itDate = itDate.plusMonths(1);
      } else {
        itDate = monthEnd;
      }
    }
  }

  public void populateWithTimetables(ForecastRecap forecastRecap) throws AxelorException {
    ForecastRecapLineType timetableForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_TIMETABLE);
    List<Integer> statusList =
        StringTool.getIntegerList(timetableForecastRecapLineType.getStatusSelect());
    List<Timetable> timetableSaleOrderList = new ArrayList<Timetable>();
    List<Timetable> timetablePurchaseOrderList = new ArrayList<Timetable>();
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        if (timetableForecastRecapLineType.getTimetableLinkedSelect()
            == ForecastRecapLineTypeRepository.TIMETABLE_SALE_ORDER) {
          if (statusList.isEmpty()) {
            statusList.add(SaleOrderRepository.STATUS_ORDER_CONFIRMED);
          }
          timetableSaleOrderList =
              Beans.get(TimetableRepository.class)
                  .all()
                  .filter(
                      "self.estimatedDate BETWEEN ?1 AND ?2 AND self.saleOrder.company = ?3 AND"
                          + " self.saleOrder.companyBankDetails.id IN ("
                          + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                          + ") AND self.saleOrder.statusSelect IN (?4) AND self.amount != 0",
                      forecastRecap.getFromDate(),
                      forecastRecap.getToDate(),
                      forecastRecap.getCompany(),
                      statusList)
                  .fetch();
        }
        if (timetableForecastRecapLineType.getTimetableLinkedSelect()
            == ForecastRecapLineTypeRepository.TIMETABLE_PURCHASE_ORDER) {
          if (statusList.isEmpty()) {
            statusList.add(PurchaseOrderRepository.STATUS_VALIDATED);
          }
          timetablePurchaseOrderList =
              Beans.get(TimetableRepository.class)
                  .all()
                  .filter(
                      "self.estimatedDate BETWEEN ?1 AND ?2 AND self.purchaseOrder.company = ?3 AND"
                          + " self.purchaseOrder.companyBankDetails.id IN ("
                          + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                          + ") AND self.purchaseOrder.statusSelect IN (?4) AND self.amount != 0",
                      forecastRecap.getFromDate(),
                      forecastRecap.getToDate(),
                      forecastRecap.getCompany(),
                      statusList)
                  .fetch();
        }
      }
    } else {
      if (timetableForecastRecapLineType.getTimetableLinkedSelect()
          == ForecastRecapLineTypeRepository.TIMETABLE_SALE_ORDER) {
        if (statusList.isEmpty()) {
          statusList.add(SaleOrderRepository.STATUS_ORDER_CONFIRMED);
        }
        timetableSaleOrderList =
            Beans.get(TimetableRepository.class)
                .all()
                .filter(
                    "self.estimatedDate BETWEEN ?1 AND ?2 AND self.saleOrder.company = ?3 AND"
                        + " self.saleOrder.statusSelect IN (?4) AND self.amount != 0",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany(),
                    statusList)
                .fetch();
      }
      if (timetableForecastRecapLineType.getTimetableLinkedSelect()
          == ForecastRecapLineTypeRepository.TIMETABLE_PURCHASE_ORDER) {
        if (statusList.isEmpty()) {
          statusList.add(PurchaseOrderRepository.STATUS_VALIDATED);
        }
        timetablePurchaseOrderList =
            Beans.get(TimetableRepository.class)
                .all()
                .filter(
                    "self.estimatedDate BETWEEN ?1 AND ?2 AND self.purchaseOrder.company = ?3 AND"
                        + " self.purchaseOrder.statusSelect IN (?4) AND self.amount != 0",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany(),
                    statusList)
                .fetch();
      }
    }
    if (timetableForecastRecapLineType.getTimetableLinkedSelect()
        == ForecastRecapLineTypeRepository.TIMETABLE_SALE_ORDER) {

      for (Timetable timetable : timetableSaleOrderList) {
        BigDecimal amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    timetable.getSaleOrder().getCurrency(),
                    timetable.getSaleOrder().getCompany().getCurrency(),
                    timetable.getAmount(),
                    appBaseService.getTodayDate())
                .setScale(2, RoundingMode.HALF_UP);
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                timetable.getEstimatedDate(),
                timetableForecastRecapLineType.getTypeSelect(),
                amountCompanyCurr,
                null,
                null,
                null,
                timetableForecastRecapLineType));
      }
    }
    if (timetableForecastRecapLineType.getTimetableLinkedSelect()
        == ForecastRecapLineTypeRepository.TIMETABLE_SALE_ORDER) {
      for (Timetable timetable : timetablePurchaseOrderList) {
        BigDecimal amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    timetable.getPurchaseOrder().getCurrency(),
                    timetable.getPurchaseOrder().getCompany().getCurrency(),
                    timetable.getAmount(),
                    appBaseService.getTodayDate())
                .setScale(2, RoundingMode.HALF_UP);
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                timetable.getEstimatedDate(),
                timetableForecastRecapLineType.getTypeSelect(),
                amountCompanyCurr,
                null,
                null,
                null,
                timetableForecastRecapLineType));
      }
    }
  }

  public void populateWithTimetablesOrOrders(ForecastRecap forecastRecap) throws AxelorException {
    List<Timetable> timetableSaleOrderList = new ArrayList<Timetable>();
    List<SaleOrder> saleOrderList = new ArrayList<SaleOrder>();
    List<Timetable> timetablePurchaseOrderList = new ArrayList<Timetable>();
    List<PurchaseOrder> purchaseOrderList = new ArrayList<PurchaseOrder>();
    ForecastRecapLineType timetableForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_TIMETABLE);
    List<Integer> statusList =
        StringTool.getIntegerList(timetableForecastRecapLineType.getStatusSelect());

    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        if (timetableForecastRecapLineType.getTimetableLinkedSelect()
            == ForecastRecapLineTypeRepository.TIMETABLE_SALE_ORDER) {
          if (statusList.isEmpty()) {
            statusList.add(SaleOrderRepository.STATUS_ORDER_CONFIRMED);
          }
          timetableSaleOrderList =
              Beans.get(TimetableRepository.class)
                  .all()
                  .filter(
                      "self.estimatedDate BETWEEN ?1 AND ?2 AND self.saleOrder.company = ?3 AND"
                          + " self.saleOrder.companyBankDetails.id IN ("
                          + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                          + ") AND self.saleOrder.statusSelect IN (?4) AND self.amount != 0",
                      forecastRecap.getFromDate(),
                      forecastRecap.getToDate(),
                      forecastRecap.getCompany(),
                      statusList)
                  .fetch();
        }
        if (timetableForecastRecapLineType.getTimetableLinkedSelect()
            == ForecastRecapLineTypeRepository.TIMETABLE_PURCHASE_ORDER) {

          if (statusList.isEmpty()) {
            statusList.add(PurchaseOrderRepository.STATUS_VALIDATED);
          }
          timetablePurchaseOrderList =
              Beans.get(TimetableRepository.class)
                  .all()
                  .filter(
                      "self.estimatedDate BETWEEN ?1 AND ?2 AND self.purchaseOrder.company = ?3 AND"
                          + " self.purchaseOrder.companyBankDetails.id IN ("
                          + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                          + ") AND self.purchaseOrder.statusSelect IN (?4) AND self.amount != 0",
                      forecastRecap.getFromDate(),
                      forecastRecap.getToDate(),
                      forecastRecap.getCompany(),
                      statusList)
                  .fetch();
        }
      }
    } else {
      if (timetableForecastRecapLineType.getTimetableLinkedSelect()
          == ForecastRecapLineTypeRepository.TIMETABLE_SALE_ORDER) {
        if (statusList.isEmpty()) {
          statusList.add(SaleOrderRepository.STATUS_ORDER_CONFIRMED);
        }
        timetableSaleOrderList =
            Beans.get(TimetableRepository.class)
                .all()
                .filter(
                    "self.estimatedDate BETWEEN ?1 AND ?2 AND self.saleOrder.company = ?3 AND"
                        + " self.saleOrder.statusSelect IN (?4) AND self.amount != 0",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany(),
                    statusList)
                .fetch();
      }
      if (timetableForecastRecapLineType.getTimetableLinkedSelect()
          == ForecastRecapLineTypeRepository.TIMETABLE_PURCHASE_ORDER) {

        if (statusList.isEmpty()) {
          statusList.add(PurchaseOrderRepository.STATUS_VALIDATED);
        }
        timetablePurchaseOrderList =
            Beans.get(TimetableRepository.class)
                .all()
                .filter(
                    "self.estimatedDate BETWEEN ?1 AND ?2 AND self.purchaseOrder.company = ?3 AND"
                        + " self.purchaseOrder.statusSelect IN (?4) AND self.amount != 0",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany(),
                    statusList)
                .fetch();
      }
    }
    if (timetableForecastRecapLineType.getTimetableLinkedSelect()
        == ForecastRecapLineTypeRepository.TIMETABLE_SALE_ORDER) {
      for (Timetable timetable : timetableSaleOrderList) {
        saleOrderList.add(timetable.getSaleOrder());
        BigDecimal amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    timetable.getSaleOrder().getCurrency(),
                    timetable.getSaleOrder().getCompany().getCurrency(),
                    timetable.getAmount(),
                    appBaseService.getTodayDate())
                .setScale(2, RoundingMode.HALF_UP);
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                timetable.getEstimatedDate(),
                timetableForecastRecapLineType.getTypeSelect(),
                amountCompanyCurr,
                null,
                null,
                null,
                timetableForecastRecapLineType));
      }
    }
    if (timetableForecastRecapLineType.getTimetableLinkedSelect()
        == ForecastRecapLineTypeRepository.TIMETABLE_PURCHASE_ORDER) {
      for (Timetable timetable : timetablePurchaseOrderList) {
        purchaseOrderList.add(timetable.getPurchaseOrder());
        BigDecimal amountCompanyCurr =
            currencyService
                .getAmountCurrencyConvertedAtDate(
                    timetable.getPurchaseOrder().getCurrency(),
                    timetable.getPurchaseOrder().getCompany().getCurrency(),
                    timetable.getAmount(),
                    appBaseService.getTodayDate())
                .setScale(2, RoundingMode.HALF_UP);
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                timetable.getEstimatedDate(),
                timetableForecastRecapLineType.getTypeSelect(),
                amountCompanyCurr,
                null,
                null,
                null,
                timetableForecastRecapLineType));
      }
    }
    List<SaleOrder> saleOrderNoTimeTableList = new ArrayList<SaleOrder>();
    List<PurchaseOrder> purchaseOrderNoTimetableList = new ArrayList<PurchaseOrder>();
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        if (timetableForecastRecapLineType.getTimetableLinkedSelect()
            == ForecastRecapLineTypeRepository.TIMETABLE_SALE_ORDER) {
          saleOrderNoTimeTableList =
              Beans.get(SaleOrderRepository.class)
                  .all()
                  .filter(
                      "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                          + " self.companyBankDetails.id IN ("
                          + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                          + ") AND self.statusSelect IN (?4)",
                      forecastRecap.getFromDate(),
                      forecastRecap.getToDate(),
                      forecastRecap.getCompany(),
                      statusList)
                  .fetch();
        }
        if (timetableForecastRecapLineType.getTimetableLinkedSelect()
            == ForecastRecapLineTypeRepository.TIMETABLE_PURCHASE_ORDER) {
          purchaseOrderNoTimetableList =
              Beans.get(PurchaseOrderRepository.class)
                  .all()
                  .filter(
                      "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                          + " self.companyBankDetails.id IN ("
                          + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                          + ") AND self.statusSelect IN (?4)",
                      forecastRecap.getFromDate(),
                      forecastRecap.getToDate(),
                      forecastRecap.getCompany(),
                      statusList)
                  .fetch();
        }
      }
    } else {
      if (timetableForecastRecapLineType.getTimetableLinkedSelect()
          == ForecastRecapLineTypeRepository.TIMETABLE_SALE_ORDER) {
        saleOrderNoTimeTableList =
            Beans.get(SaleOrderRepository.class)
                .all()
                .filter(
                    "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND self.statusSelect IN (?4)",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany(),
                    statusList)
                .fetch();
      }
      if (timetableForecastRecapLineType.getTimetableLinkedSelect()
          == ForecastRecapLineTypeRepository.TIMETABLE_PURCHASE_ORDER) {
        purchaseOrderNoTimetableList =
            Beans.get(PurchaseOrderRepository.class)
                .all()
                .filter(
                    "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                        + " self.statusSelect IN (?4)",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany(),
                    statusList)
                .fetch();
      }
    }
    if (timetableForecastRecapLineType.getTimetableLinkedSelect()
        == ForecastRecapLineTypeRepository.TIMETABLE_SALE_ORDER) {
      for (SaleOrder saleOrder : saleOrderNoTimeTableList) {
        if (!saleOrderList.contains(saleOrder)) {
          BigDecimal amountCompanyCurr =
              saleOrder.getCompanyExTaxTotal().subtract(saleOrder.getAmountInvoiced());
          if (amountCompanyCurr.compareTo(BigDecimal.ZERO) == 0) {
            forecastRecap.addForecastRecapLineListItem(
                this.createForecastRecapLine(
                    saleOrder.getExpectedRealisationDate(),
                    timetableForecastRecapLineType.getTypeSelect(),
                    amountCompanyCurr,
                    SaleOrder.class.getName(),
                    saleOrder.getId(),
                    saleOrder.getSaleOrderSeq(),
                    timetableForecastRecapLineType));
          }
        }
      }
    }
    if (timetableForecastRecapLineType.getTimetableLinkedSelect()
        == ForecastRecapLineTypeRepository.TIMETABLE_PURCHASE_ORDER) {
      for (PurchaseOrder purchaseOrder : purchaseOrderNoTimetableList) {
        if (!purchaseOrderList.contains(purchaseOrder)) {
          BigDecimal amountCompanyCurr =
              purchaseOrder.getCompanyExTaxTotal().subtract(purchaseOrder.getAmountInvoiced());
          if (amountCompanyCurr.compareTo(BigDecimal.ZERO) == 0) {
            forecastRecap.addForecastRecapLineListItem(
                this.createForecastRecapLine(
                    purchaseOrder.getExpectedRealisationDate(),
                    timetableForecastRecapLineType.getTypeSelect(),
                    amountCompanyCurr,
                    PurchaseOrder.class.getName(),
                    purchaseOrder.getId(),
                    purchaseOrder.getPurchaseOrderSeq(),
                    timetableForecastRecapLineType));
          }
        }
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
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        timetableSaleOrderList =
            Beans.get(TimetableRepository.class)
                .all()
                .filter(
                    "self.estimatedDate BETWEEN ?1 AND ?2 AND self.saleOrder.company = ?3 AND"
                        + " self.saleOrder.companyBankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ") AND (self.saleOrder.statusSelect = 2 OR self.saleOrder.statusSelect = 3) AND self.amount != 0",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany())
                .fetch();
      }
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
                  appBaseService.getTodayDate())
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
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        saleOrderNoTimeTableList =
            Beans.get(SaleOrderRepository.class)
                .all()
                .filter(
                    "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                        + " self.companyBankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ") AND (self.statusSelect = 2 OR self.statusSelect = 3)",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany())
                .fetch();
      }
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
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        forecastList =
            Beans.get(ForecastRepository.class)
                .all()
                .filter(
                    "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                        + " self.bankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ") AND (self.realizedSelect = 2 OR (self.realizedSelect = 3 AND self.estimatedDate <= ?4))",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany(),
                    appBaseService.getTodayDate())
                .fetch();
      }
    } else {
      forecastList =
          Beans.get(ForecastRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " (self.realizedSelect = 2 OR (self.realizedSelect = 3 AND self.estimatedDate <= ?4))",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  appBaseService.getTodayDate())
              .fetch();
    }
    for (Forecast forecast : forecastList) {
      ForecastReason forecastReason = forecast.getForecastReason();
      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              forecast.getEstimatedDate(),
              forecast.getTypeSelect(),
              forecast.getAmount(),
              ForecastReason.class.getName(),
              forecastReason.getId(),
              forecastReason.getReason(),
              forecastForecastRecapLineType));
      forecast.setRealizedSelect(ForecastRepository.REALISED_SELECT_YES);
      forecastRepo.save(forecast);
    }
  }

  public void populateWithForecastsNoSave(ForecastRecap forecastRecap) throws AxelorException {
    ForecastRecapLineType forecastForecastRecapLineType =
        this.getForecastRecapLineType(ForecastRecapLineTypeRepository.ELEMENT_FORECAST);
    List<Forecast> forecastList = new ArrayList<Forecast>();
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        forecastList =
            Beans.get(ForecastRepository.class)
                .all()
                .filter(
                    "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                        + " self.bankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ") AND (self.realizedSelect = 2 OR (self.realizedSelect = 3 AND self.estimatedDate <= ?4))",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany(),
                    appBaseService.getTodayDate())
                .fetch();
      }
    } else {
      forecastList =
          Beans.get(ForecastRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " (self.realizedSelect = 2 OR (self.realizedSelect = 3 AND self.estimatedDate <= ?4))",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  appBaseService.getTodayDate())
              .fetch();
    }
    for (Forecast forecast : forecastList) {
      ForecastReason forecastReason = forecast.getForecastReason();
      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              forecast.getEstimatedDate(),
              forecast.getTypeSelect(),
              forecast.getAmount(),
              ForecastReason.class.getName(),
              forecastReason.getId(),
              forecastReason.getReason(),
              forecastForecastRecapLineType));
    }
  }

  public void getForecasts(
      ForecastRecap forecastRecap,
      Map<LocalDate, BigDecimal> mapExpected,
      Map<LocalDate, BigDecimal> mapConfirmed) {
    List<Forecast> forecastList = new ArrayList<Forecast>();
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        forecastList =
            Beans.get(ForecastRepository.class)
                .all()
                .filter(
                    "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                        + " self.bankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ") AND (self.realizedSelect = 2 OR self.realizedSelect = 3)",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany())
                .fetch();
      }
    } else {
      forecastList =
          Beans.get(ForecastRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " (self.realizedSelect = 2 OR self.realizedSelect = 3)",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany())
              .fetch();
    }
    for (Forecast forecast : forecastList) {
      if (forecast.getTypeSelect() == 1) {
        if (forecast.getRealizedSelect() == 2) {
          if (mapExpected.containsKey(forecast.getEstimatedDate())) {
            mapExpected.put(
                forecast.getEstimatedDate(),
                mapExpected.get(forecast.getEstimatedDate()).add(forecast.getAmount()));
          } else {
            mapExpected.put(forecast.getEstimatedDate(), forecast.getAmount());
          }
        } else {
          if (mapConfirmed.containsKey(forecast.getEstimatedDate())) {
            mapConfirmed.put(
                forecast.getEstimatedDate(),
                mapConfirmed.get(forecast.getEstimatedDate()).add(forecast.getAmount()));
          } else {
            mapConfirmed.put(forecast.getEstimatedDate(), forecast.getAmount());
          }
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
    if (forecastRecap.getCalculationModeSelect() == ForecastRecapRepository.SPECIFIC_BANK_DETAILS) {
      if (CollectionUtils.isNotEmpty(forecastRecap.getBankDetailsSet())) {
        expenseList =
            Beans.get(ExpenseRepository.class)
                .all()
                .filter(
                    "self.validationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                        + " self.bankDetails.id IN ("
                        + StringTool.getIdListString(forecastRecap.getBankDetailsSet())
                        + ") AND self.statusSelect IN (?4)",
                    forecastRecap.getFromDate(),
                    forecastRecap.getToDate(),
                    forecastRecap.getCompany(),
                    statusList)
                .fetch();
      }
    } else {
      expenseList =
          Beans.get(ExpenseRepository.class)
              .all()
              .filter(
                  "self.validationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " self.statusSelect IN (?4)",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  statusList)
              .fetch();
    }
    for (Expense expense : expenseList) {
      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              expense.getValidationDate(),
              expenseForecastRecapLineType.getTypeSelect(),
              expense.getExTaxTotal(),
              Expense.class.getName(),
              expense.getId(),
              expense.getExpenseSeq(),
              expenseForecastRecapLineType));
    }
  }

  public ForecastRecapLine createForecastRecapLine(
      LocalDate date,
      int type,
      BigDecimal amount,
      String relatedToSelect,
      Long relatedToSelectId,
      String relatedToSelectName,
      ForecastRecapLineType forecastRecapLineType) {
    ForecastRecapLine forecastRecapLine = new ForecastRecapLine();
    forecastRecapLine.setEstimatedDate(date);
    forecastRecapLine.setTypeSelect(type);
    forecastRecapLine.setAmount(amount);
    forecastRecapLine.setRelatedToSelect(relatedToSelect);
    forecastRecapLine.setRelatedToSelectId(relatedToSelectId);
    forecastRecapLine.setRelatedToSelectName(relatedToSelectName);
    forecastRecapLine.setForecastRecapLineType(forecastRecapLineType);
    return forecastRecapLine;
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
      if (forecastRecapLine.getTypeSelect() == 1) {
        forecastRecap.setCurrentBalance(
            forecastRecap.getCurrentBalance().add(forecastRecapLine.getAmount()));
        forecastRecapLine.setBalance(forecastRecap.getCurrentBalance());
      } else {
        forecastRecap.setCurrentBalance(
            forecastRecap.getCurrentBalance().subtract(forecastRecapLine.getAmount()));
        forecastRecapLine.setBalance(forecastRecap.getCurrentBalance());
      }
    }
    forecastRecap.setForecastRecapLineList(forecastRecapLines);
  }

  public String getForecastRecapFileLink(Long forecastRecapId, String reportType)
      throws AxelorException {
    String title = I18n.get(ITranslation.CASH_MANAGEMENT_REPORT_TITLE);
    title += forecastRecapId;

    return ReportFactory.createReport(IReport.FORECAST_RECAP, title + "-${date}")
        .addParam("ForecastRecapId", forecastRecapId.toString())
        .addParam("Locale", ReportSettings.getPrintingLocale(null))
        .addFormat(reportType)
        .generate()
        .getFileLink();
  }

  protected ForecastRecapLineType getForecastRecapLineType(int elementSelect)
      throws AxelorException {

    ForecastRecapLineType forecastRecapLineType =
        forecastRecapLineTypeRepository
            .all()
            .filter("self.elementSelect = ?1", elementSelect)
            .fetchOne();

    if (forecastRecapLineType != null) {
      return forecastRecapLineType;
    }

    throw new AxelorException(
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        I18n.get(IExceptionMessage.FORECAST_RECAP_MISSING_FORECAST_RECAP_LINE_TYPE),
        elementSelect);

    // TODO get the right label in fact of integer value

  }
}
