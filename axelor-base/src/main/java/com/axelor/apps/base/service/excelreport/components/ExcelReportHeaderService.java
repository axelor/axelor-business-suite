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
package com.axelor.apps.base.service.excelreport.components;

import com.axelor.apps.base.db.Print;
import com.itextpdf.awt.geom.Dimension;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

public interface ExcelReportHeaderService {

  public Map<Integer, Map<String, Object>> getHeaderInputMap(Workbook wb, Print print);

  public void setHeader(
      Sheet originSheet,
      Sheet sheet,
      Print print,
      Map<Integer, Map<String, Object>> outputMap,
      Map<Integer, Map<String, Object>> headerOutputMap,
      Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet,
      Map<String, List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>>>
          pictureInputMap,
      Map<String, List<MutablePair<Integer, Integer>>> pictureRowShiftMap);

  public Integer getHeaderLines(Map<Integer, Map<String, Object>> headerOutputMap);

  public String generateHeaderHtml(Print print);
}
