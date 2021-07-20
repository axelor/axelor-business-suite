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
package com.axelor.apps.base.web;

import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.base.db.repo.PrintTemplateRepository;
import com.axelor.apps.base.service.PrintTemplateService;
import com.axelor.apps.base.service.imports.listener.ImporterListener;
import com.axelor.data.xml.XMLImporter;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.xmlbeans.impl.common.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintTemplateController {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void openPrint(ActionRequest request, ActionResponse response) {
    Model context = request.getContext().asType(Model.class);
    String model = request.getModel();

    LOG.debug("Print template wizard call for model : {}", model);

    Query<PrintTemplate> printTemplateQuery =
        Beans.get(PrintTemplateRepository.class)
            .all()
            .filter(
                "self.metaModel.fullName = ? AND (self.archived is null OR self.archived = false)",
                model);

    try {
      long templatesCount = printTemplateQuery.count();

      LOG.debug("Print templates count : {} ", templatesCount);

      if (templatesCount == 0) {
        response.setError(I18n.get("Please define a print template for the model :" + model));
      } else if (templatesCount == 1) {
        PrintTemplate printTemplate = printTemplateQuery.fetchOne();
        response.setView(
            Beans.get(PrintTemplateService.class)
                .generatePrintTemplate(context.getId(), printTemplate));

      } else if (templatesCount >= 2) {
        response.setView(
            ActionView.define(I18n.get("Select template"))
                .model(Wizard.class.getName())
                .add("form", "select-print-template-wizard-form")
                .param("show-confirm", "false")
                .context("_objectId", context.getId().toString())
                .context("_templateContextModel", model)
                .map());
      }
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void generatePrint(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();

    Map templateContext = (Map) context.get("template");
    PrintTemplate printTemplate = null;
    if (templateContext != null) {
      printTemplate =
          Beans.get(PrintTemplateRepository.class)
              .find(Long.parseLong(templateContext.get("id").toString()));
    }

    Long objectId = Long.parseLong(context.get("_objectId").toString());

    try {
      response.setCanClose(true);
      response.setView(
          Beans.get(PrintTemplateService.class).generatePrintTemplate(objectId, printTemplate));
    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void importPrintTemplate(ActionRequest request, ActionResponse response) {

    String config = "/data-import/import-print-template-config.xml";

    try {
      InputStream inputStream = this.getClass().getResourceAsStream(config);
      File configFile = File.createTempFile("config", ".xml");
      FileOutputStream fout = new FileOutputStream(configFile);
      IOUtil.copyCompletely(inputStream, fout);

      Path path =
          MetaFiles.getPath((String) ((Map) request.getContext().get("dataFile")).get("filePath"));
      File tempDir = Files.createTempDir();
      File importFile = new File(tempDir, "print-template.xml");
      Files.copy(path.toFile(), importFile);

      XMLImporter importer =
          new XMLImporter(configFile.getAbsolutePath(), tempDir.getAbsolutePath());

      ImporterListener listner = new ImporterListener("PrintTemplate");

      importer.addListener(listner);

      importer.run();

      FileUtils.forceDelete(configFile);

      FileUtils.forceDelete(tempDir);
      response.setValue("importLog", listner.getImportLog());

    } catch (Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
