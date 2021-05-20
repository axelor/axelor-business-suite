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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.PaymentSchedule;
import com.axelor.apps.account.db.PaymentScheduleLine;
import com.axelor.apps.account.db.repo.PaymentScheduleLineRepository;
import com.axelor.apps.account.db.repo.PaymentScheduleRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentScheduleServiceImpl implements PaymentScheduleService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected PaymentScheduleLineService paymentScheduleLineService;
  protected PaymentScheduleLineRepository paymentScheduleLineRepo;
  protected SequenceService sequenceService;
  protected PaymentScheduleRepository paymentScheduleRepo;
  protected PartnerService partnerService;

  protected AppAccountService appAccountService;

  @Inject
  public PaymentScheduleServiceImpl(
      AppAccountService appAccountService,
      PaymentScheduleLineService paymentScheduleLineService,
      PaymentScheduleLineRepository paymentScheduleLineRepo,
      SequenceService sequenceService,
      PaymentScheduleRepository paymentScheduleRepo,
      PartnerService partnerService) {
    this.paymentScheduleLineService = paymentScheduleLineService;
    this.paymentScheduleLineRepo = paymentScheduleLineRepo;
    this.sequenceService = sequenceService;
    this.paymentScheduleRepo = paymentScheduleRepo;
    this.partnerService = partnerService;

    this.appAccountService = appAccountService;
  }

  /**
   * Création d'un échéancier sans ces lignes.
   *
   * @param partner Le tiers.
   * @param invoices Collection de factures.
   * @param company La société.
   * @param startDate Date de première échéance.
   * @param nbrTerm Nombre d'échéances.
   * @return L'échéancier créé.
   * @throws AxelorException
   */
  @Override
  public PaymentSchedule createPaymentSchedule(
      Partner partner, Company company, Set<Invoice> invoices, LocalDate startDate, int nbrTerm)
      throws AxelorException {

    Invoice invoice = null;

    PaymentSchedule paymentSchedule =
        this.createPaymentSchedule(
            partner,
            invoice,
            company,
            appAccountService.getTodayDate(company),
            startDate,
            nbrTerm,
            partnerService.getDefaultBankDetails(partner),
            partner.getInPaymentMode());

    paymentSchedule.getInvoiceSet().addAll(invoices);

    return paymentSchedule;
  }

  /**
   * Création d'un échéancier sans ces lignes.
   *
   * @param partner Le tiers.
   * @param invoice Facture globale permettant de définir la facture pour une échéance. L'échéancier
   *     est automatiquement associé à la facture si celle-ci existe.
   * @param company La société.
   * @param date Date de création.
   * @param startDate Date de première échéance.
   * @param nbrTerm Nombre d'échéances.
   * @param bankDetails RIB.
   * @param paymentMode Mode de paiement.
   * @return L'échéancier créé.
   * @throws AxelorException
   */
  @Override
  public PaymentSchedule createPaymentSchedule(
      Partner partner,
      Invoice invoice,
      Company company,
      LocalDate date,
      LocalDate startDate,
      int nbrTerm,
      BankDetails bankDetails,
      PaymentMode paymentMode)
      throws AxelorException {

    PaymentSchedule paymentSchedule = new PaymentSchedule();

    paymentSchedule.setCompany(company);
    paymentSchedule.setPaymentScheduleSeq(this.getPaymentScheduleSequence(company));
    paymentSchedule.setCreationDate(date);
    paymentSchedule.setStartDate(startDate);
    paymentSchedule.setNbrTerm(nbrTerm);
    paymentSchedule.setBankDetails(bankDetails);
    paymentSchedule.setPaymentMode(paymentMode);
    paymentSchedule.setPartner(partner);

    if (paymentSchedule.getInvoiceSet() == null) {
      paymentSchedule.setInvoiceSet(new HashSet<Invoice>());
    } else {
      paymentSchedule.getInvoiceSet().clear();
    }

    if (invoice != null) {
      paymentSchedule.addInvoiceSetItem(invoice);
      invoice.setPaymentSchedule(paymentSchedule);
    }

    return paymentSchedule;
  }

  /**
   * Fonction permettant de tester et de récupérer une séquence de prélèvement
   *
   * @param company Une société
   * @return
   * @throws AxelorException
   */
  @Override
  public String getPaymentScheduleSequence(Company company) throws AxelorException {
    String seq = sequenceService.getSequenceNumber(SequenceRepository.PAYMENT_SCHEDULE, company);
    if (seq == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          "%s :\n" + I18n.get(IExceptionMessage.PAYMENT_SCHEDULE_5),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          company.getName());
    }
    return seq;
  }

  /**
   * Obtenir le total des factures des lignes d'un échéancier.
   *
   * @param paymentSchedule L'échéancier cible.
   * @return Le somme des montants TTC des lignes de l'échéancier.
   */
  @Override
  public BigDecimal getInvoiceTermTotal(PaymentSchedule paymentSchedule) {

    BigDecimal totalAmount = BigDecimal.ZERO;

    if (paymentSchedule != null
        && paymentSchedule.getPaymentScheduleLineList() != null
        && !paymentSchedule.getPaymentScheduleLineList().isEmpty()) {
      for (PaymentScheduleLine paymentScheduleLine : paymentSchedule.getPaymentScheduleLineList()) {
        if (paymentScheduleLine.getInTaxAmount() != null) {

          log.debug(
              "Somme TTC des lignes de l'échéancier {} : total = {}, ajout = {}",
              new Object[] {
                paymentSchedule.getPaymentScheduleSeq(),
                totalAmount,
                paymentScheduleLine.getInTaxAmount()
              });

          totalAmount = totalAmount.add(paymentScheduleLine.getInTaxAmount());
        }
      }
    }

    log.debug(
        "Obtention de la somme TTC des lignes de l'échéancier {} : {}",
        new Object[] {paymentSchedule.getPaymentScheduleSeq(), totalAmount});

    return totalAmount;
  }

  /**
   * Mise à jour d'un échéancier avec un nouveau montant d'échéance.
   *
   * @param paymentSchedule L'échéancier cible.
   * @param inTaxTotal Nouveau montant d'une échéance.
   */
  @Override
  @Transactional
  public void updatePaymentSchedule(PaymentSchedule paymentSchedule, BigDecimal inTaxTotal) {

    log.debug(
        "Mise à jour de l'échéancier {} : {}",
        new Object[] {paymentSchedule.getPaymentScheduleSeq(), inTaxTotal});

    for (PaymentScheduleLine paymentScheduleLine : paymentSchedule.getPaymentScheduleLineList()) {

      if (paymentScheduleLine.getStatusSelect() == PaymentScheduleLineRepository.STATUS_IN_PROGRESS
          && !paymentScheduleLine.getRejectedOk()) {

        log.debug("Mise à jour de la ligne {} ", paymentScheduleLine.getName());

        paymentScheduleLine.setInTaxAmount(inTaxTotal);
      }
    }

    paymentScheduleRepo.save(paymentSchedule);
  }

  /**
   * Création d'un échéancier avec ces lignes.
   *
   * @param company La société.
   * @param date Date de création.
   * @param firstTermDate Date de première échéance.
   * @param initialInTaxAmount Montant d'une échéance.
   * @param nbrTerm Nombre d'échéances.
   * @param bankDetails RIB.
   * @param paymentMode Mode de paiement.
   * @return L'échéancier créé.
   * @throws AxelorException
   */
  @Override
  public PaymentSchedule createPaymentSchedule(
      Partner partner,
      Company company,
      LocalDate date,
      LocalDate firstTermDate,
      BigDecimal initialInTaxAmount,
      int nbrTerm,
      BankDetails bankDetails,
      PaymentMode paymentMode)
      throws AxelorException {

    Invoice invoice = null;
    PaymentSchedule paymentSchedule =
        this.createPaymentSchedule(
            partner, invoice, company, date, firstTermDate, nbrTerm, bankDetails, paymentMode);

    paymentSchedule.setPaymentScheduleLineList(new ArrayList<PaymentScheduleLine>());

    for (int term = 1; term < nbrTerm + 1; term++) {
      paymentSchedule
          .getPaymentScheduleLineList()
          .add(
              paymentScheduleLineService.createPaymentScheduleLine(
                  paymentSchedule, initialInTaxAmount, term, firstTermDate.plusMonths(term - 1)));
    }

    return paymentSchedule;
  }

  /**
   * This method is used to get the movelines to be paid based on a paymentSchedule It loops on the
   * invoice M2M content and gets the movelines which are to pay
   *
   * @param paymentSchedule
   * @return
   */
  @Override
  public List<MoveLine> getPaymentSchedulerMoveLineToPay(PaymentSchedule paymentSchedule) {
    log.debug("In getPaymentSchedulerMoveLineToPay ....");
    List<MoveLine> moveLines = new ArrayList<MoveLine>();
    for (Invoice invoice : paymentSchedule.getInvoiceSet()) {
      if (invoice.getCompanyInTaxTotalRemaining().compareTo(BigDecimal.ZERO) > 0
          && invoice.getMove() != null
          && invoice.getMove().getMoveLineList() != null) {
        for (MoveLine moveLine : invoice.getMove().getMoveLineList()) {
          if (moveLine.getAccount().getUseForPartnerBalance()
              && moveLine.getAmountRemaining().abs().compareTo(BigDecimal.ZERO) > 0
              && moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0) {
            moveLines.add(moveLine);
          }
        }
      }
    }
    log.debug("End getPaymentSchedulerMoveLineToPay.");
    return moveLines;
  }

  @Override
  public void checkTotalLineAmount(PaymentSchedule paymentSchedule) throws AxelorException {
    BigDecimal total = getInvoiceTermTotal(paymentSchedule);

    if (total.compareTo(paymentSchedule.getInTaxAmount()) != 0) {
      throw new AxelorException(
          paymentSchedule,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.PAYMENT_SCHEDULE_LINE_AMOUNT_MISMATCH),
          total,
          paymentSchedule.getInTaxAmount());
    }
  }

  /**
   * Permet de valider un échéancier.
   *
   * @param paymentSchedule
   * @throws AxelorException
   */
  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void validatePaymentSchedule(PaymentSchedule paymentSchedule) throws AxelorException {

    log.debug("Validation de l'échéancier {}", paymentSchedule.getPaymentScheduleSeq());

    if (paymentSchedule.getPaymentScheduleLineList() == null
        || paymentSchedule.getPaymentScheduleLineList().isEmpty()) {
      throw new AxelorException(
          paymentSchedule,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.PAYMENT_SCHEDULE_6),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          paymentSchedule.getPaymentScheduleSeq());
    }

    checkTotalLineAmount(paymentSchedule);

    for (PaymentScheduleLine paymentScheduleLine : paymentSchedule.getPaymentScheduleLineList()) {
      paymentScheduleLine.setStatusSelect(PaymentScheduleLineRepository.STATUS_IN_PROGRESS);
    }

    this.updateInvoices(paymentSchedule);

    paymentSchedule.setStatusSelect(PaymentScheduleRepository.STATUS_CONFIRMED);
  }

  @Override
  public void updateInvoices(PaymentSchedule paymentSchedule) {

    if (paymentSchedule.getInvoiceSet() != null) {

      List<MoveLine> moveLineInvoiceToPay = this.getPaymentSchedulerMoveLineToPay(paymentSchedule);

      for (MoveLine moveLineInvoice : moveLineInvoiceToPay) {

        moveLineInvoice.getMove().setIgnoreInDebtRecoveryOk(true);
        this.updateInvoice(moveLineInvoice.getMove().getInvoice(), paymentSchedule);
      }
    }
  }

  @Override
  public void updateInvoice(Invoice invoice, PaymentSchedule paymentSchedule) {

    invoice.setSchedulePaymentOk(true);
    invoice.setPaymentSchedule(paymentSchedule);
  }

  /**
   * Methode qui annule un échéancier
   *
   * @param paymentSchedule
   */
  @Override
  public void cancelPaymentSchedule(PaymentSchedule paymentSchedule) {

    // L'échéancier est passé à annulé
    paymentSchedule.setStatusSelect(PaymentScheduleRepository.STATUS_CANCELED);

    for (PaymentScheduleLine paymentScheduleLine : paymentSchedule.getPaymentScheduleLineList()) {

      // Si l'échéance n'est pas complètement payée
      if (paymentScheduleLine.getInTaxAmountPaid().compareTo(paymentScheduleLine.getInTaxAmount())
          != 0) {

        // L'échéance est passée à cloturé
        paymentScheduleLine.setStatusSelect(PaymentScheduleLineRepository.STATUS_CLOSED);
      }
    }

    for (Invoice invoice : paymentSchedule.getInvoiceSet()) {
      // L'échéancier n'est plus selectionné sur la facture
      invoice.setPaymentSchedule(null);

      // L'échéancier est assigné dans un nouveau champs afin de garder un lien invisble pour
      // l'utilisateur, mais utilisé pour le passage en irrécouvrable
      invoice.setCanceledPaymentSchedule(paymentSchedule);
      invoice.setSchedulePaymentOk(false);
    }
  }

  /**
   * Methode permettant de savoir si l'échéance passée en paramètre est la dernière de l'échéancier
   *
   * @param paymentScheduleLine
   * @return
   */
  @Override
  public boolean isLastSchedule(PaymentScheduleLine paymentScheduleLine) {
    if (paymentScheduleLine != null) {
      if (paymentScheduleLineRepo
              .all()
              .filter(
                  "self.paymentSchedule = ?1 and self.scheduleDate > ?2 and self.statusSelect = ?3",
                  paymentScheduleLine.getPaymentSchedule(),
                  paymentScheduleLine.getScheduleDate(),
                  PaymentScheduleLineRepository.STATUS_IN_PROGRESS)
              .fetchOne()
          == null) {
        log.debug("Dernière échéance");
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  /**
   * Méthode permettant de passer les statuts des lignes d'échéances et de l'échéancier à 'clo' ie
   * cloturé
   *
   * @param paymentSchedule
   * @throws AxelorException
   */
  @Override
  public void closePaymentSchedule(PaymentSchedule paymentSchedule) throws AxelorException {

    log.debug("Cloture de l'échéancier");

    // On récupère un statut cloturé, afin de pouvoir changer l'état des lignes d'échéanciers

    for (PaymentScheduleLine paymentScheduleLine : paymentSchedule.getPaymentScheduleLineList()) {
      paymentScheduleLine.setStatusSelect(PaymentScheduleLineRepository.STATUS_CLOSED);
    }
    paymentSchedule.setStatusSelect(PaymentScheduleRepository.STATUS_CLOSED);
  }

  /**
   * Close payment schedule if all paid.
   *
   * @param paymentSchedule
   * @throws AxelorException
   */
  @Override
  public void closePaymentScheduleIfAllPaid(PaymentSchedule paymentSchedule)
      throws AxelorException {
    if (paymentSchedule.getPaymentScheduleLineList() == null) {
      return;
    }

    for (PaymentScheduleLine paymentScheduleLine : paymentSchedule.getPaymentScheduleLineList()) {
      if (paymentScheduleLine.getStatusSelect() < PaymentScheduleLineRepository.STATUS_VALIDATED) {
        return;
      }
    }

    paymentSchedule.setStatusSelect(PaymentScheduleRepository.STATUS_CLOSED);
  }

  @Override
  public LocalDate getMostOldDatePaymentScheduleLine(
      List<PaymentScheduleLine> paymentScheduleLineList) {
    LocalDate minPaymentScheduleLineDate;

    if (paymentScheduleLineList != null && !paymentScheduleLineList.isEmpty()) {
      minPaymentScheduleLineDate =
          appAccountService.getTodayDate(
              paymentScheduleLineList.get(0).getPaymentSchedule().getCompany());
      for (PaymentScheduleLine paymentScheduleLine : paymentScheduleLineList) {
        if (minPaymentScheduleLineDate.isAfter(paymentScheduleLine.getScheduleDate())) {
          minPaymentScheduleLineDate = paymentScheduleLine.getScheduleDate();
        }
      }
    } else {
      minPaymentScheduleLineDate = null;
    }
    return minPaymentScheduleLineDate;
  }

  @Override
  public LocalDate getMostRecentDatePaymentScheduleLine(
      List<PaymentScheduleLine> paymentScheduleLineList) {
    LocalDate minPaymentScheduleLineDate;

    if (paymentScheduleLineList != null && !paymentScheduleLineList.isEmpty()) {
      minPaymentScheduleLineDate =
          appAccountService.getTodayDate(
              paymentScheduleLineList.get(0).getPaymentSchedule().getCompany());
      for (PaymentScheduleLine paymentScheduleLine : paymentScheduleLineList) {
        if (minPaymentScheduleLineDate.isBefore(paymentScheduleLine.getScheduleDate())) {
          minPaymentScheduleLineDate = paymentScheduleLine.getScheduleDate();
        }
      }
    } else {
      minPaymentScheduleLineDate = null;
    }
    return minPaymentScheduleLineDate;
  }

  // Transactional

  /**
   * Créer des lignes d'échéancier à partir des lignes de factures de celui-ci.
   *
   * @param paymentSchedule
   * @throws AxelorException
   */
  @Override
  @Transactional
  public void createPaymentScheduleLines(PaymentSchedule paymentSchedule) {

    this.initCollection(paymentSchedule);

    paymentSchedule
        .getPaymentScheduleLineList()
        .addAll(paymentScheduleLineService.createPaymentScheduleLines(paymentSchedule));
    paymentScheduleRepo.save(paymentSchedule);
  }

  @Override
  public void initCollection(PaymentSchedule paymentSchedule) {

    if (paymentSchedule.getPaymentScheduleLineList() == null) {
      paymentSchedule.setPaymentScheduleLineList(new ArrayList<PaymentScheduleLine>());
    } else {
      paymentSchedule.getPaymentScheduleLineList().clear();
    }
  }

  @Override
  @Transactional
  public void toCancelPaymentSchedule(PaymentSchedule paymentSchedule) {
    this.cancelPaymentSchedule(paymentSchedule);
    paymentScheduleRepo.save(paymentSchedule);
  }

  @Override
  public BankDetails getBankDetails(PaymentSchedule paymentSchedule) throws AxelorException {
    BankDetails bankDetails = paymentSchedule.getBankDetails();

    if (bankDetails != null) {
      return bankDetails;
    }

    Partner partner = paymentSchedule.getPartner();
    Preconditions.checkNotNull(partner);
    bankDetails = partnerService.getDefaultBankDetails(partner);

    if (bankDetails != null) {
      return bankDetails;
    }

    throw new AxelorException(
        partner,
        TraceBackRepository.CATEGORY_MISSING_FIELD,
        I18n.get(IExceptionMessage.PARTNER_BANK_DETAILS_MISSING),
        partner.getName());
  }

  @Override
  public int getNextScheduleLineSeq(PaymentSchedule paymentSchedule) {
    List<PaymentScheduleLine> paymentScheduleLines =
        MoreObjects.firstNonNull(
            paymentSchedule.getPaymentScheduleLineList(), Collections.emptyList());
    int currentMaxSequenceNumber =
        paymentScheduleLines.stream()
            .mapToInt(PaymentScheduleLine::getScheduleLineSeq)
            .max()
            .orElse(0);
    return currentMaxSequenceNumber + 1;
  }
}
