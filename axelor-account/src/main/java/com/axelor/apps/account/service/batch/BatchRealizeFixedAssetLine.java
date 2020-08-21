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

import com.axelor.apps.account.db.FixedAssetLine;
import com.axelor.apps.account.db.repo.FixedAssetLineRepository;
import com.axelor.apps.account.db.repo.FixedAssetRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.FixedAssetLineService;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.time.LocalDate;
import java.util.List;

public class BatchRealizeFixedAssetLine extends AbstractBatch {

  protected FixedAssetLineService fixedAssetLineService;
  protected FixedAssetLineRepository fixedAssetLineRepo;
  protected boolean stop = false;

  @Inject
  public BatchRealizeFixedAssetLine(
      FixedAssetLineService fixedAssetLineService, FixedAssetLineRepository fixedAssetLineRepo) {
    this.fixedAssetLineService = fixedAssetLineService;
    this.fixedAssetLineRepo = fixedAssetLineRepo;
  }

  @Override
  protected void start() throws IllegalAccessException {
    super.start();
    LocalDate startDate = batch.getAccountingBatch().getStartDate();
    LocalDate endDate = batch.getAccountingBatch().getEndDate();

    if (startDate != null && endDate != null && endDate.isAfter(startDate)) {
      TraceBackService.trace(
          new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.BATCH_FIXED_ASSET_LINE_DATE_ERROR)));
      stop = true;
    }
  }

  @Override
  protected void process() {
    if (!stop) {
      String query = "self.statusSelect = :statusSelect";
      LocalDate startDate = batch.getAccountingBatch().getStartDate();
      LocalDate endDate = batch.getAccountingBatch().getEndDate();
      query +=
          (endDate == null ? "" : " AND self.depreciationDate < :endDate")
              + (startDate == null ? "" : " AND self.depreciationDate > :startDate")
              + " AND self.fixedAsset.company = :company";

      List<FixedAssetLine> fixedAssetLineList =
          Beans.get(FixedAssetLineRepository.class)
              .all()
              .filter(query)
              .bind("statusSelect", FixedAssetLineRepository.STATUS_PLANNED)
              .bind("startDate", startDate)
              .bind("endDate", endDate)
              .bind("dateNow", LocalDate.now())
              .bind("company", batch.getAccountingBatch().getCompany())
              .fetch();
      int moveStatus =
          batch.getAccountingBatch().getGenerateMoveAsDraftStatus()
              ? MoveRepository.STATUS_NEW
              : MoveRepository.STATUS_DAYBOOK;
      for (FixedAssetLine fixedAssetLine : fixedAssetLineList) {
        try {
          fixedAssetLine = fixedAssetLineRepo.find(fixedAssetLine.getId());
          if (fixedAssetLine.getFixedAsset().getStatusSelect()
              > FixedAssetRepository.STATUS_DRAFT) {
            fixedAssetLineService.realize(fixedAssetLine, moveStatus);
            incrementDone();
          }
        } catch (Exception e) {
          incrementAnomaly();
          TraceBackService.trace(e);
        }
        JPA.clear();
      }
    }
  }

  @Override
  protected void stop() {

    String comment =
        String.format(
            "\t* %s " + I18n.get(IExceptionMessage.BATCH_REALIZED_FIXED_ASSET_LINE) + "\n",
            batch.getDone());

    comment +=
        String.format(
            "\t" + I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ALARM_ENGINE_BATCH_4),
            batch.getAnomaly());
    addComment(comment);
    super.stop();
  }
}
