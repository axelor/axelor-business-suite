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

import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.excelreport.components.ExcelReportPictureService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.itextpdf.awt.geom.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.format.CellFormat;
import org.apache.poi.ss.format.CellFormatResult;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Shape;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

public class Excel2HtmlConvertor {

  private ExcelReportPictureService excelReportPictureService;

  private final Workbook wb;
  private boolean gotBounds;
  private int firstColumn;
  private int endColumn;
  private HtmlHelper helper;
  private String headerHtml;
  private String footerHtml;
  private boolean landscape;
  private int headerHeight;
  private int footerHeight;
  private List<Picture> pictureList;
  private BigDecimal dataSizeModification;
  private boolean hasWatermark;
  private int watermarkAngle;

  private static final String DEFAULTS_CLASS = "excelDefaults";
  private static final String STYLE_SHEET_FILE = "/reports/excel/excelStyle.css";

  private static final Map<HorizontalAlignment, String> HALIGN =
      mapFor(
          HorizontalAlignment.LEFT,
          "left",
          HorizontalAlignment.CENTER,
          "center",
          HorizontalAlignment.RIGHT,
          "right",
          HorizontalAlignment.FILL,
          "left",
          HorizontalAlignment.JUSTIFY,
          "left",
          HorizontalAlignment.CENTER_SELECTION,
          "center");

  private static final Map<VerticalAlignment, String> VALIGN =
      mapFor(
          VerticalAlignment.BOTTOM,
          "bottom",
          VerticalAlignment.CENTER,
          "middle",
          VerticalAlignment.TOP,
          "top");

  private static final Map<BorderStyle, String> BORDER =
      mapFor(
          BorderStyle.DASH_DOT,
          "dashed 1pt",
          BorderStyle.DASH_DOT_DOT,
          "dashed 1pt",
          BorderStyle.DASHED,
          "dashed 1pt",
          BorderStyle.DOTTED,
          "dotted 1pt",
          BorderStyle.DOUBLE,
          "double 3pt",
          BorderStyle.HAIR,
          "solid 1px",
          BorderStyle.MEDIUM,
          "solid 2pt",
          BorderStyle.MEDIUM_DASH_DOT,
          "dashed 2pt",
          BorderStyle.MEDIUM_DASH_DOT_DOT,
          "dashed 2pt",
          BorderStyle.MEDIUM_DASHED,
          "dashed 2pt",
          BorderStyle.NONE,
          "none",
          BorderStyle.SLANTED_DASH_DOT,
          "dashed 2pt",
          BorderStyle.THICK,
          "solid 3pt",
          BorderStyle.THIN,
          "solid 1pt");

  @SuppressWarnings({"unchecked"})
  private static <K, V> Map<K, V> mapFor(Object... mapping) {
    Map<K, V> map = new HashMap<K, V>();
    for (int i = 0; i < mapping.length; i += 2) {
      map.put((K) mapping[i], (V) mapping[i + 1]);
    }
    return map;
  }

  public static Excel2HtmlConvertor create(
      Workbook wb, String header, String footer, PrintTemplate printTemplate) {
    return new Excel2HtmlConvertor(wb, header, footer, printTemplate);
  }

  public static Excel2HtmlConvertor create(
      String path, String header, String footer, PrintTemplate printTemplate) throws IOException {
    return create(new FileInputStream(path), header, footer, printTemplate);
  }

  public static Excel2HtmlConvertor create(
      InputStream in, String header, String footer, PrintTemplate printTemplate)
      throws IOException {
    Workbook wb = WorkbookFactory.create(in);
    return create(wb, header, footer, printTemplate);
  }

  private Excel2HtmlConvertor(
      Workbook wb, String header, String footer, PrintTemplate printTemplate) {
    if (wb == null) {
      throw new NullPointerException(I18n.get(IExceptionMessage.WORKBOOK_NULL));
    }
    this.wb = wb;
    this.footerHtml = footer;
    this.headerHtml = header;
    this.landscape = printTemplate.getDisplayTypeSelect() == 2;
    this.headerHeight = printTemplate.getHeaderHeight().intValue();
    this.footerHeight = printTemplate.getFooterHeight().intValue();
    this.dataSizeModification = printTemplate.getDataSizeModification();
    this.hasWatermark = StringUtils.notEmpty(printTemplate.getWatermarkText());
    this.watermarkAngle = printTemplate.getWatermarkAngle();
    this.excelReportPictureService = Beans.get(ExcelReportPictureService.class);
    setupColorMap();
    getPictures(wb.getSheetAt(0));
  }

  private void setupColorMap() {
    if (wb instanceof HSSFWorkbook) {
      helper = new HSSFHtmlHelper((HSSFWorkbook) wb);
    } else if (wb instanceof XSSFWorkbook) {
      helper = new XSSFHtmlHelper((XSSFWorkbook) wb);
    } else {
      throw new IllegalArgumentException(
          I18n.get(IExceptionMessage.UNKNOWN_WORKBOOK) + wb.getClass().getSimpleName());
    }
  }

  public File printPage() throws IOException {
    Document document = getDocument();
    Element body = document.body();
    Element head = document.head();
    head.appendChild(getStyleTag());
    body.append(getWatermarkObject());
    body.appendChild(getHeader());
    body.insertChildren(-1, getSheetAsTables());
    body.appendChild(getFooter());
    OutputSettings outputSettings =
        new OutputSettings()
            .syntax(OutputSettings.Syntax.xml)
            .charset(StandardCharsets.UTF_8)
            .prettyPrint(true);

    document.outputSettings(outputSettings);
    Path output = MetaFiles.createTempFile(I18n.get("index"), ".html");
    String html = document.html();
    Files.write(output, html.getBytes());
    return output.toFile();
  }

  protected Element getHeader() {
    Element header = new Element("header");
    header.html(this.headerHtml);
    header.attr("style", "position: running(header);");
    return header;
  }

  protected Element getFooter() {
    Element footer = new Element("footer");
    if (ObjectUtils.notEmpty(this.footerHtml)) {
      footer.html(this.footerHtml);
      footer.attr("style", "position: running(footer);");
    }
    return footer;
  }

  protected Document getDocument() {
    Document document = Document.createShell("");
    document.parser(Parser.xmlParser());
    return document;
  }

  protected void getPictures(Sheet sheet) {
    pictureList = new ArrayList<>();
    Drawing<?> drawing = sheet.getDrawingPatriarch();
    if (ObjectUtils.notEmpty(drawing)) {
      for (Shape shape : ((XSSFDrawing) drawing).getShapes()) {
        if (shape instanceof Picture) {
          Picture picture = (XSSFPicture) shape;
          pictureList.add(picture);
        }
      }
    }
  }

  protected String getWatermarkObject() {
    String watermarkObject = "";
    if (hasWatermark) {
      watermarkObject = "<object type=\"watermark\"></object>";
    }
    return watermarkObject;
  }

  protected Element getStyleTag() {
    getDocument();
    // First, copy the base css
    Element style = new Element("style");
    style.attr("type", "text/css");
    BufferedReader in = null;
    StringBuilder sb = new StringBuilder();
    DataNode data = new DataNode("");
    try {
      in =
          new BufferedReader(
              new InputStreamReader(getClass().getResourceAsStream(STYLE_SHEET_FILE)));
      String line;
      sb.append("\n");
      while ((line = in.readLine()) != null) {
        sb.append(line + "\n");
      }
    } catch (IOException e) {
      throw new IllegalStateException(I18n.get(IExceptionMessage.READING_STANDARD_CSS), e);
    } finally {
      IOUtils.closeQuietly(in);
    }
    // now add css for each used style
    Set<CellStyle> seen = new HashSet<>();
    for (int i = 0; i < wb.getNumberOfSheets(); i++) {
      Sheet sheet = wb.getSheetAt(i);
      Iterator<Row> rows = sheet.rowIterator();
      while (rows.hasNext()) {
        Row row = rows.next();
        for (Cell cell : row) {
          CellStyle cellStyle = cell.getCellStyle();
          if (!seen.contains(cellStyle)) {
            sb.append(printStyle(cellStyle));
            seen.add(cellStyle);
          }
        }
      }
    }
    String cssStyles = sb.toString();
    if (landscape) {
      cssStyles = cssStyles.replace("size: A4;", "size: A4 landscape;");
    }
    cssStyles = setWatermarkAngle(cssStyles, hasWatermark);
    cssStyles = this.setHeaderFooterHeight(cssStyles);
    data.setWholeData(cssStyles);
    style.appendChild(data);
    return style;
  }

  protected String setWatermarkAngle(String cssStyles, boolean hasWatermark) {
    if (hasWatermark && watermarkAngle != 45) {
      cssStyles =
          cssStyles.replace(
              "transform: rotate(45deg);",
              String.format("transform: rotate(%ddeg);", watermarkAngle));
    }

    return cssStyles;
  }

  protected String setHeaderFooterHeight(String cssStyles) {
    if (headerHeight > 125) {
      cssStyles =
          cssStyles.replaceFirst(
              "margin-top: 125px;", String.format("margin-top: %dpx;", headerHeight));
    }

    if (footerHeight > 125) {
      cssStyles =
          cssStyles.replaceFirst(
              "margin-bottom: 125px;", String.format("margin-bottom: %dpx;", footerHeight));
    }
    return cssStyles;
  }

  protected String printStyle(CellStyle style) {
    Formatter cssOutput = new Formatter();
    cssOutput.format(".%s .%s {%n", DEFAULTS_CLASS, styleName(style));
    styleContents(cssOutput, style);
    cssOutput.format("}%n");
    return cssOutput.toString();
  }

  protected void styleContents(Formatter cssOutput, CellStyle style) {
    styleOut("text-align", style.getAlignment(), HALIGN, cssOutput);
    styleOut("vertical-align", style.getVerticalAlignment(), VALIGN, cssOutput);
    fontStyle(style, cssOutput);
    borderStyles(style, cssOutput);
    helper.colorStyles(style, cssOutput);
  }

  protected void borderStyles(CellStyle style, Formatter cssOutput) {
    styleOut("border-left", style.getBorderLeft(), BORDER, cssOutput);
    styleOut("border-right", style.getBorderRight(), BORDER, cssOutput);
    styleOut("border-top", style.getBorderTop(), BORDER, cssOutput);
    styleOut("border-bottom", style.getBorderBottom(), BORDER, cssOutput);
  }

  protected void fontStyle(CellStyle style, Formatter cssOutput) {
    Font font = wb.getFontAt(style.getFontIndexAsInt());
    if (font.getBold()) {
      cssOutput.format("  font-weight: bold;%n");
    }
    if (font.getItalic()) {
      cssOutput.format("  font-style: italic;%n");
    }

    int fontheight = font.getFontHeightInPoints();
    if (fontheight == 9) {
      fontheight = 10;
    }

    if (!dataSizeModification.equals(BigDecimal.valueOf(100))) {
      fontheight = (fontheight * dataSizeModification.intValue()) / 100;
    }

    cssOutput.format("  font-size: %dpx;%n", fontheight + 2);
    String fontName = font.getFontName().replace("'", "\\'");
    cssOutput.format("  font-family: %s;%n", fontName);
    // Font color is handled with the other colors
  }

  protected String styleName(CellStyle style) {
    if (style == null) {
      style = wb.getCellStyleAt((short) 0);
    }
    StringBuilder sb = new StringBuilder();
    Formatter fmt = new Formatter(sb);
    try {
      fmt.format("style_%02x", style.getIndex());
      return fmt.toString();
    } finally {
      fmt.close();
    }
  }

  protected <K> void styleOut(String attr, K key, Map<K, String> mapping, Formatter cssOutput) {
    String value = mapping.get(key);
    if (value != null) {
      cssOutput.format("  %s: %s;%n", attr, value);
    }
  }

  protected static CellType ultimateCellType(Cell c) {
    CellType type = c.getCellType();
    if (type == CellType.FORMULA) {
      type = c.getCachedFormulaResultType();
    }
    return type;
  }

  protected List<Element> getSheetAsTables() throws IOException {
    List<Element> tables = new ArrayList<>();
    for (int i = 0; i < wb.getNumberOfSheets(); i++) {
      Sheet sheet = wb.getSheetAt(i);
      tables.add(getSheetAsTable(sheet));
    }
    return tables;
  }

  protected Element getSheetAsTable(Sheet sheet) throws IOException {
    Element table = new Element("table");
    table.addClass(DEFAULTS_CLASS);
    table.appendChild(getColGroup(sheet));
    table.appendChild(getTableBody(sheet));
    return table;
  }

  protected Element getColGroup(Sheet sheet) {
    Element colgroup = new Element("colgroup");
    Element col = null;
    ensureColumnBounds(sheet);
    BigDecimal totalWidth = BigDecimal.ZERO;
    for (int i = firstColumn; i < endColumn; i++) {
      totalWidth = totalWidth.add(BigDecimal.valueOf(sheet.getColumnWidth(i)));
    }
    for (int i = firstColumn; i < endColumn; i++) {
      BigDecimal columnWidth =
          BigDecimal.valueOf(sheet.getColumnWidth(i))
              .multiply(BigDecimal.valueOf(100))
              .divide(totalWidth, 2, RoundingMode.HALF_UP);
      col = new Element("col");
      col.attr("style", "width: " + columnWidth + "%");
      colgroup.appendChild(col);
    }
    return colgroup;
  }

  protected void ensureColumnBounds(Sheet sheet) {
    if (gotBounds) {
      return;
    }

    Iterator<Row> iter = sheet.rowIterator();
    firstColumn = (iter.hasNext() ? Integer.MAX_VALUE : 0);
    endColumn = 0;
    while (iter.hasNext()) {
      Row row = iter.next();
      short firstCell = row.getFirstCellNum();
      if (firstCell >= 0) {
        firstColumn = Math.min(firstColumn, firstCell);
        endColumn = Math.max(endColumn, row.getLastCellNum());
      }
    }
    gotBounds = true;
  }

  private Element getTableBody(Sheet sheet) throws IOException {
    Element tbody = new Element("tbody");
    Iterator<Row> rows = sheet.rowIterator();
    List<CellRangeAddress> mergedRegionList = sheet.getMergedRegions();
    while (rows.hasNext()) {
      Row row = rows.next();

      Element tr = new Element("tr");
      StringBuilder trStyle = new StringBuilder();

      if (StringUtils.notBlank(trStyle)) {
        tr.attr("style", trStyle.toString());
      }

      tbody.appendChild(tr);
      for (int i = firstColumn; i < endColumn; i++) {
        String content = "";
        String styleAttrs = "";
        Pair<Integer, Integer> rowColspan = null;
        CellStyle style = null;
        if (i >= row.getFirstCellNum() && i < row.getLastCellNum()) {
          Cell cell = row.getCell(i);

          if (cell != null) {
            style = cell.getCellStyle();
            styleAttrs = tagStyle(cell, style);
            // set subscript and superscript tags
            setSubscriptsAndSuperscripts(cell);
            // Set the value that is rendered for the cell
            // also applies the format
            CellFormat cf = CellFormat.getInstance(style.getDataFormatString());
            CellFormatResult result = cf.apply(cell);
            content = result.text;
            Optional<CellRangeAddress> mergedCellAddrOpt = getMergedRegion(mergedRegionList, cell);
            Boolean isInMergedRegion = mergedCellAddrOpt.isPresent();
            // to skip cell if it is in between start and end
            if (isInMergedRegion) {
              CellRangeAddress mergedRegionAddr = mergedCellAddrOpt.get();
              if (isInBetweenMergedRegion(mergedRegionAddr, cell)) {
                continue;
              }
              rowColspan = getRowAndColumnSpan(mergedRegionAddr, cell);
            }
            if (StringUtils.isBlank(content)) {
              if (isInMergedRegion) {
                continue;
              }
              content = "<span style=\"display:inline-block;width:0.5em;\"></span>";
            }
            if (content.equals("Merged Cell")) {
              content = setImage(cell, content);
            }
          }
        }

        Element td = new Element("td");
        td.addClass(styleName(style));
        if (ObjectUtils.notEmpty(rowColspan)) {
          if (content.length() > 10 && content.length() <= 50) {
            styleAttrs = styleAttrs.concat(" white-space: nowrap;");
          }
          td.attr("rowspan", rowColspan.getLeft().toString());
          td.attr("colspan", rowColspan.getRight().toString());
        }
        if (styleAttrs != null && styleAttrs.trim().length() > 0) {
          td.attr("style", styleAttrs);
        }

        td.append(content);
        tr.appendChild(td);
      }
    }

    return tbody;
  }

  // sets image if any
  private String setImage(Cell cell, String content) throws IOException {
    if (!pictureList.isEmpty()) {
      Picture removePicture = null;
      Sheet sheet = cell.getSheet();
      for (Picture picture : pictureList) {
        if (picture.getClientAnchor().getRow1() == cell.getRowIndex()
            && picture.getClientAnchor().getCol1() == cell.getColumnIndex()) {
          // fetch dimension
          Dimension dimension =
              excelReportPictureService.getDimensions(sheet, picture, sheet.getMergedRegions());
          // save picture as file
          byte[] bytepic = picture.getPictureData().getData();
          String extension = picture.getPictureData().suggestFileExtension();
          Path imagePath =
              MetaFiles.createTempFile(I18n.get("image"), String.format(".%s", extension));
          File image = Files.write(imagePath, bytepic).toFile();
          // set <img/> tag
          content =
              String.format(
                  "<img src=\"%s\" alt=\"Image\" width=\"%.2fpx\" height=\"%.2fpx\"/>",
                  image.toURI(), dimension.getWidth(), dimension.getHeight());
          removePicture = picture;
          break;
        }
      }
      // remove added picture from list
      pictureList.remove(removePicture);
    }
    return content;
  }

  private void setSubscriptsAndSuperscripts(Cell cell) {
    int runIndex = 0;
    int runLength = 0;
    XSSFCellStyle style = null;
    XSSFFont rtsFont = null;
    XSSFRichTextString rts = (XSSFRichTextString) cell.getRichStringCellValue();
    style = (XSSFCellStyle) cell.getCellStyle();
    rtsFont = style.getFont();

    ImmutablePair<String, String> superTagPair = new ImmutablePair<>("<sup>", "</sup>");
    ImmutablePair<String, String> subTagPair = new ImmutablePair<>("<sub>", "</sub>");

    if (rts.numFormattingRuns() > 1) {
      for (int k = 0; k < rts.numFormattingRuns(); k++) {

        runLength = rts.getLengthOfFormattingRun(k);
        runIndex = rts.getIndexOfFormattingRun(k);
        String scriptText = rts.toString().substring(runIndex, (runIndex + runLength));
        rtsFont = rts.getFontOfFormattingRun(k);

        if (rtsFont.getTypeOffset() == XSSFFont.SS_SUPER) {
          rts.setString(
              rts.getString()
                  .replace(
                      scriptText, superTagPair.getLeft() + scriptText + superTagPair.getRight()));
        }
        if (rtsFont.getTypeOffset() == XSSFFont.SS_SUB) {
          rts.setString(
              rts.getString()
                  .replace(scriptText, subTagPair.getLeft() + scriptText + subTagPair.getRight()));
        }
      }
    }
  }

  private boolean isInBetweenMergedRegion(CellRangeAddress address, Cell cell) {
    int columnIndex = cell.getColumnIndex();
    int rowIndex = cell.getRowIndex();
    if (address.getFirstColumn() == columnIndex
        || address.getLastColumn() == columnIndex
        || address.getFirstRow() == rowIndex
        || address.getLastRow() == rowIndex) {
      return false;
    }
    return true;
  }

  private ImmutablePair<Integer, Integer> getRowAndColumnSpan(
      CellRangeAddress cellRangeAddress, Cell cell) {
    int rowSpan = 1;
    int colSpan = 1;
    if (cellRangeAddress.getFirstRow() == cell.getRowIndex()
        || cellRangeAddress.getFirstColumn() == cell.getColumnIndex()) {
      rowSpan = cellRangeAddress.getLastRow() - cellRangeAddress.getFirstRow() + 1;
      colSpan = cellRangeAddress.getLastColumn() - cellRangeAddress.getFirstColumn() + 1;
    }
    return ImmutablePair.of(rowSpan, colSpan);
  }

  private Optional<CellRangeAddress> getMergedRegion(
      List<CellRangeAddress> mergedRegionList, Cell cell) {
    if (ObjectUtils.isEmpty(mergedRegionList)) {
      return Optional.empty();
    }
    return mergedRegionList.stream().filter(region -> region.isInRange(cell)).findFirst();
  }

  private String tagStyle(Cell cell, CellStyle style) {
    String styleText = "";

    // set text-align
    if (style.getAlignment() == HorizontalAlignment.GENERAL) {
      switch (ultimateCellType(cell)) {
        case STRING:
          styleText = styleText.concat("text-align: left;");
          break;
        case BOOLEAN:
          break;
        case ERROR:
          styleText = styleText.concat("text-align: center;");
          break;
        case NUMERIC:
          break;
        default:
          // "right" is the default
          break;
      }
    }

    // set text-decoration
    if (((XSSFCellStyle) style).getFont().getUnderline() == Font.U_SINGLE) {
      styleText = styleText.concat(" text-decoration: underline;");
    }

    return styleText;
  }
}
