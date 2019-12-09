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

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.FixedAsset;
import com.axelor.apps.account.db.FixedAssetLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.FixedAssetLineRepository;
import com.axelor.apps.account.db.repo.FixedAssetRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class FixedAssetServiceImpl implements FixedAssetService {

  @Inject FixedAssetRepository fixedAssetRepo;

  @Inject FixedAssetLineService fixedAssetLineService;

  @Override
  public FixedAsset generateAndcomputeLines(FixedAsset fixedAsset) {

    BigDecimal depreciationValue = this.computeDepreciationValue(fixedAsset);
    BigDecimal cumulativeValue = depreciationValue;
    LocalDate depreciationDate = fixedAsset.getFirstDepreciationDate();
    LocalDate acquisitionDate = fixedAsset.getAcquisitionDate();
    int numberOfDepreciation = fixedAsset.getNumberOfDepreciation();
    boolean isProrataTemporis = fixedAsset.getFixedAssetCategory().getIsProrataTemporis();
    LocalDate endDate = depreciationDate.plusMonths(fixedAsset.getDurationInMonth());
    int counter = 1;
    int scale = Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice();
    numberOfDepreciation--;

    while (depreciationDate.isBefore(endDate)) {
      FixedAssetLine fixedAssetLine = new FixedAssetLine();
      fixedAssetLine.setStatusSelect(FixedAssetLineRepository.STATUS_PLANNED);
      fixedAssetLine.setDepreciationDate(depreciationDate);
      fixedAssetLine.setDepreciation(depreciationValue);
      fixedAssetLine.setCumulativeDepreciation(cumulativeValue);
      fixedAssetLine.setResidualValue(
          fixedAsset.getGrossValue().subtract(fixedAssetLine.getCumulativeDepreciation()));

      fixedAsset.addFixedAssetLineListItem(fixedAssetLine);
      if (counter == numberOfDepreciation) {
        depreciationValue = fixedAssetLine.getResidualValue();
        cumulativeValue = cumulativeValue.add(depreciationValue);
        depreciationDate = depreciationDate.plusMonths(fixedAsset.getPeriodicityInMonth());
        if (isProrataTemporis) {
          endDate =
              depreciationDate.minusDays(
                  ChronoUnit.DAYS.between(acquisitionDate, fixedAsset.getFirstDepreciationDate()));
          depreciationDate = endDate.minusDays(1);
        }
        counter++;
        continue;
      }

      if (fixedAsset.getComputationMethodSelect().equals("degressive")) {
        if (counter > 2 && fixedAsset.getNumberOfDepreciation() > 3) {
          if (counter == 3) {
            int remainingYear = fixedAsset.getNumberOfDepreciation() - 3;
            depreciationValue =
                fixedAssetLine
                    .getResidualValue()
                    .divide(new BigDecimal(remainingYear), RoundingMode.HALF_EVEN);
          }
        } else {
          depreciationValue =
              this.computeDepreciation(fixedAsset, fixedAssetLine.getResidualValue(), false);
        }
        depreciationDate = depreciationDate.plusMonths(fixedAsset.getPeriodicityInMonth());
      } else {
        depreciationValue =
            this.computeDepreciation(fixedAsset, fixedAsset.getResidualValue(), false);
        depreciationDate = depreciationDate.plusMonths(fixedAsset.getPeriodicityInMonth());
      }
      depreciationValue = depreciationValue.setScale(scale, RoundingMode.HALF_EVEN);
      cumulativeValue =
          cumulativeValue.add(depreciationValue).setScale(scale, RoundingMode.HALF_EVEN);
      counter++;
    }
    return fixedAsset;
  }

  private BigDecimal computeDepreciationValue(FixedAsset fixedAsset) {
    BigDecimal depreciationValue = BigDecimal.ZERO;
    depreciationValue = this.computeDepreciation(fixedAsset, fixedAsset.getGrossValue(), true);
    return depreciationValue;
  }

  private BigDecimal computeProrataTemporis(FixedAsset fixedAsset, boolean isFirstYear) {
    float prorataTemporis = 1;
    if (isFirstYear && fixedAsset.getFixedAssetCategory().getIsProrataTemporis()) {

      LocalDate acquisitionDate = fixedAsset.getAcquisitionDate();
      LocalDate depreciationDate = fixedAsset.getFirstDepreciationDate();

      long monthsBetweenDates =
          ChronoUnit.MONTHS.between(
              acquisitionDate.withDayOfMonth(1), depreciationDate.withDayOfMonth(1));
      prorataTemporis = monthsBetweenDates / fixedAsset.getPeriodicityInMonth().floatValue();
    }
    return new BigDecimal(prorataTemporis);
  }

  private BigDecimal computeDepreciation(
      FixedAsset fixedAsset, BigDecimal residualValue, boolean isFirstYear) {

    int scale = Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice();
    int numberOfDepreciation =
        fixedAsset.getFixedAssetCategory().getIsProrataTemporis()
            ? fixedAsset.getNumberOfDepreciation() - 1
            : fixedAsset.getNumberOfDepreciation();
    float depreciationRate = 1f / numberOfDepreciation * 100f;
    BigDecimal ddRate = BigDecimal.ONE;
    BigDecimal prorataTemporis = this.computeProrataTemporis(fixedAsset, isFirstYear);
    if (fixedAsset.getComputationMethodSelect().equals("degressive")) {
      ddRate = fixedAsset.getDegressiveCoef();
    }
    return residualValue
        .multiply(new BigDecimal(depreciationRate))
        .multiply(ddRate)
        .multiply(prorataTemporis)
        .divide(new BigDecimal(100), scale);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public List<FixedAsset> createFixedAssets(Invoice invoice) throws AxelorException {

    if (invoice == null || CollectionUtils.isEmpty(invoice.getInvoiceLineList())) {
      return null;
    }

    AccountConfig accountConfig =
        Beans.get(AccountConfigService.class).getAccountConfig(invoice.getCompany());
    List<FixedAsset> fixedAssetList = new ArrayList<FixedAsset>();

    for (InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {

      if (accountConfig.getFixedAssetCatReqOnInvoice()
          && invoiceLine.getFixedAssets()
          && invoiceLine.getFixedAssetCategory() == null) {
        throw new AxelorException(
            invoiceLine,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.INVOICE_LINE_ERROR_FIXED_ASSET_CATEGORY),
            invoiceLine.getProductName());
      }

      if (!invoiceLine.getFixedAssets() || invoiceLine.getFixedAssetCategory() == null) {
        continue;
      }

      FixedAsset fixedAsset = new FixedAsset();
      fixedAsset.setFixedAssetCategory(invoiceLine.getFixedAssetCategory());
      if (fixedAsset.getFixedAssetCategory().getIsValidateFixedAsset()) {
        fixedAsset.setStatusSelect(FixedAssetRepository.STATUS_VALIDATED);
      } else {
        fixedAsset.setStatusSelect(FixedAssetRepository.STATUS_DRAFT);
      }
      fixedAsset.setAcquisitionDate(invoice.getInvoiceDate());
      fixedAsset.setFirstDepreciationDate(invoice.getInvoiceDate());
      fixedAsset.setReference(invoice.getInvoiceId());
      fixedAsset.setName(invoiceLine.getProductName() + " (" + invoiceLine.getQty() + ")");
      fixedAsset.setCompany(fixedAsset.getFixedAssetCategory().getCompany());
      fixedAsset.setJournal(fixedAsset.getFixedAssetCategory().getJournal());
      fixedAsset.setComputationMethodSelect(
          fixedAsset.getFixedAssetCategory().getComputationMethodSelect());
      fixedAsset.setDegressiveCoef(fixedAsset.getFixedAssetCategory().getDegressiveCoef());
      fixedAsset.setNumberOfDepreciation(
          fixedAsset.getFixedAssetCategory().getNumberOfDepreciation());
      fixedAsset.setPeriodicityInMonth(fixedAsset.getFixedAssetCategory().getPeriodicityInMonth());
      fixedAsset.setDurationInMonth(fixedAsset.getFixedAssetCategory().getDurationInMonth());
      fixedAsset.setGrossValue(invoiceLine.getCompanyExTaxTotal());
      fixedAsset.setPartner(invoice.getPartner());
      fixedAsset.setPurchaseAccount(invoiceLine.getAccount());
      fixedAsset.setInvoiceLine(invoiceLine);

      this.generateAndcomputeLines(fixedAsset);

      fixedAssetList.add(fixedAssetRepo.save(fixedAsset));
    }
    return fixedAssetList;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void disposal(LocalDate disposalDate, BigDecimal disposalAmount, FixedAsset fixedAsset)
      throws AxelorException {

    Map<Integer, List<FixedAssetLine>> FixedAssetLineMap =
        fixedAsset
            .getFixedAssetLineList()
            .stream()
            .collect(Collectors.groupingBy(fa -> fa.getStatusSelect()));
    List<FixedAssetLine> previousPlannedLineList =
        FixedAssetLineMap.get(FixedAssetLineRepository.STATUS_PLANNED);
    List<FixedAssetLine> previousRealizedLineList =
        FixedAssetLineMap.get(FixedAssetLineRepository.STATUS_REALIZED);
    FixedAssetLine previousPlannedLine =
        previousPlannedLineList != null && !previousPlannedLineList.isEmpty()
            ? previousPlannedLineList.get(0)
            : null;
    FixedAssetLine previousRealizedLine =
        previousRealizedLineList != null && !previousRealizedLineList.isEmpty()
            ? previousRealizedLineList.get(previousRealizedLineList.size() - 1)
            : null;

    if (previousPlannedLine != null
        && disposalDate.isAfter(previousPlannedLine.getDepreciationDate())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.FIXED_ASSET_DISPOSAL_DATE_ERROR_2));
    }

    if (previousRealizedLine != null
        && disposalDate.isBefore(previousRealizedLine.getDepreciationDate())) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.FIXED_ASSET_DISPOSAL_DATE_ERROR_1));
    }

    if (disposalAmount.compareTo(BigDecimal.ZERO) != 0) {

      FixedAssetLine depreciationFixedAssetLine =
          generateProrataDepreciationLine(fixedAsset, disposalDate, previousRealizedLine);
      fixedAssetLineService.realize(depreciationFixedAssetLine);
      fixedAssetLineService.generateDisposalMove(depreciationFixedAssetLine);
    } else {
      if (disposalAmount.compareTo(fixedAsset.getResidualValue()) != 0) {
        return;
      }
    }
    List<FixedAssetLine> fixedAssetLineList =
        fixedAsset
            .getFixedAssetLineList()
            .stream()
            .filter(
                fixedAssetLine ->
                    fixedAssetLine.getStatusSelect() == FixedAssetLineRepository.STATUS_PLANNED)
            .collect(Collectors.toList());
    for (FixedAssetLine fixedAssetLine : fixedAssetLineList) {
      fixedAsset.removeFixedAssetLineListItem(fixedAssetLine);
    }
    fixedAsset.setStatusSelect(FixedAssetRepository.STATUS_TRANSFERRED);
    fixedAsset.setDisposalDate(disposalDate);
    fixedAsset.setDisposalValue(disposalAmount);
    fixedAssetRepo.save(fixedAsset);
  }

  private FixedAssetLine generateProrataDepreciationLine(
      FixedAsset fixedAsset, LocalDate disposalDate, FixedAssetLine previousRealizedLine) {

    LocalDate previousRealizedDate =
        previousRealizedLine != null
            ? previousRealizedLine.getDepreciationDate()
            : fixedAsset.getFirstDepreciationDate();
    long monthsBetweenDates =
        ChronoUnit.MONTHS.between(
            previousRealizedDate.withDayOfMonth(1), disposalDate.withDayOfMonth(1));

    FixedAssetLine fixedAssetLine = new FixedAssetLine();
    fixedAssetLine.setDepreciationDate(disposalDate);
    BigDecimal prorataTemporis =
        new BigDecimal(monthsBetweenDates / fixedAsset.getPeriodicityInMonth().floatValue());

    int scale = Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice();
    int numberOfDepreciation =
        fixedAsset.getFixedAssetCategory().getIsProrataTemporis()
            ? fixedAsset.getNumberOfDepreciation() - 1
            : fixedAsset.getNumberOfDepreciation();
    float depreciationRate = 1f / numberOfDepreciation * 100f;
    BigDecimal ddRate = BigDecimal.ONE;
    if (fixedAsset.getComputationMethodSelect().equals("degressive")) {
      ddRate = fixedAsset.getDegressiveCoef();
    }
    BigDecimal deprecationValue =
        fixedAsset
            .getGrossValue()
            .multiply(new BigDecimal(depreciationRate))
            .multiply(ddRate)
            .multiply(prorataTemporis)
            .divide(new BigDecimal(100), scale);

    fixedAssetLine.setDepreciation(deprecationValue);
    BigDecimal cumulativeValue =
        previousRealizedLine != null
            ? previousRealizedLine.getCumulativeDepreciation().add(deprecationValue)
            : deprecationValue;
    fixedAssetLine.setCumulativeDepreciation(cumulativeValue);
    fixedAssetLine.setResidualValue(
        fixedAsset.getGrossValue().subtract(fixedAssetLine.getCumulativeDepreciation()));
    fixedAsset.addFixedAssetLineListItem(fixedAssetLine);
    return fixedAssetLine;
  }
}
