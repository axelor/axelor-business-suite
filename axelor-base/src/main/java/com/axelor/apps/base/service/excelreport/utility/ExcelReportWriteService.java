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

import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

public interface ExcelReportWriteService {

  public void writeTemplateSheet(
      Map<Integer, Map<String, Object>> outputMap,
      Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet,
      Sheet originSheet,
      Sheet sheet,
      int offset);

  public void write(
      Map<Integer, Map<String, Object>> outputMap,
      Sheet originSheet,
      Sheet sheet,
      int offset,
      boolean setExtraHeight);
}
