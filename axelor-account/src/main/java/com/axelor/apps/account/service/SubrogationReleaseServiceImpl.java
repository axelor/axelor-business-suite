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
package com.axelor.apps.account.service;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.SubrogationRelease;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.db.repo.SubrogationReleaseRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.report.IReport;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Sequence;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.tool.file.CsvTool;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SubrogationReleaseServiceImpl implements SubrogationReleaseService {

  protected AppBaseService appBaseService;
  protected InvoiceRepository invoiceRepository;

  @Inject
  public SubrogationReleaseServiceImpl(
      AppBaseService appBaseService, InvoiceRepository invoiceRepository) {
    this.appBaseService = appBaseService;
    this.invoiceRepository = invoiceRepository;
  }

  @Override
  public List<Invoice> retrieveInvoices(Company company) {
    Query<Invoice> query =
        invoiceRepository
            .all()
            .filter(
                "self.company = :company AND self.partner.factorizedCustomer = TRUE "
                    + "AND self.statusSelect = :invoiceStatusVentilated "
                    + "AND self.id not in ("
                    + "		select Invoices.id "
                    + "		from SubrogationRelease as SR "
                    + "		join SR.invoiceSet as Invoices "
                    + "		where SR.statusSelect in (:subrogationReleaseStatusTransmitted, :subrogationReleaseStatusAccounted, :subrogationReleaseStatusCleared))"
                    + "AND ((self.amountRemaining > 0 AND self.hasPendingPayments = FALSE)"
                    + "			OR self.originalInvoice.id in ("
                    + "				select Invoices.id "
                    + "				from SubrogationRelease as SR "
                    + "				join SR.invoiceSet as Invoices "
                    + "				where SR.statusSelect in (:subrogationReleaseStatusTransmitted, :subrogationReleaseStatusAccounted, :subrogationReleaseStatusCleared)))")
            .order("invoiceDate")
            .order("dueDate")
            .order("invoiceId");
    query.bind("company", company);
    query.bind("invoiceStatusVentilated", InvoiceRepository.STATUS_VENTILATED);
    query.bind(
        "subrogationReleaseStatusTransmitted", SubrogationReleaseRepository.STATUS_TRANSMITTED);
    query.bind("subrogationReleaseStatusAccounted", SubrogationReleaseRepository.STATUS_ACCOUNTED);
    query.bind("subrogationReleaseStatusCleared", SubrogationReleaseRepository.STATUS_CLEARED);
    List<Invoice> invoiceList = query.fetch();
    return invoiceList;
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void transmitRelease(SubrogationRelease subrogationRelease) throws AxelorException {
    SequenceService sequenceService = Beans.get(SequenceService.class);
    String sequenceNumber =
        sequenceService.getSequenceNumber("subrogationRelease", subrogationRelease.getCompany());
    if (Strings.isNullOrEmpty(sequenceNumber)) {
      throw new AxelorException(
          Sequence.class,
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.SUBROGATION_RELEASE_MISSING_SEQUENCE),
          subrogationRelease.getCompany().getName());
    }

    subrogationRelease.setSequenceNumber(sequenceNumber);
    subrogationRelease.setStatusSelect(SubrogationReleaseRepository.STATUS_TRANSMITTED);
    subrogationRelease.setTransmissionDate(appBaseService.getTodayDate());
  }

  @Override
  public String printToPDF(SubrogationRelease subrogationRelease, String name)
      throws AxelorException {
    ReportSettings reportSettings =
        ReportFactory.createReport(
            "accountSubrogationRelease",
            subrogationRelease.getCompany(),
            IReport.SUBROGATION_RELEASE,
            name);
    reportSettings.addParam("SubrogationReleaseId", subrogationRelease.getId());
    reportSettings.addParam("Locale", ReportSettings.getPrintingLocale(null));
    reportSettings.addFormat("pdf");
    reportSettings.toAttach(subrogationRelease);
    reportSettings.generate();
    return reportSettings.getFileLink();
  }

  @Override
  public String exportToCSV(SubrogationRelease subrogationRelease)
      throws AxelorException, IOException {
    List<String[]> allMoveLineData = new ArrayList<>();

    Comparator<Invoice> byInvoiceDate =
        (i1, i2) -> i1.getInvoiceDate().compareTo(i2.getInvoiceDate());
    Comparator<Invoice> byDueDate = (i1, i2) -> i1.getDueDate().compareTo(i2.getDueDate());
    Comparator<Invoice> byInvoiceId = (i1, i2) -> i1.getInvoiceId().compareTo(i2.getInvoiceId());

    List<Invoice> releaseDetails =
        subrogationRelease
            .getInvoiceSet()
            .stream()
            .sorted(byInvoiceDate.thenComparing(byDueDate).thenComparing(byInvoiceId))
            .collect(Collectors.toList());

    for (Invoice invoice : releaseDetails) {
      String[] items = new String[6];
      BigDecimal inTaxTotal = invoice.getInTaxTotal().abs();

      if (InvoiceToolService.isOutPayment(invoice)) {
        inTaxTotal = inTaxTotal.negate();
      }

      items[0] = invoice.getPartner().getPartnerSeq();
      items[1] = invoice.getInvoiceId();
      items[2] = invoice.getInvoiceDate().toString();
      items[3] = invoice.getDueDate().toString();
      items[4] = inTaxTotal.toString();
      items[5] = invoice.getCurrency().getCode();
      allMoveLineData.add(items);
    }

    AccountConfigService accountConfigService = Beans.get(AccountConfigService.class);
    String filePath =
        accountConfigService.getAccountConfig(subrogationRelease.getCompany()).getExportPath();
    if (filePath == null) {
      filePath = com.google.common.io.Files.createTempDir().getAbsolutePath();
    } else {
      new File(filePath).mkdirs();
    }
    String fileName =
        String.format(
            "%s %s.csv", I18n.get("Subrogation release"), subrogationRelease.getSequenceNumber());
    Files.createDirectories(Paths.get(filePath));
    Path path = Paths.get(filePath, fileName);
    CsvTool.csvWriter(filePath, fileName, ';', null, allMoveLineData);

    try (InputStream is = new FileInputStream(path.toFile())) {
      Beans.get(MetaFiles.class).attach(is, fileName, subrogationRelease);
    }

    return path.toString();
  }

  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void enterReleaseInTheAccounts(SubrogationRelease subrogationRelease)
      throws AxelorException {
    MoveService moveService = Beans.get(MoveService.class);
    MoveRepository moveRepository = Beans.get(MoveRepository.class);
    AccountConfigService accountConfigService = Beans.get(AccountConfigService.class);
    AppBaseService appBaseService = Beans.get(AppBaseService.class);

    Company company = subrogationRelease.getCompany();
    AccountConfig accountConfig = accountConfigService.getAccountConfig(company);
    Journal journal = accountConfigService.getAutoMiscOpeJournal(accountConfig);
    Account factorCreditAccount = accountConfigService.getFactorCreditAccount(accountConfig);
    Account factorDebitAccount = accountConfigService.getFactorDebitAccount(accountConfig);

    if (subrogationRelease.getAccountingDate() == null) {
      subrogationRelease.setAccountingDate(appBaseService.getTodayDate());
    }

    for (Invoice invoice : subrogationRelease.getInvoiceSet()) {
      if (invoice.getCompanyInTaxTotalRemaining().compareTo(BigDecimal.ZERO) == 0) {
        continue;
      }

      LocalDate date = subrogationRelease.getAccountingDate();
      Move move =
          moveService
              .getMoveCreateService()
              .createMove(
                  journal,
                  company,
                  company.getCurrency(),
                  invoice.getPartner(),
                  date,
                  null,
                  MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC);
      MoveLine creditMoveLine, debitMoveLine;

      debitMoveLine =
          moveService
              .getMoveLineService()
              .createMoveLine(
                  move,
                  invoice.getPartner(),
                  factorDebitAccount,
                  invoice.getCompanyInTaxTotalRemaining(),
                  true,
                  date,
                  null,
                  1,
                  subrogationRelease.getSequenceNumber(),
                  null);

      creditMoveLine =
          moveService
              .getMoveLineService()
              .createMoveLine(
                  move,
                  invoice.getPartner(),
                  factorCreditAccount,
                  invoice.getCompanyInTaxTotalRemaining(),
                  false,
                  date,
                  null,
                  2,
                  subrogationRelease.getSequenceNumber(),
                  null);

      move.addMoveLineListItem(debitMoveLine);
      move.addMoveLineListItem(creditMoveLine);

      move = moveRepository.save(move);
      moveService.getMoveValidateService().validate(move);
    }

    subrogationRelease.setStatusSelect(SubrogationReleaseRepository.STATUS_ACCOUNTED);
  }

  @Override
  @Transactional
  public void clear(SubrogationRelease subrogationRelease) {

    if (isSubrogationReleaseCompletelyPaid(subrogationRelease)) {

      subrogationRelease.setStatusSelect(SubrogationReleaseRepository.STATUS_CLEARED);
    }
  }

  @Override
  public boolean isSubrogationReleaseCompletelyPaid(SubrogationRelease subrogationRelease) {

    return subrogationRelease
            .getInvoiceSet()
            .stream()
            .filter(p -> p.getAmountRemaining().compareTo(BigDecimal.ZERO) == 1)
            .count()
        == 0;
  }
}
