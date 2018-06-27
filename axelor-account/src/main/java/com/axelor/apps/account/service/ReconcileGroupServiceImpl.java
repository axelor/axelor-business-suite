/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
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
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.ReconcileGroup;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.ReconcileGroupRepository;
import com.axelor.apps.account.db.repo.ReconcileRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.base.db.Company;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class ReconcileGroupServiceImpl implements ReconcileGroupService {

  @Override
  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void validate(ReconcileGroup reconcileGroup, List<Reconcile> reconcileList)
      throws AxelorException {
    if (CollectionUtils.isEmpty(reconcileList)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.RECONCILE_GROUP_VALIDATION_NO_LINES),
          reconcileGroup);
    }

    reconcileGroup.setStatusSelect(ReconcileGroupRepository.STATUS_FINAL);

    Beans.get(ReconcileGroupSequenceService.class).fillCodeFromSequence(reconcileGroup);
  }

  @Override
  public boolean isBalanced(List<Reconcile> reconcileList) {
    List<MoveLine> debitMoveLineList =
        reconcileList
            .stream()
            .map(Reconcile::getDebitMoveLine)
            .distinct()
            .collect(Collectors.toList());
    List<MoveLine> creditMoveLineList =
        reconcileList
            .stream()
            .map(Reconcile::getCreditMoveLine)
            .distinct()
            .collect(Collectors.toList());
    List<Account> accountList =
        debitMoveLineList
            .stream()
            .map(MoveLine::getAccount)
            .distinct()
            .collect(Collectors.toList());
    accountList.addAll(
        creditMoveLineList
            .stream()
            .map(MoveLine::getAccount)
            .distinct()
            .collect(Collectors.toList()));

    for (Account account : accountList) {
      BigDecimal totalDebit =
          debitMoveLineList
              .stream()
              .filter(moveLine -> moveLine.getAccount().equals(account))
              .map(MoveLine::getDebit)
              .reduce(BigDecimal::add)
              .orElse(BigDecimal.ZERO);
      BigDecimal totalCredit =
          creditMoveLineList
              .stream()
              .filter(moveLine -> moveLine.getAccount().equals(account))
              .map(MoveLine::getCredit)
              .reduce(BigDecimal::add)
              .orElse(BigDecimal.ZERO);
      if (totalDebit.compareTo(totalCredit) != 0) {
        return false;
      }
    }
    return true;
  }

  @Override
  public ReconcileGroup findOrCreateGroup(Reconcile reconcile) {
    return findOrMergeGroup(reconcile)
        .orElseGet(() -> createReconcileGroup(reconcile.getCompany()));
  }

  @Override
  public Optional<ReconcileGroup> findOrMergeGroup(Reconcile reconcile) {
    List<ReconcileGroup> otherReconcileGroupList;
    List<Reconcile> allReconcileList = new ArrayList<>();
    List<Reconcile> debitReconcileList = reconcile.getDebitMoveLine().getDebitReconcileList();
    if (debitReconcileList != null) {
      allReconcileList.addAll(debitReconcileList);
    }
    List<Reconcile> creditReconcileList = reconcile.getCreditMoveLine().getCreditReconcileList();
    if (creditReconcileList != null) {
      allReconcileList.addAll(creditReconcileList);
    }
    otherReconcileGroupList =
        allReconcileList
            .stream()
            .filter(reconcile1 -> !reconcile.equals(reconcile1))
            .map(Reconcile::getReconcileGroup)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    if (otherReconcileGroupList.isEmpty()) {
      return Optional.empty();
    } else if (otherReconcileGroupList.size() == 1) {
      return Optional.of(otherReconcileGroupList.get(0));
    } else {
      return Optional.of(mergeReconcileGroups(otherReconcileGroupList));
    }
  }

  @Override
  @Transactional
  public ReconcileGroup mergeReconcileGroups(List<ReconcileGroup> reconcileGroupList) {
    ReconcileGroupRepository reconcileGroupRepository = Beans.get(ReconcileGroupRepository.class);
    Company company = reconcileGroupList.get(0).getCompany();
    ReconcileGroup reconcileGroup = createReconcileGroup(company);

    List<Reconcile> reconcileList =
        Beans.get(ReconcileRepository.class)
            .all()
            .filter("self.reconcileGroup.id IN (:reconcileGroupIds)")
            .bind(
                "reconcileGroupIds",
                reconcileGroupList.stream().map(ReconcileGroup::getId).collect(Collectors.toList()))
            .fetch();
    reconcileList.forEach(reconcile -> addToReconcileGroup(reconcileGroup, reconcile));

    for (ReconcileGroup toDeleteReconcileGroup : reconcileGroupList) {
      reconcileGroupRepository.remove(toDeleteReconcileGroup);
    }

    return reconcileGroupRepository.save(reconcileGroup);
  }

  @Override
  @Transactional
  public ReconcileGroup createReconcileGroup(Company company) {
    ReconcileGroup reconcileGroup = new ReconcileGroup();
    reconcileGroup.setCompany(company);
    return Beans.get(ReconcileGroupRepository.class).save(reconcileGroup);
  }

  @Override
  public void addAndValidate(ReconcileGroup reconcileGroup, Reconcile reconcile)
      throws AxelorException {
    List<Reconcile> reconcileList =
        Beans.get(ReconcileRepository.class).findByReconcileGroup(reconcileGroup).fetch();
    reconcileList.add(reconcile);
    addToReconcileGroup(reconcileGroup, reconcile);
    if (isBalanced(reconcileList)) {
      validate(reconcileGroup, reconcileList);
    }
  }

  @Override
  public void addToReconcileGroup(ReconcileGroup reconcileGroup, Reconcile reconcile) {
    reconcile.setReconcileGroup(reconcileGroup);
    reconcile.getDebitMoveLine().setReconcileGroup(reconcileGroup);
    reconcile.getCreditMoveLine().setReconcileGroup(reconcileGroup);
  }

  @Override
  public void remove(Reconcile reconcile) throws AxelorException {
    MoveLineRepository moveLineRepository = Beans.get(MoveLineRepository.class);
    ReconcileRepository reconcileRepository = Beans.get(ReconcileRepository.class);
    ReconcileGroup reconcileGroup = reconcile.getReconcileGroup();
    reconcile.setReconcileGroup(null);

    // update move lines
    List<MoveLine> moveLineToRemoveList =
        moveLineRepository.findByReconcileGroup(reconcileGroup).fetch();
    moveLineToRemoveList.forEach(moveLine -> moveLine.setReconcileGroup(null));

    List<Reconcile> reconcileList =
        reconcileRepository.findByReconcileGroup(reconcileGroup).fetch();
    reconcileList
        .stream()
        .map(Reconcile::getDebitMoveLine)
        .forEach(moveLine -> moveLine.setReconcileGroup(reconcileGroup));
    reconcileList
        .stream()
        .map(Reconcile::getCreditMoveLine)
        .forEach(moveLine -> moveLine.setReconcileGroup(reconcileGroup));

    // update status
    updateStatus(reconcileGroup);
  }

  @Override
  public void updateStatus(ReconcileGroup reconcileGroup) throws AxelorException {
    List<Reconcile> reconcileList =
        Beans.get(ReconcileRepository.class).findByReconcileGroup(reconcileGroup).fetch();
    int status = reconcileGroup.getStatusSelect();
    if (CollectionUtils.isNotEmpty(reconcileList)
        && isBalanced(reconcileList)
        && status == ReconcileGroupRepository.STATUS_TEMPORARY) {
      validate(reconcileGroup, reconcileList);
    } else if (status == ReconcileGroupRepository.STATUS_FINAL) {
      // it is not balanced or the collection is empty.
      if (CollectionUtils.isEmpty(reconcileList)) {
        Beans.get(ReconcileGroupRepository.class).remove(reconcileGroup);
      } else {
        reconcileGroup.setStatusSelect(ReconcileGroupRepository.STATUS_TEMPORARY);
        Beans.get(ReconcileGroupSequenceService.class).fillCodeFromSequence(reconcileGroup);
      }
    }
  }
}
