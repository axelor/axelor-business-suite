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
package com.axelor.apps.account.service.debtrecovery;

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.DebtRecovery;
import com.axelor.apps.account.db.DebtRecoveryHistory;
import com.axelor.apps.account.db.DebtRecoveryMethod;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PaymentScheduleLine;
import com.axelor.apps.account.db.repo.AccountingSituationRepository;
import com.axelor.apps.account.db.repo.DebtRecoveryRepository;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.db.repo.PaymentScheduleLineRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountCustomerService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.TradingNameRepository;
import com.axelor.apps.message.db.repo.MessageRepository;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.json.JSONException;

public class DebtRecoveryService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected DebtRecoverySessionService debtRecoverySessionService;
  protected DebtRecoveryActionService debtRecoveryActionService;
  protected AccountCustomerService accountCustomerService;
  protected MoveLineRepository moveLineRepo;
  protected PaymentScheduleLineRepository paymentScheduleLineRepo;
  protected AccountConfigService accountConfigService;
  protected DebtRecoveryRepository debtRecoveryRepo;
  protected CompanyRepository companyRepo;
  protected TradingNameRepository tradingNameRepo;

  protected AppAccountService appAccountService;
  protected MessageRepository messageRepo;

  @Inject
  public DebtRecoveryService(
      DebtRecoverySessionService debtRecoverySessionService,
      DebtRecoveryActionService debtRecoveryActionService,
      AccountCustomerService accountCustomerService,
      MoveLineRepository moveLineRepo,
      PaymentScheduleLineRepository paymentScheduleLineRepo,
      AccountConfigService accountConfigService,
      DebtRecoveryRepository debtRecoveryRepo,
      CompanyRepository companyRepo,
      TradingNameRepository tradingNameRepo,
      AppAccountService appAccountService,
      MessageRepository messageRepo) {

    this.debtRecoverySessionService = debtRecoverySessionService;
    this.debtRecoveryActionService = debtRecoveryActionService;
    this.accountCustomerService = accountCustomerService;
    this.moveLineRepo = moveLineRepo;
    this.paymentScheduleLineRepo = paymentScheduleLineRepo;
    this.accountConfigService = accountConfigService;
    this.debtRecoveryRepo = debtRecoveryRepo;
    this.companyRepo = companyRepo;
    this.tradingNameRepo = tradingNameRepo;
    this.appAccountService = appAccountService;
    this.messageRepo = messageRepo;
  }

  public void testCompanyField(Company company) throws AxelorException {

    AccountConfig accountConfig = accountConfigService.getAccountConfig(company);

    accountConfigService.getDebtRecoveryConfigLineList(accountConfig);
  }

  /**
   * Fonction permettant de calculer le solde exigible relançable d'un tiers
   *
   * @param moveLineList
   * @param partner
   * @return Le solde exigible relançable
   */
  public BigDecimal getBalanceDueDebtRecovery(List<MoveLine> moveLineList, Partner partner) {
    BigDecimal balanceSubstract = this.getSubstractBalanceDue(partner);
    BigDecimal balanceDueDebtRecovery = BigDecimal.ZERO;
    for (MoveLine moveLine : moveLineList) {
      balanceDueDebtRecovery = balanceDueDebtRecovery.add(moveLine.getAmountRemaining().abs());
    }
    balanceDueDebtRecovery = balanceDueDebtRecovery.add(balanceSubstract);
    return balanceDueDebtRecovery;
  }

  public BigDecimal getSubstractBalanceDue(Partner partner) {
    List<? extends MoveLine> moveLineQuery =
        moveLineRepo.all().filter("self.partner = ?1", partner).fetch();
    BigDecimal balance = BigDecimal.ZERO;
    for (MoveLine moveLine : moveLineQuery) {
      if (moveLine.getCredit().compareTo(BigDecimal.ZERO) > 0) {
        if (moveLine.getAccount() != null && moveLine.getAccount().getUseForPartnerBalance()) {
          balance = balance.subtract(moveLine.getAmountRemaining().abs());
        }
      }
    }
    return balance;
  }

  /**
   * Fonction qui récupère la plus ancienne date d'échéance d'une liste de lignes d'écriture
   *
   * @param moveLineList Une liste de lignes d'écriture
   * @return la plus ancienne date d'échéance
   */
  public LocalDate getOldDateMoveLine(List<MoveLine> moveLineList) {
    LocalDate minMoveLineDate;

    if (!moveLineList.isEmpty()) {
      minMoveLineDate =
          appAccountService.getTodayDate(
              moveLineList.get(0).getMove() != null
                  ? moveLineList.get(0).getMove().getCompany()
                  : Optional.ofNullable(AuthUtils.getUser())
                      .map(User::getActiveCompany)
                      .orElse(null));
      for (MoveLine moveLine : moveLineList) {
        if (minMoveLineDate.isAfter(moveLine.getDueDate())) {
          minMoveLineDate = moveLine.getDueDate();
        }
      }
    } else {
      minMoveLineDate = null;
    }
    return minMoveLineDate;
  }

  /**
   * Fonction qui récupére la plus récente date entre deux date
   *
   * @param date1 Une date
   * @param date2 Une date
   * @return minDate La plus ancienne date
   */
  public LocalDate getLastDate(LocalDate date1, LocalDate date2) {
    LocalDate minDate;
    if (date1 != null && date2 != null) {
      if (date1.isAfter(date2)) {
        minDate = date1;
      } else {
        minDate = date2;
      }
    } else if (date1 != null) {
      minDate = date1;
    } else if (date2 != null) {
      minDate = date2;
    } else {
      minDate = null;
    }
    return minDate;
  }

  /**
   * Fonction qui permet de récupérer la date de relance la plus récente
   *
   * @param debtRecovery Une relance
   * @return La date de relance la plus récente
   */
  public LocalDate getLastDateDebtRecovery(DebtRecovery debtRecovery) {
    return debtRecovery.getDebtRecoveryDate();
  }

  /**
   * Fonction qui détermine la date de référence
   *
   * @param debtRecovery Une relance
   * @return La date de référence
   */
  public LocalDate getReferenceDate(DebtRecovery debtRecovery) {
    AccountingSituation accountingSituation = this.getAccountingSituation(debtRecovery);
    List<MoveLine> moveLineList =
        this.getMoveLineDebtRecovery(
            accountingSituation.getPartner(),
            accountingSituation.getCompany(),
            debtRecovery.getTradingName());

    // Date la plus ancienne des lignes d'écriture
    LocalDate minMoveLineDate = getOldDateMoveLine(moveLineList);
    log.debug("minMoveLineDate : {}", minMoveLineDate);

    // 2: Date la plus récente des relances
    LocalDate debtRecoveryLastDate = getLastDateDebtRecovery(debtRecovery);
    log.debug("debtRecoveryLastDate : {}", debtRecoveryLastDate);

    // Date de référence : Date la plus récente des deux ensembles (1 et 2)
    LocalDate debtRecoveryRefDate = getLastDate(minMoveLineDate, debtRecoveryLastDate);
    log.debug("debtRecoveryRefDate : {}", debtRecoveryRefDate);

    return debtRecoveryRefDate;
  }

  /**
   * Returns a list of recoverable move lines of a partner in the scope of the activity of a company
   *
   * @param partner The partner to be concerned by the move lines
   * @param company The company to be concerned by the move lines
   * @param tradingName (Optional) The trading name to be concerned by the move lines
   * @return A list of recoverable move lines
   */
  @SuppressWarnings("unchecked")
  public List<MoveLine> getMoveLineDebtRecovery(
      Partner partner, Company company, TradingName tradingName) {
    List<MoveLine> moveLineList = new ArrayList<MoveLine>();

    List<MoveLine> moveLineQuery = (List<MoveLine>) this.getMoveLine(partner, company, tradingName);

    int mailTransitTime = company.getAccountConfig().getMailTransitTime();

    for (MoveLine moveLine : moveLineQuery) {
      if (moveLine.getMove() != null && !moveLine.getMove().getIgnoreInDebtRecoveryOk()) {
        Move move = moveLine.getMove();
        // facture exigibles non bloquée en relance et dont la date de facture + délai
        // d'acheminement < date du jour
        if (move.getStatusSelect() != MoveRepository.STATUS_CANCELED
            && move.getInvoice() != null
            && !move.getInvoice().getDebtRecoveryBlockingOk()
            && !move.getInvoice().getSchedulePaymentOk()
            && ((move.getInvoice().getInvoiceDate()).plusDays(mailTransitTime))
                .isBefore(appAccountService.getTodayDate(company))) {
          if ((moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0)
              && moveLine.getDueDate() != null
              && (appAccountService.getTodayDate(company).isAfter(moveLine.getDueDate())
                  || appAccountService.getTodayDate(company).isEqual(moveLine.getDueDate()))) {
            if (moveLine.getAccount() != null && moveLine.getAccount().getUseForPartnerBalance()) {
              if (moveLine.getAmountRemaining().abs().compareTo(BigDecimal.ZERO) > 0) {
                moveLineList.add(moveLine);
              }
            }
          }
        }
        // échéances rejetées qui ne sont pas bloqués
        else if (move.getInvoice() == null) {
          if (moveLine.getPaymentScheduleLine() != null
              && (moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0)
              && moveLine.getDueDate() != null
              && (appAccountService.getTodayDate(company).isAfter(moveLine.getDueDate())
                  || appAccountService.getTodayDate(company).isEqual(moveLine.getDueDate()))) {
            if (moveLine.getAccount() != null && moveLine.getAccount().getUseForPartnerBalance()) {
              if (moveLine.getAmountRemaining().abs().compareTo(BigDecimal.ZERO) > 0) {
                moveLineList.add(moveLine);
              }
            }
          }
        }
      }
    }
    return moveLineList;
  }

  public List<Invoice> getInvoiceList(List<MoveLine> moveLineList) {
    List<Invoice> invoiceList = new ArrayList<Invoice>();
    for (MoveLine moveLine : moveLineList) {
      if (moveLine.getMove().getInvoice() != null
          && !moveLine.getMove().getInvoice().getDebtRecoveryBlockingOk()) {
        invoiceList.add(moveLine.getMove().getInvoice());
      }
    }
    return invoiceList;
  }

  public List<PaymentScheduleLine> getPaymentScheduleList(
      List<MoveLine> moveLineList, Partner partner) {
    List<PaymentScheduleLine> paymentScheduleLineList = new ArrayList<PaymentScheduleLine>();
    for (MoveLine moveLine : moveLineList) {
      if (moveLine.getMove().getInvoice() == null) {
        // Ajout à la liste des échéances exigibles relançables
        PaymentScheduleLine paymentScheduleLine = getPaymentScheduleFromMoveLine(partner, moveLine);
        if (paymentScheduleLine != null) {
          // Si un montant reste à payer, c'est à dire une échéance rejeté
          if (moveLine.getAmountRemaining().abs().compareTo(BigDecimal.ZERO) > 0) {
            paymentScheduleLineList.add(paymentScheduleLine);
          }
        }
      }
    }
    return paymentScheduleLineList;
  }

  /**
   * Recovers all move lines for a specific partner, in the scope of the activities of a company,
   * and optionally, for a specific trading name.
   *
   * @param partner A partner to be concerned by the move lines
   * @param company A company to be concerned by the move lines
   * @param tradingName (Optional) A trading name to be concerned by the move lines
   * @return all corresponding move lines as a List
   */
  public List<? extends MoveLine> getMoveLine(
      Partner partner, Company company, TradingName tradingName) {

    Query<MoveLine> query;
    if (tradingName == null) {
      query =
          moveLineRepo
              .all()
              .filter("self.partner = ?1 and self.move.company = ?2", partner, company);
    } else {
      query =
          moveLineRepo
              .all()
              .filter(
                  "self.partner = ?1 and self.move.company = ?2 and self.move.tradingName = ?3",
                  partner,
                  company,
                  tradingName);
    }
    return query.fetch();
  }

  /**
   * Méthode permettant de récupérer une ligne d'échéancier depuis une ligne d'écriture
   *
   * @param partner Un tiers
   * @param moveLine
   * @return
   */
  public PaymentScheduleLine getPaymentScheduleFromMoveLine(Partner partner, MoveLine moveLine) {
    return paymentScheduleLineRepo.all().filter("self.rejectMoveLine = ?1", moveLine).fetchOne();
  }

  /**
   * Procédure permettant de tester si aujourd'hui nous sommes dans une période particulière
   *
   * @param dayBegin Le jour du début de la période
   * @param dayEnd Le jour de fin de la période
   * @param monthBegin Le mois de début de la période
   * @param monthEnd Le mois de fin de la période
   * @return Sommes-nous dans la période?
   */
  public boolean periodOk(Company company, int dayBegin, int dayEnd, int monthBegin, int monthEnd) {

    return DateTool.dateInPeriod(
        appAccountService.getTodayDate(company), dayBegin, monthBegin, dayEnd, monthEnd);
  }

  public DebtRecovery getDebtRecovery(Partner partner, Company company) throws AxelorException {

    AccountingSituationRepository accSituationRepo = Beans.get(AccountingSituationRepository.class);
    AccountingSituation accountingSituation =
        accSituationRepo
            .all()
            .filter("self.partner = ?1 and self.company = ?2", partner, company)
            .fetchOne();

    if (accountingSituation == null) {
      throw new AxelorException(
          accountingSituation,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          "%s :\n"
              + I18n.get("Partner")
              + " %s, "
              + I18n.get("Company")
              + " %s : "
              + I18n.get(IExceptionMessage.DEBT_RECOVERY_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          partner.getName(),
          company.getName());
    }

    return accountingSituation.getDebtRecovery();
  }

  /**
   * Find an existing debtRecovery object for a corresponding partner, company and optionally,
   * trading name.
   *
   * @param partner A partner
   * @param company A company
   * @param tradingName (Optional) A trading name
   * @return The corresponding DebtRecovery object, storing information regarding the current debt
   *     recovery situation of the partner for this company & trading name
   * @throws AxelorException
   */
  public DebtRecovery getDebtRecovery(Partner partner, Company company, TradingName tradingName)
      throws AxelorException {

    AccountingSituationRepository accSituationRepo = Beans.get(AccountingSituationRepository.class);
    AccountingSituation accountingSituation =
        accSituationRepo
            .all()
            .filter("self.partner = ?1 and self.company = ?2", partner, company)
            .fetchOne();

    if (accountingSituation == null) {
      throw new AxelorException(
          accountingSituation,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          "%s :\n"
              + I18n.get("Partner")
              + " %s, "
              + I18n.get("Company")
              + " %s : "
              + I18n.get(IExceptionMessage.DEBT_RECOVERY_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          partner.getName(),
          company.getName());
    }
    if (tradingName == null) {
      return accountingSituation.getDebtRecovery();
    } else {
      if (accountingSituation.getTradingNameDebtRecoveryList() != null
          && !accountingSituation.getTradingNameDebtRecoveryList().isEmpty()) {
        for (DebtRecovery debtRecovery : accountingSituation.getTradingNameDebtRecoveryList()) {
          if (debtRecovery.getTradingName().getId() == tradingName.getId()) {
            return debtRecovery;
          }
        }
      }
    }

    return null; // if no debtRecovery has been found for the specified tradingName
  }

  @Transactional(rollbackOn = {Exception.class})
  public DebtRecovery createDebtRecovery(
      AccountingSituation accountingSituation, TradingName tradingName) {
    DebtRecovery debtRecovery = new DebtRecovery();
    if (tradingName != null) {
      debtRecovery.setTradingNameAccountingSituation(accountingSituation);
      if (accountingSituation.getTradingNameDebtRecoveryList() != null) {
        accountingSituation.getTradingNameDebtRecoveryList().add(debtRecovery);
      } else {
        List<DebtRecovery> tradingNameDebtRecoveryList = new ArrayList<DebtRecovery>();
        tradingNameDebtRecoveryList.add(debtRecovery);
        accountingSituation.setTradingNameDebtRecoveryList(tradingNameDebtRecoveryList);
      }
    } else {
      debtRecovery.setAccountingSituation(accountingSituation);
      accountingSituation.setDebtRecovery(debtRecovery);
    }
    debtRecoveryRepo.save(debtRecovery);
    return debtRecovery;
  }

  /**
   * Handle the debt recovery process for a partner and company. Can optionally specify a trading
   * name.
   *
   * @param partner The partner that has debts to be recovered
   * @param company The company for which to recover the debts
   * @param tradingName (optional) A trading name of the company for which to recover the debts
   * @throws AxelorException
   * @throws IllegalAccessException
   * @throws InstantiationException
   * @throws ClassNotFoundException
   * @throws IOException
   */
  @Transactional(rollbackOn = {Exception.class})
  public boolean debtRecoveryGenerate(Partner partner, Company company, TradingName tradingName)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, IOException, JSONException {
    boolean remindedOk = false;

    DebtRecovery debtRecovery =
        this.getDebtRecovery(
            partner, company, tradingName); // getDebtRecovery if one already exists

    BigDecimal balanceDue = accountCustomerService.getBalanceDue(partner, company, tradingName);

    if (balanceDue.compareTo(BigDecimal.ZERO) > 0) {

      log.debug("balanceDue : {} ", balanceDue);

      BigDecimal balanceDueDebtRecovery =
          accountCustomerService.getBalanceDueDebtRecovery(partner, company, tradingName);

      if (balanceDueDebtRecovery.compareTo(BigDecimal.ZERO) > 0) {
        log.debug("balanceDueDebtRecovery : {} ", balanceDueDebtRecovery);

        remindedOk = true;

        if (debtRecovery == null) {
          AccountingSituationRepository accSituationRepo =
              Beans.get(AccountingSituationRepository.class);
          AccountingSituation accountingSituation =
              accSituationRepo
                  .all()
                  .filter("self.partner = ?1 and self.company = ?2", partner, company)
                  .fetchOne();
          debtRecovery = this.createDebtRecovery(accountingSituation, tradingName);
        }

        debtRecovery.setCompany(companyRepo.find(company.getId()));
        if (tradingName != null)
          debtRecovery.setTradingName(tradingNameRepo.find(tradingName.getId()));
        debtRecovery.setCurrency(partner.getCurrency());
        debtRecovery.setBalanceDue(balanceDue);

        List<MoveLine> moveLineList = this.getMoveLineDebtRecovery(partner, company, tradingName);

        this.updateInvoiceDebtRecovery(debtRecovery, this.getInvoiceList(moveLineList));
        this.updatePaymentScheduleLineDebtRecovery(
            debtRecovery, this.getPaymentScheduleList(moveLineList, partner));

        debtRecovery.setBalanceDueDebtRecovery(balanceDueDebtRecovery);

        Integer levelDebtRecovery = -1;
        if (debtRecovery.getDebtRecoveryMethodLine() != null) {
          levelDebtRecovery = debtRecovery.getDebtRecoveryMethodLine().getSequence();
        }

        LocalDate referenceDate = this.getReferenceDate(debtRecovery);

        if (referenceDate != null) {
          log.debug("date de référence : {} ", referenceDate);
          debtRecovery.setReferenceDate(referenceDate);
        } else {
          throw new AxelorException(
              debtRecovery,
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              "%s :\n"
                          + I18n.get("Partner")
                          + " %s, "
                          + I18n.get("Company")
                          + " %s : "
                          + tradingName
                      != null
                  ? I18n.get("Trading name") + " %s : "
                  : "" + I18n.get(IExceptionMessage.DEBT_RECOVERY_2),
              I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
              partner.getName(),
              company.getName());
        }
        if (debtRecovery.getDebtRecoveryMethod() == null) {
          DebtRecoveryMethod debtRecoveryMethod =
              debtRecoverySessionService.getDebtRecoveryMethod(debtRecovery);
          if (debtRecoveryMethod != null) {
            debtRecovery.setDebtRecoveryMethod(debtRecoveryMethod);
            debtRecoverySessionService.debtRecoverySession(debtRecovery);
          } else {
            throw new AxelorException(
                debtRecovery,
                TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                "%s :\n"
                            + I18n.get("Partner")
                            + " %s, "
                            + I18n.get("Company")
                            + " %s : "
                            + tradingName
                        != null
                    ? I18n.get("Trading name") + " %s : "
                    : "" + I18n.get(IExceptionMessage.DEBT_RECOVERY_3),
                I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
                partner.getName(),
                company.getName());
          }
        } else {
          debtRecoverySessionService.debtRecoverySession(debtRecovery);
        }
        if (debtRecovery.getWaitDebtRecoveryMethodLine() == null) {
          // Si le niveau de relance a évolué
          if (debtRecovery.getDebtRecoveryMethodLine() != null
              && debtRecovery.getDebtRecoveryMethodLine().getSequence() > levelDebtRecovery) {
            debtRecoveryActionService.runAction(debtRecovery);

            DebtRecoveryHistory debtRecoveryHistory =
                debtRecoveryActionService.getDebtRecoveryHistory(debtRecovery);

            if (CollectionUtils.isEmpty(
                messageRepo
                    .findByRelatedTo(
                        Math.toIntExact(debtRecoveryHistory.getId()),
                        DebtRecoveryHistory.class.getCanonicalName())
                    .fetch())) {
              debtRecoveryActionService.runMessage(debtRecovery);
            }
          }
        } else {
          log.debug(
              "Tiers {}, Société {} - Niveau de relance en attente ",
              partner.getName(),
              company.getName());
          // TODO Alarm ?
          TraceBackService.trace(
              new AxelorException(
                  debtRecovery,
                  TraceBackRepository.CATEGORY_INCONSISTENCY,
                  "%s :\n"
                      + I18n.get("Partner")
                      + " %s, "
                      + I18n.get("Company")
                      + " %s : "
                      + I18n.get(IExceptionMessage.DEBT_RECOVERY_4),
                  I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
                  partner.getName(),
                  company.getName()));
        }
      } else {
        debtRecoverySessionService.debtRecoveryInitialization(debtRecovery);
      }
    } else {
      debtRecoverySessionService.debtRecoveryInitialization(debtRecovery);
    }
    return remindedOk;
  }

  public void updateInvoiceDebtRecovery(DebtRecovery debtRecovery, List<Invoice> invoiceList) {
    debtRecovery.setInvoiceDebtRecoverySet(new HashSet<Invoice>());
    debtRecovery.getInvoiceDebtRecoverySet().addAll(invoiceList);
  }

  public void updatePaymentScheduleLineDebtRecovery(
      DebtRecovery debtRecovery, List<PaymentScheduleLine> paymentSchedueLineList) {
    debtRecovery.setPaymentScheduleLineDebtRecoverySet(new HashSet<PaymentScheduleLine>());
    debtRecovery.getPaymentScheduleLineDebtRecoverySet().addAll(paymentSchedueLineList);
  }

  public AccountingSituation getAccountingSituation(DebtRecovery debtRecovery) {
    if (debtRecovery.getTradingName() == null) {
      return debtRecovery.getAccountingSituation();
    } else {
      return debtRecovery.getTradingNameAccountingSituation();
    }
  }
}
