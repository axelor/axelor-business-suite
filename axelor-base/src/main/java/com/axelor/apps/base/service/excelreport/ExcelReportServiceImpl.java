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
package com.axelor.apps.base.service.excelreport;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.excelreport.components.ExcelReportFooterService;
import com.axelor.apps.base.service.excelreport.components.ExcelReportHeaderService;
import com.axelor.apps.base.service.excelreport.config.ExcelReportConstants;
import com.axelor.apps.base.service.excelreport.config.ExcelReportHelperService;
import com.axelor.apps.base.service.excelreport.html.Excel2HtmlConvertor;
import com.axelor.apps.base.service.excelreport.pdf.Html2PdfConvertorService;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.google.common.io.Files;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.script.ScriptException;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.xml.sax.SAXException;

public class ExcelReportServiceImpl implements ExcelReportService {

  ExcelReportDataMapService dataMapService;

  @Inject private ExcelReportHelperService excelReportHelperService;
  @Inject private ExcelReportHeaderService excelReportHeaderService;
  @Inject private ExcelReportFooterService excelReportFooterService;
  @Inject private Html2PdfConvertorService html2PdfConvertorService;
  @Inject private MetaFiles metaFiles;

  @Override
  public File createReport(List<Long> objectIds, PrintTemplate printTemplate)
      throws IOException, ClassNotFoundException, AxelorException, ScriptException,
          ParserConfigurationException, SAXException {
    dataMapService =
        ExcelReportDataMapServiceFactory.get()
            .setPrintTemplate(printTemplate)
            .addObjectIds(objectIds)
            .getService();
    return getOutputFile(objectIds);
  }

  protected File getOutputFile(List<Long> objectIds)
      throws ClassNotFoundException, IOException, AxelorException, ScriptException,
          ParserConfigurationException, SAXException {
    PrintTemplate printTemplate = dataMapService.getPrintTemplate();
    String formatType = printTemplate.getFormatSelect();
    List<Model> result = this.getModelData(printTemplate.getMetaModel().getFullName(), objectIds);
    File excelFile = getExcelFile(result);
    File outputFile = null;

    if (formatType.equals("XLSX")) {
      outputFile = excelFile;
    } else if (formatType.equals("PDF")) {
      outputFile = getPdfFile(excelFile);
    }

    // attach to record
    if (printTemplate.getAttach()) {
      attachMetaFile(outputFile, result.get(0));
    }

    return outputFile;
  }

  protected File getExcelFile(List<Model> result)
      throws IOException, ClassNotFoundException, AxelorException, ScriptException {
    PrintTemplate printTemplate = dataMapService.getPrintTemplate();
    File file = MetaFiles.getPath(printTemplate.getExcelTemplate()).toFile();
    Workbook wb = WorkbookFactory.create(file);

    if (ObjectUtils.isEmpty(wb.getSheet(ExcelReportConstants.TEMPLATE_SHEET_TITLE))) {
      wb.close();
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.NO_TEMPLATE_SHEET_FOUND));
    }

    Map<Integer, Map<String, Object>> inputMap =
        dataMapService.getInputMap(wb, ExcelReportConstants.TEMPLATE_SHEET_TITLE);

    Workbook newWb =
        dataMapService.createWorkbook(
            inputMap,
            result,
            excelReportHelperService.getMapper(printTemplate.getMetaModel().getFullName()),
            printTemplate.getFormatSelect(),
            wb);
    wb.close();
    File excelFile =
        MetaFiles.createTempFile(I18n.get(printTemplate.getMetaModel().getName()), ".xlsx")
            .toFile();
    FileOutputStream outputStream = new FileOutputStream(excelFile.getAbsolutePath());
    newWb.write(outputStream);
    newWb.close();

    return excelFile;
  }

  protected File getPdfFile(File excelFile)
      throws IOException, ParserConfigurationException, SAXException, AxelorException {

    ZipSecureFile.setMinInflateRatio(0);
    Excel2HtmlConvertor toHtml =
        Excel2HtmlConvertor.create(
            excelFile.getPath(),
            excelReportHeaderService.generateHeaderHtml(dataMapService.getPrint()),
            excelReportFooterService.generateFooterHtml(dataMapService.getPrint()),
            dataMapService.getPrintTemplate());
    File html = toHtml.printPage();
    String attachmentPath = AppSettings.get().getPath("file.upload.dir", "");
    if (attachmentPath != null) {
      attachmentPath =
          attachmentPath.endsWith(File.separator)
              ? attachmentPath
              : attachmentPath + File.separator;
    }
    return html2PdfConvertorService.toPdf(
        html, attachmentPath, dataMapService.getPrintTemplate(), dataMapService.getPrint());
  }

  protected void attachMetaFile(File file, Model model) throws IOException {
    if (ObjectUtils.isEmpty(file) || ObjectUtils.isEmpty(model)) {
      return;
    }
    String fileName =
        String.format(
            "%1$s - %2$s.%3$s",
            model.getClass().getSimpleName(),
            LocalDateTime.now().toString(),
            Files.getFileExtension(file.getName()));
    metaFiles.attach(FileUtils.openInputStream(file), fileName, model);
  }

  @SuppressWarnings("unchecked")
  protected <T extends Model> List<T> getModelData(String modelFullName, List<Long> ids)
      throws ClassNotFoundException {
    Class<T> modelClass = (Class<T>) Class.forName(modelFullName);
    return JpaRepository.of(modelClass).all().filter("id in :ids").bind("ids", ids).fetch();
  }
}
