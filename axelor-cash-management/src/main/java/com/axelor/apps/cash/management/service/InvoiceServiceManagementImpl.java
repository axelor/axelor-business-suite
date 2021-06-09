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

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceTerm;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.PaymentConditionLineRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceTermService;
import com.axelor.apps.account.service.invoice.factory.CancelFactory;
import com.axelor.apps.account.service.invoice.factory.ValidateFactory;
import com.axelor.apps.account.service.invoice.factory.VentilateFactory;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.alarm.AlarmEngineService;
import com.axelor.apps.businessproject.service.InvoiceServiceProjectImpl;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import org.apache.commons.collections.CollectionUtils;

public class InvoiceServiceManagementImpl extends InvoiceServiceProjectImpl {

  @Inject
  public InvoiceServiceManagementImpl(
      ValidateFactory validateFactory,
      VentilateFactory ventilateFactory,
      CancelFactory cancelFactory,
      AlarmEngineService<Invoice> alarmEngineService,
      InvoiceRepository invoiceRepo,
      AppAccountService appAccountService,
      PartnerService partnerService,
      InvoiceLineService invoiceLineService,
      AccountConfigService accountConfigService,
      MoveToolService moveToolService,
      InvoiceTermService invoiceTermService,
      InvoiceLineRepository invoiceLineRepo) {
    super(
        validateFactory,
        ventilateFactory,
        cancelFactory,
        alarmEngineService,
        invoiceRepo,
        appAccountService,
        partnerService,
        invoiceLineService,
        accountConfigService,
        moveToolService,
        invoiceTermService,
        invoiceLineRepo);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void ventilate(Invoice invoice) throws AxelorException {
    super.ventilate(invoice);
    this.computeEstimatedPaymentDate(invoice);
    invoiceRepo.save(invoice);
  }

  public Invoice computeEstimatedPaymentDate(Invoice invoice) {

    if (CollectionUtils.isEmpty(invoice.getInvoiceTermList())) {
      return invoice;
    }
    if (invoice.getPartner() != null && invoice.getPartner().getPaymentDelay() != null) {

      int paymentDelay = invoice.getPartner().getPaymentDelay().intValue();

      for (InvoiceTerm invoiceTerm : invoice.getInvoiceTermList()) {
        if (invoiceTerm.getEstimatedPaymentDate() == null) {
          invoiceTerm.setEstimatedPaymentDate(invoiceTerm.getDueDate().plusDays(paymentDelay));
        }
      }
    } else {
      for (InvoiceTerm invoiceTerm : invoice.getInvoiceTermList()) {
        if (invoiceTerm.getEstimatedPaymentDate() != null) {
          continue;
        }

        LocalDate estimatedPaymentDate = invoiceTerm.getDueDate();

        if (invoiceTerm.getPaymentConditionLine().getTypeSelect()
            == PaymentConditionLineRepository.TYPE_NET) {
          if (invoiceTerm.getPaymentConditionLine().getPeriodTypeSelect()
              == PaymentConditionLineRepository.PERIOD_TYPE_DAYS) {
            estimatedPaymentDate =
                estimatedPaymentDate.plusDays(
                    invoiceTerm.getPaymentConditionLine().getPaymentTime());
          } else if (invoiceTerm.getPaymentConditionLine().getPeriodTypeSelect()
              == PaymentConditionLineRepository.PERIOD_TYPE_MONTH) {
            estimatedPaymentDate =
                estimatedPaymentDate.plusMonths(
                    invoiceTerm.getPaymentConditionLine().getPaymentTime());
          }
        } else if (invoiceTerm.getPaymentConditionLine().getTypeSelect()
            == PaymentConditionLineRepository.TYPE_END_OF_MONTH_N_DAYS) {
          estimatedPaymentDate =
              estimatedPaymentDate.withDayOfMonth(estimatedPaymentDate.lengthOfMonth());
          if (invoiceTerm.getPaymentConditionLine().getPeriodTypeSelect()
              == PaymentConditionLineRepository.PERIOD_TYPE_DAYS) {
            estimatedPaymentDate =
                estimatedPaymentDate.plusDays(
                    invoiceTerm.getPaymentConditionLine().getPaymentTime());
          } else if (invoiceTerm.getPaymentConditionLine().getPeriodTypeSelect()
              == PaymentConditionLineRepository.PERIOD_TYPE_MONTH) {
            estimatedPaymentDate =
                estimatedPaymentDate.plusMonths(
                    invoiceTerm.getPaymentConditionLine().getPaymentTime());
          }
        } else if (invoiceTerm.getPaymentConditionLine().getTypeSelect()
            == PaymentConditionLineRepository.TYPE_N_DAYS_END_OF_MONTH) {
          if (invoiceTerm.getPaymentConditionLine().getPeriodTypeSelect()
              == PaymentConditionLineRepository.PERIOD_TYPE_DAYS) {
            estimatedPaymentDate =
                estimatedPaymentDate.plusDays(
                    invoiceTerm.getPaymentConditionLine().getPaymentTime());
          } else if (invoiceTerm.getPaymentConditionLine().getPeriodTypeSelect()
              == PaymentConditionLineRepository.PERIOD_TYPE_MONTH) {
            estimatedPaymentDate =
                estimatedPaymentDate.plusMonths(
                    invoiceTerm.getPaymentConditionLine().getPaymentTime());
          }
          estimatedPaymentDate =
              estimatedPaymentDate.withDayOfMonth(estimatedPaymentDate.lengthOfMonth());
        } else if (invoiceTerm.getPaymentConditionLine().getTypeSelect()
            == PaymentConditionLineRepository.TYPE_N_DAYS_END_OF_MONTH_AT) {
          if (invoiceTerm.getPaymentConditionLine().getPeriodTypeSelect()
              == PaymentConditionLineRepository.PERIOD_TYPE_DAYS) {
            estimatedPaymentDate =
                estimatedPaymentDate.plusDays(
                    invoiceTerm.getPaymentConditionLine().getPaymentTime());
          } else if (invoiceTerm.getPaymentConditionLine().getPeriodTypeSelect()
              == PaymentConditionLineRepository.PERIOD_TYPE_MONTH) {
            estimatedPaymentDate =
                estimatedPaymentDate.plusMonths(
                    invoiceTerm.getPaymentConditionLine().getPaymentTime());
          }
          estimatedPaymentDate =
              estimatedPaymentDate.withDayOfMonth(
                  invoiceTerm.getPaymentConditionLine().getDaySelect() == 0
                      ? estimatedPaymentDate.lengthOfMonth()
                      : invoiceTerm.getPaymentConditionLine().getDaySelect());
        }

        invoiceTerm.setEstimatedPaymentDate(estimatedPaymentDate);
      }
    }
    return invoice;
  }
}
