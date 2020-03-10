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
package com.axelor.apps.base.service;

import static com.axelor.apps.base.service.administration.AbstractBatch.FETCH_LIMIT;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.ABCAnalysis;
import com.axelor.apps.base.db.ABCAnalysisClass;
import com.axelor.apps.base.db.ABCAnalysisLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Sequence;
import com.axelor.apps.base.db.repo.ABCAnalysisClassRepository;
import com.axelor.apps.base.db.repo.ABCAnalysisLineRepository;
import com.axelor.apps.base.db.repo.ABCAnalysisRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.report.IReport;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ABCAnalysisServiceImpl implements ABCAnalysisService {
  protected ABCAnalysisLineRepository abcAnalysisLineRepository;
  protected UnitConversionService unitConversionService;
  protected SequenceService sequenceService;
  protected ABCAnalysisRepository abcAnalysisRepository;
  protected ProductRepository productRepository;
  protected ABCAnalysisClassRepository abcAnalysisClassRepository;

  private BigDecimal totalQty = BigDecimal.ZERO;
  private BigDecimal totalWorth = BigDecimal.ZERO;

  private BigDecimal cumulatedQty = BigDecimal.valueOf(0, 3);
  private BigDecimal cumulatedWorth = BigDecimal.valueOf(0, 3);

  private final String abcAnalysisSequenceCode = "abcAnalysis";

  private List<ABCAnalysisClass> abcAnalysisClassList;

  @Inject
  public ABCAnalysisServiceImpl(
      ABCAnalysisLineRepository abcAnalysisLineRepository,
      UnitConversionService unitConversionService,
      ABCAnalysisRepository abcAnalysisRepository,
      ProductRepository productRepository,
      ABCAnalysisClassRepository abcAnalysisClassRepository,
      SequenceService sequenceService) {
    this.abcAnalysisLineRepository = abcAnalysisLineRepository;
    this.unitConversionService = unitConversionService;
    this.abcAnalysisRepository = abcAnalysisRepository;
    this.productRepository = productRepository;
    this.abcAnalysisClassRepository = abcAnalysisClassRepository;
    this.sequenceService = sequenceService;
  }

  @Override
  public List<ABCAnalysisClass> initABCClasses() {
    List<ABCAnalysisClass> abcAnalysisClassList = new ArrayList<>();

    abcAnalysisClassList.add(
        createAbcClass("A", 0, BigDecimal.valueOf(80), BigDecimal.valueOf(20)));
    abcAnalysisClassList.add(
        createAbcClass("B", 1, BigDecimal.valueOf(15), BigDecimal.valueOf(30)));
    abcAnalysisClassList.add(createAbcClass("C", 2, BigDecimal.valueOf(5), BigDecimal.valueOf(50)));

    return abcAnalysisClassList;
  }

  protected ABCAnalysisClass createAbcClass(
      String name, Integer sequence, BigDecimal worth, BigDecimal qty) {
    ABCAnalysisClass abcAnalysisClass = new ABCAnalysisClass();

    abcAnalysisClass.setName(name);
    abcAnalysisClass.setSequence(sequence);
    abcAnalysisClass.setWorth(worth);
    abcAnalysisClass.setQty(qty);

    return abcAnalysisClass;
  }

  @Override
  @Transactional
  public void reset(ABCAnalysis abcAnalysis) {
    abcAnalysisLineRepository
        .all()
        .filter("self.abcAnalysis.id = :abcAnalysisId")
        .bind("abcAnalysisId", abcAnalysis.getId())
        .remove();
    abcAnalysis.setStatusSelect(ABCAnalysisRepository.STATUS_DRAFT);
    abcAnalysisRepository.save(abcAnalysis);
  }

  @Override
  public void runAnalysis(ABCAnalysis abcAnalysis) throws AxelorException {
    reset(abcAnalysis);
    start(abcAnalysis);
    getAbcAnalysisClassList(abcAnalysis);
    createAllABCAnalysisLine(abcAnalysis);
    doAnalysis(abcAnalysisRepository.find(abcAnalysis.getId()));
    finish(abcAnalysisRepository.find(abcAnalysis.getId()));
  }

  @Transactional
  protected void start(ABCAnalysis abcAnalysis) {
    abcAnalysis.setStatusSelect(ABCAnalysisRepository.STATUS_ANALYZING);
    abcAnalysisRepository.save(abcAnalysis);
  }

  protected void getAbcAnalysisClassList(ABCAnalysis abcAnalysis) {
    Query<ABCAnalysisClass> abcAnalysisClassQuery =
        abcAnalysisClassRepository
            .all()
            .filter("self.abcAnalysis.id = :abcAnalysisId")
            .bind("abcAnalysisId", abcAnalysis.getId())
            .order("sequence");
    this.abcAnalysisClassList = abcAnalysisClassQuery.fetch();
  }

  protected Set<Product> getProductSet(ABCAnalysis abcAnalysis) {
    Set<Product> productList = new HashSet<>();
    String productCategoryQuery = getProductCategoryQuery();
    String productFamilyQuery = getProductFamilyQuery();

    if (!abcAnalysis.getProductSet().isEmpty()) {
      productList.addAll(abcAnalysis.getProductSet());
    }

    if (!abcAnalysis.getProductCategorySet().isEmpty()) {
      productList.addAll(
          productRepository
              .all()
              .filter(
                  productCategoryQuery,
                  abcAnalysis.getProductCategorySet(),
                  ProductRepository.PRODUCT_TYPE_STORABLE)
              .fetch());
    }

    if (!abcAnalysis.getProductFamilySet().isEmpty()) {
      productList.addAll(
          productRepository
              .all()
              .filter(
                  productFamilyQuery,
                  abcAnalysis.getProductFamilySet(),
                  ProductRepository.PRODUCT_TYPE_STORABLE)
              .fetch());
    }

    return productList;
  }

  protected String getProductCategoryQuery() {
    return "self.productCategory in (?1) AND self.productTypeSelect = ?2";
  }

  protected String getProductFamilyQuery() {
    return "self.productFamily in (?1) AND self.productTypeSelect = ?2";
  }

  protected void createAllABCAnalysisLine(ABCAnalysis abcAnalysis) throws AxelorException {
    int offset = 0;

    List<Product> productList;
    Query<Product> productQuery =
        productRepository
            .all()
            .filter("self.id IN (" + StringTool.getIdListString(getProductSet(abcAnalysis)) + ")");

    while (!(productList = productQuery.fetch(FETCH_LIMIT, offset)).isEmpty()) {
      abcAnalysis = abcAnalysisRepository.find(abcAnalysis.getId());
      offset += productList.size();

      for (Product product : productList) {
        product = productRepository.find(product.getId());
        createABCAnalysisLineForEachProduct(abcAnalysis, product);
      }

      JPA.clear();
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void createABCAnalysisLineForEachProduct(ABCAnalysis abcAnalysis, Product product)
      throws AxelorException {
    Optional<ABCAnalysisLine> optionalAbcAnalysisLine = createABCAnalysisLine(abcAnalysis, product);
    optionalAbcAnalysisLine.ifPresent(
        abcAnalysisLine -> {
          abcAnalysisLine = abcAnalysisLineRepository.find(abcAnalysisLine.getId());
          if (abcAnalysisLine.getDecimalWorth().compareTo(BigDecimal.ZERO) == 0
              && abcAnalysisLine.getDecimalQty().compareTo(BigDecimal.ZERO) == 0) {
            abcAnalysisLineRepository.remove(
                abcAnalysisLineRepository.find(abcAnalysisLine.getId()));
          }
        });
  }

  @Transactional(rollbackOn = {Exception.class})
  protected Optional<ABCAnalysisLine> createABCAnalysisLine(
      ABCAnalysis abcAnalysis, Product product) throws AxelorException {
    ABCAnalysisLine abcAnalysisLine = new ABCAnalysisLine();

    abcAnalysisLine.setAbcAnalysis(abcAnalysis);
    abcAnalysisLine.setProduct(product);

    abcAnalysisLineRepository.save(abcAnalysisLine);

    return Optional.of(abcAnalysisLine);
  }

  @Transactional
  protected void setQtyWorth(
      ABCAnalysisLine abcAnalysisLine, BigDecimal decimalQty, BigDecimal decimalWorth) {
    abcAnalysisLine.setDecimalQty(decimalQty);
    abcAnalysisLine.setDecimalWorth(decimalWorth);
    abcAnalysisLineRepository.save(abcAnalysisLine);
  }

  protected void doAnalysis(ABCAnalysis abcAnalysis) {
    List<ABCAnalysisLine> abcAnalysisLineList;
    int offset = 0;
    Query<ABCAnalysisLine> query =
        abcAnalysisLineRepository
            .all()
            .filter("self.abcAnalysis.id = :abcAnalysisId")
            .bind("abcAnalysisId", abcAnalysis.getId())
            .order("-decimalWorth")
            .order("id");

    while (!(abcAnalysisLineList = query.fetch(FETCH_LIMIT, offset)).isEmpty()) {
      offset += abcAnalysisLineList.size();
      abcAnalysisLineList.forEach(this::analyzeLine);
      JPA.clear();
    }
  }

  @Transactional
  protected void analyzeLine(ABCAnalysisLine abcAnalysisLine) {
    computePercentage(abcAnalysisLine);
    setABCAnalysisClass(abcAnalysisLine);

    abcAnalysisLineRepository.save(abcAnalysisLine);
  }

  protected void computePercentage(ABCAnalysisLine abcAnalysisLine) {
    BigDecimal qty = BigDecimal.ZERO;
    if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
      qty =
          abcAnalysisLine
              .getDecimalQty()
              .multiply(BigDecimal.valueOf(100))
              .divide(totalQty, 3, RoundingMode.HALF_EVEN);
    }

    BigDecimal worth = BigDecimal.ZERO;
    if (totalWorth.compareTo(BigDecimal.ZERO) > 0) {
      worth =
          abcAnalysisLine
              .getDecimalWorth()
              .multiply(BigDecimal.valueOf(100))
              .divide(totalWorth, 3, RoundingMode.HALF_EVEN);
    }

    incCumulatedQty(qty);
    incCumulatedWorth(worth);

    abcAnalysisLine.setQty(qty);
    abcAnalysisLine.setCumulatedQty(cumulatedQty);
    abcAnalysisLine.setWorth(worth);
    abcAnalysisLine.setCumulatedWorth(cumulatedWorth);
  }

  protected void setABCAnalysisClass(ABCAnalysisLine abcAnalysisLine) {
    BigDecimal maxQty = BigDecimal.ZERO;
    BigDecimal maxWorth = BigDecimal.ZERO;
    BigDecimal lineCumulatedQty =
        abcAnalysisLine.getCumulatedQty().setScale(2, RoundingMode.HALF_EVEN);
    BigDecimal lineCumulatedWorth =
        abcAnalysisLine.getCumulatedWorth().setScale(2, RoundingMode.HALF_EVEN);

    for (ABCAnalysisClass abcAnalysisClass : abcAnalysisClassList) {
      maxQty = maxQty.add(abcAnalysisClass.getQty());
      maxWorth = maxWorth.add(abcAnalysisClass.getWorth());
      if (lineCumulatedQty.compareTo(maxQty) <= 0 && lineCumulatedWorth.compareTo(maxWorth) <= 0) {
        abcAnalysisLine.setAbcAnalysisClass(
            abcAnalysisClassRepository.find(abcAnalysisClass.getId()));
        break;
      }
    }
  }

  protected void incTotalQty(BigDecimal totalQty) {
    this.totalQty = this.totalQty.add(totalQty);
  }

  protected void incTotalWorth(BigDecimal totalWorth) {
    this.totalWorth = this.totalWorth.add(totalWorth);
  }

  protected void incCumulatedQty(BigDecimal cumulatedQty) {
    this.cumulatedQty = this.cumulatedQty.add(cumulatedQty);
  }

  protected void incCumulatedWorth(BigDecimal cumulatedWorth) {
    this.cumulatedWorth = this.cumulatedWorth.add(cumulatedWorth);
  }

  @Transactional
  protected void finish(ABCAnalysis abcAnalysis) {
    abcAnalysis.setStatusSelect(ABCAnalysisRepository.STATUS_FINISHED);
    abcAnalysisRepository.save(abcAnalysis);
  }

  @Override
  public void setSequence(ABCAnalysis abcAnalysis) {
    String abcAnalysisSequence = abcAnalysis.getAbcAnalysisSeq();

    if (abcAnalysisSequence != null && !abcAnalysisSequence.isEmpty()) {
      return;
    }

    Sequence sequence =
        sequenceService.getSequence(abcAnalysisSequenceCode, abcAnalysis.getCompany());

    if (sequence == null) {
      return;
    }

    abcAnalysis.setAbcAnalysisSeq(sequenceService.getSequenceNumber(sequence));
  }

  @Override
  public String printReport(ABCAnalysis abcAnalysis, String reportType) throws AxelorException {
    if (abcAnalysis.getStatusSelect() != ABCAnalysisRepository.STATUS_FINISHED) {
      throw new AxelorException(
          abcAnalysis,
          TraceBackRepository.TYPE_FUNCTIONNAL,
          I18n.get(IExceptionMessage.ABC_CLASSES_INVALID_STATE_FOR_REPORTING));
    }

    String name = I18n.get("ABC Analysis") + " - " + abcAnalysis.getAbcAnalysisSeq();

    return ReportFactory.createReport(IReport.ABC_ANALYSIS, name)
        .addParam("abcAnalysisId", abcAnalysis.getId())
        .addParam("Locale", ReportSettings.getPrintingLocale(null))
        .addFormat(reportType)
        .toAttach(abcAnalysis)
        .generate()
        .getFileLink();
  }

  @Override
  public void checkClasses(ABCAnalysis abcAnalysis) throws AxelorException {
    List<ABCAnalysisClass> abcAnalysisClassList = abcAnalysis.getAbcAnalysisClassList();
    BigDecimal classQty, classWorth;
    BigDecimal totalQty = BigDecimal.ZERO, totalWorth = BigDecimal.ZERO;
    BigDecimal comparisonValue = new BigDecimal(100);

    for (ABCAnalysisClass abcAnalysisClass : abcAnalysisClassList) {
      classQty = abcAnalysisClass.getQty();
      classWorth = abcAnalysisClass.getWorth();
      if (classQty.compareTo(BigDecimal.ZERO) <= 0 || classWorth.compareTo(BigDecimal.ZERO) <= 0) {
        throw new AxelorException(
            abcAnalysis,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.ABC_CLASSES_NEGATIVE_OR_NULL_QTY_OR_WORTH));
      }

      totalQty = totalQty.add(classQty);
      totalWorth = totalWorth.add(classWorth);
    }

    if (totalQty.compareTo(comparisonValue) != 0 || totalWorth.compareTo(comparisonValue) != 0) {
      throw new AxelorException(
          abcAnalysis,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.ABC_CLASSES_INVALID_QTY_OR_WORTH));
    }
  }
}
