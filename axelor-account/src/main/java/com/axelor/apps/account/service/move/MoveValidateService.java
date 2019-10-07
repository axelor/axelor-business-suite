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

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.AccountRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.PeriodRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoveValidateService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AccountConfigService accountConfigService;
  protected MoveSequenceService moveSequenceService;
  protected MoveCustAccountService moveCustAccountService;
  protected MoveRepository moveRepository;
  protected AccountRepository accountRepository;
  protected PartnerRepository partnerRepository;

  @Inject
  public MoveValidateService(
      AccountConfigService accountConfigService,
      MoveSequenceService moveSequenceService,
      MoveCustAccountService moveCustAccountService,
      MoveRepository moveRepository,
      AccountRepository accountRepository,
      PartnerRepository partnerRepository) {

    this.accountConfigService = accountConfigService;
    this.moveSequenceService = moveSequenceService;
    this.moveCustAccountService = moveCustAccountService;
    this.moveRepository = moveRepository;
    this.accountRepository = accountRepository;
    this.partnerRepository = partnerRepository;
  }

  /**
   * In move lines, fill the dates field and the partner if they are missing, and fill the counter.
   *
   * @param move
   */
  public void completeMoveLines(Move move) {
    LocalDate date = move.getDate();
    Partner partner = move.getPartner();

    int counter = 1;
    for (MoveLine moveLine : move.getMoveLineList()) {
      if (moveLine.getDate() == null) {
        moveLine.setDate(date);
      }

      if (moveLine.getAccount() != null
          && moveLine.getAccount().getUseForPartnerBalance()
          && moveLine.getDueDate() == null) {
        moveLine.setDueDate(date);
      }

      if (moveLine.getOriginDate() == null) {
        moveLine.setOriginDate(date);
      }

      if (partner != null) {
        moveLine.setPartner(partner);
      }
      moveLine.setCounter(counter);
      counter++;
    }
  }

  public void checkPreconditions(Move move) throws AxelorException {

    Journal journal = move.getJournal();
    Company company = move.getCompany();

    if (company == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, I18n.get(IExceptionMessage.MOVE_3));
    }

    if (journal == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, I18n.get(IExceptionMessage.MOVE_2));
    }

    if (move.getPeriod() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, I18n.get(IExceptionMessage.MOVE_4));
    }

    if (move.getMoveLineList() == null || move.getMoveLineList().isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY, I18n.get(IExceptionMessage.MOVE_8));
    }

    if (move.getMoveLineList()
        .stream()
        .allMatch(
            moveLine ->
                moveLine.getDebit().add(moveLine.getCredit()).compareTo(BigDecimal.ZERO) == 0)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY, I18n.get(IExceptionMessage.MOVE_8));
    }

    MoveLineService moveLineService = Beans.get(MoveLineService.class);

    for (MoveLine moveLine : move.getMoveLineList()) {
      Account account = moveLine.getAccount();
      if (account.getIsTaxAuthorizedOnMoveLine()
          && account.getIsTaxRequiredOnMoveLine()
          && moveLine.getTaxLine() == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.MOVE_9),
            account.getName());
      }

      if (moveLine.getAnalyticDistributionTemplate() == null
          && ObjectUtils.isEmpty(moveLine.getAnalyticMoveLineList())
          && account.getAnalyticDistributionAuthorized()
          && account.getAnalyticDistributionRequiredOnMoveLines()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.MOVE_10),
            account.getName());
      }

      if (account != null
          && !account.getAnalyticDistributionAuthorized()
          && (moveLine.getAnalyticDistributionTemplate() != null
              || (moveLine.getAnalyticMoveLineList() != null
                  && !moveLine.getAnalyticMoveLineList().isEmpty()))) {
        throw new AxelorException(
            move,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.VENTILATE_STATE_7));
      }

      moveLineService.validateMoveLine(moveLine);
    }

    this.validateWellBalancedMove(move);
  }

  /**
   * Valider une écriture comptable.
   *
   * @param move
   * @throws AxelorException
   */
  public void validate(Move move) throws AxelorException {

    this.validate(move, true);
  }

  /**
   * Valider une écriture comptable.
   *
   * @param move
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void validate(Move move, boolean updateCustomerAccount) throws AxelorException {

    log.debug("Validation de l'écriture comptable {}", move.getReference());

    this.checkPreconditions(move);

    Boolean dayBookMode =
        accountConfigService.getAccountConfig(move.getCompany()).getAccountingDaybook();

    if (!dayBookMode || move.getStatusSelect() == MoveRepository.STATUS_DAYBOOK) {
      moveSequenceService.setSequence(move);
    }

    if (move.getPeriod().getStatusSelect() == PeriodRepository.STATUS_ADJUSTING) {
      move.setAdjustingMove(true);
    }

    this.completeMoveLines(move);

    this.freezeAccountAndPartnerFieldsOnMoveLines(move);

    this.updateValidateStatus(move, dayBookMode);

    moveRepository.save(move);

    if (updateCustomerAccount) {
      moveCustAccountService.updateCustomerAccount(move);
    }
  }

  /**
   * Procédure permettant de vérifier qu'une écriture est équilibré, et la validé si c'est le cas
   *
   * @param move Une écriture
   * @throws AxelorException
   */
  public void validateWellBalancedMove(Move move) throws AxelorException {

    log.debug("Well-balanced validation on account move {}", move.getReference());

    if (move.getMoveLineList() != null) {

      BigDecimal totalDebit = BigDecimal.ZERO;
      BigDecimal totalCredit = BigDecimal.ZERO;

      for (MoveLine moveLine : move.getMoveLineList()) {

        if (moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0
            && moveLine.getCredit().compareTo(BigDecimal.ZERO) > 0) {
          throw new AxelorException(
              move,
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(IExceptionMessage.MOVE_6),
              moveLine.getName());
        }

        totalDebit = totalDebit.add(moveLine.getDebit());
        totalCredit = totalCredit.add(moveLine.getCredit());
      }

      if (totalDebit.compareTo(totalCredit) != 0) {
        throw new AxelorException(
            move,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.MOVE_7),
            move.getReference(),
            totalDebit,
            totalCredit);
      }
    }
  }

  public void updateValidateStatus(Move move, boolean daybook) throws AxelorException {

    if (daybook && move.getStatusSelect() == MoveRepository.STATUS_NEW) {
      move.setStatusSelect(MoveRepository.STATUS_DAYBOOK);
    } else {
      move.setStatusSelect(MoveRepository.STATUS_VALIDATED);
      move.setValidationDate(LocalDate.now());
    }
  }

  @Transactional(rollbackOn = {AxelorException.class, RuntimeException.class})
  public void updateInDayBookMode(Move move) throws AxelorException {

    this.checkPreconditions(move);

    Set<Partner> partnerSet = new HashSet<>();

    partnerSet.addAll(this.getPartnerOfMoveBeforeUpdate(move));
    partnerSet.addAll(moveCustAccountService.getPartnerOfMove(move));

    List<Partner> partnerList = new ArrayList<>();
    partnerList.addAll(partnerSet);

    this.freezeAccountAndPartnerFieldsOnMoveLines(move);
    moveRepository.save(move);

    moveCustAccountService.updateCustomerAccount(partnerList, move.getCompany());
  }

  /**
   * Get the distinct partners of an account move that impact the partner balances
   *
   * @param move
   * @return A list of partner
   */
  public List<Partner> getPartnerOfMoveBeforeUpdate(Move move) {
    List<Partner> partnerList = new ArrayList<Partner>();
    for (MoveLine moveLine : move.getMoveLineList()) {
      if (moveLine.getAccountId() != null) {
        Account account = accountRepository.find(moveLine.getAccountId());
        if (account != null
            && account.getUseForPartnerBalance()
            && moveLine.getPartnerId() != null) {
          Partner partner = partnerRepository.find(moveLine.getPartnerId());
          if (partner != null && !partnerList.contains(partner)) {
            partnerList.add(partner);
          }
        }
      }
    }
    return partnerList;
  }

  /**
   * Method that freeze the account and partner fields on move lines
   *
   * @param move
   */
  public void freezeAccountAndPartnerFieldsOnMoveLines(Move move) {
    for (MoveLine moveLine : move.getMoveLineList()) {

      Account account = moveLine.getAccount();

      moveLine.setAccountId(account.getId());
      moveLine.setAccountCode(account.getCode());
      moveLine.setAccountName(account.getName());

      Partner partner = moveLine.getPartner();

      if (partner != null) {
        moveLine.setPartnerId(partner.getId());
        moveLine.setPartnerFullName(partner.getFullName());
        moveLine.setPartnerSeq(partner.getPartnerSeq());
      }
      if (moveLine.getTaxLine() != null) {
        moveLine.setTaxRate(moveLine.getTaxLine().getValue());
        moveLine.setTaxCode(moveLine.getTaxLine().getTax().getCode());
      }
    }
  }

  public boolean validateMultiple(List<? extends Move> moveList) {
    boolean error = false;
    if (moveList == null) {
      return error;
    }
    for (Move move : moveList) {
      try {
        validate(moveRepository.find(move.getId()));
      } catch (Exception e) {
        TraceBackService.trace(e);
        error = true;
      } finally {
        JPA.clear();
      }
    }
    return error;
  }

  private String getPartnerFullName(Partner partner) {
    if (!Strings.isNullOrEmpty(partner.getName())
        && !Strings.isNullOrEmpty(partner.getFirstName())) {
      return partner.getName() + " " + partner.getFirstName();
    } else if (!Strings.isNullOrEmpty(partner.getName())) {
      return partner.getName();
    } else if (!Strings.isNullOrEmpty(partner.getFirstName())) {
      return partner.getFirstName();
    } else {
      return "" + partner.getId();
    }
  }
}
