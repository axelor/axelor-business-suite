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
package com.axelor.apps.base.service.excelreport.utility;

import com.axelor.apps.base.service.excelreport.config.ExcelReportConstants;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

public class ExcelReportCellMergingServiceImpl implements ExcelReportCellMergingService {

  @Override // sets global variable mergeOffset
  public int setMergeOffset(
      Map<Integer, Map<String, Object>> inputMap,
      Mapper mapper,
      List<CellRangeAddress> mergedCellsRangeAddressList) {
    int mergeRowNumber;
    int lastRow;
    int firstRow;
    Property property;
    String content = "";
    int mergeOffset = 0;
    for (Map.Entry<Integer, Map<String, Object>> entry : inputMap.entrySet()) {
      for (CellRangeAddress cellRange : mergedCellsRangeAddressList) {
        if (cellRange.isInRange(
            (int) entry.getValue().get(ExcelReportConstants.KEY_ROW),
            (int) entry.getValue().get(ExcelReportConstants.KEY_COLUMN))) {
          content = entry.getValue().get(ExcelReportConstants.KEY_VALUE).toString();
          if (StringUtils.notBlank(content) && content.charAt(0) == '$') {
            String propertyName = content.substring(1);
            if (propertyName.contains(".")) {
              property = mapper.getProperty(propertyName.substring(0, propertyName.indexOf(".")));
              if (property.isCollection()) {
                firstRow = cellRange.getFirstRow();
                lastRow = cellRange.getLastRow();
                mergeRowNumber = lastRow - firstRow + 1;
                if (mergeRowNumber > mergeOffset) mergeOffset = mergeRowNumber - 1;
              }
            }
          }
        }
      }
    }
    return mergeOffset;
  }

  @Override // sets blank merged cells from origin sheet to target sheet (header and footer sheets
  // not included)
  public Set<CellRangeAddress> getBlankMergedCells(
      Sheet originSheet, List<CellRangeAddress> mergedCellsRangeAddressList, String sheetType) {

    Set<CellRangeAddress> blankMergedCells = new HashSet<>();
    Cell cell = null;
    for (CellRangeAddress cellRange : mergedCellsRangeAddressList) {
      cell = originSheet.getRow(cellRange.getFirstRow()).getCell(cellRange.getFirstColumn());

      if (ObjectUtils.notEmpty(cell) && ObjectUtils.isEmpty(cell.getStringCellValue())) {
        blankMergedCells.add(cellRange);
      }
    }
    return blankMergedCells;
  }

  @Override // sets merged cells for the result row of the current table
  public CellRangeAddress setMergedCellsForTotalRow(
      List<CellRangeAddress> mergedCellsRangeAddressList,
      int rowIndex,
      int columnIndex,
      int totalRecord) {
    CellRangeAddress cellRange =
        this.findMergedRegion(mergedCellsRangeAddressList, rowIndex, columnIndex);

    if (ObjectUtils.isEmpty(cellRange)) return null;

    int firstCellRow = cellRange.getFirstRow() + totalRecord;
    int lastCellRow = cellRange.getLastRow() + totalRecord;

    return new CellRangeAddress(
        firstCellRow, lastCellRow, cellRange.getFirstColumn(), cellRange.getLastColumn());
  }

  @Override // shifts only single merged region according to the given offset and returns both
  // original and offsetted merged region
  public ImmutablePair<CellRangeAddress, CellRangeAddress> shiftMergedRegion(
      List<CellRangeAddress> mergedCellsRangeAddressList,
      int rowIndex,
      int columnIndex,
      int offset) {
    CellRangeAddress originalCellRange =
        this.findMergedRegion(mergedCellsRangeAddressList, rowIndex, columnIndex);

    if (ObjectUtils.isEmpty(originalCellRange)) return null;

    int firstCellRow = originalCellRange.getFirstRow() + offset;
    int lastCellRow = originalCellRange.getLastRow() + offset;

    CellRangeAddress offsettedCellRange =
        new CellRangeAddress(
            firstCellRow,
            lastCellRow,
            originalCellRange.getFirstColumn(),
            originalCellRange.getLastColumn());

    return new ImmutablePair<>(originalCellRange, offsettedCellRange);
  }

  protected CellRangeAddress findMergedRegion(
      List<CellRangeAddress> mergedCellsRangeAddressList, int rowIndex, int columnIndex) {
    CellRangeAddress mergedRegion = null;
    for (CellRangeAddress cellR : mergedCellsRangeAddressList) {
      if (cellR.isInRange(rowIndex, columnIndex)) {
        mergedRegion = cellR;
      }
    }
    return mergedRegion;
  }

  @Override // shifts all the merged regions according to the given offset
  public void shiftMergedRegions(Set<CellRangeAddress> mergedRegionsAddressSet, int offset) {
    if (ObjectUtils.isEmpty(mergedRegionsAddressSet)) return;

    Set<CellRangeAddress> newMergedRegionsSet = new HashSet<>();

    for (CellRangeAddress cellRange : mergedRegionsAddressSet) {
      int firstCellRow = cellRange.getFirstRow() + offset;
      int lastCellRow = cellRange.getLastRow() + offset;

      newMergedRegionsSet.add(
          new CellRangeAddress(
              firstCellRow, lastCellRow, cellRange.getFirstColumn(), cellRange.getLastColumn()));
    }

    mergedRegionsAddressSet.clear();
    mergedRegionsAddressSet.addAll(newMergedRegionsSet);
  }

  @Override
  public void fillMergedRegionCells(
      Sheet currentSheet, Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet) {
    CellStyle cellStyle;
    int firstRow;
    int lastRow;
    int firstColumn;
    int lastColumn;
    for (CellRangeAddress cellRange : mergedCellsRangeAddressSetPerSheet) {
      firstRow = cellRange.getFirstRow();
      lastRow = cellRange.getLastRow();
      firstColumn = cellRange.getFirstColumn();
      lastColumn = cellRange.getLastColumn();

      if (ObjectUtils.notEmpty(currentSheet.getRow(firstRow))
          && ObjectUtils.notEmpty(currentSheet.getRow(firstRow).getCell(firstColumn))) {
        cellStyle = currentSheet.getRow(firstRow).getCell(firstColumn).getCellStyle();
        for (int i = firstRow; i <= lastRow; i++) {
          for (int j = firstColumn; j <= lastColumn; j++) {
            if (ObjectUtils.isEmpty(currentSheet.getRow(i))) {
              currentSheet.createRow(i).createCell(j).setCellStyle(cellStyle);
            } else {
              if (ObjectUtils.isEmpty(currentSheet.getRow(i).getCell(j)))
                currentSheet.getRow(i).createCell(j).setCellStyle(cellStyle);
            }
          }
        }
      }
    }
  }

  @Override
  public void setMergedRegionsInSheet(Sheet sheet, Set<CellRangeAddress> mergedCellsAddressSet) {
    if (ObjectUtils.isEmpty(mergedCellsAddressSet)) return;

    for (CellRangeAddress cellRange : mergedCellsAddressSet) sheet.addMergedRegionUnsafe(cellRange);
  }

  @Override
  public void setMergedCellsRangeAddressSetPerSheet(
      Map<String, Object> entryValueMap,
      Collection<Object> collection,
      int totalRecord,
      List<CellRangeAddress> mergedCellsRangeAddressList,
      Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet,
      int mergeOffset) {
    int firstRow = 0;
    int lastRow = 0;
    int firstColumn = 0;
    int lastColumn = 0;
    CellRangeAddress cellR = null;
    boolean isMatch = false;
    CellRangeAddress cellRangeOriginal;
    for (CellRangeAddress cellRange : mergedCellsRangeAddressList) {
      if (cellRange.isInRange(
          (int) entryValueMap.get(ExcelReportConstants.KEY_ROW),
          (int) entryValueMap.get(ExcelReportConstants.KEY_COLUMN))) {
        firstRow = cellRange.getFirstRow() + totalRecord;
        lastRow = cellRange.getLastRow() + totalRecord;
        firstColumn = cellRange.getFirstColumn();
        lastColumn = cellRange.getLastColumn();
        cellR = cellRange;
        isMatch = true;
      }
    }

    if (isMatch && cellR != null) {
      int mergeRowNumber = lastRow - firstRow + 1;
      for (int i = 0; i < collection.size(); i++) {

        cellRangeOriginal = new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn);
        mergedCellsRangeAddressSetPerSheet.add(cellRangeOriginal);
        firstRow += mergeRowNumber + ((mergeOffset + 1) - mergeRowNumber);
        lastRow += mergeRowNumber + ((mergeOffset + 1) - mergeRowNumber);
      }
    }
  }

  @Override // sets dummy value if the cell is the first cell of a blank merged region
  public boolean isFirstCellOfTheMergedRegion(
      Workbook wb, Cell cell, List<CellRangeAddress> mergedCellsRangeAddressList) {
    boolean isFirstCell = false;
    for (CellRangeAddress cellRange : mergedCellsRangeAddressList) {
      if (cell.getRowIndex() == cellRange.getFirstRow()
          && cell.getColumnIndex() == cellRange.getFirstColumn()) {
        isFirstCell = true;
        cell.setCellValue("Merged Cell");
        wb.getFontAt(cell.getCellStyle().getFontIndexAsInt())
            .setColor(IndexedColors.WHITE.getIndex());
        break;
      }
    }
    return isFirstCell;
  }

  @Override
  public int getSheetLastRowNum(Sheet sheet, Set<CellRangeAddress> mergedCellsSet) {
    int lastRowNum = 0;
    int temp = 0;
    lastRowNum = sheet.getLastRowNum();

    if (ObjectUtils.notEmpty(mergedCellsSet)) {
      for (CellRangeAddress cellRange : mergedCellsSet) {
        temp = cellRange.getLastRow();
        if (lastRowNum < temp) lastRowNum = temp;
      }
    }
    return lastRowNum;
  }
}
