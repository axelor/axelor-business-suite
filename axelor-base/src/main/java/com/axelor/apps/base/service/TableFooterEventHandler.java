/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service;

import com.axelor.apps.base.db.Print;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.TextAlignment;
import java.io.IOException;
import java.math.BigDecimal;

public class TableFooterEventHandler implements IEventHandler {
  protected Document doc;
  protected Print print;
  protected Cell cellFooter;

  public TableFooterEventHandler(Document doc, Print print) throws IOException {
    this.doc = doc;
    this.print = print;

    cellFooter = new Cell();
    cellFooter.setBorder(Border.NO_BORDER);
    if (ObjectUtils.notEmpty(print.getFooterWidth())) {
      cellFooter.setWidth(print.getFooterWidth().floatValue());
    }

    if (StringUtils.notEmpty(print.getPrintPdfFooter())) {
      String footerTextAlignment = print.getFooterTextAlignment();
      String footerFontColor = print.getFooterFontColor();

      if (footerTextAlignment != null) {
        this.setCellFooterTextAlignment(cellFooter, footerTextAlignment);
      }
      cellFooter.setFont(
          PdfFontFactory.createFont(
              print.getFooterFontType() != null
                  ? print.getFooterFontType()
                  : StandardFonts.TIMES_ROMAN));
      cellFooter.setFontSize(
          print.getFooterFontSize().compareTo(BigDecimal.ZERO) > 0
              ? print.getFooterFontSize().floatValue()
              : 10);
      if (footerFontColor != null) {
        this.setCellFooterFontColor(cellFooter, footerFontColor);
      }
      if (print.getIsFooterUnderLine()) {
        cellFooter.setUnderline();
      }
      cellFooter.add(
          new Paragraph(print.getPrintPdfFooter() != null ? print.getPrintPdfFooter() : ""));
    }
  }

  public Table generateTableFooter(Event event) {
    Table tableFooter = new Table(2);

    tableFooter = new Table(2);
    tableFooter.setWidth(
        doc.getPdfDocument().getDefaultPageSize().getRight()
            - doc.getPdfDocument().getDefaultPageSize().getLeft()
            - doc.getLeftMargin()
            - doc.getRightMargin());

    tableFooter.setMarginTop(20);

    tableFooter.addCell(cellFooter);

    if (print.getWithPagination()) {
      Cell paginationCell = new Cell();
      paginationCell.setBorder(Border.NO_BORDER);
      if (ObjectUtils.notEmpty(print.getPaginationWidth())) {
        paginationCell.setWidth(print.getPaginationWidth().floatValue());
      }
      String paginationTextAlignment = print.getPaginationTextAlignment();
      if (paginationTextAlignment != null) {
        this.setCellFooterTextAlignment(paginationCell, paginationTextAlignment);
      }
      try {
        paginationCell.setFont(
            PdfFontFactory.createFont(
                print.getPaginationFontType() != null
                    ? print.getPaginationFontType()
                    : StandardFonts.TIMES_ROMAN));
      } catch (IOException e) {
        paginationCell.setFont(StandardFonts.TIMES_ROMAN);
      }
      paginationCell.setFontSize(
          print.getPaginationFontSize().compareTo(BigDecimal.ZERO) > 0
              ? print.getPaginationFontSize().floatValue()
              : 10);
      String paginationFontColor = print.getPaginationFontColor();
      if (paginationFontColor != null) {
        this.setCellFooterFontColor(paginationCell, paginationFontColor);
      }
      if (print.getIsPaginationUnderLine()) {
        paginationCell.setUnderline();
      }

      PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
      PdfDocument pdf = docEvent.getDocument();
      PdfPage page = docEvent.getPage();
      int pageNumber = pdf.getPageNumber(page);

      paginationCell.add(
          new Paragraph(
              StringUtils.notEmpty(print.getPaginationTemplate())
                  ? String.format(print.getPaginationTemplate(), pageNumber, pdf.getNumberOfPages())
                  : ""));

      tableFooter.addCell(paginationCell);
    }

    return tableFooter;
  }

  @SuppressWarnings("resource")
  @Override
  public void handleEvent(Event event) {
    PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
    PdfDocument pdfDoc = docEvent.getDocument();
    PdfPage page = docEvent.getPage();
    PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);
    Rectangle rect1 =
        new Rectangle(
            pdfDoc.getDefaultPageSize().getX() + doc.getLeftMargin(),
            pdfDoc.getDefaultPageSize().getBottom() - doc.getBottomMargin(),
            100,
            90);
    Table tableFooter = generateTableFooter(event);
    new Canvas(canvas, pdfDoc, rect1).add(tableFooter);
  }

  private void setCellFooterTextAlignment(Cell cellFooter, String footerTextAlignment) {

    switch (footerTextAlignment) {
      case "left":
        cellFooter.setTextAlignment(TextAlignment.LEFT);
        break;
      case "center":
        cellFooter.setTextAlignment(TextAlignment.CENTER);
        break;
      case "right":
        cellFooter.setTextAlignment(TextAlignment.RIGHT);
        break;
      default:
        break;
    }
  }

  private void setCellFooterFontColor(Cell cellFooter, String footerFontColor) {

    switch (footerFontColor) {
      case "blue":
        cellFooter.setFontColor(ColorConstants.BLUE);
        break;
      case "cyan":
        cellFooter.setFontColor(ColorConstants.CYAN);
        break;
      case "dark-gray":
        cellFooter.setFontColor(ColorConstants.DARK_GRAY);
        break;
      case "gray":
        cellFooter.setFontColor(ColorConstants.GRAY);
        break;
      case "green":
        cellFooter.setFontColor(ColorConstants.GREEN);
        break;
      case "light-gray":
        cellFooter.setFontColor(ColorConstants.LIGHT_GRAY);
        break;
      case "magneta":
        cellFooter.setFontColor(ColorConstants.MAGENTA);
        break;
      case "orange":
        cellFooter.setFontColor(ColorConstants.ORANGE);
        break;
      case "pink":
        cellFooter.setFontColor(ColorConstants.PINK);
        break;
      case "red":
        cellFooter.setFontColor(ColorConstants.RED);
        break;
      case "white":
        cellFooter.setFontColor(ColorConstants.WHITE);
        break;
      case "yellow":
        cellFooter.setFontColor(ColorConstants.YELLOW);
        break;
      default:
        break;
    }
  }
}
