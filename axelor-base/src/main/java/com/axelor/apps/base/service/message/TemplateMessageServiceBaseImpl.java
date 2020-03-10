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
package com.axelor.apps.base.service.message;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.db.BirtTemplate;
import com.axelor.apps.base.db.BirtTemplateParameter;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.TemplateContextService;
import com.axelor.apps.message.service.TemplateMessageServiceImpl;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.tool.template.TemplateMaker;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.eclipse.birt.core.data.DataTypeUtil;
import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.report.model.api.elements.DesignChoiceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TemplateMessageServiceBaseImpl extends TemplateMessageServiceImpl {

  private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject
  public TemplateMessageServiceBaseImpl(
      MessageService messageService, TemplateContextService templateContextService) {
    super(messageService, templateContextService);
  }

  @Override
  public Set<MetaFile> getMetaFiles(Template template) throws AxelorException, IOException {

    Set<MetaFile> metaFiles = super.getMetaFiles(template);
    if (template.getBirtTemplate() == null) {
      return metaFiles;
    }

    metaFiles.add(createMetaFileUsingBirtTemplate(maker, template.getBirtTemplate()));

    logger.debug("Metafile to attach: {}", metaFiles);

    return metaFiles;
  }

  public MetaFile createMetaFileUsingBirtTemplate(TemplateMaker maker, BirtTemplate birtTemplate)
      throws AxelorException, IOException {

    logger.debug("Generate birt metafile: {}", birtTemplate.getName());

    String fileName =
        birtTemplate.getName()
            + "-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    File file =
        generateBirtTemplate(
            maker,
            fileName,
            birtTemplate.getTemplateLink(),
            birtTemplate.getFormat(),
            birtTemplate.getBirtTemplateParameterList());

    try (InputStream is = new FileInputStream(file)) {
      return Beans.get(MetaFiles.class).upload(is, fileName + "." + birtTemplate.getFormat());
    }
  }

  public File generateBirtTemplate(
      TemplateMaker maker,
      String fileName,
      String modelPath,
      String format,
      List<BirtTemplateParameter> birtTemplateParameterList)
      throws AxelorException {

    File birtTemplate = null;

    ReportSettings reportSettings =
        generateTemplate(maker, fileName, modelPath, format, birtTemplateParameterList);

    if (reportSettings != null) {
      birtTemplate = reportSettings.getFile();
    }

    return birtTemplate;
  }

  public String generateBirtTemplateLink(
      TemplateMaker maker,
      String fileName,
      String modelPath,
      String format,
      List<BirtTemplateParameter> birtTemplateParameterList)
      throws AxelorException {

    String birtTemplateFileLink = null;

    ReportSettings reportSettings =
        generateTemplate(maker, fileName, modelPath, format, birtTemplateParameterList);

    if (reportSettings != null) {
      birtTemplateFileLink = reportSettings.getFileLink();
    }

    return birtTemplateFileLink;
  }

  protected ReportSettings generateTemplate(
      TemplateMaker maker,
      String fileName,
      String modelPath,
      String format,
      List<BirtTemplateParameter> birtTemplateParameterList)
      throws AxelorException {

    if (modelPath == null || modelPath.isEmpty()) {
      return null;
    }

    ReportSettings reportSettings =
        ReportFactory.createReport(modelPath, fileName).addFormat(format);

    for (BirtTemplateParameter birtTemplateParameter : birtTemplateParameterList) {
      maker.setTemplate(birtTemplateParameter.getValue());

      try {
        reportSettings.addParam(
            birtTemplateParameter.getName(),
            convertValue(birtTemplateParameter.getType(), maker.make()));
      } catch (BirtException e) {
        throw new AxelorException(
            e.getCause(),
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.TEMPLATE_MESSAGE_BASE_2));
      }
    }

    reportSettings.generate();
    return reportSettings;
  }

  protected Object convertValue(String type, String value) throws BirtException {

    if (DesignChoiceConstants.PARAM_TYPE_BOOLEAN.equals(type)) {
      return DataTypeUtil.toBoolean(value);
    } else if (DesignChoiceConstants.PARAM_TYPE_DATETIME.equals(type)) {
      return DataTypeUtil.toDate(value);
    } else if (DesignChoiceConstants.PARAM_TYPE_DATE.equals(type)) {
      return DataTypeUtil.toSqlDate(value);
    } else if (DesignChoiceConstants.PARAM_TYPE_TIME.equals(type)) {
      return DataTypeUtil.toSqlTime(value);
    } else if (DesignChoiceConstants.PARAM_TYPE_DECIMAL.equals(type)) {
      return DataTypeUtil.toBigDecimal(value);
    } else if (DesignChoiceConstants.PARAM_TYPE_FLOAT.equals(type)) {
      return DataTypeUtil.toDouble(value);
    } else if (DesignChoiceConstants.PARAM_TYPE_STRING.equals(type)) {
      return DataTypeUtil.toLocaleNeutralString(value);
    } else if (DesignChoiceConstants.PARAM_TYPE_INTEGER.equals(type)) {
      return DataTypeUtil.toInteger(value);
    }
    return value;
  }
}
