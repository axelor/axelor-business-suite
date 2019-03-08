/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.report.engine;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.beust.jcommander.internal.Maps;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportSettings {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String FORMAT_PDF = "pdf";
  public static final String FORMAT_XLS = "xls";
  public static final String FORMAT_XLSX = "xlsx";
  public static final String FORMAT_DOC = "doc";
  public static final String FORMAT_DOCX = "docx";
  public static final String FORMAT_ODS = "ods";
  public static final String FORMAT_ODT = "odt";
  public static final String FORMAT_HTML = "html";

  protected Map<String, Object> params = Maps.newHashMap();

  protected String format = FORMAT_PDF;
  protected String rptdesign;
  protected String outputName;
  protected Model model;
  protected String fileName;
  protected File output;

  private boolean FLAG_ATTACH = false;

  public ReportSettings(String rptdesign, String outputName) {

    this.rptdesign = rptdesign;
    this.computeOutputName(outputName);
    addDataBaseConnection();
    addAttachmentPath();
  }

  /**
   * This method generate the Birt report output.
   *
   * @return The ReportSettings instance.
   * @throws AxelorException
   */
  public ReportSettings generate() throws AxelorException {

    this.computeFileName();

    return this;
  }

  /**
   * The method get the generated report file link.
   *
   * @return The generated report file link.
   */
  public String getFileLink() {

    if (output == null) {
      return null;
    }

    String fileLink = "ws/files/report/" + output.getName();

    try {
      fileLink += "?name=" + URLEncoder.encode(fileName, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      logger.error(e.getLocalizedMessage());
    }

    logger.debug("URL : {}", fileLink);

    return fileLink;
  }

  public String getOutputName() {
    return outputName;
  }

  /**
   * This method get the generated report file.
   *
   * @return The generated report file.
   */
  public File getFile() {
    return output;
  }

  protected void attach() throws FileNotFoundException, IOException {

    if (FLAG_ATTACH && model.getId() != null && output != null) {
      try (InputStream is = new FileInputStream(output)) {
        Beans.get(MetaFiles.class).attach(is, fileName, model);
      }
    }
  }

  protected void computeOutputName(String outputName) {

    this.outputName =
        outputName
            .replace("${date}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
            .replace("${time}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));

    this.outputName = StringTool.getFilename(this.outputName);
  }

  protected void computeFileName() {

    this.fileName = String.format("%s.%s", outputName, format);
  }

  /**
   * This method can be use to define a specific report output format. The default format is PDF.
   *
   * @param format The ouput format
   *     <p>
   *     <ul>
   *       <li>FORMAT_PDF = "pdf"
   *       <li>FORMAT_XLS = "xls"
   *       <li>FORMAT_DOC = "doc"
   *       <li>FORMAT_HTML = "html"
   *       <li>Or any value supported by Birt
   *     </ul>
   *     <p>
   * @return The ReportSettings instance.
   */
  public ReportSettings addFormat(String format) {

    if (format != null) {
      this.format = format;
    }

    return this;
  }

  /**
   * Method that link the generated report as attachment to the model passed in parameter
   *
   * @param model An Axelor Model
   * @return The ReportSettings instance
   */
  public ReportSettings toAttach(Model model) {

    this.model = Objects.requireNonNull(model);
    FLAG_ATTACH = true;

    return this;
  }

  /**
   * This method is use to pass a parameter to the Birt report.
   *
   * @param param A string key.
   * @param value An object value. The type of value must be a supported type per the Birt report.
   * @return The ReportSettings instance.
   */
  public ReportSettings addParam(String param, Object value) {

    this.params.put(param, value);

    return this;
  }

  protected ReportSettings addDataBaseConnection() {

    AppSettings appSettings = AppSettings.get();

    return this.addParam("DefaultDriver", appSettings.get("db.default.driver"))
        .addParam("DBName", appSettings.get("db.default.url"))
        .addParam("UserName", appSettings.get("db.default.user"))
        .addParam("Password", appSettings.get("db.default.password"));
  }

  private ReportSettings addAttachmentPath() {

    String attachmentPath = AppSettings.get().getPath("file.upload.dir", "");
    if (attachmentPath == null) {
      return this;
    }

    attachmentPath =
        attachmentPath.endsWith(File.separator) ? attachmentPath : attachmentPath + File.separator;

    return this.addParam("AttachmentPath", attachmentPath);
  }

  public static boolean useIntegratedEngine() {

    AppSettings appsSettings = AppSettings.get();

    String useIntegratedEngine = appsSettings.get("axelor.report.use.embedded.engine", "true");

    return "true".equals(useIntegratedEngine);
  }

  public static ReportEngine getReportEngine() {
    AppSettings appsSettings = AppSettings.get();

    String engine = appsSettings.get("axelor.report.engine.type", "birt");

    return "birt".equals(engine) ? ReportEngine.BIRT : ReportEngine.JASPER_REPORTS;
  }

  /**
   * Get the language select for a partner or current connected user.
   *
   * @param partner The partner affected. You can pass a new partner to get the default partner
   *     language. If null, the user language is used.
   * @return Partner language selected or User language if no partner is given. Otherwise, english.
   */
  public static String getPrintingLocale(Partner partner) {

    if (partner != null) {
      return Beans.get(PartnerService.class).getPartnerLanguageCode(partner);
    } else {
      return Beans.get(UserService.class).getLanguage();
    }
  }

  /**
   * Get the language select current connected user.
   *
   * @return
   */
  public static String getPrintingLocale() {
    return getPrintingLocale(null);
  }

  public enum ReportEngine {
    BIRT,
    JASPER_REPORTS
  }
}
