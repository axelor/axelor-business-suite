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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.repo.AccountingSituationRepository;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.service.AccountingSituationServiceImpl;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.sale.db.SaleConfig;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.exception.BlockedSaleOrderException;
import com.axelor.apps.sale.service.config.SaleConfigService;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Singleton
public class AccountingSituationSupplychainServiceImpl extends AccountingSituationServiceImpl
    implements AccountingSituationSupplychainService {

  private SaleConfigService saleConfigService;

  @Inject private AppAccountService appAccountService;

  @Inject
  public AccountingSituationSupplychainServiceImpl(
      AccountConfigService accountConfigService,
      SequenceService sequenceService,
      AccountingSituationRepository accountingSituationRepo,
      SaleConfigService saleConfigService) {
    super(accountConfigService, sequenceService, accountingSituationRepo);
    this.saleConfigService = saleConfigService;
  }

  @Override
  public AccountingSituation createAccountingSituation(Partner partner, Company company)
      throws AxelorException {

    AccountingSituation accountingSituation = super.createAccountingSituation(partner, company);

    AppAccountService appAccountService = Beans.get(AppAccountService.class);
    if (appAccountService.getAppAccount().getManageCustomerCredit()
        && appAccountService.isApp("supplychain")) {
      SaleConfig config = saleConfigService.getSaleConfig(accountingSituation.getCompany());
      if (config != null) {
        accountingSituation.setAcceptedCredit(config.getAcceptedCredit());
      }
    }

    return accountingSituation;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateUsedCredit(Partner partner) throws AxelorException {
    if (appAccountService.getAppAccount().getManageCustomerCredit()) {
      List<AccountingSituation> accountingSituationList =
          accountingSituationRepo.all().filter("self.partner = ?1", partner).fetch();
      for (AccountingSituation accountingSituation : accountingSituationList) {
        accountingSituationRepo.save(this.computeUsedCredit(accountingSituation));
      }
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void updateCustomerCredit(Partner partner) throws AxelorException {
    if (!Beans.get(AppAccountService.class).isApp("supplychain")) {
      super.updateCustomerCredit(partner);
      return;
    }
    if (!appAccountService.getAppAccount().getManageCustomerCredit()
        || partner.getIsContact()
        || !partner.getIsCustomer()) {
      return;
    }

    List<AccountingSituation> accountingSituationList = partner.getAccountingSituationList();

    for (AccountingSituation accountingSituation : accountingSituationList) {
      computeUsedCredit(accountingSituation);
    }
  }

  @Override
  @Transactional(
    rollbackOn = {AxelorException.class, Exception.class},
    ignore = {BlockedSaleOrderException.class}
  )
  public void updateCustomerCreditFromSaleOrder(SaleOrder saleOrder) throws AxelorException {

    if (!appAccountService.getAppAccount().getManageCustomerCredit()) {
      return;
    }

    Partner partner = saleOrder.getClientPartner();
    List<AccountingSituation> accountingSituationList = partner.getAccountingSituationList();
    for (AccountingSituation accountingSituation : accountingSituationList) {
      if (accountingSituation.getCompany().equals(saleOrder.getCompany())) {
        // Update UsedCredit
        accountingSituation = this.computeUsedCredit(accountingSituation);
        if (saleOrder.getStatusSelect() == SaleOrderRepository.STATUS_DRAFT_QUOTATION) {
          BigDecimal inTaxInvoicedAmount =
              Beans.get(SaleOrderInvoiceService.class).getInTaxInvoicedAmount(saleOrder);

          BigDecimal usedCredit =
              accountingSituation
                  .getUsedCredit()
                  .add(saleOrder.getInTaxTotal())
                  .subtract(inTaxInvoicedAmount);

          accountingSituation.setUsedCredit(usedCredit);
        }
        boolean usedCreditExceeded = isUsedCreditExceeded(accountingSituation);
        if (usedCreditExceeded) {
          saleOrder.setBlockedOnCustCreditExceed(true);
          if (!saleOrder.getManualUnblock()) {
            String message = accountingSituation.getCompany().getOrderBloquedMessage();
            if (Strings.isNullOrEmpty(message)) {
              message = I18n.get("Client blocked : maximal accepted credit exceeded.");
            }
            throw new BlockedSaleOrderException(accountingSituation, message);
          }
        }
      }
    }
  }

  @Override
  public AccountingSituation computeUsedCredit(AccountingSituation accountingSituation)
      throws AxelorException {
    BigDecimal sum = BigDecimal.ZERO;
    List<SaleOrder> saleOrderList =
        Beans.get(SaleOrderRepository.class)
            .all()
            .filter(
                "self.company = ?1 AND self.clientPartner = ?2 AND self.statusSelect > ?3 AND self.statusSelect < ?4",
                accountingSituation.getCompany(),
                accountingSituation.getPartner(),
                SaleOrderRepository.STATUS_DRAFT_QUOTATION,
                SaleOrderRepository.STATUS_CANCELED)
            .fetch();
    for (SaleOrder saleOrder : saleOrderList) {
      sum =
          sum.add(
              saleOrder
                  .getInTaxTotal()
                  .subtract(
                      Beans.get(SaleOrderInvoiceService.class).getInTaxInvoicedAmount(saleOrder)));
    }
    // subtract the amount of payments if there is no move created for
    // invoice payments
    if (!accountConfigService
        .getAccountConfig(accountingSituation.getCompany())
        .getGenerateMoveForInvoicePayment()) {
      List<InvoicePayment> invoicePaymentList =
          Beans.get(InvoicePaymentRepository.class)
              .all()
              .filter(
                  "self.invoice.company = :company"
                      + " AND self.invoice.partner = :partner"
                      + " AND self.statusSelect = :validated"
                      + " AND self.typeSelect != :imputation")
              .bind("company", accountingSituation.getCompany())
              .bind("partner", accountingSituation.getPartner())
              .bind("validated", InvoicePaymentRepository.STATUS_VALIDATED)
              .bind("imputation", InvoicePaymentRepository.TYPE_ADV_PAYMENT_IMPUTATION)
              .fetch();
      if (invoicePaymentList != null) {
        for (InvoicePayment invoicePayment : invoicePaymentList) {
          sum = sum.subtract(invoicePayment.getAmount());
        }
      }
    }
    sum = accountingSituation.getBalanceCustAccount().add(sum);
    accountingSituation.setUsedCredit(sum.setScale(2, RoundingMode.HALF_EVEN));

    return accountingSituation;
  }

  protected boolean isUsedCreditExceeded(AccountingSituation accountingSituation) {
    return accountingSituation.getUsedCredit().compareTo(accountingSituation.getAcceptedCredit())
        > 0;
  }

  //	@Override
  //	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
  //	public boolean checkBlockedPartner(Partner partner, Company company) throws AxelorException {
  //		AccountingSituation accountingSituation = accountingSituationRepo.all().filter("self.company =
  // ?1 AND self.partner = ?2", company, partner).fetchOne();
  //		accountingSituation = this.computeUsedCredit(accountingSituation);
  //		accountingSituationRepo.save(accountingSituation);
  //
  //		return this.isUsedCreditExceeded(accountingSituation);
  //	}
}
