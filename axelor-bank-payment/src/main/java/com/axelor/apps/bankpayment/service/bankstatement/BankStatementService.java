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
package com.axelor.apps.bankpayment.service.bankstatement;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.bankpayment.db.BankStatement;
import com.axelor.apps.bankpayment.db.BankStatementFileFormat;
import com.axelor.apps.bankpayment.db.BankStatementLine;
import com.axelor.apps.bankpayment.db.BankStatementLineAFB120;
import com.axelor.apps.bankpayment.db.BankStatementRule;
import com.axelor.apps.bankpayment.db.repo.BankStatementFileFormatRepository;
import com.axelor.apps.bankpayment.db.repo.BankStatementLineAFB120Repository;
import com.axelor.apps.bankpayment.db.repo.BankStatementLineRepository;
import com.axelor.apps.bankpayment.db.repo.BankStatementRepository;
import com.axelor.apps.bankpayment.db.repo.BankStatementRuleRepository;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.apps.bankpayment.report.IReport;
import com.axelor.apps.bankpayment.service.bankstatement.file.afb120.BankStatementFileAFB120Service;
import com.axelor.apps.base.db.repo.YearBaseRepository;
import com.axelor.apps.base.service.PeriodService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.db.JPA;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.Context;
import com.axelor.script.GroovyScriptHelper;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.util.List;

public class BankStatementService {

  protected BankStatementRepository bankStatementRepository;
  protected PeriodService periodService;
  protected MoveRepository moveRepository;
  protected MoveLineRepository moveLineRepository;
  protected BankStatementLineRepository bankStatementLineRepository;
  protected BankStatementLineAFB120Repository bankStatementLineAFB120Repository;
  protected BankStatementRuleRepository bankStatementRuleRepository;
  protected MoveService moveService;

  @Inject
  public BankStatementService(
      BankStatementRepository bankStatementRepository,
      PeriodService periodService,
      MoveRepository moveRepository,
      MoveLineRepository moveLineRepository,
      BankStatementLineRepository bankStatementLineRepository,
      BankStatementLineAFB120Repository bankStatementLineAFB120Repository,
      BankStatementRuleRepository bankStatementRuleRepository,
      MoveService moveService) {
    this.bankStatementRepository = bankStatementRepository;
    this.periodService = periodService;
    this.moveRepository = moveRepository;
    this.moveLineRepository = moveLineRepository;
    this.bankStatementLineRepository = bankStatementLineRepository;
    this.bankStatementLineAFB120Repository = bankStatementLineAFB120Repository;
    this.bankStatementRuleRepository = bankStatementRuleRepository;
    this.moveService = moveService;
  }

  public void runImport(BankStatement bankStatement, boolean alertIfFormatNotSupported)
      throws IOException, AxelorException {

    bankStatement = find(bankStatement);

    if (bankStatement.getBankStatementFile() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.BANK_STATEMENT_MISSING_FILE));
    }

    if (bankStatement.getBankStatementFileFormat() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.BANK_STATEMENT_MISSING_FILE_FORMAT));
    }

    BankStatementFileFormat bankStatementFileFormat = bankStatement.getBankStatementFileFormat();

    switch (bankStatementFileFormat.getStatementFileFormatSelect()) {
      case BankStatementFileFormatRepository.FILE_FORMAT_CAMT_XXX_CFONB120_REP:
      case BankStatementFileFormatRepository.FILE_FORMAT_CAMT_XXX_CFONB120_STM:
        Beans.get(BankStatementFileAFB120Service.class).process(bankStatement);
        updateStatus(bankStatement);
        break;

      default:
        if (alertIfFormatNotSupported) {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(IExceptionMessage.BANK_STATEMENT_FILE_UNKNOWN_FORMAT));
        }
    }
  }

  @Transactional
  public void updateStatus(BankStatement bankStatement) {
    bankStatement = find(bankStatement);
    bankStatement.setStatusSelect(BankStatementRepository.STATUS_IMPORTED);
    bankStatementRepository.save(bankStatement);
  }

  /**
   * Print bank statement.
   *
   * @param bankStatement
   * @return
   * @throws AxelorException
   */
  public String print(BankStatement bankStatement) throws AxelorException {
    String reportName;

    switch (bankStatement.getBankStatementFileFormat().getStatementFileFormatSelect()) {
      case BankStatementFileFormatRepository.FILE_FORMAT_CAMT_XXX_CFONB120_REP:
      case BankStatementFileFormatRepository.FILE_FORMAT_CAMT_XXX_CFONB120_STM:
        reportName = IReport.BANK_STATEMENT_AFB120;
        break;
      default:
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.BANK_STATEMENT_FILE_UNKNOWN_FORMAT));
    }

    return ReportFactory.createReport(reportName, bankStatement.getName() + "-${date}")
        .addParam("BankStatementId", bankStatement.getId())
        .addParam("Locale", ReportSettings.getPrintingLocale(null))
        .addParam("Timezone", getTimezone(bankStatement))
        .addFormat("pdf")
        .toAttach(bankStatement)
        .generate()
        .getFileLink();
  }

  private String getTimezone(BankStatement bankStatement) {
    if (bankStatement.getEbicsPartner() == null
        || bankStatement.getEbicsPartner().getDefaultSignatoryEbicsUser() == null
        || bankStatement.getEbicsPartner().getDefaultSignatoryEbicsUser().getAssociatedUser()
            == null
        || bankStatement
                .getEbicsPartner()
                .getDefaultSignatoryEbicsUser()
                .getAssociatedUser()
                .getActiveCompany()
            == null) {
      return null;
    }
    return bankStatement
        .getEbicsPartner()
        .getDefaultSignatoryEbicsUser()
        .getAssociatedUser()
        .getActiveCompany()
        .getTimezone();
  }

  /**
   * Finds bank statement.
   *
   * @param bankStatement
   * @return
   */
  public BankStatement find(BankStatement bankStatement) {
    return JPA.em().contains(bankStatement)
        ? bankStatement
        : bankStatementRepository.find(bankStatement.getId());
  }

  public void generateMoves(BankStatement bankStatement) {
    Context scriptContext;
    Move move;
    List<BankStatementLineAFB120> bankStatementLines =
        bankStatementLineAFB120Repository
            .all()
            .filter(
                "self.lineTypeSelect = :lineTypeSelect AND self.bankStatement = :bankStatement AND self.moveLine IS NULL")
            .bind("lineTypeSelect", BankStatementLineAFB120Repository.LINE_TYPE_MOVEMENT)
            .bind("bankStatement", bankStatement)
            .fetch();

    List<BankStatementRule> bankStatementRules;

    for (BankStatementLineAFB120 bankStatementLineAFB120 : bankStatementLines) {
      scriptContext =
          new Context(
              Mapper.toMap(bankStatementLineAFB120), BankStatementLineAFB120.class.getClass());
      bankStatementRules =
          bankStatementRuleRepository
              .all()
              .filter(
                  "self.ruleType = :ruleType AND self.accountManagement.interbankCodeLine = :interbankCodeLine")
              .bind("ruleType", BankStatementRuleRepository.RULE_TYPE_ACCOUNTING_AUTO)
              .bind("interbankCodeLine", bankStatementLineAFB120.getOperationInterbankCodeLine())
              .fetch();
      for (BankStatementRule bankStatementRule : bankStatementRules) {
        if (Boolean.TRUE.equals(
            new GroovyScriptHelper(scriptContext)
                .eval(
                    bankStatementRule
                        .getBankStatementQuery()
                        .getQuery()
                        .replaceAll("%s", "'" + bankStatementRule.getSearchLabel() + "'")))) {
          if (bankStatementRule.getAccountManagement().getJournal() == null) continue;
          move = generateMove(bankStatementLineAFB120, bankStatementRule);
          try {
            moveService.getMoveValidateService().validate(move);
          } catch (AxelorException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          break;
        }
      }
    }
  }

  @Transactional
  public Move generateMove(
      BankStatementLine bankStatementLine, BankStatementRule bankStatementRule) {
    if (bankStatementRule == null) {
      bankStatementRule = Beans.get(BankStatementRuleRepository.class).all().fetchOne();
    }
    Move move = new Move();
    move.setCompany(bankStatementRule.getAccountManagement().getCompany());
    move.setJournal(bankStatementRule.getAccountManagement().getJournal());
    move.setPeriod(
        periodService.getPeriod(
            bankStatementLine.getValueDate(), move.getCompany(), YearBaseRepository.TYPE_FISCAL));
    move.setDate(bankStatementLine.getValueDate());
    if (bankStatementLine.getOperationDate() != null) {}

    move.setPartner(bankStatementRule.getPartner());
    move.setCurrency(bankStatementLine.getCurrency());
    move.setPaymentMode(bankStatementRule.getAccountManagement().getPaymentMode());
    move.setTechnicalOriginSelect(MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC);
    move.setFunctionalOriginSelect(MoveRepository.FUNCTIONAL_ORIGIN_PAYMENT);
    move.clearMoveLineList();
    MoveLine moveLine = generateMoveLine(bankStatementLine, bankStatementRule, move, true);
    move.addMoveLineListItem(moveLine);
    moveLine = generateMoveLine(bankStatementLine, bankStatementRule, move, false);
    moveLine = moveLineRepository.save(moveLine);
    move.addMoveLineListItem(moveLine);
    bankStatementLine.setMoveLine(moveLine);

    return moveRepository.save(move);
  }

  protected MoveLine generateMoveLine(
      BankStatementLine bankStatementLine,
      BankStatementRule bankStatementRule,
      Move move,
      boolean isFirstLine) {
    MoveLine moveLine = new MoveLine();
    moveLine.setMove(move);
    moveLine.setDate(bankStatementLine.getValueDate());
    if (isFirstLine) {
      if (move.getCurrency().equals(move.getCompany().getCurrency())) {
        moveLine.setDebit(bankStatementLine.getCredit());
        moveLine.setCredit(bankStatementLine.getDebit());
      } else {
        // TODO MODIFY to fit spec
        moveLine.setDebit(bankStatementLine.getCredit());
        moveLine.setCredit(bankStatementLine.getDebit());
      }
      moveLine.setAccount(bankStatementRule.getCounterpartAccount());
    } else {
      if (move.getCurrency().equals(move.getCompany().getCurrency())) {
        moveLine.setDebit(bankStatementLine.getDebit());
        moveLine.setCredit(bankStatementLine.getCredit());
      } else {
        // TODO Modify to fit spec
        moveLine.setDebit(bankStatementLine.getDebit());
        moveLine.setCredit(bankStatementLine.getCredit());
      }
      moveLine.setAccount(bankStatementRule.getAccountManagement().getCashAccount());
    }

    moveLine.setOrigin(bankStatementLine.getOrigin());
    moveLine.setDescription(bankStatementLine.getDescription());

    return moveLine;
  }
}
