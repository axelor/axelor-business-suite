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
package com.axelor.apps.stock.service;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.stock.db.FreightCarrierCustomerAccountNumber;
import com.axelor.apps.stock.db.LogisticalForm;
import com.axelor.apps.stock.db.LogisticalFormLine;
import com.axelor.apps.stock.db.StockConfig;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.LogisticalFormLineRepository;
import com.axelor.apps.stock.db.repo.LogisticalFormRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.exception.LogisticalFormError;
import com.axelor.apps.stock.exception.LogisticalFormWarning;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.tool.QueryBuilder;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.JPA;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.Context;
import com.axelor.rpc.ContextEntity;
import com.axelor.script.GroovyScriptHelper;
import com.axelor.script.ScriptHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.TypedQuery;
import org.apache.commons.lang3.tuple.Pair;

public class LogisticalFormServiceImpl implements LogisticalFormService {

  @Inject ProductRepository productRepository;

  @Override
  public void addDetailLines(LogisticalForm logisticalForm, StockMove stockMove)
      throws AxelorException {
    Objects.requireNonNull(logisticalForm);
    Objects.requireNonNull(stockMove);

    if (logisticalForm.getDeliverToCustomerPartner() != null
        && !logisticalForm.getDeliverToCustomerPartner().equals(stockMove.getPartner())) {
      throw new AxelorException(
          logisticalForm,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.LOGISTICAL_FORM_PARTNER_MISMATCH),
          logisticalForm.getDeliverToCustomerPartner().getName());
    }

    if (stockMove.getStockMoveLineList() == null) {
      return;
    }

    StockMoveLineService stockMoveLineService = Beans.get(StockMoveLineService.class);
    List<Pair<StockMoveLine, BigDecimal>> toAddList = new ArrayList<>();

    for (StockMoveLine stockMoveLine : stockMove.getStockMoveLineList()) {
      BigDecimal spreadableQty =
          stockMoveLineService.computeSpreadableQtyOverLogisticalFormLines(
              stockMoveLine, logisticalForm);

      if (spreadableQty.signum() <= 0) {
        continue;
      }

      if (testForDetailLine(stockMoveLine)) {
        toAddList.add(Pair.of(stockMoveLine, spreadableQty));
      }
    }

    if (!toAddList.isEmpty()) {
      if (logisticalForm.getLogisticalFormLineList() == null
          || logisticalForm.getLogisticalFormLineList().isEmpty()) {
        addParcelPalletLine(logisticalForm, LogisticalFormLineRepository.TYPE_PARCEL);
      }

      toAddList.forEach(item -> addDetailLine(logisticalForm, item.getLeft(), item.getRight()));
    }
  }

  /**
   * Test for detail line (to be overridden).
   *
   * @param stockMoveLine
   * @return
   */
  @SuppressWarnings("all")
  protected boolean testForDetailLine(StockMoveLine stockMoveLine) {
    return true;
  }

  @Override
  public void checkLines(LogisticalForm logisticalForm)
      throws LogisticalFormWarning, LogisticalFormError {
    List<String> warningMessageList = new ArrayList<>();

    checkRequiredLineFields(logisticalForm);
    checkInvalidLineDimensions(logisticalForm);
    checkEmptyParcelPalletLines(logisticalForm, warningMessageList);
    checkInconsistentQties(logisticalForm, warningMessageList);

    if (!warningMessageList.isEmpty()) {
      String errorMessage =
          String.format(
              "<ul>%s</ul>",
              warningMessageList
                  .stream()
                  .map(message -> String.format("<li>%s</li>", message))
                  .collect(Collectors.joining("\n")));
      throw new LogisticalFormWarning(logisticalForm, errorMessage);
    }
  }

  protected void checkRequiredLineFields(LogisticalForm logisticalForm) throws LogisticalFormError {
    if (logisticalForm.getLogisticalFormLineList() == null) {
      return;
    }

    for (LogisticalFormLine logisticalFormLine : logisticalForm.getLogisticalFormLineList()) {
      if (logisticalFormLine.getTypeSelect() == 0) {
        throw new LogisticalFormError(
            logisticalFormLine,
            I18n.get(IExceptionMessage.LOGISTICAL_FORM_LINE_REQUIRED_TYPE),
            logisticalFormLine.getSequence() + 1);
      }

      if (logisticalFormLine.getTypeSelect() == LogisticalFormLineRepository.TYPE_DETAIL) {
        if (logisticalFormLine.getStockMoveLine() == null) {
          throw new LogisticalFormError(
              logisticalFormLine,
              I18n.get(IExceptionMessage.LOGISTICAL_FORM_LINE_REQUIRED_STOCK_MOVE_LINE),
              logisticalFormLine.getSequence() + 1);
        }
        if (logisticalFormLine.getQty() == null || logisticalFormLine.getQty().signum() <= 0) {
          throw new LogisticalFormError(
              logisticalFormLine,
              I18n.get(IExceptionMessage.LOGISTICAL_FORM_LINE_REQUIRED_QUANTITY),
              logisticalFormLine.getSequence() + 1);
        }
      }
    }
  }

  protected void checkInconsistentQties(
      LogisticalForm logisticalForm, List<String> errorMessageList) {
    Map<StockMoveLine, BigDecimal> spreadableQtyMap = getSpreadableQtyMap(logisticalForm);
    Map<StockMoveLine, BigDecimal> spreadQtyMap = getSpreadQtyMap(logisticalForm);
    Locale locale = new Locale(Beans.get(UserService.class).getLanguage());
    NumberFormat nf = NumberFormat.getInstance(locale);

    for (Entry<StockMoveLine, BigDecimal> entry : spreadableQtyMap.entrySet()) {
      StockMoveLine stockMoveLine = entry.getKey();
      BigDecimal spreadableQty = entry.getValue();

      if (spreadableQty.signum() != 0) {
        BigDecimal spreadQty = spreadQtyMap.getOrDefault(stockMoveLine, BigDecimal.ZERO);
        BigDecimal expectedQty = spreadQty.add(spreadableQty);
        String errorMessage =
            String.format(
                locale,
                I18n.get(IExceptionMessage.LOGISTICAL_FORM_LINES_INCONSISTENT_QUANTITY),
                String.format(
                    "%s (%s)",
                    stockMoveLine.getProductName(), stockMoveLine.getStockMove().getStockMoveSeq()),
                nf.format(spreadQty),
                nf.format(expectedQty));
        errorMessageList.add(errorMessage);
      }
    }
  }

  protected void checkEmptyParcelPalletLines(
      LogisticalForm logisticalForm, List<String> errorMessageList) throws LogisticalFormError {
    if (logisticalForm.getLogisticalFormLineList() == null) {
      return;
    }

    Map<LogisticalFormLine, BigDecimal> qtyMap = new HashMap<>();
    LogisticalFormLine currentLine = null;

    for (LogisticalFormLine logisticalFormLine : logisticalForm.getLogisticalFormLineList()) {
      if (logisticalFormLine.getTypeSelect() != LogisticalFormLineRepository.TYPE_DETAIL) {
        currentLine = logisticalFormLine;
        qtyMap.put(currentLine, BigDecimal.ZERO);
      } else {
        if (currentLine == null) {
          throw new LogisticalFormError(
              logisticalForm, I18n.get(IExceptionMessage.LOGISTICAL_FORM_LINES_ORPHAN_DETAIL));
        }
        qtyMap.merge(currentLine, logisticalFormLine.getQty(), BigDecimal::add);
      }
    }

    for (Entry<LogisticalFormLine, BigDecimal> entry : qtyMap.entrySet()) {
      LogisticalFormLine logisticalFormLine = entry.getKey();

      BigDecimal qty = entry.getValue();

      if (qty.signum() <= 0) {
        String msg;

        if (logisticalFormLine.getTypeSelect() == LogisticalFormLineRepository.TYPE_PARCEL) {
          msg = I18n.get(IExceptionMessage.LOGISTICAL_FORM_LINES_EMPTY_PARCEL);
        } else {
          msg = I18n.get(IExceptionMessage.LOGISTICAL_FORM_LINES_EMPTY_PALLET);
        }

        String errorMessage = String.format(msg, logisticalFormLine.getParcelPalletNumber());
        errorMessageList.add(errorMessage);
      }
    }
  }

  protected void checkInvalidLineDimensions(LogisticalForm logisticalForm)
      throws LogisticalFormError {
    if (logisticalForm.getLogisticalFormLineList() != null) {
      LogisticalFormLineService logisticalFormLineService =
          Beans.get(LogisticalFormLineService.class);
      for (LogisticalFormLine logisticalFormLine : logisticalForm.getLogisticalFormLineList()) {
        logisticalFormLineService.validateDimensions(logisticalFormLine);
      }
    }
  }

  @Override
  public List<StockMoveLine> getFullySpreadStockMoveLineList(LogisticalForm logisticalForm) {
    List<StockMoveLine> stockMoveLineList = new ArrayList<>();
    Map<StockMoveLine, BigDecimal> spreadableQtyMap = new HashMap<>();

    for (LogisticalForm item : findPendingLogisticalForms(logisticalForm)) {
      spreadableQtyMap.putAll(getSpreadableQtyMap(item));
    }

    for (Entry<StockMoveLine, BigDecimal> entry : spreadableQtyMap.entrySet()) {
      StockMoveLine stockMoveLine = entry.getKey();
      BigDecimal spreadableQty = entry.getValue();

      if (spreadableQty.signum() <= 0) {
        stockMoveLineList.add(stockMoveLine);
      }
    }

    return stockMoveLineList;
  }

  protected List<LogisticalForm> findPendingLogisticalForms(LogisticalForm logisticalForm) {
    Preconditions.checkNotNull(logisticalForm);
    Preconditions.checkNotNull(logisticalForm.getDeliverToCustomerPartner());

    QueryBuilder<LogisticalForm> queryBuilder = QueryBuilder.of(LogisticalForm.class);
    queryBuilder.add("self.deliverToCustomerPartner = :deliverToCustomerPartner");
    queryBuilder.bind("deliverToCustomerPartner", logisticalForm.getDeliverToCustomerPartner());
    queryBuilder.add("self.statusSelect < :statusSelect");
    queryBuilder.bind("statusSelect", LogisticalFormRepository.STATUS_COLLECTED);

    if (logisticalForm.getId() != null) {
      queryBuilder.add("self.id != :id");
      queryBuilder.bind("id", logisticalForm.getId());
    }

    List<LogisticalForm> logisticalFormList = queryBuilder.build().fetch();
    logisticalFormList.add(logisticalForm);

    return logisticalFormList;
  }

  protected List<StockMove> getFullySpreadStockMoveList(LogisticalForm logisticalForm) {
    List<StockMove> fullySpreadStockMoveList = new ArrayList<>();
    List<StockMoveLine> fullySpreadStockMoveLineList =
        getFullySpreadStockMoveLineList(logisticalForm);

    Set<StockMove> stockMoveSet = new HashSet<>();

    for (StockMoveLine stockMoveLine : fullySpreadStockMoveLineList) {
      stockMoveSet.add(stockMoveLine.getStockMove());
    }

    for (StockMove stockMove : stockMoveSet) {
      if (fullySpreadStockMoveLineList.containsAll(stockMove.getStockMoveLineList())) {
        fullySpreadStockMoveList.add(stockMove);
      }
    }

    return fullySpreadStockMoveList;
  }

  @Override
  public Map<StockMoveLine, BigDecimal> getSpreadableQtyMap(LogisticalForm logisticalForm) {
    Set<StockMove> stockMoveSet = new LinkedHashSet<>();
    Map<StockMoveLine, BigDecimal> spreadableQtyMap = new LinkedHashMap<>();

    if (logisticalForm.getLogisticalFormLineList() != null) {
      StockMoveLineService stockMoveLineService = Beans.get(StockMoveLineService.class);

      logisticalForm
          .getLogisticalFormLineList()
          .stream()
          .filter(
              logisticalFormLine ->
                  logisticalFormLine.getTypeSelect() == LogisticalFormLineRepository.TYPE_DETAIL
                      && logisticalFormLine.getStockMoveLine() != null
                      && logisticalFormLine.getStockMoveLine().getStockMove() != null)
          .forEach(
              logisticalFormLine ->
                  stockMoveSet.add(logisticalFormLine.getStockMoveLine().getStockMove()));

      for (StockMove stockMove : stockMoveSet) {
        if (stockMove.getStockMoveLineList() == null) {
          continue;
        }

        for (StockMoveLine stockMoveLine : stockMove.getStockMoveLineList()) {
          BigDecimal spreadableQty =
              stockMoveLineService.computeSpreadableQtyOverLogisticalFormLines(
                  stockMoveLine, logisticalForm);
          spreadableQtyMap.put(stockMoveLine, spreadableQty);
        }
      }
    }

    return spreadableQtyMap;
  }

  @Override
  public Map<StockMoveLine, BigDecimal> getSpreadQtyMap(LogisticalForm logisticalForm) {
    Map<StockMoveLine, BigDecimal> spreadQtyMap = new LinkedHashMap<>();

    if (logisticalForm.getLogisticalFormLineList() != null) {
      logisticalForm
          .getLogisticalFormLineList()
          .stream()
          .filter(
              logisticalFormLine ->
                  logisticalFormLine.getTypeSelect() == LogisticalFormLineRepository.TYPE_DETAIL)
          .forEach(
              logisticalFormLine -> {
                StockMoveLine stockMoveLine = logisticalFormLine.getStockMoveLine();
                if (stockMoveLine != null && logisticalFormLine.getQty() != null) {
                  spreadQtyMap.merge(stockMoveLine, logisticalFormLine.getQty(), BigDecimal::add);
                }
              });
    }

    return spreadQtyMap;
  }

  protected void addDetailLine(
      LogisticalForm logisticalForm, StockMoveLine stockMoveLine, BigDecimal qty) {
    Preconditions.checkNotNull(logisticalForm);
    Preconditions.checkNotNull(stockMoveLine);

    LogisticalFormLine logisticalFormLine =
        createLogisticalFormLine(logisticalForm, stockMoveLine, qty);
    addLogisticalFormLineListItem(logisticalForm, logisticalFormLine);
  }

  @Override
  public void addParcelPalletLine(LogisticalForm logisticalForm, int typeSelect) {
    LogisticalFormLine logisticalFormLine = new LogisticalFormLine();
    logisticalFormLine.setTypeSelect(typeSelect);
    logisticalFormLine.setParcelPalletNumber(getNextParcelPalletNumber(logisticalForm, typeSelect));
    logisticalFormLine.setSequence(getNextLineSequence(logisticalForm));
    addLogisticalFormLineListItem(logisticalForm, logisticalFormLine);
  }

  // Workaround for #9759
  protected void addLogisticalFormLineListItem(
      LogisticalForm logisticalForm, LogisticalFormLine logisticalFormLine) {
    if (logisticalForm instanceof ContextEntity) {
      List<LogisticalFormLine> logisticalFormLineList = logisticalForm.getLogisticalFormLineList();
      if (logisticalFormLineList == null) {
        logisticalFormLineList = new ArrayList<>();
        logisticalForm.setLogisticalFormLineList(logisticalFormLineList);
      }
      logisticalFormLineList.add(logisticalFormLine);
    } else {
      logisticalForm.addLogisticalFormLineListItem(logisticalFormLine);
    }
  }

  @Override
  public int getNextParcelPalletNumber(LogisticalForm logisticalForm, int typeSelect) {
    int highest = 0;
    Set<Integer> parcelPalletNumberSet = new HashSet<>();

    if (logisticalForm.getLogisticalFormLineList() != null) {
      for (LogisticalFormLine logisticalFormLine : logisticalForm.getLogisticalFormLineList()) {
        if (logisticalFormLine.getTypeSelect() == typeSelect
            && logisticalFormLine.getParcelPalletNumber() != null) {
          parcelPalletNumberSet.add(logisticalFormLine.getParcelPalletNumber());

          if (logisticalFormLine.getParcelPalletNumber() > highest) {
            highest = logisticalFormLine.getParcelPalletNumber();
          }
        }
      }
    }

    for (int i = 1; i < highest; ++i) {
      if (!parcelPalletNumberSet.contains(i)) {
        return i;
      }
    }

    return highest + 1;
  }

  @Override
  public int getNextLineSequence(LogisticalForm logisticalForm) {
    if (logisticalForm.getLogisticalFormLineList() == null) {
      return 0;
    }

    OptionalInt max =
        logisticalForm
            .getLogisticalFormLineList()
            .stream()
            .mapToInt(LogisticalFormLine::getSequence)
            .max();

    return max.isPresent() ? max.getAsInt() + 1 : 0;
  }

  @Override
  public void computeTotals(LogisticalForm logisticalForm) throws LogisticalFormError {
    BigDecimal totalNetMass = BigDecimal.ZERO;
    BigDecimal totalGrossMass = BigDecimal.ZERO;
    BigDecimal totalVolume = BigDecimal.ZERO;

    if (logisticalForm.getLogisticalFormLineList() != null) {
      ScriptHelper scriptHelper = getScriptHelper(logisticalForm);
      LogisticalFormLineService logisticalFormLineService =
          Beans.get(LogisticalFormLineService.class);

      for (LogisticalFormLine logisticalFormLine : logisticalForm.getLogisticalFormLineList()) {
        StockMoveLine stockMoveLine = logisticalFormLine.getStockMoveLine();

        if (logisticalFormLine.getTypeSelect() != LogisticalFormLineRepository.TYPE_DETAIL) {
          if (logisticalFormLine.getGrossMass() != null) {
            totalGrossMass = totalGrossMass.add(logisticalFormLine.getGrossMass());
          }

          totalVolume =
              totalVolume.add(
                  logisticalFormLineService.evalVolume(logisticalFormLine, scriptHelper));
        } else if (stockMoveLine != null) {
          totalNetMass =
              totalNetMass.add(logisticalFormLine.getQty().multiply(stockMoveLine.getNetMass()));
        }
      }

      totalVolume = totalVolume.divide(new BigDecimal(1_000_000), 10, RoundingMode.HALF_UP);
      logisticalForm.setTotalNetMass(totalNetMass.setScale(3, RoundingMode.HALF_EVEN));
      logisticalForm.setTotalGrossMass(totalGrossMass.setScale(3, RoundingMode.HALF_EVEN));
      logisticalForm.setTotalVolume(totalVolume.setScale(3, RoundingMode.HALF_EVEN));
    }
  }

  protected ScriptHelper getScriptHelper(LogisticalForm logisticalForm) {
    Context scriptContext = new Context(Mapper.toMap(logisticalForm), logisticalForm.getClass());
    return new GroovyScriptHelper(scriptContext);
  }

  @Override
  public String getStockMoveDomain(LogisticalForm logisticalForm) throws AxelorException {

    if (logisticalForm.getDeliverToCustomerPartner() == null) {
      return "self IS NULL";
    }

    List<String> domainList = new ArrayList<>();

    domainList.add("self.partner = :deliverToCustomerPartner");
    domainList.add(String.format("self.typeSelect = %d", StockMoveRepository.TYPE_OUTGOING));
    domainList.add(
        String.format(
            "self.statusSelect in (%d, %d)",
            StockMoveRepository.STATUS_PLANNED, StockMoveRepository.STATUS_REALIZED));
    domainList.add("COALESCE(self.fullySpreadOverLogisticalFormsFlag, FALSE) = FALSE");
    if (logisticalForm.getStockLocation() != null) {
      domainList.add("self.fromStockLocation = :stockLocation");
    }
    List<StockMove> fullySpreadStockMoveList = getFullySpreadStockMoveList(logisticalForm);

    if (!fullySpreadStockMoveList.isEmpty()) {
      String idListString = StringTool.getIdListString(fullySpreadStockMoveList);
      domainList.add(String.format("self.id NOT IN (%s)", idListString));
    }

    return domainList
        .stream()
        .map(domain -> String.format("(%s)", domain))
        .collect(Collectors.joining(" AND "));
  }

  @Override
  public void sortLines(LogisticalForm logisticalForm) {
    if (logisticalForm.getLogisticalFormLineList() != null) {
      logisticalForm
          .getLogisticalFormLineList()
          .sort(Comparator.comparing(LogisticalFormLine::getSequence));
    }
  }

  @Override
  public List<Long> getIdList(StockMove stockMove) throws AxelorException {
    if (stockMove.getId() == null) {
      throw new AxelorException(
          StockMove.class,
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.RECORD_UNSAVED));
    }

    TypedQuery<LogisticalForm> query =
        JPA.em()
            .createQuery(
                "SELECT DISTINCT self FROM LogisticalForm self "
                    + "JOIN self.logisticalFormLineList logisticalFormLine "
                    + "WHERE logisticalFormLine.stockMoveLine.stockMove.id = :stockMoveId",
                LogisticalForm.class);
    query.setParameter("stockMoveId", stockMove.getId());
    List<LogisticalForm> resultList = query.getResultList();

    return resultList.isEmpty()
        ? Lists.newArrayList(0L)
        : resultList.stream().map(LogisticalForm::getId).collect(Collectors.toList());
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void processCollected(LogisticalForm logisticalForm) throws AxelorException {
    if (logisticalForm.getLogisticalFormLineList() == null) {
      return;
    }

    Set<StockMove> stockMoveSet = new HashSet<>();

    logisticalForm
        .getLogisticalFormLineList()
        .stream()
        .filter(
            logisticalFormLine ->
                logisticalFormLine.getTypeSelect() == LogisticalFormLineRepository.TYPE_DETAIL
                    && logisticalFormLine.getStockMoveLine() != null
                    && logisticalFormLine.getStockMoveLine().getStockMove() != null)
        .forEach(
            logisticalFormLine ->
                stockMoveSet.add(logisticalFormLine.getStockMoveLine().getStockMove()));

    StockMoveService stockMoveService = Beans.get(StockMoveService.class);

    stockMoveSet.forEach(stockMoveService::updateFullySpreadOverLogisticalFormsFlag);

    StockConfigService stockConfigService = Beans.get(StockConfigService.class);
    StockConfig stockConfig = stockConfigService.getStockConfig(logisticalForm.getCompany());

    if (stockConfig.getRealizeStockMovesUponParcelPalletCollection()) {
      for (StockMove stockMove : stockMoveSet) {
        if (stockMove.getFullySpreadOverLogisticalFormsFlag()) {
          stockMoveService.realize(stockMove);
        }
      }
    }

    logisticalForm.setStatusSelect(LogisticalFormRepository.STATUS_COLLECTED);
  }

  @Override
  public Optional<String> getCustomerAccountNumberToCarrier(LogisticalForm logisticalForm)
      throws AxelorException {
    Preconditions.checkNotNull(logisticalForm);
    List<FreightCarrierCustomerAccountNumber> freightCarrierCustomerAccountNumberList = null;

    switch (logisticalForm.getAccountSelectionToCarrierSelect()) {
      case LogisticalFormRepository.ACCOUNT_COMPANY:
        if (logisticalForm.getCompany() != null
            && logisticalForm.getCompany().getStockConfig() != null) {
          freightCarrierCustomerAccountNumberList =
              logisticalForm
                  .getCompany()
                  .getStockConfig()
                  .getFreightCarrierCustomerAccountNumberList();
        }
        break;
      case LogisticalFormRepository.ACCOUNT_CUSTOMER:
        if (logisticalForm.getDeliverToCustomerPartner() != null) {
          freightCarrierCustomerAccountNumberList =
              logisticalForm
                  .getDeliverToCustomerPartner()
                  .getFreightCarrierCustomerAccountNumberList();
        }
        break;
      default:
        throw new AxelorException(
            logisticalForm,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.LOGISTICAL_FORM_UNKNOWN_ACCOUNT_SELECTION));
    }

    if (freightCarrierCustomerAccountNumberList != null) {
      Optional<FreightCarrierCustomerAccountNumber> freightCarrierCustomerAccountNumber =
          freightCarrierCustomerAccountNumberList
              .stream()
              .filter(it -> it.getCarrierPartner().equals(logisticalForm.getCarrierPartner()))
              .findFirst();

      if (freightCarrierCustomerAccountNumber.isPresent()) {
        return Optional.ofNullable(
            freightCarrierCustomerAccountNumber.get().getCustomerAccountNumber());
      }
    }

    return Optional.empty();
  }

  protected LogisticalFormLine createLogisticalFormLine(
      LogisticalForm logisticalForm, StockMoveLine stockMoveLine, BigDecimal qty) {

    LogisticalFormLine logisticalFormLine = new LogisticalFormLine();
    logisticalFormLine.setTypeSelect(LogisticalFormLineRepository.TYPE_DETAIL);
    logisticalFormLine.setStockMoveLine(stockMoveLine);
    logisticalFormLine.setQty(qty);
    logisticalFormLine.setSequence(getNextLineSequence(logisticalForm));
    logisticalFormLine.setUnitNetMass(stockMoveLine.getNetMass());

    return logisticalFormLine;
  }

  public void updateProductNetMass(LogisticalForm logisticalForm) {
    BigDecimal totalNetMass = BigDecimal.ZERO;
    if (logisticalForm.getLogisticalFormLineList() != null) {
      for (LogisticalFormLine logisticalFormLine : logisticalForm.getLogisticalFormLineList()) {
        if (logisticalFormLine.getStockMoveLine() != null
            && logisticalFormLine.getStockMoveLine().getProduct() != null
            && logisticalFormLine
                .getTypeSelect()
                .equals(LogisticalFormLineRepository.TYPE_DETAIL)) {
          Product product =
              productRepository.find(logisticalFormLine.getStockMoveLine().getProduct().getId());
          logisticalFormLine.setUnitNetMass(product.getNetMass());
          totalNetMass =
              totalNetMass.add(logisticalFormLine.getQty().multiply(product.getNetMass()));
        }
      }
      logisticalForm.setTotalNetMass(totalNetMass);
    }
  }
}
