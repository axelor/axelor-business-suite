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
package com.axelor.apps.account.service.payment.paymentvoucher;

import com.axelor.apps.account.db.PaymentVoucher;
import com.axelor.apps.account.db.repo.PaymentVoucherRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;

public class PaymentVoucherToolService {

  /**
   * @param paymentVoucher : Une saisie Paiement
   *     <p>OperationTypeSelect 1 : Achat fournisseur 2 : Avoir fournisseur 3 : Vente client 4 :
   *     Avoir client
   * @return
   * @throws AxelorException
   */
  public boolean isDebitToPay(PaymentVoucher paymentVoucher) throws AxelorException {
    boolean isDebitToPay;

    switch (paymentVoucher.getOperationTypeSelect()) {
      case PaymentVoucherRepository.OPERATION_TYPE_SUPPLIER_PURCHASE: // fall-through
      case PaymentVoucherRepository.OPERATION_TYPE_CLIENT_REFUND:
        isDebitToPay = false;
        break;
      case PaymentVoucherRepository.OPERATION_TYPE_SUPPLIER_REFUND: // fall-through
      case PaymentVoucherRepository.OPERATION_TYPE_CLIENT_SALE:
        isDebitToPay = true;
        break;

      default:
        throw new AxelorException(
            paymentVoucher,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.PAYMENT_VOUCHER_TOOL_1),
            paymentVoucher.getRef());
    }

    return isDebitToPay;
  }

  /**
   * @param paymentVoucher : Une saisie Paiement
   *     <p>OperationTypeSelect 1 : Achat fournisseur 2 : Avoir fournisseur 3 : Vente client 4 :
   *     Avoir client
   * @return
   * @throws AxelorException
   */
  public boolean isPurchase(PaymentVoucher paymentVoucher) throws AxelorException {

    boolean isPurchase;

    switch (paymentVoucher.getOperationTypeSelect()) {
      case PaymentVoucherRepository.OPERATION_TYPE_SUPPLIER_PURCHASE:
        isPurchase = true;
        break;
      case PaymentVoucherRepository.OPERATION_TYPE_SUPPLIER_REFUND:
        isPurchase = true;
        break;
      case PaymentVoucherRepository.OPERATION_TYPE_CLIENT_SALE:
        isPurchase = false;
        break;
      case PaymentVoucherRepository.OPERATION_TYPE_CLIENT_REFUND:
        isPurchase = false;
        break;

      default:
        throw new AxelorException(
            paymentVoucher,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.PAYMENT_VOUCHER_TOOL_1),
            paymentVoucher.getRef());
    }

    return isPurchase;
  }
}
