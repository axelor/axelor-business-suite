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
import com.axelor.apps.account.db.AnalyticAccount;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.InvoiceLineTax;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.TaxPaymentMoveLine;
import com.axelor.apps.account.db.repo.AccountTypeRepository;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountManagementAccountService;
import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.apps.account.service.FiscalPositionAccountService;
import com.axelor.apps.account.service.TaxAccountService;
import com.axelor.apps.account.service.TaxPaymentMoveLineService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.account.service.payment.PaymentService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.config.CompanyConfigService;
import com.axelor.apps.tool.StringTool;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoveLineService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AccountManagementAccountService accountManagementService;
  protected TaxAccountService taxAccountService;
  protected FiscalPositionAccountService fiscalPositionAccountService;
  protected AnalyticMoveLineService analyticMoveLineService;
  protected AppAccountService appAccountService;
  protected CurrencyService currencyService;
  protected CompanyConfigService companyConfigService;
  protected MoveLineRepository moveLineRepository;
  protected TaxPaymentMoveLineService taxPaymentMoveLineService;
  protected AnalyticMoveLineRepository analyticMoveLineRepository;

  public static final boolean IS_CREDIT = false;
  public static final boolean IS_DEBIT = true;

  @Inject
  public MoveLineService(
      AccountManagementAccountService accountManagementService,
      TaxAccountService taxAccountService,
      FiscalPositionAccountService fiscalPositionAccountService,
      AppAccountService appAccountService,
      AnalyticMoveLineService analyticMoveLineService,
      CurrencyService currencyService,
      CompanyConfigService companyConfigService,
      MoveLineRepository moveLineRepository,
      TaxPaymentMoveLineService taxPaymentMoveLineService,
      AnalyticMoveLineRepository analyticMoveLineRepository) {
    this.accountManagementService = accountManagementService;
    this.taxAccountService = taxAccountService;
    this.fiscalPositionAccountService = fiscalPositionAccountService;
    this.analyticMoveLineService = analyticMoveLineService;
    this.appAccountService = appAccountService;
    this.currencyService = currencyService;
    this.companyConfigService = companyConfigService;
    this.moveLineRepository = moveLineRepository;
    this.taxPaymentMoveLineService = taxPaymentMoveLineService;
    this.analyticMoveLineRepository = analyticMoveLineRepository;
  }

  public MoveLine computeAnalyticDistribution(MoveLine moveLine) {

    List<AnalyticMoveLine> analyticMoveLineList = moveLine.getAnalyticMoveLineList();

    if ((analyticMoveLineList == null || analyticMoveLineList.isEmpty())) {
      createAnalyticDistributionWithTemplate(moveLine);
    } else {
      LocalDate date = moveLine.getDate();
      BigDecimal amount = moveLine.getDebit().add(moveLine.getCredit());
      for (AnalyticMoveLine analyticMoveLine : analyticMoveLineList) {
        analyticMoveLineService.updateAnalyticMoveLine(analyticMoveLine, amount, date);
      }
    }
    updateAccountTypeOnAnalytic(moveLine, analyticMoveLineList);

    return moveLine;
  }

  public MoveLine createAnalyticDistributionWithTemplate(MoveLine moveLine) {

    List<AnalyticMoveLine> analyticMoveLineList =
        analyticMoveLineService.generateLines(
            moveLine.getAnalyticDistributionTemplate(),
            moveLine.getDebit().add(moveLine.getCredit()),
            AnalyticMoveLineRepository.STATUS_REAL_ACCOUNTING,
            moveLine.getDate());

    if (moveLine.getAnalyticMoveLineList() == null) {
      moveLine.setAnalyticMoveLineList(new ArrayList<>());
    } else {
      moveLine.getAnalyticMoveLineList().clear();
    }
    moveLine.getAnalyticMoveLineList().addAll(analyticMoveLineList);
    return moveLine;
  }

  public void updateAccountTypeOnAnalytic(
      MoveLine moveLine, List<AnalyticMoveLine> analyticMoveLineList) {

    if ((analyticMoveLineList == null || analyticMoveLineList.isEmpty())) {
      return;
    }

    for (AnalyticMoveLine analyticMoveLine : analyticMoveLineList) {
      if (moveLine.getAccount() != null) {
        analyticMoveLine.setAccount(moveLine.getAccount());
        analyticMoveLine.setAccountType(moveLine.getAccount().getAccountType());
      }
    }
  }

  public void generateAnalyticMoveLines(MoveLine moveLine) {

    List<AnalyticMoveLine> analyticMoveLineList =
        analyticMoveLineService.generateLines(
            moveLine.getAnalyticDistributionTemplate(),
            moveLine.getDebit().add(moveLine.getCredit()),
            AnalyticMoveLineRepository.STATUS_REAL_ACCOUNTING,
            moveLine.getDate());

    analyticMoveLineList.stream().forEach(moveLine::addAnalyticMoveLineListItem);
  }

  /**
   * Creating accounting move line method using move currency
   *
   * @param move
   * @param partner
   * @param account
   * @param amountInSpecificMoveCurrency
   * @param isDebit <code>true = debit</code>, <code>false = credit</code>
   * @param date
   * @param dueDate
   * @param counter
   * @param origin
   * @return
   * @throws AxelorException
   */
  public MoveLine createMoveLine(
      Move move,
      Partner partner,
      Account account,
      BigDecimal amountInSpecificMoveCurrency,
      boolean isDebit,
      LocalDate date,
      LocalDate dueDate,
      int counter,
      String origin,
      String description)
      throws AxelorException {

    log.debug(
        "Creating accounting move line (Account : {}, Amount in specific move currency : {}, debit ? : {}, date : {}, counter : {}, reference : {}",
        new Object[] {
          account.getName(), amountInSpecificMoveCurrency, isDebit, date, counter, origin
        });

    Currency currency = move.getCurrency();
    Currency companyCurrency = companyConfigService.getCompanyCurrency(move.getCompany());

    BigDecimal currencyRate =
        currencyService.getCurrencyConversionRate(currency, companyCurrency, date);

    BigDecimal amountConvertedInCompanyCurrency =
        currencyService.getAmountCurrencyConvertedUsingExchangeRate(
            amountInSpecificMoveCurrency, currencyRate);

    return this.createMoveLine(
        move,
        partner,
        account,
        amountInSpecificMoveCurrency,
        amountConvertedInCompanyCurrency,
        currencyRate,
        isDebit,
        date,
        dueDate,
        date,
        counter,
        origin,
        description);
  }

  /**
   * Creating accounting move line method using all currency informations (amount in specific move
   * currency, amount in company currency, currency rate)
   *
   * @param move
   * @param partner
   * @param account
   * @param amountInSpecificMoveCurrency
   * @param amountInCompanyCurrency
   * @param currencyRate
   * @param isDebit
   * @param date
   * @param dueDate
   * @param counter
   * @param origin
   * @return
   * @throws AxelorException
   */
  public MoveLine createMoveLine(
      Move move,
      Partner partner,
      Account account,
      BigDecimal amountInSpecificMoveCurrency,
      BigDecimal amountInCompanyCurrency,
      BigDecimal currencyRate,
      boolean isDebit,
      LocalDate date,
      LocalDate dueDate,
      LocalDate originDate,
      int counter,
      String origin,
      String description)
      throws AxelorException {

    amountInSpecificMoveCurrency = amountInSpecificMoveCurrency.abs();

    log.debug(
        "Creating accounting move line (Account : {}, Amount in specific move currency : {}, debit ? : {}, date : {}, counter : {}, reference : {}",
        new Object[] {
          account.getName(), amountInSpecificMoveCurrency, isDebit, date, counter, origin
        });

    if (partner != null) {
      account = fiscalPositionAccountService.getAccount(partner.getFiscalPosition(), account);
    }

    BigDecimal debit = BigDecimal.ZERO;
    BigDecimal credit = BigDecimal.ZERO;

    if (amountInCompanyCurrency.compareTo(BigDecimal.ZERO) == -1) {
      isDebit = !isDebit;
      amountInCompanyCurrency = amountInCompanyCurrency.negate();
    }

    if (isDebit) {
      debit = amountInCompanyCurrency;
    } else {
      credit = amountInCompanyCurrency;
    }

    if (currencyRate == null) {
      if (amountInSpecificMoveCurrency.compareTo(BigDecimal.ZERO) == 0) {
        currencyRate = BigDecimal.ONE;
      } else {
        currencyRate =
            amountInCompanyCurrency.divide(amountInSpecificMoveCurrency, 5, RoundingMode.HALF_EVEN);
      }
    }

    if (originDate == null) {
      originDate = date;
    }

    return new MoveLine(
        move,
        partner,
        account,
        date,
        dueDate,
        counter,
        debit,
        credit,
        StringTool.cutTooLongString(
            this.determineDescriptionMoveLine(move.getJournal(), origin, description)),
        origin,
        currencyRate.setScale(5, RoundingMode.HALF_EVEN),
        amountInSpecificMoveCurrency,
        originDate);
  }

  /**
   * Créer une ligne d'écriture comptable
   *
   * @param move
   * @param partner
   * @param account
   * @param amount
   * @param isDebit <code>true = débit</code>, <code>false = crédit</code>
   * @param date
   * @param ref
   * @param origin
   * @return
   * @throws AxelorException
   */
  public MoveLine createMoveLine(
      Move move,
      Partner partner,
      Account account,
      BigDecimal amount,
      boolean isDebit,
      LocalDate date,
      int ref,
      String origin,
      String description)
      throws AxelorException {

    return this.createMoveLine(
        move, partner, account, amount, isDebit, date, date, ref, origin, description);
  }

  /**
   * Créer les lignes d'écritures comptables d'une facture.
   *
   * @param invoice
   * @param move
   * @param consolidate
   * @return
   */
  public List<MoveLine> createMoveLines(
      Invoice invoice,
      Move move,
      Company company,
      Partner partner,
      Account partnerAccount,
      boolean consolidate,
      boolean isPurchase,
      boolean isDebitCustomer)
      throws AxelorException {

    log.debug(
        "Création des lignes d'écriture comptable de la facture/l'avoir {}",
        invoice.getInvoiceId());

    List<MoveLine> moveLines = new ArrayList<MoveLine>();

    Set<AnalyticAccount> analyticAccounts = new HashSet<AnalyticAccount>();

    int moveLineId = 1;

    if (partner == null) {
      throw new AxelorException(
          invoice,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.MOVE_LINE_1),
          invoice.getInvoiceId());
    }
    if (partnerAccount == null) {
      throw new AxelorException(
          invoice,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.MOVE_LINE_2),
          invoice.getInvoiceId());
    }

    String origin = invoice.getInvoiceId();

    if (InvoiceToolService.isPurchase(invoice)) {
      origin = invoice.getSupplierInvoiceNb();
    }

    // Creation of partner move line
    MoveLine moveLine1 =
        this.createMoveLine(
            move,
            partner,
            partnerAccount,
            invoice.getInTaxTotal(),
            invoice.getCompanyInTaxTotal(),
            null,
            isDebitCustomer,
            invoice.getInvoiceDate(),
            invoice.getDueDate(),
            invoice.getOriginDate(),
            moveLineId++,
            origin,
            null);
    moveLines.add(moveLine1);

    AnalyticMoveLineRepository analyticMoveLineRepository =
        Beans.get(AnalyticMoveLineRepository.class);

    // Creation of product move lines for each invoice line
    for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {

      BigDecimal companyExTaxTotal = invoiceLine.getCompanyExTaxTotal();

      if (companyExTaxTotal.compareTo(BigDecimal.ZERO) != 0) {

        analyticAccounts.clear();

        Account account = invoiceLine.getAccount();

        if (account == null) {
          throw new AxelorException(
              move,
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.MOVE_LINE_4),
              invoiceLine.getName(),
              company.getName());
        }

        companyExTaxTotal = invoiceLine.getCompanyExTaxTotal();

        log.debug(
            "Traitement de la ligne de facture : compte comptable = {}, montant = {}",
            new Object[] {account.getName(), companyExTaxTotal});

        if (invoiceLine.getAnalyticDistributionTemplate() == null
            && (invoiceLine.getAnalyticMoveLineList() == null
                || invoiceLine.getAnalyticMoveLineList().isEmpty())
            && account.getAnalyticDistributionAuthorized()
            && account.getAnalyticDistributionRequiredOnInvoiceLines()) {
          throw new AxelorException(
              move,
              TraceBackRepository.CATEGORY_MISSING_FIELD,
              I18n.get(IExceptionMessage.ANALYTIC_DISTRIBUTION_MISSING),
              invoiceLine.getName(),
              company.getName());
        }

        MoveLine moveLine =
            this.createMoveLine(
                move,
                partner,
                account,
                invoiceLine.getExTaxTotal(),
                companyExTaxTotal,
                null,
                !isDebitCustomer,
                invoice.getInvoiceDate(),
                null,
                invoice.getOriginDate(),
                moveLineId++,
                origin,
                invoiceLine.getProductName());

        moveLine.setAnalyticDistributionTemplate(invoiceLine.getAnalyticDistributionTemplate());
        if (invoiceLine.getAnalyticMoveLineList() != null
            && !invoiceLine.getAnalyticMoveLineList().isEmpty()) {
          for (AnalyticMoveLine invoiceAnalyticMoveLine : invoiceLine.getAnalyticMoveLineList()) {
            AnalyticMoveLine analyticMoveLine =
                analyticMoveLineRepository.copy(invoiceAnalyticMoveLine, false);
            analyticMoveLine.setTypeSelect(AnalyticMoveLineRepository.STATUS_REAL_ACCOUNTING);
            analyticMoveLine.setInvoiceLine(null);
            analyticMoveLine.setAccount(moveLine.getAccount());
            analyticMoveLine.setAccountType(moveLine.getAccount().getAccountType());
            analyticMoveLineService.updateAnalyticMoveLine(
                analyticMoveLine,
                moveLine.getDebit().add(moveLine.getCredit()),
                moveLine.getDate());
            moveLine.addAnalyticMoveLineListItem(analyticMoveLine);
          }
        } else {
          generateAnalyticMoveLines(moveLine);
        }

        TaxLine taxLine = invoiceLine.getTaxLine();
        if (taxLine != null) {
          moveLine.setTaxLine(taxLine);
          moveLine.setTaxRate(taxLine.getValue());
          moveLine.setTaxCode(taxLine.getTax().getCode());
        }

        moveLines.add(moveLine);
      }
    }

    // Creation of tax move lines for each invoice line tax
    for (InvoiceLineTax invoiceLineTax : invoice.getInvoiceLineTaxList()) {

      BigDecimal companyTaxTotal = invoiceLineTax.getCompanyTaxTotal();

      if (companyTaxTotal.compareTo(BigDecimal.ZERO) != 0) {

        Tax tax = invoiceLineTax.getTaxLine().getTax();
        boolean hasFixedAssets = !invoiceLineTax.getSubTotalOfFixedAssets().equals(BigDecimal.ZERO);
        boolean hasOtherAssets =
            !invoiceLineTax.getSubTotalExcludingFixedAssets().equals(BigDecimal.ZERO);
        Account account;
        MoveLine moveLine;
        if (hasFixedAssets
            && invoiceLineTax.getCompanySubTotalOfFixedAssets().compareTo(BigDecimal.ZERO) != 0) {
          account = taxAccountService.getAccount(tax, company, isPurchase, true);
          if (account == null) {
            throw new AxelorException(
                move,
                TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                I18n.get(IExceptionMessage.MOVE_LINE_6),
                tax.getName(),
                company.getName());
          }
          moveLine =
              this.createMoveLine(
                  move,
                  partner,
                  account,
                  invoiceLineTax.getSubTotalOfFixedAssets(),
                  invoiceLineTax.getCompanySubTotalOfFixedAssets(),
                  null,
                  !isDebitCustomer,
                  invoice.getInvoiceDate(),
                  null,
                  invoice.getOriginDate(),
                  moveLineId++,
                  origin,
                  null);

          moveLine.setTaxLine(invoiceLineTax.getTaxLine());
          moveLine.setTaxRate(invoiceLineTax.getTaxLine().getValue());
          moveLine.setTaxCode(tax.getCode());
          moveLines.add(moveLine);
        }

        if (hasOtherAssets
            && invoiceLineTax.getCompanySubTotalExcludingFixedAssets().compareTo(BigDecimal.ZERO)
                != 0) {
          account = taxAccountService.getAccount(tax, company, isPurchase, false);
          if (account == null) {
            throw new AxelorException(
                move,
                TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                I18n.get(IExceptionMessage.MOVE_LINE_6),
                tax.getName(),
                company.getName());
          }
          moveLine =
              this.createMoveLine(
                  move,
                  partner,
                  account,
                  invoiceLineTax.getSubTotalExcludingFixedAssets(),
                  invoiceLineTax.getCompanySubTotalExcludingFixedAssets(),
                  null,
                  !isDebitCustomer,
                  invoice.getInvoiceDate(),
                  null,
                  invoice.getOriginDate(),
                  moveLineId++,
                  origin,
                  null);
          moveLine.setTaxLine(invoiceLineTax.getTaxLine());
          moveLine.setTaxRate(invoiceLineTax.getTaxLine().getValue());
          moveLine.setTaxCode(tax.getCode());
          moveLines.add(moveLine);
        }
      }
    }

    if (consolidate) {
      this.consolidateMoveLines(moveLines);
    }

    return moveLines;
  }

  public MoveLine findConsolidateMoveLine(
      Map<List<Object>, MoveLine> map, MoveLine moveLine, List<Object> keys) {
    if (map != null && !map.isEmpty()) {

      Map<List<Object>, MoveLine> copyMap = new HashMap<List<Object>, MoveLine>(map);
      while (!copyMap.isEmpty()) {

        if (map.containsKey(keys)) {

          MoveLine moveLineIt = map.get(keys);
          int count = 0;
          if (moveLineIt.getAnalyticMoveLineList() == null
              && moveLine.getAnalyticMoveLineList() == null) {
            return moveLineIt;
          } else if (moveLineIt.getAnalyticMoveLineList() == null
              || moveLine.getAnalyticMoveLineList() == null) {
            break;
          }
          List<AnalyticMoveLine> list1 = moveLineIt.getAnalyticMoveLineList();
          List<AnalyticMoveLine> list2 = moveLine.getAnalyticMoveLineList();
          List<AnalyticMoveLine> copyList = new ArrayList<AnalyticMoveLine>(list1);
          if (list1.size() == list2.size()) {
            for (AnalyticMoveLine analyticDistributionLine : list2) {
              for (AnalyticMoveLine analyticDistributionLineIt : copyList) {
                if (analyticDistributionLine
                        .getAnalyticAxis()
                        .equals(analyticDistributionLineIt.getAnalyticAxis())
                    && analyticDistributionLine
                        .getAnalyticAccount()
                        .equals(analyticDistributionLineIt.getAnalyticAccount())
                    && analyticDistributionLine
                        .getAccount()
                        .equals(analyticDistributionLineIt.getAccount())
                    && analyticDistributionLine
                        .getPercentage()
                        .equals(analyticDistributionLineIt.getPercentage())
                    && ((analyticDistributionLine.getAnalyticJournal() == null
                            && analyticDistributionLineIt.getAnalyticJournal() == null)
                        || analyticDistributionLine
                            .getAnalyticJournal()
                            .equals(analyticDistributionLineIt.getAnalyticJournal()))) {
                  copyList.remove(analyticDistributionLineIt);
                  count++;
                  break;
                }
              }
            }
            if (count == list1.size()) {
              return moveLineIt;
            }
          }
        } else {
          return null;
        }
      }
    }

    return null;
  }

  /**
   * Consolider des lignes d'écritures par compte comptable.
   *
   * @param moveLines
   */
  public List<MoveLine> consolidateMoveLines(List<MoveLine> moveLines) {

    Map<List<Object>, MoveLine> map = new HashMap<List<Object>, MoveLine>();
    MoveLine consolidateMoveLine = null;

    for (MoveLine moveLine : moveLines) {

      List<Object> keys = new ArrayList<Object>();

      keys.add(moveLine.getAccount());
      keys.add(moveLine.getTaxLine());
      keys.add(moveLine.getAnalyticDistributionTemplate());

      consolidateMoveLine = this.findConsolidateMoveLine(map, moveLine, keys);
      if (consolidateMoveLine != null) {

        BigDecimal consolidateCurrencyAmount = BigDecimal.ZERO;

        log.debug(
            "MoveLine :: Debit : {}, Credit : {}, Currency amount : {}",
            moveLine.getDebit(),
            moveLine.getCredit(),
            moveLine.getCurrencyAmount());
        log.debug(
            "Consolidate moveLine :: Debit : {}, Credit : {}, Currency amount : {}",
            consolidateMoveLine.getDebit(),
            consolidateMoveLine.getCredit(),
            consolidateMoveLine.getCurrencyAmount());

        if (moveLine.getDebit().subtract(moveLine.getCredit()).compareTo(BigDecimal.ZERO)
            != consolidateMoveLine
                .getDebit()
                .subtract(consolidateMoveLine.getCredit())
                .compareTo(BigDecimal.ZERO)) {
          consolidateCurrencyAmount =
              consolidateMoveLine.getCurrencyAmount().subtract(moveLine.getCurrencyAmount());
        } else {
          consolidateCurrencyAmount =
              consolidateMoveLine.getCurrencyAmount().add(moveLine.getCurrencyAmount());
        }
        consolidateMoveLine.setCurrencyAmount(consolidateCurrencyAmount.abs());
        consolidateMoveLine.setCredit(consolidateMoveLine.getCredit().add(moveLine.getCredit()));
        consolidateMoveLine.setDebit(consolidateMoveLine.getDebit().add(moveLine.getDebit()));

        if (consolidateMoveLine.getAnalyticMoveLineList() != null
            && !consolidateMoveLine.getAnalyticMoveLineList().isEmpty()) {
          for (AnalyticMoveLine analyticDistributionLine :
              consolidateMoveLine.getAnalyticMoveLineList()) {
            for (AnalyticMoveLine analyticDistributionLineIt : moveLine.getAnalyticMoveLineList()) {
              if (analyticDistributionLine
                      .getAnalyticAxis()
                      .equals(analyticDistributionLineIt.getAnalyticAxis())
                  && analyticDistributionLine
                      .getAnalyticAccount()
                      .equals(analyticDistributionLineIt.getAnalyticAccount())
                  && analyticDistributionLine
                      .getAccount()
                      .equals(analyticDistributionLineIt.getAccount())
                  && analyticDistributionLine
                      .getPercentage()
                      .equals(analyticDistributionLineIt.getPercentage())
                  && ((analyticDistributionLine.getAnalyticJournal() == null
                          && analyticDistributionLineIt.getAnalyticJournal() == null)
                      || analyticDistributionLine
                          .getAnalyticJournal()
                          .equals(analyticDistributionLineIt.getAnalyticJournal()))) {
                analyticDistributionLine.setAmount(
                    analyticDistributionLine
                        .getAmount()
                        .add(analyticDistributionLineIt.getAmount()));
                break;
              }
            }
          }
        }
      } else {
        map.put(keys, moveLine);
      }
    }

    BigDecimal credit = null;
    BigDecimal debit = null;

    int moveLineId = 1;
    moveLines.clear();

    for (MoveLine moveLine : map.values()) {

      credit = moveLine.getCredit();
      debit = moveLine.getDebit();

      moveLine.setCurrencyAmount(moveLine.getCurrencyAmount().abs());

      if (debit.compareTo(BigDecimal.ZERO) == 1 && credit.compareTo(BigDecimal.ZERO) == 1) {

        if (debit.compareTo(credit) == 1) {
          moveLine.setDebit(debit.subtract(credit));
          moveLine.setCredit(BigDecimal.ZERO);
          moveLine.setCounter(moveLineId++);
          moveLines.add(moveLine);
        } else if (credit.compareTo(debit) == 1) {
          moveLine.setCredit(credit.subtract(debit));
          moveLine.setDebit(BigDecimal.ZERO);
          moveLine.setCounter(moveLineId++);
          moveLines.add(moveLine);
        }

      } else if (debit.compareTo(BigDecimal.ZERO) == 1 || credit.compareTo(BigDecimal.ZERO) == 1) {
        moveLine.setCounter(moveLineId++);
        moveLines.add(moveLine);
      }
    }

    return moveLines;
  }

  /**
   * Fonction permettant de récuperer la ligne d'écriture (au credit et non complétement lettrée sur
   * le compte client) de la facture
   *
   * @param invoice Une facture
   * @return
   */
  public MoveLine getCreditCustomerMoveLine(Invoice invoice) {
    if (invoice.getMove() != null) {
      return this.getCreditCustomerMoveLine(invoice.getMove());
    }
    return null;
  }

  /**
   * Fonction permettant de récuperer la ligne d'écriture (au credit et non complétement lettrée sur
   * le compte client) de l'écriture de facture
   *
   * @param move Une écriture de facture
   * @return
   */
  public MoveLine getCreditCustomerMoveLine(Move move) {
    for (MoveLine moveLine : move.getMoveLineList()) {
      if (moveLine.getAccount().getUseForPartnerBalance()
          && moveLine.getCredit().compareTo(BigDecimal.ZERO) > 0
          && moveLine.getAmountRemaining().compareTo(BigDecimal.ZERO) > 0) {
        return moveLine;
      }
    }
    return null;
  }

  /**
   * Fonction permettant de récuperer la ligne d'écriture (au débit et non complétement lettrée sur
   * le compte client) de la facture
   *
   * @param invoice Une facture
   * @return
   */
  public MoveLine getDebitCustomerMoveLine(Invoice invoice) {
    if (invoice.getMove() != null) {
      return this.getDebitCustomerMoveLine(invoice.getMove());
    }
    return null;
  }

  /**
   * Fonction permettant de récuperer la ligne d'écriture (au débit et non complétement lettrée sur
   * le compte client) de l'écriture de facture
   *
   * @param move Une écriture de facture
   * @return
   */
  public MoveLine getDebitCustomerMoveLine(Move move) {
    for (MoveLine moveLine : move.getMoveLineList()) {
      if (moveLine.getAccount().getUseForPartnerBalance()
          && moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0
          && moveLine.getAmountRemaining().compareTo(BigDecimal.ZERO) > 0) {
        return moveLine;
      }
    }
    return null;
  }

  /**
   * Fonction permettant de générér automatiquement la description des lignes d'écritures
   *
   * @param journal Le journal de l'écriture
   * @param origin Le n° pièce réglée, facture, avoir ou de l'opération rejetée
   * @return
   */
  public String determineDescriptionMoveLine(Journal journal, String origin, String description) {
    String descriptionComputed = "";
    if (journal == null) {
      return "";
    }

    if (journal.getDescriptionModel() != null) {
      descriptionComputed += journal.getDescriptionModel();
    }

    if (journal.getDescriptionIdentificationOk() && origin != null) {
      if (!descriptionComputed.isEmpty()) {
        descriptionComputed += " ";
      }
      descriptionComputed += origin;
    }

    if (!journal.getIsInvoiceMoveConsolidated() && description != null) {
      if (!descriptionComputed.isEmpty()) {
        descriptionComputed += " - ";
      }
      descriptionComputed += description;
    }
    return descriptionComputed;
  }

  /**
   * Procédure permettant d'impacter la case à cocher "Passage à l'huissier" sur la facture liée à
   * l'écriture
   *
   * @param moveLine Une ligne d'écriture
   */
  @Transactional
  public void usherProcess(MoveLine moveLine) {

    Invoice invoice = moveLine.getMove().getInvoice();
    if (invoice != null) {
      if (moveLine.getUsherPassageOk()) {
        invoice.setUsherPassageOk(true);
      } else {
        invoice.setUsherPassageOk(false);
      }
      Beans.get(InvoiceRepository.class).save(invoice);
    }
  }

  /**
   * Method used to recover all credit reconciliable move line from a move line list
   *
   * @param moveLineList
   * @return reconciliableCreditMoveLineList
   */
  public List<MoveLine> getReconciliableCreditMoveLines(List<MoveLine> moveLineList) {

    List<MoveLine> reconciliableCreditMoveLineList = new ArrayList<>();

    for (MoveLine moveLine : moveLineList) {
      if (moveLine.getAccount().getReconcileOk()
          && moveLine.getCredit().compareTo(BigDecimal.ZERO) > 0
          && moveLine.getDebit().compareTo(BigDecimal.ZERO) == 0) {
        reconciliableCreditMoveLineList.add(moveLine);
      }
    }

    return reconciliableCreditMoveLineList;
  }

  /**
   * Method used to recover all debit reconciliable move line from a move line list
   *
   * @param moveLineList
   * @return reconciliableDebitMoveLineList
   */
  public List<MoveLine> getReconciliableDebitMoveLines(List<MoveLine> moveLineList) {

    List<MoveLine> reconciliableDebitMoveLineList = new ArrayList<>();

    for (MoveLine moveLine : moveLineList) {
      if (moveLine.getAccount().getReconcileOk()
          && moveLine.getCredit().compareTo(BigDecimal.ZERO) == 0
          && moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0) {
        reconciliableDebitMoveLineList.add(moveLine);
      }
    }

    return reconciliableDebitMoveLineList;
  }

  /**
   * Method used to reconcile the move line list passed as a parameter
   *
   * @param moveLineList
   */
  public void reconcileMoveLinesWithCacheManagement(List<MoveLine> moveLineList) {

    List<MoveLine> reconciliableCreditMoveLineList = getReconciliableCreditMoveLines(moveLineList);
    List<MoveLine> reconciliableDebitMoveLineList = getReconciliableDebitMoveLines(moveLineList);

    Map<List<Object>, Pair<List<MoveLine>, List<MoveLine>>> moveLineMap = new HashMap<>();

    populateCredit(moveLineMap, reconciliableCreditMoveLineList);

    populateDebit(moveLineMap, reconciliableDebitMoveLineList);

    Comparator<MoveLine> byDate = Comparator.comparing(MoveLine::getDate);

    PaymentService paymentService = Beans.get(PaymentService.class);

    for (Pair<List<MoveLine>, List<MoveLine>> moveLineLists : moveLineMap.values()) {
      try {
        moveLineLists = this.findMoveLineLists(moveLineLists);
        this.useExcessPaymentOnMoveLinesDontThrow(byDate, paymentService, moveLineLists);
      } catch (Exception e) {
        TraceBackService.trace(e);
        log.debug(e.getMessage());
      } finally {
        JPA.clear();
      }
    }
  }

  protected Pair<List<MoveLine>, List<MoveLine>> findMoveLineLists(
      Pair<List<MoveLine>, List<MoveLine>> moveLineLists) {
    List<MoveLine> fetchedDebitMoveLineList =
        moveLineLists
            .getLeft()
            .stream()
            .map(moveLine -> moveLineRepository.find(moveLine.getId()))
            .collect(Collectors.toList());
    List<MoveLine> fetchedCreditMoveLineList =
        moveLineLists
            .getRight()
            .stream()
            .map(moveLine -> moveLineRepository.find(moveLine.getId()))
            .collect(Collectors.toList());
    return Pair.of(fetchedDebitMoveLineList, fetchedCreditMoveLineList);
  }

  @Transactional
  public void reconcileMoveLines(List<MoveLine> moveLineList) {
    List<MoveLine> reconciliableCreditMoveLineList = getReconciliableCreditMoveLines(moveLineList);
    List<MoveLine> reconciliableDebitMoveLineList = getReconciliableDebitMoveLines(moveLineList);

    Map<List<Object>, Pair<List<MoveLine>, List<MoveLine>>> moveLineMap = new HashMap<>();

    populateCredit(moveLineMap, reconciliableCreditMoveLineList);

    populateDebit(moveLineMap, reconciliableDebitMoveLineList);

    Comparator<MoveLine> byDate = Comparator.comparing(MoveLine::getDate);

    PaymentService paymentService = Beans.get(PaymentService.class);

    for (Pair<List<MoveLine>, List<MoveLine>> moveLineLists : moveLineMap.values()) {
      List<MoveLine> companyPartnerCreditMoveLineList = moveLineLists.getLeft();
      List<MoveLine> companyPartnerDebitMoveLineList = moveLineLists.getRight();
      companyPartnerCreditMoveLineList.sort(byDate);
      companyPartnerDebitMoveLineList.sort(byDate);
      paymentService.useExcessPaymentOnMoveLinesDontThrow(
          companyPartnerDebitMoveLineList, companyPartnerCreditMoveLineList);
    }
  }

  @Transactional
  protected void useExcessPaymentOnMoveLinesDontThrow(
      Comparator<MoveLine> byDate,
      PaymentService paymentService,
      Pair<List<MoveLine>, List<MoveLine>> moveLineLists) {
    List<MoveLine> companyPartnerCreditMoveLineList = moveLineLists.getLeft();
    List<MoveLine> companyPartnerDebitMoveLineList = moveLineLists.getRight();
    companyPartnerCreditMoveLineList.sort(byDate);
    companyPartnerDebitMoveLineList.sort(byDate);
    paymentService.useExcessPaymentOnMoveLinesDontThrow(
        companyPartnerDebitMoveLineList, companyPartnerCreditMoveLineList);
  }

  private void populateCredit(
      Map<List<Object>, Pair<List<MoveLine>, List<MoveLine>>> moveLineMap,
      List<MoveLine> reconciliableMoveLineList) {
    populateMoveLineMap(moveLineMap, reconciliableMoveLineList, true);
  }

  private void populateDebit(
      Map<List<Object>, Pair<List<MoveLine>, List<MoveLine>>> moveLineMap,
      List<MoveLine> reconciliableMoveLineList) {
    populateMoveLineMap(moveLineMap, reconciliableMoveLineList, false);
  }

  private void populateMoveLineMap(
      Map<List<Object>, Pair<List<MoveLine>, List<MoveLine>>> moveLineMap,
      List<MoveLine> reconciliableMoveLineList,
      boolean isCredit) {
    for (MoveLine moveLine : reconciliableMoveLineList) {

      Move move = moveLine.getMove();

      List<Object> keys = new ArrayList<Object>();

      keys.add(move.getCompany());
      keys.add(moveLine.getAccount());
      keys.add(moveLine.getPartner());

      Pair<List<MoveLine>, List<MoveLine>> moveLineLists = moveLineMap.get(keys);

      if (moveLineLists == null) {
        moveLineLists = Pair.of(new ArrayList<>(), new ArrayList<>());
        moveLineMap.put(keys, moveLineLists);
      }

      List<MoveLine> moveLineList = isCredit ? moveLineLists.getLeft() : moveLineLists.getRight();
      moveLineList.add(moveLine);
    }
  }

  public void autoTaxLineGenerate(Move move) throws AxelorException {

    List<MoveLine> moveLineList = move.getMoveLineList();

    moveLineList.sort(
        new Comparator<MoveLine>() {
          @Override
          public int compare(MoveLine o1, MoveLine o2) {
            if (o2.getSourceTaxLine() != null) {
              return 0;
            }
            return -1;
          }
        });

    Iterator<MoveLine> moveLineItr = moveLineList.iterator();

    Map<String, MoveLine> map = new HashMap<>();
    Map<String, MoveLine> newMap = new HashMap<>();

    while (moveLineItr.hasNext()) {

      MoveLine moveLine = moveLineItr.next();

      TaxLine taxLine = moveLine.getTaxLine();
      TaxLine sourceTaxLine = moveLine.getSourceTaxLine();

      if (sourceTaxLine != null) {

        String sourceTaxLineKey = moveLine.getAccount().getCode() + sourceTaxLine.getId();

        moveLine.setCredit(BigDecimal.ZERO);
        moveLine.setDebit(BigDecimal.ZERO);
        map.put(sourceTaxLineKey, moveLine);
        moveLineItr.remove();
        continue;
      }

      if (taxLine != null) {

        String accountType = moveLine.getAccount().getAccountType().getTechnicalTypeSelect();

        if (accountType.equals(AccountTypeRepository.TYPE_DEBT)
            || accountType.equals(AccountTypeRepository.TYPE_CHARGE)
            || accountType.equals(AccountTypeRepository.TYPE_INCOME)
            || accountType.equals(AccountTypeRepository.TYPE_ASSET)) {

          BigDecimal debit = moveLine.getDebit();
          BigDecimal credit = moveLine.getCredit();
          LocalDate date = moveLine.getDate();
          Company company = move.getCompany();

          MoveLine newOrUpdatedMoveLine = new MoveLine();

          if (accountType.equals(AccountTypeRepository.TYPE_DEBT)
              || accountType.equals(AccountTypeRepository.TYPE_CHARGE)) {
            newOrUpdatedMoveLine.setAccount(
                taxAccountService.getAccount(taxLine.getTax(), company, true, false));
          } else if (accountType.equals(AccountTypeRepository.TYPE_INCOME)) {
            newOrUpdatedMoveLine.setAccount(
                taxAccountService.getAccount(taxLine.getTax(), company, false, false));
          } else if (accountType.equals(AccountTypeRepository.TYPE_ASSET)) {
            newOrUpdatedMoveLine.setAccount(
                taxAccountService.getAccount(taxLine.getTax(), company, true, true));
          }

          Account newAccount = newOrUpdatedMoveLine.getAccount();

          if (newAccount == null) {
            throw new AxelorException(
                move,
                TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                I18n.get(IExceptionMessage.MOVE_LINE_6),
                taxLine.getName(),
                company.getName());
          }

          String newSourceTaxLineKey = newAccount.getCode() + taxLine.getId();

          if (!map.containsKey(newSourceTaxLineKey) && !newMap.containsKey(newSourceTaxLineKey)) {

            newOrUpdatedMoveLine =
                this.createNewMoveLine(
                    debit, credit, date, accountType, taxLine, newOrUpdatedMoveLine);
          } else {

            if (newMap.containsKey(newSourceTaxLineKey)) {
              newOrUpdatedMoveLine = newMap.get(newSourceTaxLineKey);
            } else if (!newMap.containsKey(newSourceTaxLineKey)
                && map.containsKey(newSourceTaxLineKey)) {
              newOrUpdatedMoveLine = map.get(newSourceTaxLineKey);
            }
            newOrUpdatedMoveLine.setDebit(
                newOrUpdatedMoveLine.getDebit().add(debit.multiply(taxLine.getValue())));
            newOrUpdatedMoveLine.setCredit(
                newOrUpdatedMoveLine.getCredit().add(credit.multiply(taxLine.getValue())));
          }

          newMap.put(newSourceTaxLineKey, newOrUpdatedMoveLine);
        }
      }
    }

    moveLineList.addAll(newMap.values());
  }

  public MoveLine createNewMoveLine(
      BigDecimal debit,
      BigDecimal credit,
      LocalDate date,
      String accountType,
      TaxLine taxLine,
      MoveLine newOrUpdatedMoveLine) {

    newOrUpdatedMoveLine.setSourceTaxLine(taxLine);
    newOrUpdatedMoveLine.setDebit(debit.multiply(taxLine.getValue()));
    newOrUpdatedMoveLine.setCredit(credit.multiply(taxLine.getValue()));
    newOrUpdatedMoveLine.setDescription(taxLine.getTax().getName());
    newOrUpdatedMoveLine.setDate(date);

    return newOrUpdatedMoveLine;
  }

  public void validateMoveLine(MoveLine moveLine) throws AxelorException {
    if (moveLine.getDebit().compareTo(BigDecimal.ZERO) == 0
        && moveLine.getCredit().compareTo(BigDecimal.ZERO) == 0
        && moveLine.getCurrencyAmount().compareTo(BigDecimal.ZERO) == 0) {
      throw new AxelorException(
          moveLine,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.MOVE_LINE_7),
          moveLine.getAccount().getCode());
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public MoveLine generateTaxPaymentMoveLineList(
      MoveLine customerMoveLine, Invoice invoice, Reconcile reconcile) throws AxelorException {
    BigDecimal paymentAmount = reconcile.getAmount();
    BigDecimal invoiceTotalAmount = invoice.getCompanyInTaxTotal();
    for (InvoiceLineTax invoiceLineTax : invoice.getInvoiceLineTaxList()) {

      TaxLine taxLine = invoiceLineTax.getTaxLine();
      BigDecimal vatRate = taxLine.getValue();
      BigDecimal baseAmount = invoiceLineTax.getCompanyExTaxBase();
      BigDecimal detailPaymentAmount =
          baseAmount
              .multiply(paymentAmount)
              .divide(invoiceTotalAmount, 6, RoundingMode.HALF_EVEN)
              .setScale(2, RoundingMode.HALF_UP);

      TaxPaymentMoveLine taxPaymentMoveLine =
          new TaxPaymentMoveLine(
              customerMoveLine,
              taxLine,
              reconcile,
              vatRate,
              detailPaymentAmount,
              Beans.get(AppBaseService.class).getTodayDate());

      taxPaymentMoveLine = taxPaymentMoveLineService.computeTaxAmount(taxPaymentMoveLine);

      customerMoveLine.addTaxPaymentMoveLineListItem(taxPaymentMoveLine);
    }
    this.computeTaxAmount(customerMoveLine);
    return Beans.get(MoveLineRepository.class).save(customerMoveLine);
  }

  @Transactional(rollbackOn = {Exception.class})
  public MoveLine reverseTaxPaymentMoveLines(MoveLine customerMoveLine, Reconcile reconcile)
      throws AxelorException {
    List<TaxPaymentMoveLine> reverseTaxPaymentMoveLines = new ArrayList<TaxPaymentMoveLine>();
    for (TaxPaymentMoveLine taxPaymentMoveLine : customerMoveLine.getTaxPaymentMoveLineList()) {
      if (!taxPaymentMoveLine.getIsAlreadyReverse()
          && taxPaymentMoveLine.getReconcile().equals(reconcile)) {
        TaxPaymentMoveLine reverseTaxPaymentMoveLine =
            taxPaymentMoveLineService.getReverseTaxPaymentMoveLine(taxPaymentMoveLine);

        reverseTaxPaymentMoveLines.add(reverseTaxPaymentMoveLine);
      }
    }
    for (TaxPaymentMoveLine reverseTaxPaymentMoveLine : reverseTaxPaymentMoveLines) {
      customerMoveLine.addTaxPaymentMoveLineListItem(reverseTaxPaymentMoveLine);
    }
    this.computeTaxAmount(customerMoveLine);
    return Beans.get(MoveLineRepository.class).save(customerMoveLine);
  }

  @Transactional(rollbackOn = {Exception.class})
  public MoveLine computeTaxAmount(MoveLine moveLine) throws AxelorException {
    moveLine.setTaxAmount(BigDecimal.ZERO);
    if (!ObjectUtils.isEmpty(moveLine.getTaxPaymentMoveLineList())) {
      for (TaxPaymentMoveLine taxPaymentMoveLine : moveLine.getTaxPaymentMoveLineList()) {
        moveLine.setTaxAmount(moveLine.getTaxAmount().add(taxPaymentMoveLine.getTaxAmount()));
      }
    }
    return moveLine;
  }

  @Transactional(rollbackOn = {Exception.class})
  public MoveLine cleanAnalytic(MoveLine moveLine) throws AxelorException {
    moveLine.setAnalyticDistributionTemplate(null);
    moveLine.clearAnalyticMoveLineList();
    return moveLine;
  }
}
