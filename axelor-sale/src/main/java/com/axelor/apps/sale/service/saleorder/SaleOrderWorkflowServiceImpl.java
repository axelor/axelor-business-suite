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
package com.axelor.apps.sale.service.saleorder;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.Blocking;
import com.axelor.apps.base.db.CancelReason;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.repo.BlockingRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.service.BlockingService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.crm.db.Opportunity;
import com.axelor.apps.crm.db.OpportunityStatus;
import com.axelor.apps.crm.db.repo.OpportunityStatusRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.exception.BlockedSaleOrderException;
import com.axelor.apps.sale.exception.IExceptionMessage;
import com.axelor.apps.sale.report.IReport;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import javax.persistence.Query;

public class SaleOrderWorkflowServiceImpl implements SaleOrderWorkflowService {

  protected SequenceService sequenceService;
  protected PartnerRepository partnerRepo;
  protected SaleOrderRepository saleOrderRepo;
  protected AppSaleService appSaleService;
  protected UserService userService;

  @Inject
  public SaleOrderWorkflowServiceImpl(
      SequenceService sequenceService,
      PartnerRepository partnerRepo,
      SaleOrderRepository saleOrderRepo,
      AppSaleService appSaleService,
      UserService userService) {

    this.sequenceService = sequenceService;
    this.partnerRepo = partnerRepo;
    this.saleOrderRepo = saleOrderRepo;
    this.appSaleService = appSaleService;
    this.userService = userService;
  }

  @Override
  @Transactional
  public Partner validateCustomer(SaleOrder saleOrder) {

    Partner clientPartner = partnerRepo.find(saleOrder.getClientPartner().getId());
    clientPartner.setIsCustomer(true);
    clientPartner.setIsProspect(false);

    return partnerRepo.save(clientPartner);
  }

  @Override
  public String getSequence(Company company) throws AxelorException {

    String seq = sequenceService.getSequenceNumber(SequenceRepository.SALES_ORDER, company);
    if (seq == null) {
      throw new AxelorException(
          company,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.SALES_ORDER_1),
          company.getName());
    }
    return seq;
  }

  @Override
  @Transactional
  public void cancelSaleOrder(
      SaleOrder saleOrder, CancelReason cancelReason, String cancelReasonStr) {
    Query q =
        JPA.em()
            .createQuery(
                "select count(*) FROM SaleOrder as self WHERE self.statusSelect in (?1 , ?2) AND self.clientPartner = ?3 ");
    q.setParameter(1, SaleOrderRepository.STATUS_ORDER_CONFIRMED);
    q.setParameter(2, SaleOrderRepository.STATUS_ORDER_COMPLETED);
    q.setParameter(3, saleOrder.getClientPartner());
    if ((long) q.getSingleResult() == 0) {
      saleOrder.getClientPartner().setIsCustomer(false);
      saleOrder.getClientPartner().setIsProspect(true);
    }
    saleOrder.setStatusSelect(SaleOrderRepository.STATUS_CANCELED);
    saleOrder.setCancelReason(cancelReason);
    if (Strings.isNullOrEmpty(cancelReasonStr)) {
      saleOrder.setCancelReasonStr(cancelReason.getName());
    } else {
      saleOrder.setCancelReasonStr(cancelReasonStr);
    }
    saleOrderRepo.save(saleOrder);
  }

  @Override
  @Transactional(
      rollbackOn = {Exception.class},
      ignore = {BlockedSaleOrderException.class})
  public void finalizeQuotation(SaleOrder saleOrder) throws AxelorException {
    Partner partner = saleOrder.getClientPartner();

    Blocking blocking =
        Beans.get(BlockingService.class)
            .getBlocking(partner, saleOrder.getCompany(), BlockingRepository.SALE_BLOCKING);

    if (blocking != null) {
      saleOrder.setBlockedOnCustCreditExceed(true);
      if (!saleOrder.getManualUnblock()) {
        saleOrderRepo.save(saleOrder);
        String reason =
            blocking.getBlockingReason() != null ? blocking.getBlockingReason().getName() : "";
        throw new BlockedSaleOrderException(
            partner, I18n.get("Client is sale blocked:") + " " + reason);
      }
    }

    if (saleOrder.getVersionNumber() == 1
        && sequenceService.isEmptyOrDraftSequenceNumber(saleOrder.getSaleOrderSeq())) {
      saleOrder.setSaleOrderSeq(this.getSequence(saleOrder.getCompany()));
    }

    saleOrder.setStatusSelect(SaleOrderRepository.STATUS_FINALIZED_QUOTATION);
    if (appSaleService.getAppSale().getPrintingOnSOFinalization()) {
      this.saveSaleOrderPDFAsAttachment(saleOrder);
    }
    saleOrderRepo.save(saleOrder);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void confirmSaleOrder(SaleOrder saleOrder) throws AxelorException {
    saleOrder.setStatusSelect(SaleOrderRepository.STATUS_ORDER_CONFIRMED);
    saleOrder.setConfirmationDateTime(appSaleService.getTodayDateTime().toLocalDateTime());
    saleOrder.setConfirmedByUser(userService.getUser());

    this.validateCustomer(saleOrder);

    if (appSaleService.getAppSale().getCloseOpportunityUponSaleOrderConfirmation()) {
      Opportunity opportunity = saleOrder.getOpportunity();
      OpportunityStatus statusClosedWon =
          Beans.get(OpportunityStatusRepository.class)
              .findByTechnicalTypeSelect(OpportunityStatusRepository.TECHNICAL_TYPE_CLOSED_WON);
      if (opportunity != null && statusClosedWon != null) {
        opportunity.setOpportunityStatus(statusClosedWon);
      }
    }

    saleOrderRepo.save(saleOrder);
  }

  @Transactional
  public void completeSaleOrder(SaleOrder saleOrder) throws AxelorException {
    saleOrder.setStatusSelect(SaleOrderRepository.STATUS_ORDER_COMPLETED);
    saleOrder.setOrderBeingEdited(false);

    saleOrderRepo.save(saleOrder);
  }

  @Override
  public void saveSaleOrderPDFAsAttachment(SaleOrder saleOrder) throws AxelorException {

    if (saleOrder.getPrintingSettings() == null) {
      if (saleOrder.getCompany().getPrintingSettings() != null) {
        saleOrder.setPrintingSettings(saleOrder.getCompany().getPrintingSettings());
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            String.format(
                I18n.get(IExceptionMessage.SALE_ORDER_MISSING_PRINTING_SETTINGS),
                saleOrder.getSaleOrderSeq()),
            saleOrder);
      }
    }

    ReportFactory.createReport(IReport.SALES_ORDER, this.getFileName(saleOrder) + "-${date}")
        .addParam("Locale", ReportSettings.getPrintingLocale(saleOrder.getClientPartner()))
        .addParam(
            "Timezone",
            saleOrder.getCompany() != null ? saleOrder.getCompany().getTimezone() : null)
        .addParam("SaleOrderId", saleOrder.getId())
        .addParam("HeaderHeight", saleOrder.getPrintingSettings().getPdfHeaderHeight())
        .addParam("FooterHeight", saleOrder.getPrintingSettings().getPdfFooterHeight())
        .toAttach(saleOrder)
        .generate()
        .getFileLink();

    //		String relatedModel = generalService.getPersistentClass(saleOrder).getCanonicalName();
    // required ?

  }

  @Override
  public String getFileName(SaleOrder saleOrder) {
    String fileNamePrefix;
    if (saleOrder.getStatusSelect() == SaleOrderRepository.STATUS_DRAFT_QUOTATION
        || saleOrder.getStatusSelect() == SaleOrderRepository.STATUS_FINALIZED_QUOTATION) {
      fileNamePrefix = "Sale quotation";
    } else {
      fileNamePrefix = "Sale order";
    }

    return I18n.get(fileNamePrefix)
        + " "
        + saleOrder.getSaleOrderSeq()
        + ((saleOrder.getVersionNumber() > 1) ? "-V" + saleOrder.getVersionNumber() : "");
  }
}
