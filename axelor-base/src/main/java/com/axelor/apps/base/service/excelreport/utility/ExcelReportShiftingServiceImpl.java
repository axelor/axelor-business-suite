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
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.poi.ss.util.CellRangeAddress;

public class ExcelReportShiftingServiceImpl implements ExcelReportShiftingService {

  @Inject ExcelReportCellMergingService excelReportCellMergingService;

  @Override
  public void shiftAll(Map<Integer, Map<String, Object>> map, int offset) {
    for (Map.Entry<Integer, Map<String, Object>> entry : map.entrySet()) {
      int rowNo = (int) entry.getValue().get(ExcelReportConstants.KEY_ROW);
      entry.getValue().replace(ExcelReportConstants.KEY_ROW, rowNo + offset);
    }
  }

  @Override
  public void shiftRows(
      Map<String, Object> map,
      int offset,
      int collectionEntryRow,
      List<CellRangeAddress> mergedCellsRangeAddressList,
      Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet) {
    int rowIndex = (int) map.get(ExcelReportConstants.KEY_ROW);
    int columnIndex = (int) map.get(ExcelReportConstants.KEY_COLUMN);
    CellRangeAddress newAddress = null;
    CellRangeAddress oldAddress = null;
    if (rowIndex >= collectionEntryRow) {

      map.replace(ExcelReportConstants.KEY_ROW, rowIndex + offset);
      ImmutablePair<CellRangeAddress, CellRangeAddress> cellRangePair =
          excelReportCellMergingService.shiftMergedRegion(
              mergedCellsRangeAddressList, rowIndex, columnIndex, offset);
      if (ObjectUtils.isEmpty(cellRangePair)) return;
      oldAddress = cellRangePair.getLeft();
      newAddress = cellRangePair.getRight();

      if (ObjectUtils.notEmpty(newAddress) && !newAddress.equals(oldAddress)) {
        // removes old merged regions
        if (mergedCellsRangeAddressSetPerSheet.contains(oldAddress)) {
          mergedCellsRangeAddressSetPerSheet.remove(oldAddress);
        }
        mergedCellsRangeAddressSetPerSheet.add(newAddress);
      }
    }
  }
}
