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
package com.axelor.apps.base.service.excelreport.html;

import java.util.Formatter;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFPalette;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.CellStyle;

public class HSSFHtmlHelper implements HtmlHelper {
  private final HSSFWorkbook wb;
  private final HSSFPalette colors;

  private static final HSSFColor HSSF_AUTO = new HSSFColor();

  public HSSFHtmlHelper(HSSFWorkbook wb) {
    this.wb = wb;
    // If there is no custom palette, then this creates a new one that is
    // a copy of the default
    colors = wb.getCustomPalette();
  }

  public void colorStyles(CellStyle style, Formatter out) {
    HSSFCellStyle cs = (HSSFCellStyle) style;
    out.format("  /* fill pattern = %d */%n", cs.getFillPattern());
    styleColor(out, "background-color", cs.getFillForegroundColor());
    styleColor(out, "color", cs.getFont(wb).getColor());
    styleColor(out, "border-left-color", cs.getLeftBorderColor());
    styleColor(out, "border-right-color", cs.getRightBorderColor());
    styleColor(out, "border-top-color", cs.getTopBorderColor());
    styleColor(out, "border-bottom-color", cs.getBottomBorderColor());
  }

  private void styleColor(Formatter out, String attr, short index) {
    HSSFColor color = colors.getColor(index);
    if (index == HSSF_AUTO.getIndex() || color == null) {
      out.format("  /* %s: index = %d */%n", attr, index);
    } else {
      short[] rgb = color.getTriplet();
      out.format("  %s: #%02x%02x%02x; /* index = %d */%n", attr, rgb[0], rgb[1], rgb[2], index);
    }
  }
}
