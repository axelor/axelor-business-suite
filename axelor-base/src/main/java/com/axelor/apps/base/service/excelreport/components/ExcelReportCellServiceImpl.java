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

import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.IndexedColors;

public class ExcelReportCellServiceImpl implements ExcelReportCellService {

  @Override
  public Object getCellValue(Cell cell) {
    Object value = null;
    switch (cell.getCellType()) {
      case BOOLEAN:
        value = cell.getBooleanCellValue();
        break;
      case NUMERIC:
        value = cell.getNumericCellValue();
        break;
      case STRING:
        value = cell.getRichStringCellValue();
        break;
      case BLANK:
        value = cell.getStringCellValue();
        break;
      case ERROR:
        break;
      case FORMULA:
        break;
      case _NONE:
        break;
      default:
        break;
    }
    return value;
  }

  @Override
  public boolean isCellEmpty(Cell cell) {
    BorderStyle borderStyleNone = BorderStyle.NONE;

    boolean isBlank = cell.getCellType().equals(CellType.BLANK);
    boolean isEmpty =
        (cell.getCellType() == CellType.STRING && StringUtils.isBlank(cell.getStringCellValue()))
            || cell.getCellType() == CellType.FORMULA;
    boolean hasNoBorder =
        cell.getCellStyle().getBorderLeft().equals(borderStyleNone)
            && cell.getCellStyle().getBorderRight().equals(borderStyleNone)
            && cell.getCellStyle().getBorderTop().equals(borderStyleNone)
            && cell.getCellStyle().getBorderBottom().equals(borderStyleNone);
    boolean hasNoBackgroundColor = cell.getCellStyle().getFillBackgroundColor() == 64;

    return isEmpty || (isBlank && hasNoBorder && hasNoBackgroundColor);
  }

  @Override
  public short getCellFooterFontColor(String footerFontColor) {

    short color = IndexedColors.BLACK.getIndex();
    if (ObjectUtils.isEmpty(footerFontColor)) {
      return color;
    }
    switch (footerFontColor) {
      case "blue":
        color = IndexedColors.BLUE.getIndex();
        break;
      case "cyan":
        color = IndexedColors.LIGHT_BLUE.getIndex();
        break;
      case "dark-gray":
        color = IndexedColors.GREY_80_PERCENT.getIndex();
        break;
      case "gray":
        color = IndexedColors.GREY_50_PERCENT.getIndex();
        break;
      case "green":
        color = IndexedColors.GREEN.getIndex();
        break;
      case "light-gray":
        color = IndexedColors.GREY_25_PERCENT.getIndex();
        break;
      case "magneta":
        color = IndexedColors.LAVENDER.getIndex();
        break;
      case "orange":
        color = IndexedColors.ORANGE.getIndex();
        break;
      case "pink":
        color = IndexedColors.PINK.getIndex();
        break;
      case "red":
        color = IndexedColors.RED.getIndex();
        break;
      case "white":
        color = IndexedColors.WHITE.getIndex();
        break;
      case "yellow":
        color = IndexedColors.YELLOW.getIndex();
        break;
      default:
        break;
    }
    return color;
  }
}
