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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class CollectionColumnHidingServiceImpl implements CollectionColumnHidingService {

  @Override
  public Map<Integer, Map<String, Object>> getHideCollectionInputMap(
      Map<Integer, Map<String, Object>> inputMap, List<Integer> removeCellKeyList) {
    List<ImmutablePair<Integer, Integer>> removeCellRowColumnPair = new ArrayList<>();
    List<Integer> finalRemoveKeyPairList = new ArrayList<>();
    // new input map
    Map<Integer, Map<String, Object>> newInputMap = new HashMap<>(inputMap);

    // get location of all cells to hide
    getCellsToHideLocation(inputMap, removeCellRowColumnPair, removeCellKeyList);

    // get all hiding cell keys
    getHiddenCellKeys(inputMap, removeCellRowColumnPair, finalRemoveKeyPairList);

    // shift cells to left which occur after the cells to remove
    resetCellPositions(inputMap, removeCellRowColumnPair, newInputMap);

    // remove cells to hide
    for (Integer key : finalRemoveKeyPairList) {
      newInputMap.remove(key);
    }

    return newInputMap;
  }

  private void resetCellPositions(
      Map<Integer, Map<String, Object>> inputMap,
      List<ImmutablePair<Integer, Integer>> removeCellRowColumnPair,
      Map<Integer, Map<String, Object>> newInputMap) {
    for (Map.Entry<Integer, Map<String, Object>> entry : inputMap.entrySet()) {
      for (ImmutablePair<Integer, Integer> pair : removeCellRowColumnPair) {
        if (entry.getValue().get(ExcelReportConstants.KEY_ROW).equals(pair.getLeft())
            && (Integer) entry.getValue().get(ExcelReportConstants.KEY_COLUMN)
                > (pair.getRight())) {
          newInputMap
              .get(entry.getKey())
              .replace(
                  ExcelReportConstants.KEY_COLUMN,
                  (Integer) entry.getValue().get(ExcelReportConstants.KEY_COLUMN) - 1);
        }
      }
    }
  }

  private void getHiddenCellKeys(
      Map<Integer, Map<String, Object>> inputMap,
      List<ImmutablePair<Integer, Integer>> removeCellRowColumnPair,
      List<Integer> finalRemoveKeyPairList) {
    for (Map.Entry<Integer, Map<String, Object>> entry : inputMap.entrySet()) {
      for (ImmutablePair<Integer, Integer> pair : removeCellRowColumnPair) {
        if (entry.getValue().get(ExcelReportConstants.KEY_ROW).equals(pair.getLeft())
            && entry.getValue().get(ExcelReportConstants.KEY_COLUMN).equals(pair.getRight())) {
          finalRemoveKeyPairList.add(entry.getKey());
        }
      }
    }
  }

  private void getCellsToHideLocation(
      Map<Integer, Map<String, Object>> inputMap,
      List<ImmutablePair<Integer, Integer>> removeCellRowColumnPair,
      List<Integer> removeCellKeyList) {
    for (Integer key : removeCellKeyList) {
      Integer row = (Integer) inputMap.get(key).get(ExcelReportConstants.KEY_ROW);
      Integer column = (Integer) inputMap.get(key).get(ExcelReportConstants.KEY_COLUMN);
      removeCellRowColumnPair.add(new ImmutablePair<>(row, column));
      removeCellRowColumnPair.add(new ImmutablePair<>(row + 1, column));
      removeCellRowColumnPair.add(new ImmutablePair<>(row - 1, column));
    }
  }
}
