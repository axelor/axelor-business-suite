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
package com.axelor.apps.account.service.batch;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AccountingBatch;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.repo.AccountRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountingCloseAnnualService;
import com.axelor.apps.account.service.AccountingReportService;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Year;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.YearRepository;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchCloseAnnualAccounts extends BatchStrategy {
  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected PartnerRepository partnerRepository;
  protected YearRepository yearRepository;
  protected AccountRepository accountRepository;
  protected AccountingCloseAnnualService accountingCloseAnnualService;
  protected MoveRepository moveRepo;
  protected boolean end = false;

  @Inject
  public BatchCloseAnnualAccounts(
      PartnerRepository partnerRepository,
      YearRepository yearRepository,
      AccountRepository accountRepository,
      AccountingCloseAnnualService accountingCloseAnnualService,
      MoveRepository moveRepo) {
    this.partnerRepository = partnerRepository;
    this.yearRepository = yearRepository;
    this.accountRepository = accountRepository;
    this.accountingCloseAnnualService = accountingCloseAnnualService;
    this.moveRepo = moveRepo;
  }

  @Override
  protected void start() throws IllegalAccessException {
    super.start();
    try {
      Beans.get(AccountingReportService.class)
          .testReportedDateField(batch.getAccountingBatch().getYear().getReportedBalanceDate());
      this.testCloseAnnualBatchFields();
    } catch (AxelorException e) {
      TraceBackService.trace(
          new AxelorException(e, e.getCategory(), ""),
          ExceptionOriginRepository.REPORTED_BALANCE,
          batch.getId());
      incrementAnomaly();
      end = true;
    }
  }

  protected void testCloseAnnualBatchFields() throws AxelorException {
    AccountingBatch accountingBatch = batch.getAccountingBatch();
    if (CollectionUtils.isEmpty(accountingBatch.getAccountSet())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.BATCH_CLOSE_ANNUAL_ACCOUNT_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountingBatch.getCode());
    }
    if (accountingBatch.getYear() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.BATCH_CLOSE_ANNUAL_ACCOUNT_2),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          accountingBatch.getCode());
    }
  }

  protected void process() {
    if (!end) {
      AccountingBatch accountingBatch = batch.getAccountingBatch();
      removeSimulatedMoves(accountingBatch);
      boolean allocatePerPartner = accountingBatch.getAllocatePerPartner();
      boolean closeYear = accountingBatch.getCloseYear();
      boolean openYear = accountingBatch.getOpenYear();
      Year year = accountingBatch.getYear();
      LocalDate endOfYearDate = year.getToDate();
      LocalDate reportedBalanceDate = year.getReportedBalanceDate();
      String origin = accountingBatch.getCode();
      String moveDescription = accountingBatch.getMoveDescription();

      List<Long> accountIdList =
          accountingCloseAnnualService.getAllAccountOfYear(accountingBatch.getAccountSet(), year);

      List<Pair<Long, Long>> accountAndPartnerPairList =
          accountingCloseAnnualService.assignPartner(accountIdList, year, allocatePerPartner);

      Account account = null;
      Partner partner = null;

      for (Pair<Long, Long> accountAndPartnerPair : accountAndPartnerPairList) {

        try {
          account = accountRepository.find(accountAndPartnerPair.getLeft());
          if (accountAndPartnerPair.getRight() != null) {
            partner = partnerRepository.find(accountAndPartnerPair.getRight());
          } else {
            partner = null;
          }

          List<Move> generateMoves =
              accountingCloseAnnualService.generateCloseAnnualAccount(
                  yearRepository.find(year.getId()),
                  account,
                  partner,
                  endOfYearDate,
                  reportedBalanceDate,
                  origin,
                  moveDescription,
                  closeYear,
                  openYear,
                  allocatePerPartner);

          if (generateMoves != null && !generateMoves.isEmpty()) {
            updateAccount(account);

            for (Move move : generateMoves) {
              updateAccountMove(move, false);
            }
          }

        } catch (AxelorException e) {
          TraceBackService.trace(
              new AxelorException(
                  e, e.getCategory(), I18n.get("Account") + " %s", account.getCode()),
              null,
              batch.getId());
          incrementAnomaly();
          break;
        } catch (Exception e) {
          TraceBackService.trace(
              new Exception(String.format(I18n.get("Account") + " %s", account.getCode()), e),
              null,
              batch.getId());
          incrementAnomaly();
          LOG.error("Anomaly generated for the account {}", account.getCode());
          break;
        } finally {
          JPA.clear();
        }
      }
    }
  }

  @Transactional
  public void removeSimulatedMoves(AccountingBatch accountingBatch) {
    AccountConfig accountConfig = accountingBatch.getCompany().getAccountConfig();
    if (accountConfig != null
        && accountConfig.getIsActivateSimulatedMove()
        && accountingBatch.getIsDeleteSimulatedMove()) {
      List<Move> moveList =
          moveRepo
              .all()
              .filter(
                  "self.company = ?1 AND self.statusSelect = ?2 AND self.period.year = ?3",
                  accountingBatch.getCompany(),
                  MoveRepository.STATUS_SIMULATED,
                  accountingBatch.getYear())
              .fetch();
      for (Move move : moveList) {
        moveRepo.remove(move);
      }
    }
  }

  @Override
  protected void stop() {
    StringBuilder sb = new StringBuilder();
    sb.append(I18n.get(IExceptionMessage.BATCH_CLOSE_OPEN_ANNUAL_ACCOUNT_REPORT_TITLE)).append(" ");
    sb.append(
        String.format(
            I18n.get(
                    IExceptionMessage.BATCH_CLOSE_OPEN_ANNUAL_ACCOUNT_DONE_SINGULAR,
                    IExceptionMessage.BATCH_CLOSE_OPEN_ANNUAL_ACCOUNT_DONE_PLURAL,
                    batch.getDone())
                + " ",
            batch.getDone()));
    sb.append(
        String.format(
            I18n.get(
                com.axelor.apps.base.exceptions.IExceptionMessage.ABSTRACT_BATCH_ANOMALY_SINGULAR,
                com.axelor.apps.base.exceptions.IExceptionMessage.ABSTRACT_BATCH_ANOMALY_PLURAL,
                batch.getAnomaly()),
            batch.getAnomaly()));
    addComment(sb.toString());
    super.stop();
  }
}
