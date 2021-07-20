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

import com.axelor.common.ObjectUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder.BorderSide;

public class XSSFHtmlHelper implements HtmlHelper {
  private final XSSFWorkbook wb;

  public XSSFHtmlHelper(XSSFWorkbook wb) {
    this.wb = wb;
  }

  public void colorStyles(CellStyle style, Formatter out) {
    XSSFCellStyle cs = (XSSFCellStyle) style;
    List<XSSFColor> borderColorList = new ArrayList<>();

    styleColor(out, "background-color", cs.getFillForegroundXSSFColor());
    styleColor(out, "color", cs.getFont().getXSSFColor());

    borderColorList.add(
        cs.getBorderTop().equals(BorderStyle.NONE) ? null : cs.getBorderColor(BorderSide.TOP));
    borderColorList.add(
        cs.getBorderRight().equals(BorderStyle.NONE) ? null : cs.getBorderColor(BorderSide.RIGHT));
    borderColorList.add(
        cs.getBorderBottom().equals(BorderStyle.NONE)
            ? null
            : cs.getBorderColor(BorderSide.BOTTOM));
    borderColorList.add(
        cs.getBorderLeft().equals(BorderStyle.NONE) ? null : cs.getBorderColor(BorderSide.LEFT));

    styleBorderColor(out, "border-color", borderColorList);
  }

  private void styleColor(Formatter out, String attr, XSSFColor color) {
    if (color == null || color.isAuto()) return;

    byte[] rgb = color.getRGB();
    if (rgb == null) {
      return;
    }

    // This is done twice -- rgba is new with CSS 3, and browser that don't
    // support it will ignore the rgba specification and stick with the
    // solid color, which is declared first
    out.format("  %s: #%02x%02x%02x;%n", attr, rgb[0], rgb[1], rgb[2]);
    byte[] argb = color.getARGB();
    if (argb == null) {
      return;
    }
    out.format(
        "  %s: rgba(0x%02x, 0x%02x, 0x%02x, 0x%02x);%n", attr, argb[3], argb[0], argb[1], argb[2]);
  }

  private void styleBorderColor(Formatter out, String attr, List<XSSFColor> colorList) {
    if (colorList.isEmpty()) {
      return;
    }

    XSSFColor black = new XSSFColor(Color.black, wb.getStylesSource().getIndexedColors());
    StringBuilder borderColor = new StringBuilder();

    for (XSSFColor color : colorList) {
      byte[] rgb = ObjectUtils.isEmpty(color) ? black.getRGB() : color.getRGB();
      if (ObjectUtils.isEmpty(rgb)) {
        return;
      }
      borderColor.append(String.format(" #%02x%02x%02x", rgb[0], rgb[1], rgb[2]));
    }

    out.format("  %1$s: %2$s;%n", attr, borderColor);
  }
}
