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
package com.axelor.apps.account.service.move;

import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountCustomerService;
import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.tool.service.ArchivingToolService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.List;
import java.util.Map;

public class MoveRemoveService {

  protected MoveRepository moveRepo;

  protected MoveLineRepository moveLineRepo;

  protected ArchivingToolService archivingToolService;

  protected ReconcileService reconcileService;

  protected AccountingSituationService accountingSituationService;

  protected AccountCustomerService accountCustomerService;

  @Inject
  public MoveRemoveService(
      MoveRepository moveRepo,
      MoveLineRepository moveLineRepo,
      ArchivingToolService archivingToolService,
      ReconcileService reconcileService,
      AccountingSituationService accountingSituationService,
      AccountCustomerService accountCustomerService) {
    this.moveRepo = moveRepo;
    this.moveLineRepo = moveLineRepo;
    this.archivingToolService = archivingToolService;
    this.reconcileService = reconcileService;
    this.accountingSituationService = accountingSituationService;
    this.accountCustomerService = accountCustomerService;
  }

  public void archiveDaybookMove(Move move) throws Exception {
    if (move.getStatusSelect().equals(MoveRepository.STATUS_DAYBOOK)) {
      this.checkDaybookMove(move);
      this.cleanMoveToArchived(move);
      move = this.updateMoveToArchived(move);
      this.archiveMove(move);
      this.updateSystem(move);
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected Move updateMoveToArchived(Move move) {
    move.setStatusSelect(MoveRepository.STATUS_CANCELED);
    return move;
  }

  protected void cleanMoveToArchived(Move move) throws Exception {
    for (MoveLine moveLine : move.getMoveLineList()) {
      for (Reconcile reconcile : moveLine.getDebitReconcileList()) {
        reconcileService.unreconcile(reconcile);
      }
      for (Reconcile reconcile : moveLine.getCreditReconcileList()) {
        reconcileService.unreconcile(reconcile);
      }
    }
  }

  protected void updateSystem(Move move) throws Exception {
    for (MoveLine moveLine : move.getMoveLineList()) {
      if (moveLine.getPartner() != null) {
        accountCustomerService.updateAccountingSituationCustomerAccount(
            accountingSituationService.getAccountingSituation(
                moveLine.getPartner(), move.getCompany()),
            true,
            true,
            true);
        accountingSituationService.updateCustomerCredit(moveLine.getPartner());
      }
    }
  }

  protected void checkDaybookMove(Move move) throws Exception {
    String errorMessage = "";
    Map<String, String> objectsLinkToMoveMap =
        archivingToolService.getObjectLinkTo(move, move.getId());
    for (Map.Entry<String, String> entry : objectsLinkToMoveMap.entrySet()) {
      String modelName = entry.getKey();
      if (!"MoveLine".equals(modelName)) {
        errorMessage +=
            String.format(
                I18n.get(IExceptionMessage.MOVE_ARCHIVE_NOT_OK_BECAUSE_OF_LINK_WITH),
                move.getReference(),
                modelName);
      }
    }
    for (MoveLine moveLine : move.getMoveLineList()) {
      Map<String, String> objectsLinkToMoveLineMap =
          archivingToolService.getObjectLinkTo(moveLine, moveLine.getId());
      for (Map.Entry<String, String> entry : objectsLinkToMoveLineMap.entrySet()) {
        String modelName = entry.getKey();
        if (!modelName.equals("Move") && !modelName.equals("Reconcile")) {
          errorMessage +=
              String.format(
                  I18n.get(IExceptionMessage.MOVE_LINE_ARCHIVE_NOT_OK_BECAUSE_OF_LINK_WITH),
                  moveLine.getName(),
                  modelName);
        }
      }
    }
    if (errorMessage != null && !errorMessage.isEmpty()) {
      throw new AxelorException(TraceBackRepository.CATEGORY_INCONSISTENCY, errorMessage);
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public Move archiveMove(Move move) {
    move.setArchived(true);
    for (MoveLine moveLine : move.getMoveLineList()) {
      moveLine.setArchived(true);
    }
    return move;
  }

  public boolean deleteMultiple(List<? extends Move> moveList) {
    boolean error = false;
    if (moveList == null) {
      return error;
    }
    for (Move move : moveList) {
      try {
        move = moveRepo.find(move.getId());
        if (move.getStatusSelect().equals(MoveRepository.STATUS_NEW)) {
          this.deleteMove(move);
        } else if (move.getStatusSelect().equals(MoveRepository.STATUS_DAYBOOK)) {
          this.archiveDaybookMove(move);
        } else if (move.getStatusSelect().equals(MoveRepository.STATUS_CANCELED)) {
          this.archiveMove(move);
        }
      } catch (Exception e) {
        TraceBackService.trace(e);
        error = true;
      } finally {
        JPA.clear();
      }
    }
    return error;
  }

  @Transactional(rollbackOn = {Exception.class})
  public void deleteMove(Move move) throws Exception {
    moveRepo.remove(move);
  }
}
