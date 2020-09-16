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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AnalyticDistributionTemplate;
import com.axelor.apps.account.db.FixedAsset;
import com.axelor.apps.account.db.FixedAssetLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.FixedAssetLineRepository;
import com.axelor.apps.account.db.repo.FixedAssetRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class FixedAssetServiceImpl implements FixedAssetService {

  @Inject FixedAssetRepository fixedAssetRepo;

  @Inject FixedAssetLineService fixedAssetLineService;

  protected MoveLineService moveLineService;

  protected static int calculationScale = 6;

  @Inject
  public FixedAssetServiceImpl(MoveLineService moveLineService) {
    this.moveLineService = moveLineService;
  }

  @Override
  public FixedAsset generateAndcomputeLines(FixedAsset fixedAsset) {
    boolean isLinear =
        fixedAsset
            .getComputationMethodSelect()
            .equals(FixedAssetRepository.COMPUTATION_METHOD_LINEAR);
    BigDecimal depreciationValue = this.computeDepreciationValue(fixedAsset, isLinear);
    BigDecimal cumulativeValue = depreciationValue;
    LocalDate depreciationDate = fixedAsset.getFirstDepreciationDate();
    LocalDate firstDepreciationDate = fixedAsset.getFirstDepreciationDate();
    LocalDate acquisitionDate = fixedAsset.getAcquisitionDate();
    int numberOfDepreciation = fixedAsset.getNumberOfDepreciation();
    boolean isFirstPeriodDay =
        DateTool.minusMonths(firstDepreciationDate, fixedAsset.getPeriodicityInMonth())
            .plusDays(1)
            .equals(acquisitionDate);
    boolean isProrataTemporis =
        fixedAsset.getFixedAssetCategory().getIsProrataTemporis()
            && !fixedAsset.getAcquisitionDate().equals(fixedAsset.getFirstDepreciationDate())
            && !isFirstPeriodDay;
    LocalDate endDate = DateTool.plusMonths(depreciationDate, fixedAsset.getDurationInMonth());
    int counter = 1;
    int scale = Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice();

    numberOfDepreciation--;
    while (depreciationDate.isBefore(endDate.plusDays(1))
        && depreciationValue.compareTo(BigDecimal.ZERO) > 0
        && ((isProrataTemporis && counter <= numberOfDepreciation + 2)
            || (!isProrataTemporis && counter <= numberOfDepreciation + 1))) {
      FixedAssetLine fixedAssetLine = new FixedAssetLine();
      fixedAssetLine.setStatusSelect(FixedAssetLineRepository.STATUS_PLANNED);
      fixedAssetLine.setDepreciationDate(depreciationDate);
      // case : cumulativeValue > grossValue, i have to reduce the exceed amount
      if (cumulativeValue.compareTo(fixedAsset.getGrossValue()) > 0) {
        fixedAssetLine.setDepreciation(
            depreciationValue.subtract(cumulativeValue.subtract(fixedAsset.getGrossValue())));
        fixedAssetLine.setCumulativeDepreciation(fixedAsset.getGrossValue());
        fixedAssetLine.setResidualValue(BigDecimal.ZERO);
      } else {
        fixedAssetLine.setDepreciation(depreciationValue);
        fixedAssetLine.setCumulativeDepreciation(cumulativeValue);
        fixedAssetLine.setResidualValue(
            fixedAsset.getGrossValue().subtract(fixedAssetLine.getCumulativeDepreciation()));
      }

      fixedAsset.addFixedAssetLineListItem(fixedAssetLine);
      if ((!isLinear && counter == numberOfDepreciation)
          || (isLinear && (isProrataTemporis && counter == numberOfDepreciation + 1)
              || (!isProrataTemporis && counter == numberOfDepreciation))) {
        depreciationValue = fixedAssetLine.getResidualValue();
        cumulativeValue = cumulativeValue.add(depreciationValue);

        depreciationDate = addPeriodicity(fixedAsset, firstDepreciationDate, counter);
        if (isProrataTemporis) {

          endDate =
              DateTool.plusMonths(acquisitionDate, fixedAsset.getDurationInMonth()).minusDays(1);
          depreciationDate = endDate;
        }
        counter++;
        continue;
      }

      if (fixedAsset
          .getComputationMethodSelect()
          .equals(FixedAssetRepository.COMPUTATION_METHOD_DEGRESSIVE)) {
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
              this.computeDepreciation(
                  fixedAsset, fixedAssetLine.getResidualValue(), false, isLinear);
        }

        depreciationDate = addPeriodicity(fixedAsset, firstDepreciationDate, counter);
      } else {
        depreciationValue =
            this.computeDepreciation(fixedAsset, fixedAsset.getGrossValue(), false, isLinear);

        depreciationDate = addPeriodicity(fixedAsset, firstDepreciationDate, counter);
      }
      depreciationValue = depreciationValue.setScale(scale, RoundingMode.HALF_EVEN);
      cumulativeValue =
          cumulativeValue.add(depreciationValue).setScale(scale, RoundingMode.HALF_EVEN);
      counter++;
    }
    return fixedAsset;
  }

  protected LocalDate addPeriodicity(
      FixedAsset fixedAsset, LocalDate fisrtDepreciationDate, int counter) {
    LocalDate depreciationDate;
    depreciationDate =
        DateTool.plusMonths(fisrtDepreciationDate, fixedAsset.getPeriodicityInMonth() * counter);
    // Manage Leap year, february with 29 days.
    if (depreciationDate.getMonthValue() == 2) {
      depreciationDate =
          fisrtDepreciationDate.plusMonths(fixedAsset.getPeriodicityInMonth() * counter);
    }
    return depreciationDate;
  }

  protected BigDecimal computeDepreciationValue(FixedAsset fixedAsset, boolean isLinear) {
    BigDecimal depreciationValue = BigDecimal.ZERO;
    depreciationValue =
        this.computeDepreciation(fixedAsset, fixedAsset.getGrossValue(), true, isLinear);
    return depreciationValue;
  }

  protected BigDecimal computeProrataTemporis(FixedAsset fixedAsset, boolean isFirstYear) {
    BigDecimal prorataTemporis = BigDecimal.ONE;
    if (isFirstYear
        && fixedAsset.getFixedAssetCategory().getIsProrataTemporis()
        && !fixedAsset.getAcquisitionDate().equals(fixedAsset.getFirstDepreciationDate())) {

      LocalDate acquisitionDate = fixedAsset.getAcquisitionDate();
      LocalDate depreciationDate = fixedAsset.getFirstDepreciationDate();

      int acquisitionYear = acquisitionDate.getYear();
      Month acquisitionMonth = acquisitionDate.getMonth();
      int acquisitionDay = acquisitionDate.getDayOfMonth();
      int depreciationYear = depreciationDate.getYear();
      Month depreciationMonth = depreciationDate.getMonth();
      int depreciationDay = depreciationDate.getDayOfMonth();

      // US way
      if (fixedAsset.getFixedAssetCategory().getIsUSProrataTemporis()) {

        if (acquisitionMonth == Month.FEBRUARY
            && depreciationMonth == Month.FEBRUARY
            && isLastDayOfFebruary(acquisitionYear, acquisitionDay)
            && isLastDayOfFebruary(depreciationYear, depreciationDay)) {
          depreciationDay = 30;
        }

        if (acquisitionMonth == Month.FEBRUARY
            && isLastDayOfFebruary(acquisitionYear, acquisitionDay)) {
          acquisitionDay = 30;
        }

        if (acquisitionDay >= 30 && depreciationDay > 30) {
          depreciationDay = 30;
        }

        if (acquisitionDay > 30) {
          acquisitionDay = 30;
        }

      } else { // European way

        if (acquisitionDay == 31) {
          acquisitionDay = 30;
        }

        if (depreciationDay == 31) {
          depreciationDay = 30;
        }
      }

      BigDecimal nbDaysBetweenAcqAndFirstDepDate =
          BigDecimal.valueOf(
                  360 * (depreciationYear - acquisitionYear)
                      + 30 * (depreciationMonth.getValue() - acquisitionMonth.getValue())
                      + (depreciationDay - acquisitionDay))
              .setScale(calculationScale);
      BigDecimal nbDaysOfPeriod =
          BigDecimal.valueOf(fixedAsset.getPeriodicityInMonth() * 30).setScale(calculationScale);
      prorataTemporis =
          nbDaysBetweenAcqAndFirstDepDate
              .divide(nbDaysOfPeriod, BigDecimal.ROUND_HALF_EVEN)
              .setScale(calculationScale);
    }
    return prorataTemporis;
  }

  protected BigDecimal computeDepreciation(
      FixedAsset fixedAsset, BigDecimal residualValue, boolean isFirstYear, boolean isLinear) {

    int scale = Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice();
    int numberOfDepreciation =
        !isLinear && fixedAsset.getFixedAssetCategory().getIsProrataTemporis()
            ? fixedAsset.getNumberOfDepreciation() - 1
            : fixedAsset.getNumberOfDepreciation();
    BigDecimal depreciationRate =
        numberOfDepreciation == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(100)
                .divide(
                    BigDecimal.valueOf(numberOfDepreciation),
                    calculationScale,
                    BigDecimal.ROUND_HALF_EVEN);
    BigDecimal ddRate = BigDecimal.ONE;
    BigDecimal prorataTemporis = this.computeProrataTemporis(fixedAsset, isFirstYear);
    if (fixedAsset
        .getComputationMethodSelect()
        .equals(FixedAssetRepository.COMPUTATION_METHOD_DEGRESSIVE)) {
      ddRate = fixedAsset.getDegressiveCoef();
    }
    return residualValue
        .multiply(depreciationRate)
        .multiply(ddRate)
        .multiply(prorataTemporis)
        .divide(new BigDecimal(100), scale, BigDecimal.ROUND_HALF_EVEN);
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

  protected FixedAssetLine generateProrataDepreciationLine(
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
        BigDecimal.valueOf(monthsBetweenDates)
            .divide(
                BigDecimal.valueOf(fixedAsset.getPeriodicityInMonth()), BigDecimal.ROUND_HALF_EVEN)
            .setScale(calculationScale);

    int scale = Beans.get(AppBaseService.class).getNbDecimalDigitForUnitPrice();
    int numberOfDepreciation =
        fixedAsset.getFixedAssetCategory().getIsProrataTemporis()
            ? fixedAsset.getNumberOfDepreciation() - 1
            : fixedAsset.getNumberOfDepreciation();
    BigDecimal depreciationRate =
        numberOfDepreciation <= 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(100)
                .divide(
                    BigDecimal.valueOf(numberOfDepreciation),
                    calculationScale,
                    BigDecimal.ROUND_HALF_EVEN);
    BigDecimal ddRate = BigDecimal.ONE;
    if (fixedAsset
        .getComputationMethodSelect()
        .equals(FixedAssetRepository.COMPUTATION_METHOD_DEGRESSIVE)) {
      ddRate = fixedAsset.getDegressiveCoef();
    }
    BigDecimal deprecationValue =
        fixedAsset
            .getGrossValue()
            .multiply(depreciationRate)
            .multiply(ddRate)
            .multiply(prorataTemporis)
            .divide(new BigDecimal(100), scale, BigDecimal.ROUND_HALF_EVEN);

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

  @Override
  @Transactional
  public void createAnalyticOnMoveLine(
      AnalyticDistributionTemplate analyticDistributionTemplate, MoveLine moveLine)
      throws AxelorException {
    if (analyticDistributionTemplate != null
        && moveLine.getAccount().getAnalyticDistributionAuthorized()) {
      moveLine.setAnalyticDistributionTemplate(analyticDistributionTemplate);
      moveLine = moveLineService.createAnalyticDistributionWithTemplate(moveLine);
    }
  }

  @Override
  public void updateAnalytic(FixedAsset fixedAsset) throws AxelorException {
    if (fixedAsset.getAnalyticDistributionTemplate() != null) {
      if (fixedAsset.getDisposalMove() != null) {
        for (MoveLine moveLine : fixedAsset.getDisposalMove().getMoveLineList()) {
          this.createAnalyticOnMoveLine(fixedAsset.getAnalyticDistributionTemplate(), moveLine);
        }
      }
      if (fixedAsset.getFixedAssetLineList() != null) {
        for (FixedAssetLine fixedAssetLine : fixedAsset.getFixedAssetLineList()) {
          if (fixedAssetLine.getDepreciationAccountMove() != null) {
            for (MoveLine moveLine :
                fixedAssetLine.getDepreciationAccountMove().getMoveLineList()) {
              this.createAnalyticOnMoveLine(fixedAsset.getAnalyticDistributionTemplate(), moveLine);
            }
          }
        }
      }
    }
  }

  protected boolean isLastDayOfFebruary(int year, int day) {
    Calendar calendar = Calendar.getInstance();
    calendar.set(year, Calendar.FEBRUARY, 1);
    int maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    return maxDays == day;
  }
}
