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
package com.axelor.apps.bankpayment.service.batch;

import com.axelor.apps.account.db.AccountingBatch;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.PaymentSchedule;
import com.axelor.apps.account.db.PaymentScheduleLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.PaymentScheduleLineRepository;
import com.axelor.apps.account.db.repo.PaymentScheduleRepository;
import com.axelor.apps.account.db.repo.ReconcileRepository;
import com.axelor.apps.account.service.PaymentScheduleService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentValidateService;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.BankOrderLine;
import com.axelor.apps.bankpayment.db.repo.BankOrderRepository;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderCreateService;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderLineService;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderMergeService;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.tool.QueryBuilder;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

public class BatchBankPaymentServiceImpl implements BatchBankPaymentService {
  protected AppBaseService appBaseService;
  protected MoveService moveService;
  protected InvoicePaymentValidateService invoicePaymentValidateService;
  protected PaymentScheduleService paymentScheduleService;

  protected BankOrderCreateService bankOrderCreateService;
  protected BankOrderService bankOrderService;
  protected BankOrderLineService bankOrderLineService;
  protected BankOrderMergeService bankOrderMergeService;

  protected ReconcileRepository reconcileRepo;
  protected InvoicePaymentRepository invoicePaymentRepo;
  protected BankOrderRepository bankOrderRepo;
  protected BatchRepository batchRepo;

  @Inject
  public BatchBankPaymentServiceImpl(
      AppBaseService appBaseService,
      MoveService moveService,
      InvoicePaymentValidateService invoicePaymentValidateService,
      PaymentScheduleService paymentScheduleService,
      BankOrderCreateService bankOrderCreateService,
      BankOrderService bankOrderService,
      BankOrderLineService bankOrderLineService,
      BankOrderMergeService bankOrderMergeService,
      ReconcileRepository reconcileRepo,
      InvoicePaymentRepository invoicePaymentRepo,
      BankOrderRepository bankOrderRepo,
      BatchRepository batchRepo) {

    this.appBaseService = appBaseService;
    this.moveService = moveService;
    this.invoicePaymentValidateService = invoicePaymentValidateService;
    this.paymentScheduleService = paymentScheduleService;

    this.bankOrderCreateService = bankOrderCreateService;
    this.bankOrderService = bankOrderService;
    this.bankOrderLineService = bankOrderLineService;
    this.bankOrderMergeService = bankOrderMergeService;

    this.reconcileRepo = reconcileRepo;
    this.invoicePaymentRepo = invoicePaymentRepo;
    this.bankOrderRepo = bankOrderRepo;
    this.batchRepo = batchRepo;
  }

  @Override
  public boolean paymentScheduleLineDoneListExists(Batch batch) {
    return getPaymentScheduleLineDoneListQuery(batch).fetchOne() != null;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public BankOrder createBankOrder(Batch batch)
      throws AxelorException, JAXBException, IOException, DatatypeConfigurationException {

    PaymentScheduleLine paymentScheduleLine = getPaymentScheduleLineDoneListQuery(batch).fetchOne();

    if (paymentScheduleLine == null) {
      throw new AxelorException(
          batch,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.BATCH_DIRECT_DEBIT_NO_PROCESSED_PAYMENT_SCHEDULE_LINES));
    }

    PaymentSchedule paymentSchedule = paymentScheduleLine.getPaymentSchedule();

    switch (paymentSchedule.getTypeSelect()) {
      case PaymentScheduleRepository.TYPE_TERMS:
        return createBankOrderFromPaymentScheduleLines(batch);
      case PaymentScheduleRepository.TYPE_MONTHLY:
        return createBankOrderFromMonthlyPaymentScheduleLines(batch);
      default:
        throw new AxelorException(
            paymentSchedule,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.BATCH_DIRECT_DEBIT_UNKNOWN_DATA_TYPE));
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public BankOrder createBankOrderFromPaymentScheduleLines(Batch batch)
      throws AxelorException, JAXBException, IOException, DatatypeConfigurationException {

    List<PaymentScheduleLine> paymentScheduleLineList;
    int offset = 0;

    while (!(paymentScheduleLineList = fetchPaymentScheduleLineDoneList(batch, offset)).isEmpty()) {
      createBankOrders(batch, paymentScheduleLineList);
      offset += paymentScheduleLineList.size();
      JPA.clear();
      batch = batchRepo.find(batch.getId());
    }

    List<BankOrder> bankOrderList;

    while ((bankOrderList = fetchLimitedBankOrderList(batch)).size() > 1) {
      bankOrderMergeService.mergeBankOrders(bankOrderList);
      JPA.clear();
      batch = batchRepo.find(batch.getId());
    }

    if (bankOrderList.isEmpty()) {
      throw new AxelorException(
          batch,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.BANK_ORDER_MERGE_NO_BANK_ORDERS));
    }

    BankOrder bankOrder = bankOrderRepo.find(bankOrderList.iterator().next().getId());
    batch.setBankOrder(bankOrder);

    return bankOrder;
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void createBankOrders(Batch batch, Collection<PaymentScheduleLine> paymentScheduleLines)
      throws AxelorException, JAXBException, IOException, DatatypeConfigurationException {

    for (PaymentScheduleLine paymentScheduleLine : paymentScheduleLines) {
      PaymentSchedule paymentSchedule = paymentScheduleLine.getPaymentSchedule();
      MoveLine creditMoveLine = paymentScheduleLine.getAdvanceMoveLine();

      for (Invoice invoice : paymentSchedule.getInvoiceSet()) {
        MoveLine debitMoveLine = moveService.getMoveLineService().getDebitCustomerMoveLine(invoice);
        Reconcile reconcile = reconcileRepo.findByMoveLines(debitMoveLine, creditMoveLine);

        if (reconcile == null) {
          continue;
        }

        createBankOrders(batch, reconcile);
      }
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void createBankOrders(Batch batch, Reconcile reconcile)
      throws AxelorException, JAXBException, IOException, DatatypeConfigurationException {

    for (InvoicePayment invoicePayment : invoicePaymentRepo.findByReconcile(reconcile).fetch()) {
      if (invoicePayment.getBankOrder() != null) {
        continue;
      }

      invoicePaymentValidateService.validate(invoicePayment, true);

      if (invoicePayment.getBankOrder() == null) {
        throw new AxelorException(
            invoicePayment,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get("Failed to create bank order from invoice payment"));
      }

      invoicePayment.getBankOrder().setBatch(batch);
      bankOrderRepo.save(invoicePayment.getBankOrder());
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public BankOrder createBankOrderFromMonthlyPaymentScheduleLines(Batch batch)
      throws AxelorException, JAXBException, IOException, DatatypeConfigurationException {

    AccountingBatch accountingBatch = batch.getAccountingBatch();
    LocalDate bankOrderDate = accountingBatch.getDueDate();
    Company senderCompany = accountingBatch.getCompany();
    BankDetails senderBankDetails = accountingBatch.getBankDetails();

    if (senderBankDetails == null) {
      senderBankDetails = accountingBatch.getCompany().getDefaultBankDetails();
    }

    PaymentMode paymentMode = accountingBatch.getPaymentMode();

    Currency currency = senderCompany.getCurrency();
    int partnerType = BankOrderRepository.PARTNER_TYPE_CUSTOMER;
    String senderReference = "";
    String senderLabel = "";

    if (bankOrderDate == null) {
      bankOrderDate = appBaseService.getTodayDate();
    }

    BankOrder bankOrder =
        bankOrderCreateService.createBankOrder(
            paymentMode,
            partnerType,
            bankOrderDate,
            senderCompany,
            senderBankDetails,
            currency,
            senderReference,
            senderLabel,
            BankOrderRepository.TECHNICAL_ORIGIN_AUTOMATIC);
    bankOrder = JPA.save(bankOrder);

    List<PaymentScheduleLine> paymentScheduleLineList;
    int offset = 0;

    try {
      while (!(paymentScheduleLineList = fetchPaymentScheduleLineDoneList(batch, offset))
          .isEmpty()) {
        bankOrder = bankOrderRepo.find(bankOrder.getId());

        for (PaymentScheduleLine paymentScheduleLine : paymentScheduleLineList) {
          PaymentSchedule paymentSchedule = paymentScheduleLine.getPaymentSchedule();
          Partner partner = paymentSchedule.getPartner();
          BankDetails bankDetails = paymentScheduleService.getBankDetails(paymentSchedule);
          BigDecimal amount = paymentScheduleLine.getInTaxAmount();
          String receiverReference = paymentScheduleLine.getName();
          String receiverLabel = paymentScheduleLine.getDebitNumber();
          BankOrderLine bankOrderLine =
              bankOrderLineService.createBankOrderLine(
                  paymentMode.getBankOrderFileFormat(),
                  null,
                  partner,
                  bankDetails,
                  amount,
                  currency,
                  bankOrderDate,
                  receiverReference,
                  receiverLabel,
                  paymentScheduleLine);
          bankOrder.addBankOrderLineListItem(bankOrderLine);
        }

        bankOrder = JPA.save(bankOrder);
        offset += paymentScheduleLineList.size();
        JPA.clear();
      }
    } catch (Exception e) {
      bankOrder = bankOrderRepo.find(bankOrder.getId());
      bankOrderRepo.remove(bankOrder);
      throw e;
    }

    bankOrder = bankOrderRepo.find(bankOrder.getId());
    bankOrder = bankOrderRepo.save(bankOrder);
    bankOrderService.confirm(bankOrder);

    batch = batchRepo.find(batch.getId());
    batch.setBankOrder(bankOrder);

    return bankOrder;
  }

  protected List<PaymentScheduleLine> fetchPaymentScheduleLineDoneList(Batch batch, int offset) {
    return getPaymentScheduleLineDoneListQuery(batch).fetch(AbstractBatch.FETCH_LIMIT, offset);
  }

  protected Query<PaymentScheduleLine> getPaymentScheduleLineDoneListQuery(Batch batch) {
    QueryBuilder<PaymentScheduleLine> queryBuilder = QueryBuilder.of(PaymentScheduleLine.class);

    queryBuilder.add(":batch MEMBER OF self.batchSet");
    queryBuilder.bind("batch", batch);

    queryBuilder.add("self.statusSelect = :statusSelect");
    queryBuilder.bind("statusSelect", PaymentScheduleLineRepository.STATUS_VALIDATED);

    return queryBuilder.build().order("id");
  }

  protected List<BankOrder> fetchLimitedBankOrderList(Batch batch) {
    return getBankOrderListQuery(batch).fetch(AbstractBatch.FETCH_LIMIT);
  }

  protected Query<BankOrder> getBankOrderListQuery(Batch batch) {
    QueryBuilder<BankOrder> queryBuilder = QueryBuilder.of(BankOrder.class);

    queryBuilder.add("self.batch = :batch");
    queryBuilder.bind("batch", batch);

    return queryBuilder.build().order("id");
  }
}
