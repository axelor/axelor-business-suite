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
package com.axelor.apps.account.service.batch;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.AccountRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountingService;
import com.axelor.apps.account.service.debtrecovery.DoubtfulCustomerService;
import com.axelor.apps.base.db.Company;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchDoubtfulCustomer extends BatchStrategy {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected boolean stop = false;

  protected String updateCustomerAccountLog = "";

  protected AccountRepository accountRepo;

  @Inject
  public BatchDoubtfulCustomer(
      DoubtfulCustomerService doubtfulCustomerService,
      BatchAccountCustomer batchAccountCustomer,
      AccountRepository accountRepo) {

    super(doubtfulCustomerService, batchAccountCustomer);

    this.accountRepo = accountRepo;

    AccountingService.setUpdateCustomerAccount(false);
  }

  @Override
  protected void start() throws IllegalAccessException {

    super.start();

    Company company = batch.getAccountingBatch().getCompany();

    try {

      doubtfulCustomerService.testCompanyField(company);

    } catch (AxelorException e) {

      TraceBackService.trace(
          new AxelorException(e, e.getCategory(), ""),
          ExceptionOriginRepository.DOUBTFUL_CUSTOMER,
          batch.getId());
      incrementAnomaly();
      stop = true;
    }

    checkPoint();
  }

  @Override
  protected void process() {

    if (!stop) {
      Company company = batch.getAccountingBatch().getCompany();

      AccountConfig accountConfig = company.getAccountConfig();

      Account doubtfulCustomerAccount = accountConfig.getDoubtfulCustomerAccount();
      String sixMonthDebtPassReason = accountConfig.getSixMonthDebtPassReason();
      String threeMonthDebtPassReason = accountConfig.getThreeMonthDebtPassReason();
      int fetchLimit = getFetchLimit();

      // FACTURES

      this.createDoubtFulCustomerMove(
          0, company, doubtfulCustomerAccount, sixMonthDebtPassReason, fetchLimit);

      this.createDoubtFulCustomerMove(
          1, company, doubtfulCustomerAccount, threeMonthDebtPassReason, fetchLimit);

      // FACTURES REJETES
      this.createDoubtFulCustomerRejectMove(
          0, company, doubtfulCustomerAccount, sixMonthDebtPassReason, fetchLimit);

      this.createDoubtFulCustomerRejectMove(
          1, company, doubtfulCustomerAccount, threeMonthDebtPassReason, fetchLimit);

      updateCustomerAccountLog +=
          batchAccountCustomer.updateAccountingSituationMarked(companyRepo.find(company.getId()));
    }
  }

  /**
   * Procédure permettant de créer les écritures de passage en client douteux pour chaque écriture
   * de facture
   *
   * @param moveLineList Une liste d'écritures de facture
   * @param doubtfulCustomerAccount Un compte client douteux
   * @param debtPassReason Un motif de passage en client douteux
   * @throws AxelorException
   */
  public void createDoubtFulCustomerMove(
      int rule,
      Company company,
      Account doubtfulCustomerAccount,
      String debtPassReason,
      int fetchLimit) {

    int i = 0;
    int position = 0;
    List<Move> moveList = null;
    while (!(moveList =
            doubtfulCustomerService.getMove(
                rule, doubtfulCustomerAccount, company, fetchLimit, position))
        .isEmpty()) {
      position += moveList.size();
      String logMsg =
          rule == 0
              ? "Nombre d'écritures de facture concernées (Créance de + 6 mois) au 411 : {} "
              : "Nombre d'écritures de facture concernées (Créance de + 3 mois) au 411 : {} ";
      log.debug(logMsg, moveList.size());
      for (Move move : moveList) {

        try {
          doubtfulCustomerService.createDoubtFulCustomerMove(
              moveRepo.find(move.getId()),
              accountRepo.find(doubtfulCustomerAccount.getId()),
              debtPassReason);
          updateInvoice(moveRepo.find(move.getId()).getInvoice());

        } catch (AxelorException e) {

          TraceBackService.trace(
              new AxelorException(
                  e,
                  e.getCategory(),
                  I18n.get("Invoice") + " %s",
                  move.getInvoice().getInvoiceId()),
              ExceptionOriginRepository.DOUBTFUL_CUSTOMER,
              batch.getId());
          incrementAnomaly();

        } catch (Exception e) {

          TraceBackService.trace(
              new Exception(
                  String.format(I18n.get("Invoice") + " %s", move.getInvoice().getInvoiceId()), e),
              ExceptionOriginRepository.DOUBTFUL_CUSTOMER,
              batch.getId());

          incrementAnomaly();

          log.error(
              "Bug(Anomalie) généré(e) pour la facture {}",
              moveRepo.find(move.getId()).getInvoice().getInvoiceId());

        } finally {

          if (i % 10 == 0) {
            JPA.clear();
          }
        }
      }
    }
  }

  /**
   * Procédure permettant de créer les écritures de passage en client douteux pour chaque ligne
   * d'écriture de rejet de facture
   *
   * @param moveLineList Une liste de lignes d'écritures de rejet de facture
   * @param doubtfulCustomerAccount Un compte client douteux
   * @param debtPassReason Un motif de passage en client douteux
   * @throws AxelorException
   */
  @SuppressWarnings("unchecked")
  public void createDoubtFulCustomerRejectMove(
      int rule,
      Company company,
      Account doubtfulCustomerAccount,
      String debtPassReason,
      int fetchLimit) {

    int i = 0;
    int position = 0;
    List<MoveLine> moveLineList = null;
    while (!(moveLineList =
            (List<MoveLine>)
                doubtfulCustomerService.getRejectMoveLine(
                    rule, doubtfulCustomerAccount, company, fetchLimit, position))
        .isEmpty()) {
      position += moveLineList.size();
      String logMsg =
          rule == 0
              ? "Nombre de lignes d'écriture de rejet concernées (Créance de + 6 mois) au 411 : {} "
              : "Nombre de lignes d'écriture de rejet concernées (Créance de + 3 mois) au 411 : {} ";
      log.debug(logMsg, moveLineList.size());
      for (MoveLine moveLine : moveLineList) {

        try {

          doubtfulCustomerService.createDoubtFulCustomerRejectMove(
              moveLineRepo.find(moveLine.getId()),
              accountRepo.find(doubtfulCustomerAccount.getId()),
              debtPassReason);
          updateInvoice(moveLineRepo.find(moveLine.getId()).getInvoiceReject());
          i++;

        } catch (AxelorException e) {

          TraceBackService.trace(
              new AxelorException(
                  e,
                  e.getCategory(),
                  I18n.get("Invoice") + " %s",
                  moveLine.getInvoiceReject().getInvoiceId()),
              ExceptionOriginRepository.DOUBTFUL_CUSTOMER,
              batch.getId());
          incrementAnomaly();

        } catch (Exception e) {

          TraceBackService.trace(
              new Exception(
                  String.format(
                      I18n.get("Invoice") + " %s", moveLine.getInvoiceReject().getInvoiceId()),
                  e),
              ExceptionOriginRepository.DOUBTFUL_CUSTOMER,
              batch.getId());

          incrementAnomaly();

          log.error(
              "Bug(Anomalie) généré(e) pour la facture {}",
              moveLineRepo.find(moveLine.getId()).getInvoiceReject().getInvoiceId());

        } finally {

          if (i % 10 == 0) {
            JPA.clear();
          }
        }
      }
    }
  }

  /**
   * As {@code batch} entity can be detached from the session, call {@code Batch.find()} get the
   * entity in the persistant context. Warning : {@code batch} entity have to be saved before.
   */
  @Override
  protected void stop() {

    AccountingService.setUpdateCustomerAccount(true);

    String comment = I18n.get(IExceptionMessage.BATCH_DOUBTFUL_1) + " :\n";
    comment +=
        String.format("\t" + I18n.get(IExceptionMessage.BATCH_DOUBTFUL_2) + "\n", batch.getDone());
    comment +=
        String.format(
            "\t" + I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ALARM_ENGINE_BATCH_4),
            batch.getAnomaly());

    comment += String.format("\t* ------------------------------- \n");
    comment += String.format("\t* %s ", updateCustomerAccountLog);

    super.stop();
    addComment(comment);
  }
}
