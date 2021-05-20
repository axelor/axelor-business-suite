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

import com.axelor.apps.account.db.AccountingBatch;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Reimbursement;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.ReimbursementExportService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.List;
import javax.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchCreditTransferPartnerReimbursement extends BatchStrategy {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected PartnerRepository partnerRepo;
  protected PartnerService partnerService;
  protected ReimbursementExportService partnerReimbursementExportService;

  @Inject
  public BatchCreditTransferPartnerReimbursement(
      PartnerRepository partnerRepo,
      PartnerService partnerService,
      ReimbursementExportService reimbursementExportService) {
    this.partnerRepo = partnerRepo;
    this.partnerService = partnerService;
    partnerReimbursementExportService = reimbursementExportService;
  }

  @Override
  protected void process() {
    AccountingBatch accountingBatch = batch.getAccountingBatch();

    // Fetch all partners that have a credit balance for the specified company.
    TypedQuery<Partner> partnerQuery =
        JPA.em()
            .createQuery(
                "SELECT self FROM Partner self JOIN self.accountingSituationList accountingSituation "
                    + "WHERE accountingSituation.company = :company AND accountingSituation.balanceCustAccount < 0",
                Partner.class);
    partnerQuery.setParameter("company", accountingBatch.getCompany());
    List<Partner> partnerList = partnerQuery.getResultList();

    for (Partner partner : partnerList) {
      try {
        partner = partnerRepo.find(partner.getId());
        Reimbursement reimbursement = createReimbursement(partner, accountingBatch.getCompany());
        if (reimbursement != null) {
          incrementDone();
        }
      } catch (Exception ex) {
        incrementAnomaly();
        TraceBackService.trace(ex, ExceptionOriginRepository.CREDIT_TRANSFER, batch.getId());
        ex.printStackTrace();
        log.error(
            String.format(
                "Credit transfer batch for partner credit balance reimbursement: anomaly for partner %s",
                partner.getName()));
      }
      JPA.clear();
    }
  }

  @Override
  protected void stop() {
    StringBuilder sb = new StringBuilder();
    sb.append(I18n.get(IExceptionMessage.BATCH_CREDIT_TRANSFER_REPORT_TITLE)).append(" ");
    sb.append(
        String.format(
            I18n.get(
                    IExceptionMessage.BATCH_CREDIT_TRANSFER_REIMBURSEMENT_DONE_SINGULAR,
                    IExceptionMessage.BATCH_CREDIT_TRANSFER_REIMBURSEMENT_DONE_PLURAL,
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

  /**
   * Create a reimbursement for the specified partner and from the specified company.
   *
   * @param partner
   * @param company
   * @return
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  protected Reimbursement createReimbursement(Partner partner, Company company)
      throws AxelorException {
    List<MoveLine> moveLineList =
        moveLineRepo
            .all()
            .filter(
                "self.account.reconcileOk = true AND (self.move.statusSelect = ?1 OR self.move.statusSelect = ?2) "
                    + "AND self.amountRemaining != 0 AND self.credit > 0 "
                    + "AND self.move.partner = ?3 AND self.move.company = ?4 "
                    + "AND self.reimbursementStatusSelect = ?5",
                MoveRepository.STATUS_VALIDATED,
                MoveRepository.STATUS_ACCOUNTED,
                partner,
                company,
                MoveLineRepository.REIMBURSEMENT_STATUS_NULL)
            .fetch();

    Reimbursement reimbursement =
        partnerReimbursementExportService.runCreateReimbursement(moveLineList, company, partner);
    return reimbursement;
  }
}
