package com.axelor.apps.account.service.batch;

import com.axelor.apps.account.db.AccountingBatch;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountingService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.tool.QueryBuilder;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.bp.LocalDate;

public class BatchInterestInvoice extends BatchStrategy {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  @Transactional
  protected void process() {
    AccountingBatch accountingBatch = batch.getAccountingBatch();

    PaymentCondition condition = accountingBatch.getPaymentCondition();
    LocalDate today = LocalDate.now();

    QueryBuilder<Invoice> query =
        QueryBuilder.of(Invoice.class)
            .add("self.company = :comapny")
            .add("self.operationTypeSelect = :operationTypeSelect")
            .add("self.operationSubTypeSelect != :operationSubTypeSelect")
            .add("self.dueDate < :dueDate")
            .add("self.amountRemaining > 0")
            .add("self.paymentCondition IS NOT NULL")
            .add("self.paymentCondition.dailyPenaltyRate > 0")
            .bind("comapny", accountingBatch.getInterestInvoiceCompany())
            .bind("operationTypeSelect", InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)
            .bind("operationSubTypeSelect", InvoiceRepository.OPERATION_SUB_TYPE_INTEREST)
            .bind("dueDate", today);

    if (condition != null) {
      query =
          query
              .add("self.paymentCondition = :paymentCondition")
              .bind("paymentCondition", condition);
    }

    List<Invoice> invoiceList = query.build().fetch();
    if (invoiceList != null) {
      InvoiceRepository invoiceRepository = Beans.get(InvoiceRepository.class);
      for (Invoice invoice : invoiceList) {
        try {

          Invoice interestInvoice =
              Beans.get(InvoiceService.class).generateInterestInvoice(invoice);
          updateInvoice(interestInvoice);
          invoiceRepository.save(interestInvoice);

        } catch (AxelorException e) {

          TraceBackService.trace(
              new AxelorException(
                  e, e.getCategory(), I18n.get("Invoice") + " %s", invoice.getInvoiceId()),
              ExceptionOriginRepository.INTEREST_INVOICE,
              batch.getId());
          incrementAnomaly();

        } catch (Exception e) {

          TraceBackService.trace(
              new Exception(String.format(I18n.get("Invoice") + " %s", invoice.getInvoiceId()), e),
              ExceptionOriginRepository.INTEREST_INVOICE,
              batch.getId());

          incrementAnomaly();

          log.error(I18n.get("Bug (Anomaly) generated for the invoice {}"), invoice.getInvoiceId());
        }
      }
    }
  }

  @Override
  protected void stop() {

    AccountingService.setUpdateCustomerAccount(true);

    String comment = I18n.get(IExceptionMessage.BATCH_INTEREST_INVOICE_1) + " :\n";
    comment +=
        String.format(
            "\t" + I18n.get(IExceptionMessage.BATCH_INTEREST_INVOICE_2) + "\n", batch.getDone());
    comment +=
        String.format(
            "\t" + I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.ALARM_ENGINE_BATCH_4),
            batch.getAnomaly());

    super.stop();
    addComment(comment);
  }
}
