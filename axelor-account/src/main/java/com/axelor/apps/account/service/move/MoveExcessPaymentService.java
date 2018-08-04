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

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.base.db.Company;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MoveExcessPaymentService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected MoveLineRepository moveLineRepository;
  protected MoveToolService moveToolService;

  @Inject
  public MoveExcessPaymentService(
      MoveLineRepository moveLineRepository, MoveToolService moveToolService) {

    this.moveLineRepository = moveLineRepository;
    this.moveToolService = moveToolService;
  }

  /**
   * Méthode permettant de récupérer les trop-perçus et une facture
   *
   * @param invoice Une facture
   * @return
   * @throws AxelorException
   */
  public List<MoveLine> getExcessPayment(Invoice invoice) throws AxelorException {
    Company company = invoice.getCompany();
    AccountConfig accountConfig = Beans.get(AccountConfigService.class).getAccountConfig(company);

    // get advance payments
    List<MoveLine> advancePaymentMoveLines =
        Beans.get(InvoiceService.class).getMoveLinesFromAdvancePayments(invoice);

    if (accountConfig.getAutoReconcileOnInvoice()) {
      List<MoveLine> creditMoveLines =
          moveLineRepository
              .all()
              .filter(
                  "self.move.company = ?1 AND (self.move.statusSelect = ?2 OR self.move.statusSelect = ?3) AND self.move.ignoreInAccountingOk IN (false,null)"
                      + " AND self.account.useForPartnerBalance = ?4 AND self.credit > 0 and self.amountRemaining > 0"
                      + " AND self.partner = ?5 ORDER BY self.date ASC",
                  company,
                  MoveRepository.STATUS_VALIDATED,
                  MoveRepository.STATUS_DAYBOOK,
                  true,
                  invoice.getPartner())
              .fetch();

      log.debug(
          "Nombre de trop-perçus à imputer sur la facture récupéré : {}", creditMoveLines.size());
      advancePaymentMoveLines.addAll(creditMoveLines);
    }
    // remove duplicates
    advancePaymentMoveLines =
        advancePaymentMoveLines.stream().distinct().collect(Collectors.toList());
    return advancePaymentMoveLines;
  }

  public List<MoveLine> getAdvancePaymentMoveList(Invoice invoice) {

    List<MoveLine> moveLineList = Lists.newArrayList();

    if (invoice.getInvoicePaymentList() != null) {

      for (InvoicePayment invoicePayment : invoice.getInvoicePaymentList()) {

        for (MoveLine moveLine : invoicePayment.getMove().getMoveLineList()) {

          if (moveLine.getCredit().compareTo(BigDecimal.ZERO) != 0) {
            moveLineList.add(moveLine);
          }
        }
      }

      return moveToolService.orderListByDate(moveLineList);
    }

    return moveLineList;
  }
}
