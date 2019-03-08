/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
import com.axelor.apps.cash.management.db.repo.ForecastRecapLineRepository;
import com.axelor.apps.cash.management.db.repo.ForecastRecapRepository;
import com.axelor.apps.cash.management.db.repo.ForecastRepository;
import com.axelor.apps.cash.management.report.IReport;
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
import com.axelor.exception.AxelorException;
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
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ForecastRecapService {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject protected AppBaseService appBaseService;

  @Inject protected ForecastRepository forecastRepo;

  @Inject protected ForecastRecapLineRepository forecastRecapLineRepo;

  @Inject protected CurrencyService currencyService;

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
    forecastRecap.setEndingBalance(forecastRecap.getCurrentBalance());
  }

  public void populateWithOpportunities(ForecastRecap forecastRecap) throws AxelorException {
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
                    appBaseService.getTodayDate())
                .setScale(2, RoundingMode.HALF_UP);
        forecastRecap.setCurrentBalance(forecastRecap.getCurrentBalance().add(amountCompanyCurr));
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                opportunity.getExpectedCloseDate(),
                1,
                null,
                amountCompanyCurr,
                forecastRecap.getCurrentBalance()));
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
        forecastRecap.setCurrentBalance(forecastRecap.getCurrentBalance().add(amountCompanyCurr));
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                opportunity.getExpectedCloseDate(),
                1,
                null,
                amountCompanyCurr,
                forecastRecap.getCurrentBalance()));
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
        forecastRecap.setCurrentBalance(forecastRecap.getCurrentBalance().add(amountCompanyCurr));
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                opportunity.getExpectedCloseDate(),
                1,
                null,
                amountCompanyCurr,
                forecastRecap.getCurrentBalance()));
      }
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

  public void populateWithInvoices(ForecastRecap forecastRecap) {
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
        forecastRecap.setCurrentBalance(forecastRecap.getCurrentBalance().add(amount));
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                invoice.getEstimatedPaymentDate(),
                1,
                null,
                amount,
                forecastRecap.getCurrentBalance()));
      }
      if (invoice.getOperationTypeSelect() == 1 || invoice.getOperationTypeSelect() == 4) {
        forecastRecap.setCurrentBalance(forecastRecap.getCurrentBalance().subtract(amount));
        forecastRecap.addForecastRecapLineListItem(
            this.createForecastRecapLine(
                invoice.getEstimatedPaymentDate(),
                2,
                null,
                amount,
                forecastRecap.getCurrentBalance()));
      }
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

  public void populateWithSalaries(ForecastRecap forecastRecap) {
    List<Employee> employeeList = new ArrayList<Employee>();
    if (forecastRecap.getBankDetails() != null) {
      employeeList =
          Beans.get(EmployeeRepository.class)
              .all()
              .filter(
                  "self.mainEmploymentContract.payCompany = ?1 AND self.bankDetails = ?2",
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails())
              .fetch();
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
          forecastRecap.setCurrentBalance(
              forecastRecap
                  .getCurrentBalance()
                  .subtract(
                      employee
                          .getHourlyRate()
                          .multiply(employee.getWeeklyWorkHours().multiply(new BigDecimal(4)))));
          forecastRecap.addForecastRecapLineListItem(
              this.createForecastRecapLine(
                  itDate,
                  2,
                  null,
                  employee
                      .getHourlyRate()
                      .multiply(employee.getWeeklyWorkHours().multiply(new BigDecimal(4))),
                  forecastRecap.getCurrentBalance()));
        }
        itDate = itDate.plusMonths(1);
      } else {
        itDate = monthEnd;
      }
    }
  }

  public void populateWithTimetables(ForecastRecap forecastRecap) throws AxelorException {
    List<Timetable> timetableSaleOrderList = new ArrayList<Timetable>();
    List<Timetable> timetablePurchaseOrderList = new ArrayList<Timetable>();
    if (forecastRecap.getBankDetails() != null) {
      timetableSaleOrderList =
          Beans.get(TimetableRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.saleOrder.company = ?3 AND"
                      + " self.saleOrder.companyBankDetails = ?4 AND self.saleOrder.statusSelect = 3 AND self.amount != 0",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails())
              .fetch();
      timetablePurchaseOrderList =
          Beans.get(TimetableRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.purchaseOrder.company = ?3 AND"
                      + " self.purchaseOrder.companyBankDetails = ?4 AND self.purchaseOrder.statusSelect = 3 AND self.amount != 0",
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
                      + " self.saleOrder.statusSelect = 3 AND self.amount != 0",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany())
              .fetch();
      timetablePurchaseOrderList =
          Beans.get(TimetableRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.purchaseOrder.company = ?3 AND"
                      + " self.purchaseOrder.statusSelect = 3 AND self.amount != 0",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany())
              .fetch();
    }
    for (Timetable timetable : timetableSaleOrderList) {
      BigDecimal amountCompanyCurr =
          currencyService
              .getAmountCurrencyConvertedAtDate(
                  timetable.getSaleOrder().getCurrency(),
                  timetable.getSaleOrder().getCompany().getCurrency(),
                  timetable.getAmount(),
                  appBaseService.getTodayDate())
              .setScale(2, RoundingMode.HALF_UP);
      forecastRecap.setCurrentBalance(forecastRecap.getCurrentBalance().add(amountCompanyCurr));
      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              timetable.getEstimatedDate(),
              1,
              null,
              amountCompanyCurr,
              forecastRecap.getCurrentBalance()));
    }
    for (Timetable timetable : timetablePurchaseOrderList) {
      BigDecimal amountCompanyCurr =
          currencyService
              .getAmountCurrencyConvertedAtDate(
                  timetable.getPurchaseOrder().getCurrency(),
                  timetable.getPurchaseOrder().getCompany().getCurrency(),
                  timetable.getAmount(),
                  appBaseService.getTodayDate())
              .setScale(2, RoundingMode.HALF_UP);
      forecastRecap.setCurrentBalance(
          forecastRecap.getCurrentBalance().subtract(amountCompanyCurr));
      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              timetable.getEstimatedDate(),
              2,
              null,
              amountCompanyCurr,
              forecastRecap.getCurrentBalance()));
    }
  }

  public void populateWithTimetablesOrOrders(ForecastRecap forecastRecap) throws AxelorException {
    List<Timetable> timetableSaleOrderList = new ArrayList<Timetable>();
    List<SaleOrder> saleOrderList = new ArrayList<SaleOrder>();
    List<Timetable> timetablePurchaseOrderList = new ArrayList<Timetable>();
    List<PurchaseOrder> purchaseOrderList = new ArrayList<PurchaseOrder>();
    if (forecastRecap.getBankDetails() != null) {
      timetableSaleOrderList =
          Beans.get(TimetableRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.saleOrder.company = ?3 AND"
                      + " self.saleOrder.companyBankDetails = ?4 AND self.saleOrder.statusSelect = 3 AND self.amount != 0",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails())
              .fetch();
      timetablePurchaseOrderList =
          Beans.get(TimetableRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.purchaseOrder.company = ?3 AND"
                      + " self.purchaseOrder.companyBankDetails = ?4 AND self.purchaseOrder.statusSelect = 3 AND self.amount != 0",
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
                      + " self.saleOrder.statusSelect = 3 AND self.amount != 0",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany())
              .fetch();
      timetablePurchaseOrderList =
          Beans.get(TimetableRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.purchaseOrder.company = ?3 AND"
                      + " self.purchaseOrder.statusSelect = 3 AND self.amount != 0",
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
      forecastRecap.setCurrentBalance(forecastRecap.getCurrentBalance().add(amountCompanyCurr));
      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              timetable.getEstimatedDate(),
              1,
              null,
              amountCompanyCurr,
              forecastRecap.getCurrentBalance()));
    }
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
      forecastRecap.setCurrentBalance(
          forecastRecap.getCurrentBalance().subtract(amountCompanyCurr));
      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              timetable.getEstimatedDate(),
              2,
              null,
              amountCompanyCurr,
              forecastRecap.getCurrentBalance()));
    }
    List<SaleOrder> saleOrderNoTimeTableList = new ArrayList<SaleOrder>();
    List<PurchaseOrder> purchaseOrderNoTimetableList = new ArrayList<PurchaseOrder>();
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
      purchaseOrderNoTimetableList =
          Beans.get(PurchaseOrderRepository.class)
              .all()
              .filter(
                  "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " self.companyBankDetails = ?4 AND self.statusSelect = 3",
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
      purchaseOrderNoTimetableList =
          Beans.get(PurchaseOrderRepository.class)
              .all()
              .filter(
                  "self.expectedRealisationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " self.statusSelect = 3",
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
          forecastRecap.setCurrentBalance(forecastRecap.getCurrentBalance().add(amountCompanyCurr));
          forecastRecap.addForecastRecapLineListItem(
              this.createForecastRecapLine(
                  saleOrder.getExpectedRealisationDate(),
                  1,
                  null,
                  amountCompanyCurr,
                  forecastRecap.getCurrentBalance()));
        }
      }
    }
    for (PurchaseOrder purchaseOrder : purchaseOrderNoTimetableList) {
      if (!purchaseOrderList.contains(purchaseOrder)) {
        BigDecimal amountCompanyCurr =
            purchaseOrder.getCompanyExTaxTotal().subtract(purchaseOrder.getAmountInvoiced());
        if (amountCompanyCurr.compareTo(BigDecimal.ZERO) == 0) {
          forecastRecap.setCurrentBalance(
              forecastRecap.getCurrentBalance().subtract(amountCompanyCurr));
          forecastRecap.addForecastRecapLineListItem(
              this.createForecastRecapLine(
                  purchaseOrder.getExpectedRealisationDate(),
                  2,
                  null,
                  amountCompanyCurr,
                  forecastRecap.getCurrentBalance()));
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
  public void populateWithForecasts(ForecastRecap forecastRecap) {
    List<Forecast> forecastList = new ArrayList<Forecast>();
    if (forecastRecap.getBankDetails() != null) {
      forecastList =
          Beans.get(ForecastRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " self.bankDetails = ?4 AND (self.realizedSelect = 2 OR (self.realizedSelect = 3 AND self.estimatedDate <= ?5))",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails(),
                  appBaseService.getTodayDate())
              .fetch();
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
      if (forecast.getTypeSelect() == 1) {
        forecastRecap.setCurrentBalance(
            forecastRecap.getCurrentBalance().add(forecast.getAmount()));
      } else {
        forecastRecap.setCurrentBalance(
            forecastRecap.getCurrentBalance().subtract(forecast.getAmount()));
      }
      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              forecast.getEstimatedDate(),
              forecast.getTypeSelect(),
              forecast.getForecastReason(),
              forecast.getAmount(),
              forecastRecap.getCurrentBalance()));
      forecast.setRealizedSelect(ForecastRepository.REALISED_SELECT_YES);
      forecastRepo.save(forecast);
    }
  }

  public void populateWithForecastsNoSave(ForecastRecap forecastRecap) {
    List<Forecast> forecastList = new ArrayList<Forecast>();
    if (forecastRecap.getBankDetails() != null) {
      forecastList =
          Beans.get(ForecastRepository.class)
              .all()
              .filter(
                  "self.estimatedDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " self.bankDetails = ?4 AND (self.realizedSelect = 2 OR (self.realizedSelect = 3 AND self.estimatedDate <= ?5))",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails(),
                  appBaseService.getTodayDate())
              .fetch();
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
      if (forecast.getTypeSelect() == 1) {
        forecastRecap.setCurrentBalance(
            forecastRecap.getCurrentBalance().add(forecast.getAmount()));
      } else {
        forecastRecap.setCurrentBalance(
            forecastRecap.getCurrentBalance().subtract(forecast.getAmount()));
      }
      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              forecast.getEstimatedDate(),
              forecast.getTypeSelect(),
              forecast.getForecastReason(),
              forecast.getAmount(),
              forecastRecap.getCurrentBalance()));
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
                      + " self.bankDetails = ?4 AND (self.realizedSelect = 2 OR self.realizedSelect = 3)",
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

  public void populateWithExpenses(ForecastRecap forecastRecap) {
    List<Expense> expenseList = new ArrayList<Expense>();
    if (forecastRecap.getBankDetails() != null) {
      expenseList =
          Beans.get(ExpenseRepository.class)
              .all()
              .filter(
                  "self.validationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " self.bankDetails = ?4 AND self.statusSelect = 3",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany(),
                  forecastRecap.getBankDetails())
              .fetch();
    } else {
      expenseList =
          Beans.get(ExpenseRepository.class)
              .all()
              .filter(
                  "self.validationDate BETWEEN ?1 AND ?2 AND self.company = ?3 AND"
                      + " self.statusSelect = 3",
                  forecastRecap.getFromDate(),
                  forecastRecap.getToDate(),
                  forecastRecap.getCompany())
              .fetch();
    }
    for (Expense expense : expenseList) {
      forecastRecap.setCurrentBalance(
          forecastRecap.getCurrentBalance().subtract(expense.getExTaxTotal()));
      forecastRecap.addForecastRecapLineListItem(
          this.createForecastRecapLine(
              expense.getValidationDate(),
              2,
              null,
              expense.getExTaxTotal(),
              forecastRecap.getCurrentBalance()));
    }
  }

  public ForecastRecapLine createForecastRecapLine(
      LocalDate date, int type, ForecastReason reason, BigDecimal amount, BigDecimal balance) {
    ForecastRecapLine forecastRecapLine = new ForecastRecapLine();
    forecastRecapLine.setEstimatedDate(date);
    forecastRecapLine.setTypeSelect(type);
    forecastRecapLine.setForecastReason(reason);
    forecastRecapLine.setAmount(amount);
    forecastRecapLine.setBalance(balance);
    return forecastRecapLine;
  }

  public String getURLForecastRecapPDF(ForecastRecap forecastRecap) throws AxelorException {
    String title = I18n.get("ForecastRecap");
    title += forecastRecap.getId();

    return ReportFactory.createReport(
            "cashManagementForecastRecap",
            forecastRecap.getCompany(),
            IReport.FORECAST_RECAP,
            title + "-${date}")
        .addParam("ForecastRecapId", forecastRecap.getId().toString())
        .addParam("Locale", ReportSettings.getPrintingLocale(null))
        .generate()
        .getFileLink();
  }
}
