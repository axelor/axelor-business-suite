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
package com.axelor.apps;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.CompanyDocumentTemplateAssignment;
import com.axelor.apps.base.db.DocumentTemplate;
import com.axelor.apps.base.db.UserDocumentTemplateAssignment;
import com.axelor.apps.report.engine.EmbeddedReportSettings;
import com.axelor.apps.report.engine.ExternalReportSettings;
import com.axelor.apps.report.engine.JasperReportSettings;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.meta.MetaFiles;
import com.axelor.report.ReportResourceLocator;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.birt.report.model.api.IResourceLocator;

/** Handles creation of reports settings based on application configuration. */
public class ReportFactory {
  /**
   * Creates a ReportSettings instance suitable to print a given template type for the specified
   * company.
   *
   * @param templateType Type of template, one of the base.document.template.type.select values
   * @param company Company to use to customise the report. If null, no customisation will occur.
   * @param defaultReport Default report to use if no customisation exists.
   * @param outputName Name of the file to generate.
   * @return The ReportSettings with appropriate template configuration.
   */
  public static ReportSettings createReport(
      String templateType, Company company, String defaultReport, String outputName) {
    return createReport(null, templateType, company, defaultReport, outputName);
  }

  /**
   * Creates a ReportSettings using the given document template. If no template is specified,
   * default customisation research occurs.
   *
   * @param template Forced template to use
   * @param templateType Type of template, one of the base.document.template.type.select values.
   *     Only used to look for customised configuration if template is null.
   * @param company Company used to customise template.
   * @param defaultReport Default report to use if template is null an no customisation available.
   * @param outputName Name of the file that the returned ReportSettings will generate.
   * @return The ReportSettings with appropriate template configuration.
   */
  public static ReportSettings createReport(
      DocumentTemplate template,
      String templateType,
      Company company,
      String defaultReport,
      String outputName) {
    Objects.requireNonNull(defaultReport);
    if (template == null) {
      User user = AuthUtils.getUser();
      if (user != null) {
        for (UserDocumentTemplateAssignment assignment : user.getDocumentTemplates()) {
          if (assignment.getType().equals(templateType)
              && assignment.getCompany().equals(company)) {
            template = assignment.getTemplate();
            break;
          }
        }
      }
      if (template == null && company != null) {
        for (CompanyDocumentTemplateAssignment assignment : company.getDocumentTemplates()) {
          if (assignment.getType().equals(templateType)) {
            template = assignment.getTemplate();
          }
        }
      }
      if (template == null) return createReport(defaultReport, outputName);
    }

    final Path templatePath = MetaFiles.getPath(template.getTemplateFile());

    switch (template.getEngine()) {
      case BIRT:
        if (ReportSettings.useIntegratedEngine()) {
          return new EmbeddedReportSettings(templatePath.toString(), outputName);
        } else {
          return new ExternalReportSettings(templatePath.toString(), outputName);
        }
      case JASPER:
        try {
          return new JasperReportSettings(templatePath.toUri().toURL(), outputName);
        } catch (MalformedURLException e) {
          // This shouldn't happen
          return null;
        }
      default:
        throw new IllegalArgumentException("Unhandled template engine: " + template.getEngine());
    }
  }

  public static ReportSettings createReport(String rptdesign, String outputName) {
    if (ReportSettings.getReportEngine() == ReportSettings.ReportEngine.JASPER_REPORTS) {
      IResourceLocator locator = new ReportResourceLocator();
      URL reportURL =
          locator.findResource(
              null, FilenameUtils.removeExtension(rptdesign) + ".jrxml", IResourceLocator.OTHERS);
      if (reportURL != null) {
        return new JasperReportSettings(reportURL, outputName);
      }
    }

    if (ReportSettings.useIntegratedEngine()) {

      return new EmbeddedReportSettings(rptdesign, outputName);
    } else {

      return new ExternalReportSettings(rptdesign, outputName);
    }
  }
}
