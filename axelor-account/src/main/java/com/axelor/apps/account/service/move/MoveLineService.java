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
package com.axelor.apps.account.service.move;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MoveLineService {

  public static final boolean IS_CREDIT = false;
  public static final boolean IS_DEBIT = true;

  public MoveLine computeAnalyticDistribution(MoveLine moveLine);

  public MoveLine createAnalyticDistributionWithTemplate(MoveLine moveLine);

  public void updateAccountTypeOnAnalytic(
      MoveLine moveLine, List<AnalyticMoveLine> analyticMoveLineList);

  public void generateAnalyticMoveLines(MoveLine moveLine);

  public MoveLine createMoveLine(
      Move move,
      Partner partner,
      Account account,
      BigDecimal amountInSpecificMoveCurrency,
      boolean isDebit,
      LocalDate date,
      LocalDate dueDate,
      int counter,
      String origin,
      String description)
      throws AxelorException;

  public MoveLine createMoveLine(
      Move move,
      Partner partner,
      Account account,
      BigDecimal amountInSpecificMoveCurrency,
      BigDecimal amountInCompanyCurrency,
      BigDecimal currencyRate,
      boolean isDebit,
      LocalDate date,
      LocalDate dueDate,
      LocalDate originDate,
      int counter,
      String origin,
      String description)
      throws AxelorException;

  public MoveLine createMoveLine(
      Move move,
      Partner partner,
      Account account,
      BigDecimal amount,
      boolean isDebit,
      LocalDate date,
      int ref,
      String origin,
      String description)
      throws AxelorException;

  public List<MoveLine> createMoveLines(
      Invoice invoice,
      Move move,
      Company company,
      Partner partner,
      Account partnerAccount,
      boolean consolidate,
      boolean isPurchase,
      boolean isDebitCustomer)
      throws AxelorException;

  public MoveLine findConsolidateMoveLine(
      Map<List<Object>, MoveLine> map, MoveLine moveLine, List<Object> keys);

  public List<MoveLine> consolidateMoveLines(List<MoveLine> moveLines);

  public MoveLine getCreditCustomerMoveLine(Invoice invoice);

  public List<MoveLine> getCreditCustomerMoveLines(Invoice invoice);

  public MoveLine getCreditCustomerMoveLine(Move move);

  public List<MoveLine> getCreditCustomerMoveLines(Move move);

  public MoveLine getDebitCustomerMoveLine(Invoice invoice);

  public List<MoveLine> getDebitCustomerMoveLines(Invoice invoice);

  public MoveLine getDebitCustomerMoveLine(Move move);

  public List<MoveLine> getDebitCustomerMoveLines(Move move);

  public String determineDescriptionMoveLine(Journal journal, String origin, String description);

  public void usherProcess(MoveLine moveLine);

  public List<MoveLine> getReconciliableCreditMoveLines(List<MoveLine> moveLineList);

  public List<MoveLine> getReconciliableDebitMoveLines(List<MoveLine> moveLineList);

  public void reconcileMoveLinesWithCacheManagement(List<MoveLine> moveLineList);

  public void reconcileMoveLines(List<MoveLine> moveLineList);

  public void autoTaxLineGenerate(Move move) throws AxelorException;

  public MoveLine createNewMoveLine(
      BigDecimal debit,
      BigDecimal credit,
      LocalDate date,
      String accountType,
      TaxLine taxLine,
      MoveLine newOrUpdatedMoveLine);

  public void validateMoveLine(MoveLine moveLine) throws AxelorException;

  public MoveLine generateTaxPaymentMoveLineList(
      MoveLine customerMoveLine, Invoice invoice, Reconcile reconcile) throws AxelorException;

  public MoveLine reverseTaxPaymentMoveLines(MoveLine customerMoveLine, Reconcile reconcile)
      throws AxelorException;

  public MoveLine computeTaxAmount(MoveLine moveLine) throws AxelorException;
}
