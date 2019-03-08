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
package com.axelor.apps.bankpayment.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.EbicsUser;
import com.axelor.apps.bankpayment.db.repo.BankOrderRepository;
import com.axelor.apps.bankpayment.db.repo.EbicsPartnerRepository;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.apps.bankpayment.report.IReport;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderMergeService;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BankOrderController {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject protected BankOrderService bankOrderService;

  @Inject protected BankOrderRepository bankOrderRepo;

  public void confirm(ActionRequest request, ActionResponse response) {

    try {
      BankOrder bankOrder = request.getContext().asType(BankOrder.class);
      bankOrder = bankOrderRepo.find(bankOrder.getId());
      if (bankOrder != null) {
        bankOrderService.confirm(bankOrder);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
    response.setReload(true);
  }

  public void sign(ActionRequest request, ActionResponse response) throws AxelorException {

    BankOrder bankOrder = request.getContext().asType(BankOrder.class);
    bankOrder = bankOrderRepo.find(bankOrder.getId());
    try {
      ActionViewBuilder confirmView =
          ActionView.define("Sign bank order")
              .model(BankOrder.class.getName())
              .add("form", "bank-order-sign-wizard-form")
              .param("popup", "reload")
              .param("show-toolbar", "false")
              .param("show-confirm", "false")
              .param("popup-save", "false")
              .param("forceEdit", "true")
              .context("_showRecord", bankOrder.getId());

      response.setView(confirmView.map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void validate(ActionRequest request, ActionResponse response) throws AxelorException {

    Context context = request.getContext();

    BankOrder bankOrder = context.asType(BankOrder.class);
    bankOrder = bankOrderRepo.find(bankOrder.getId());

    try {

      EbicsUser ebicsUser = bankOrder.getSignatoryEbicsUser();

      if (ebicsUser == null) {
        response.setError(I18n.get(IExceptionMessage.EBICS_MISSING_NAME));
      } else {
        if (ebicsUser.getEbicsPartner().getEbicsTypeSelect()
            == EbicsPartnerRepository.EBICS_TYPE_TS) {
          bankOrderService.validate(bankOrder);
        } else {
          if (context.get("password") == null) {
            response.setError(I18n.get(IExceptionMessage.EBICS_WRONG_PASSWORD));
          }
          if (context.get("password") != null) {
            String password = (String) context.get("password");
            if (ebicsUser.getPassword() == null || !ebicsUser.getPassword().equals(password)) {
              response.setValue("password", "");
              response.setError(I18n.get(IExceptionMessage.EBICS_WRONG_PASSWORD));
            } else {
              bankOrderService.validate(bankOrder);
            }
          }
          response.setReload(true);
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void realize(ActionRequest request, ActionResponse response) {

    try {
      BankOrder bankOrder = request.getContext().asType(BankOrder.class);
      bankOrder = bankOrderRepo.find(bankOrder.getId());
      if (bankOrder != null) {
        bankOrderService.realize(bankOrder);
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
    response.setReload(true);
  }

  public void print(ActionRequest request, ActionResponse response) throws AxelorException {

    BankOrder bankOrder = request.getContext().asType(BankOrder.class);

    String name = I18n.get("Bank Order") + " " + bankOrder.getBankOrderSeq();

    String fileLink =
        ReportFactory.createReport(
                "bankPaymentBankOrder",
                bankOrder.getSenderCompany(),
                IReport.BANK_ORDER,
                name + "-${date}")
            .addParam("BankOrderId", bankOrder.getId())
            .addParam("Locale", ReportSettings.getPrintingLocale(null))
            .generate()
            .getFileLink();

    log.debug("Printing " + name);

    response.setView(ActionView.define(name).add("html", fileLink).map());
  }

  @SuppressWarnings("unchecked")
  public void merge(ActionRequest request, ActionResponse response) {

    try {

      List<Integer> listSelectedBankOrder = (List<Integer>) request.getContext().get("_ids");

      List<BankOrder> bankOrderList = Lists.newArrayList();
      if (listSelectedBankOrder != null) {
        for (Integer bankOrderId : listSelectedBankOrder) {

          BankOrder bankOrder = bankOrderRepo.find(bankOrderId.longValue());

          if (bankOrder != null) {
            bankOrderList.add(bankOrder);
          }
        }

        BankOrder bankOrder = Beans.get(BankOrderMergeService.class).mergeBankOrders(bankOrderList);

        response.setView(
            ActionView.define(I18n.get("Bank Order"))
                .model(BankOrder.class.getName())
                .add("form", "bank-order-form")
                .param("forceEdit", "true")
                .context("_showRecord", String.valueOf(bankOrder.getId()))
                .map());
      }

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void fillSignatoryEbicsUser(ActionRequest request, ActionResponse response) {
    BankOrder bankOrder = request.getContext().asType(BankOrder.class);
    if (bankOrder.getSenderBankDetails() != null) {
      EbicsUser ebicsUser =
          bankOrderService.getDefaultEbicsUserFromBankDetails(bankOrder.getSenderBankDetails());
      bankOrder.setSignatoryEbicsUser(ebicsUser);
      response.setValues(bankOrder);
    }
  }

  public void setBankDetailDomain(ActionRequest request, ActionResponse response) {
    BankOrder bankOrder = request.getContext().asType(BankOrder.class);
    String domain = bankOrderService.createDomainForBankDetails(bankOrder);
    // if nothing was found for the domain, we set it at a default value.
    if (domain.equals("")) {
      response.setAttr("senderBankDetails", "domain", "self.id IN (0)");
    } else {
      response.setAttr("senderBankDetails", "domain", domain);
    }
  }

  public void fillBankDetails(ActionRequest request, ActionResponse response) {
    BankOrder bankOrder = request.getContext().asType(BankOrder.class);
    BankDetails bankDetails = bankOrderService.getDefaultBankDetails(bankOrder);
    response.setValue("senderBankDetails", bankDetails);
  }

  public void resetReceivers(ActionRequest request, ActionResponse response) {
    try {
      BankOrder bankOrder = request.getContext().asType(BankOrder.class);
      bankOrderService.resetReceivers(bankOrder);
      response.setValue("bankOrderLineList", bankOrder.getBankOrderLineList());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void displayBankOrderLines(ActionRequest actionRequest, ActionResponse response) {
    try {
      String linesDomain = (String) actionRequest.getContext().get("_linesDomain");
      System.out.println(linesDomain);
      response.setView(
          bankOrderService
              .buildBankOrderLineView("bank-order-line-grid", "bank-order-line-form", linesDomain)
              .map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void displayBankOrderLinesBankToBank(
      ActionRequest actionRequest, ActionResponse response) {
    try {
      String linesDomain = (String) actionRequest.getContext().get("_linesDomain");
      System.out.println(linesDomain);
      response.setView(
          bankOrderService
              .buildBankOrderLineView(
                  "bank-order-line-grid-bank-to-bank", "bank-order-line-form", linesDomain)
              .map());
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
