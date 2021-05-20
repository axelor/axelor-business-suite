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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.ReportedBalanceLine;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.db.repo.ReportedBalanceLineRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.base.db.AdjustHistory;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Period;
import com.axelor.apps.base.db.Year;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.PeriodRepository;
import com.axelor.apps.base.db.repo.YearRepository;
import com.axelor.apps.base.service.AdjustHistoryService;
import com.axelor.apps.base.service.YearServiceImpl;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YearServiceAccountImpl extends YearServiceImpl {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected ReportedBalanceLineRepository reportedBalanceLineRepo;
  protected AdjustHistoryService adjustHistoryService;
  protected PartnerRepository partnerRepository;
  protected PeriodServiceAccountImpl periodServiceAccountImpl;

  @Inject
  public YearServiceAccountImpl(
      PartnerRepository partnerRepository,
      ReportedBalanceLineRepository reportedBalanceLineRepo,
      YearRepository yearRepository,
      AdjustHistoryService adjustHistoryService,
      PeriodServiceAccountImpl periodServiceAccountImpl) {
    super(yearRepository);
    this.partnerRepository = partnerRepository;
    this.reportedBalanceLineRepo = reportedBalanceLineRepo;
    this.adjustHistoryService = adjustHistoryService;
    this.periodServiceAccountImpl = periodServiceAccountImpl;
  }

  /**
   * Procédure permettant de cloturer un exercice comptable
   *
   * @param year Un exercice comptable
   * @throws AxelorException
   */
  public void closeYearProcess(Year year) throws AxelorException {
    year = yearRepository.find(year.getId());

    for (Period period : year.getPeriodList()) {
      if (period.getStatusSelect() == PeriodRepository.STATUS_ADJUSTING) {
        adjustHistoryService.setEndDate(period);
      }

      period.setStatusSelect(PeriodRepository.STATUS_CLOSED);
      period.setClosureDateTime(LocalDateTime.now());
    }
    Company company = year.getCompany();
    if (company == null) {
      throw new AxelorException(
          year,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.YEAR_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          year.getName());
    }

    Query q;
    if (year.getStatusSelect() == YearRepository.STATUS_ADJUSTING) {
      AdjustHistory adjustHistory = adjustHistoryService.setEndDate(year);

      q =
          JPA.em()
              .createQuery(
                  "select DISTINCT(self.partner) FROM MoveLine as self WHERE self.move.ignoreInAccountingOk = false AND self.move.period.year = ?1 "
                      + "AND self.move.statusSelect = ?2 AND self.move.adjustingMove = true AND self.date >= ?3 AND self.date <= ?4 AND self.move.company = ?5");

      q.setParameter(1, year);
      q.setParameter(2, MoveRepository.STATUS_VALIDATED);
      q.setParameter(3, adjustHistory.getStartDate().toLocalDate());
      q.setParameter(4, adjustHistory.getEndDate().toLocalDate());
    } else {

      q =
          JPA.em()
              .createQuery(
                  "select DISTINCT(self.partner) FROM MoveLine as self WHERE self.move.ignoreInAccountingOk = false AND self.move.period.year = ?1 "
                      + "AND self.move.statusSelect = ?2 AND self.date >= ?3 AND self.date <= ?4 AND self.move.company = ?5");

      q.setParameter(1, year);
      q.setParameter(2, MoveRepository.STATUS_VALIDATED);
      q.setParameter(3, year.getFromDate());
      q.setParameter(4, year.getToDate());
    }
    q.setParameter(5, year.getCompany());

    @SuppressWarnings("unchecked")
    List<Partner> partnerList = q.getResultList();

    List<? extends Partner> partnerListAll = partnerRepository.all().fetch();

    log.debug("Nombre total de tiers : {}", partnerListAll.size());
    log.debug("Nombre de tiers récupéré : {}", partnerList.size());

    for (Partner partner : partnerList) {
      partner = partnerRepository.find(partner.getId());
      year = yearRepository.find(year.getId());
      log.debug("Tiers en cours de traitement : {}", partner.getName());

      for (AccountingSituation accountingSituation : partner.getAccountingSituationList()) {
        if (accountingSituation.getCompany().equals(year.getCompany())) {
          log.debug("On ajoute une ligne à la Situation comptable trouvée");

          BigDecimal reportedBalanceAmount =
              this.computeReportedBalance(year.getFromDate(), year.getToDate(), partner, year);
          this.createReportedBalanceLine(accountingSituation, reportedBalanceAmount, year);

          break;
        }
      }
      JPA.clear();
    }
    year = yearRepository.find(year.getId());
    closeYear(year);
  }

  @Transactional
  public void closeYear(Year year) {
    year.setStatusSelect(YearRepository.STATUS_CLOSED);
    year.setClosureDateTime(LocalDateTime.now());
    yearRepository.save(year);
  }

  /**
   * Procédure permettant de rectifier un exercice comptable
   *
   * @param year Un exercice comptable
   * @throws AxelorException
   */
  @Transactional
  public void adjust(Year year) {
    year = yearRepository.find(year.getId());

    adjustHistoryService.setStartDate(year);

    year.setStatusSelect(YearRepository.STATUS_ADJUSTING);
    yearRepository.save(year);
  }

  @Transactional
  public ReportedBalanceLine createReportedBalanceLine(
      AccountingSituation accountingSituation, BigDecimal amount, Year year) {
    ReportedBalanceLine reportedBalanceLine = new ReportedBalanceLine();
    accountingSituation.addReportedBalanceLineListItem(reportedBalanceLine);
    reportedBalanceLine.setAmount(amount);
    reportedBalanceLine.setYear(year);
    reportedBalanceLineRepo.save(reportedBalanceLine);
    return reportedBalanceLine;
  }

  public BigDecimal computeReportedBalance(
      LocalDate fromDate, LocalDate toDate, Partner partner, Year year) {
    Query q =
        JPA.em()
            .createQuery(
                "select SUM(self.debit - self.credit) FROM MoveLine as self "
                    + "WHERE self.partner = ?1 AND self.move.ignoreInAccountingOk = false AND self.date >= ?2 AND self.date <= ?3 "
                    + "AND self.move.period.year = ?4 AND self.move.statusSelect = ?5 AND self.account.useForPartnerBalance is true",
                BigDecimal.class);
    q.setParameter(1, partner);
    q.setParameter(2, fromDate);
    q.setParameter(3, toDate);
    q.setParameter(4, year);
    q.setParameter(5, MoveRepository.STATUS_VALIDATED);

    BigDecimal result = (BigDecimal) q.getSingleResult();
    log.debug("Annual balance : {} for partner : {}", result, partner.getPartnerSeq());

    if (result != null) {
      return result;
    } else {
      return BigDecimal.ZERO;
    }
  }

  @Deprecated
  public BigDecimal computeReportedBalance2(
      LocalDate fromDate, LocalDate toDate, Partner partner, Account account) {

    MoveLineRepository moveLineRepo = Beans.get(MoveLineRepository.class);

    List<? extends MoveLine> moveLineList =
        moveLineRepo
            .all()
            .filter(
                "self.partner = ?1 AND self.ignoreInAccountingOk = 'false' AND self.date >= ?2 AND self.date <= ?3 AND self.account = ?4",
                partner,
                fromDate,
                toDate,
                account)
            .fetch();

    BigDecimal reportedBalanceAmount = BigDecimal.ZERO;
    for (MoveLine moveLine : moveLineList) {
      if (moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0) {
        reportedBalanceAmount = reportedBalanceAmount.subtract(moveLine.getAmountRemaining().abs());
      } else if (moveLine.getCredit().compareTo(BigDecimal.ZERO) > 0) {
        reportedBalanceAmount = reportedBalanceAmount.add(moveLine.getAmountRemaining().abs());
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("Solde rapporté : {}", reportedBalanceAmount);
    }
    return reportedBalanceAmount;
  }
}
