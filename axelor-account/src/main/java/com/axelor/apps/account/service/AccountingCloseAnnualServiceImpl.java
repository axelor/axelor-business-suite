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

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.AccountRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveCreateService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveValidateService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Year;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.Query;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountingCloseAnnualServiceImpl implements AccountingCloseAnnualService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected MoveCreateService moveCreateService;
  protected MoveLineService moveLineService;
  protected AccountConfigService accountConfigService;
  protected MoveRepository moveRepository;
  protected MoveValidateService moveValidateService;
  protected ReconcileService reconcileService;
  protected AccountService accountService;
  protected AccountRepository accountRepository;
  protected int counter = 0;

  @Inject
  public AccountingCloseAnnualServiceImpl(
      MoveCreateService moveCreateService,
      MoveLineService moveLineService,
      AccountConfigService accountConfigService,
      MoveRepository moveRepository,
      MoveValidateService moveValidateService,
      ReconcileService reconcileService,
      AccountService accountService,
      AccountRepository accountRepository) {

    this.moveCreateService = moveCreateService;
    this.moveLineService = moveLineService;
    this.accountConfigService = accountConfigService;
    this.moveRepository = moveRepository;
    this.moveValidateService = moveValidateService;
    this.reconcileService = reconcileService;
    this.accountService = accountService;
    this.accountRepository = accountRepository;
  }

  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public List<Move> generateCloseAnnualAccount(
      Year year,
      Account account,
      Partner partner,
      LocalDate endOfYearDate,
      LocalDate reportedBalanceDate,
      String origin,
      String moveDescription,
      boolean closeYear,
      boolean openYear,
      boolean allocatePerPartner)
      throws AxelorException {

    List<Move> moveList = new ArrayList<>();

    Move closeYearMove = null;
    Move openYearMove = null;

    if (closeYear) {
      closeYearMove =
          generateCloseAnnualAccountMove(
              year,
              account,
              endOfYearDate,
              endOfYearDate,
              origin,
              moveDescription,
              partner,
              false,
              allocatePerPartner);

      if (closeYearMove == null) {
        return null;
      }
      moveList.add(closeYearMove);
    }

    if (openYear) {
      openYearMove =
          generateCloseAnnualAccountMove(
              year,
              account,
              reportedBalanceDate,
              endOfYearDate,
              origin,
              moveDescription,
              partner,
              true,
              allocatePerPartner);

      if (openYearMove == null) {
        return null;
      }
      moveList.add(openYearMove);
    }

    if (closeYearMove != null && openYearMove != null) {
      reconcile(closeYearMove, openYearMove);
    }

    return moveList;
  }

  protected Move generateCloseAnnualAccountMove(
      Year year,
      Account account,
      LocalDate moveDate,
      LocalDate originDate,
      String origin,
      String moveDescription,
      Partner partner,
      boolean isReverse,
      boolean allocatePerPartner)
      throws AxelorException {

    Company company = account.getCompany();

    AccountConfig accountConfig = accountConfigService.getAccountConfig(company);

    BigDecimal balance = computeBalance(year, account, partner, allocatePerPartner);

    if (balance.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }

    Integer functionalOriginSelect = null;

    if (isReverse) {
      balance = balance.negate();
      functionalOriginSelect = MoveRepository.FUNCTIONAL_ORIGIN_OPENING;
    } else {
      functionalOriginSelect = MoveRepository.FUNCTIONAL_ORIGIN_CLOSURE;
    }

    Move move =
        moveCreateService.createMove(
            accountConfigService.getReportedBalanceJournal(accountConfig),
            company,
            company.getCurrency(),
            partner,
            moveDate,
            null,
            MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC,
            false,
            false,
            !isReverse);
    move.setFunctionalOriginSelect(functionalOriginSelect);
    counter = 0;

    this.generateCloseAnnualMoveLine(
        move, origin, account, moveDescription, originDate, balance.negate());

    this.generateCloseAnnualMoveLine(
        move,
        origin,
        getYearClosureOrOpeningAccount(accountConfig, isReverse),
        moveDescription,
        originDate,
        balance);

    if (move.getMoveLineList() != null && !move.getMoveLineList().isEmpty()) {
      moveValidateService.validate(move);
    } else {
      moveRepository.remove(move);
      return null;
    }

    return move;
  }

  protected Account getYearClosureOrOpeningAccount(AccountConfig accountConfig, boolean isReverse)
      throws AxelorException {

    if (isReverse) {
      return accountConfigService.getYearOpeningAccount(accountConfig);
    } else {
      return accountConfigService.getYearClosureAccount(accountConfig);
    }
  }

  protected MoveLine generateCloseAnnualMoveLine(
      Move move,
      String origin,
      Account account,
      String moveDescription,
      LocalDate originDate,
      BigDecimal balance)
      throws AxelorException {
    LocalDate moveDate = move.getDate();

    MoveLine moveLine =
        moveLineService.createMoveLine(
            move,
            move.getPartner(),
            account,
            balance.abs(),
            balance.abs(),
            null,
            balance.compareTo(BigDecimal.ZERO) == 1,
            moveDate,
            moveDate,
            originDate,
            ++counter,
            origin,
            moveDescription);

    move.addMoveLineListItem(moveLine);

    return moveLine;
  }

  protected BigDecimal computeBalance(
      Year year, Account account, Partner partner, boolean allocatePerPartner) {

    String prepareQuery =
        "select SUM(self.debit - self.credit) FROM MoveLine as self "
            + "WHERE self.move.ignoreInAccountingOk = false AND self.move.period.year = ?1 AND self.account = ?2 "
            + "AND self.move.statusSelect = ?3 AND self.move.autoYearClosureMove is not true";

    if (allocatePerPartner && account.getUseForPartnerBalance()) {
      if (partner != null) {
        prepareQuery += " AND self.partner = ?4";
      } else {
        prepareQuery += " AND self.partner is null";
      }
    }

    Query q = JPA.em().createQuery(prepareQuery, BigDecimal.class);
    q.setParameter(1, year);
    q.setParameter(2, account);
    q.setParameter(3, MoveRepository.STATUS_VALIDATED);

    if (partner != null) {
      q.setParameter(4, partner);
    }

    BigDecimal result = (BigDecimal) q.getSingleResult();
    LOG.debug(
        "Balance : {} for the account : {} and the year : {}",
        result,
        account.getCode(),
        year.getCode());

    if (result != null) {
      return result;
    } else {
      return BigDecimal.ZERO;
    }
  }

  protected void reconcile(Move move, Move reverseMove) throws AxelorException {

    List<MoveLine> moveLineSortedList = move.getMoveLineList();
    Collections.sort(moveLineSortedList, Comparator.comparing(MoveLine::getCounter));

    List<MoveLine> reverseMoveLineSortedList = reverseMove.getMoveLineList();
    Collections.sort(reverseMoveLineSortedList, Comparator.comparing(MoveLine::getCounter));

    Iterator<MoveLine> reverseMoveLinesIt = reverseMoveLineSortedList.iterator();

    for (MoveLine moveLine : moveLineSortedList) {

      MoveLine reverseMoveLine = reverseMoveLinesIt.next();

      reconcileService.reconcile(moveLine, reverseMoveLine, false, false);
    }
  }

  public List<Long> getAllAccountOfYear(
      Set<Account> accountSet, Year year, int fetchLimit, int position) {

    List<Long> accountIdList =
        accountService.getAllAccountsSubAccountIncluded(
            accountSet.stream().map(Account::getId).collect(Collectors.toList()));

    Query q =
        JPA.em()
            .createQuery(
                "select distinct(self.account.id) FROM MoveLine as self "
                    + "WHERE self.move.ignoreInAccountingOk = false AND self.move.period.year  = ?1 AND self.account.id in (?2) "
                    + "AND self.move.statusSelect = ?3 AND self.move.autoYearClosureMove is not true",
                Long.class);
    q.setParameter(1, year);
    q.setParameter(2, accountIdList);
    q.setParameter(3, MoveRepository.STATUS_VALIDATED);
    q.setMaxResults(fetchLimit);
    q.setFirstResult(position);

    List<Long> result = q.getResultList();

    return result;
  }

  public List<Pair<Long, Long>> assignPartner(
      List<Long> accountIdList, Year year, boolean allocatePerPartner) {

    List<Pair<Long, Long>> accountAndPartnerPair = new ArrayList<>();

    for (Long accountId : accountIdList) {
      if (allocatePerPartner && accountRepository.find(accountId).getUseForPartnerBalance()) {
        for (Long partnerId : getPartner(accountId, year)) {
          accountAndPartnerPair.add(Pair.of(accountId, partnerId));
        }

      } else {
        accountAndPartnerPair.add(Pair.of(accountId, null));
      }
    }
    return accountAndPartnerPair;
  }

  protected List<Long> getPartner(Long accountId, Year year) {

    Query q =
        JPA.em()
            .createQuery(
                "select distinct(self.partner.id) FROM MoveLine as self "
                    + "WHERE self.move.ignoreInAccountingOk = false AND self.move.period.year = ?1 AND self.account.id = ?2 "
                    + "AND self.move.statusSelect = ?3 AND self.move.autoYearClosureMove is not true",
                Long.class);
    q.setParameter(1, year);
    q.setParameter(2, accountId);
    q.setParameter(3, MoveRepository.STATUS_VALIDATED);

    List<Long> result = q.getResultList();

    return result;
  }
}
