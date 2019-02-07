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

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.exception.AxelorException;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MoveDueService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MoveLineRepository moveLineRepository;

  @Inject
  public MoveDueService(MoveLineRepository moveLineRepository) {

    this.moveLineRepository = moveLineRepository;
  }

  public MoveLine getOrignalInvoiceFromRefund(Invoice invoice) {

    Invoice originalInvoice = invoice.getOriginalInvoice();

    if (originalInvoice != null && originalInvoice.getMove() != null) {
      for (MoveLine moveLine : originalInvoice.getMove().getMoveLineList()) {
        if (moveLine.getAccount().getUseForPartnerBalance()
            && moveLine.getDebit().compareTo(BigDecimal.ZERO) > 0
            && moveLine.getAmountRemaining().compareTo(BigDecimal.ZERO) > 0) {
          return moveLine;
        }
      }
    }

    return null;
  }

  public List<MoveLine> getInvoiceDue(Invoice invoice, boolean useOthersInvoiceDue)
      throws AxelorException {
    Company company = invoice.getCompany();
    Partner partner = invoice.getPartner();

    List<MoveLine> debitMoveLines = Lists.newArrayList();

    // Ajout de la facture d'origine
    MoveLine originalInvoice = this.getOrignalInvoiceFromRefund(invoice);

    if (originalInvoice != null) {
      debitMoveLines.add(originalInvoice);
    }

    // Récupérer les dûs du tiers pour le même compte que celui de l'avoir
    List<? extends MoveLine> othersDebitMoveLines = null;
    if (useOthersInvoiceDue) {
      if (debitMoveLines != null && debitMoveLines.size() != 0) {
        othersDebitMoveLines =
            moveLineRepository
                .all()
                .filter(
                    "self.move.company = ?1 AND (self.move.statusSelect = ?2 OR self.move.statusSelect = ?3) AND self.move.ignoreInAccountingOk IN (false,null)"
                        + " AND self.account.useForPartnerBalance = ?4 AND self.debit > 0 AND self.amountRemaining > 0 "
                        + " AND self.partner = ?5 AND self NOT IN (?6) ORDER BY self.date ASC ",
                    company,
                    MoveRepository.STATUS_VALIDATED,
                    MoveRepository.STATUS_DAYBOOK,
                    true,
                    partner,
                    debitMoveLines)
                .fetch();
      } else {
        othersDebitMoveLines =
            moveLineRepository
                .all()
                .filter(
                    "self.move.company = ?1 AND (self.move.statusSelect = ?2 OR self.move.statusSelect = ?3) AND self.move.ignoreInAccountingOk IN (false,null)"
                        + " AND self.account.useForPartnerBalance = ?4 AND self.debit > 0 AND self.amountRemaining > 0 "
                        + " AND self.partner = ?5 ORDER BY self.date ASC ",
                    company,
                    MoveRepository.STATUS_VALIDATED,
                    MoveRepository.STATUS_DAYBOOK,
                    true,
                    partner)
                .fetch();
      }
      debitMoveLines.addAll(othersDebitMoveLines);
    }

    log.debug("Nombre de ligne à payer avec l'avoir récupéré : {}", debitMoveLines.size());

    return debitMoveLines;
  }
}
