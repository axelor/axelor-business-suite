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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.db.JPA;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.List;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected MoveLineService moveLineService;
  protected InvoiceLineService invoiceLineService;

  /** Debit balance = debit - credit */
  public static final Integer BALANCE_TYPE_DEBIT_BALANCE = 1;

  /** Credit balance = credit - debit */
  public static final Integer BALANCE_TYPE_CREDIT_BALANCE = 2;

  @Inject
  public AccountService(MoveLineService moveLineService, InvoiceLineService invoiceLineService) {
    this.moveLineService = moveLineService;
    this.invoiceLineService = invoiceLineService;
  }

  /**
   * Compute the balance of the account, depending of the balance type
   *
   * @param account Account
   * @param balanceType
   *     <p>1 : debit balance = debit - credit
   *     <p>2 : credit balance = credit - debit
   * @return The balance (debit balance or credit balance)
   */
  public BigDecimal computeBalance(Account account, int balanceType) {

    Query balanceQuery =
        JPA.em()
            .createQuery(
                "select sum(self.debit - self.credit) from MoveLine self where self.account = :account "
                    + "and self.move.ignoreInAccountingOk IN ('false', null) and self.move.statusSelect IN (2, 3)");

    balanceQuery.setParameter("account", account);

    BigDecimal balance = (BigDecimal) balanceQuery.getSingleResult();

    if (balance != null) {

      if (balanceType == BALANCE_TYPE_CREDIT_BALANCE) {
        balance = balance.negate();
      }
      log.debug("Account balance : {}", balance);

      return balance;
    } else {
      return BigDecimal.ZERO;
    }
  }

  public List<MoveLine> searchMoveLinesByAccountAndAnalytic(Account account, boolean isWithAnalytic)
      throws Exception {
    String analyticFilter = "";
    if (isWithAnalytic) {
      analyticFilter =
          "(moveLine.analyticDistributionTemplate is not null OR (SELECT COUNT(0) FROM AnalyticMoveLine  analyticMoveLine WHERE analyticMoveLine.moveLine = moveLine.id) > 0)";
    } else {
      analyticFilter =
          "(moveLine.analyticDistributionTemplate is null OR (SELECT COUNT(0) FROM AnalyticMoveLine analyticMoveLine WHERE analyticMoveLine.moveLine = moveLine.id) = 0)";
    }
    TypedQuery<MoveLine> moveLineQuery =
        JPA.em()
            .createQuery(
                "SELECT moveLine FROM MoveLine moveLine WHERE moveLine.account = :accountId AND "
                    + analyticFilter,
                MoveLine.class);
    moveLineQuery.setParameter("accountId", account);
    return moveLineQuery.getResultList();
  }

  public List<InvoiceLine> searchInvoiceLinesByAccountAndAnalytic(
      Account account, boolean isWithAnalytic) throws Exception {
    String analyticFilter = "";
    if (isWithAnalytic) {
      analyticFilter =
          "(invoiceLine.analyticDistributionTemplate is not null OR (SELECT COUNT(0) FROM AnalyticMoveLine analyticMoveLine WHERE analyticMoveLine.invoiceLine = invoiceLine.id) > 0)";
    } else {
      analyticFilter =
          "(invoiceLine.analyticDistributionTemplate is null OR (SELECT COUNT(0) FROM AnalyticMoveLine analyticMoveLine WHERE analyticMoveLine.invoiceLine = invoiceLine.id) = 0)";
    }
    TypedQuery<InvoiceLine> invoiceLineQuery =
        JPA.em()
            .createQuery(
                "SELECT invoiceLine FROM InvoiceLine invoiceLine WHERE invoiceLine.account = :accountId AND "
                    + analyticFilter,
                InvoiceLine.class);
    invoiceLineQuery.setParameter("accountId", account);
    return invoiceLineQuery.getResultList();
  }

  public void cleanAnalytic(Account account) throws Exception {
    List<MoveLine> moveLineList = this.searchMoveLinesByAccountAndAnalytic(account, true);
    for (MoveLine moveLine : moveLineList) {
      moveLineService.cleanAnalytic(moveLine);
    }

    List<InvoiceLine> incoiceLineList = this.searchInvoiceLinesByAccountAndAnalytic(account, true);
    for (InvoiceLine invoiceLine : incoiceLineList) {
      invoiceLineService.cleanAnalytic(invoiceLine);
    }
  }
}
