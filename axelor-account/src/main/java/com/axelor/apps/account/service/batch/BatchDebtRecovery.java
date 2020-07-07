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

import com.axelor.apps.account.db.DebtRecovery;
import com.axelor.apps.account.db.repo.DebtRecoveryRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.debtrecovery.DebtRecoveryActionService;
import com.axelor.apps.account.service.debtrecovery.DebtRecoveryService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.db.EntityHelper;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.util.List;
import javax.persistence.Table;

public class BatchDebtRecovery extends BatchStrategy {

  protected boolean stopping = false;
  protected PartnerRepository partnerRepository;
  protected MessageRepository messageRepository;
  protected DebtRecoveryRepository debtRecoveryRepository;
  protected DebtRecoveryActionService debtRecoveryActionService;

  @Inject
  public BatchDebtRecovery(
      DebtRecoveryService debtRecoveryService,
      PartnerRepository partnerRepository,
      DebtRecoveryRepository debtRecoveryRepository,
      DebtRecoveryActionService debtRecoveryActionService,
      MessageRepository messageRepository) {
    super(debtRecoveryService);
    this.partnerRepository = partnerRepository;
    this.debtRecoveryRepository = debtRecoveryRepository;
    this.debtRecoveryActionService = debtRecoveryActionService;
    this.messageRepository = messageRepository;
  }

  @Override
  protected void start() throws IllegalAccessException {

    super.start();

    Company company = batch.getAccountingBatch().getCompany();

    try {

      debtRecoveryService.testCompanyField(company);

    } catch (AxelorException e) {

      TraceBackService.trace(
          new AxelorException(e, e.getCategory(), ""),
          ExceptionOriginRepository.DEBT_RECOVERY,
          batch.getId());
      incrementAnomaly();
      stopping = true;
    }

    checkPoint();
  }

  @Override
  protected void process() {

    if (!stopping) {
      int fetchLimit = getFetchLimit();
      this.debtRecoveryPartner(fetchLimit);
    }
  }

  public void debtRecoveryPartner(int fetchLimit) {
    Company company = batch.getAccountingBatch().getCompany();

    Query<Partner> query =
        partnerRepository
            .all()
            .filter(
                "self.isContact = false "
                    + "AND :_company MEMBER OF self.companySet "
                    + "AND self.accountingSituationList IS NOT EMPTY "
                    + "AND self.isCustomer = true "
                    + "AND self.id NOT IN ("
                    + Beans.get(BlockingService.class)
                        .listOfBlockedPartner(company, BlockingRepository.REMINDER_BLOCKING)
                    + ")")
            .bind("_company", company)
            .order("id");

    int offset = 0;
    List<Partner> partnerList;

    while (!(partnerList = query.fetch(fetchLimit, offset)).isEmpty()) {
      findBatch();

      for (Partner partner : partnerList) {
        ++offset;

        try {
          boolean remindedOk = debtRecoveryService.debtRecoveryGenerate(partner, company);
          if (remindedOk) {
            DebtRecovery debtRecovery = debtRecoveryService.getDebtRecovery(partner, company);
            addBatchToModel(debtRecovery);
            incrementDone(partner);
          }
        } catch (AxelorException e) {
          TraceBackService.trace(
              new AxelorException(
                  e, e.getCategory(), I18n.get("Partner") + " %s", partner.getName()),
              ExceptionOriginRepository.DEBT_RECOVERY,
              batch.getId());
          incrementAnomaly(partner);
          break;
        } catch (Exception e) {
          TraceBackService.trace(
              new Exception(String.format(I18n.get("Partner") + " %s", partner.getName()), e),
              ExceptionOriginRepository.DEBT_RECOVERY,
              batch.getId());
          incrementAnomaly(partner);
          break;
        }
      }

      JPA.clear();
    }
  }

  protected void incrementDone(Partner partner) {
    addBatchToModel(partner);
    _incrementDone();
  }

  protected void incrementAnomaly(Partner partner) {
    findBatch();
    partner = partnerRepository.find(partner.getId());
    // addBatchToModel(partner);
    _incrementAnomaly();
  }

  protected void addBatchToModel(Model model) {
    String tableName = getBatchSetTableName(model);

    // Insert using native query for performance reasons in case of big batch set.
    String sqlString = String.format("INSERT INTO %s VALUES (:modelId, :batchId)", tableName);
    javax.persistence.Query query = JPA.em().createNativeQuery(sqlString);
    query.setParameter("modelId", model.getId());
    query.setParameter("batchId", batch.getId());
    JPA.runInTransaction(query::executeUpdate);
  }

  private String getBatchSetTableName(Model model) {
    String modelTableName = EntityHelper.getEntityClass(model).getAnnotation(Table.class).name();
    return modelTableName + "_BATCH_SET";
  }

  /**
   * As {@code batch} entity can be detached from the session, call {@code Batch.find()} get the
   * entity in the persistant context. Warning : {@code batch} entity have to be saved before.
   */
  @Override
  protected void stop() {

    String comment = I18n.get(IExceptionMessage.BATCH_DEBT_RECOVERY_1);
    comment +=
        String.format(
            "\t* %s " + I18n.get(IExceptionMessage.BATCH_DEBT_RECOVERY_2) + "\n", batch.getDone());
    comment +=
        String.format(
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ALARM_ENGINE_BATCH_4),
            batch.getAnomaly());

    super.stop();
    addComment(comment);
  }
}
