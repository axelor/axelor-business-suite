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
package com.axelor.apps.account.service.payment;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PayVoucherElementToPay;
import com.axelor.apps.account.db.PaymentScheduleLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentServiceImpl implements PaymentService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected ReconcileService reconcileService;
  protected MoveLineService moveLineService;

  protected AppAccountService appAccountService;

  @Inject
  public PaymentServiceImpl(
      AppAccountService appAccountService,
      ReconcileService reconcileService,
      MoveLineService moveLineService) {

    this.reconcileService = reconcileService;
    this.moveLineService = moveLineService;
    this.appAccountService = appAccountService;
  }

  /**
   * Use excess payment between a list of debit move lines and a list of credit move lines. The
   * lists needs to be ordered by date in order to pay the invoices chronologically.
   *
   * @param debitMoveLines = dûs
   * @param creditMoveLines = trop-perçu
   * @throws AxelorException
   */
  @Override
  public void useExcessPaymentOnMoveLines(
      List<MoveLine> debitMoveLines, List<MoveLine> creditMoveLines) throws AxelorException {
    useExcessPaymentOnMoveLines(debitMoveLines, creditMoveLines, false);
  }

  /**
   * Use excess payment between a list of debit move lines and a list of credit move lines. The
   * lists needs to be ordered by date in order to pay the invoices chronologically. This method
   * doesn't throw any exception if a reconciliation fails.
   *
   * @param debitMoveLines
   * @param creditMoveLines
   */
  @Override
  public void useExcessPaymentOnMoveLinesDontThrow(
      List<MoveLine> debitMoveLines, List<MoveLine> creditMoveLines) {
    try {
      useExcessPaymentOnMoveLines(debitMoveLines, creditMoveLines, true);
    } catch (Exception e) {
      TraceBackService.trace(e);
      log.debug(e.getMessage());
    }
  }

  /**
   * Use excess payment between a list of debit move lines and a list of credit move lines. The
   * lists needs to be ordered by date in order to pay the invoices chronologically.
   *
   * @param debitMoveLines
   * @param creditMoveLines
   * @param dontThrow
   * @throws AxelorException
   */
  protected void useExcessPaymentOnMoveLines(
      List<MoveLine> debitMoveLines, List<MoveLine> creditMoveLines, boolean dontThrow)
      throws AxelorException {

    if (debitMoveLines != null && creditMoveLines != null) {

      log.debug(
          "Emploie du trop perçu (nombre de lignes en débit : {}, nombre de ligne en crédit : {})",
          new Object[] {debitMoveLines.size(), creditMoveLines.size()});

      BigDecimal debitTotalRemaining = BigDecimal.ZERO;
      BigDecimal creditTotalRemaining = BigDecimal.ZERO;
      for (MoveLine creditMoveLine : creditMoveLines) {

        log.debug("Emploie du trop perçu : ligne en crédit : {})", creditMoveLine);

        log.debug(
            "Emploie du trop perçu : ligne en crédit (restant à payer): {})",
            creditMoveLine.getAmountRemaining());
        creditTotalRemaining = creditTotalRemaining.add(creditMoveLine.getAmountRemaining());
      }
      for (MoveLine debitMoveLine : debitMoveLines) {

        log.debug("Emploie du trop perçu : ligne en débit : {})", debitMoveLine);

        log.debug(
            "Emploie du trop perçu : ligne en débit (restant à payer): {})",
            debitMoveLine.getAmountRemaining());
        debitTotalRemaining = debitTotalRemaining.add(debitMoveLine.getAmountRemaining());
      }

      for (MoveLine creditMoveLine : creditMoveLines) {

        if (creditMoveLine.getAmountRemaining().compareTo(BigDecimal.ZERO) == 1) {

          for (MoveLine debitMoveLine : debitMoveLines) {
            if ((debitMoveLine.getAmountRemaining().compareTo(BigDecimal.ZERO) == 1)
                && (creditMoveLine.getAmountRemaining().compareTo(BigDecimal.ZERO) == 1)) {
              try {
                createReconcile(
                    debitMoveLine, creditMoveLine, debitTotalRemaining, creditTotalRemaining);
              } catch (Exception e) {
                if (dontThrow) {
                  TraceBackService.trace(e);
                  log.debug(e.getMessage());
                } else {
                  throw e;
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Private method called by useExcessPaymentOnMoveLines used to lighten it and to create a
   * reconcile
   *
   * @param debitMoveLine
   * @param creditMoveLine
   * @param debitTotalRemaining
   * @param creditTotalRemaining
   * @throws AxelorException
   */
  @Transactional
  protected void createReconcile(
      MoveLine debitMoveLine,
      MoveLine creditMoveLine,
      BigDecimal debitTotalRemaining,
      BigDecimal creditTotalRemaining)
      throws AxelorException {
    BigDecimal amount;
    Reconcile reconcile;
    if (debitMoveLine.getMaxAmountToReconcile() != null
        && debitMoveLine.getMaxAmountToReconcile().compareTo(BigDecimal.ZERO) > 0) {
      amount = debitMoveLine.getMaxAmountToReconcile().min(creditMoveLine.getAmountRemaining());
      debitMoveLine.setMaxAmountToReconcile(null);
    } else {
      amount = creditMoveLine.getAmountRemaining().min(debitMoveLine.getAmountRemaining());
    }
    log.debug("amount : {}", amount);
    log.debug("debitTotalRemaining : {}", debitTotalRemaining);
    log.debug("creditTotalRemaining : {}", creditTotalRemaining);
    BigDecimal nextDebitTotalRemaining = debitTotalRemaining.subtract(amount);
    BigDecimal nextCreditTotalRemaining = creditTotalRemaining.subtract(amount);
    // Gestion du passage en 580
    if (nextDebitTotalRemaining.compareTo(BigDecimal.ZERO) <= 0
        || nextCreditTotalRemaining.compareTo(BigDecimal.ZERO) <= 0) {
      log.debug("last loop");
      reconcile = reconcileService.createReconcile(debitMoveLine, creditMoveLine, amount, true);
    } else {
      reconcile = reconcileService.createReconcile(debitMoveLine, creditMoveLine, amount, false);
    }
    // End gestion du passage en 580

    if (reconcile != null) {
      reconcileService.confirmReconcile(reconcile, true);

      debitTotalRemaining = debitTotalRemaining.subtract(amount);
      creditTotalRemaining = creditTotalRemaining.subtract(amount);

      log.debug("Réconciliation : {}", reconcile);
    }
  }

  /**
   * Il crée des écritures de trop percu avec des montants exacts pour chaque débitMoveLines avec le
   * compte du débitMoveLines. A la fin, si il reste un trop-percu alors créer un trop-perçu
   * classique.
   *
   * @param debitMoveLines Les lignes d'écriture à payer
   * @param remainingPaidAmount Le montant restant à payer
   * @param move Une écriture
   * @param moveLineNo Un numéro de ligne d'écriture
   * @return
   * @throws AxelorException
   */
  @Override
  public int createExcessPaymentWithAmount(
      List<MoveLine> debitMoveLines,
      BigDecimal remainingPaidAmount,
      Move move,
      int moveLineNo,
      Partner partner,
      Company company,
      PayVoucherElementToPay payVoucherElementToPay,
      Account account,
      LocalDate paymentDate)
      throws AxelorException {
    log.debug("In createExcessPaymentWithAmount");
    int moveLineNo2 = moveLineNo;
    BigDecimal remainingPaidAmount2 = remainingPaidAmount;

    List<Reconcile> reconcileList = new ArrayList<Reconcile>();
    int i = debitMoveLines.size();
    for (MoveLine debitMoveLine : debitMoveLines) {
      i--;
      BigDecimal amountRemaining = debitMoveLine.getAmountRemaining();

      // Afin de pouvoir arrêter si il n'y a plus rien pour payer
      if (remainingPaidAmount2.compareTo(BigDecimal.ZERO) <= 0) {
        break;
      }
      BigDecimal amountToPay = remainingPaidAmount2.min(amountRemaining);

      String invoiceName = "";
      if (debitMoveLine.getMove().getInvoice() != null) {
        invoiceName = debitMoveLine.getMove().getInvoice().getInvoiceId();
      } else {
        invoiceName = payVoucherElementToPay.getPaymentVoucher().getRef();
      }

      MoveLine creditMoveLine =
          moveLineService.createMoveLine(
              move,
              debitMoveLine.getPartner(),
              debitMoveLine.getAccount(),
              amountToPay,
              false,
              appAccountService.getTodayDate(),
              moveLineNo2,
              invoiceName,
              null);
      move.getMoveLineList().add(creditMoveLine);

      // Utiliser uniquement dans le cas du paiemnt des échéances lors d'une saisie paiement
      if (payVoucherElementToPay != null) {
        creditMoveLine.setPaymentScheduleLine(
            payVoucherElementToPay.getMoveLine().getPaymentScheduleLine());

        payVoucherElementToPay.setMoveLineGenerated(creditMoveLine);
      }

      moveLineNo2++;
      Reconcile reconcile = null;

      // Gestion du passage en 580
      if (i == 0) {
        log.debug("last loop");
        reconcile =
            reconcileService.createReconcile(debitMoveLine, creditMoveLine, amountToPay, true);
      } else {
        reconcile =
            reconcileService.createReconcile(debitMoveLine, creditMoveLine, amountToPay, false);
      }
      // End gestion du passage en 580

      if (reconcile != null) {
        reconcileList.add(reconcile);
        remainingPaidAmount2 = remainingPaidAmount2.subtract(amountRemaining);
      }
    }

    for (Reconcile reconcile : reconcileList) {
      reconcileService.confirmReconcile(reconcile, true);
    }

    // Si il y a un restant à payer, alors on crée un trop-perçu.
    if (remainingPaidAmount2.compareTo(BigDecimal.ZERO) > 0) {

      MoveLine moveLine =
          moveLineService.createMoveLine(
              move,
              partner,
              account,
              remainingPaidAmount2,
              false,
              appAccountService.getTodayDate(),
              moveLineNo2,
              null,
              null);

      move.getMoveLineList().add(moveLine);
      moveLineNo2++;
      // Gestion du passage en 580
      reconcileService.balanceCredit(moveLine);
    }
    log.debug("End createExcessPaymentWithAmount");
    return moveLineNo2;
  }

  @SuppressWarnings("unchecked")
  @Override
  public int useExcessPaymentWithAmountConsolidated(
      List<MoveLine> creditMoveLines,
      BigDecimal remainingPaidAmount,
      Move move,
      int moveLineNo,
      Partner partner,
      Company company,
      Account account,
      LocalDate date,
      LocalDate dueDate)
      throws AxelorException {

    log.debug("In useExcessPaymentWithAmount");

    int moveLineNo2 = moveLineNo;
    BigDecimal remainingPaidAmount2 = remainingPaidAmount;

    List<Reconcile> reconcileList = new ArrayList<Reconcile>();
    int i = creditMoveLines.size();

    if (i != 0) {
      Query q =
          JPA.em()
              .createQuery(
                  "select new map(ml.account, SUM(ml.amountRemaining)) FROM MoveLine as ml "
                      + "WHERE ml in ?1 group by ml.account");
      q.setParameter(1, creditMoveLines);

      List<Map<Account, BigDecimal>> allMap = new ArrayList<Map<Account, BigDecimal>>();
      allMap = q.getResultList();
      for (Map<Account, BigDecimal> map : allMap) {
        Account accountMap = (Account) map.values().toArray()[0];
        BigDecimal amountMap = (BigDecimal) map.values().toArray()[1];
        BigDecimal amountDebit = amountMap.min(remainingPaidAmount2);
        if (amountDebit.compareTo(BigDecimal.ZERO) > 0) {
          MoveLine debitMoveLine =
              moveLineService.createMoveLine(
                  move,
                  partner,
                  accountMap,
                  amountDebit,
                  true,
                  date,
                  dueDate,
                  moveLineNo2,
                  null,
                  null);
          move.getMoveLineList().add(debitMoveLine);
          moveLineNo2++;

          for (MoveLine creditMoveLine : creditMoveLines) {
            if (creditMoveLine.getAccount().equals(accountMap)) {
              Reconcile reconcile = null;
              i--;

              // Afin de pouvoir arrêter si il n'y a plus rien à payer
              if (amountDebit.compareTo(BigDecimal.ZERO) <= 0) {
                break;
              }

              BigDecimal amountToPay = amountDebit.min(creditMoveLine.getAmountRemaining());

              // Gestion du passage en 580
              if (i == 0) {
                reconcile =
                    reconcileService.createReconcile(
                        debitMoveLine, creditMoveLine, amountToPay, true);
              } else {
                reconcile =
                    reconcileService.createReconcile(
                        debitMoveLine, creditMoveLine, amountToPay, false);
              }
              // End gestion du passage en 580

              if (reconcile != null) {
                remainingPaidAmount2 = remainingPaidAmount2.subtract(amountToPay);
                amountDebit = amountDebit.subtract(amountToPay);
                reconcileList.add(reconcile);
              }
            }
          }
        }
      }

      for (Reconcile reconcile : reconcileList) {
        reconcileService.confirmReconcile(reconcile, true);
      }
    }
    // Si il y a un restant à payer, alors on crée un dû.
    if (remainingPaidAmount2.compareTo(BigDecimal.ZERO) > 0) {

      MoveLine debitmoveLine =
          moveLineService.createMoveLine(
              move,
              partner,
              account,
              remainingPaidAmount2,
              true,
              date,
              dueDate,
              moveLineNo2,
              null,
              null);

      move.getMoveLineList().add(debitmoveLine);
      moveLineNo2++;
    }
    log.debug("End useExcessPaymentWithAmount");

    return moveLineNo2;
  }

  @Override
  public BigDecimal getAmountRemainingFromPaymentMove(PaymentScheduleLine psl) {
    BigDecimal amountRemaining = BigDecimal.ZERO;
    if (psl.getAdvanceOrPaymentMove() != null
        && psl.getAdvanceOrPaymentMove().getMoveLineList() != null) {
      for (MoveLine moveLine : psl.getAdvanceOrPaymentMove().getMoveLineList()) {
        if (moveLine.getAccount().getUseForPartnerBalance()) {
          amountRemaining = amountRemaining.add(moveLine.getCredit());
        }
      }
    }
    return amountRemaining;
  }

  @Override
  public BigDecimal getAmountRemainingFromPaymentMove(Invoice invoice) {
    BigDecimal amountRemaining = BigDecimal.ZERO;
    if (invoice.getPaymentMove() != null && invoice.getPaymentMove().getMoveLineList() != null) {
      for (MoveLine moveLine : invoice.getPaymentMove().getMoveLineList()) {
        if (moveLine.getAccount().getUseForPartnerBalance()) {
          amountRemaining = amountRemaining.add(moveLine.getCredit());
        }
      }
    }
    return amountRemaining;
  }
}
