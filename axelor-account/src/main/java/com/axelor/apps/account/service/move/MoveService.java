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
package com.axelor.apps.account.service.move;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.exception.AxelorException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MoveService {

  MoveLineService getMoveLineService();

  MoveCreateService getMoveCreateService();

  MoveValidateService getMoveValidateService();

  MoveRemoveService getMoveRemoveService();

  MoveToolService getMoveToolService();

  ReconcileService getReconcileService();

  /**
   * Créer une écriture comptable propre à la facture.
   *
   * @param invoice
   * @param consolidate
   * @return
   * @throws AxelorException
   */
  Move createMove(Invoice invoice) throws AxelorException;

  /**
   * Méthode permettant d'employer les trop-perçus 2 cas : - le compte des trop-perçus est le même
   * que celui de la facture : alors on lettre directement - le compte n'est pas le même : on créée
   * une O.D. de passage sur le bon compte
   *
   * @param invoice
   * @return
   * @throws AxelorException
   */
  Move createMoveUseExcessPaymentOrDue(Invoice invoice) throws AxelorException;

  /**
   * Méthode permettant d'employer les dûs sur l'avoir On récupère prioritairement les dûs
   * (factures) selectionné sur l'avoir, puis les autres dûs du tiers
   *
   * <p>2 cas : - le compte des dûs est le même que celui de l'avoir : alors on lettre directement -
   * le compte n'est pas le même : on créée une O.D. de passage sur le bon compte
   *
   * @param invoice
   * @return
   * @throws AxelorException
   */
  Move createMoveUseInvoiceDue(Invoice invoice) throws AxelorException;

  void createMoveUseExcessPayment(Invoice invoice) throws AxelorException;

  Move createMoveUseDebit(
      Invoice invoice, List<MoveLine> debitMoveLines, MoveLine invoiceCustomerMoveLine)
      throws AxelorException;

  Move generateReverse(
      Move move,
      boolean isAutomaticReconcile,
      boolean isAutomaticAccounting,
      boolean isUnreconcileOriginalMove,
      LocalDate dateOfReversion)
      throws AxelorException;

  MoveLine findMoveLineByAccount(Move move, Account account) throws AxelorException;

  public Map<String, Object> computeTotals(Move move);

  public String filterPartner(Move move);

  Move generateReverse(Move move, Map<String, Object> assistantMap) throws AxelorException;

  Move updateMoveLinesDateExcludeFromPeriodOnlyWithoutSave(Move move) throws AxelorException;

  MoveLine createCounterpartMoveLine(Move move);
}
