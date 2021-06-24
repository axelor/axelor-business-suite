package com.axelor.apps.account.service.batch;

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.DebtRecovery;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.DebtRecoveryRepository;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.ExceptionOriginRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchBlockCustomersWithLatePayments extends BatchStrategy {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  private AccountConfigService accountConfigService;
  private AppBaseService appBaseService;
  private DebtRecoveryRepository debtRecoveryRepository;

  @Inject
  public BatchBlockCustomersWithLatePayments(
      AccountConfigService accountConfigService,
      AppBaseService appBaseService,
      DebtRecoveryRepository debtRecoveryRepository) {
    this.accountConfigService = accountConfigService;
    this.appBaseService = appBaseService;
    this.debtRecoveryRepository = debtRecoveryRepository;
  }

  @Override
  protected void process() {
    try {
      String result = blockCustomersWithLatePayments();
      addComment(result);
    } catch (Exception e) {
      TraceBackService.trace(e, ExceptionOriginRepository.IMPORT, batch.getId());
      incrementAnomaly();
    }
  }

  protected String blockCustomersWithLatePayments() {
    StringBuilder result = new StringBuilder();
    List<DebtRecovery> debtRecoveries;
    List<Long> customersToBlock = new ArrayList<Long>();
    List<Long> customerToUnblock = new ArrayList<Long>();
    int offset = 0;
    Query<DebtRecovery> query = debtRecoveryRepository.all().filter("self.isArchived = false");
    while (!(debtRecoveries = query.fetch(FETCH_LIMIT, offset)).isEmpty()) {
      for (DebtRecovery debtRecovery : debtRecoveries) {
        ++offset;
        if (debtRecovery
                .getRespiteDateBeforeAccountBlocking()
                .compareTo(appBaseService.getTodayDate(debtRecovery.getCompany()))
            >= 1) {
          for (Invoice invoice : debtRecovery.getInvoiceDebtRecoverySet()) {
            if (!customerToUnblock.contains(invoice.getPartner().getId())) {
              log.debug("Unblocking {}", invoice.getPartner());
              result
                  .append(I18n.get("Unblocking"))
                  .append(" ")
                  .append(invoice.getPartner().getFullName())
                  .append("</br>");
              customerToUnblock.add(invoice.getPartner().getId());
              incrementDone();
            }
          }
          continue;
        }
        for (Invoice invoice : debtRecovery.getInvoiceDebtRecoverySet()) {
          try {
            Partner partner = processInvoice(invoice);
            if (partner != null && !customersToBlock.contains(partner.getId())) {
              log.debug("Blocking {}", partner.getFullName());
              result
                  .append(I18n.get("Blocking"))
                  .append(" ")
                  .append(partner.getFullName())
                  .append("</br>");
              customersToBlock.add(partner.getId());
              incrementDone();
            }
          } catch (Exception e) {
            TraceBackService.trace(
                new Exception(String.format("%s %s", "Invoice", invoice.getInvoiceId()), e),
                null,
                batch.getId());
            log.error("Error for invoice {}", invoice.getInvoiceId());
            incrementAnomaly();
          }
        }
      }
      JPA.clear();
    }
    unblockCustomers(customerToUnblock);
    blockCustomers(customersToBlock);
    return result.toString();
  }

  @Transactional(rollbackOn = Exception.class)
  protected void blockCustomers(List<Long> customersToBlock) {
    if (CollectionUtils.isNotEmpty(customersToBlock)) {
      Query.of(Partner.class)
          .filter("self.id in :ids")
          .bind("ids", customersToBlock)
          .update("hasBlockedAccount", true);
    }
  }

  @Transactional(rollbackOn = Exception.class)
  protected void unblockCustomers(List<Long> customersToUnblock) {
    if (CollectionUtils.isNotEmpty(customersToUnblock)) {
      Query.of(Partner.class)
          .filter("self.id in :ids")
          .bind("ids", customersToUnblock)
          .update("hasBlockedAccount", false);
      //      Query.of(Partner.class)
      //      .filter("self.id in :ids")
      //      .bind("ids", customersToUnblock)
      //      .update("hasManuallyBlockedAccount", false);
    }
  }

  protected Partner processInvoice(Invoice invoice) throws AxelorException {
    AccountConfig config = accountConfigService.getAccountConfig(invoice.getCompany());
    if (!config.getHasLatePaymentAccountBlocking()) {
      return null;
    }
    if (invoice
            .getDueDate()
            .plusDays(config.getNumberOfDaysBeforeAccountBlocking())
            .compareTo(appBaseService.getTodayDate(invoice.getCompany()))
        <= 0) {
      return invoice.getPartner();
    }
    return null;
  }
}
