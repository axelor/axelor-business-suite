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

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Print;
import com.axelor.apps.base.db.repo.PrintRepository;
import com.axelor.apps.base.service.excelreport.components.HtmlToExcel.RichTextDetails;
import com.axelor.apps.base.service.excelreport.config.ExcelReportConstants;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportCellMergingService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportShiftingService;
import com.axelor.apps.base.service.excelreport.utility.ExcelReportWriteService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.meta.MetaFiles;
import com.google.inject.Inject;
import com.itextpdf.awt.geom.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;

public class ExcelReportHeaderServiceImpl implements ExcelReportHeaderService {

  @Inject private HtmlToExcel htmlToExcel;
  @Inject private ExcelReportWriteService excelReportWriteService;
  @Inject private ExcelReportShiftingService excelReportShiftingService;
  @Inject private ExcelReportCellMergingService excelReportCellMergingService;
  @Inject private ExcelReportPictureService excelReportPictureService;

  @Override
  public Map<Integer, Map<String, Object>> getHeaderInputMap(Workbook wb, Print print) {
    Map<Integer, Map<String, Object>> map = new HashMap<>();
    if (ObjectUtils.notEmpty(print.getPrintPdfHeader())) {
      Map<String, Object> dataMap = new HashMap<>();
      dataMap.put(ExcelReportConstants.KEY_ROW, 1);
      dataMap.put(ExcelReportConstants.KEY_COLUMN, 1);
      dataMap.put(ExcelReportConstants.KEY_VALUE, "");
      dataMap.put(ExcelReportConstants.KEY_CELL_STYLE, wb.createCellStyle());
      map.put(0, dataMap);
    }
    return map;
  }

  @Override
  public void setHeader(
      Sheet originSheet,
      Sheet sheet,
      Print print,
      Map<Integer, Map<String, Object>> outputMap,
      Map<Integer, Map<String, Object>> headerOutputMap,
      Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet,
      Map<String, List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>>>
          pictureInputMap,
      Map<String, List<MutablePair<Integer, Integer>>> pictureRowShiftMap) {

    String html = print.getPrintPdfHeader();
    if (StringUtils.notEmpty(html)) {
      // convert html to rich text
      List<RichTextDetails> cellValues = new ArrayList<>();
      RichTextString cellValue = new XSSFRichTextString(html);
      cellValues.add(htmlToExcel.createCellValue(html, sheet.getWorkbook()));
      if (ObjectUtils.notEmpty(cellValues.get(0))) {
        cellValue = htmlToExcel.mergeTextDetails(cellValues);
      }
      // set rich text in map
      headerOutputMap.get(0).replace(ExcelReportConstants.KEY_VALUE, cellValue);
    }

    int lastHeaderLineRow = 0;
    if (ObjectUtils.notEmpty(headerOutputMap)) {
      lastHeaderLineRow = this.getHeaderLines(headerOutputMap);
      excelReportWriteService.write(headerOutputMap, originSheet, sheet, 0, true);
    }

    int offset = 2;
    if (outputMap.size() != 0) {
      offset = lastHeaderLineRow - (int) outputMap.get(0).get(ExcelReportConstants.KEY_ROW) + 2;
    }

    if (offset != 0) {
      excelReportShiftingService.shiftAll(outputMap, offset);
      excelReportCellMergingService.shiftMergedRegions(mergedCellsRangeAddressSetPerSheet, offset);
    }
    setPictures(sheet, pictureInputMap, pictureRowShiftMap, offset);
  }

  private void setPictures(
      Sheet sheet,
      Map<String, List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>>>
          pictureInputMap,
      Map<String, List<MutablePair<Integer, Integer>>> pictureRowShiftMap,
      int offset) {
    List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>> templateTripleList =
        pictureInputMap.get(ExcelReportConstants.TEMPLATE_SHEET_TITLE);
    if (ObjectUtils.notEmpty(templateTripleList)) {
      excelReportPictureService.setPictureRowOffset(
          pictureRowShiftMap,
          templateTripleList,
          offset,
          sheet.getSheetName(),
          ExcelReportConstants.TEMPLATE_SHEET_TITLE);
      excelReportPictureService.writePictures(
          sheet, pictureRowShiftMap, templateTripleList, ExcelReportConstants.TEMPLATE_SHEET_TITLE);
    }
  }

  @Override
  public Integer getHeaderLines(Map<Integer, Map<String, Object>> headerOutputMap) {
    int headerLines = 0;
    for (Map.Entry<Integer, Map<String, Object>> entry : headerOutputMap.entrySet()) {
      if (headerLines < (int) entry.getValue().get(ExcelReportConstants.KEY_ROW)) {
        headerLines = (int) entry.getValue().get(ExcelReportConstants.KEY_ROW);
      }
    }
    return headerLines + 1;
  }

  @Override
  public String generateHeaderHtml(Print print) {
    StringBuilder htmlBuilder = new StringBuilder();
    String attachmentPath = AppSettings.get().getPath("file.upload.dir", "");
    if (attachmentPath != null) {
      attachmentPath =
          attachmentPath.endsWith(File.separator)
              ? attachmentPath
              : attachmentPath + File.separator;
    }

    if (!print.getHidePrintSettings()) {
      Company company = print.getCompany();
      Integer logoPosition = print.getLogoPositionSelect();
      String pdfHeader = print.getPrintPdfHeader() != null ? print.getPrintPdfHeader() : "";
      String imageTag = "";
      String logoWidth =
          print.getLogoWidth() != null ? "width: " + print.getLogoWidth() + ";" : "width: 50%;";
      String headerWidth =
          print.getHeaderContentWidth() != null
              ? "width: " + print.getHeaderContentWidth() + ";"
              : "width: 40%;";

      if (company != null
          && company.getLogo() != null
          && logoPosition != PrintRepository.LOGO_POSITION_NONE
          && new File(attachmentPath + company.getLogo().getFilePath()).exists()) {
        String width = company.getWidth() != 0 ? "width='" + company.getWidth() + "px'" : "";
        String height = company.getHeight() != 0 ? company.getHeight() + "px" : "71px";
        imageTag =
            "<img src='"
                + MetaFiles.getPath(company.getLogo()).toUri()
                + "' height='"
                + height
                + "' "
                + width
                + "/>";
      }

      switch (logoPosition) {
        case PrintRepository.LOGO_POSITION_LEFT:
          htmlBuilder.append(
              "<table style='width: 100%;'><tr><td valign='top' style='text-align: left; "
                  + logoWidth
                  + "'>"
                  + imageTag
                  + "</td><td valign='top' style='width: 10%'></td><td valign='top' style='text-align: left; "
                  + headerWidth
                  + "'>"
                  + pdfHeader
                  + "</td></tr></table>");
          break;
        case PrintRepository.LOGO_POSITION_CENTER:
          htmlBuilder.append(
              "<table style=\"width: 100%;\"><tr><td valign='top' style='width: 33.33%;'></td><td valign='top' style='text-align: center; width: 33.33%;'>"
                  + imageTag
                  + "</td><td valign='top' style='text-align: left; width: 33.33%;'>"
                  + pdfHeader
                  + "</td></tr></table>");
          break;
        case PrintRepository.LOGO_POSITION_RIGHT:
          htmlBuilder.append(
              "<table style='width: 100%;'><tr><td valign='top' style='text-align: left; "
                  + headerWidth
                  + "'>"
                  + pdfHeader
                  + "</td><td valign='top' style='width: 10%'></td><td valign='top' style='text-align: center; "
                  + logoWidth
                  + "'>"
                  + imageTag
                  + "</td></tr></table>");
          break;
        default:
          htmlBuilder.append(
              "<table style='width: 100%;'><tr><td style='width: 60%;'></td><td valign='top' style='text-align: left; width: 40%'>"
                  + pdfHeader
                  + "</td></tr></table>");
          break;
      }
    }

    return htmlBuilder.toString();
  }
}
