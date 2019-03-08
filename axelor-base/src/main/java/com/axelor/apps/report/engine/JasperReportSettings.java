package com.axelor.apps.report.engine;

import com.axelor.apps.base.service.user.UserService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.google.common.base.CaseFormat;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.export.oasis.JROdsExporter;
import net.sf.jasperreports.engine.export.oasis.JROdtExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.Exporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

public class JasperReportSettings extends ReportSettings {
  private URL reportURL;

  public JasperReportSettings(URL reportURL, String outputName) {
    super(null, outputName);
    this.reportURL = reportURL;
  }

  @Override
  public ReportSettings generate() throws AxelorException {
    super.generate();

    try {
      try (InputStream templateStream = reportURL.openStream()) {

        output = MetaFiles.createTempFile(null, "").toFile();
        // normalize parameters as per JasperReports conventions
        final Map<String, Object> jasperParams = new HashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
          String k = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, e.getKey());
          jasperParams.put(k, e.getValue());
        }
        Locale locale;
        if (jasperParams.containsKey("LOCALE") == false) {
          locale = new Locale(Beans.get(UserService.class).getLanguage());
        } else {
          locale = new Locale((String) jasperParams.get("LOCALE"));
        }
        jasperParams.put("REPORT_LOCALE", locale);
        jasperParams.put("REPORT_RESOURCE_BUNDLE", I18n.getBundle(locale));

        final Exporter exporter;
        switch (format) {
          case FORMAT_PDF:
            exporter = new JRPdfExporter();
            break;
          case FORMAT_XLS:
            exporter = new JRXlsExporter();
            break;
          case FORMAT_XLSX:
            exporter = new JRXlsxExporter();
            break;
          case FORMAT_DOCX:
          case FORMAT_DOC:
            exporter = new JRDocxExporter();
            break;
          case FORMAT_ODS:
            exporter = new JROdsExporter();
            break;
          case FORMAT_ODT:
            exporter = new JROdtExporter();
            break;
          case FORMAT_HTML:
            exporter = new HtmlExporter();
            break;
          default:
            throw new AxelorException(
                TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                "Unsupported export format: %s",
                format);
        }

        JPA.jdbcWork(
            connection -> {
              try {
                JasperPrint print =
                    JasperFillManager.fillReport(
                        JasperCompileManager.compileReport(templateStream),
                        jasperParams,
                        connection);
                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
                exporter.exportReport();
              } catch (JRException e) {
                throw new RuntimeException(e);
              }
            });
      }
    } catch (Exception e) {
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }

    return this;
  }

  @Override
  protected ReportSettings addDataBaseConnection() {
    // Useless for us
    return this;
  }
}
