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
package com.axelor.apps.base.service.excelreport.config;

import com.axelor.apps.base.db.PrintTemplate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.poi.ss.util.CellRangeAddress;

public class ReportParameterVariables {

  private PrintTemplate printTemplate;
  private Map<Integer, Map<String, Object>> outputMap;
  private Map<String, Object> entryValueMap;
  private Collection<Object> collection;
  private Map.Entry<Integer, Map<String, Object>> entry;
  private String fieldName;
  private int index;
  private int totalRecord;
  private boolean hide;
  private String operationString;
  private List<Integer> removeCellKeyList;
  private int collectionEntryRow;
  private int rowNumber;
  private int mergeOffset;
  private int record;
  private boolean nextRowCheckActive;
  private boolean isModel;
  private List<CellRangeAddress> mergedCellsRangeAddressList;
  private Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet;

  public ReportParameterVariables(
      PrintTemplate printTemplate,
      Map<Integer, Map<String, Object>> outputMap,
      Map<String, Object> entryValueMap,
      Collection<Object> collection,
      Entry<Integer, Map<String, Object>> entry,
      String fieldName,
      int index,
      int totalRecord,
      boolean hide,
      String operationString,
      List<Integer> removeCellKeyList,
      int collectionEntryRow,
      int rowNumber,
      int mergeOffset,
      int record,
      boolean nextRowCheckActive,
      boolean isModel,
      List<CellRangeAddress> mergedCellsRangeAddressList,
      Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet) {
    super();
    this.printTemplate = printTemplate;
    this.outputMap = outputMap;
    this.entryValueMap = entryValueMap;
    this.collection = collection;
    this.entry = entry;
    this.fieldName = fieldName;
    this.index = index;
    this.totalRecord = totalRecord;
    this.hide = hide;
    this.operationString = operationString;
    this.removeCellKeyList = removeCellKeyList;
    this.collectionEntryRow = collectionEntryRow;
    this.rowNumber = rowNumber;
    this.mergeOffset = mergeOffset;
    this.record = record;
    this.nextRowCheckActive = nextRowCheckActive;
    this.isModel = isModel;
    this.mergedCellsRangeAddressList = mergedCellsRangeAddressList;
    this.mergedCellsRangeAddressSetPerSheet = mergedCellsRangeAddressSetPerSheet;
  }

  public PrintTemplate getPrintTemplate() {
    return printTemplate;
  }

  public void setPrintTemplate(PrintTemplate printTemplate) {
    this.printTemplate = printTemplate;
  }

  public Map<Integer, Map<String, Object>> getOutputMap() {
    return outputMap;
  }

  public void setOutputMap(Map<Integer, Map<String, Object>> outputMap) {
    this.outputMap = outputMap;
  }

  public Map<String, Object> getEntryValueMap() {
    return entryValueMap;
  }

  public void setEntryValueMap(Map<String, Object> entryValueMap) {
    this.entryValueMap = entryValueMap;
  }

  public Collection<Object> getCollection() {
    return collection;
  }

  public void setCollection(Collection<Object> collection) {
    this.collection = collection;
  }

  public Map.Entry<Integer, Map<String, Object>> getEntry() {
    return entry;
  }

  public void setEntry(Map.Entry<Integer, Map<String, Object>> entry) {
    this.entry = entry;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public int getTotalRecord() {
    return totalRecord;
  }

  public void setTotalRecord(int totalRecord) {
    this.totalRecord = totalRecord;
  }

  public boolean isHide() {
    return hide;
  }

  public void setHide(boolean hide) {
    this.hide = hide;
  }

  public String getOperationString() {
    return operationString;
  }

  public void setOperationString(String operationString) {
    this.operationString = operationString;
  }

  public List<Integer> getRemoveCellKeyList() {
    return removeCellKeyList;
  }

  public void setRemoveCellKeyList(List<Integer> removeCellKeyList) {
    this.removeCellKeyList = removeCellKeyList;
  }

  public int getCollectionEntryRow() {
    return collectionEntryRow;
  }

  public void setCollectionEntryRow(int collectionEntryRow) {
    this.collectionEntryRow = collectionEntryRow;
  }

  public int getRowNumber() {
    return rowNumber;
  }

  public void setRowNumber(int rowNumber) {
    this.rowNumber = rowNumber;
  }

  public int getMergeOffset() {
    return mergeOffset;
  }

  public void setMergeOffset(int mergeOffset) {
    this.mergeOffset = mergeOffset;
  }

  public int getRecord() {
    return record;
  }

  public void setRecord(int record) {
    this.record = record;
  }

  public boolean isNextRowCheckActive() {
    return nextRowCheckActive;
  }

  public void setNextRowCheckActive(boolean nextRowCheckActive) {
    this.nextRowCheckActive = nextRowCheckActive;
  }

  public boolean isModel() {
    return isModel;
  }

  public void setModel(boolean isModel) {
    this.isModel = isModel;
  }

  public List<CellRangeAddress> getMergedCellsRangeAddressList() {
    return mergedCellsRangeAddressList;
  }

  public void setMergedCellsRangeAddressList(List<CellRangeAddress> mergedCellsRangeAddressList) {
    this.mergedCellsRangeAddressList = mergedCellsRangeAddressList;
  }

  public Set<CellRangeAddress> getMergedCellsRangeAddressSetPerSheet() {
    return mergedCellsRangeAddressSetPerSheet;
  }

  public void setMergedCellsRangeAddressSetPerSheet(
      Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet) {
    this.mergedCellsRangeAddressSetPerSheet = mergedCellsRangeAddressSetPerSheet;
  }
}
