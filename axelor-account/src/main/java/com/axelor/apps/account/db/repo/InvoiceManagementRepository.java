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
package com.axelor.apps.account.db.repo;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.SubrogationRelease;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.account.service.invoice.workflow.validate.ValidateState;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.persistence.PersistenceException;
import org.apache.commons.collections.CollectionUtils;

public class InvoiceManagementRepository extends InvoiceRepository {
  @Override
  public Invoice copy(Invoice entity, boolean deep) {

    Invoice copy = super.copy(entity, deep);

    InvoiceToolService.resetInvoiceStatusOnCopy(copy);
    return copy;
  }

  @Override
  public Invoice save(Invoice invoice) {
    try {
      AppAccountService appAccountService = Beans.get(AppAccountService.class);
      List<InvoicePayment> invoicePayments = invoice.getInvoicePaymentList();
      if (CollectionUtils.isNotEmpty(invoicePayments)) {
        LocalDate latestPaymentDate =
            invoicePayments.stream()
                .filter(
                    invoicePayment ->
                        invoicePayment.getStatusSelect()
                            == InvoicePaymentRepository.STATUS_VALIDATED)
                .map(InvoicePayment::getPaymentDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        invoice.setPaymentDate(latestPaymentDate);
      }

      if ((invoice
                  .getOperationTypeSelect()
                  .equals(InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE)
              || invoice
                  .getOperationTypeSelect()
                  .equals(InvoiceRepository.OPERATION_TYPE_SUPPLIER_REFUND))
          && appAccountService.isApp("budget")
          && !appAccountService.getAppBudget().getManageMultiBudget()
          && !invoice.getStatusSelect().equals(InvoiceRepository.STATUS_DRAFT)
          && !invoice.getStatusSelect().equals(InvoiceRepository.STATUS_CANCELED)) {
        Beans.get(ValidateState.class).generateBudgetDistribution(invoice);
      }

      invoice = super.save(invoice);
      Beans.get(InvoiceService.class).setDraftSequence(invoice);

      return invoice;
    } catch (Exception e) {
      throw new PersistenceException(e.getLocalizedMessage());
    }
  }

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {
    try {
      final String subrogationStatusSelect = "$subrogationStatusSelect";
      if (context.get("_model") != null
          && context.get("_model").toString().equals(SubrogationRelease.class.getName())) {
        if (context.get("id") != null) {
          long id = (long) context.get("id");
          SubrogationRelease subrogationRelease =
              Beans.get(SubrogationReleaseRepository.class).find(id);
          if (subrogationRelease != null && subrogationRelease.getStatusSelect() != null) {
            json.put(subrogationStatusSelect, subrogationRelease.getStatusSelect());
          } else {
            json.put(subrogationStatusSelect, SubrogationReleaseRepository.STATUS_NEW);
          }
        }
      } else {
        json.put(subrogationStatusSelect, SubrogationReleaseRepository.STATUS_NEW);
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return super.populate(json, context);
  }
}
