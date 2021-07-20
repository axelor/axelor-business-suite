/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service.excelreport;

import com.axelor.apps.base.db.Print;
import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.db.ReportQueryBuilder;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.excelreport.components.ExcelReportCellService;
import com.axelor.apps.base.service.excelreport.components.ExcelReportFooterService;
import com.axelor.apps.base.service.excelreport.components.ExcelReportHeaderService;
import com.axelor.apps.base.service.excelreport.components.ExcelReportPictureService;
import com.axelor.apps.base.service.excelreport.config.ExcelReportConstants;
import com.axelor.apps.base.service.excelreport.config.ExcelReportHelperService;
import com.axelor.apps.base.service.excelreport.config.ReportParameterVariables;
import com.axelor.apps.base.service.excelreport.utility.CollectionColumnHidingService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportCellMergingService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportGroovyService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportShiftingService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportTranslationService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportWriteService;
import com.axelor.apps.base.service.excelreport.utility.ReportQueryBuilderService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.inject.Inject;
import com.itextpdf.awt.geom.Dimension;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.script.ScriptException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;

public class ExcelReportDataMapService {

  @Inject private ExcelReportPictureService excelReportPictureService;
  @Inject private AppBaseService appBaseService;
  @Inject private ExcelReportCellService excelReportCellService;
  @Inject private ExcelReportCellMergingService excelReportCellMergingService;
  @Inject private ExcelReportHeaderService excelReportHeaderService;
  @Inject private ExcelReportFooterService excelReportFooterService;
  @Inject private ExcelReportWriteService excelReportWriteService;
  @Inject private ReportQueryBuilderService reportQueryBuilderService;
  @Inject private ExcelReportTranslationService excelReportTranslationService;
  @Inject private ExcelReportShiftingService excelReportShiftingService;
  @Inject private ExcelReportGroovyService excelReportGroovyService;
  @Inject private ExcelReportHelperService excelReportHelperService;
  @Inject private MetaModelRepository metaModelRepository;
  @Inject private CollectionColumnHidingService collectionColumnHidingService;

  // variable parameters
  private int rowNumber = 0;
  private int mergeOffset = 0;
  private List<CellRangeAddress> mergedCellsRangeAddressList;
  private Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet;
  private int collectionEntryRow = -1;
  private int record;
  private boolean nextRowCheckActive = false;
  private Map<String, List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>>>
      pictureInputMap = new HashMap<>();
  private Map<String, List<MutablePair<Integer, Integer>>> pictureRowShiftMap = new HashMap<>();
  private Sheet originSheet;
  private List<Integer> removeCellKeyList = new ArrayList<>();
  private List<ReportQueryBuilder> reportQueryBuilderList;
  private Print print = null;
  private PrintTemplate printTemplate;

  public void setPrintTemplate(PrintTemplate printTemplate) {
    this.printTemplate = printTemplate;
  }

  public PrintTemplate getPrintTemplate() {
    return printTemplate;
  }

  public void setPrint(Print print) {
    this.print = print;
  }

  public Print getPrint() {
    return print;
  }

  public void setReportQueryBuilderList(List<ReportQueryBuilder> reportQueryBuilderList) {
    this.reportQueryBuilderList = reportQueryBuilderList;
  }

  public Map<Integer, Map<String, Object>> getInputMap(Workbook wb, String sheetName) {
    Map<Integer, Map<String, Object>> map = new HashMap<>();

    Sheet sheet;
    if (wb.getSheet(sheetName) == null) {
      return map;
    }
    sheet = wb.getSheet(sheetName);
    if (sheetName.equalsIgnoreCase(ExcelReportConstants.TEMPLATE_SHEET_TITLE)) {
      mergedCellsRangeAddressList = sheet.getMergedRegions();
      originSheet = sheet;
    }
    excelReportPictureService.getPictures(
        sheet, pictureInputMap, mergedCellsRangeAddressList, sheetName);
    this.fillInputMap(wb, map, sheet);

    return map;
  }

  private void fillInputMap(Workbook wb, Map<Integer, Map<String, Object>> map, Sheet sheet) {
    int n = 0;
    int lastColumn = 0;
    int maxRows = appBaseService.getAppBase().getMaxRows();
    int maxColumns = appBaseService.getAppBase().getMaxColumns();
    for (int i = 0; i < maxRows; i++) {
      Row row = sheet.getRow(i);
      if (ObjectUtils.notEmpty(row)) {
        for (int j = 0; j < maxColumns; j++) {
          Cell cell = row.getCell(j);
          if (ObjectUtils.isEmpty(cell)
              || (excelReportCellService.isCellEmpty(cell)
                  && !excelReportCellMergingService.isFirstCellOfTheMergedRegion(
                      wb, cell, mergedCellsRangeAddressList))) {
            continue;
          }

          map.put(n, excelReportHelperService.getDataMap(cell));
          if (lastColumn < cell.getColumnIndex()) lastColumn = cell.getColumnIndex();
          n++;
        }
      }
    }
  }

  public Workbook createWorkbook(
      Map<Integer, Map<String, Object>> inputMap,
      List<Model> data,
      Mapper mapper,
      String formatType,
      Workbook wb)
      throws AxelorException, ScriptException, IOException, ClassNotFoundException {
    Workbook newWb = WorkbookFactory.create(true);
    Map<Integer, Map<String, Object>> headerOutputMap;
    Map<Integer, Map<String, Object>> footerOutputMap;
    Map<Integer, Map<String, Object>> outputMap;

    mergeOffset =
        excelReportCellMergingService.setMergeOffset(inputMap, mapper, mergedCellsRangeAddressList);

    int i = 1;
    Map<Integer, Map<String, Object>> headerInputMap =
        excelReportHeaderService.getHeaderInputMap(wb, print);
    Map<Integer, Map<String, Object>> footerInputMap =
        excelReportFooterService.getFooterInputMap(wb, print);
    String modelName = printTemplate.getMetaModel().getName();
    for (Model dataItem : data) {

      String sheetName = String.format("%s %s", modelName, i++);
      headerOutputMap = headerInputMap;
      footerOutputMap = footerInputMap;

      outputMap =
          this.getOutputMap(
              inputMap, mapper, dataItem, ExcelReportConstants.TEMPLATE_SHEET_TITLE, sheetName, wb);

      outputMap = recalculateMap(inputMap, mapper, wb, outputMap, dataItem, sheetName);
      fillSheet(formatType, newWb, headerOutputMap, footerOutputMap, outputMap, sheetName);
    }

    return newWb;
  }

  private void fillSheet(
      String formatType,
      Workbook newWb,
      Map<Integer, Map<String, Object>> headerOutputMap,
      Map<Integer, Map<String, Object>> footerOutputMap,
      Map<Integer, Map<String, Object>> outputMap,
      String sheetName) {
    Sheet newSheet = newWb.createSheet(sheetName);

    if (formatType.equals("XLSX")) {
      excelReportHeaderService.setHeader(
          originSheet,
          newSheet,
          print,
          outputMap,
          headerOutputMap,
          mergedCellsRangeAddressSetPerSheet,
          pictureInputMap,
          pictureRowShiftMap);
      excelReportWriteService.writeTemplateSheet(
          outputMap, mergedCellsRangeAddressSetPerSheet, originSheet, newSheet, 0);
      excelReportFooterService.setFooter(
          originSheet, newSheet, mergedCellsRangeAddressSetPerSheet, footerOutputMap);
    } else if (formatType.equals("PDF")) {
      excelReportWriteService.writeTemplateSheet(
          outputMap, mergedCellsRangeAddressSetPerSheet, originSheet, newSheet, 0);
      excelReportPictureService.writePictures(
          newSheet,
          pictureRowShiftMap,
          pictureInputMap.get(ExcelReportConstants.TEMPLATE_SHEET_TITLE),
          ExcelReportConstants.TEMPLATE_SHEET_TITLE);
    }

    excelReportPictureService.resetPictureMap(pictureInputMap);
  }

  protected Sheet createSheet(
      Workbook workbook, Map<Integer, Map<String, Object>> map, String sheetName) {

    Sheet sheet = workbook.createSheet(sheetName);
    excelReportWriteService.write(map, originSheet, sheet, 0, false);
    excelReportCellMergingService.fillMergedRegionCells(sheet, mergedCellsRangeAddressSetPerSheet);

    return sheet;
  }

  @SuppressWarnings("unchecked")
  protected Map<Integer, Map<String, Object>> getOutputMap(
      Map<Integer, Map<String, Object>> inputMap,
      Mapper mapper,
      Object object,
      String sheetType,
      String sheetName,
      Workbook wb)
      throws AxelorException, IOException, ScriptException, ClassNotFoundException {

    mergedCellsRangeAddressSetPerSheet = new HashSet<>();
    collectionEntryRow = -1;
    record = 0;
    Map<Integer, Map<String, Object>> outputMap = new HashMap<>(inputMap);

    if (sheetType.equalsIgnoreCase(ExcelReportConstants.TEMPLATE_SHEET_TITLE)) {
      Set<CellRangeAddress> blankMergedCellsRangeAddressSet =
          excelReportCellMergingService.getBlankMergedCells(
              originSheet, mergedCellsRangeAddressList, sheetType);
      mergedCellsRangeAddressSetPerSheet.addAll(blankMergedCellsRangeAddressSet);
      mergedCellsRangeAddressSetPerSheet.addAll(mergedCellsRangeAddressList);
    }

    this.setMapEntries(inputMap, outputMap, wb, object, mapper, sheetType, sheetName);

    return outputMap;
  }

  private void setMapEntries(
      Map<Integer, Map<String, Object>> inputMap,
      Map<Integer, Map<String, Object>> outputMap,
      Workbook wb,
      Object object,
      Mapper mapper,
      String sheetType,
      String sheetName)
      throws ClassNotFoundException, IOException, AxelorException, ScriptException {

    Object mainObject = object;
    Map<String, List<Object>> reportQueryBuilderResultMap =
        reportQueryBuilderService.getAllReportQueryBuilderResult(reportQueryBuilderList, object);
    Property property = null;
    int index = inputMap.size();
    int totalRecord = 0;

    for (Map.Entry<Integer, Map<String, Object>> entry : inputMap.entrySet()) {
      Map<String, Object> m = new HashMap<>(entry.getValue());
      boolean hide = false; // groovy condition boolean
      boolean translate = false; // language translation boolean
      String operationString = null;
      totalRecord = resetNextRowCheckActive(sheetType, sheetName, totalRecord, m);
      Object cellValue = m.get(ExcelReportConstants.KEY_VALUE);
      String value = cellValue == null ? null : cellValue.toString();
      String originValue = value;

      // check for translation function
      String translatedValue = excelReportTranslationService.checkTranslationFunction(value);
      if (!translatedValue.equals(value)) {
        value = translatedValue;
        translate = true;
      }
      outputMap.put(entry.getKey(), m);

      if (StringUtils.notBlank(value)) {
        if (value.contains("$")) {

          String propertyName = value;

          // Check for groovy conditional text
          ImmutableTriple<String, String, Boolean> tripleResult =
              excelReportGroovyService.checkGroovyConditionalText(
                  propertyName, object, m, reportQueryBuilderResultMap, reportQueryBuilderList);
          if (tripleResult.getRight() == null) {
            shiftRows(totalRecord, m);
            continue;
          } else {
            propertyName = tripleResult.getLeft();
            operationString = tripleResult.getMiddle();
            hide = tripleResult.getRight();
          }

          Pair<Boolean, String> translatePropertyNamePair =
              excelReportHelperService.checkForTranslationFuction(propertyName, translate);
          translate = translatePropertyNamePair.getLeft();
          propertyName = translatePropertyNamePair.getRight().substring(1);
          property = excelReportHelperService.getProperty(mapper, propertyName);

          if (ObjectUtils.isEmpty(property)) {
            if (!propertyName.contains(".") || reportQueryBuilderResultMap.isEmpty()) {
              excelReportHelperService.setEmptyCell(wb, m);
              continue;
            }
            if (!reportQueryBuilderResultMap.isEmpty()
                && reportQueryBuilderResultMap.containsKey(
                    propertyName.substring(0, propertyName.indexOf(".")))) {
              totalRecord =
                  setReportQueryVariableData(
                      wb,
                      outputMap,
                      entry,
                      m,
                      Triple.of(
                          reportQueryBuilderResultMap.get(
                              propertyName.substring(0, propertyName.indexOf("."))),
                          operationString,
                          propertyName),
                      Triple.of(totalRecord, index, hide));
              continue;
            } else {
              throw new AxelorException(
                  TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                  I18n.get(IExceptionMessage.NO_SUCH_FIELD) + propertyName);
            }
          }
          totalRecord =
              this.setCollectionAndNonCollectionEntry(
                  outputMap,
                  entry,
                  m,
                  Triple.of(mainObject, property, mapper),
                  Triple.of(propertyName, totalRecord, index),
                  Triple.of(operationString, translate, hide));
          object = mainObject;
        } else {
          String temp = value;
          value = excelReportHelperService.getLabel(printTemplate, value, object, translate);
          // replace cell value if different
          if (!temp.equals(value) || !originValue.equals(value)) {
            ((XSSFRichTextString) cellValue).setString(value);
          }
          shiftRows(totalRecord, m);
        }
      } else {
        shiftRows(totalRecord, m);
      }
    }
  }

  private int setCollectionAndNonCollectionEntry(
      Map<Integer, Map<String, Object>> outputMap,
      Map.Entry<Integer, Map<String, Object>> entry,
      Map<String, Object> m,
      Triple<Object, Property, Mapper> mainObjectPropertyMapper,
      Triple<String, Integer, Integer> propertyNameTotalRecordIndex,
      Triple<String, Boolean, Boolean> operationStringTranslateHide)
      throws AxelorException, ScriptException {
    int totalRecord = propertyNameTotalRecordIndex.getMiddle();
    String propertyName = propertyNameTotalRecordIndex.getLeft();
    Property property = mainObjectPropertyMapper.getMiddle();
    if (!property.isCollection()) {
      if (Boolean.TRUE.equals(operationStringTranslateHide.getRight())) {
        m.replace(ExcelReportConstants.KEY_VALUE, "");
        return totalRecord;
      }
      this.getNonCollectionEntry(
          m,
          mainObjectPropertyMapper,
          Triple.of(
              propertyName,
              operationStringTranslateHide.getLeft(),
              operationStringTranslateHide.getMiddle()),
          totalRecord);
    } else {

      if (!propertyName.contains(".")) {
        m.replace(ExcelReportConstants.KEY_VALUE, property.getTitle());
        shiftRows(totalRecord, m);
        return totalRecord;
      }

      ImmutablePair<Integer, Map<Integer, Map<String, Object>>> collectionEntryPair =
          this.getCollectionEntryPair(
              outputMap,
              entry,
              property,
              mainObjectPropertyMapper.getLeft(),
              propertyNameTotalRecordIndex,
              operationStringTranslateHide);
      totalRecord = collectionEntryPair.getLeft();
    }

    return totalRecord;
  }

  private ImmutablePair<Integer, Map<Integer, Map<String, Object>>> getCollectionEntryPair(
      Map<Integer, Map<String, Object>> outputMap,
      Map.Entry<Integer, Map<String, Object>> entry,
      Property property,
      Object object,
      Triple<String, Integer, Integer> propertyNameTotalRecordIndex,
      Triple<String, Boolean, Boolean> operationStringTranslateHide)
      throws AxelorException, ScriptException {
    Map<String, Object> entryValueMap = new HashMap<>(entry.getValue());
    if (collectionEntryRow != (int) entryValueMap.get(ExcelReportConstants.KEY_ROW)) {
      collectionEntryRow = (int) entryValueMap.get(ExcelReportConstants.KEY_ROW);
    }

    rowNumber = (Integer) entryValueMap.get(ExcelReportConstants.KEY_ROW);

    Collection<Object> collection = (Collection<Object>) property.get(object);

    excelReportCellMergingService.setMergedCellsRangeAddressSetPerSheet(
        entryValueMap,
        collection,
        propertyNameTotalRecordIndex.getMiddle(),
        mergedCellsRangeAddressList,
        mergedCellsRangeAddressSetPerSheet,
        mergeOffset);

    return this.getCollectionEntry(
        outputMap,
        entryValueMap,
        collection,
        entry,
        property,
        propertyNameTotalRecordIndex,
        operationStringTranslateHide);
  }

  private int resetNextRowCheckActive(
      String sheetType, String sheetName, int totalRecord, Map<String, Object> m) {
    if (nextRowCheckActive) {
      if ((int) m.get(ExcelReportConstants.KEY_ROW) > collectionEntryRow) {
        excelReportPictureService.setPictureRowShiftMap(
            pictureInputMap,
            pictureRowShiftMap,
            sheetName,
            sheetType,
            collectionEntryRow,
            record,
            totalRecord);
        totalRecord = totalRecord + record;
        record = 0;
      }
      nextRowCheckActive = false;
    }
    return totalRecord;
  }

  protected ImmutablePair<Integer, Map<Integer, Map<String, Object>>> getCollectionEntry(
      Map<Integer, Map<String, Object>> outputMap,
      Map<String, Object> entryValueMap,
      Collection<Object> collection,
      Map.Entry<Integer, Map<String, Object>> entry,
      Property property,
      Triple<String, Integer, Integer> propertyNameTotalRecordIndex,
      Triple<String, Boolean, Boolean> operationStringTranslateHide)
      throws AxelorException, ScriptException {
    boolean isFirstIteration = true;
    int bigDecimalScale = excelReportHelperService.getBigDecimalScale();
    Mapper o2mMapper = Mapper.of(property.getTarget());
    String propertyName =
        propertyNameTotalRecordIndex
            .getLeft()
            .substring(propertyNameTotalRecordIndex.getLeft().indexOf(".") + 1);
    int totalRecord = propertyNameTotalRecordIndex.getMiddle();
    int index = propertyNameTotalRecordIndex.getRight();
    if (Boolean.TRUE.equals(operationStringTranslateHide.getRight())) {
      removeCellKeyList.add(entry.getKey());
    }

    Map<String, Object> newEntryValueMap = new HashMap<>(entryValueMap);
    shiftRows(totalRecord, newEntryValueMap);
    rowNumber = (int) newEntryValueMap.get(ExcelReportConstants.KEY_ROW);

    this.setCollection(
        outputMap,
        collection,
        entry,
        operationStringTranslateHide,
        Triple.of(newEntryValueMap, o2mMapper, isFirstIteration),
        Triple.of(propertyName, bigDecimalScale, index));
    if (!nextRowCheckActive) {
      nextRowCheckActive = true;
    }

    return ImmutablePair.of(totalRecord, outputMap);
  }

  private void setCollection(
      Map<Integer, Map<String, Object>> outputMap,
      Collection<Object> collection,
      Map.Entry<Integer, Map<String, Object>> entry,
      Triple<String, Boolean, Boolean> operationStringTranslateHide,
      Triple<Map<String, Object>, Mapper, Boolean> newEntryMapMapperIsFirstIterationTriple,
      Triple<String, Integer, Integer> propertyNameBigDecimalScaleIndexTriple)
      throws AxelorException, ScriptException {
    Property property;
    ImmutablePair<Property, Object> pair;
    int localMergeOffset = 0;
    int rowOffset = 0;
    int index = propertyNameBigDecimalScaleIndexTriple.getRight();
    Map<String, Object> newEntryValueMap = newEntryMapMapperIsFirstIterationTriple.getLeft();
    boolean isFirstIteration = newEntryMapMapperIsFirstIterationTriple.getRight();

    if (!collection.isEmpty()) {

      for (Object ob : collection) {
        Map<String, Object> newMap = new HashMap<>();

        pair =
            excelReportHelperService.findField(
                newEntryMapMapperIsFirstIterationTriple.getMiddle(),
                ob,
                propertyNameBigDecimalScaleIndexTriple.getLeft());

        if (ObjectUtils.isEmpty(pair))
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.NO_SUCH_FIELD)
                  + propertyNameBigDecimalScaleIndexTriple.getLeft());

        property = pair.getLeft();
        newMap.putAll(newEntryValueMap);
        newMap.replace(ExcelReportConstants.KEY_ROW, rowNumber + rowOffset + localMergeOffset);
        ob = pair.getRight();

        Object keyValue =
            getCollectionKeyValue(
                operationStringTranslateHide, propertyNameBigDecimalScaleIndexTriple, property, ob);
        newMap.replace(ExcelReportConstants.KEY_VALUE, keyValue);

        while (outputMap.containsKey(index)) index++;
        if (isFirstIteration) {
          index = entry.getKey();
          isFirstIteration = false;
        }

        outputMap.put(index, newMap);
        index++;
        rowOffset = rowOffset + localMergeOffset + 1;
        if (localMergeOffset == 0 && mergeOffset != 0) localMergeOffset = mergeOffset;
      }
      if (record == 0) record = rowOffset - 1;
    } else {
      newEntryValueMap.replace(ExcelReportConstants.KEY_VALUE, "");
      outputMap.put(entry.getKey(), newEntryValueMap);
    }
  }

  private Object getCollectionKeyValue(
      Triple<String, Boolean, Boolean> operationStringTranslateHide,
      Triple<String, Integer, Integer> propertyNameBigDecimalScaleIndexTriple,
      Property property,
      Object ob)
      throws ScriptException {
    Object keyValue = "";

    if (ObjectUtils.isEmpty(property.get(ob))
        || Boolean.TRUE.equals(operationStringTranslateHide.getRight())) {
      keyValue = "";
    } else if (property.isReference()) {
      keyValue = excelReportHelperService.findNameColumn(property, property.get(ob));
    } else if (!ObjectUtils.isEmpty(property.getSelection())) {
      String title =
          MetaStore.getSelectionItem(property.getSelection(), property.get(ob).toString())
              .getTitle();
      keyValue = I18n.get(title);
    } else if (property.get(ob).getClass().equals(BigDecimal.class)) {
      keyValue =
          ((BigDecimal) property.get(ob))
              .setScale(propertyNameBigDecimalScaleIndexTriple.getMiddle())
              .toString();
    } else {
      keyValue = property.get(ob).toString();
    }

    if (StringUtils.notEmpty(operationStringTranslateHide.getLeft())) {
      keyValue =
          excelReportGroovyService.calculateFromString(
              keyValue.toString().concat(operationStringTranslateHide.getLeft()),
              propertyNameBigDecimalScaleIndexTriple.getMiddle());
    }

    if (Boolean.TRUE.equals(operationStringTranslateHide.getMiddle())) {
      keyValue = excelReportTranslationService.getTranslatedValue(keyValue, printTemplate);
    }
    return keyValue;
  }

  protected Map<String, Object> getNonCollectionEntry(
      Map<String, Object> m,
      Triple<Object, Property, Mapper> mainObjectPropertyMapper,
      Triple<String, String, Boolean> propertyNameOperationStringTranslate,
      int totalRecord)
      throws AxelorException, ScriptException {
    Object object = mainObjectPropertyMapper.getLeft();
    Property property = mainObjectPropertyMapper.getMiddle();
    ImmutablePair<Property, Object> pair =
        excelReportHelperService.findField(
            mainObjectPropertyMapper.getRight(),
            object,
            propertyNameOperationStringTranslate.getLeft());

    if (ObjectUtils.isEmpty(pair)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.NO_SUCH_FIELD)
              + propertyNameOperationStringTranslate.getLeft());
    }

    property = pair.getLeft();
    object = pair.getRight();

    Object outputValue =
        excelReportHelperService.getNonCollectionOutputValue(
            propertyNameOperationStringTranslate, printTemplate, object, property);
    m.replace(ExcelReportConstants.KEY_VALUE, outputValue);

    if (totalRecord > 0) {
      shiftRows(totalRecord, m);
    }

    return m;
  }

  private Map<Integer, Map<String, Object>> recalculateMap(
      Map<Integer, Map<String, Object>> inputMap,
      Mapper mapper,
      Workbook wb,
      Map<Integer, Map<String, Object>> outputMap,
      Model dataItem,
      String sheetName)
      throws AxelorException, IOException, ScriptException, ClassNotFoundException {
    // hide collections if any and recalculate
    if (ObjectUtils.notEmpty(removeCellKeyList)) {
      outputMap =
          this.getOutputMap(
              collectionColumnHidingService.getHideCollectionInputMap(inputMap, removeCellKeyList),
              mapper,
              dataItem,
              ExcelReportConstants.TEMPLATE_SHEET_TITLE,
              sheetName,
              wb);
    }
    return outputMap;
  }

  private int setReportQueryVariableData(
      Workbook wb,
      Map<Integer, Map<String, Object>> outputMap,
      Map.Entry<Integer, Map<String, Object>> entry,
      Map<String, Object> m,
      Triple<List<Object>, String, String> collectionOperationStringPropertyNameTriple,
      Triple<Integer, Integer, Boolean> totalRecordIndexHideTriple)
      throws ClassNotFoundException, ScriptException {
    List<Object> collection = collectionOperationStringPropertyNameTriple.getLeft();
    int totalRecord = totalRecordIndexHideTriple.getLeft();
    if (ObjectUtils.isEmpty(collection)) {
      m.replace(ExcelReportConstants.KEY_VALUE, "");
      m.replace(ExcelReportConstants.KEY_CELL_STYLE, wb.createCellStyle());
      return totalRecord;
    }
    String modelFullName =
        ((LinkedHashMap<String, Object>) collection.get(0))
            .values()
            .iterator()
            .next()
            .getClass()
            .getName();
    boolean isModel =
        metaModelRepository.all().filter("self.fullName = ?1", modelFullName).count() > 0;
    String fieldName =
        collectionOperationStringPropertyNameTriple
            .getRight()
            .substring(collectionOperationStringPropertyNameTriple.getRight().indexOf(".") + 1);

    // continue if no such field found in report query
    if (isModel && ObjectUtils.isEmpty(collection)
        || (!isModel
            && ((ObjectUtils.notEmpty(collection)
                && !((LinkedHashMap<String, String>) collection.get(0)).containsKey(fieldName))))) {
      m.replace(ExcelReportConstants.KEY_VALUE, "");
      m.replace(ExcelReportConstants.KEY_CELL_STYLE, wb.createCellStyle());
      return totalRecord;
    }
    totalRecord =
        this.setReportQueryBuilder(
            outputMap,
            entry,
            collection,
            Pair.of(collectionOperationStringPropertyNameTriple.getMiddle(), fieldName),
            Pair.of(totalRecord, totalRecordIndexHideTriple.getMiddle()),
            Pair.of(isModel, totalRecordIndexHideTriple.getRight()));
    return totalRecord;
  }

  private int setReportQueryBuilder(
      Map<Integer, Map<String, Object>> outputMap,
      Map.Entry<Integer, Map<String, Object>> entry,
      List<Object> collection,
      Pair<String, String> operationStringFieldNamePair,
      Pair<Integer, Integer> totalRecordIndexPair,
      Pair<Boolean, Boolean> isModelHidePair)
      throws ClassNotFoundException, ScriptException {
    Map<String, Object> entryValueMap = new HashMap<>(entry.getValue());

    if (collectionEntryRow != (int) entryValueMap.get(ExcelReportConstants.KEY_ROW)) {
      collectionEntryRow = (int) entryValueMap.get(ExcelReportConstants.KEY_ROW);
    }

    rowNumber = (Integer) entryValueMap.get(ExcelReportConstants.KEY_ROW);

    setMergedCellsRangeAddressSetPerSheet(
        totalRecordIndexPair.getLeft(), collection, entryValueMap);

    ReportParameterVariables reportVariables =
        new ReportParameterVariables(
            printTemplate,
            outputMap,
            entryValueMap,
            collection,
            entry,
            operationStringFieldNamePair.getRight(),
            totalRecordIndexPair.getRight(),
            totalRecordIndexPair.getLeft(),
            isModelHidePair.getRight(),
            operationStringFieldNamePair.getLeft(),
            removeCellKeyList,
            collectionEntryRow,
            rowNumber,
            mergeOffset,
            record,
            nextRowCheckActive,
            isModelHidePair.getLeft(),
            mergedCellsRangeAddressList,
            mergedCellsRangeAddressSetPerSheet);
    // get report query builder result
    reportQueryBuilderService.getReportQueryBuilderCollectionEntry(reportVariables);
    removeCellKeyList = reportVariables.getRemoveCellKeyList();
    rowNumber = reportVariables.getRowNumber();
    record = reportVariables.getRecord();
    nextRowCheckActive = reportVariables.isNextRowCheckActive();

    return reportVariables.getTotalRecord();
  }

  private void setMergedCellsRangeAddressSetPerSheet(
      int totalRecord, List<Object> collection, Map<String, Object> entryValueMap) {
    excelReportCellMergingService.setMergedCellsRangeAddressSetPerSheet(
        entryValueMap,
        collection,
        totalRecord,
        mergedCellsRangeAddressList,
        mergedCellsRangeAddressSetPerSheet,
        mergeOffset);
  }

  private void shiftRows(int offset, Map<String, Object> map) {
    excelReportShiftingService.shiftRows(
        map,
        offset,
        collectionEntryRow,
        mergedCellsRangeAddressList,
        mergedCellsRangeAddressSetPerSheet);
  }
}
