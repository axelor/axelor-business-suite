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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.ReconcileGroup;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.ReconcileRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveAdjustementService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCancelService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCreateService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReconcileServiceImpl implements ReconcileService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected MoveToolService moveToolService;
  protected AccountCustomerService accountCustomerService;
  protected AccountConfigService accountConfigService;
  protected ReconcileRepository reconcileRepository;
  protected MoveAdjustementService moveAdjustementService;
  protected ReconcileSequenceService reconcileSequenceService;
  protected InvoicePaymentCreateService invoicePaymentCreateService;
  protected InvoicePaymentCancelService invoicePaymentCancelService;
  protected MoveLineService moveLineService;

  @Inject
  public ReconcileServiceImpl(
      MoveToolService moveToolService,
      AccountCustomerService accountCustomerService,
      AccountConfigService accountConfigService,
      ReconcileRepository reconcileRepository,
      MoveAdjustementService moveAdjustementService,
      ReconcileSequenceService reconcileSequenceService,
      InvoicePaymentCancelService invoicePaymentCancelService,
      InvoicePaymentCreateService invoicePaymentCreateService,
      MoveLineService moveLineService) {

    this.moveToolService = moveToolService;
    this.accountCustomerService = accountCustomerService;
    this.accountConfigService = accountConfigService;
    this.reconcileRepository = reconcileRepository;
    this.moveAdjustementService = moveAdjustementService;
    this.reconcileSequenceService = reconcileSequenceService;
    this.invoicePaymentCancelService = invoicePaymentCancelService;
    this.invoicePaymentCreateService = invoicePaymentCreateService;
    this.moveLineService = moveLineService;
  }

  /**
   * Permet de créer une réconciliation en passant les paramètres qu'il faut
   *
   * @param lineDebit Une ligne d'écriture au débit
   * @param lineCredit Une ligne d'écriture au crédit
   * @param amount Le montant à reconciler
   * @param canBeZeroBalanceOk Peut être soldé?
   * @return Une reconciliation
   */
  @Transactional
  public Reconcile createReconcile(
      MoveLine debitMoveLine,
      MoveLine creditMoveLine,
      BigDecimal amount,
      boolean canBeZeroBalanceOk) {

    if (ReconcileService.isReconcilable(debitMoveLine, creditMoveLine)
        && amount.compareTo(BigDecimal.ZERO) > 0) {
      log.debug(
          "Create Reconcile (Company : {}, Debit MoveLine : {}, Credit MoveLine : {}, Amount : {}, Can be zero balance ? {} )",
          debitMoveLine.getMove().getCompany(),
          debitMoveLine.getName(),
          creditMoveLine.getName(),
          amount,
          canBeZeroBalanceOk);

      Reconcile reconcile =
          new Reconcile(
              debitMoveLine.getMove().getCompany(),
              amount.setScale(2, RoundingMode.HALF_EVEN),
              debitMoveLine,
              creditMoveLine,
              ReconcileRepository.STATUS_DRAFT,
              canBeZeroBalanceOk);

      if (!moveToolService.isDebitMoveLine(debitMoveLine)) {

        reconcile.setDebitMoveLine(creditMoveLine);
        reconcile.setCreditMoveLine(debitMoveLine);
      }

      return reconcileRepository.save(reconcile);
    }
    return null;
  }

  /**
   * Permet de confirmer une réconciliation On ne peut réconcilier que des moveLine ayant le même
   * compte
   *
   * @param reconcile Une reconciliation
   * @return L'etat de la reconciliation
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  public Reconcile confirmReconcile(Reconcile reconcile, boolean updateInvoicePayments)
      throws AxelorException {

    this.reconcilePreconditions(reconcile);

    MoveLine debitMoveLine = reconcile.getDebitMoveLine();
    MoveLine creditMoveLine = reconcile.getCreditMoveLine();

    // Add the reconciled amount to the reconciled amount in the move line
    creditMoveLine.setAmountPaid(creditMoveLine.getAmountPaid().add(reconcile.getAmount()));
    debitMoveLine.setAmountPaid(debitMoveLine.getAmountPaid().add(reconcile.getAmount()));

    reconcile = reconcileRepository.save(reconcile);

    reconcile.setStatusSelect(ReconcileRepository.STATUS_CONFIRMED);

    if (reconcile.getCanBeZeroBalanceOk()) {
      // Alors nous utilisons la règle de gestion consitant à imputer l'écart sur un compte
      // transitoire si le seuil est respecté
      canBeZeroBalance(reconcile);
    }

    reconcile.setReconciliationDate(LocalDate.now());

    reconcileSequenceService.setSequence(reconcile);

    this.updatePartnerAccountingSituation(reconcile);
    this.updateInvoiceCompanyInTaxTotalRemaining(reconcile);
    this.udpatePaymentTax(reconcile);
    if (updateInvoicePayments) {
      this.updateInvoicePayments(reconcile);
    }
    this.addToReconcileGroup(reconcile);

    return reconcileRepository.save(reconcile);
  }

  @Override
  public void addToReconcileGroup(Reconcile reconcile) throws AxelorException {
    ReconcileGroupService reconcileGroupService = Beans.get(ReconcileGroupService.class);

    ReconcileGroup reconcileGroup = reconcileGroupService.findOrCreateGroup(reconcile);

    reconcileGroupService.addAndValidate(reconcileGroup, reconcile);
  }

  public void reconcilePreconditions(Reconcile reconcile) throws AxelorException {

    MoveLine debitMoveLine = reconcile.getDebitMoveLine();
    MoveLine creditMoveLine = reconcile.getCreditMoveLine();

    if (debitMoveLine == null || creditMoveLine == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.RECONCILE_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION));
    }

    // Check if move lines companies are the same as the reconcile company
    Company reconcileCompany = reconcile.getCompany();
    Company debitMoveLineCompany = debitMoveLine.getMove().getCompany();
    Company creditMoveLineCompany = creditMoveLine.getMove().getCompany();
    if (!debitMoveLineCompany.equals(reconcileCompany)
        && !creditMoveLineCompany.equals(reconcileCompany)) {
      throw new AxelorException(
          String.format(
              I18n.get(IExceptionMessage.RECONCILE_7),
              I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
              debitMoveLineCompany,
              creditMoveLineCompany,
              reconcileCompany),
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    // Check if move lines accounts are the same (debit and credit)
    if (!creditMoveLine.getAccount().equals(debitMoveLine.getAccount())) {
      log.debug(
          "Compte ligne de credit : {} , Compte ligne de debit : {}",
          creditMoveLine.getAccount(),
          debitMoveLine.getAccount());

      // Check if move lines accounts are compatible
      if (!debitMoveLine
          .getAccount()
          .getCompatibleAccountSet()
          .contains(creditMoveLine.getAccount())) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.RECONCILE_2),
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION));
      }
    }

    // Check if the amount to reconcile is != zero
    if (reconcile.getAmount() == null || reconcile.getAmount().compareTo(BigDecimal.ZERO) == 0) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.RECONCILE_4),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          reconcile.getReconcileSeq(),
          debitMoveLine.getName(),
          debitMoveLine.getAccount().getLabel(),
          creditMoveLine.getName(),
          creditMoveLine.getAccount().getLabel());
    }

    if ((reconcile
                .getAmount()
                .compareTo(creditMoveLine.getCredit().subtract(creditMoveLine.getAmountPaid()))
            > 0
        || (reconcile
                .getAmount()
                .compareTo(debitMoveLine.getDebit().subtract(debitMoveLine.getAmountPaid()))
            > 0))) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.RECONCILE_5) + " " + I18n.get(IExceptionMessage.RECONCILE_3),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          reconcile.getReconcileSeq(),
          reconcile.getAmount(),
          debitMoveLine.getName(),
          debitMoveLine.getAccount().getLabel(),
          debitMoveLine.getDebit().subtract(debitMoveLine.getAmountPaid()),
          creditMoveLine.getName(),
          creditMoveLine.getAccount().getLabel(),
          creditMoveLine.getCredit().subtract(creditMoveLine.getAmountPaid()));
    }
  }

  public void updatePartnerAccountingSituation(Reconcile reconcile) throws AxelorException {

    List<Partner> partnerList = this.getPartners(reconcile);

    if (partnerList != null && !partnerList.isEmpty()) {

      Company company = reconcile.getDebitMoveLine().getMove().getCompany();

      if (AccountingService.getUpdateCustomerAccount()) {
        accountCustomerService.updatePartnerAccountingSituation(
            partnerList, company, true, true, false);
      } else {
        accountCustomerService.flagPartners(partnerList, company);
      }
    }
  }

  public List<Partner> getPartners(Reconcile reconcile) {

    List<Partner> partnerList = Lists.newArrayList();
    Partner debitPartner = reconcile.getDebitMoveLine().getPartner();
    Partner creditPartner = reconcile.getCreditMoveLine().getPartner();
    if (debitPartner != null && creditPartner != null && debitPartner.equals(creditPartner)) {
      partnerList.add(debitPartner);
    } else if (debitPartner != null) {
      partnerList.add(debitPartner);
    } else if (creditPartner != null) {
      partnerList.add(creditPartner);
    }

    return partnerList;
  }

  public void updateInvoiceCompanyInTaxTotalRemaining(Reconcile reconcile) throws AxelorException {

    Invoice debitInvoice = reconcile.getDebitMoveLine().getMove().getInvoice();
    Invoice creditInvoice = reconcile.getCreditMoveLine().getMove().getInvoice();

    // Update amount remaining on invoice or refund
    if (debitInvoice != null) {

      debitInvoice.setCompanyInTaxTotalRemaining(
          moveToolService.getInTaxTotalRemaining(debitInvoice));
    }
    if (creditInvoice != null) {

      creditInvoice.setCompanyInTaxTotalRemaining(
          moveToolService.getInTaxTotalRemaining(creditInvoice));
    }
  }

  public void updateInvoicePayments(Reconcile reconcile) throws AxelorException {

    MoveLine debitMoveLine = reconcile.getDebitMoveLine();
    MoveLine creditMoveLine = reconcile.getCreditMoveLine();
    Move debitMove = debitMoveLine.getMove();
    Move creditMove = creditMoveLine.getMove();
    Invoice debitInvoice = debitMove.getInvoice();
    Invoice creditInvoice = creditMove.getInvoice();
    BigDecimal amount = reconcile.getAmount();

    if (debitInvoice != null
        && debitMoveLine.getAccount().getUseForPartnerBalance()
        && creditMoveLine.getAccount().getUseForPartnerBalance()) {
      InvoicePayment debitInvoicePayment =
          invoicePaymentCreateService.createInvoicePayment(
              debitInvoice, amount, creditMove, creditMove.getDate());
      debitInvoicePayment.setReconcile(reconcile);
    }
    if (creditInvoice != null
        && debitMoveLine.getAccount().getUseForPartnerBalance()
        && creditMoveLine.getAccount().getUseForPartnerBalance()) {
      InvoicePayment creditInvoicePayment =
          invoicePaymentCreateService.createInvoicePayment(
              creditInvoice, amount, debitMove, debitMove.getDate());
      creditInvoicePayment.setReconcile(reconcile);
    }
  }

  protected void udpatePaymentTax(Reconcile reconcile) throws AxelorException {
    Move debitMove = reconcile.getDebitMoveLine().getMove();
    Move creditMove = reconcile.getCreditMoveLine().getMove();
    Invoice debitInvoice = debitMove.getInvoice();
    Invoice creditInvoice = creditMove.getInvoice();

    if (debitInvoice != null && creditInvoice == null) {
      moveLineService.generateTaxPaymentMoveLineList(
          reconcile.getCreditMoveLine(), debitInvoice, reconcile);
    }
    if (creditInvoice != null && debitInvoice == null) {
      moveLineService.generateTaxPaymentMoveLineList(
          reconcile.getDebitMoveLine(), creditInvoice, reconcile);
    }
  }

  /**
   * Méthode permettant de lettrer une écriture au débit avec une écriture au crédit
   *
   * @param debitMoveLine
   * @param creditMoveLine
   * @throws AxelorException
   */
  public Reconcile reconcile(
      MoveLine debitMoveLine,
      MoveLine creditMoveLine,
      boolean canBeZeroBalanceOk,
      boolean updateInvoicePayments)
      throws AxelorException {
    BigDecimal amount = debitMoveLine.getAmountRemaining().min(creditMoveLine.getAmountRemaining());
    Reconcile reconcile =
        this.createReconcile(debitMoveLine, creditMoveLine, amount, canBeZeroBalanceOk);
    if (reconcile != null) {
      this.confirmReconcile(reconcile, updateInvoicePayments);
      return reconcile;
    }

    return null;
  }

  /**
   * Permet de déréconcilier
   *
   * @param reconcile Une reconciliation
   * @return L'etat de la réconciliation
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  public void unreconcile(Reconcile reconcile) throws AxelorException {

    log.debug("unreconcile : reconcile : {}", reconcile);

    MoveLine debitMoveLine = reconcile.getDebitMoveLine();
    MoveLine creditMoveLine = reconcile.getCreditMoveLine();

    // Change the state
    reconcile.setStatusSelect(ReconcileRepository.STATUS_CANCELED);
    // Add the reconciled amount to the reconciled amount in the move line
    creditMoveLine.setAmountPaid(creditMoveLine.getAmountPaid().subtract(reconcile.getAmount()));
    debitMoveLine.setAmountPaid(debitMoveLine.getAmountPaid().subtract(reconcile.getAmount()));

    reconcileRepository.save(reconcile);

    // Update amount remaining on invoice or refund
    this.updatePartnerAccountingSituation(reconcile);
    this.updateInvoiceCompanyInTaxTotalRemaining(reconcile);
    this.updateInvoicePaymentsCanceled(reconcile);
    this.reverseTaxPaymentMoveLines(reconcile);
    // Update reconcile group
    Beans.get(ReconcileGroupService.class).remove(reconcile);
  }

  protected void reverseTaxPaymentMoveLines(Reconcile reconcile) throws AxelorException {
    Move debitMove = reconcile.getDebitMoveLine().getMove();
    Move creditMove = reconcile.getCreditMoveLine().getMove();
    Invoice debitInvoice = debitMove.getInvoice();
    Invoice creditInvoice = creditMove.getInvoice();
    if (debitInvoice == null) {
      moveLineService.reverseTaxPaymentMoveLines(reconcile.getDebitMoveLine(), reconcile);
    }
    if (creditInvoice == null) {
      moveLineService.reverseTaxPaymentMoveLines(reconcile.getCreditMoveLine(), reconcile);
    }
  }

  public void updateInvoicePaymentsCanceled(Reconcile reconcile) throws AxelorException {

    log.debug("updateInvoicePaymentsCanceled : reconcile : {}", reconcile);

    List<InvoicePayment> invoicePaymentList =
        Beans.get(InvoicePaymentRepository.class)
            .all()
            .filter("self.reconcile = ?1", reconcile)
            .fetch();

    for (InvoicePayment invoicePayment : invoicePaymentList) {
      invoicePaymentCancelService.updateCancelStatus(invoicePayment);
    }
  }

  /**
   * Procédure permettant de gérer les écarts de règlement, check sur la case à cocher 'Peut être
   * soldé' Alors nous utilisons la règle de gestion consitant à imputer l'écart sur un compte
   * transitoire si le seuil est respecté
   *
   * @param reconcile Une reconciliation
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  public void canBeZeroBalance(Reconcile reconcile) throws AxelorException {

    MoveLine debitMoveLine = reconcile.getDebitMoveLine();

    BigDecimal debitAmountRemaining = debitMoveLine.getAmountRemaining();
    log.debug("Montant à payer / à lettrer au débit : {}", debitAmountRemaining);
    if (debitAmountRemaining.compareTo(BigDecimal.ZERO) > 0) {
      Company company = reconcile.getDebitMoveLine().getMove().getCompany();

      AccountConfig accountConfig = accountConfigService.getAccountConfig(company);

      if (debitAmountRemaining.plus().compareTo(accountConfig.getThresholdDistanceFromRegulation())
              < 0
          || reconcile.getMustBeZeroBalanceOk()) {

        log.debug("Seuil respecté");

        MoveLine creditAdjustMoveLine =
            moveAdjustementService.createAdjustmentCreditMove(debitMoveLine);

        // Création de la réconciliation
        Reconcile newReconcile =
            this.createReconcile(debitMoveLine, creditAdjustMoveLine, debitAmountRemaining, false);
        if (newReconcile != null) {
          this.confirmReconcile(newReconcile, true);
          reconcileRepository.save(newReconcile);
        }
      }
    }

    reconcile.setCanBeZeroBalanceOk(false);
    log.debug("Fin de la gestion des écarts de règlement");
  }

  /**
   * Solder le trop-perçu si il respect les règles de seuil
   *
   * @param creditMoveLine
   * @param company
   * @throws AxelorException
   */
  public void balanceCredit(MoveLine creditMoveLine) throws AxelorException {
    if (creditMoveLine != null) {
      BigDecimal creditAmountRemaining = creditMoveLine.getAmountRemaining();
      log.debug("Montant à payer / à lettrer au crédit : {}", creditAmountRemaining);

      if (creditAmountRemaining.compareTo(BigDecimal.ZERO) > 0) {
        AccountConfig accountConfig =
            accountConfigService.getAccountConfig(creditMoveLine.getMove().getCompany());

        if (creditAmountRemaining
                .plus()
                .compareTo(accountConfig.getThresholdDistanceFromRegulation())
            < 0) {

          log.debug("Seuil respecté");

          MoveLine debitAdjustmentMoveLine =
              moveAdjustementService.createAdjustmentCreditMove(creditMoveLine);

          // Création de la réconciliation
          Reconcile newReconcile =
              this.createReconcile(
                  debitAdjustmentMoveLine, creditMoveLine, creditAmountRemaining, false);
          if (newReconcile != null) {
            this.confirmReconcile(newReconcile, true);
            reconcileRepository.save(newReconcile);
          }
        }
      }
    }
  }

  public List<Reconcile> getReconciles(MoveLine moveLine) {

    List<Reconcile> debitReconcileList = moveLine.getDebitReconcileList();
    List<Reconcile> creditReconcileList = moveLine.getCreditReconcileList();

    if (moveToolService.isDebitMoveLine(moveLine)) {
      return debitReconcileList;
    } else if (debitReconcileList != null && !creditReconcileList.isEmpty()) {
      return creditReconcileList;
    }
    return Lists.newArrayList();
  }
}
