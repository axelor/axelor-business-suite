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
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;

@Singleton
public class MoveAdjustementService {

  protected MoveLineService moveLineService;
  protected MoveCreateService moveCreateService;
  protected MoveValidateService moveValidateService;
  protected MoveRepository moveRepository;
  protected AccountConfigService accountConfigService;
  protected AppAccountService appAccountService;

  @Inject
  public MoveAdjustementService(
      AppAccountService appAccountService,
      MoveLineService moveLineService,
      MoveCreateService moveCreateService,
      MoveValidateService moveValidateService,
      AccountConfigService accountConfigService,
      MoveRepository moveRepository) {

    this.moveLineService = moveLineService;
    this.moveCreateService = moveCreateService;
    this.moveValidateService = moveValidateService;
    this.moveRepository = moveRepository;
    this.accountConfigService = accountConfigService;
    this.appAccountService = appAccountService;
  }

  /**
   * Creating move of passage in gap regulation (on debit)
   *
   * @param debitMoveLine
   * @return
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void createAdjustmentDebitMove(MoveLine debitMoveLine) throws AxelorException {

    Partner partner = debitMoveLine.getPartner();
    Account account = debitMoveLine.getAccount();
    Move debitMove = debitMoveLine.getMove();
    Company company = debitMove.getCompany();
    AccountConfig accountConfig = accountConfigService.getAccountConfig(company);

    BigDecimal debitAmountRemaining = debitMoveLine.getAmountRemaining();

    Journal miscOperationJournal = accountConfigService.getAutoMiscOpeJournal(accountConfig);

    Move adjustmentMove =
        moveCreateService.createMove(
            miscOperationJournal,
            company,
            null,
            partner,
            null,
            MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC);

    // Création de la ligne au crédit
    MoveLine creditAdjustmentMoveLine =
        moveLineService.createMoveLine(
            adjustmentMove,
            partner,
            account,
            debitAmountRemaining,
            false,
            appAccountService.getTodayDate(),
            1,
            null,
            null);

    // Création de la ligne au debit
    MoveLine debitAdjustmentMoveLine =
        moveLineService.createMoveLine(
            adjustmentMove,
            partner,
            accountConfigService.getCashPositionVariationAccount(accountConfig),
            debitAmountRemaining,
            true,
            appAccountService.getTodayDate(),
            2,
            null,
            null);

    adjustmentMove.addMoveLineListItem(creditAdjustmentMoveLine);
    adjustmentMove.addMoveLineListItem(debitAdjustmentMoveLine);

    moveValidateService.validate(adjustmentMove);
    moveRepository.save(adjustmentMove);
  }

  /**
   * Creating move of passage in gap regulation (on credit)
   *
   * @param debitMoveLine
   * @return
   * @throws AxelorException
   */
  public MoveLine createAdjustmentCreditMove(MoveLine debitMoveLine) throws AxelorException {

    Partner partner = debitMoveLine.getPartner();
    Account account = debitMoveLine.getAccount();
    Move debitMove = debitMoveLine.getMove();
    Company company = debitMove.getCompany();
    BigDecimal debitAmountRemaining = debitMoveLine.getAmountRemaining();
    AccountConfig accountConfig = accountConfigService.getAccountConfig(company);

    Journal miscOperationJournal = accountConfigService.getAutoMiscOpeJournal(accountConfig);

    Move adjustmentMove =
        moveCreateService.createMove(
            miscOperationJournal,
            company,
            null,
            partner,
            null,
            MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC);

    // Création de la ligne au crédit
    MoveLine creditAdjustmentMoveLine =
        moveLineService.createMoveLine(
            adjustmentMove,
            partner,
            account,
            debitAmountRemaining,
            false,
            appAccountService.getTodayDate(),
            1,
            null,
            null);

    // Création de la ligne au débit
    MoveLine debitAdjustmentMoveLine =
        moveLineService.createMoveLine(
            adjustmentMove,
            partner,
            accountConfigService.getCashPositionVariationAccount(accountConfig),
            debitAmountRemaining,
            true,
            appAccountService.getTodayDate(),
            2,
            null,
            null);

    adjustmentMove.addMoveLineListItem(creditAdjustmentMoveLine);
    adjustmentMove.addMoveLineListItem(debitAdjustmentMoveLine);
    moveValidateService.validate(adjustmentMove);
    moveRepository.save(adjustmentMove);

    return creditAdjustmentMoveLine;
  }

  /**
   * Méthode permettant de créer une écriture du passage du compte de l'écriture au debit vers le
   * compte de l'écriture au credit.
   *
   * @param debitMoveLineToReconcile Ecriture au débit
   * @param creditMoveLineToReconcile Ecriture au crédit
   * @param amount Montant
   * @return L'écriture de passage du compte de l'écriture au debit vers le compte de l'écriture au
   *     credit.
   * @throws AxelorException
   */
  public Move createMoveToPassOnTheOtherAccount(
      MoveLine debitMoveLineToReconcile, MoveLine creditMoveLineToReconcile, BigDecimal amount)
      throws AxelorException {

    Partner partnerDebit = debitMoveLineToReconcile.getPartner();
    Partner partnerCredit = creditMoveLineToReconcile.getPartner();

    Company company = debitMoveLineToReconcile.getMove().getCompany();
    AccountConfig accountConfig = accountConfigService.getAccountConfig(company);

    Journal journal = accountConfigService.getAutoMiscOpeJournal(accountConfig);

    // Move
    Move move =
        moveCreateService.createMove(
            journal, company, null, partnerDebit, null, MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC);

    MoveLine debitMoveLine =
        moveLineService.createMoveLine(
            move,
            partnerCredit,
            creditMoveLineToReconcile.getAccount(),
            amount,
            true,
            appAccountService.getTodayDate(),
            1,
            null,
            null);

    MoveLine creditMoveLine =
        moveLineService.createMoveLine(
            move,
            partnerDebit,
            debitMoveLineToReconcile.getAccount(),
            amount,
            false,
            appAccountService.getTodayDate(),
            2,
            null,
            null);

    move.addMoveLineListItem(debitMoveLine);
    move.addMoveLineListItem(creditMoveLine);

    moveValidateService.validate(move);

    return move;
  }
}
