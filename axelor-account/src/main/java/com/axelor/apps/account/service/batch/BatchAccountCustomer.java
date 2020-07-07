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
package com.axelor.apps.account.service.batch;

import com.axelor.apps.account.db.AccountingBatch;
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.repo.AccountingSituationRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountCustomerService;
import com.axelor.apps.base.db.Company;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchAccountCustomer extends BatchStrategy {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AccountingSituationRepository accountingSituationRepo;

  @Inject
  public BatchAccountCustomer(
      AccountCustomerService accountCustomerService,
      AccountingSituationRepository accountingSituationRepo) {

    super(accountCustomerService);

    this.accountingSituationRepo = accountingSituationRepo;
  }

  @Override
  protected void start() throws IllegalAccessException {
    super.start();
    checkPoint();
  }

  @Override
  public void process() {

    AccountingBatch accountingBatch = batch.getAccountingBatch();
    Company company = accountingBatch.getCompany();

    boolean updateCustAccountOk = accountingBatch.getUpdateCustAccountOk();
    boolean updateDueCustAccountOk = accountingBatch.getUpdateDueCustAccountOk();
    boolean updateDueDebtRecoveryCustAccountOk =
        accountingBatch.getUpdateDueDebtRecoveryCustAccountOk();

    int fetchLimit = getFetchLimit();

    List<AccountingSituation> accountingSituationList = null;
    Query<AccountingSituation> query =
        accountingSituationRepo.all().filter("self.company = ?1", company);
    int i = 0;

    int offset = 0;
    while (!(accountingSituationList = query.fetch(fetchLimit, offset)).isEmpty()) {
      JPA.clear();
      for (AccountingSituation accountingSituation : accountingSituationList) {
        try {
          ++offset;

          accountingSituation =
              accountCustomerService.updateAccountingSituationCustomerAccount(
                  accountingSituationRepo.find(accountingSituation.getId()),
                  updateCustAccountOk,
                  updateDueCustAccountOk,
                  updateDueDebtRecoveryCustAccountOk);

          if (accountingSituation != null) {
            this.updateAccountingSituation(accountingSituation);
            i++;
          }

        } catch (Exception e) {

          TraceBackService.trace(
              new Exception(
                  String.format(
                      I18n.get(IExceptionMessage.BATCH_ACCOUNT_1),
                      accountingSituationRepo.find(accountingSituation.getId()).getName()),
                  e),
              ExceptionOriginRepository.ACCOUNT_CUSTOMER,
              batch.getId());

          incrementAnomaly();

          log.error(
              "Bug(Anomalie) généré(e) pour la situation compable {}",
              accountingSituationRepo.find(accountingSituation.getId()).getName());

        } finally {

          if (i % 1 == 0) {
            JPA.clear();
          }
        }
      }
    }
  }

  /**
   * As {@code batch} entity can be detached from the session, call {@code Batch.find()} get the
   * entity in the persistant context. Warning : {@code batch} entity have to be saved before.
   */
  @Override
  protected void stop() {
    String comment = "";
    comment = I18n.get(IExceptionMessage.BATCH_ACCOUNT_2) + "\n";
    comment +=
        String.format("\t" + I18n.get(IExceptionMessage.BATCH_ACCOUNT_3) + "\n", batch.getDone());
    comment +=
        String.format(
            "\t" + I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ALARM_ENGINE_BATCH_4),
            batch.getAnomaly());

    super.stop();
    addComment(comment);
  }

  public String updateAccountingSituationMarked(Company company) {

    int anomaly = 0;

    List<AccountingSituation> accountingSituationList = null;

    if (company != null) {
      accountingSituationList =
          accountingSituationRepo
              .all()
              .filter("self.company = ?1 and self.custAccountMustBeUpdateOk = 'true'", company)
              .fetch();
    } else {
      accountingSituationList =
          accountingSituationRepo.all().filter("self.custAccountMustBeUpdateOk = 'true'").fetch();
    }

    int i = 0;
    JPA.clear();
    for (AccountingSituation accountingSituation : accountingSituationList) {
      try {

        accountingSituation =
            accountCustomerService.updateAccountingSituationCustomerAccount(
                accountingSituationRepo.find(accountingSituation.getId()), true, true, false);

        if (accountingSituation != null) {
          i++;
        }

      } catch (Exception e) {

        TraceBackService.trace(
            new Exception(
                String.format(
                    I18n.get(IExceptionMessage.BATCH_ACCOUNT_1),
                    accountingSituationRepo.find(accountingSituation.getId()).getName()),
                e),
            ExceptionOriginRepository.CUSTOMER_ACCOUNT,
            batch.getId());

        anomaly++;

        log.error(
            "Bug(Anomalie) généré(e) pour le compte client {}",
            accountingSituationRepo.find(accountingSituation.getId()));

      } finally {

        if (i % 5 == 0) {
          JPA.clear();
        }
      }
    }

    if (anomaly != 0) {
      return String.format(I18n.get(IExceptionMessage.BATCH_ACCOUNT_4), anomaly);
    } else {
      return String.format(I18n.get(IExceptionMessage.BATCH_ACCOUNT_5), i);
    }
  }
}
