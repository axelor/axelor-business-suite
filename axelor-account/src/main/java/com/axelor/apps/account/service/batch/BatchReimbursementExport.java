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

import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Reimbursement;
import com.axelor.apps.account.db.repo.AccountingBatchRepository;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.db.repo.ReimbursementRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountingService;
import com.axelor.apps.account.service.ReimbursementExportService;
import com.axelor.apps.account.service.bankorder.file.cfonb.CfonbExportService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchReimbursementExport extends BatchStrategy {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected boolean stop = false;

  protected BigDecimal totalAmount = BigDecimal.ZERO;

  protected String updateCustomerAccountLog = "";

  protected ReimbursementRepository reimbursementRepo;
  protected PartnerRepository partnerRepository;

  @Inject
  public BatchReimbursementExport(
      ReimbursementExportService reimbursementExportService,
      CfonbExportService cfonbExportService,
      BatchAccountCustomer batchAccountCustomer,
      ReimbursementRepository reimbursementRepo,
      PartnerRepository partnerRepository) {

    super(reimbursementExportService, cfonbExportService, batchAccountCustomer);

    this.reimbursementRepo = reimbursementRepo;
    this.partnerRepository = partnerRepository;

    AccountingService.setUpdateCustomerAccount(false);
  }

  @Override
  protected void start() throws IllegalAccessException {

    super.start();

    Company company = batch.getAccountingBatch().getCompany();

    switch (batch.getAccountingBatch().getReimbursementExportTypeSelect()) {
      case AccountingBatchRepository.REIMBURSEMENT_EXPORT_TYPE_GENERATE:
        try {
          this.testAccountingBatchBankDetails(batch.getAccountingBatch());
          reimbursementExportService.testCompanyField(company);
        } catch (AxelorException e) {
          TraceBackService.trace(
              new AxelorException(e, e.getCategory(), ""),
              ExceptionOriginRepository.REIMBURSEMENT,
              batch.getId());
          incrementAnomaly();
          stop = true;
        }
        break;

      case AccountingBatchRepository.REIMBURSEMNT_EXPORT_TYPE_EXPORT:
        try {
          this.testAccountingBatchBankDetails(batch.getAccountingBatch());
          reimbursementExportService.testCompanyField(company);
          cfonbExportService.testCompanyExportCFONBField(company);
        } catch (AxelorException e) {
          TraceBackService.trace(
              new AxelorException(e, e.getCategory(), ""),
              ExceptionOriginRepository.REIMBURSEMENT,
              batch.getId());
          incrementAnomaly();
          stop = true;
        }
        break;

      default:
        TraceBackService.trace(
            new AxelorException(
                TraceBackRepository.CATEGORY_INCONSISTENCY,
                I18n.get(IExceptionMessage.BATCH_PAYMENT_SCHEDULE_1),
                batch.getAccountingBatch().getActionSelect()),
            ExceptionOriginRepository.REIMBURSEMENT,
            batch.getId());
        incrementAnomaly();
        stop = true;
    }

    checkPoint();
  }

  @Override
  protected void process() {
    if (!stop) {
      Company company = batch.getAccountingBatch().getCompany();

      switch (batch.getAccountingBatch().getReimbursementExportTypeSelect()) {
        case AccountingBatchRepository.REIMBURSEMENT_EXPORT_TYPE_GENERATE:
          this.runCreateReimbursementExport(company);

          break;

        case AccountingBatchRepository.REIMBURSEMNT_EXPORT_TYPE_EXPORT:
          this.runReimbursementExportProcess(company);

          updateCustomerAccountLog +=
              batchAccountCustomer.updateAccountingSituationMarked(
                  companyRepo.find(company.getId()));

          break;

        default:
          break;
      }
    }
  }

  public void runCreateReimbursementExport(Company company) {

    List<Reimbursement> reimbursementList = null;
    Query<Reimbursement> query =
        reimbursementRepo
            .all()
            .filter(
                "self.statusSelect != ?1 AND self.statusSelect != ?2 AND self.company = ?3 "
                    + "AND self.partner.id NOT IN ("
                    + Beans.get(BlockingService.class)
                        .listOfBlockedPartner(company, BlockingRepository.REIMBURSEMENT_BLOCKING)
                    + " )",
                ReimbursementRepository.STATUS_REIMBURSED,
                ReimbursementRepository.STATUS_CANCELED,
                company);

    int fetchLimit = getFetchLimit();
    int i = 0;
    int offset = 0;
    while (!(reimbursementList = query.fetch(fetchLimit, offset)).isEmpty()) {
      offset += reimbursementList.size();
      for (Reimbursement reimbursement : reimbursementList) {
        log.debug("Remboursement n° {}", reimbursement.getRef());

        updateReimbursement(reimbursementRepo.find(reimbursement.getId()));
      }

      List<Partner> partnerList = Lists.transform(reimbursementList, Reimbursement::getPartner);

      for (Partner partner : partnerList) {

        try {
          partner = partnerRepository.find(partner.getId());

          log.debug("Tiers n° {}", partner.getName());

          List<MoveLine> moveLineList = null;
          Query<MoveLine> moveLineQuery =
              moveLineRepo
                  .all()
                  .filter(
                      "self.account.useForPartnerBalance = 'true' "
                          + "AND (self.move.statusSelect = ?1 OR self.move.statusSelect = ?2) AND self.amountRemaining > 0 AND self.credit > 0 AND self.partner = ?3 AND self.move.company = ?4 AND "
                          + "self.reimbursementStatusSelect = ?5 ",
                      MoveRepository.STATUS_VALIDATED,
                      MoveRepository.STATUS_DAYBOOK,
                      partnerRepository.find(partner.getId()),
                      companyRepo.find(company.getId()),
                      MoveLineRepository.REIMBURSEMENT_STATUS_NULL);

          log.debug("Liste des trop perçus : {}", moveLineList);
          int moveLineOffset = 0;
          while (!(moveLineList = moveLineQuery.fetch(fetchLimit, moveLineOffset)).isEmpty()) {
            moveLineOffset += moveLineList.size();
            if (moveLineList != null && !moveLineList.isEmpty()) {

              Reimbursement reimbursement =
                  reimbursementExportService.runCreateReimbursement(
                      moveLineList,
                      companyRepo.find(company.getId()),
                      partnerRepository.find(partner.getId()));
              if (reimbursement != null) {
                updateReimbursement(reimbursementRepo.find(reimbursement.getId()));
                this.totalAmount =
                    this.totalAmount.add(
                        reimbursementRepo.find(reimbursement.getId()).getAmountToReimburse());
                i++;
              }
            }
          }
        } catch (AxelorException e) {

          TraceBackService.trace(
              new AxelorException(
                  e,
                  e.getCategory(),
                  I18n.get("Partner") + "%s",
                  partnerRepository.find(partner.getId()).getName()),
              ExceptionOriginRepository.REIMBURSEMENT,
              batch.getId());

          incrementAnomaly();

        } catch (Exception e) {

          TraceBackService.trace(
              new Exception(
                  String.format(
                      I18n.get("Partner") + "%s",
                      partnerRepository.find(partner.getId()).getName()),
                  e),
              ExceptionOriginRepository.REIMBURSEMENT,
              batch.getId());

          incrementAnomaly();

          log.error(
              "Bug(Anomalie) généré(e) pour le tiers {}",
              partnerRepository.find(partner.getId()).getName());

        } finally {

          if (i % 10 == 0) {
            JPA.clear();
          }
        }
      }
    }
  }

  public void runReimbursementExportProcess(Company company) {

    int i = 0;

    // On récupère les remboursements dont les trop perçu ont été annulés
    List<Reimbursement> reimbursementToCancelList =
        reimbursementRepo
            .all()
            .filter(
                "self.company = ?1 and self.statusSelect = ?2 and self.amountToReimburse = 0",
                ReimbursementRepository.STATUS_VALIDATED,
                company)
            .fetch();

    // On annule les remboursements
    for (Reimbursement reimbursement : reimbursementToCancelList) {
      reimbursement.setStatusSelect(ReimbursementRepository.STATUS_CANCELED);
    }

    // On récupère les remboursement à rembourser
    List<Reimbursement> reimbursementList = null;
    Query<Reimbursement> query =
        reimbursementRepo
            .all()
            .filter(
                "self.company = ?1 and self.statusSelect = ?2 and self.amountToReimburse > 0 AND self.partner",
                company,
                ReimbursementRepository.STATUS_VALIDATED);

    List<Reimbursement> reimbursementToExport = new ArrayList<>();

    int fetchLimit = getFetchLimit();
    int offset = 0;
    while (!(reimbursementList = query.fetch(fetchLimit, offset)).isEmpty()) {
      for (Reimbursement reimbursement : reimbursementList) {
        ++offset;
        try {
          reimbursement = reimbursementRepo.find(reimbursement.getId());

          if (reimbursementExportService.canBeReimbursed(
              reimbursement.getPartner(), reimbursement.getCompany())) {

            reimbursementExportService.reimburse(reimbursement, company);
            updateReimbursement(reimbursementRepo.find(reimbursement.getId()));
            reimbursementToExport.add(reimbursement);
            this.totalAmount =
                this.totalAmount.add(
                    reimbursementRepo.find(reimbursement.getId()).getAmountReimbursed());
            i++;
          }

        } catch (AxelorException e) {

          TraceBackService.trace(
              new AxelorException(
                  e,
                  e.getCategory(),
                  I18n.get("Reimbursement") + " %s",
                  reimbursementRepo.find(reimbursement.getId()).getRef()),
              ExceptionOriginRepository.REIMBURSEMENT,
              batch.getId());

          incrementAnomaly();

        } catch (Exception e) {

          TraceBackService.trace(
              new Exception(
                  String.format(
                      I18n.get("Reimbursement") + " %s",
                      reimbursementRepo.find(reimbursement.getId()).getRef()),
                  e),
              ExceptionOriginRepository.REIMBURSEMENT,
              batch.getId());

          incrementAnomaly();

          log.error(
              "Bug(Anomalie) généré(e) pour l'export du remboursement {}",
              reimbursementRepo.find(reimbursement.getId()).getRef());

        } finally {

          if (i % 10 == 0) {
            JPA.clear();
          }
        }
      }
    }

    if (reimbursementToExport != null && !reimbursementToExport.isEmpty()) {
      /*
      try {

      	reimbursementExportService.exportSepa(companyRepo.find(company.getId()), batchRepo.find(batch.getId()).getStartDate(), reimbursementToExport, batchRepo.find(batch.getId()).getAccountingBatch().getBankDetails());

      } catch (Exception e) {

      	TraceBackService.trace(new Exception(String.format(I18n.get(IExceptionMessage.BATCH_REIMBURSEMENT_1), batch.getId()), e), TraceBackRepository.REIMBURSEMENT, batch.getId());

      	incrementAnomaly();

      	log.error("Bug(Anomalie) généré(e)e dans l'export SEPA - Batch {}", batch.getId());

      }
      */

      try {

        cfonbExportService.exportCFONB(
            companyRepo.find(company.getId()),
            batchRepo.find(batch.getId()).getStartDate(),
            reimbursementToExport,
            batchRepo.find(batch.getId()).getAccountingBatch().getBankDetails());

      } catch (Exception e) {

        TraceBackService.trace(
            new Exception(
                String.format(I18n.get(IExceptionMessage.BATCH_REIMBURSEMENT_1), batch.getId()), e),
            ExceptionOriginRepository.REIMBURSEMENT,
            batch.getId());

        incrementAnomaly();

        log.error("Bug(Anomalie) généré(e)e dans l'export CFONB - Batch {}", batch.getId());
      }
    }
  }

  /**
   * As {@code batch} entity can be detached from the session, call {@code Batch.find()} get the
   * entity in the persistant context. Warning : {@code batch} entity have to be saved before.
   */
  @Override
  protected void stop() {

    AccountingService.setUpdateCustomerAccount(true);

    String comment = "";
    batch = batchRepo.find(batch.getId());
    switch (batch.getAccountingBatch().getReimbursementExportTypeSelect()) {
      case AccountingBatchRepository.REIMBURSEMENT_EXPORT_TYPE_GENERATE:
        comment = I18n.get(IExceptionMessage.BATCH_REIMBURSEMENT_2) + "\n";
        comment +=
            String.format(
                "\t* %s " + I18n.get(IExceptionMessage.BATCH_REIMBURSEMENT_3) + "\n",
                batch.getDone());
        comment +=
            String.format(
                "\t* " + I18n.get(IExceptionMessage.BATCH_REIMBURSEMENT_10) + " : %s \n",
                this.totalAmount);

        break;

      case AccountingBatchRepository.REIMBURSEMNT_EXPORT_TYPE_EXPORT:
        comment = I18n.get(IExceptionMessage.BATCH_REIMBURSEMENT_4) + "\n";
        comment +=
            String.format(
                "\t* %s " + I18n.get(IExceptionMessage.BATCH_REIMBURSEMENT_5) + "\n",
                batch.getDone());
        comment +=
            String.format(
                "\t* " + I18n.get(IExceptionMessage.BATCH_REIMBURSEMENT_10) + " : %s \n",
                this.totalAmount);

        comment += String.format("\t* ------------------------------- \n");
        comment += String.format("\t* %s ", updateCustomerAccountLog);

        break;

      default:
        break;
    }

    comment +=
        String.format(
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ALARM_ENGINE_BATCH_5),
            batch.getAnomaly());

    super.stop();
    addComment(comment);
  }
}
