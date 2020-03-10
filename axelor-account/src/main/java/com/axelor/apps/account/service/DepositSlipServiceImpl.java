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
package com.axelor.apps.account.service;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.DepositSlip;
import com.axelor.apps.account.db.PaymentVoucher;
import com.axelor.apps.account.db.repo.PaymentModeRepository;
import com.axelor.apps.account.db.repo.PaymentVoucherRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.report.IReport;
import com.axelor.apps.account.service.payment.paymentvoucher.PaymentVoucherConfirmService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.tool.QueryBuilder;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.List;

public class DepositSlipServiceImpl implements DepositSlipService {

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void loadPayments(DepositSlip depositSlip) throws AxelorException {
    if (depositSlip.getPublicationDate() != null) {
      throw new AxelorException(
          depositSlip,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.DEPOSIT_SLIP_ALREADY_PUBLISHED));
    }

    depositSlip.clearPaymentVoucherList();

    fetchPaymentVouchers(depositSlip).forEach(depositSlip::addPaymentVoucherListItem);
    compute(depositSlip);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public String publish(DepositSlip depositSlip) throws AxelorException {
    confirmPayments(depositSlip);

    ReportSettings settings =
        ReportFactory.createReport(getReportName(depositSlip), getFilename(depositSlip));
    settings.addParam("DepositSlipId", depositSlip.getId());
    settings.addParam("Locale", ReportSettings.getPrintingLocale(null));
    settings.addFormat("pdf");
    String fileLink = settings.toAttach(depositSlip).generate().getFileLink();
    depositSlip.setPublicationDate(Beans.get(AppBaseService.class).getTodayDate());
    return fileLink;
  }

  @Override
  public String getFilename(DepositSlip depositSlip) throws AxelorException {
    String name;

    switch (depositSlip.getPaymentModeTypeSelect()) {
      case PaymentModeRepository.TYPE_CHEQUE:
        name = I18n.get("Cheque deposit slip");
        break;
      case PaymentModeRepository.TYPE_CASH:
        name = I18n.get("Cash deposit slip");
        break;
      default:
        throw new AxelorException(
            depositSlip,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            IExceptionMessage.DEPOSIT_SLIP_UNSUPPORTED_PAYMENT_MODE_TYPE);
    }

    return String.format("%s - %s", name, depositSlip.getDepositNumber());
  }

  protected String getReportName(DepositSlip depositSlip) throws AxelorException {
    switch (depositSlip.getPaymentModeTypeSelect()) {
      case PaymentModeRepository.TYPE_CHEQUE:
        return IReport.CHEQUE_DEPOSIT_SLIP;
      case PaymentModeRepository.TYPE_CASH:
        return IReport.CASH_DEPOSIT_SLIP;
      default:
        throw new AxelorException(
            depositSlip,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            IExceptionMessage.DEPOSIT_SLIP_UNSUPPORTED_PAYMENT_MODE_TYPE);
    }
  }

  protected void compute(DepositSlip depositSlip) {
    if (depositSlip.getPaymentVoucherList() != null) {
      List<PaymentVoucher> paymentVoucherList = depositSlip.getPaymentVoucherList();
      BigDecimal totalAmount =
          paymentVoucherList
              .stream()
              .map(PaymentVoucher::getPaidAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      depositSlip.setTotalAmount(totalAmount);
      depositSlip.setChequeCount(paymentVoucherList.size());
    }
  }

  protected List<PaymentVoucher> fetchPaymentVouchers(DepositSlip depositSlip) {
    QueryBuilder<PaymentVoucher> queryBuilder = QueryBuilder.of(PaymentVoucher.class);

    if (depositSlip.getPaymentModeTypeSelect() != 0) {
      queryBuilder.add("self.paymentMode.typeSelect = :paymentModeTypeSelect");
      queryBuilder.bind("paymentModeTypeSelect", depositSlip.getPaymentModeTypeSelect());
    }

    if (depositSlip.getCompany() != null) {
      queryBuilder.add("self.company = :company");
      queryBuilder.bind("company", depositSlip.getCompany());
    }

    if (depositSlip.getCurrency() != null) {
      queryBuilder.add("self.currency = :currency");
      queryBuilder.bind("currency", depositSlip.getCurrency());
    }

    if (depositSlip.getCompanyBankDetails() != null
        && Beans.get(AppBaseService.class).getAppBase().getManageMultiBanks()) {
      queryBuilder.add("self.companyBankDetails = :companyBankDetails");
      queryBuilder.bind("companyBankDetails", depositSlip.getCompanyBankDetails());
    }

    if (depositSlip.getFromDate() != null) {
      if (depositSlip.getToDate() != null) {
        queryBuilder.add(
            "self.chequeDate IS NULL OR self.chequeDate BETWEEN :fromDate AND :toDate");
        queryBuilder.bind("fromDate", depositSlip.getFromDate());
        queryBuilder.bind("toDate", depositSlip.getToDate());
      } else {
        queryBuilder.add("self.chequeDate IS NULL OR self.chequeDate >= :fromDate");
        queryBuilder.bind("fromDate", depositSlip.getFromDate());
      }
    } else if (depositSlip.getToDate() != null) {
      queryBuilder.add("self.chequeDate IS NULL OR self.chequeDate <= :toDate");
      queryBuilder.bind("toDate", depositSlip.getToDate());
    }

    queryBuilder.add("self.depositSlip IS NULL");

    queryBuilder.add("self.statusSelect = :statusSelect");
    queryBuilder.bind("statusSelect", PaymentVoucherRepository.STATUS_WAITING_FOR_DEPOSIT_SLIP);

    return queryBuilder.build().fetch();
  }

  protected void confirmPayments(DepositSlip depositSlip) throws AxelorException {
    if (depositSlip.getPaymentVoucherList() != null) {
      PaymentVoucherConfirmService paymentVoucherConfirmService =
          Beans.get(PaymentVoucherConfirmService.class);

      for (PaymentVoucher paymentVoucher : depositSlip.getPaymentVoucherList()) {
        paymentVoucherConfirmService.createMoveAndConfirm(paymentVoucher);
      }
    }
  }
}
